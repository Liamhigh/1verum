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
}
