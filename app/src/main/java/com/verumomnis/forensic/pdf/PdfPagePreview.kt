package com.verumomnis.forensic.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.verumomnis.forensic.model.DocumentExtractionCoverage
import com.verumomnis.forensic.vault.EvidenceVault
import java.io.File

/** Read-only rasterisation. This path deliberately has no text extractor or OCR dependency. */
class PdfPagePreview(
    private val vault: EvidenceVault,
    private val cacheDir: File,
    private val render: (File, Int) -> Bitmap = ::renderOriginalPage
) {
    /** Stored page numbers are one-based, regardless of coverage ordering or retry selection. */
    fun load(record: DocumentExtractionCoverage, pageNumber: Int): Bitmap {
        require(pageNumber > 0 && record.coverage.pages.count { it.page == pageNumber } == 1) {
            "The selected page has no unambiguous saved extraction record."
        }
        record.coverage.totalPages?.let { require(pageNumber <= it) { "Page is outside the saved PDF page count." } }
        val temporary = File.createTempFile("vo_page_preview_", ".pdf", cacheDir)
        return try {
            vault.materializePdfSource(record, temporary)
            render(temporary, pageNumber - 1)
        } finally {
            temporary.delete()
        }
    }
}

private fun renderOriginalPage(file: File, index: Int): Bitmap =
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            require(index in 0 until renderer.pageCount) { "Page is outside the original PDF." }
            renderer.openPage(index).use { page ->
                // Bound memory and preserve aspect ratio, including unusually large PDF pages.
                val scale = 1600f / maxOf(page.width, page.height).coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(
                    (page.width * scale).toInt().coerceAtLeast(1),
                    (page.height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } catch (error: Throwable) {
                    bitmap.recycle()
                    throw error
                }
            }
        }
    }
