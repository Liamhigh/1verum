package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.PageExtraction
import com.verumomnis.forensic.model.PageReadStatus
import com.verumomnis.forensic.model.PdfExtractionCoverage
import java.io.Closeable

/** Device OCR boundary; tests substitute recognition, not the PDF text-layer parser. */
internal interface PdfOcrPages : Closeable {
    val pageCount: Int
    fun recognise(index: Int): String
}

/** Shared, deterministic merge policy. Null layers denote parsing failures, not blank pages. */
internal fun extractPdfPages(
    layers: List<String?>,
    pageCountKnown: Boolean,
    thinPageChars: Int,
    maxOcrPages: Int,
    openOcr: () -> PdfOcrPages
): PdfExtractionResult {
    require(thinPageChars >= 0 && maxOcrPages >= 0)
    val text = mutableListOf<String>()
    val provenance = mutableListOf<PageExtraction>()
    var session: PdfOcrPages? = null
    var failure: String? = null
    var count: Int? = if (pageCountKnown) layers.size else null
    val needsOcr = !pageCountKnown || layers.any { it == null || it.length < thinPageChars || it.isBlank() }
    if (needsOcr) {
        try {
            session = openOcr()
            if (count != null && count != session.pageCount) failure = "PDF parser and renderer page counts disagree"
            count = maxOf(count ?: 0, session.pageCount)
        } catch (_: Exception) {
            failure = "PDF rendering or OCR initialisation failed"
        }
    }
    var attempts = 0
    try {
        for (index in 0 until (count ?: 0)) {
            val layer = layers.getOrNull(index).orEmpty()
            var output = layer
            var attempted = false
            val (status, reason) = when {
                layer.isNotBlank() && layer.length >= thinPageChars ->
                    PageReadStatus.TEXT_LAYER to "embedded_text"
                attempts >= maxOcrPages ->
                    PageReadStatus.SKIPPED to "ocr_page_limit"
                session == null ->
                    PageReadStatus.FAILED to "renderer_or_ocr_unavailable"
                else -> {
                    // Count failures too: broken pages must not defeat the OCR work cap.
                    attempts++
                    attempted = true
                    try {
                        val recognised = session.recognise(index).trim()
                        if (recognised.isBlank()) PageReadStatus.UNREAD to "ocr_returned_no_text"
                        else {
                            output = recognised
                            PageReadStatus.OCR to "on_device_ocr"
                        }
                    } catch (_: Exception) {
                        PageReadStatus.FAILED to "page_render_or_ocr_failed"
                    }
                }
            }
            provenance += PageExtraction(index + 1, status, reason, layer.length, output.length, attempted,
                textLayerFailed = layers.getOrNull(index) == null)
            text += if (status == PageReadStatus.OCR) "[OCR] $output" else output
        }
    } finally {
        try { session?.close() } catch (_: Exception) { /* Extraction result remains valid. */ }
    }
    return PdfExtractionResult(text.joinToString("\n").trim(), PdfExtractionCoverage(
        count, provenance, maxOcrPages, thinPageChars, failure
    ))
}
