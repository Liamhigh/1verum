package com.verumomnis.forensic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.collectAsState
import com.github.takahirom.roborazzi.captureRoboImage
import com.verumomnis.forensic.model.*
import com.verumomnis.forensic.ui.PdfPageReviewDialog
import com.verumomnis.forensic.ui.theme.VerumOmnisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfPageReviewUiTest {
    @get:Rule val compose = createComposeRule()
    private val record = DocumentExtractionCoverage("DOC001", "synthetic-review.pdf", "a".repeat(128),
        PdfExtractionCoverage(4, listOf(
            PageExtraction(1, PageReadStatus.TEXT_LAYER, "embedded_text", 40, 40, extractedText = "Synthetic readable record, preserved unchanged."),
            PageExtraction(2, PageReadStatus.UNREAD, "ocr_returned_no_text"),
            PageExtraction(3, PageReadStatus.FAILED, "page_render_or_ocr_failed"),
            PageExtraction(4, PageReadStatus.SKIPPED, "ocr_page_limit")
        )))

    @Test fun selectionContainsOnlyExplicitlyChosenRemainingPages() {
        var chosen: List<Int>? = null
        compose.setContent { VerumOmnisTheme {
            PdfPageReviewDialog(record, false, {}, { chosen = it })
        } }
        compose.onNodeWithText("Start fresh OCR retry").assertIsNotEnabled()
        val checkboxes = compose.onAllNodes(isToggleable())
        checkboxes[0].assertIsNotEnabled()
        checkboxes[2].performScrollTo().performClick()
        checkboxes[3].performScrollTo().performClick()
        compose.onNodeWithText("2 selected", substring = true).assertExists()
        compose.onNode(isRoot() and hasAnyDescendant(hasText("Review PDF pages")))
            .captureRoboImage("build/screenshots/pdf-page-review-retry.png")
        compose.onNodeWithText("Start fresh OCR retry").performClick()
        assertEquals(listOf(3, 4), chosen)
    }

    @Test fun unknownPageCountExplainsReimportRatherThanAllowingAnUnboundedRetry() {
        compose.setContent { VerumOmnisTheme {
            PdfPageReviewDialog(record.copy(coverage = PdfExtractionCoverage()), false, {}, { error("must not retry") })
        } }
        compose.onNodeWithText("Re-import the original PDF", substring = true).assertExists()
        compose.onNodeWithText("Start fresh OCR retry").assertIsNotEnabled()
    }

    @Test fun reviewUsesReimportedCoverageAndAllowsSelectionInsteadOfTheOlderUnknownCount() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val vault = com.verumomnis.forensic.vault.EvidenceVault(
            java.io.File(app.cacheDir, "reimport-review"),
            masterKeyProvider = { javax.crypto.spec.SecretKeySpec(ByteArray(32) { 3 }, "AES") }
        )
        val bytes = "%PDF synthetic reimport fixture".toByteArray()
        val hash = vault.preservePdfSource(bytes)
        vault.storePdfExtraction("DOC001", record.fileName, hash, PdfExtractionCoverage())
        vault.storePdfExtraction("DOC001", record.fileName, hash, record.coverage)
        val vm = com.verumomnis.forensic.ui.VerumViewModel(app,
            com.verumomnis.forensic.identity.IdentityService(app,
                com.verumomnis.forensic.identity.InMemoryIdentityKeyStore()), vault = vault)
        compose.setContent { VerumOmnisTheme {
            com.verumomnis.forensic.ui.PdfPageReviewCard(vm.state.collectAsState().value, vm)
        } }
        compose.waitUntil(10_000) { vm.state.value.pdfReviewRecords.size == 2 }
        compose.onNodeWithText("REVIEW PAGES: ${record.fileName.uppercase()}").performClick()
        compose.onNodeWithText("Page 2 · UNREAD").assertExists()
        val checkbox = compose.onNodeWithContentDescription("Select original page 2, UNREAD")
        checkbox.assertIsEnabled().performScrollTo().performClick()
        compose.onNodeWithText("Start fresh OCR retry").assertIsEnabled()
    }

    @Test fun savedPageTextLoadsReadOnlyAndShowsItsExtractionSourceAfterReload() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val previewAttemptId = "preview-${UUID.randomUUID()}"
        val savedOcrText = "OCR text segment. ".repeat(32)
        val previewRecord = record.copy(
            fileName = "saved-preview.pdf",
            coverage = PdfExtractionCoverage(
                totalPages = 2,
                pages = listOf(
                    PageExtraction(
                        page = 1,
                        status = PageReadStatus.TEXT_LAYER,
                        reason = "embedded_text",
                        textLayerChars = 36,
                        extractedChars = 36,
                        extractedText = "Saved embedded PDF text from page one."
                    ),
                    PageExtraction(
                        page = 2,
                        status = PageReadStatus.OCR,
                        reason = "on_device_ocr",
                        extractedChars = savedOcrText.length,
                        ocrAttempted = true,
                        extractedText = savedOcrText
                    )
                ),
                retryAttempt = PdfRetryAttempt(
                    id = previewAttemptId,
                    createdAt = "2026-11-22T00:00:00Z",
                    selectedPages = listOf(2)
                )
            )
        )
        val vault = com.verumomnis.forensic.vault.EvidenceVault(
            java.io.File(app.cacheDir, "saved-page-text-preview-$previewAttemptId"),
            masterKeyProvider = { javax.crypto.spec.SecretKeySpec(ByteArray(32) { 4 }, "AES") }
        )
        val hash = vault.preservePdfSource("%PDF saved preview fixture".toByteArray())
        vault.storePdfExtraction("DOC001", previewRecord.fileName, hash, previewRecord.coverage)
        var ocrCalls = 0
        val vm = com.verumomnis.forensic.ui.VerumViewModel(
            app,
            com.verumomnis.forensic.identity.IdentityService(
                app,
                com.verumomnis.forensic.identity.InMemoryIdentityKeyStore()
            ),
            vault = vault,
            pdfPageRetry = { _, _, _, _, _ ->
                ocrCalls += 1
                error("Previewing saved text must not rerun OCR")
            }
        )

        compose.setContent { VerumOmnisTheme {
            com.verumomnis.forensic.ui.PdfPageReviewCard(vm.state.collectAsState().value, vm)
        } }
        compose.waitUntil(10_000) {
            vm.state.value.pdfReviewRecords.any { it.fileName == previewRecord.fileName }
        }
        assertEquals(1, vm.state.value.pdfRetryRecoveries.size)
        assertTrue(vm.state.value.pdfRetryRecoveries.single().canResumeReport)
        compose.onNodeWithText("RESUME REPORT GENERATION").assertExists()
        compose.onNodeWithText("REVIEW PAGES: ${previewRecord.fileName.uppercase()}").performClick()
        compose.onNodeWithText("Text source: Embedded PDF text").assertExists()
        compose.onNodeWithText("Saved embedded PDF text from page one.").assertExists()
        compose.onNodeWithText("Text source: On-device OCR").assertExists()
        compose.onNodeWithText("Show full saved text").performScrollTo().performClick()
        compose.onNodeWithText(savedOcrText).assertExists()
        assertEquals(0, ocrCalls)
    }

    @Test fun activePdfRetryShowsCancelActionAndSavesAStoppedAttempt() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val vault = com.verumomnis.forensic.vault.EvidenceVault(
            java.io.File(app.cacheDir, "cancel-review-${UUID.randomUUID()}"),
            masterKeyProvider = { javax.crypto.spec.SecretKeySpec(ByteArray(32) { 5 }, "AES") }
        )
        val originalBytes = "%PDF synthetic cancel review fixture".toByteArray()
        val hash = vault.preservePdfSource(originalBytes)
        val coverage = PdfExtractionCoverage(2, listOf(
            PageExtraction(1, PageReadStatus.TEXT_LAYER, "embedded_text", 42, 42,
                extractedText = "Synthetic readable source page one."),
            PageExtraction(2, PageReadStatus.UNREAD, "ocr_returned_no_text")
        ))
        vault.storePdfExtraction("DOC001", "cancel-review.pdf", hash, coverage)
        val enteredRetry = java.util.concurrent.CountDownLatch(1)
        val finishRetrySetup = java.util.concurrent.CountDownLatch(1)
        val vm = com.verumomnis.forensic.ui.VerumViewModel(
            app,
            com.verumomnis.forensic.identity.IdentityService(
                app, com.verumomnis.forensic.identity.InMemoryIdentityKeyStore()
            ),
            vault = vault,
            pdfPageRetry = { _, previous, attempt, isCancelled, onProgress ->
                enteredRetry.countDown()
                check(finishRetrySetup.await(10, java.util.concurrent.TimeUnit.SECONDS))
                com.verumomnis.forensic.engine.retryPdfPages(
                    previous, attempt, 200, isCancellationRequested = isCancelled, onProgress = onProgress
                ) { error("A cancelled retry must not open OCR.") }
            }
        )
        compose.setContent { VerumOmnisTheme {
            com.verumomnis.forensic.ui.PdfPageReviewCard(vm.state.collectAsState().value, vm)
        } }

        compose.waitUntil(10_000) { vm.state.value.pdfReviewRecords.any { it.sha512 == hash } }
        compose.onNodeWithText("REVIEW PAGES: CANCEL-REVIEW.PDF").performClick()
        compose.onNodeWithContentDescription("Select original page 2, UNREAD")
            .performScrollTo().performClick()
        compose.onNodeWithText("Start fresh OCR retry").performClick()
        assertTrue(enteredRetry.await(10, java.util.concurrent.TimeUnit.SECONDS))
        compose.onNodeWithText("CANCEL OCR RETRY").assertExists().performClick()
        assertTrue(vm.state.value.pdfRetryCancelRequested)
        finishRetrySetup.countDown()

        compose.waitUntil(20_000) { !vm.state.value.pdfRetryBusy }
        val result = requireNotNull(vm.state.value.pdfRetryResult)
        assertTrue(result.record.coverage.retryAttempt?.cancelled == true)
        assertTrue(result.record.coverage.retryAttempt?.processedPages.isNullOrEmpty())
        assertNull(result.report)
        assertTrue(result.record.coverage.summary().contains("were not read"))
        assertArrayEquals(originalBytes, vault.readEvidence("pdf_source_$hash.pdf"))
    }
}
