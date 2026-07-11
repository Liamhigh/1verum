package com.verumomnis.forensic.pdf

import com.verumomnis.forensic.model.ForensicReport
import com.verumomnis.forensic.model.SealedEmail

/** Paginated content for a sealed PDF. */
data class SealedPdfContent(
    val title: String,
    val classification: String,
    val sealFooter: String,
    val shortcode: String,
    val bodyLines: List<String>
) {
    data class Page(
        val title: String,
        val classification: String,
        val lines: List<String>,
        val sealFooter: String,
        val pageLabel: String
    )

    fun paginate(linesPerPage: Int = 46): List<Page> {
        val chunks = if (bodyLines.isEmpty()) listOf(emptyList()) else bodyLines.chunked(linesPerPage)
        val total = chunks.size
        return chunks.mapIndexed { index, lines ->
            Page(
                title = title,
                classification = classification,
                lines = lines,
                sealFooter = sealFooter,
                pageLabel = "Page ${index + 1} of $total | $shortcode"
            )
        }
    }

    companion object {
        private const val WRAP = 88

        private fun wrap(text: String): List<String> {
            if (text.length <= WRAP) return listOf(text)
            val out = mutableListOf<String>()
            var remaining = text
            while (remaining.length > WRAP) {
                var cut = remaining.lastIndexOf(' ', WRAP)
                if (cut <= 0) cut = WRAP
                out += remaining.substring(0, cut)
                remaining = remaining.substring(cut).trimStart()
            }
            if (remaining.isNotEmpty()) out += remaining
            return out
        }

        fun fromReport(report: ForensicReport): SealedPdfContent {
            val lines = report.body.lines().flatMap { wrap(it) }
            return SealedPdfContent(
                title = report.title,
                classification = report.classification,
                sealFooter = report.seal.sealFooter(),
                shortcode = report.seal.shortcode,
                bodyLines = lines
            )
        }

        fun fromEmail(email: SealedEmail): SealedPdfContent {
            val lines = buildList {
                add("To: ${email.draft.recipient}")
                add("Subject: ${email.draft.subject}")
                add("Sent: ${email.sentAt}")
                add("Delivery: ${if (email.delivered) "DELIVERED" else "HELD"} (${email.assessment.verdict})")
                add("")
                addAll(email.draft.body.lines())
            }.flatMap { wrap(it) }
            return SealedPdfContent(
                title = "Sealed Communication — ${email.draft.subject}",
                classification = "CONFIDENTIAL — DISTRIBUTION RECORD",
                sealFooter = email.seal.sealFooter(),
                shortcode = email.seal.shortcode,
                bodyLines = lines
            )
        }
    }
}
