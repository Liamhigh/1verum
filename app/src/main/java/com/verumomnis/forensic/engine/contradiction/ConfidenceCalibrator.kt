package com.verumomnis.forensic.engine.contradiction

/**
 * Confidence Calibrator — v5.3.1c.
 * Per-detector false-positive rates from validation against
 * 111 AllFuels contradictions + 47 DIGSIM contradictions.
 */
object ConfidenceCalibrator {

    /** Per-detector FP rates — calibrated against AllFuels 111 + DIGSIM 47 contradictions. */
    private val FP_RATES = mapOf(
        // v5.2.9 base types (16 types)
        "STATEMENT_VS_STATEMENT" to 0.15,
        "STATEMENT_VS_EVIDENCE" to 0.10,
        "FINANCIAL_IRREGULARITY" to 0.05,
        "JUDICIAL_VS_DOCUMENTARY" to 0.08,
        "TEMPORAL_CONTRADICTION" to 0.12,
        "CONSCIOUSNESS_OF_GUILT" to 0.10,
        "BEHAVIORAL" to 0.25,
        "SHAM_TRANSACTION" to 0.10,
        "TACIT_LEASE_VIOLATION" to 0.05,
        "POST_EXPIRY_ENFORCEMENT" to 0.08,
        "OMISSION" to 0.15,
        "PATTERN_OF_RACKETEERING" to 0.10,
        "REGULATORY_CAPTURE" to 0.12,
        "FRAUD_ON_THE_COURT" to 0.05,
        "CORPORATE_VEIL_ABUSE" to 0.10,
        "PERJURY_BY_TIMELINE" to 0.08,
        // v5.3.1c DIGSIM types (6 new types)
        "DEFECTIVE_JURAT" to 0.05,
        "PROTECTION_ORDER_AS_LEVERAGE" to 0.08,
        "FALSE_ALLEGATION_IN_AFFIDAVIT" to 0.06,
        "TEMPORAL_PRECEDENCE_CONFLICT" to 0.10,
        "PROCESS_REMEDY_CONFLICT" to 0.07,
        "CHARACTER_ASSASSINATION" to 0.15
    )

    private const val SEMANTIC_AGREEMENT_BOOST = 0.20

    /** Calibrate confidence based on detector-specific false-positive rates. */
    fun calibrate(
        baseConfidence: EngineConfidence,
        detectorType: String,
        semanticAgreement: Boolean = false
    ): EngineConfidence {
        val fpRate = FP_RATES[detectorType] ?: 0.15
        var score = EngineScores.confidenceScore(baseConfidence)
        score *= (1.0 - fpRate)
        if (semanticAgreement) {
            score = score.coerceAtMost(1.0 - 1e-9) + SEMANTIC_AGREEMENT_BOOST
            score = score.coerceAtMost(1.0)
        }
        return EngineScores.scoreToConfidence(score)
    }

    /** Returns calibration report for audit trail. */
    fun reportCalibration(): Map<String, String> = mapOf(
        "methodology" to "Per-detector false-positive rates from validation against 111 AllFuels + 47 DIGSIM contradictions",
        "lastCalibrated" to "2026-07-13",
        "validationCases" to "ALLFUELS-2026 (111 contradictions) + DIGSIM-2026 (47 contradictions)",
        "engineVersion" to "v5.3.1c",
        "constitution" to "v6.0 Final",
        "detectorCount" to "16",
        "typeCount" to "43",
        "semanticAgreementBoost" to SEMANTIC_AGREEMENT_BOOST.toString()
    )
}
