package com.verumomnis.forensic.ui

/**
 * Minimal markdown model for the in-app Constitution reader.
 *
 * Deliberately not a general markdown engine: it renders exactly the constructs
 * CONSTITUTION.md uses — headings, paragraphs, tables, bullet and numbered
 * lists, blockquotes, fenced code, and rules. Anything unrecognised falls
 * through as a paragraph rather than being dropped, so no clause can silently
 * disappear from a governing document because the parser did not expect it.
 */
sealed interface ConstitutionBlock {
    /** `#`..`######` — [level] is the number of hashes. */
    data class Heading(val level: Int, val text: String) : ConstitutionBlock
    data class Paragraph(val text: String) : ConstitutionBlock
    /** Table with a header row; every row has the same cell count as [header]. */
    data class Table(val header: List<String>, val rows: List<List<String>>) : ConstitutionBlock
    /** `-` bullet or `1.` numbered item; [marker] is rendered verbatim. */
    data class ListItem(val marker: String, val text: String) : ConstitutionBlock
    data class Quote(val text: String) : ConstitutionBlock
    data class Code(val text: String) : ConstitutionBlock
    data object Rule : ConstitutionBlock
}

object ConstitutionMarkdown {

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val BULLET = Regex("^\\s*[-*]\\s+(.*)$")
    private val NUMBERED = Regex("^\\s*(\\d+)\\.\\s+(.*)$")
    /** A table separator such as `|---|:--:|`, which carries no content. */
    private val TABLE_DIVIDER = Regex("^\\s*\\|?[\\s:|-]+\\|[\\s:|-]*$")

    fun parse(markdown: String): List<ConstitutionBlock> {
        val blocks = mutableListOf<ConstitutionBlock>()
        val lines = markdown.replace("\r\n", "\n").split('\n')
        val paragraph = StringBuilder()

        fun flushParagraph() {
            val text = paragraph.toString().trim()
            if (text.isNotEmpty()) blocks += ConstitutionBlock.Paragraph(text)
            paragraph.setLength(0)
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> flushParagraph()

                trimmed.startsWith("```") -> {
                    flushParagraph()
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        code.appendLine(lines[i]); i++
                    }
                    blocks += ConstitutionBlock.Code(code.toString().trimEnd())
                }

                trimmed == "---" || trimmed == "***" -> {
                    flushParagraph(); blocks += ConstitutionBlock.Rule
                }

                HEADING.matches(trimmed) -> {
                    flushParagraph()
                    val m = HEADING.find(trimmed)!!
                    blocks += ConstitutionBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim())
                }

                // A table is a run of pipe rows; the second is the divider.
                trimmed.startsWith("|") && i + 1 < lines.size && TABLE_DIVIDER.matches(lines[i + 1].trim()) -> {
                    flushParagraph()
                    val header = splitRow(trimmed)
                    i += 2
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        val cells = splitRow(lines[i].trim())
                        // Pad or trim so rendering never index-errors on a ragged row.
                        rows += List(header.size) { cells.getOrElse(it) { "" } }
                        i++
                    }
                    blocks += ConstitutionBlock.Table(header, rows)
                    continue
                }

                trimmed.startsWith(">") -> {
                    flushParagraph()
                    blocks += ConstitutionBlock.Quote(trimmed.removePrefix(">").trim())
                }

                BULLET.matches(line) -> {
                    flushParagraph()
                    blocks += ConstitutionBlock.ListItem("•", BULLET.find(line)!!.groupValues[1].trim())
                }

                NUMBERED.matches(line) -> {
                    flushParagraph()
                    val m = NUMBERED.find(line)!!
                    blocks += ConstitutionBlock.ListItem("${m.groupValues[1]}.", m.groupValues[2].trim())
                }

                else -> {
                    // Hard line breaks are preserved rather than reflowed. Standard
                    // markdown would join these with a space, but this is a legal
                    // instrument: the header block, signature block and party
                    // details are line-structured, and collapsing them produces a
                    // wall of text that reads as one run-on sentence.
                    if (paragraph.isNotEmpty()) paragraph.append('\n')
                    paragraph.append(trimmed)
                }
            }
            i++
        }
        flushParagraph()
        return blocks
    }

    private fun splitRow(row: String): List<String> =
        row.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

    /**
     * Strips inline `**bold**`, `*italic*` and `` `code` `` markers.
     *
     * The reader renders emphasis through typography rather than by showing the
     * markers, but the text itself is never altered — only the delimiters go.
     */
    fun stripInline(text: String): String = text
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "$1")
        .replace(Regex("`(.+?)`"), "$1")
}
