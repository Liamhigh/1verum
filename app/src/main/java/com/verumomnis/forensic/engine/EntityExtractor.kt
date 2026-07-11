package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.ContradictionCategory
import com.verumomnis.forensic.model.StatementType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A single extracted, normalised statement anchored to its source. */
data class Statement(
    val text: String,
    val normalized: String,
    val doc: EvidenceDocument,
    val line: Int,
    val statementType: StatementType,
    val date: LocalDate?,
    val subject: ContradictionCategory,
    /** -1 = denial/negation, +1 = assertion/action, 0 = neutral. */
    val polarity: Int
)

data class ExtractedEntities(
    val people: List<String>,
    val companies: List<String>,
    val dates: List<String>,
    val amounts: List<Double>
)

/**
 * EntityExtractor + statement extraction (build spec: EntityExtractor.kt and the
 * B1 statement pipeline). Deterministic, offline, text-based.
 */
object EntityExtractor {

    private val PERSON_FROM = Regex("""(?im)^[ \t]*(?:from|respondent|by|witness|deponent)[ \t]*[:\-][ \t]*([A-Z][a-zA-Z.'-]+(?:[ \t]+[A-Z][a-zA-Z.'-]+){0,3})""")
    private val PERSON_INLINE = Regex("""\b([A-Z][a-z]+\s+[A-Z][a-z]+)\b""")
    private val COMPANY = Regex("""\b([A-Z][A-Za-z&]+(?:\s+[A-Z][A-Za-z&]+)*\s+(?:\(Pty\)\s*Ltd|Ltd|LLC|FZ-LLC|Inc|CC|Holdings|Motors|Export|Petroleum|Fuels))\b""")
    private val AMOUNT = Regex("""R\s?([\d][\d.,\s]*)(million|m|k)?\b""", RegexOption.IGNORE_CASE)
    private val DATE_PATTERNS = listOf(
        Regex("""\b\d{4}-\d{2}-\d{2}\b"""),
        Regex("""\b\d{1,2}\s+(?:January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{4}\b"""),
        Regex("""\b\d{1,2}/\d{1,2}/\d{2,4}\b""")
    )

    fun extractEntities(documents: List<EvidenceDocument>): ExtractedEntities {
        val text = documents.joinToString("\n") { it.text }
        val people = LinkedHashSet<String>()
        PERSON_FROM.findAll(text).forEach { people += it.groupValues[1].trim() }
        PERSON_INLINE.findAll(text).forEach { people += it.groupValues[1].trim() }
        val companies = COMPANY.findAll(text).map { it.value.trim() }.toCollection(LinkedHashSet())
        val dates = DATE_PATTERNS.flatMap { p -> p.findAll(text).map { it.value } }.toCollection(LinkedHashSet())
        val amounts = AMOUNT.findAll(text).mapNotNull { m ->
            val base = m.groupValues[1].replace(Regex("[,\\s]"), "").toDoubleOrNull() ?: return@mapNotNull null
            val multiplier = when (m.groupValues[2].lowercase()) {
                "million", "m" -> 1_000_000
                "k" -> 1_000
                else -> 1
            }
            base * multiplier
        }.filter { it > 0 }.toList()
        return ExtractedEntities(people.toList(), companies.toList(), dates.toList(), amounts)
    }

    fun authorOf(doc: EvidenceDocument): String? =
        PERSON_FROM.find(doc.text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    /** Split each document into sentence-level statements with metadata. */
    fun statements(documents: List<EvidenceDocument>): List<Statement> {
        val out = mutableListOf<Statement>()
        documents.forEach { doc ->
            doc.text.lines().forEachIndexed { lineIdx, rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEachIndexed
                splitSentences(line).forEach sentence@{ sentence ->
                    val s = sentence.trim()
                    if (s.length < 4) return@sentence
                    val subject = SubjectClassifier.subjectOf(s)
                    out += Statement(
                        text = s,
                        normalized = normalise(s),
                        doc = doc,
                        line = lineIdx + 1,
                        statementType = statementType(s),
                        date = extractDate(s),
                        subject = subject,
                        polarity = SubjectClassifier.polarity(subject, s)
                    )
                }
            }
        }
        return out
    }

    private fun splitSentences(line: String): List<String> =
        line.split(Regex("(?<=[.!?;])\\s+")).filter { it.isNotBlank() }

    fun normalise(text: String): String =
        text.lowercase(Locale.ROOT)
            .replace(Regex("[\"'`]"), "")
            .replace(Regex("\\b(the|a|an|is|was|were|are|been|be|to|of|for|that|this|with|and)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun statementType(text: String): StatementType {
        val t = text.lowercase(Locale.ROOT)
        return when {
            Regex("sworn|affidavit|under oath|para\\s*\\d|cct\\d|deposed").containsMatchIn(t) -> StatementType.SWORN_STATEMENT
            Regex("i admit|we admit|acknowledg|conced").containsMatchIn(t) -> StatementType.ADMISSION
            Regex("demand|must pay|pay r|invoice|fee of|owe").containsMatchIn(t) -> StatementType.DEMAND
            Regex("will |promise|undertake|agree to").containsMatchIn(t) -> StatementType.PROMISE
            Regex("or else|regret|threat").containsMatchIn(t) -> StatementType.THREAT
            Regex("never |no |not |denies|denied|deny").containsMatchIn(t) -> StatementType.DENIAL
            extractDate(text) != null || Regex("email|whatsapp|message|dated").containsMatchIn(t) -> StatementType.CONTEMPORANEOUS
            else -> StatementType.CLAIM
        }
    }

    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

    fun extractDate(text: String): LocalDate? {
        Regex("""\b(\d{4}-\d{2}-\d{2})\b""").find(text)?.let {
            return runCatching { LocalDate.parse(it.groupValues[1]) }.getOrNull()
        }
        Regex("""\b(\d{1,2}\s+(?:January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{4})\b""")
            .find(text)?.let {
                return runCatching { LocalDate.parse(it.groupValues[1], DAY_MONTH) }.getOrNull()
            }
        return null
    }
}
