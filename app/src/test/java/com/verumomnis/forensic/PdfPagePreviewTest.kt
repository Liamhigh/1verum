package com.verumomnis.forensic

import android.graphics.Color
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.verumomnis.forensic.model.*
import com.verumomnis.forensic.pdf.PdfPagePreview
import com.verumomnis.forensic.vault.EvidenceVault
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import javax.crypto.spec.SecretKeySpec

/** Synthetic originals with distinct page sizes and colours. */
internal fun comparisonPdf(): ByteArray {
    PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
    return PDDocument().use { doc ->
        listOf(PDRectangle(100f, 200f), PDRectangle(200f, 100f)).forEachIndexed { index, size ->
            val page = PDPage(size)
            doc.addPage(page)
            PDPageContentStream(doc, page).use {
                it.setNonStrokingColor(if (index == 0) 1f else 0f, 0f, if (index == 0) 0f else 1f)
                it.addRect(0f, 0f, size.width, size.height)
                it.fill()
            }
        }
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }
}

/**
 * Robolectric 4.13 does not implement PdfRenderer (its page count is always zero).
 * Substitute only rasterisation, parsing the real original to check the requested page.
 * This is not a test of Android's renderer pixels or OCR accuracy.
 */
internal fun comparisonRaster(file: File, index: Int): android.graphics.Bitmap = PDDocument.load(file).use { doc ->
    require(index in 0 until doc.numberOfPages)
    val box = doc.getPage(index).mediaBox
    val scale = 1600f / maxOf(box.width, box.height)
    android.graphics.Bitmap.createBitmap(
        (box.width * scale).toInt(), (box.height * scale).toInt(),
        android.graphics.Bitmap.Config.ARGB_8888
    ).apply { eraseColor(if (box.width < box.height) Color.RED else Color.BLUE) }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfPagePreviewTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val root by lazy { File(app.cacheDir, "compare-${System.nanoTime()}").apply { mkdirs() } }
    private val cache by lazy { File(root, "private-cache").apply { mkdirs() } }
    private val vaultRoot by lazy { File(root, "vault") }
    private val vault by lazy {
        EvidenceVault(vaultRoot, masterKeyProvider = { SecretKeySpec(ByteArray(32) { 6 }, "AES") })
    }
    private fun source(bytes: ByteArray = comparisonPdf()): DocumentExtractionCoverage {
        val hash = vault.preservePdfSource(bytes)
        // Deliberately out of order: renderer must use stored page numbers, not list positions.
        val coverage = PdfExtractionCoverage(2, listOf(
            PageExtraction(2, PageReadStatus.OCR, "on_device_ocr", extractedChars = 18,
                extractedText = "Saved OCR\n  exact. "),
            PageExtraction(1, PageReadStatus.TEXT_LAYER, "embedded_text", extractedChars = 14,
                extractedText = "Embedded text.")
        ))
        vault.storePdfExtraction("DOC001", "same-name.pdf", hash, coverage)
        return requireNotNull(vault.loadPdfExtraction(hash))
    }
    private fun snapshots() = vaultRoot.walkTopDown().filter { it.isFile }
        .associate { it.relativeTo(vaultRoot).path to it.readBytes().toList() }

    @Test fun storedPageNumbersRenderTheMatchingOriginalWithoutChangingAnyVaultBytes() {
        val record = source()
        val before = snapshots()
        val preview = PdfPagePreview(vault, cache, ::comparisonRaster)
        val second = preview.load(record, 2)
        assertEquals(1600, second.width)
        assertEquals(800, second.height)
        assertEquals(Color.BLUE, second.getPixel(800, 400))
        second.recycle()
        val first = preview.load(record, 1)
        assertEquals(800, first.width)
        assertEquals(1600, first.height)
        assertEquals(Color.RED, first.getPixel(400, 800))
        first.recycle()
        assertEquals(before, snapshots())
        assertEquals(record, vault.loadPdfExtraction(record.sha512))
        assertTrue(cache.listFiles().orEmpty().isEmpty())
    }

    @Test fun retrySnapshotStillMapsToOriginalPageAndUsesHashPinnedSource() {
        val record = source()
        // An unrelated upload under the same display name must never supply the preview.
        vault.storeEvidence(record.fileName, "unrelated PDF bytes".toByteArray())
        val retry = vault.storePdfRetry(record.copy(coverage = record.coverage.copy(
            pages = record.coverage.pages.map {
                if (it.page == 2) it.copy(extractedText = "Retry saved text.") else it
            },
            retryAttempt = PdfRetryAttempt("preview-retry", "2026-11-23T00:00:00Z", listOf(2))
        )))
        val before = snapshots()
        val original = vault.readEvidence("pdf_source_${record.sha512}.pdf")
        var renderedIndex = -1
        PdfPagePreview(vault, cache) { file, index ->
            assertArrayEquals(original, file.readBytes())
            renderedIndex = index
            android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        }.load(retry, 2).recycle()
        assertEquals(1, renderedIndex)
        assertEquals(before, snapshots())
        assertTrue(cache.listFiles().orEmpty().isEmpty())
    }

    @Test fun invalidMappingAndUnavailableOriginalNeverReachRendererOrChangeSavedText() {
        val record = source()
        var renderCalls = 0
        val preview = PdfPagePreview(vault, cache) { _, _ -> renderCalls++; error("must not render") }
        val before = snapshots()
        assertThrows(IllegalArgumentException::class.java) { preview.load(record, 0) }
        assertThrows(IllegalArgumentException::class.java) { preview.load(record, 3) }
        assertThrows(IllegalArgumentException::class.java) {
            preview.load(record.copy(coverage = record.coverage.copy(pages = record.coverage.pages + record.coverage.pages[0])), 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            preview.load(record.copy(sha512 = "f".repeat(128), fileName = "missing.pdf"), 1)
        }
        assertEquals(0, renderCalls)
        assertEquals(before, snapshots())
        assertTrue(cache.listFiles().orEmpty().isEmpty())
    }

    @Test fun mismatchedLegacySourceIsRefusedAndTemporaryPlaintextIsRemoved() {
        val record = source()
        vault.storeEvidence("legacy.pdf", "different source".toByteArray())
        val before = snapshots()
        assertThrows(IllegalStateException::class.java) {
            PdfPagePreview(vault, cache).load(record.copy(sha512 = "e".repeat(128), fileName = "legacy.pdf"), 1)
        }
        assertEquals(before, snapshots())
        assertTrue(cache.listFiles().orEmpty().isEmpty())
    }

    @Test fun rendererFailureAndOutOfRangePageLeaveNoTemporaryOriginal() {
        val record = source()
        val outside = record.copy(coverage = PdfExtractionCoverage(3, listOf(
            PageExtraction(3, PageReadStatus.UNREAD, "ocr_returned_no_text")
        )))
        assertThrows(IllegalArgumentException::class.java) {
            PdfPagePreview(vault, cache, ::comparisonRaster).load(outside, 3)
        }
        val corrupt = source("%PDF broken".toByteArray())
        assertThrows(Exception::class.java) {
            PdfPagePreview(vault, cache, ::comparisonRaster).load(corrupt, 1)
        }
        assertTrue(cache.listFiles().orEmpty().isEmpty())
    }
}
