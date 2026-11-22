package com.verumomnis.forensic.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/**
 * Hybrid PDF text extractor with on-device OCR for image-only pages.
 *
 * A page's embedded text layer is used when present. Only pages
 * that have little or no text layer — scanned exhibits, photographed documents —
 * are rendered with Android's PdfRenderer and read with Google ML Kit's
 * on-device text recognition. Nothing leaves the device, preserving the
 * "nothing leaves your device" guarantee. OCR is fallible; coverage records
 * describe extraction, not proof that all visible page content was understood.
 *
 * This mirrors the website's selective OCR (only image-only pages are OCR'd),
 * but uses the platform's native recogniser instead of tesseract.js.
 */
class PdfOcrExtractor(
    private val context: Context,
    /** Per-page text-layer length below which a page is treated as image-only. */
    private val thinPageChars: Int = DEFAULT_THIN_PAGE_CHARS,
    /** Cap on pages to OCR, to bound worst-case time on very large scans. */
    private val maxOcrPages: Int = DEFAULT_MAX_OCR_PAGES,
) : PdfTextExtractor {

    /** Test seam: actual PDFBox parsing plus a deterministic substitute for device OCR. */
    internal var ocrSource: ((File) -> PdfOcrPages)? = null

    companion object {
        /** A page with fewer text-layer chars than this is treated as image-only. */
        const val DEFAULT_THIN_PAGE_CHARS: Int = 30
        /** Worst-case bound on how many image-only pages get OCR'd per document. */
        const val DEFAULT_MAX_OCR_PAGES: Int = 200
        /** Rasterisation resolution for OCR — 200 dpi balances accuracy and memory. */
        private const val RENDER_DPI: Float = 200f
        /** PDF user-space is 72 units/inch; scale = dpi / 72. */
        private const val PDF_POINTS_PER_INCH: Float = 72f
        /** Hard cap on either bitmap dimension so a huge page can't OOM the device. */
        private const val MAX_RENDER_DIMENSION_PX: Int = 2600
    }

    /** Number of image-only pages OCR'd and skipped in the last run (for notes). */
    var lastOcrPageCount: Int = 0
        private set
    var lastSkippedPageCount: Int = 0
        private set

    /**
     * Extract text from a PDF, OCR'ing only image-only pages.
     *
     * BLOCKING: this waits on PDFBox parsing and (via `Tasks.await`) on ML Kit
     * OCR, so it MUST be called off the main thread. All current callers invoke
     * it inside `Dispatchers.IO` coroutines (VerumViewModel, MediaIngestor).
     */
    /**
     * File-based extraction — the path large evidence should take.
     *
     * PDFBox loads from the file, and the OCR renderer
     * renders directly from it instead of writing a temporary copy. A 150 MB
     * bundle costs roughly its own size on disk and near-constant heap, where
     * the ByteArray path cost several multiples of it.
     */
    override fun extractText(file: File): String = extractWithCoverage(file).text

    override fun extractText(bytes: ByteArray): String = extractWithCoverage(bytes).text

    override fun extractWithCoverage(bytes: ByteArray): PdfExtractionResult {
        val tmp = File.createTempFile("vo_ocr_", ".pdf", context.cacheDir)
        return try {
            tmp.writeBytes(bytes)
            extractWithCoverage(tmp)
        } finally {
            tmp.delete()
        }
    }

    /** Shared file-backed path, including byte-array inputs spilled into a temporary file. */
    override fun extractWithCoverage(file: File): PdfExtractionResult {
        lastOcrPageCount = 0
        lastSkippedPageCount = 0

        // 1. Per-page text-layer extraction with PDFBox.
        val pageText = mutableListOf<String?>()
        var pageCountKnown = false
        try {
            PDDocument.load(file).use { doc ->
                val stripper = PDFTextStripper()
                val n = doc.numberOfPages
                pageCountKnown = true
                for (i in 1..n) {
                    stripper.startPage = i
                    stripper.endPage = i
                    // A broken page must not truncate the page list or hide later pages.
                    pageText.add(try { stripper.getText(doc).orEmpty().trim() } catch (_: Exception) { null })
                }
            }
        } catch (_: Exception) {
            // If parsing fails before the count is known, the renderer may recover it.
        }

        return extractPdfPages(pageText, pageCountKnown, thinPageChars, maxOcrPages) {
            ocrSource?.invoke(file) ?: openDeviceOcr(file)
        }.also { result ->
            lastOcrPageCount = result.coverage.pages.count { it.status == com.verumomnis.forensic.model.PageReadStatus.OCR }
            lastSkippedPageCount = result.coverage.pages.count { it.status == com.verumomnis.forensic.model.PageReadStatus.SKIPPED }
        }
    }

    private fun openDeviceOcr(file: File): PdfOcrPages {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var recognizer: com.google.mlkit.vision.text.TextRecognizer? = null
        try {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd = descriptor
            val pdfRenderer = PdfRenderer(descriptor)
            renderer = pdfRenderer
            val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer = textRecognizer
            return object : PdfOcrPages {
                override val pageCount: Int get() = pdfRenderer.pageCount
                override fun recognise(index: Int): String = renderPageAndRecognise(pdfRenderer, index, textRecognizer)
                override fun close() {
                    try { pdfRenderer.close() } finally {
                        try { descriptor.close() } finally { textRecognizer.close() }
                    }
                }
            }
        } catch (e: Exception) {
            try { renderer?.close() } catch (e: Exception) {}
            try { pfd?.close() } catch (e: Exception) {}
            try { recognizer?.close() } catch (e: Exception) {}
            throw e
        }
    }

    /** Fresh bounded attempt on verified original bytes, off the UI thread. */
    fun retryPages(
        file: File,
        previous: com.verumomnis.forensic.model.PdfExtractionCoverage,
        attempt: com.verumomnis.forensic.model.PdfRetryAttempt
    ): PdfExtractionResult = retryPdfPages(previous, attempt, maxOcrPages) {
        ocrSource?.invoke(file) ?: openDeviceOcr(file)
    }

    private fun renderPageAndRecognise(renderer: PdfRenderer, index: Int, recognizer: com.google.mlkit.vision.text.TextRecognizer): String {
        val page = renderer.openPage(index)
        try {
            // DPI-based rasterisation (page.width/height are PDF points = 1/72").
            // Capped per side so an oversized page can't allocate an OOM bitmap.
            val scale = RENDER_DPI / PDF_POINTS_PER_INCH
            val w = (page.width * scale).toInt().coerceIn(1, MAX_RENDER_DIMENSION_PX)
            val h = (page.height * scale).toInt().coerceIn(1, MAX_RENDER_DIMENSION_PX)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            return try {
                Canvas(bmp).drawColor(Color.WHITE) // white background: OCR needs dark-on-light
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val visionText = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)))
                visionText.text.trim()
            } finally {
                bmp.recycle()
            }
        } finally {
            page.close()
        }
    }
}
