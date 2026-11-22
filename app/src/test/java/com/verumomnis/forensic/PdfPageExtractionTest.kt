package com.verumomnis.forensic

import com.verumomnis.forensic.engine.PdfOcrPages
import com.verumomnis.forensic.engine.extractPdfPages
import com.verumomnis.forensic.model.PageReadStatus.*
import org.junit.Assert.*
import org.junit.Test

class PdfPageExtractionTest {
    private class Ocr(override val pageCount: Int, val read: (Int) -> String) : PdfOcrPages {
        val calls = mutableListOf<Int>()
        var closed = false
        override fun recognise(index: Int): String { calls += index; return read(index) }
        override fun close() { closed = true }
    }

    @Test fun productionCapCountsFailedAttemptsAndKeepsTrailingPages() {
        val ocr = Ocr(203) { index -> if (index % 2 == 0) error("synthetic failure") else "synthetic OCR" }
        val result = extractPdfPages(List(203) { "" }, true, 30, 200) { ocr }
        assertEquals(200, ocr.calls.size)
        assertEquals(203, result.coverage.totalPages)
        assertEquals(203, result.coverage.pages.size)
        assertEquals(100, result.coverage.pages.count { it.status == FAILED })
        assertEquals(100, result.coverage.pages.count { it.status == OCR })
        assertEquals(listOf(201, 202, 203), result.coverage.pages.filter { it.status == SKIPPED }.map { it.page })
        assertTrue(ocr.closed)
        assertTrue(result.coverage.incomplete)
    }

    @Test fun partialLayerFailureAndEmptyOcrRemainDistinctAndLaterTextSurvives() {
        val last = "Synthetic machine-readable page after a broken page."
        val ocr = Ocr(5) { when (it) {
            0 -> ""
            1 -> error("synthetic page failure")
            2 -> "Synthetic recovered page"
            else -> error("OCR should not run")
        } }
        val result = extractPdfPages(listOf("", null, null, "", last), true, 30, 3) { ocr }
        assertEquals(listOf(UNREAD, FAILED, OCR, SKIPPED, TEXT_LAYER), result.coverage.pages.map { it.status })
        assertTrue(result.coverage.pages[1].textLayerFailed)
        assertTrue(result.coverage.pages[2].textLayerFailed)
        assertEquals(listOf(0, 1, 2), ocr.calls)
        assertTrue(result.text.contains(last))
        assertTrue(result.coverage.summary().contains("not a clean-content assurance"))
    }

    @Test fun rendererFailurePreservesKnownPageCountAndReadableText() {
        val layer = "A synthetic readable page with enough embedded text."
        val result = extractPdfPages(listOf(layer, "", null, ""), true, 30, 200) { error("synthetic open failure") }
        assertEquals(4, result.coverage.totalPages)
        assertEquals(listOf(TEXT_LAYER, FAILED, FAILED, FAILED), result.coverage.pages.map { it.status })
        assertEquals(layer, result.text)
        assertTrue(result.coverage.pages.none { it.ocrAttempted })
    }

    @Test fun entireParserFailureStillAccountsForBlankAndCappedTrailingPages() {
        val ocr = Ocr(4) { if (it == 0) "Recovered synthetic text" else "" }
        val result = extractPdfPages(emptyList(), false, 30, 2) { ocr }
        assertEquals(4, result.coverage.totalPages)
        assertEquals(listOf(OCR, UNREAD, SKIPPED, SKIPPED), result.coverage.pages.map { it.status })
    }

    @Test fun unknownCountAndTotalFailureCannotLookComplete() {
        val result = extractPdfPages(emptyList(), false, 30, 200) { error("unopenable synthetic PDF") }
        assertNull(result.coverage.totalPages)
        assertTrue(result.text.isBlank())
        assertTrue(result.coverage.incomplete)
        assertTrue(result.coverage.summary().contains("unknown"))
    }

    @Test fun textLayerOnlyNeverOpensOcrAndZeroBudgetSkipsDeliberately() {
        val text = "A synthetic full text-layer page, not an image-only page."
        val full = extractPdfPages(listOf(text), true, 30, 200) { error("must not open") }
        assertFalse(full.coverage.incomplete)
        assertEquals(TEXT_LAYER, full.coverage.pages.single().status)
        val zero = extractPdfPages(listOf(""), true, 30, 0) { Ocr(1) { error("must not recognise") } }
        assertEquals(SKIPPED, zero.coverage.pages.single().status)
        assertEquals("ocr_page_limit", zero.coverage.pages.single().reason)
    }
}
