package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.Confidence
import com.verumomnis.forensic.model.Contradiction
import com.verumomnis.forensic.model.ContradictionCategory
import com.verumomnis.forensic.model.ContradictionClaim
import com.verumomnis.forensic.model.ContradictionType
import com.verumomnis.forensic.model.Severity
import com.verumomnis.forensic.model.StatementType
import java.time.Instant
import java.util.Locale

/**
 * B1 Contradiction Brain (build spec Section 9). Extracts statements, groups them
 * by subject, and flags opposing statements as contradictions — anchored to a
 * person, page and statute, severity-scored, and categorised into the 7 canonical
 * categories. Also runs a set of explicit high-signal rules. Fully deterministic.
 */
object ContradictionExtractor {

    private data class ExplicitRule(
        val patternA: Regex,
        val patternB: Regex,
        val category: ContradictionCategory,
        val type: ContradictionType,
        val severity: Severity,
        val legalSignificance: String,
        val applicableLaw: List<String>
    )

    private val EXPLICIT_RULES = listOf(
        ExplicitRule(
            Regex("""(deal|transaction)\s+(fell\s+through|did\s+not\s+proceed|collapsed)""", RegexOption.IGNORE_CASE),
            Regex("""(proceeded|went\s+ahead|completed|finalised|finalized)\s+(with\s+)?(the\s+)?(deal|transaction|export)""", RegexOption.IGNORE_CASE),
            ContradictionCategory.CONTRACT_VALIDITY, ContradictionType.IMPLIED_CONTRADICTION, Severity.CRITICAL,
            "Fraud with consciousness of guilt: a claim was made that was known to be false.",
            listOf("SA Common Law - Fraud", "UAE CCL Art.84")
        ),
        ExplicitRule(
            Regex("""never\s+received\s+(any\s+)?payment|no\s+payment\s+was\s+(made|outstanding)""", RegexOption.IGNORE_CASE),
            Regex("""payment\s+(was\s+)?(made|received|transferred|paid)|paid\s+r""", RegexOption.IGNORE_CASE),
            ContradictionCategory.COMPENSATION, ContradictionType.DIRECT_NEGATION, Severity.VERY_HIGH,
            "Contradictory statements regarding payment; possible concealment of funds.",
            listOf("SA Common Law - Fraud", "Companies Act 71 of 2008")
        ),
        ExplicitRule(
            Regex("""no\s+(knowledge|awareness)\s+of""", RegexOption.IGNORE_CASE),
            Regex("""(was\s+)?(aware|informed|notified|knew)\s+(of|about)""", RegexOption.IGNORE_CASE),
            ContradictionCategory.PERJURY, ContradictionType.DIRECT_NEGATION, Severity.HIGH,
            "Denial of knowledge contradicted by evidence of awareness.",
            listOf("SA Common Law - Fraud")
        )
    )

    fun extract(documents: List<EvidenceDocument>, now: Instant): List<Contradiction> {
        val raw = mutableListOf<Contradiction>()
        raw += explicitContradictions(documents, now)
        raw += subjectContradictions(documents, now)

        // Deterministic de-duplication by claim text pair, then stable ordering by
        // severity (CRITICAL first) and re-number the IDs.
        val seen = HashSet<String>()
        val deduped = raw.filter { c ->
            val key = (c.claimA.text + "||" + c.claimB.text).lowercase(Locale.ROOT)
            val rev = (c.claimB.text + "||" + c.claimA.text).lowercase(Locale.ROOT)
            if (seen.contains(key) || seen.contains(rev)) false else { seen.add(key); true }
        }
        return deduped
            .sortedBy { it.severity.ordinal }
            .mapIndexed { i, c -> c.copy(contradictionId = "C-%03d".format(i + 1)) }
    }

    // ---- Explicit rules ----

    private fun explicitContradictions(documents: List<EvidenceDocument>, now: Instant): List<Contradiction> {
        val results = mutableListOf<Contradiction>()
        for (rule in EXPLICIT_RULES) {
            val a = firstMatch(documents, rule.patternA) ?: continue
            val b = firstMatch(documents, rule.patternB) ?: continue
            if (a.docIndex == b.docIndex && a.line == b.line) continue
            val respondent = EntityExtractor.authorOf(b.doc) ?: EntityExtractor.authorOf(a.doc) ?: "Unknown Respondent"
            results += Contradiction(
                contradictionId = "C-000",
                brainSource = "B1-ContradictionBrain",
                category = rule.category,
                type = rule.type,
                respondent = respondent,
                claimA = a.toClaim(StatementType.CLAIM),
                claimB = b.toClaim(StatementType.CLAIM),
                severity = rule.severity,
                description = "${a.text.trim()} ↔ ${b.text.trim()}",
                legalSignificance = rule.legalSignificance,
                applicableLaw = rule.applicableLaw,
                confidence = Confidence.VERY_HIGH,
                patternIndicator = false,
                timestamp = now.toString()
            )
        }
        return results
    }

    private data class LineHit(val docIndex: Int, val doc: EvidenceDocument, val line: Int, val text: String) {
        fun toClaim(type: StatementType) = ContradictionClaim(
            text = text.trim(), source = doc.fileName, evidenceId = doc.evidenceId,
            page = 1, line = line, sha512 = doc.sha512, statementType = type
        )
    }

    private fun firstMatch(documents: List<EvidenceDocument>, pattern: Regex): LineHit? {
        documents.forEachIndexed { di, doc ->
            doc.text.lines().forEachIndexed { li, line ->
                if (pattern.containsMatchIn(line)) return LineHit(di, doc, li + 1, line)
            }
        }
        return null
    }

    // ---- Subject-based statement pairing ----

    private fun subjectContradictions(documents: List<EvidenceDocument>, now: Instant): List<Contradiction> {
        val statements = EntityExtractor.statements(documents)
            .filter { it.subject != ContradictionCategory.OTHER && it.polarity != 0 }
        val bySubject = statements.groupBy { it.subject }
        val results = mutableListOf<Contradiction>()

        for ((subject, group) in bySubject) {
            val negatives = group.filter { it.polarity < 0 }
            val positives = group.filter { it.polarity > 0 }
            for (neg in negatives) {
                for (pos in positives) {
                    if (neg.line == pos.line && neg.doc.evidenceId == pos.doc.evidenceId) continue
                    val severity = severity(neg, pos)
                    val type = contradictionType(neg, pos)
                    val respondent = EntityExtractor.authorOf(pos.doc) ?: EntityExtractor.authorOf(neg.doc) ?: "Unknown Respondent"
                    results += Contradiction(
                        contradictionId = "C-000",
                        brainSource = "B1-ContradictionBrain",
                        category = subject,
                        type = type,
                        respondent = respondent,
                        claimA = ContradictionClaim(neg.text, neg.doc.fileName, neg.doc.evidenceId, 1, neg.line, neg.doc.sha512, neg.statementType),
                        claimB = ContradictionClaim(pos.text, pos.doc.fileName, pos.doc.evidenceId, 1, pos.line, pos.doc.sha512, pos.statementType),
                        severity = severity,
                        description = legalSignificanceFor(subject),
                        legalSignificance = legalSignificanceFor(subject),
                        applicableLaw = lawsFor(subject),
                        confidence = if (severity == Severity.CRITICAL) Confidence.VERY_HIGH else Confidence.HIGH,
                        patternIndicator = supportsRacketeering(neg, pos),
                        timestamp = now.toString()
                    )
                }
            }
        }
        return results
    }

    /** Severity scoring (build spec Section 9.3). */
    private fun severity(a: Statement, b: Statement): Severity {
        var score = 0
        if (a.statementType == StatementType.SWORN_STATEMENT || b.statementType == StatementType.SWORN_STATEMENT) score += 40
        if (a.statementType == StatementType.CONTEMPORANEOUS || b.statementType == StatementType.CONTEMPORANEOUS) score += 30
        if (involvesFinancial(a, b)) score += 20
        if (involvesBlankSignature(a, b)) score += 25
        if (supportsRacketeering(a, b)) score += 15
        return when {
            score >= 70 -> Severity.CRITICAL
            score >= 55 -> Severity.VERY_HIGH
            score >= 40 -> Severity.HIGH
            score >= 25 -> Severity.MODERATE
            else -> Severity.LOW
        }
    }

    private fun contradictionType(a: Statement, b: Statement): ContradictionType {
        val actionWords = Regex("""paid|collected|demanded|signed|accepted|proceeded|escalated|removed""", RegexOption.IGNORE_CASE)
        val eitherAction = actionWords.containsMatchIn(a.text) || actionWords.containsMatchIn(b.text) ||
            a.statementType == StatementType.DEMAND || b.statementType == StatementType.DEMAND
        return when {
            eitherAction -> ContradictionType.ACTION_VS_WORDS
            kotlin.math.abs(a.polarity) == 1 && kotlin.math.abs(b.polarity) == 1 -> ContradictionType.DIRECT_NEGATION
            else -> ContradictionType.IMPLIED_CONTRADICTION
        }
    }

    private fun involvesFinancial(a: Statement, b: Statement): Boolean {
        val r = Regex("""\br\s?\d|fee|payment|deposit|rent|million""", RegexOption.IGNORE_CASE)
        return r.containsMatchIn(a.text) || r.containsMatchIn(b.text)
    }

    private fun involvesBlankSignature(a: Statement, b: Statement): Boolean {
        val r = Regex("""blank|countersign|unsigned|not signed|never signed""", RegexOption.IGNORE_CASE)
        return r.containsMatchIn(a.text) || r.containsMatchIn(b.text)
    }

    private fun supportsRacketeering(a: Statement, b: Statement): Boolean {
        val r = Regex("""racketeer|pattern|five-step|enterprise""", RegexOption.IGNORE_CASE)
        return r.containsMatchIn(a.text) || r.containsMatchIn(b.text)
    }

    private fun legalSignificanceFor(subject: ContradictionCategory): String = when (subject) {
        ContradictionCategory.GOODWILL_VALUE -> "Denial of compensable goodwill contradicted by conduct treating goodwill as a valuable asset."
        ContradictionCategory.CONTRACT_VALIDITY -> "Reliance on contract terms while denying the contract's existence or validity."
        ContradictionCategory.SIGNATURE_STATUS -> "Document presented as binding despite a missing or blank signature."
        ContradictionCategory.SECTION_12B -> "Asserted PPA compliance without the mandatory Section 12B arbitration referral."
        ContradictionCategory.COMPENSATION -> "Simultaneous denial of compensable value and demand for payment for that value."
        ContradictionCategory.PERJURY -> "Sworn statement to court contradicted by sealed contemporaneous evidence."
        ContradictionCategory.COERCION -> "Narrative of consent contradicted by a contemporaneous record of duress or isolation."
        ContradictionCategory.OTHER -> "Material contradiction between two statements."
    }

    private fun lawsFor(subject: ContradictionCategory): List<String> = when (subject) {
        ContradictionCategory.GOODWILL_VALUE -> listOf("SA Common Law - Fraud", "Petroleum Products Act 120 of 1977 s.12B")
        ContradictionCategory.CONTRACT_VALIDITY -> listOf("Contract Law - Common Law", "Companies Act 71 of 2008")
        ContradictionCategory.SIGNATURE_STATUS -> listOf("Contract Law - Common Law", "ECT Act 25 of 2002")
        ContradictionCategory.SECTION_12B -> listOf("Petroleum Products Act 120 of 1977 s.12B")
        ContradictionCategory.COMPENSATION -> listOf("SA Common Law - Fraud")
        ContradictionCategory.PERJURY -> listOf("SA Common Law - Perjury", "Constitution of RSA 1996 s.34")
        ContradictionCategory.COERCION -> listOf("SA Common Law - Fraud", "Prevention of Organised Crime Act 121 of 1998")
        ContradictionCategory.OTHER -> listOf("SA Common Law - Fraud")
    }
}
