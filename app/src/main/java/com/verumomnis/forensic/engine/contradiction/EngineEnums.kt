package com.verumomnis.forensic.engine.contradiction

import kotlinx.serialization.Serializable

/**
 * 16 contradiction types detected by the v5.2.9 engine.
 * The 7 constitutional categories map to these 16 detection patterns.
 */
enum class EngineContradictionType {
    STATEMENT_VS_STATEMENT,
    STATEMENT_VS_EVIDENCE,
    OMISSION,
    BEHAVIORAL,
    FINANCIAL_IRREGULARITY,
    JUDICIAL_VS_DOCUMENTARY,
    TEMPORAL_CONTRADICTION,
    CONSCIOUSNESS_OF_GUILT,
    PERJURY_BY_TIMELINE,
    PATTERN_OF_RACKETEERING,
    REGULATORY_CAPTURE,
    SHAM_TRANSACTION,
    FRAUD_ON_THE_COURT,
    CORPORATE_VEIL_ABUSE,
    TACIT_LEASE_VIOLATION,
    POST_EXPIRY_ENFORCEMENT
}

/** Ordinal severity — no percentages, ever (Constitution Directive 1). */
enum class EngineSeverity { VERY_HIGH, HIGH, MODERATE, LOW, INSUFFICIENT }

/**
 * Ordinal confidence — DETERMINISTIC is highest (mathematical proof).
 * No probability scores. No 0-1 floats exposed to consumers.
 */
enum class EngineConfidence {
    DETERMINISTIC, VERY_HIGH, HIGH, MODERATE, LOW, INSUFFICIENT
}

/** Statement types for evidence classification. */
enum class EngineStatementType {
    CLAIM, DENIAL, ADMISSION, DEMAND, PROMISE, THREAT,
    SWORN_STATEMENT, CONTEMPORANEOUS, JUDICIAL_RECORD, CONTRACT_CLAUSE
}

/** The 7 constitutional subjects + RACKETEERING + OTHER. */
enum class EngineSubject {
    GOODWILL_VALUE, CONTRACT_VALIDITY, SIGNATURE_STATUS, SECTION_12B,
    COMPENSATION, PERJURY, COERCION, RACKETEERING, OTHER
}

/** File types supported for evidence ingestion. */
enum class EngineFileType { PDF, IMAGE, AUDIO, EMAIL, CHAT_LOG, ZIP, DOCX, XLSX, TXT, CSV, UNKNOWN }

/** Score conversion utilities — internal use only, never exposed as confidence. */
object EngineScores {
    fun severityScore(s: EngineSeverity): Int = when (s) {
        EngineSeverity.VERY_HIGH -> 5
        EngineSeverity.HIGH -> 4
        EngineSeverity.MODERATE -> 3
        EngineSeverity.LOW -> 2
        EngineSeverity.INSUFFICIENT -> 1
    }

    fun confidenceScore(c: EngineConfidence): Double = when (c) {
        EngineConfidence.DETERMINISTIC -> 1.0
        EngineConfidence.VERY_HIGH -> 0.9
        EngineConfidence.HIGH -> 0.75
        EngineConfidence.MODERATE -> 0.5
        EngineConfidence.LOW -> 0.25
        EngineConfidence.INSUFFICIENT -> 0.0
    }

    fun scoreToConfidence(s: Double): EngineConfidence = when {
        s >= 0.95 -> EngineConfidence.DETERMINISTIC
        s >= 0.80 -> EngineConfidence.VERY_HIGH
        s >= 0.60 -> EngineConfidence.HIGH
        s >= 0.35 -> EngineConfidence.MODERATE
        s >= 0.15 -> EngineConfidence.LOW
        else -> EngineConfidence.INSUFFICIENT
    }
}
