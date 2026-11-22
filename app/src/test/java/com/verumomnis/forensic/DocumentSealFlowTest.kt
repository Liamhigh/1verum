package com.verumomnis.forensic

import android.app.Application
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.verumomnis.forensic.blockchain.BlockchainService
import com.verumomnis.forensic.crypto.Sha512
import com.verumomnis.forensic.identity.IdentityService
import com.verumomnis.forensic.identity.InMemoryIdentityKeyStore
import com.verumomnis.forensic.model.*
import com.verumomnis.forensic.pdf.SealedOutputNames
import com.verumomnis.forensic.seal.DocumentSealer
import com.verumomnis.forensic.seal.OpenTimestampsClient
import com.verumomnis.forensic.seal.SealMetadataCodec
import com.verumomnis.forensic.ui.DocumentSealServices
import com.verumomnis.forensic.ui.VerumViewModel
import com.verumomnis.forensic.engine.PdfExtractionResult
import com.verumomnis.forensic.model.PdfExtractionCoverage
import com.verumomnis.forensic.model.PageExtraction
import com.verumomnis.forensic.model.PageReadStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Workflow tests: real analysis/report generation, substituted device PDF/OCR/network boundaries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DocumentSealFlowTest {
    private val app get() = RuntimeEnvironment.getApplication() as Application
    private val now = Instant.parse("2026-11-21T19:00:00Z")
    private val input = "%PDF-1.4\nsynthetic input".toByteArray()
    private val extractionCalls = AtomicInteger()
    private val sealCalls = AtomicInteger()

    private val offline = object : BlockchainService {
        override fun anchor(sha512Hex: String) = OtsAnchorResult(
            status = OtsStatus.OFFLINE, sha512 = sha512Hex, sha256Digest = "",
            calendarUrls = emptyList(), otsProofBase64 = "", otsProofFile = "", submittedAt = now.toString()
        )
        override fun verify(otsProofBase64: String) = OtsVerifyResult(
            status = OtsStatus.OFFLINE, pending = false, bitcoinAttested = false, message = "Offline fixture"
        )
        override fun upgrade(otsProofBase64: String) = anchor("")
    }

    private fun vm(
        text: String = "A neutral copied record was identified and verified.",
        writer: (ForensicReport, String?) -> File = { report, sourceName ->
            SealedOutputNames.createUniqueFile(
                app.cacheDir, SealedOutputNames.forensicReport(sourceName, report.reference)
            ).apply { writeText("%PDF-1.4\nsynthetic report writer boundary") }
        },
        inspectOptions: (DocumentSealer.SealOptions) -> Unit = {},
        coverage: PdfExtractionCoverage? = null
    ) = VerumViewModel(
        application = app, identityService = IdentityService(app, InMemoryIdentityKeyStore()),
        blockchainService = offline, reportPdfWriter = writer,
        documentSealServices = DocumentSealServices(
            seal = { bytes, options ->
                sealCalls.incrementAndGet()
                inspectOptions(options)
                DocumentSealer.SealedDocument(
                    sealedPdf = bytes.copyOf(), sealId = "fixture-seal",
                    sha256 = "b".repeat(64), sha512 = Sha512.hash(bytes),
                    verifyUrl = "https://verumglobal.foundation/verify.html",
                    metadata = SealMetadataCodec.SealMetadata(v = "1.2", t = options.timestampMs, type = options.sealType),
                    priorChain = emptyList(), ots = null, pageCount = 1, pageErrors = 0, passwordProtected = false
                )
            },
            extractText = { extractionCalls.incrementAndGet(); text },
            anchor = { OpenTimestampsClient.OtsResult.Failure("Offline fixture") },
            extractPdf = {
                extractionCalls.incrementAndGet()
                PdfExtractionResult(text, coverage ?: PdfExtractionCoverage(documentFailure = "Synthetic legacy extractor"))
            }
        )
    )

    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!predicate() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue("Workflow did not finish within the test deadline", predicate())
    }

    @Test fun sealOnlyNeverReadsContentsOrCreatesAForensicReport() {
        val vm = vm()
        vm.selectPdfForSealing(input, "original.pdf", input.size.toLong())
        vm.sealDocument(now)
        await { !vm.state.value.sealBusy }
        val state = vm.state.value
        assertNotNull(state.sealResult)
        assertTrue(state.websiteSealedFile!!.isFile)
        assertEquals(
            "original_sealed_fixture-seal_${now.toEpochMilli()}.pdf",
            state.websiteSealedFile!!.name
        )
        val sharedUri = FileProvider.getUriForFile(
            app, "${app.packageName}.fileprovider", state.websiteSealedFile!!
        )
        // Robolectric starts with an empty MIME registry; Android includes PDF.
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("pdf", "application/pdf")
        assertEquals("application/pdf", app.contentResolver.getType(sharedUri))
        app.contentResolver.query(
            sharedUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(state.websiteSealedFile!!.name, cursor.getString(0))
        }
        assertEquals(0, extractionCalls.get())
        assertFalse(state.sealForensicRequested)
        assertNull(state.report)
        assertNull(state.reportPdfFile)
    }

    @Test fun forensicChoiceWaitsForPdfAndIgnoresDoubleTapOrModeChanges() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val vm = vm(writer = { report, sourceName ->
            entered.countDown()
            check(release.await(15, TimeUnit.SECONDS))
            SealedOutputNames.createUniqueFile(
                app.cacheDir, SealedOutputNames.forensicReport(sourceName, report.reference)
            ).apply { writeText("%PDF-1.4\nfixture") }
        })
        vm.selectPdfForSealing(input, "original.pdf", input.size.toLong())
        vm.setSealMode("forensic")
        vm.sealDocument(now)
        try {
            assertTrue(entered.await(15, TimeUnit.SECONDS))
            assertTrue(vm.state.value.sealBusy)
            assertTrue(vm.state.value.sealForensicRunning)
            vm.setSealMode("seal-only")
            vm.clearSealDocument()
            vm.sealDocument(now)
            assertEquals("forensic", vm.state.value.sealMode)
            assertEquals(1, sealCalls.get())
        } finally { release.countDown() }
        await { !vm.state.value.sealBusy }
        val state = vm.state.value
        assertEquals(1, extractionCalls.get())
        assertNotNull(state.report)
        assertTrue(state.reportPdfFile!!.isFile)
        assertTrue(state.reportPdfFile!!.name.startsWith("original_forensic_report_"))
        assertEquals(state.report!!.reference, state.sealReportReference)
        assertTrue(state.sealForensicError.isBlank())
        assertNull(state.guardianBlock)
        assertEquals(Sha512.hash(input), state.files.single().sha512)
    }

    @Test fun unreadableEvidenceKeepsSealedPdfButNeverClaimsAReportWasCreated() {
        val vm = vm(text = "")
        vm.selectPdfForSealing(input, "unreadable.pdf", input.size.toLong())
        vm.setSealMode("forensic")
        vm.sealDocument(now)
        await { !vm.state.value.sealBusy }
        assertNotNull(vm.state.value.sealResult)
        assertTrue(vm.state.value.websiteSealedFile!!.isFile)
        assertTrue(vm.state.value.sealForensicError.contains("No readable text"))
        assertTrue(vm.state.value.sealForensicError.contains("incomplete PDF coverage"))
        assertTrue(vm.state.value.sealReportReference.isBlank())
        assertNull(vm.state.value.report)
        assertTrue(File(app.filesDir, "vault/findings/pdf_extraction_${Sha512.hash(input)}.json").isFile)
    }

    @Test fun reportWriteFailureKeepsDocumentAndSupportsPdfRetry() {
        var fail = true
        val vm = vm(writer = { report, sourceName ->
            if (fail) error("Simulated writer failure")
            SealedOutputNames.createUniqueFile(
                app.cacheDir, SealedOutputNames.forensicReport(sourceName, report.reference)
            ).apply { writeText("%PDF-1.4\nretry") }
        })
        vm.selectPdfForSealing(input, "retry.pdf", input.size.toLong())
        vm.setSealMode("forensic")
        vm.sealDocument(now)
        await { !vm.state.value.sealBusy }
        assertTrue(vm.state.value.websiteSealedFile!!.isFile)
        assertNotNull(vm.state.value.report)
        assertTrue(vm.state.value.reportPdfError.isNotBlank())
        assertNull(vm.state.value.reportPdfFile)
        fail = false
        // Merely choosing another input must not rename an already-created report.
        vm.selectPdfForSealing(input, "newly-selected.pdf", input.size.toLong())
        vm.retryReportPdf()
        await { !vm.state.value.reportPdfGenerating }
        assertTrue(vm.state.value.reportPdfFile!!.isFile)
        assertTrue(vm.state.value.reportPdfFile!!.name.startsWith("retry_forensic_report_"))
        assertTrue(vm.state.value.reportPdfError.isBlank())
    }

    @Test fun nextDocumentDoesNotReuseOldReportOrMixOldEvidence() {
        val vm = vm()
        vm.selectPdfForSealing(input, "first.pdf", input.size.toLong())
        vm.setSealMode("forensic")
        vm.sealDocument(now)
        await { !vm.state.value.sealBusy }
        val firstPdf = vm.state.value.reportPdfFile!!
        assertTrue(firstPdf.name.startsWith("first_forensic_report_"))
        assertTrue(vm.state.value.websiteSealedFile!!.name.startsWith("first_sealed_fixture-seal_"))
        val firstBytes = firstPdf.readBytes()
        vm.clearSealDocument()
        vm.selectPdfForSealing(input, "second.pdf", input.size.toLong())
        assertFalse(vm.state.value.sealForensicRequested)
        assertTrue(vm.state.value.sealReportReference.isBlank())
        vm.setSealMode("forensic")
        vm.sealDocument(now.plusSeconds(2))
        await { !vm.state.value.sealBusy }
        assertTrue(vm.state.value.reportPdfFile!!.name.startsWith("second_forensic_report_"))
        assertTrue(vm.state.value.websiteSealedFile!!.name.startsWith("second_sealed_fixture-seal_"))
        assertEquals(listOf("second.pdf"), vm.state.value.files.map { it.name })
        assertArrayEquals(firstBytes, firstPdf.readBytes())
        assertEquals(2, extractionCalls.get())
    }

    @Test fun bothModesPassCapturedGpsToTheSealer() {
        for (mode in listOf("seal-only", "forensic")) {
            var observed: DocumentSealer.SealOptions? = null
            val vm = vm(inspectOptions = { observed = it })
            vm.setGps(GpsRecord(latitude = 0.0, longitude = 0.0, accuracy = 12.0, timestamp = now.toString()))
            vm.selectPdfForSealing(input, "gps.pdf", input.size.toLong())
            vm.setSealMode(mode)
            vm.sealDocument(now)
            await { !vm.state.value.sealBusy }
            assertEquals("0.0", observed!!.gpsLat)
            assertEquals("0.0", observed!!.gpsLng)
            assertEquals(12, observed!!.gpsAccuracyM)
            assertEquals(now.toEpochMilli(), observed!!.timestampMs)
        }
    }

    @Test fun forensicReportRetainsPartialCoverageThroughThePrimarySealFlow() {
        val coverage = PdfExtractionCoverage(totalPages = 2, pages = listOf(
            PageExtraction(1, PageReadStatus.TEXT_LAYER, "embedded_text", 50, 50),
            PageExtraction(2, PageReadStatus.FAILED, "page_render_or_ocr_failed", ocrAttempted = true)
        ))
        val vm = vm(coverage = coverage)
        vm.selectPdfForSealing(input, "synthetic-partial.pdf", input.size.toLong())
        vm.setSealMode("forensic")
        vm.sealDocument(now)
        await { !vm.state.value.sealBusy }
        assertTrue(vm.state.value.sealForensicError.isBlank())
        assertEquals(coverage, vm.state.value.report!!.extractionCoverage.single().coverage)
        assertTrue(vm.state.value.report!!.body.contains("Page 2: FAILED"))
        assertTrue(vm.state.value.report!!.executiveSummary.contains("WARNING"))
        assertTrue(vm.state.value.scanLog.contains("incomplete page coverage"))
        assertTrue(vm.state.value.sealPipeline.last().detail.contains("incomplete page coverage"))
    }

    @Test fun previewConfirmationDoesNotLosePageProvenance() {
        val coverage = PdfExtractionCoverage(totalPages = 1, pages = listOf(
            PageExtraction(1, PageReadStatus.SKIPPED, "ocr_page_limit")
        ))
        val vm = vm()
        vm.setPendingFiles(listOf(com.verumomnis.forensic.ui.PendingFilePreview(
            "synthetic-preview.pdf", "application/pdf", 100, "a".repeat(128),
            coverage.summary(), false, documentText = "", extractionCoverage = coverage
        )))
        vm.confirmAndSeal("SyntheticPreview", now)
        await { vm.state.value.report != null }
        assertEquals(coverage, vm.state.value.report!!.extractionCoverage.single().coverage)
        assertTrue(vm.state.value.report!!.executiveSummary.contains("not a clean-content assurance"))
    }
}
