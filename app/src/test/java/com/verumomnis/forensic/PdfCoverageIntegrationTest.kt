package com.verumomnis.forensic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.verumomnis.forensic.crypto.Sha512
import com.verumomnis.forensic.engine.*
import com.verumomnis.forensic.model.*
import com.verumomnis.forensic.ui.IngestResult
import com.verumomnis.forensic.ui.MediaIngestor
import com.verumomnis.forensic.vault.EvidenceVault
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import javax.crypto.spec.SecretKeySpec

/** Only generated synthetic input. OCR is substituted: this does NOT assert phone OCR parity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PdfCoverageIntegrationTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val now = Instant.parse("2026-11-22T12:00:00Z")

    @Before fun initPdfBox() { PDFBoxResourceLoader.init(app) }

    private fun mixedPdf(): ByteArray = PDDocument().use { doc ->
        repeat(6) { index ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { stream ->
                if (index == 0 || index == 5) {
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(50f, 700f)
                    stream.showText("Synthetic readable record on original page ${index + 1}.")
                    stream.endText()
                } else {
                    val bitmap = Bitmap.createBitmap(600, 100, Bitmap.Config.ARGB_8888)
                    try {
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        canvas.drawText("Synthetic scan ${index + 1}", 12f, 50f,
                            Paint().apply { color = Color.BLACK; textSize = 32f })
                        stream.drawImage(LosslessFactory.createFromImage(doc, bitmap), 50f, 600f, 300f, 50f)
                    } finally { bitmap.recycle() }
                }
            }
        }
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }

    private fun extractor(): PdfOcrExtractor = PdfOcrExtractor(app, maxOcrPages = 3).apply {
        ocrSource = {
            object : PdfOcrPages {
                override val pageCount = 6
                override fun recognise(index: Int): String = when (index) {
                    1 -> "Synthetic recognised record."
                    2 -> ""
                    3 -> error("synthetic OCR failure")
                    else -> error("OCR must not read text-layer or capped pages")
                }
                override fun close() {}
            }
        }
    }

    @Test fun mixedPdfFileAndBytePathsKeepEveryOriginalPage() {
        val bytes = mixedPdf()
        val extractor = extractor()
        val fromBytes = extractor.extractWithCoverage(bytes)
        val file = File.createTempFile("synthetic-mixed-", ".pdf", app.cacheDir)
        try {
            file.writeBytes(bytes)
            assertEquals(fromBytes, extractor.extractWithCoverage(file))
            assertArrayEquals(bytes, file.readBytes())
        } finally { file.delete() }
        assertEquals(listOf(PageReadStatus.TEXT_LAYER, PageReadStatus.OCR, PageReadStatus.UNREAD,
            PageReadStatus.FAILED, PageReadStatus.SKIPPED, PageReadStatus.TEXT_LAYER),
            fromBytes.coverage.pages.map { it.status })
        assertEquals(1, extractor.lastOcrPageCount)
        assertEquals(1, extractor.lastSkippedPageCount)
        assertTrue(fromBytes.text.contains("page 6"))
    }

    @Test fun ingestFindingsAndVaultedReportPreserveCoverageAndWarnings() {
        val bytes = mixedPdf()
        val uri = Uri.parse("content://synthetic/mixed.pdf")
        ShadowContentResolver.registerProviderInternal("synthetic", object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "application/pdf"
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?) =
                MatrixCursor(projection).apply {
                    addRow(projection!!.map { if (it == OpenableColumns.DISPLAY_NAME) "synthetic-mixed.pdf" else bytes.size }.toTypedArray())
                }
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        })
        shadowOf(app.contentResolver).registerInputStream(uri, bytes.inputStream())
        val vault = EvidenceVault(File(app.cacheDir, "synthetic-coverage-vault"),
            masterKeyProvider = { SecretKeySpec(ByteArray(32) { 1 }, "AES") })
        val ingested = MediaIngestor(app, extractor(), vault).ingestDocument(uri, now) as IngestResult.DocumentSuccess
        assertEquals(Sha512.hash(bytes), ingested.sha512)
        assertEquals(6, ingested.extractionCoverage!!.totalPages)
        val original = File.createTempFile("synthetic-original-", ".pdf", app.cacheDir)
        try {
            vault.materializeEvidence(ingested.fileName, original)
            assertArrayEquals(bytes, original.readBytes())
        } finally { original.delete() }
        val extractionFile = File(vault.findings, "pdf_extraction_${ingested.sha512}.json")
        val persisted = Json.decodeFromString<DocumentExtractionCoverage>(extractionFile.readText())
        assertEquals(ingested.extractionCoverage, persisted.coverage)
        val result = ForensicService.scan(listOf(EvidenceDocument(
            "SYN001", ingested.fileName, "pdf", ingested.text, ingested.sha512,
            extractionCoverage = ingested.extractionCoverage
        )), now = now, vault = vault, caseName = "SyntheticCoverage")
        val findingsFile = File(vault.findings, FindingsJsonEmitter.findingsFileName("SyntheticCoverage", now))
        val json = Json.decodeFromString<FindingsJsonEmitter.FindingsJsonDocument>(findingsFile.readText())
        assertEquals(result.findings.extractionCoverage, json.extraction_coverage)
        assertTrue(json.coverage_warnings.single().contains("WARNING"))
        val report = ReportGenerator.generate(result.findings, "SyntheticCoverage", now)
        assertTrue(report.executiveSummary.startsWith("${ingested.fileName}: WARNING"))
        assertTrue(report.body.contains("Page 3: UNREAD"))
        assertTrue(report.body.contains("Page 4: FAILED"))
        assertTrue(report.body.contains("Page 5: SKIPPED"))
        assertTrue(report.body.contains("not a clean-content assurance"))
        assertTrue(report.body.contains("OCR can omit or misread"))
        assertFalse(report.body.contains(": CLEAR"))
        assertFalse(report.body.contains(": NO TAMPER\n"))
        assertEquals(Sha512.hash(report.body.toByteArray()), report.seal.sha512)
        vault.storeReport(report)
        assertEquals(report.extractionCoverage, vault.listReports().first { it.reference == report.reference }.extractionCoverage)
    }

    @Test fun legacyPdfTextCannotClaimFullCoverageAndOldFindingsStillDecode() {
        val findings = ForensicService.scan(listOf(EvidenceDocument(
            "SYN001", "synthetic.pdf", "pdf", "Synthetic text from an untracked extractor.", "a".repeat(128)
        )), now = now).findings
        assertTrue(findings.extractionCoverage.single().coverage.incomplete)
        assertTrue(ReportGenerator.generate(findings, "Legacy", now).executiveSummary.contains("unknown"))
        val old = """{"engine_version":"test","findings_json_version":"1.0.0","generated_utc":"$now","source_bundle":"old","contradictions":[]}"""
        assertTrue(Json.decodeFromString<FindingsJsonEmitter.FindingsJsonDocument>(old).extraction_coverage.isEmpty())
    }

    @Test fun malformedPdfKeepsUnknownCoverageInsteadOfPretendingItHasZeroPages() {
        val extractor = PdfOcrExtractor(app).apply { ocrSource = { error("synthetic renderer failure") } }
        val result = extractor.extractWithCoverage("not a PDF — synthetic malformed fixture".toByteArray())
        assertTrue(result.text.isBlank())
        assertNull(result.coverage.totalPages)
        assertTrue(result.coverage.incomplete)
        assertNotNull(result.coverage.documentFailure)
    }
}
