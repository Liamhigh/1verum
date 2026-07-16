package com.verumomnis.forensic.model

import kotlinx.serialization.Serializable

/**
 * Lightweight engine update payload delivered via email or push notification.
 * Contains JSON patches that update the local contradiction engine, fraud
 * detection rules, and entity patterns without requiring a full app update
 * through the Play Store.
 *
 * The update system works as follows:
 *   1. Verification events are collected globally (with consent)
 *   2. Gemma/AI analyses patterns and detects new fraud types
 *   3. EngineUpdateService generates a patch with new rules
 *   4. AutoUpdateDistributor sends mass emails with the JSON payload
 *   5. Devices, banks, and firewalls auto-apply the patch on receipt
 *
 * All patches are cryptographically signed and version-controlled.
 */
@Serializable
data class EngineUpdate(
    /** Unique update identifier. Format: UPD-YYYYMMDD-NNN */
    val updateId: String = "",
    /** Engine version this patch applies to. */
    val version: String = "",
    /** When the update was issued. */
    val issuedAt: String = "",
    /** Priority: routine, important, critical */
    val priority: UpdatePriority = UpdatePriority.ROUTINE,
    /** Human-readable description of what changed. */
    val description: String = "",
    /** Statistics from the verification analytics that triggered this update. */
    val statistics: UpdateStatistics = UpdateStatistics(),
    /** The actual patches to apply. */
    val patches: UpdatePatches = UpdatePatches(),
    /** Version compatibility range. */
    val compatibility: VersionCompatibility = VersionCompatibility(),
    /** SHA-256 signature of the patch data for integrity verification. */
    val signature: String = "",
    /** URL to full changelog and documentation. */
    val changelogUrl: String = "",
    /** Whether this update requires user confirmation before applying. */
    val requiresConfirmation: Boolean = false
)

@Serializable
data class UpdateStatistics(
    /** Total verifications analysed to generate this update. */
    val verificationsAnalyzed: Int = 0,
    /** Number of new fraud patterns detected. */
    val newPatternsDetected: Int = 0,
    /** Geographic regions where patterns were clustered. */
    val geographicClusters: List<String> = emptyList(),
    /** AI confidence in the new patterns (0.0 - 1.0). */
    val confidence: Double = 0.0,
    /** Number of devices that will receive this update. */
    val estimatedRecipients: Int = 0
)

@Serializable
data class UpdatePatches(
    /** New contradiction detection patterns. */
    val contradictionPatterns: List<ContradictionPatternPatch> = emptyList(),
    /** New fraud keywords and scoring weights. */
    val fraudKeywords: List<FraudKeywordPatch> = emptyList(),
    /** New detection rules for the fraud engine. */
    val detectionRules: List<DetectionRulePatch> = emptyList(),
    /** New or updated statute mappings. */
    val statuteMappings: List<StatuteMappingPatch> = emptyList(),
    /** New entity extraction patterns (company names, person names, etc.). */
    val entityPatterns: List<EntityPatternPatch> = emptyList(),
    /** Updated confidence thresholds. */
    val thresholdAdjustments: List<ThresholdAdjustment> = emptyList()
)

@Serializable
data class ContradictionPatternPatch(
    /** Unique pattern ID. */
    val patternId: String = "",
    /** Human-readable name. */
    val name: String = "",
    /** The contradiction category this pattern belongs to. */
    val category: String = "",
    /** Severity: LOW, MEDIUM, HIGH, CRITICAL */
    val severity: String = "MEDIUM",
    /** Regex patterns that trigger this contradiction. */
    val triggerPatterns: List<String> = emptyList(),
    /** Keywords that must be present for the pattern to match. */
    val requiredKeywords: List<String> = emptyList(),
    /** The legal/statutory basis for this contradiction type. */
    val legalBasis: String = "",
    /** Example text that would trigger this pattern. */
    val example: String = "",
    /** Whether this pattern replaces an existing one. */
    val replacesPatternId: String? = null,
    /** Confidence score from AI analysis (0.0 - 1.0). */
    val aiConfidence: Double = 0.0
)

@Serializable
data class FraudKeywordPatch(
    /** The keyword or phrase. */
    val keyword: String = "",
    /** Fraud category: impersonation, coercion, financial, legal, etc. */
    val category: String = "",
    /** Weight/score contribution when detected (0-100). */
    val weight: Int = 10,
    /** Context patterns where this keyword is significant. */
    val contextPatterns: List<String> = emptyList(),
    /** Whether this is a new keyword or an update to an existing one. */
    val isNew: Boolean = true
)

@Serializable
data class DetectionRulePatch(
    /** Rule identifier. */
    val ruleId: String = "",
    /** Rule name. */
    val name: String = "",
    /** Rule type: metadata_tamper, document_inconsistency, signature_mismatch, etc. */
    val ruleType: String = "",
    /** The detection logic as a simple expression. */
    val condition: String = "",
    /** Score to add when this rule triggers. */
    val score: Int = 0,
    /** Whether this rule is enabled by default. */
    val enabled: Boolean = true
)

@Serializable
data class StatuteMappingPatch(
    /** Jurisdiction code: ZA, ZA-KZN, ZA-GP, US, UK, etc. */
    val jurisdiction: String = "",
    /** Statute citation. */
    val citation: String = "",
    /** Relevant section(s). */
    val sections: List<String> = emptyList(),
    /** Keywords that link evidence to this statute. */
    val triggerKeywords: List<String> = emptyList(),
    /** Penalty information. */
    val penalty: String = ""
)

@Serializable
data class EntityPatternPatch(
    /** Entity type: COMPANY, PERSON, LOCATION, STATUTE, etc. */
    val entityType: String = "",
    /** Regex pattern for extraction. */
    val pattern: String = "",
    /** Examples that should match. */
    val examples: List<String> = emptyList(),
    /** Whether this is a South African-specific pattern. */
    val saSpecific: Boolean = false
)

@Serializable
data class ThresholdAdjustment(
    /** What threshold is being adjusted: fraud_score, contradiction_confidence, etc. */
    val thresholdName: String = "",
    /** Old value. */
    val oldValue: Double = 0.0,
    /** New value. */
    val newValue: Double = 0.0,
    /** Reason for the adjustment. */
    val reason: String = ""
)

@Serializable
data class VersionCompatibility(
    /** Minimum engine version that can apply this patch. */
    val minEngineVersion: String = "5.3.0",
    /** Maximum engine version that can apply this patch. */
    val maxEngineVersion: String = "5.4.0",
    /** Specific app versions compatible with this patch. */
    val compatibleAppVersions: List<String> = emptyList()
)

enum class UpdatePriority {
    ROUTINE,      // Monthly maintenance, minor pattern additions
    IMPORTANT,    // Significant new fraud patterns detected
    CRITICAL      // Active fraud campaign detected, immediate protection needed
}

/**
 * Record of an update that has been applied to this device.
 */
@Serializable
data class AppliedUpdateRecord(
    val updateId: String,
    val appliedAt: String,
    val versionBefore: String,
    val versionAfter: String,
    val patchesApplied: Int,
    val success: Boolean
)

/**
 * A verification event collected from the verification page (with user consent).
 * These events feed the analytics engine that generates update patches.
 */
@Serializable
data class VerificationEvent(
    /** Anonymised seal ID (first 16 chars of SHA-512). */
    val sealIdPrefix: String = "",
    /** Verification timestamp. */
    val verifiedAt: String = "",
    /** Result: VERIFIED, TAMPERED, NOT_FOUND */
    val result: String = "",
    /** Fraud score if present (0-100). */
    val fraudScore: Int = 0,
    /** Geographic region (anonymised). */
    val region: String = "",
    /** Whether the document was password-protected (.voice). */
    val wasEncrypted: Boolean = false,
    /** Whether identity data was present in the seal. */
    val hadIdentity: Boolean = false,
    /** Whether fraud was detected. */
    val fraudDetected: Boolean = false,
    /** Document type inferred from content. */
    val documentType: String = "",
    /** Seal type: private, commercial, law_enforcement */
    val sealType: String = ""
)
