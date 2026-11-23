package com.verumomnis.forensic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.verumomnis.forensic.engine.*
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
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfRetryProgressUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun liveProgressWaitsForEachPageAndCancellationPreservesUnstartedPagesAndEarlierEvidence() {
        val app = RuntimeEnvironment.getApplication()
        val vault = EvidenceVault(
            File(app.cacheDir, "progress-${UUID.randomUUID()}"),
            masterKeyProvider = { SecretKeySpec(ByteArray(32) { 6 }, "AES") }
        )
        val originalBytes = "%PDF synthetic immutable progress fixture".toByteArray()
        val hash = vault.preservePdfSource(originalBytes)
        val coverage = PdfExtractionCoverage(4, (1..4).map {
            if (it == 1) PageExtraction(it, PageReadStatus.TEXT_LAYER, "embedded_text",
                extractedChars = 31, extractedText = "Synthetic original page one text.")
            else PageExtraction(it, PageReadStatus.SKIPPED, "ocr_page_limit")
        })
        vault.storePdfExtraction("DOC001", "progress-source.pdf", hash, coverage)
        val source = vault.listPdfExtractions().single()
        val oldReportBytes = "Synthetic prior sealed report bytes".toByteArray()
        vault.storeSealedReport("prior-report.pdf", oldReportBytes)
        val oldReport = File(vault.reportsSealed, "prior-report.pdf")
        val enteredPage2 = CountDownLatch(1)
        val enteredPage3 = CountDownLatch(1)
        val finishPage2 = CountDownLatch(1)
        val finishPage3 = CountDownLatch(1)
        val calls = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val vm = VerumViewModel(
            app, IdentityService(app, InMemoryIdentityKeyStore()), vault = vault,
            reportPdfWriter = { _, _ -> error("Cancelled OCR must not generate a report") },
            pdfPageRetry = { _, previous, attempt, isCancelled, onProgress ->
                retryPdfPages(previous, attempt, 200,
                    isCancellationRequested = isCancelled, onProgress = onProgress
                ) {
                    object : PdfOcrPages {
                        override val pageCount = 4
                        override fun recognise(index: Int): String {
                            calls += index + 1
                            when (index + 1) {
                                2 -> { enteredPage2.countDown(); check(finishPage2.await(30, TimeUnit.SECONDS)) }
                                3 -> { enteredPage3.countDown(); check(finishPage3.await(30, TimeUnit.SECONDS)) }
                                else -> error("Unstarted page must not be recognised")
                            }
                            return "Synthetic recovered original page ${index + 1}."
                        }
                        override fun close() {}
                    }
                }
            }
        )
        compose.setContent { VerumOmnisTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PdfPageReviewCard(vm.state.collectAsState().value, vm)
            }
        } }
        compose.waitUntil(10_000) { vm.state.value.pdfReviewRecords.isNotEmpty() }
        try {
            compose.runOnIdle { vm.retryPdfPages(source, listOf(4, 2, 3)) }
            assertTrue(enteredPage2.await(10, TimeUnit.SECONDS))
            compose.onNodeWithText("Completed page attempts: 0 of 3").assertExists()
            compose.onNodeWithText("Reading original page 2").assertExists()
            assertEquals(PdfRetryProgress(0, 3, 2), vm.state.value.pdfRetryProgress)

            finishPage2.countDown()
            assertTrue(enteredPage3.await(10, TimeUnit.SECONDS))
            compose.onNodeWithText("Completed page attempts: 1 of 3").assertExists()
            compose.onNodeWithText("Reading original page 3").assertExists()
            compose.onNodeWithText("Finished attempts may still contain unread or failed pages.").assertExists()
            compose.onNode(isRoot()).captureRoboImage("build/screenshots/pdf-retry-live-progress.png")
            compose.onNodeWithText("CANCEL OCR RETRY").performScrollTo().performClick()
            assertTrue(vm.state.value.pdfRetryCancelRequested)
            assertEquals(PdfRetryProgress(1, 3, 3), vm.state.value.pdfRetryProgress)
            finishPage3.countDown()
            compose.waitUntil(20_000) { !vm.state.value.pdfRetryBusy }
            val result = requireNotNull(vm.state.value.pdfRetryResult)
            assertEquals(listOf(2, 3), result.record.coverage.retryAttempt!!.processedPages)
            assertTrue(result.record.coverage.retryAttempt!!.cancelled)
            assertEquals(listOf(2, 3), calls.toList())
            assertEquals(coverage.pages[3], result.record.coverage.pages[3])
            assertNull(vm.state.value.pdfRetryProgress)
            assertTrue(vm.state.value.pdfRetrySourceName.isEmpty())
            assertArrayEquals(originalBytes, vault.readEvidence("pdf_source_$hash.pdf"))
            assertArrayEquals(oldReportBytes, oldReport.readBytes())
            assertTrue(vault.listPdfExtractions().contains(source))
            assertEquals(hash, result.record.sha512)
        } finally {
            finishPage2.countDown()
            finishPage3.countDown()
        }
    }
}
