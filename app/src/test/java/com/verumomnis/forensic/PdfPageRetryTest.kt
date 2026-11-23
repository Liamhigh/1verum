package com.verumomnis.forensic

import com.verumomnis.forensic.engine.*
import com.verumomnis.forensic.model.*
import com.verumomnis.forensic.model.PageReadStatus.*
import org.junit.Assert.*
import org.junit.Test

class PdfPageRetryTest {
    private val original = PdfExtractionCoverage(5, listOf(
        PageExtraction(1, TEXT_LAYER, "embedded_text", 48, 48, extractedText = "Original readable text on page one stays exactly as saved."),
        PageExtraction(2, UNREAD, "ocr_returned_no_text", ocrAttempted = true),
        PageExtraction(3, FAILED, "page_render_or_ocr_failed", ocrAttempted = true),
        PageExtraction(4, SKIPPED, "ocr_page_limit"),
        PageExtraction(5, OCR, "on_device_ocr", extractedChars = 20, extractedText = "Earlier OCR text stays")
    ))
    private fun attempt(vararg pages: Int) = PdfRetryAttempt("synthetic-attempt", "2026-11-22T12:00:00Z", pages.toList())
    private class Ocr(override val pageCount: Int = 5, val text: (Int) -> String) : PdfOcrPages {
        val calls = mutableListOf<Int>()
        var closed = false
        override fun recognise(index: Int): String { calls += index; return text(index) }
        override fun close() { closed = true }
    }

    @Test fun selectedPagesOnlyAreReadWithOriginalNumbersAndUnselectedTextUntouched() {
        val ocr = Ocr { "Recovered original page ${it + 1}" }
        val result = retryPdfPages(original, attempt(3, 4), 200) { ocr }
        assertEquals(listOf(2, 3), ocr.calls)
        assertEquals(listOf(TEXT_LAYER, UNREAD, OCR, OCR, OCR), result.coverage.pages.map { it.status })
        for (index in listOf(0, 1, 4)) assertEquals(original.pages[index], result.coverage.pages[index])
        assertEquals(listOf(1, 2, 3, 4, 5), result.coverage.pages.map { it.page })
        assertTrue(result.text.contains("Recovered original page 3"))
        assertTrue(result.coverage.summary().contains("NEW OCR RETRY ATTEMPT"))
        assertTrue(ocr.closed)
        assertNull(original.retryAttempt)
    }

    @Test fun failedAndBlankRetriesRemainGapsAndASecondAttemptHasAFreshBudget() {
        val first = Ocr { if (it == 1) "" else error("synthetic failure") }
        val result = retryPdfPages(original, attempt(2, 3), 2) { first }
        assertEquals(listOf(1, 2), first.calls)
        assertEquals(listOf(2, 3, 4), result.coverage.remainingPages.map { it.page })
        val second = Ocr { "New attempt text" }
        val next = retryPdfPages(result.coverage, attempt(2, 3).copy(id = "second", parentAttemptId = "synthetic-attempt"), 2) { second }
        assertEquals(listOf(1, 2), second.calls)
        assertEquals("synthetic-attempt", next.coverage.retryAttempt?.parentAttemptId)
    }

    @Test fun invalidSelectionsAndLegacyOrUnknownCoverageNeverOpenOcr() {
        val selections = listOf(emptyList(), listOf(1), listOf(0), listOf(6), listOf(2, 2), listOf(2, 3, 4))
        selections.forEach { pages ->
            assertThrows(IllegalArgumentException::class.java) {
                retryPdfPages(original, attempt().copy(selectedPages = pages), 2) { error("must not open") }
            }
        }
        listOf(
            original.copy(totalPages = null),
            original.copy(pages = original.pages.dropLast(1)),
            original.copy(pages = original.pages.map { it.copy(extractedText = null) })
        ).forEach { previous ->
            assertThrows(IllegalArgumentException::class.java) {
                retryPdfPages(previous, attempt(2), 200) { error("must not open") }
            }
        }
    }

    @Test fun unavailableOrMismatchedRendererLeavesUnselectedPagesUntouched() {
        val ocr = Ocr(pageCount = 4) { error("must not read a mismatched PDF") }
        val mismatch = retryPdfPages(original, attempt(2), 200) { ocr }
        assertTrue(ocr.closed)
        assertTrue(ocr.calls.isEmpty())
        assertEquals(FAILED, mismatch.coverage.pages[1].status)
        assertNotNull(mismatch.coverage.documentFailure)
        val unavailable = retryPdfPages(original, attempt(4), 200) { error("synthetic init error") }
        assertEquals(original.pages[0], unavailable.coverage.pages[0])
        assertFalse(unavailable.coverage.pages[3].ocrAttempted)
        assertTrue(unavailable.coverage.incomplete)
    }

    @Test fun budgetCanNeverExceedTheDeviceSafetyLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            retryPdfPages(original, attempt(2), 201) { error("must not open") }
        }
    }

    @Test fun cancellationDuringRecognitionFinishesCurrentPageAndClosesOcrResources() {
        val cancellation = java.util.concurrent.atomic.AtomicBoolean(false)
        val ocr = Ocr { index ->
            if (index == 1) cancellation.set(true)
            "Recognised page ${index + 1}"
        }

        val result = retryPdfPages(
            original,
            attempt(2, 3),
            200,
            isCancellationRequested = cancellation::get
        ) { ocr }

        assertEquals(listOf(1), ocr.calls)
        assertEquals(listOf(2), result.coverage.retryAttempt?.processedPages)
        assertTrue(result.coverage.retryAttempt?.cancelled == true)
        assertEquals(OCR, result.coverage.pages[1].status)
        assertEquals(original.pages[2], result.coverage.pages[2])
        assertTrue(result.coverage.summary().contains("Unprocessed selected pages remain"))
        assertTrue(ocr.closed)
        assertEquals(UNREAD, original.pages[1].status)
    }

    @Test fun cancellationBetweenPagesDoesNotMarkTheNextPageAsRead() {
        val checks = java.util.concurrent.atomic.AtomicInteger()
        val ocr = Ocr { "Recognised page ${it + 1}" }
        // Checks occur before opening OCR, after opening, before each page,
        // and after each recognition. Request cancellation before page 3 starts.
        val result = retryPdfPages(
            original,
            attempt(2, 3),
            200,
            isCancellationRequested = { checks.incrementAndGet() >= 5 }
        ) { ocr }

        assertEquals(listOf(1), ocr.calls)
        assertEquals(listOf(2), result.coverage.retryAttempt?.processedPages)
        assertTrue(result.coverage.retryAttempt?.cancelled == true)
        assertEquals(OCR, result.coverage.pages[1].status)
        assertEquals(original.pages[2], result.coverage.pages[2])
        assertTrue(ocr.closed)
    }

    @Test fun cancellationAfterTheLastSelectedPageLabelsTheStoppedReportWithoutCallingOcrPartial() {
        val checks = java.util.concurrent.atomic.AtomicInteger()
        val ocr = Ocr { "Recognised page ${it + 1}" }
        val result = retryPdfPages(
            original,
            attempt(2),
            200,
            isCancellationRequested = { checks.incrementAndGet() >= 4 }
        ) { ocr }

        assertTrue(result.coverage.retryAttempt?.cancelled == true)
        assertEquals(listOf(2), result.coverage.retryAttempt?.processedPages)
        assertTrue(result.coverage.summary().contains("All selected pages finished before cancellation"))
        assertFalse(result.coverage.summary().contains("Unprocessed selected pages remain"))
        assertTrue(ocr.closed)
    }

    @Test fun progressKeepsOriginalNumbersAndCountsAPageOnlyAfterRecognitionFinishes() {
        val updates = mutableListOf<PdfRetryProgress>()
        val ocr = Ocr { index ->
            val expectedCompleted = if (index == 1) 0 else 1
            assertEquals(PdfRetryProgress(expectedCompleted, 2, index + 1), updates.last())
            "Recovered original page ${index + 1}"
        }
        val result = retryPdfPages(
            original, attempt(3, 2), 200, onProgress = updates::add
        ) { ocr }
        assertEquals(listOf(
            PdfRetryProgress(0, 2),
            PdfRetryProgress(0, 2, 2),
            PdfRetryProgress(1, 2),
            PdfRetryProgress(1, 2, 3),
            PdfRetryProgress(2, 2)
        ), updates)
        assertEquals(listOf(2, 3), result.coverage.retryAttempt!!.processedPages)
        assertEquals(UNREAD, original.pages[1].status)
        assertNull(original.retryAttempt)
    }

    @Test fun failedAndBlankRecognitionFinishAttemptsButDoNotClaimReadablePages() {
        val updates = mutableListOf<PdfRetryProgress>()
        val ocr = Ocr { index ->
            assertEquals(if (index == 1) 0 else 1, updates.last().completedPages)
            if (index == 1) "" else throw java.io.IOException("Synthetic recognition failure")
        }
        val result = retryPdfPages(original, attempt(2, 3), 200, onProgress = updates::add) { ocr }
        assertEquals(PdfRetryProgress(2, 2), updates.last())
        assertEquals(UNREAD, result.coverage.pages[1].status)
        assertEquals(FAILED, result.coverage.pages[2].status)
        assertTrue(result.coverage.incomplete)
    }

    @Test fun cancellingFromCompletedPageProgressNeverStartsOrCountsTheNextPage() {
        val cancellation = java.util.concurrent.atomic.AtomicBoolean(false)
        val updates = mutableListOf<PdfRetryProgress>()
        val ocr = Ocr { "Finished page ${it + 1}" }
        val result = retryPdfPages(
            original, attempt(2, 3, 4), 200, isCancellationRequested = cancellation::get,
            onProgress = {
                updates += it
                if (it.completedPages == 1 && it.currentOriginalPage == null) cancellation.set(true)
            }
        ) { ocr }
        assertEquals(listOf(PdfRetryProgress(0, 3), PdfRetryProgress(0, 3, 2), PdfRetryProgress(1, 3)), updates)
        assertEquals(listOf(1), ocr.calls)
        assertEquals(listOf(2), result.coverage.retryAttempt!!.processedPages)
        assertTrue(result.coverage.retryAttempt!!.cancelled)
        assertEquals(original.pages[2], result.coverage.pages[2])
        assertTrue(ocr.closed)
    }

    @Test fun rendererFailureOrEarlyCancellationNeverPretendsAPageWasCompleted() {
        for (cancelled in listOf(false, true)) {
            val updates = mutableListOf<PdfRetryProgress>()
            val result = retryPdfPages(
                original, attempt(2, 3), 200,
                isCancellationRequested = { cancelled }, onProgress = updates::add
            ) { throw java.io.IOException("Synthetic renderer failure") }
            assertEquals(listOf(PdfRetryProgress(0, 2)), updates)
            assertTrue(result.coverage.retryAttempt!!.processedPages.isEmpty())
        }
    }
}
