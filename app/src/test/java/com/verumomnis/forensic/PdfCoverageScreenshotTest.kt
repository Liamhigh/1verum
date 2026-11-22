package com.verumomnis.forensic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.verumomnis.forensic.engine.EvidenceDocument
import com.verumomnis.forensic.identity.IdentityService
import com.verumomnis.forensic.identity.InMemoryIdentityKeyStore
import com.verumomnis.forensic.model.*
import com.verumomnis.forensic.ui.*
import com.verumomnis.forensic.ui.theme.VerumOmnisTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfCoverageScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reportShowsCoverageWarningEvenWhenNoIndicatorsWereFound() {
        val app = RuntimeEnvironment.getApplication()
        val vm = VerumViewModel(app, IdentityService(app, InMemoryIdentityKeyStore()))
        vm.addDocument(EvidenceDocument("SYN001", "synthetic-mixed.pdf", "pdf",
            "Synthetic neutral readable text on the first page.", "a".repeat(128),
            extractionCoverage = PdfExtractionCoverage(totalPages = 4, pages = listOf(
                PageExtraction(1, PageReadStatus.TEXT_LAYER, "embedded_text", 49, 49),
                PageExtraction(2, PageReadStatus.UNREAD, "ocr_returned_no_text", ocrAttempted = true),
                PageExtraction(3, PageReadStatus.FAILED, "page_render_or_ocr_failed", ocrAttempted = true),
                PageExtraction(4, PageReadStatus.SKIPPED, "ocr_page_limit")
            ))
        ))
        val now = Instant.parse("2026-11-22T12:00:00Z")
        vm.runScan(now, "SyntheticCoverage")
        vm.generateReport("SyntheticCoverage", now)
        compose.setContent { VerumOmnisTheme { ReportScreen(vm.state.value, vm) } }
        compose.onAllNodesWithText("WARNING — incomplete PDF coverage.", substring = true)[0]
            .performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/screenshots/pdf-coverage-warning.png")
    }
}
