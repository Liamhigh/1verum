package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.PageReadStatus
import com.verumomnis.forensic.model.PdfExtractionCoverage
import com.verumomnis.forensic.model.PdfRetryAttempt

/** Strictly selected OCR: never reparses/relabels good pages or spends work on unselected pages. */
internal fun retryPdfPages(
    previous: PdfExtractionCoverage,
    attempt: PdfRetryAttempt,
    maxOcrPages: Int,
    openOcr: () -> PdfOcrPages
): PdfExtractionResult {
    val count = requireNotNull(previous.totalPages) { "Page count is unknown. Re-import the original PDF." }
    require(count > 0 && previous.pages.map { it.page }.sorted() == (1..count).toList()) {
        "Page coverage is missing or invalid. Re-import the original PDF."
    }
    val selected = attempt.selectedPages.toSet()
    require(selected.isNotEmpty() && selected.size == attempt.selectedPages.size)
    require(maxOcrPages in 1..PdfOcrExtractor.DEFAULT_MAX_OCR_PAGES && selected.size <= maxOcrPages) {
        "Select at most $maxOcrPages remaining pages per attempt."
    }
    require(previous.remainingPages.map { it.page }.containsAll(selected)) { "Only remaining pages can be retried." }
    require(previous.pages.none {
        it.extractedChars > 0 && it.extractedText == null
    }) { "Saved page text is unavailable. Re-import the original PDF before retrying." }

    var session: PdfOcrPages? = null
    var failure: String? = null
    try {
        session = openOcr()
        check(session.pageCount == count) { "PDF page counts disagree" }
    } catch (_: Exception) {
        try { session?.close() } catch (_: Exception) { }
        session = null
        failure = "PDF rendering or OCR initialisation failed, or page counts disagree"
    }
    val pages = try {
        previous.pages.sortedBy { it.page }.map { page ->
            if (page.page !in selected) return@map page
            if (session == null) return@map page.copy(
                status = PageReadStatus.FAILED, reason = "retry_renderer_or_ocr_unavailable", ocrAttempted = false
            )
            try {
                val text = session.recognise(page.page - 1).trim()
                if (text.isBlank()) page.copy(
                    status = PageReadStatus.UNREAD, reason = "retry_ocr_returned_no_text", ocrAttempted = true
                ) else page.copy(
                    status = PageReadStatus.OCR, reason = "on_device_ocr_retry",
                    extractedChars = text.length, extractedText = text, ocrAttempted = true
                )
            } catch (_: Exception) {
                page.copy(status = PageReadStatus.FAILED, reason = "retry_page_render_or_ocr_failed", ocrAttempted = true)
            }
        }
    } finally {
        try { session?.close() } catch (_: Exception) { }
    }
    val coverage = previous.copy(
        pages = pages, ocrPageLimit = maxOcrPages, retryAttempt = attempt,
        // Do not erase an unresolved parser/renderer disagreement from the original attempt.
        documentFailure = failure ?: previous.documentFailure
    )
    return PdfExtractionResult(pages.joinToString("\n") {
        val text = it.extractedText.orEmpty()
        if (it.status == PageReadStatus.OCR && text.isNotBlank()) "[OCR] $text" else text
    }.trim(), coverage)
}
