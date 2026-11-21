package com.verumomnis.forensic.engine

import com.verumomnis.forensic.core.Safeguards
import com.verumomnis.forensic.model.BehavioralAnalysis
import com.verumomnis.forensic.model.Confidence
import com.verumomnis.forensic.model.Contradiction
import com.verumomnis.forensic.model.ContradictionCategory
import com.verumomnis.forensic.model.FinancialAnalysis
import com.verumomnis.forensic.model.GuardianAssessment
import com.verumomnis.forensic.model.GuardianViolation
import com.verumomnis.forensic.model.GuardianViolationType
import com.verumomnis.forensic.model.Severity
import com.verumomnis.forensic.security.EicarScanner
import java.time.Instant

/**
 * B9 — Guardian / Red Team Brain.
 *
 * B9 never votes on contradictions. It validates that the entire process stays
 * within Constitutional bounds: no weaponization (Article X), no coercion, no
 * privatization, and no EICAR test malware. It also performs cross-brain sanity
 * checks (the previous R&D role) and returns notes when brain outputs disagree.
 */
object GuardianBrain {

    private val COERCION_PATTERNS = listOf(
        "keep quiet", "don't tell anyone", "this stays between us", "you'll regret",
        "or else", "remember what happened to", "keep your mouth shut", "intimidate",
        "threaten", "pay you to forget", "hush money", "silence you", "destroy the evidence",
        "make this go away", "you know what will happen", "watch your back"
    )

    private val ARTICLE_X_KEYWORDS = listOf(
        "kill chain", "target acquisition", "artillery correction", "lethal targeting",
        "battlefield", "munition", "missile", "drone strike", "warfare", "neutralize target",
        "collateral damage", "armed conflict", "rules of engagement", "fire mission",
        "sniper", "ied", "improvised explosive", "bioweapon", "chemical weapon"
    )

    // Never find a weapon keyword inside ordinary words (e.g. IED in "denied").
    // Keep boundaries Unicode-aware and permit PDF line wrapping within phrases.
    private fun wholeTermPattern(term: String): Regex {
        val plural = if (term in setOf(
                "ied", "munition", "missile", "drone strike", "sniper",
                "improvised explosive", "bioweapon", "chemical weapon"
            )) "(?:s)?" else ""
        val phrase = term.split(' ').joinToString("[\\s\\p{Z}]+") { Regex.escape(it) }
        return Regex(
            "(?<![\\p{L}\\p{M}\\p{N}_])$phrase$plural(?![\\p{L}\\p{M}\\p{N}_])",
            RegexOption.IGNORE_CASE
        )
    }

    private val articleXPatterns = ARTICLE_X_KEYWORDS.associateWith(::wholeTermPattern)
    private val coercionPatterns = COERCION_PATTERNS.associateWith(::wholeTermPattern)

    fun analyze(
        documents: List<EvidenceDocument>,
        audioTranscripts: List<String> = emptyList(),
        contradictions: List<Contradiction> = emptyList(),
        financial: FinancialAnalysis? = null,
        behavioral: BehavioralAnalysis? = null,
        now: Instant
    ): GuardianAssessment {
        val violations = mutableListOf<GuardianViolation>()
        val notes = mutableListOf<String>()

        val allTexts = documents.map { it.text } + audioTranscripts

        // Article X — anti-war / anti-weaponization hard stop.
        allTexts.forEachIndexed { index, text ->
            val evidenceId = documents.getOrNull(index)?.evidenceId ?: ""
            articleXPatterns.forEach { (keyword, pattern) ->
                if (pattern.containsMatchIn(text)) {
                    violations += GuardianViolation(
                        type = GuardianViolationType.ARTICLE_X_WEAPONIZATION,
                        severity = Severity.CRITICAL,
                        description = "Article X screening indicator: keyword \"$keyword\" matched in evidence text. " +
                            "A keyword match alone does not establish intent or wrongdoing.",
                        evidenceId = evidenceId,
                        trigger = keyword,
                        timestamp = now.toString()
                    )
                }
            }
            coercionPatterns.forEach { (pattern, matcher) ->
                if (matcher.containsMatchIn(text)) {
                    violations += GuardianViolation(
                        type = GuardianViolationType.COERCION_ATTEMPT,
                        severity = Severity.VERY_HIGH,
                        description = "Potential coercion / suppression language: \"$pattern\"",
                        evidenceId = evidenceId,
                        trigger = pattern,
                        timestamp = now.toString()
                    )
                }
            }
        }

        // Constitutional privatization firewall.
        allTexts.forEachIndexed { index, text ->
            if (Safeguards.isPrivatizationAttempt(text)) {
                violations += GuardianViolation(
                    type = GuardianViolationType.ANTI_PRIVATIZATION,
                    severity = Severity.HIGH,
                    description = "Constitutional privatization attempt detected.",
                    evidenceId = documents.getOrNull(index)?.evidenceId ?: "",
                    timestamp = now.toString()
                )
            }
        }

        // EICAR anti-malware test pattern.
        documents.forEach { doc ->
            if (EicarScanner.isEicar(doc.text)) {
                violations += GuardianViolation(
                    type = GuardianViolationType.EICAR_TEST_PATTERN,
                    severity = Severity.MODERATE,
                    description = "EICAR anti-malware test pattern found in evidence text.",
                    evidenceId = doc.evidenceId,
                    timestamp = now.toString()
                )
            }
        }

        // Cross-brain sanity checks (previous R&D role).
        if (contradictions.isEmpty() && behavioral != null && !behavioral.isEmpty()) {
            notes += "B1 found no contradictions but B4 flagged behavioural signals — recommend deeper B1 pass."
        }
        if (contradictions.any { it.applicableLaw.any { law -> law.contains("Fraud", ignoreCase = true) } } &&
            financial?.flaggedAnomalies.isNullOrEmpty()
        ) {
            notes += "Fraud mapped by B7 but no financial anomaly from B6 — recommend deeper financial audit."
        }
        if (contradictions.any { it.patternIndicator }) {
            notes += "Racketeering pattern indicator present — recommend POCA 121/1998 enterprise-liability review."
        }
        if (contradictions.any { it.category == ContradictionCategory.COERCION }) {
            notes += "Coercion-category contradiction confirmed — silence ledger should be reviewed."
        }

        val hardStop = violations.any { it.type == GuardianViolationType.ARTICLE_X_WEAPONIZATION }
        return GuardianAssessment(violations = violations, notes = notes, hardStopRequired = hardStop)
    }

    /**
     * Validates a user chat prompt before it reaches any model. Returns a non-empty
     * assessment with `hardStopRequired = true` if the prompt violates Article X.
     */
    fun validatePrompt(prompt: String, now: Instant = Instant.now()): GuardianAssessment {
        val violations = articleXPatterns.filterValues { it.containsMatchIn(prompt) }.keys.map { keyword ->
            GuardianViolation(
                type = GuardianViolationType.ARTICLE_X_WEAPONIZATION,
                severity = Severity.CRITICAL,
                description = "Article X anti-weaponization keyword in user prompt: \"$keyword\"",
                trigger = keyword,
                timestamp = now.toString()
            )
        }
        return GuardianAssessment(
            violations = violations,
            hardStopRequired = violations.isNotEmpty()
        )
    }
}
