package com.verumomnis.forensic.model

import kotlinx.serialization.Serializable

/** Page numbers are one-based and always refer to the original PDF, not merged text. */
@Serializable
enum class PageReadStatus { TEXT_LAYER, OCR, UNREAD, FAILED, SKIPPED }

@Serializable
data class PageExtraction(
    val page: Int,
    val status: PageReadStatus,
    val reason: String,
    val textLayerChars: Int = 0,
    val extractedChars: Int = 0,
    val ocrAttempted: Boolean = false,
    val textLayerFailed: Boolean = false,
    /** Exact extracted text for this original page; null for legacy coverage records. */
    val extractedText: String? = null
)

@Serializable
data class PdfExtractionCoverage(
    /** Null means neither parser nor renderer could establish the page count. */
    val totalPages: Int? = null,
    val pages: List<PageExtraction> = emptyList(),
    val ocrPageLimit: Int = 200,
    val thinPageChars: Int = 30,
    val documentFailure: String? = null
) {
    val incomplete: Boolean get() = documentFailure != null || totalPages == null || totalPages == 0 ||
        pages.size != totalPages || pages.any { it.status !in setOf(PageReadStatus.TEXT_LAYER, PageReadStatus.OCR) }

    fun summary(): String = buildString {
        append(if (incomplete) "WARNING — incomplete PDF coverage. " else "PDF extraction coverage. ")
        append("Pages: ${totalPages ?: "unknown"}; ")
        append("text layer: ${pages.count { it.status == PageReadStatus.TEXT_LAYER }}; ")
        append("OCR: ${pages.count { it.status == PageReadStatus.OCR }}; ")
        append("unread: ${pages.count { it.status == PageReadStatus.UNREAD }}; ")
        append("failed: ${pages.count { it.status == PageReadStatus.FAILED }}; ")
        append("deliberately skipped: ${pages.count { it.status == PageReadStatus.SKIPPED }}. ")
        documentFailure?.let { append("$it. ") }
        if (incomplete) append("Unread, failed, skipped or unknown pages were not fully analysed. A zero-indicator result is not a clean-content assurance. ")
        append("Results apply only to extracted text. OCR can omit or misread text, handwriting, tables and images; it is not proof of completeness or accuracy. ")
        append("OCR is attempted below $thinPageChars text-layer characters, at up to 200 dpi with a 2600-pixel dimension cap, and at most $ocrPageLimit page attempts per document (including failures). Review original pages manually.")
    }
}

@Serializable
data class DocumentExtractionCoverage(
    val evidenceId: String,
    val fileName: String,
    val sha512: String,
    val coverage: PdfExtractionCoverage
)
