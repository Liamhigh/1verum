package com.verumomnis.forensic

import com.verumomnis.forensic.crypto.Sha512
import com.verumomnis.forensic.engine.*
import com.verumomnis.forensic.model.*
import com.verumomnis.forensic.model.PageReadStatus.*
import com.verumomnis.forensic.vault.EvidenceVault
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import javax.crypto.spec.SecretKeySpec

/** Original-byte preservation and real analysis; only device recognition/PDF output are substituted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PdfRetryIntegrationTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val now = Instant.parse("2026-11-22T12:00:00Z")
    private val bytes = "%PDF-1.4 synthetic original immutable PDF fixture".toByteArray()
    private val root by lazy { File(app.cacheDir, "retry-${System.nanoTime()}").apply { mkdirs() } }
    private val key = SecretKeySpec(ByteArray(32) { 9 }, "AES")
    private fun vault() = EvidenceVault(root, masterKeyProvider = { key }).apply { initialize() }
    private val coverage = PdfExtractionCoverage(3, listOf(
        PageExtraction(1, TEXT_LAYER, "embedded_text", 40, 40, extractedText = "Synthetic neutral readable record on original page one."),
        PageExtraction(2, FAILED, "page_render_or_ocr_failed", ocrAttempted = true),
        PageExtraction(3, SKIPPED, "ocr_page_limit")
    ))
    private fun source(vault: EvidenceVault, previous: PdfExtractionCoverage = coverage): DocumentExtractionCoverage {
        val hash = vault.preservePdfSource(bytes)
        vault.storePdfExtraction("DOC001", "original.pdf", hash, previous)
        return vault.loadPdfExtraction(hash)!!
    }
    private fun service(vault: EvidenceVault, recognised: String = "Synthetic recovered record on page two.", failExport: Boolean = false) =
        PdfRetryService(vault, app.cacheDir, extract = { file, previous, attempt ->
            assertEquals(Sha512.hash(bytes), Sha512.hash(file))
            retryPdfPages(previous, attempt, 200) {
                object : PdfOcrPages {
                    override val pageCount = 3
                    override fun recognise(index: Int): String { assertTrue(index in listOf(1, 2)); return recognised }
                    override fun close() {}
                }
            }
        }, writePdf = { report, _ ->
            if (failExport) error("Synthetic export boundary failure")
            File(vault.reportsSealed, "${report.reference}.pdf").apply {
                check(createNewFile())
                writeText("%PDF-1.4 synthetic native writer boundary\n${report.body}")
            }
        })

    @Test fun retrySurvivesReloadWithoutChangingOriginalBytesHashesOrEarlierReports() {
        val vault = vault()
        val source = source(vault)
        val old = ReportGenerator.generate(ForensicService.scan(listOf(EvidenceDocument(
            "DOC001", source.fileName, "pdf", source.coverage.pages[0].extractedText!!, source.sha512,
            extractionCoverage = source.coverage
        )), now).findings, "Original", now)
        val originalReportFile = vault.storeReport(old)
        vault.storeSealedReport("prior-sealed.pdf", "synthetic prior sealed bytes".toByteArray())
        val before = root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).path to Sha512.hash(it) }

        val first = service(vault).retry(source, listOf(2), old.reference, now)
        val second = service(vault).retry(first.record, listOf(3), old.reference, now.plusSeconds(1))
        before.forEach { (path, hash) -> assertEquals("Must retain $path", hash, Sha512.hash(File(root, path))) }
        assertEquals(old.body, Json.decodeFromString(ForensicReport.serializer(), originalReportFile.readText()).body)
        val firstReport = requireNotNull(first.report)
        assertNotEquals(firstReport.reference, second.report!!.reference)
        assertNotEquals(first.reportFile, second.reportFile)
        assertTrue(firstReport.body.contains("NEW PDF-only OCR Retry Report"))
        assertTrue(firstReport.body.contains(old.reference))
        assertTrue(firstReport.body.contains("selected original pages: 2"))
        assertEquals(Sha512.hash(firstReport.body), firstReport.seal.sha512)
        assertTrue(File(firstReport.findingsJsonPath).readText().contains("NEW OCR RETRY ATTEMPT"))
        assertEquals(source.sha512, first.record.sha512)
        assertEquals(first.record.coverage.retryAttempt!!.id, second.record.coverage.retryAttempt!!.parentAttemptId)

        val reloaded = vault()
        assertEquals(3, reloaded.listPdfExtractions().size)
        assertEquals(source, reloaded.loadPdfExtraction(source.sha512))
        val materialised = File(root, "source-check.pdf")
        reloaded.materializePdfSource(second.record, materialised)
        assertArrayEquals(bytes, materialised.readBytes())
    }

    @Test fun allUnreadAttemptIsSavedWithoutClaimingCleanFindingsOrProducingAReport() {
        val vault = vault()
        val source = source(vault, coverage.copy(pages = coverage.pages.map {
            it.copy(status = UNREAD, extractedText = null, extractedChars = 0, textLayerChars = 0)
        }))
        val result = service(vault, recognised = "").retry(source, listOf(2), null, now)
        assertNull(result.report)
        assertNull(result.reportFile)
        assertTrue(result.message.contains("not a clean result"))
        assertEquals(2, vault().listPdfExtractions().size)
    }

    @Test fun exportFailureKeepsRetryCoverageAndNewReportSnapshot() {
        val vault = vault()
        val source = source(vault)
        val result = service(vault, failExport = true).retry(source, listOf(2), "prior-report", now)
        assertNull(result.reportFile)
        assertNotNull(result.report)
        assertTrue(vault.listReports().any { it.reference == result.report!!.reference })
        assertEquals(2, vault().listPdfExtractions().size)
        assertTrue(result.message.contains("could not be exported"))
    }

    @Test fun sourceFingerprintMismatchStopsBeforeOcrOrNewFindings() {
        val vault = vault()
        val source = source(vault)
        // A legacy filename collision must not be interpreted as the selected original.
        File(vault.evidenceRaw, "pdf_source_${source.sha512}.pdf").delete()
        vault.storeEvidence(source.fileName, "different original bytes".toByteArray())
        assertThrows(IllegalStateException::class.java) {
            service(vault).retry(source, listOf(2), null, now)
        }
        assertEquals(1, vault.listPdfExtractions().size)
        assertTrue(vault.listReports().isEmpty())
    }

    @Test fun savingTheSamePdfAgainDoesNotReplaceItsHistoricalExtraction() {
        val vault = vault()
        val original = source(vault)
        vault.storePdfExtraction("DOC999", "renamed.pdf", original.sha512, coverage.copy(documentFailure = "different attempt"))
        assertEquals(original, vault.loadPdfExtraction(original.sha512))
        assertEquals(2, vault.listPdfExtractions().size)
        assertEquals("renamed.pdf", vault.listPdfExtractions().first().fileName)
    }

    @Test fun reimportRepairsUnknownCountAndLegacyPageTextWithoutReplacingEarlierSnapshots() {
        listOf(
            PdfExtractionCoverage(documentFailure = "Original parser and renderer unavailable"),
            coverage.copy(pages = coverage.pages.map { it.copy(extractedText = null) })
        ).forEachIndexed { index, unusable ->
            val vault = EvidenceVault(File(root, "recover-$index"), masterKeyProvider = { key })
            val original = source(vault, unusable)
            val oldSnapshot = File(vault.findings, "pdf_extraction_${original.sha512}.json")
            val oldHash = Sha512.hash(oldSnapshot)
            val sourceCiphertext = File(vault.evidenceRaw, "pdf_source_${original.sha512}.pdf")
            val originalCiphertextHash = Sha512.hash(sourceCiphertext)

            // Identical evidence is re-imported, now with a usable page count and page text.
            assertEquals(original.sha512, vault.preservePdfSource(bytes))
            vault.storePdfExtraction("DOC002", original.fileName, original.sha512, coverage)
            val snapshots = vault.listPdfExtractions()
            val repaired = snapshots.first()
            assertEquals(2, snapshots.size)
            assertEquals(coverage, repaired.coverage)
            assertTrue(repaired.revision > original.revision)
            assertEquals(listOf(2, 3), repaired.coverage.remainingPages.map { it.page })
            assertEquals(oldHash, Sha512.hash(oldSnapshot))
            assertEquals(originalCiphertextHash, Sha512.hash(sourceCiphertext))
            assertEquals(original, vault.loadPdfExtraction(original.sha512))

            val result = service(vault).retry(repaired, listOf(2), null, now)
            assertNotNull(result.reportFile)
            assertEquals(OCR, result.record.coverage.pages[1].status)
            assertTrue(result.record.revision > repaired.revision)
            assertEquals(result.record, vault.listPdfExtractions().first())
            assertEquals(oldHash, Sha512.hash(oldSnapshot))
        }
    }

    @Test fun nativeRetryActionDoesNotReplaceTheActiveCaseAndIgnoresRepeatedTaps() {
        val vault = vault()
        val source = source(vault)
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val vm = com.verumomnis.forensic.ui.VerumViewModel(
            app, com.verumomnis.forensic.identity.IdentityService(app,
                com.verumomnis.forensic.identity.InMemoryIdentityKeyStore()), vault = vault,
            reportPdfWriter = { report, _ ->
                File(vault.reportsSealed, "${report.reference}.pdf").apply { writeText("Synthetic PDF writer boundary") }
            },
            pdfPageRetry = { _, previous, attempt ->
                calls.incrementAndGet()
                entered.countDown()
                check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                retryPdfPages(previous, attempt, 200) {
                    object : PdfOcrPages {
                        override val pageCount = 3
                        override fun recognise(index: Int) = "Recovered synthetic original page ${index + 1}."
                        override fun close() {}
                    }
                }
            }
        )
        vm.addDocument(EvidenceDocument("OTHER001", "another-case.txt", "text",
            "A separate active case record must remain untouched.", "f".repeat(128)))
        vm.runScan(now, "Active")
        val prior = vm.state.value
        vm.retryPdfPages(source, listOf(2))
        try {
            assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(vm.state.value.pdfRetryBusy)
            vm.retryPdfPages(source, listOf(3))
        } finally { release.countDown() }
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20)
        while (vm.state.value.pdfRetryBusy && System.nanoTime() < deadline) Thread.sleep(10)
        assertFalse(vm.state.value.pdfRetryBusy)
        assertEquals(1, calls.get())
        assertNotNull(vm.state.value.pdfRetryResult?.reportFile)
        assertEquals(prior.scanResult, vm.state.value.scanResult)
        assertEquals(prior.files, vm.state.value.files)
        assertEquals(prior.report, vm.state.value.report)
        assertEquals("another-case.txt", vm.state.value.files.single().name)
    }
}
