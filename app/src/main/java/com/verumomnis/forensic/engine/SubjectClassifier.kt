package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.ContradictionCategory
import java.util.Locale

/**
 * Maps a statement to one of the 7 canonical contradiction subjects and assigns a
 * polarity within that subject (build spec Section 9.2). Two statements in the
 * same subject with opposite polarity are candidate contradictions.
 */
object SubjectClassifier {

    private data class Rule(
        val subject: ContradictionCategory,
        val triggers: List<String>,
        val negative: List<String>, // denials / "no value" style
        val positive: List<String>  // assertions / actions proving the opposite
    )

    private val RULES = listOf(
        Rule(
            ContradictionCategory.GOODWILL_VALUE,
            triggers = listOf("goodwill", "compensable", "entrenched value", "brand fee", "forfeiture", "extension fee"),
            negative = listOf("no compensable goodwill", "goodwill has no value", "no value", "not compensable", "no compensable"),
            positive = listOf("demanded", "brand fee", "extension fee", "forfeit goodwill", "forfeiture", "quantifiable asset", "pay r", "r3.8m", "r3.25m")
        ),
        Rule(
            ContradictionCategory.SIGNATURE_STATUS,
            triggers = listOf("signature", "signed", "countersign", "countersignature", "blank", "presented as", "bound by"),
            negative = listOf("never countersigned", "never signed", "not signed", "unsigned", "blank", "signature space blank", "signature lines blank"),
            positive = listOf("presented as binding", "presented as valid", "bound by", "binding agreement", "valid agreement", "presented as")
        ),
        Rule(
            ContradictionCategory.CONTRACT_VALIDITY,
            triggers = listOf("contract", "agreement", "binding", "mou", "lease", "occupation", "oral extension"),
            negative = listOf("no binding contract", "no contract", "never countersigned", "no oral extension", "not binding", "no right of occupation"),
            positive = listOf("collected rent", "collected r", "interim terms", "accepted", "rent payments", "monthly rent", "binding", "87 consecutive")
        ),
        Rule(
            ContradictionCategory.SECTION_12B,
            triggers = listOf("section 12b", "arbitration", "referral", "business zone", "ppa"),
            negative = listOf("no section 12b", "no referral", "proceeded with eviction", "no arbitration", "verbal notice only"),
            positive = listOf("comply with ppa", "we comply", "filed section 12b", "12-month notice")
        ),
        Rule(
            ContradictionCategory.COMPENSATION,
            triggers = listOf("fee", "payment", "compensation", "deposit", "balance", "rent escalat"),
            negative = listOf("no compensable", "no compensation", "no value"),
            positive = listOf("pay r", "brand fee", "extension fee", "deposit", "balance", "demanded r", "escalated")
        ),
        Rule(
            ContradictionCategory.PERJURY,
            triggers = listOf("sworn", "constitutional court", "cct237", "cct19", "para", "perjury", "affidavit"),
            negative = listOf("no contract existed", "abandoned", "no oral agreement", "does not abolish", "only applies to premature"),
            positive = listOf("rent collected", "mou clause", "binding", "signed forfeiture", "before court", "7 years", "five-step pattern")
        ),
        Rule(
            ContradictionCategory.COERCION,
            triggers = listOf("grateful", "exit gracefully", "coercion", "duress", "isolation", "witness", "non-committal"),
            negative = listOf("grateful", "exit gracefully", "ready to exit", "agreed to exit", "decided not to renew", "for discussion"),
            positive = listOf("non-committal", "negotiated", "more time", "directed", "lawyer", "removed", "witness", "refused", "expired 18 months")
        )
    )

    fun subjectOf(text: String): ContradictionCategory {
        val t = text.lowercase(Locale.ROOT)
        return RULES.firstOrNull { rule -> rule.triggers.any { t.contains(it) } }?.subject
            ?: ContradictionCategory.OTHER
    }

    fun polarity(subject: ContradictionCategory, text: String): Int {
        if (subject == ContradictionCategory.OTHER) return 0
        val rule = RULES.first { it.subject == subject }
        val t = text.lowercase(Locale.ROOT)
        val neg = rule.negative.any { t.contains(it) }
        val pos = rule.positive.any { t.contains(it) }
        return when {
            neg && !pos -> -1
            pos && !neg -> 1
            else -> 0
        }
    }
}
