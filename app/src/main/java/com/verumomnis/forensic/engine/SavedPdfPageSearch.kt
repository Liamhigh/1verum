package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.DocumentExtractionCoverage

/** A location in one immutable, persisted extraction snapshot, not a new OCR result. */
data class SavedPdfPageMatch(
    val originalPage: Int,
    val snippet: String,
    val highlightStart: Int,
    val highlightEnd: Int,
    val occurrences: Int
)

data class SavedPdfPageSearchGroup(
    val record: DocumentExtractionCoverage,
    val matches: List<SavedPdfPageMatch>
)

/**
 * Literal, case-insensitive phrase search over saved text only. Whitespace may
 * span OCR line breaks. Snippets keep the original text and original page number;
 * extraction records, earlier revisions and retry provenance are never rewritten.
 */
fun searchSavedPdfPages(
    records: List<DocumentExtractionCoverage>,
    query: String
): List<SavedPdfPageSearchGroup> {
    val words = query.split(Regex("[\\s\\p{Z}]+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    val pattern = Regex(words.joinToString("[\\s\\p{Z}]+") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
    return records.sortedWith(
        compareBy<DocumentExtractionCoverage> { it.fileName.lowercase(java.util.Locale.ROOT) }
            .thenBy { it.sha512 }
            .thenByDescending { it.revision }
            .thenBy { it.coverage.retryAttempt?.id.orEmpty() }
    ).mapNotNull { record ->
        val matches = record.coverage.pages.sortedBy { it.page }.mapNotNull { page ->
            val text = page.extractedText?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val first = pattern.find(text) ?: return@mapNotNull null
            val start = (first.range.first - 70).coerceAtLeast(0)
            val end = (first.range.last + 1 + 110).coerceAtMost(text.length)
            val prefix = if (start > 0) "…" else ""
            val suffix = if (end < text.length) "…" else ""
            SavedPdfPageMatch(
                originalPage = page.page,
                snippet = prefix + text.substring(start, end) + suffix,
                highlightStart = prefix.length + first.range.first - start,
                highlightEnd = prefix.length + first.range.last + 1 - start,
                occurrences = pattern.findAll(text).count()
            )
        }
        if (matches.isEmpty()) null else SavedPdfPageSearchGroup(record, matches)
    }
}
