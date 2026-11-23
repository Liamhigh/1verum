package com.verumomnis.forensic

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.verumomnis.forensic.engine.*
import com.verumomnis.forensic.identity.IdentityService
import com.verumomnis.forensic.identity.InMemoryIdentityKeyStore
import com.verumomnis.forensic.model.*
import com.verumomnis.forensic.ui.PdfPageReviewCard
import com.verumomnis.forensic.ui.UiState
import com.verumomnis.forensic.ui.VerumViewModel
import com.verumomnis.forensic.ui.theme.VerumOmnisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h1000dp-mdpi")
class RetryPairShareUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pairActionIsShownOnlyWithBothFilesAndMissingPdfOrJsonHasAnExplanation() {
        val app = RuntimeEnvironment.getApplication()
        val now = Instant.parse("2026-11-22T12:00:00Z")
        val attempt = PdfRetryAttempt("share-ui-attempt", now.toString(), listOf(1))
        val coverage = PdfExtractionCoverage(
            totalPages = 1,
            pages = listOf(PageExtraction(1, PageReadStatus.OCR, "on_device_ocr_retry",
                extractedChars = 20, extractedText = "Synthetic OCR text.")),
            retryAttempt = attempt
        )
        val record = DocumentExtractionCoverage("DOC001", "source.pdf", "a".repeat(128), coverage)
        val report = ReportGenerator.generate(ForensicService.scan(listOf(EvidenceDocument(
            record.evidenceId, record.fileName, "pdf", "Synthetic OCR text.",
            record.sha512, extractionCoverage = coverage
        )), now).findings, "OCR retry ${attempt.id}", now)
        val pdf = File.createTempFile("share-ui-", ".pdf", app.cacheDir).apply { writeText("%PDF-1.4 fixture") }
        val json = File.createTempFile("share-ui-", ".json", app.cacheDir).apply { writeText("{}") }
        val result = PdfRetryResult(record, report, pdf, "Retry ready.", json)
        val state = mutableStateOf(UiState(
            pdfReviewRecords = listOf(record), pdfRetryRecoveries = listOf(result)
        ))
        val vm = VerumViewModel(app, IdentityService(app, InMemoryIdentityKeyStore()))
        compose.setContent { VerumOmnisTheme { PdfPageReviewCard(state.value, vm) } }
        compose.onNodeWithText("SHARE RETRY PDF + FINDINGS JSON").assertExists()

        compose.runOnIdle {
            assertTrue(json.delete())
            state.value = state.value.copy(pdfRetryMessage = "JSON removed")
        }
        compose.onNodeWithText("SHARE RETRY PDF + FINDINGS JSON").assertDoesNotExist()
        compose.onNodeWithText("No other JSON file will be substituted.", substring = true).assertExists()
        compose.runOnIdle {
            assertTrue(pdf.delete())
            state.value = state.value.copy(pdfRetryMessage = "PDF removed")
        }
        compose.onNodeWithText("SHARE RETRY PDF + FINDINGS JSON").assertDoesNotExist()
        compose.onNodeWithText("The retry PDF is missing or unreadable.", substring = true).assertExists()
    }
}
