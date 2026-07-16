package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.ContradictionPatternPatch
import com.verumomnis.forensic.model.FraudKeywordPatch

/**
 * Integrates engine update patches into the live contradiction engine.
 *
 * The contradiction engine reads both its built-in patterns AND any
 * patterns stored by the EngineUpdateReceiver. This means the engine
 * automatically improves over time as new fraud patterns are detected
 * globally and distributed via the auto-update system.
 *
 * Usage:
 *   val updater = ContradictionEngineUpdater(context)
 *   val enhancedPatterns = updater.getAllContradictionPatterns()
 *   // Pass enhancedPatterns to the contradiction engine scan
 */
class ContradictionEngineUpdater(private val updateReceiver: EngineUpdateReceiver) {

    /**
     * Get all contradiction patterns: built-in + updates.
     */
    fun getAllContradictionPatterns(): List<EngineContradictionPattern> {
        val builtIn = getBuiltInPatterns()
        val fromUpdates = updateReceiver.getStoredContradictionPatterns().map { it.toEnginePattern() }
        return builtIn + fromUpdates
    }

    /**
     * Get all fraud keywords: built-in + updates.
     */
    fun getAllFraudKeywords(): List<EngineFraudKeyword> {
        val builtIn = getBuiltInFraudKeywords()
        val fromUpdates = updateReceiver.getStoredFraudKeywords().map { it.toEngineKeyword() }
        return builtIn + fromUpdates
    }

    /**
     * Check if a document text matches any update-derived contradiction pattern.
     * This is called during the forensic scan to detect newly-discovered fraud types.
     */
    fun matchUpdatePatterns(text: String): List<PatternMatch> {
        val patterns = updateReceiver.getStoredContradictionPatterns()
        return patterns.mapNotNull { pattern ->
            val matched = pattern.triggerPatterns.any { regex ->
                Regex(regex, RegexOption.IGNORE_CASE).containsMatchIn(text)
            }
            if (matched) {
                PatternMatch(
                    patternId = pattern.patternId,
                    patternName = pattern.name,
                    category = pattern.category,
                    severity = pattern.severity,
                    aiConfidence = pattern.aiConfidence,
                    legalBasis = pattern.legalBasis
                )
            } else null
        }
    }

    /**
     * Calculate an enhanced fraud score using both built-in and update-derived keywords.
     */
    fun calculateEnhancedFraudScore(text: String): Int {
        val allKeywords = getAllFraudKeywords()
        var score = 0
        allKeywords.forEach { keyword ->
            val occurrences = keyword.keyword.toRegex(RegexOption.IGNORE_CASE).findAll(text).count()
            if (occurrences > 0) {
                score += keyword.weight * occurrences
            }
        }
        return score.coerceIn(0, 100)
    }

    // ------------------------------------------------------------------
    // Built-in patterns (fallback when no updates available)
    // ------------------------------------------------------------------

    private fun getBuiltInPatterns(): List<EngineContradictionPattern> = listOf(
        EngineContradictionPattern(
            patternId = "BUILT-001",
            name = "Goodwill Denial vs Gratitude Claim",
            category = "CONTRACT",
            severity = "HIGH",
            triggerRegexes = listOf("goodwill.*no.*value", "grateful.*exit", "forfeit.*goodwill"),
            legalBasis = "AllFuels v Highcock precedent — contractual estoppel",
            aiConfidence = 0.95
        ),
        EngineContradictionPattern(
            patternId = "BUILT-002",
            name = "Binding Lease vs Informal Arrangement",
            category = "CONTRACT",
            severity = "HIGH",
            triggerRegexes = listOf("binding.*lease", "informal.*arrangement", "no.*written.*agreement"),
            legalBasis = "Statute of Frauds — written agreement requirements",
            aiConfidence = 0.92
        ),
        EngineContradictionPattern(
            patternId = "BUILT-003",
            name = "Statutory Compliance Claim vs Non-Compliance Evidence",
            category = "REGULATORY",
            severity = "CRITICAL",
            triggerRegexes = listOf("section 12b", "ppa.*compliance", "no.*referral"),
            legalBasis = "Petroleum Products Act, Section 12B — mandatory referral",
            aiConfidence = 0.98
        )
    )

    private fun getBuiltInFraudKeywords(): List<EngineFraudKeyword> = listOf(
        EngineFraudKeyword("goodwill forfeiture", "CONTRACT", 25),
        EngineFraudKeyword("extension fee", "FINANCIAL", 20),
        EngineFraudKeyword("no written agreement", "DOCUMENT", 15),
        EngineFraudKeyword("section 12b", "REGULATORY", 30),
        EngineFraudKeyword("grateful exit", "COMMUNICATION", 20),
        EngineFraudKeyword("informal arrangement", "CONTRACT", 15),
        EngineFraudKeyword("witness removed", "BEHAVIORAL", 25),
        EngineFraudKeyword("mentally broken", "BEHAVIORAL", 20)
    )

    // ------------------------------------------------------------------
    // Data classes
    // ------------------------------------------------------------------

    data class EngineContradictionPattern(
        val patternId: String,
        val name: String,
        val category: String,
        val severity: String,
        val triggerRegexes: List<String>,
        val legalBasis: String,
        val aiConfidence: Double
    )

    data class EngineFraudKeyword(
        val keyword: String,
        val category: String,
        val weight: Int
    )

    data class PatternMatch(
        val patternId: String,
        val patternName: String,
        val category: String,
        val severity: String,
        val aiConfidence: Double,
        val legalBasis: String
    )
}

// Extension functions to convert patch types to engine types
private fun ContradictionPatternPatch.toEnginePattern(): ContradictionEngineUpdater.EngineContradictionPattern {
    return ContradictionEngineUpdater.EngineContradictionPattern(
        patternId = patternId,
        name = name,
        category = category,
        severity = severity,
        triggerRegexes = triggerPatterns,
        legalBasis = legalBasis,
        aiConfidence = aiConfidence
    )
}

private fun FraudKeywordPatch.toEngineKeyword(): ContradictionEngineUpdater.EngineFraudKeyword {
    return ContradictionEngineUpdater.EngineFraudKeyword(
        keyword = keyword,
        category = category,
        weight = weight
    )
}
