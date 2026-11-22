package com.verumomnis.forensic

import com.verumomnis.forensic.crypto.Sha512
import com.verumomnis.forensic.engine.*
import com.verumomnis.forensic.model.*
import com.verumomnis.forensic.model.PageReadStatus.*
import com.verumomnis.forensic.pdf.SealedOutputNames
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
        }, writePdf = { report, sourceName ->
            if (failExport) error("Synthetic export boundary failure")
            SealedOutputNames.createUniqueFile(
                vault.reportsSealed,
                SealedOutputNames.forensicReport(sourceName, report.reference)
            ).apply {
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
        val recovered = service(vault).recoverSaved(result.record)
        assertEquals(PdfRetryRecoveryStage.FINDINGS_READY, recovered.stage)
        assertEquals(result.report!!.reference, recovered.report!!.reference)
    }

    @Test fun interruptedExtractionCanResumeAfterReloadWithoutRepeatingOcr() {
        val vault = vault()
        val source = source(vault)
        val attempt = PdfRetryAttempt(
            id = "resume-attempt-123",
            createdAt = now.toString(),
            selectedPages = listOf(2),
            priorReportReference = "prior-report"
        )
        val savedCoverage = coverage.copy(
            pages = coverage.pages.map { page ->
                if (page.page == 2) page.copy(
                    status = OCR,
                    reason = "on_device_ocr_retry",
                    extractedChars = 33,
                    extractedText = "Recovered text from original page two.",
                    ocrAttempted = true
                ) else page
            },
            retryAttempt = attempt
        )
        val savedRecord = vault.storePdfRetry(source.copy(coverage = savedCoverage))
        val ocrCalls = java.util.concurrent.atomic.AtomicInteger()
        val recoveredService = PdfRetryService(
            vault = vault,
            cacheDir = app.cacheDir,
            extract = { _, _, _ -> ocrCalls.incrementAndGet(); error("OCR must not run during recovery.") },
            writePdf = { report, name ->
                SealedOutputNames.createUniqueFile(
                    vault.reportsSealed,
                    SealedOutputNames.forensicReport(name, report.reference)
                ).apply { writeText("%PDF-1.4 recovered retry PDF") }
            }
        )

        val extractionOnly = recoveredService.recoverSaved(vault.listPdfExtractions().first())
        assertEquals(PdfRetryRecoveryStage.EXTRACTION_ONLY, extractionOnly.stage)
        assertTrue(extractionOnly.canResumeReport)
        assertTrue(extractionOnly.message.contains("OCR will not run again"))

        val ready = recoveredService.resumeReport(savedRecord)
        assertEquals(PdfRetryRecoveryStage.PDF_READY, ready.stage)
        assertEquals(0, ocrCalls.get())
        assertEquals(attempt.id, ready.report!!.extractionCoverage.single().coverage.retryAttempt!!.id)
        assertTrue(ready.report!!.title.contains("NEW PDF-only OCR Retry Report"))
        assertTrue(File(ready.report!!.findingsJsonPath).isFile)
        assertTrue(ready.reportFile!!.name.startsWith("original_forensic_report_"))
        assertEquals(2, vault.listPdfExtractions().size)
    }

    @Test fun duplicateRetryCoverageWriteFailureDoesNotReplaceSavedAttempt() {
        val vault = vault()
        val source = source(vault)
        val attempted = source.copy(coverage = coverage.copy(
            retryAttempt = PdfRetryAttempt("duplicate-attempt", now.toString(), listOf(2))
        ))
        val first = vault.storePdfRetry(attempted)
        val before = File(vault.findings, "pdf_retry_${source.sha512}_duplicate-attempt.json").readBytes()

        assertThrows(java.nio.file.FileAlreadyExistsException::class.java) {
            vault.storePdfRetry(attempted.copy(coverage = attempted.coverage.copy(
                pages = attempted.coverage.pages.map { it.copy(extractedText = "changed") }
            )))
        }
        assertEquals(first, vault.listPdfExtractions().first())
        assertArrayEquals(before, File(vault.findings, "pdf_retry_${source.sha512}_duplicate-attempt.json").readBytes())
        assertArrayEquals(bytes, vault.readEvidence("pdf_source_${source.sha512}.pdf"))
    }

    @Test fun findingsRetryRecoveryNeverOverwritesAnEarlierJsonSnapshot() {
        val vault = vault()
        val first = vault.storeFindingUnique("ocr_retry_attempt_findings.json", """{"version":1}""")
        val second = vault.storeFindingUnique("ocr_retry_attempt_findings.json", """{"version":2}""")
        assertEquals("ocr_retry_attempt_findings.json", first.name)
        assertEquals("ocr_retry_attempt_findings_2.json", second.name)
        assertEquals("""{"version":1}""", first.readText())
        assertEquals("""{"version":2}""", second.readText())
        assertThrows(IllegalArgumentException::class.java) {
            vault.storeFindingUnique("../escape.json", """{}""")
        }
    }

    @Test fun viewModelRecoversSavedTextAndPdfExportAfterProcessRecreation() {
        val vault = vault()
        val original = source(vault)
        val attempt = PdfRetryAttempt(
            id = "process-recovery-42",
            createdAt = now.toString(),
            selectedPages = listOf(2),
            priorReportReference = "older-sealed-report"
        )
        val savedCoverage = coverage.copy(
            pages = coverage.pages.map { page ->
                if (page.page == 2) page.copy(
                    status = OCR,
                    reason = "on_device_ocr_retry",
                    extractedChars = 29,
                    extractedText = "Saved recognition from page two.",
                    ocrAttempted = true
                ) else page
            },
            retryAttempt = attempt
        )
        val retryRecord = vault.storePdfRetry(original.copy(coverage = savedCoverage))
        var failPdfWrite = true
        val ocrCalls = java.util.concurrent.atomic.AtomicInteger()

        fun createViewModel() = com.verumomnis.forensic.ui.VerumViewModel(
            application = app,
            identityService = com.verumomnis.forensic.identity.IdentityService(
                app, com.verumomnis.forensic.identity.InMemoryIdentityKeyStore()
            ),
            vault = vault,
            reportPdfWriter = { report, sourceName ->
                if (failPdfWrite) error("Simulated low-storage failure")
                SealedOutputNames.createUniqueFile(
                    vault.reportsSealed,
                    SealedOutputNames.forensicReport(sourceName, report.reference)
                ).apply { writeText("%PDF-1.4 recovered after restart") }
            },
            pdfPageRetry = { _, _, _ ->
                ocrCalls.incrementAndGet()
                error("Saved retry recovery must not repeat OCR.")
            }
        )

        fun awaitRecovery(vm: com.verumomnis.forensic.ui.VerumViewModel) {
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20)
            while (
                vm.state.value.pdfRetryRecoveries.none {
                    it.record.coverage.retryAttempt?.id == attempt.id
                } && System.nanoTime() < deadline
            ) Thread.sleep(10)
            assertTrue(vm.state.value.pdfRetryRecoveries.any {
                it.record.coverage.retryAttempt?.id == attempt.id
            })
        }

        val firstProcess = createViewModel()
        firstProcess.loadPdfPageReview()
        awaitRecovery(firstProcess)
        assertEquals(PdfRetryRecoveryStage.EXTRACTION_ONLY, firstProcess.state.value.pdfRetryRecoveries.single().stage)
        firstProcess.resumePdfRetryReport(retryRecord)
        val firstDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20)
        while (firstProcess.state.value.pdfRetryBusy && System.nanoTime() < firstDeadline) Thread.sleep(10)
        assertEquals(PdfRetryRecoveryStage.FINDINGS_READY, firstProcess.state.value.pdfRetryRecoveries.single().stage)
        val savedReport = requireNotNull(firstProcess.state.value.pdfRetryRecoveries.single().report)
        val snapshotFile = vault.reportsSealed.listFiles()!!.single { it.name.endsWith(".json") }
        val snapshotBytes = snapshotFile.readBytes()

        // A second ViewModel represents process recreation; it reloads both saved artifacts.
        val restartedProcess = createViewModel()
        restartedProcess.loadPdfPageReview()
        awaitRecovery(restartedProcess)
        val reloaded = restartedProcess.state.value.pdfRetryRecoveries.single()
        assertEquals(PdfRetryRecoveryStage.FINDINGS_READY, reloaded.stage)
        assertEquals(savedReport.reference, reloaded.report!!.reference)
        assertTrue(reloaded.message.contains("PDF can be exported again"))

        failPdfWrite = false
        restartedProcess.retryPdfReviewExport(retryRecord)
        val secondDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20)
        while (restartedProcess.state.value.pdfRetryBusy && System.nanoTime() < secondDeadline) Thread.sleep(10)
        val completed = restartedProcess.state.value.pdfRetryRecoveries.single()
        assertEquals(PdfRetryRecoveryStage.PDF_READY, completed.stage)
        assertEquals(savedReport.reference, completed.report!!.reference)
        assertTrue(completed.reportFile!!.isFile)
        assertEquals(0, ocrCalls.get())
        assertArrayEquals(snapshotBytes, snapshotFile.readBytes())
        assertEquals(1, vault.listReports().size)
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
            reportPdfWriter = { report, sourceName ->
                SealedOutputNames.createUniqueFile(
                    vault.reportsSealed,
                    SealedOutputNames.forensicReport(sourceName, report.reference)
                ).apply { writeText("Synthetic PDF writer boundary") }
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
