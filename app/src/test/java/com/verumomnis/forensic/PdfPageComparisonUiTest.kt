package com.verumomnis.forensic

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.verumomnis.forensic.identity.IdentityService
import com.verumomnis.forensic.identity.InMemoryIdentityKeyStore
import com.verumomnis.forensic.model.*
import com.verumomnis.forensic.ui.*
import com.verumomnis.forensic.ui.theme.VerumOmnisTheme
import com.verumomnis.forensic.vault.EvidenceVault
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfPageComparisonUiTest {
    @get:Rule val compose = createComposeRule()
    private val coverage = PdfExtractionCoverage(2, listOf(
        PageExtraction(1, PageReadStatus.TEXT_LAYER, "embedded_text", extractedChars = 20,
            extractedText = "Saved embedded text."),
        PageExtraction(2, PageReadStatus.OCR, "on_device_ocr", extractedChars = 580,
            extractedText = "Exact saved OCR.\n  ".repeat(30))
    ))

    @Test fun comparingThenChangingPageShowsMatchingImageAndFullSavedTextWithoutOcr() {
        val app = RuntimeEnvironment.getApplication()
        val vaultRoot = File(app.cacheDir, "comparison-ui-${System.nanoTime()}")
        val vault = EvidenceVault(vaultRoot,
            masterKeyProvider = { SecretKeySpec(ByteArray(32) { 7 }, "AES") })
        val bytes = comparisonPdf()
        val hash = vault.preservePdfSource(bytes)
        vault.storePdfExtraction("DOC001", "compare.pdf", hash, coverage)
        val before = vaultRoot.walkTopDown().filter { it.isFile }
            .associate { it.relativeTo(vaultRoot).path to it.readBytes().toList() }
        var ocrCalls = 0
        val vm = VerumViewModel(app, IdentityService(app, InMemoryIdentityKeyStore()), vault = vault,
            pdfPageRetry = { _, _, _, _ -> ocrCalls++; error("Preview must never run OCR") },
            pdfPagePreview = com.verumomnis.forensic.pdf.PdfPagePreview(vault, app.cacheDir, ::comparisonRaster))
        compose.setContent { VerumOmnisTheme { PdfPageReviewCard(vm.state.collectAsState().value, vm) } }
        compose.waitUntil(10_000) { vm.state.value.pdfReviewRecords.any { it.sha512 == hash } }
        compose.onNodeWithText("REVIEW PAGES: COMPARE.PDF").performClick()
        compose.onNodeWithText("Compare original page 2").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Original PDF page 2").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Original page 2 · saved text").assertExists()
        compose.onNodeWithText(coverage.pages[1].extractedText!!).assertExists()
        compose.onNodeWithText("Text source: On-device OCR").assertExists()
        File("build/screenshots").mkdirs()
        compose.onNode(isRoot() and hasAnyDescendant(hasText("Original page 2 · saved text")))
            .captureRoboImage("build/screenshots/pdf-page-comparison.png")
        compose.onNodeWithText("Back to pages").performClick()
        compose.onNodeWithText("Compare original page 1").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Original PDF page 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Saved embedded text.").assertExists()
        compose.onNodeWithText("Text source: Embedded PDF text").assertExists()
        compose.onNodeWithContentDescription("Original PDF page 2").assertDoesNotExist()
        compose.onNodeWithText("Back to pages").performClick()
        compose.onNodeWithText("Close").performClick()
        assertEquals(0, ocrCalls)
        assertFalse(vm.state.value.pdfRetryBusy)
        // ViewModel startup may create case-memory metadata; every pre-existing
        // evidence, manifest and extraction file must remain byte-for-byte identical.
        before.forEach { (path, saved) ->
            assertEquals(path, saved, File(vaultRoot, path).readBytes().toList())
        }
        assertEquals(1, vault.listPdfExtractions().size)
        assertArrayEquals(bytes, vault.readEvidence("pdf_source_$hash.pdf"))
        assertTrue(app.cacheDir.listFiles().orEmpty().none { it.name.startsWith("vo_page_preview_") })
    }

    @Test fun missingOriginalShowsHonestErrorWhileKeepingSavedTextReadable() {
        val record = DocumentExtractionCoverage("DOC001", "missing.pdf", "a".repeat(128), coverage)
        compose.setContent { VerumOmnisTheme {
            PdfPageReviewDialog(record, false, {}, loadPreview = { _, _ -> error("missing original") },
                onRetry = { error("Comparison must not start a retry") })
        } }
        compose.onNodeWithText("Compare original page 1").performScrollTo().performClick()
        compose.onNodeWithText("Original page unavailable.", substring = true).assertExists()
        compose.onNodeWithText("Saved embedded text.").assertExists()
        compose.onNodeWithText("Back to pages").performClick()
        compose.onNodeWithText("Review PDF pages").assertExists()
    }
}
