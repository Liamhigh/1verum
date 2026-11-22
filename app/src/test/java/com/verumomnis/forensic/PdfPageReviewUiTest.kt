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
}
