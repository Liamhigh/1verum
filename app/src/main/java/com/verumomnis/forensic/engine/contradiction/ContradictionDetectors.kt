package com.verumomnis.forensic.engine.contradiction

import java.util.concurrent.atomic.AtomicInteger

/**
 * 10 Contradiction Detectors — v5.2.9.
 * Each detector identifies a specific pattern of contradiction.
 * Results are deduplicated and sorted by severity.
 */
object ContradictionDetectors {

    private val counter = AtomicInteger(0)
    fun resetCounter() { counter.set(0) }
    private fun nextId(): String = "C-${counter.incrementAndNext().toString().padStart(4, '0')}"
    private fun AtomicInteger.incrementAndNext(): Int {
        incrementAndGet()
        return get()
    }

    // ==================== HELPERS ====================

    private fun severityScore(claimA: EngineClaim, claimB: EngineClaim, base: Int = 0): EngineSeverity {
        var score = base
        if (claimA.sourceType == EngineStatementType.SWORN_STATEMENT || claimB.sourceType == EngineStatementType.SWORN_STATEMENT) score += 40
        if (claimA.sourceType == EngineStatementType.CONTEMPORANEOUS || claimB.sourceType == EngineStatementType.CONTEMPORANEOUS) score += 30
        if (claimA.sourceType == EngineStatementType.ADMISSION || claimB.sourceType == EngineStatementType.ADMISSION) score += 20
        if (claimA.subject == "GOODWILL_VALUE" || claimB.subject == "GOODWILL_VALUE") score += 15
        return when {
            score >= 70 -> EngineSeverity.VERY_HIGH
            score >= 50 -> EngineSeverity.HIGH
            score >= 30 -> EngineSeverity.MODERATE
            score >= 10 -> EngineSeverity.LOW
            else -> EngineSeverity.INSUFFICIENT
        }
    }

    private val NEGATIONS = listOf(
        "no " to "", "not " to "", "false" to "true", "deny" to "admit",
        "never" to "always", "did not" to "did", "does not" to "does"
    )
    private val OPPOSITE_WORDS = setOf("no", "not", "never", "false", "deny", "refuse", "none")

    private fun isOpposing(a: EngineClaim, b: EngineClaim): Boolean {
        val textA = a.value.lowercase()
        val textB = b.value.lowercase()
        for ((neg, _) in NEGATIONS) {
            if (neg in textA && neg !in textB && a.subject == b.subject) return true
            if (neg in textB && neg !in textA && a.subject == b.subject) return true
        }
        val wordsA = textA.split(Regex("\\s+")).toSet()
        val wordsB = textB.split(Regex("\\s+")).toSet()
        if (wordsA.isNotEmpty() && wordsB.isNotEmpty()) {
            val overlap = wordsA.intersect(wordsB).size
            val union = wordsA.union(wordsB).size
            if (union > 0 && overlap.toDouble() / union < 0.2) {
                if (wordsA.any { it in OPPOSITE_WORDS } || wordsB.any { it in OPPOSITE_WORDS }) return true
            }
        }
        return a.subject == b.subject && a.predicate == b.predicate && a.value != b.value
    }

    private fun createContradiction(
        claimA: EngineClaim, claimB: EngineClaim,
        cType: EngineContradictionType, severity: EngineSeverity,
        baseConfidence: EngineConfidence,
        patternType: String, description: String,
        facts: List<String>, score: Double
    ): EngineContradiction {
        val fact = DetectedFact(
            factText = "Actor \"${claimA.actor}\" stated: \"${claimA.value}\" | Actor \"${claimB.actor}\" stated: \"${claimB.value}\"",
            sourceDocument = "${claimA.documentId} + ${claimB.documentId}",
            sourcePage = claimA.pageNumber,
            sourceLine = 0,
            sha512Hash = claimA.sha512Hash,
            extractionMethod = patternType,
            confidence = baseConfidence
        )
        val pattern = LogicalPattern(patternType, description, facts, score)
        val hypothesis = when (cType) {
            EngineContradictionType.JUDICIAL_VS_DOCUMENTARY,
            EngineContradictionType.PERJURY_BY_TIMELINE -> LegalHypothesis(
                suggestedOffence = "Perjury / Fraud on the Court",
                legalBasis = "Contradiction between sworn statement and documentary evidence",
                jurisdictionalNote = "Varies by jurisdiction — requires legal review",
                requiredAdditionalEvidence = listOf(
                    "Sworn statement transcript", "Original documentary evidence", "Authentication of documents"
                )
            )
            else -> null
        }
        return EngineContradiction(
            contradictionId = nextId(), type = cType, severity = severity,
            confidence = ConfidenceCalibrator.calibrate(baseConfidence, cType.name, true),
            detectedFact = fact, logicalPattern = pattern, legalHypothesis = hypothesis,
            propositionAText = claimA.value, propositionBText = claimB.value,
            propositionAActor = claimA.actor, propositionBActor = claimB.actor,
            conflictDescription = "${claimA.actor} claims: \"${claimA.value}\" but ${claimB.actor} claims: \"${claimB.value}\""
        )
    }

    // ==================== DETECTOR 1: STATEMENT_VS_STATEMENT ====================
    fun detectStatementVsStatement(claims: List<EngineClaim>): List<EngineContradiction> {
        val results = mutableListOf<EngineContradiction>()
        for (i in claims.indices) {
            for (j in i + 1 until claims.size) {
                val a = claims[i]; val b = claims[j]
                if (a.actor == b.actor && isOpposing(a, b)) {
                    val (isSem, semScore) = SemanticAnalyzer.detectSemanticContradiction(a, b)
                    if (isSem || (a.subject == b.subject && a.predicate == b.predicate && a.value != b.value)) {
                        results += createContradiction(a, b,
                            EngineContradictionType.STATEMENT_VS_STATEMENT,
                            severityScore(a, b, if (isSem) (semScore * 30).toInt() else 20),
                            if (isSem) EngineConfidence.HIGH else EngineConfidence.MODERATE,
                            "SAME_ACTOR_OPPOSING_CLAIMS",
                            "Same actor \"${a.actor}\" made contradictory statements on the same subject",
                            listOf(a.value, b.value), if (isSem) semScore else 0.5
                        )
                    }
                }
            }
        }
        return results
    }

    // ==================== DETECTOR 2: STATEMENT_VS_EVIDENCE ====================
    fun detectStatementVsEvidence(claims: List<EngineClaim>): List<EngineContradiction> {
        val results = mutableListOf<EngineContradiction>()
        val sworn = claims.filter { it.sourceType == EngineStatementType.SWORN_STATEMENT }
        val docs = claims.filter {
            it.sourceType == EngineStatementType.CONTEMPORANEOUS ||
            it.sourceType == EngineStatementType.CONTRACT_CLAUSE
        }
        for (s in sworn) {
            for (d in docs) {
                if (s.subject == d.subject && isOpposing(s, d)) {
                    results += createContradiction(s, d,
                        EngineContradictionType.STATEMENT_VS_EVIDENCE,
                        EngineSeverity.VERY_HIGH, EngineConfidence.VERY_HIGH,
                        "SWORN_STATEMENT_VS_DOCUMENTARY_EVIDENCE",
                        "Sworn statement contradicted by contemporaneous documentary evidence",
                        listOf(s.value, d.value), 0.9
                    )
                }
            }
        }
        return results
    }

    // ==================== DETECTOR 3: FINANCIAL_IRREGULARITY ====================
    fun detectFinancialIrregularity(claims: List<EngineClaim>): List<EngineContradiction> {
        val keywords = listOf("payment", "amount", "balance", "deposit", "withdrawal", "transfer", "fee", "rent", "commission")
        val financial = claims.filter { c -> keywords.any { c.value.lowercase().contains(it) } }
        val results = mutableListOf<EngineContradiction>()
        for (i in financial.indices) {
            for (j in i + 1 until financial.size) {
                val a = financial[i]; val b = financial[j]
                if (a.actor == b.actor && a.subject == b.subject && a.value != b.value) {
                    val (isSem, semScore) = SemanticAnalyzer.detectSemanticContradiction(a, b)
                    if (isSem) {
                        results += createContradiction(a, b,
                            EngineContradictionType.FINANCIAL_IRREGULARITY,
                            EngineSeverity.HIGH, EngineConfidence.HIGH,
                            "FINANCIAL_AMOUNT_DISCREPANCY",
                            "Same actor reported inconsistent financial figures for the same subject",
                            listOf(a.value, b.value), semScore
                        )
                    }
                }
            }
        }
        return results
    }

    // ==================== DETECTOR 4: JUDICIAL_VS_DOCUMENTARY ====================
    fun detectJudicialVsDocumentary(claims: List<EngineClaim>): List<EngineContradiction> {
        val judicial = claims.filter { it.sourceType == EngineStatementType.JUDICIAL_RECORD }
        val docs = claims.filter {
            it.sourceType == EngineStatementType.CONTRACT_CLAUSE ||
            it.sourceType == EngineStatementType.CONTEMPORANEOUS
        }
        val results = mutableListOf<EngineContradiction>()
        for (j in judicial) {
            for (d in docs) {
                if (j.subject == d.subject && isOpposing(j, d)) {
                    results += createContradiction(j, d,
                        EngineContradictionType.JUDICIAL_VS_DOCUMENTARY,
                        EngineSeverity.VERY_HIGH, EngineConfidence.VERY_HIGH,
                        "COURT_STATEMENT_VS_SEALED_DOCUMENT",
                        "Statement made to judicial body contradicted by sealed documentary evidence",
                        listOf(j.value, d.value), 0.95
                    )
                }
            }
        }
        return results
    }

    // ==================== DETECTOR 5: TEMPORAL_CONTRADICTION ====================
    fun detectTemporalContradiction(claims: List<EngineClaim>): List<EngineContradiction> {
        val results = mutableListOf<EngineContradiction>()
        val dayMs = 1000L * 60 * 60 * 24
        for (i in claims.indices) {
            for (j in i + 1 until claims.size) {
                val a = claims[i]; val b = claims[j]
                if (a.actor == b.actor && a.subject == b.subject && a.date != null && b.date != null && a.date != b.date) {
                    if (isOpposing(a, b)) {
                        val gapDays = kotlin.math.abs(a.date - b.date) / dayMs
                        val sev = when {
                            gapDays > 730 -> EngineSeverity.VERY_HIGH
                            gapDays > 365 -> EngineSeverity.HIGH
                            else -> EngineSeverity.MODERATE
                        }
                        results += createContradiction(a, b,
                            EngineContradictionType.TEMPORAL_CONTRADICTION, sev, EngineConfidence.HIGH,
                            "TEMPORALLY_SEPARATED_CONTRADICTORY_STATEMENTS",
                            "Same actor made contradictory statements ${gapDays} days apart",
                            listOf(a.value, b.value, "Gap: $gapDays days"), kotlin.math.min(0.9, gapDays / 730.0)
                        )
                    }
                }
            }
        }
        return results
    }

    // ==================== DETECTOR 6: CONSCIOUSNESS_OF_GUILT ====================
    fun detectConsciousnessOfGuilt(claims: List<EngineClaim>): List<EngineContradiction> {
        val results = mutableListOf<EngineContradiction>()
        val dayMs = 1000L * 60 * 60 * 24
        for (i in claims.indices) {
            for (j in i + 1 until claims.size) {
                val a = claims[i]; val b = claims[j]
                if (a.actor == b.actor && a.date != null && b.date != null) {
                    val gapDays = kotlin.math.abs(a.date - b.date) / dayMs
                    if (gapDays > 730 && isOpposing(a, b)) {
                        results += createContradiction(a, b,
                            EngineContradictionType.CONSCIOUSNESS_OF_GUILT,
                            EngineSeverity.VERY_HIGH, EngineConfidence.VERY_HIGH,
                            "CONSCIOUSNESS_OF_GUILT_730DAY_GAP",
                            "Actor made contradictory statements ${gapDays} days apart (>2yr gap proves consciousness of guilt)",
                            listOf(a.value, b.value, "Gap: $gapDays days"), 0.95
                        )
                    }
                }
            }
        }
        return results
    }

    // ==================== DETECTOR 7: BEHAVIORAL ====================
    fun detectBehavioral(claims: List<EngineClaim>): List<EngineContradiction> {
        val behavioral = listOf("agreed", "promised", "committed", "guaranteed", "assured")
        val denial = listOf("denied", "refused", "rejected", "declined", "never")
        return claims.filter { c ->
            val v = c.value.lowercase()
            behavioral.any { v.contains(it) } && denial.any { v.contains(it) }
        }.map { c ->
            createContradiction(c, c, EngineContradictionType.BEHAVIORAL,
                EngineSeverity.MODERATE, EngineConfidence.MODERATE,
                "BEHAVIORAL_INCONSISTENCY",
                "Actor's statement contains both commitment language and denial language",
                listOf(c.value), 0.5
            )
        }
    }

    // ==================== DETECTOR 8: SHAM_TRANSACTION ====================
    fun detectShamTransaction(claims: List<EngineClaim>): List<EngineContradiction> {
        val sham = listOf("arm's length", "independent", "unrelated party", "third party", "at market value")
        val control = listOf("same director", "common ownership", "related party", "subsidiary", "parent company", "controlled by")
        val shamClaims = claims.filter { c -> sham.any { c.value.lowercase().contains(it) } }
        val ctrlClaims = claims.filter { c -> control.any { c.value.lowercase().contains(it) } }
        val results = mutableListOf<EngineContradiction>()
        for (s in shamClaims) {
            for (ctrl in ctrlClaims) {
                if (s.actor == ctrl.actor || s.subject == ctrl.subject) {
                    results += createContradiction(s, ctrl,
                        EngineContradictionType.SHAM_TRANSACTION, EngineSeverity.HIGH, EngineConfidence.HIGH,
                        "SHAM_TRANSACTION_DUAL_CONTROL",
                        "Entity claims arm's-length transaction but evidence shows common control",
                        listOf(s.value, ctrl.value), 0.85
                    )
                }
            }
        }
        return results
    }

    // ==================== DETECTOR 9: TACIT_LEASE_VIOLATION ====================
    fun detectTacitLeaseViolation(claims: List<EngineClaim>): List<EngineContradiction> {
        val rent = listOf("rent", "lease", "monthly payment", "occupation", "possession")
        val deny = listOf("no contract", "no lease", "expired", "not valid", "no agreement")
        val rentClaims = claims.filter { c -> rent.any { c.value.lowercase().contains(it) } }
        val denyClaims = claims.filter { c -> deny.any { c.value.lowercase().contains(it) } }
        val results = mutableListOf<EngineContradiction>()
        for (r in rentClaims) {
            for (d in denyClaims) {
                if (r.actor == d.actor) {
                    results += createContradiction(r, d,
                        EngineContradictionType.TACIT_LEASE_VIOLATION, EngineSeverity.HIGH, EngineConfidence.HIGH,
                        "RENT_ACCEPTANCE_WHILE_DENYING_CONTRACT",
                        "Actor collected rent/payments while simultaneously denying contract existence",
                        listOf(r.value, d.value), 0.9
                    )
                }
            }
        }
        return results
    }

    // ==================== DETECTOR 10: POST_EXPIRY_ENFORCEMENT ====================
    fun detectPostExpiryEnforcement(claims: List<EngineClaim>): List<EngineContradiction> {
        val expiry = listOf("expired", "terminated", "ended", "lapsed", "no longer valid")
        val enforce = listOf("enforce", "demand", "require", "compel", "pursuant to")
        val expClaims = claims.filter { c -> expiry.any { c.value.lowercase().contains(it) } }
        val enfClaims = claims.filter { c -> enforce.any { c.value.lowercase().contains(it) } }
        val results = mutableListOf<EngineContradiction>()
        for (e in expClaims) {
            for (enf in enfClaims) {
                if (e.actor == enf.actor && e.subject == enf.subject) {
                    results += createContradiction(e, enf,
                        EngineContradictionType.POST_EXPIRY_ENFORCEMENT, EngineSeverity.VERY_HIGH, EngineConfidence.VERY_HIGH,
                        "ENFORCING_CLAUSE_AFTER_ITS_OWN_EXPIRY",
                        "Actor enforced a clause after claiming the underlying agreement had expired",
                        listOf(e.value, enf.value), 0.95
                    )
                }
            }
        }
        return results
    }

    // ==================== MASTER DETECT ALL ====================

    private val ALL_DETECTORS: List<(List<EngineClaim>) -> List<EngineContradiction>> = listOf(
        ::detectStatementVsStatement,
        ::detectStatementVsEvidence,
        ::detectFinancialIrregularity,
        ::detectJudicialVsDocumentary,
        ::detectTemporalContradiction,
        ::detectConsciousnessOfGuilt,
        ::detectBehavioral,
        ::detectShamTransaction,
        ::detectTacitLeaseViolation,
        ::detectPostExpiryEnforcement
    )

    /** Run all 10 detectors, deduplicate, and sort by severity (highest first). */
    fun detectAll(claims: List<EngineClaim>): List<EngineContradiction> {
        val all = ALL_DETECTORS.flatMap { it(claims) }
        val seen = mutableSetOf<String>()
        val unique = mutableListOf<EngineContradiction>()
        for (c in all) {
            val key = "${c.propositionAActor}:${c.propositionBActor}:${c.type}:${c.logicalPattern.patternType}"
            if (key !in seen) {
                seen += key
                unique += c
            }
        }
        return unique.sortedByDescending { EngineScores.severityScore(it.severity) }
    }
}
