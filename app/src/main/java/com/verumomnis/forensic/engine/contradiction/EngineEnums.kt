package com.verumomnis.forensic.engine.contradiction

/**
 * 43 contradiction types detected by the v5.3.1c engine.
 * The 7 constitutional categories map to these 43 detection patterns.
 *
 * Engine: v5.3.1c | Seal: VO-CE-v531c-DIGSIM-20260713
 * Constitution: v6.0 Final | Brains: B1-B11
 */
enum class EngineContradictionType {
    // === v5.2.9 Legacy (16 types) ===
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
    POST_EXPIRY_ENFORCEMENT,

    // === v5.3.0 Expansion (14 types) ===
    DOCUMENT_TAMPERING,
    WITNESS_INTIMIDATION,
    EVIDENCE_DESTRUCTION,
    CONFLICT_OF_INTEREST,
    BRIBERY_CORRUPTION,
    MONEY_LAUNDERING,
    IDENTITY_FRAUD,
    FORGERY,
    EXTORTION,
    EMBEZZLEMENT,
    TAX_EVASION,
    INSIDER_TRADING,
    CYBERCRIME_EVIDENCE,
    HUMAN_RIGHTS_VIOLATION,

    // === v5.3.1 Expansion (7 types) ===
    MANDATE_ABANDONMENT,
    ACKNOWLEDGE_THEN_DENY,
    OWNERSHIP_MISREPRESENTATION,
    COSTING_MANIPULATION,
    COMMUNITY_MANIPULATION,
    INSTITUTIONAL_SILENCE,
    ICCPR_REMEDY_DENIAL,

    // === v5.3.1c DIGSIM Expansion (6 types) ===
    DEFECTIVE_JURAT,
    PROTECTION_ORDER_AS_LEVERAGE,
    FALSE_ALLEGATION_IN_AFFIDAVIT,
    TEMPORAL_PRECEDENCE_CONFLICT,
    PROCESS_REMEDY_CONFLICT,
    CHARACTER_ASSASSINATION,

    // === v5.3.1c Ported High-Value Detectors (9 types) ===
    NO_COUNTERSIGNATURE_TRAP,
    GOODWILL_FORFEITURE_SWINDLE,
    MANUFACTURED_CONSENT,
    FABRICATED_DECOY_EVIDENCE,
    DATA_BREACH_ENABLED_FRAUD,
    SPOLIATION_OF_EVIDENCE,
    ATTORNEY_OBSTRUCTION,
    DEFAMATION_THREAT,
    TECHNOLOGY_REFUSAL_LIABILITY
}

/** 17 serial fraud patterns detected by the engine. */
enum class SerialFraudPattern {
    P001_GOODWILL_FORFEITURE,
    P002_RENT_WHILE_DENYING_CONTRACT,
    P003_UNSIGNED_DOCUMENT_ENFORCEMENT,
    P004_SECTION_12B_ARBITRATION_AVOIDANCE,
    P005_COMPENSATION_DEMAND_WITHOUT_RIGHT,
    P006_DUAL_CONTROL_SHAM,
    P007_JUDICIAL_PERJURY_PATTERN,
    P008_CROSS_BORDER_OPPRESSION,
    P009_FRANCHISE_SYSTEMATIC_EVICT,
    P010_EXCLUSIVITY_DENIAL_PATTERN,
    P011_GASLIGHTING_ISOLATION,
    P012_DIGITAL_EVIDENCE_TAMPERING,
    P013_BANKING_FRAUD_PATTERN,
    P014_LAW_ENFORCEMENT_INTERFERENCE,
    P015_DEFECTIVE_JURAT,
    P016_PROTECTION_ORDER_LEVERAGE,
    P017_PROCESS_REMEDY_DENIAL,
    P018_GOODWILL_FORFEITURE_SWINDLE,
    P019_NO_COUNTERSIGNATURE_TRAP,
    P020_MANUFACTURED_CONSENT,
    P021_FABRICATED_DECOY_EVIDENCE,
    P022_DATA_BREACH_ENABLED_FRAUD,
    P023_SPOLIATION_OF_EVIDENCE,
    P024_ATTORNEY_OBSTRUCTION,
    P025_DEFAMATION_THREAT,
    P026_TECHNOLOGY_REFUSAL_LIABILITY
}

/** Ordinal severity — no percentages, ever (Constitution v6.0 Final Directive 1). */
enum class EngineSeverity { VERY_HIGH, HIGH, MODERATE, LOW, INSUFFICIENT }

/**
 * Confidence levels — DETERMINISTIC is highest (mathematical proof).
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

/** 11 Brain identifiers (B1-B11). */
enum class Brain {
    B1_CONTRADICTION,
    B2_DOCUMENT,
    B3_COMMUNICATIONS,
    B4_BEHAVIORAL,
    B5_TIMELINE,
    B6_FINANCIAL,
    B7_LEGAL,
    B8_AUDIO,
    B9_RESEARCH,
    B10_HUMAN_RIGHTS,
    B11_INSTITUTIONAL
}

/** Score conversion utilities — internal use only. */
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
