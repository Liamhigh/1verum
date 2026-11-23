package com.verumomnis.forensic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.verumomnis.forensic.crypto.Sha512
import com.verumomnis.forensic.identity.IdentityService
import com.verumomnis.forensic.identity.InMemoryIdentityKeyStore
import com.verumomnis.forensic.model.*
import com.verumomnis.forensic.ui.PdfPageReviewCard
import com.verumomnis.forensic.ui.SavedPdfPageSearchPanel
import com.verumomnis.forensic.ui.VerumViewModel
import com.verumomnis.forensic.ui.theme.VerumOmnisTheme
import com.verumomnis.forensic.vault.EvidenceVault
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SavedPdfPageSearchUiTest {
    @get:Rule val compose = createComposeRule()
    @Before fun boundUiIdleWait() {
        androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
    }

    @Test fun resultOpensExactSavedRetryAtOriginalPageWithoutRunningOcrOrChangingEvidence() {
        val app = RuntimeEnvironment.getApplication()
        val root = File(app.cacheDir, "search-${UUID.randomUUID()}")
        val vault = EvidenceVault(root, masterKeyProvider = { SecretKeySpec(ByteArray(32) { 8 }, "AES") })
        val originalBytes = "%PDF synthetic immutable search fixture".toByteArray()
        val hash = vault.preservePdfSource(originalBytes)
        val originalCoverage = PdfExtractionCoverage(12, (1..12).map { number ->
            val text = when (number) {
                2 -> "Ada Lovelace appears in the original saved extraction."
                12 -> null
                else -> "Synthetic neutral original page $number."
            }
            PageExtraction(
                number, if (text == null) PageReadStatus.UNREAD else PageReadStatus.TEXT_LAYER,
                if (text == null) "ocr_returned_no_text" else "embedded_text",
                extractedChars = text?.length ?: 0, extractedText = text
            )
        })
        vault.storePdfExtraction("DOC001", "saved-source.pdf", hash, originalCoverage)
        val originalRecord = vault.listPdfExtractions().single()
        val retryText = "Preserved context. ".repeat(25) + "Ada\nLovelace was named on original page twelve."
        val attempt = PdfRetryAttempt("saved-search-attempt", "2026-11-23T00:00:00Z", listOf(12))
        val savedRetry = vault.storePdfRetry(originalRecord.copy(
            coverage = originalCoverage.copy(
                retryAttempt = attempt,
                pages = originalCoverage.pages.map {
                    if (it.page != 12) it else it.copy(
                        status = PageReadStatus.OCR, reason = "on_device_ocr_retry",
                        extractedChars = retryText.length, extractedText = retryText, ocrAttempted = true
                    )
                }
            )
        ))
        val savedBefore = vault.listPdfExtractions()
        val snapshotHashes = vault.findings.listFiles().orEmpty().filter { it.isFile }
            .associateWith { Sha512.hash(it) }
        var ocrCalls = 0
        val vm = VerumViewModel(
            app, IdentityService(app, InMemoryIdentityKeyStore()), vault = vault,
            pdfPageRetry = { _, _, _, _ -> ocrCalls++; error("Search must never run OCR") }
        )
        compose.setContent { VerumOmnisTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PdfPageReviewCard(vm.state.collectAsState().value, vm)
            }
        } }
        compose.waitUntil(10_000) { vm.state.value.pdfReviewRecords.size == 2 }
        compose.onNodeWithText("SEARCH SAVED PAGE TEXT").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("Ada Lovelace")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("3 matching page(s) across 2 saved extraction(s)").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Retry ${attempt.id}", substring = true).assertExists()
        compose.onNode(isRoot() and hasAnyDescendant(hasText("Search saved PDF text")))
            .captureRoboImage("build/screenshots/saved-pdf-search-results.png")
        compose.onNodeWithTag("saved-pdf-search-results").performScrollTo().performScrollToNode(
            hasText("Original extraction · revision ${originalRecord.revision}")
        )
        compose.onNodeWithText("Original extraction · revision ${originalRecord.revision}").assertExists()
        val target = "Open ${savedRetry.fileName}, retry ${attempt.id}, original page 12"
        compose.onNodeWithTag("saved-pdf-search-results").performScrollToNode(hasContentDescription(target))
        compose.onNodeWithContentDescription(target).performClick()

        compose.onNodeWithText("Review PDF pages").assertExists()
        compose.onNodeWithText("Saved retry attempt: ${attempt.id}").assertExists()
        compose.onNodeWithText("Search result · original page 12").assertExists()
        compose.onNodeWithText("Page 12 · OCR").assertIsDisplayed()
        compose.onNodeWithText(retryText).assertExists()
        compose.onNodeWithText("0 selected", substring = true).assertExists()
        compose.onNodeWithText("Start fresh OCR retry").assertIsNotEnabled()
        compose.onNode(isRoot() and hasAnyDescendant(hasText("Review PDF pages")))
            .captureRoboImage("build/screenshots/saved-pdf-search-open-page.png")
        assertEquals(0, ocrCalls)
        assertEquals(savedBefore, vault.listPdfExtractions())
        snapshotHashes.forEach { (file, expected) -> assertEquals(expected, Sha512.hash(file)) }
        assertArrayEquals(originalBytes, vault.readEvidence("pdf_source_$hash.pdf"))
    }

    @Test fun emptyAndNoMatchStatesExplainSavedTextLimitsAndDoNotOpenAnyPage() {
        val record = DocumentExtractionCoverage(
            "DOC001", "empty-pages.pdf", "c".repeat(128),
            PdfExtractionCoverage(1, listOf(PageExtraction(1, PageReadStatus.UNREAD, "ocr_returned_no_text")))
        )
        var opened = false
        compose.setContent { VerumOmnisTheme {
            SavedPdfPageSearchPanel(listOf(record)) { _, _ -> opened = true }
        } }
        compose.onNodeWithText("Enter a name or phrase", substring = true).assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("Absent person")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("No matches in saved page text.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Missing matches do not prove", substring = true).assertExists()
        assertFalse(opened)
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNodeWithText("Enter a name or phrase", substring = true).assertExists()
    }
}
