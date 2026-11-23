package com.verumomnis.forensic

import com.verumomnis.forensic.engine.searchSavedPdfPages
import com.verumomnis.forensic.model.*
import org.junit.Assert.*
import org.junit.Test

class SavedPdfPageSearchTest {
    private fun page(number: Int, text: String?) = PageExtraction(
        number, PageReadStatus.TEXT_LAYER, "embedded_text",
        extractedChars = text?.length ?: 0, extractedText = text
    )
    private fun record(
        hash: String = "a".repeat(128),
        pages: List<PageExtraction>,
        revision: Int = 0,
        attempt: PdfRetryAttempt? = null
    ) = DocumentExtractionCoverage(
        "DOC001", "same-name.pdf", hash,
        PdfExtractionCoverage(12, pages, retryAttempt = attempt), revision
    )

    @Test fun findsNamesAcrossWhitespaceAndCaseWithoutChangingOriginalSnippet() {
        val text = "Witness: ADA\n \u00a0Lovelace recorded the event."
        val record = record(pages = listOf(page(12, text)))
        val result = searchSavedPdfPages(listOf(record), "  ada lovelace  ").single()
        val match = result.matches.single()
        assertSame(record, result.record)
        assertEquals(12, match.originalPage)
        assertEquals(text, match.snippet)
        assertEquals("ADA\n \u00a0Lovelace", match.snippet.substring(match.highlightStart, match.highlightEnd))
    }

    @Test fun sameFilenameSourcesAndRetryAttemptsRemainSeparateAndKeepTheirProvenance() {
        val original = record(pages = listOf(page(2, "Ada in the original.")))
        val retry = record(
            pages = listOf(page(12, "Ada in retry.")), revision = 1,
            attempt = PdfRetryAttempt("retry-one", "2026-11-23T00:00:00Z", listOf(12))
        )
        val otherSource = record(hash = "b".repeat(128), pages = listOf(page(7, "Ada elsewhere.")))
        val before = listOf(original.copy(), retry.copy(), otherSource.copy())

        val groups = searchSavedPdfPages(listOf(original, otherSource, retry), "ada")

        assertEquals(3, groups.size)
        assertEquals(listOf(original, retry, otherSource), before)
        assertEquals(2, groups.single { it.record === original }.matches.single().originalPage)
        assertEquals(12, groups.single { it.record === retry }.matches.single().originalPage)
        assertEquals("retry-one", groups.single { it.record === retry }.record.coverage.retryAttempt!!.id)
        assertEquals(7, groups.single { it.record === otherSource }.matches.single().originalPage)
    }

    @Test fun originalPageNumbersAreNotRenumberedOrMergedAcrossPages() {
        val record = record(pages = listOf(
            page(12, "Needle on original twelve."), page(2, "Needle on original two."),
            page(3, "Ada"), page(4, "Lovelace")
        ))
        assertEquals(listOf(2, 12), searchSavedPdfPages(listOf(record), "needle").single().matches.map { it.originalPage })
        assertTrue(searchSavedPdfPages(listOf(record), "Ada Lovelace").isEmpty())
    }

    @Test fun blankAndLegacyPagesDoNotInventMatchesFromFilenamesOrCounters() {
        val record = record(pages = listOf(
            page(1, null).copy(extractedChars = 100),
            page(2, ""), page(3, " \n\t"), page(4, "Saved text only.")
        ))
        assertTrue(searchSavedPdfPages(listOf(record), "same-name.pdf").isEmpty())
        assertTrue(searchSavedPdfPages(listOf(record), " \n\t").isEmpty())
        assertTrue(searchSavedPdfPages(emptyList(), "Saved").isEmpty())
        assertEquals(4, searchSavedPdfPages(listOf(record), "Saved").single().matches.single().originalPage)
    }

    @Test fun snippetsLocateTheFirstMatchAndCountOccurrencesWithoutRewritingSavedText() {
        val text = "start ".repeat(50) + "Unique Person" + " after ".repeat(50) + "UNIQUE PERSON"
        val record = record(pages = listOf(page(9, text)))
        val hit = searchSavedPdfPages(listOf(record), "unique person").single().matches.single()
        assertTrue(hit.snippet.startsWith("…"))
        assertTrue(hit.snippet.endsWith("…"))
        assertTrue(hit.snippet.length < text.length)
        assertEquals("Unique Person", hit.snippet.substring(hit.highlightStart, hit.highlightEnd))
        assertEquals(2, hit.occurrences)
        assertEquals(text, record.coverage.pages.single().extractedText)
    }

    @Test fun punctuationIsLiteralRatherThanARegularExpression() {
        val record = record(pages = listOf(page(8, "Reference [A.1] and A21.")))
        val hit = searchSavedPdfPages(listOf(record), "[A.1]").single().matches.single()
        assertEquals(1, hit.occurrences)
        assertEquals("[A.1]", hit.snippet.substring(hit.highlightStart, hit.highlightEnd))
        assertTrue(searchSavedPdfPages(listOf(record), ".*").isEmpty())
    }
}
