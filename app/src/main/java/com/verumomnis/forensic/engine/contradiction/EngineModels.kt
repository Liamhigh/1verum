package com.verumomnis.forensic.engine.contradiction

import kotlinx.serialization.Serializable

/** Raw evidence extracted from documents — Layer 0 input. */
@Serializable
data class EngineEvidenceAtom(
    val artifactHash: String,
    val pageNumber: Int,
    val lineNumber: Int,
    val timestamp: Long? = null,
    val sourcePath: String,
    val content: String,
    val fileType: EngineFileType = EngineFileType.TXT
)

/** Extracted claim from evidence — Layer 1 input. */
@Serializable
data class EngineClaim(
    val id: String,
    val subject: String,
    val predicate: String,
    val value: String,
    val actor: String,
    val date: Long? = null,
    val sourceType: EngineStatementType = EngineStatementType.CLAIM,
    val sourceLocation: String = "",
    val documentId: String = "",
    val sha512Hash: String = "",
    val pageNumber: Int = 0,
    val context: String = ""
)

/** Layer 1: Immutable fact extracted directly from evidence. No interpretation. */
@Serializable
data class DetectedFact(
    val factText: String,
    val sourceDocument: String,
    val sourcePage: Int,
    val sourceLine: Int,
    val sha512Hash: String,
    val extractionMethod: String,
    val confidence: EngineConfidence
)

/** Layer 2: The logical/mathematical contradiction pattern. Not legal interpretation. */
@Serializable
data class LogicalPattern(
    val patternType: String,
    val patternDescription: String,
    val supportingFacts: List<String>,
    val contradictionScore: Double,
    val detectorVersion: String = "v5.2.9"
)

/**
 * Layer 3: Legal interpretation suggested by the pattern.
 * ALWAYS a hypothesis — AI cannot replace judicial process.
 */
@Serializable
data class LegalHypothesis(
    val suggestedOffence: String,
    val legalBasis: String,
    val jurisdictionalNote: String,
    val requiredAdditionalEvidence: List<String>,
    val isHypothesis: Boolean = true,
    val requiresHumanReview: Boolean = true
)

/** Complete contradiction output — all three layers. */
@Serializable
data class EngineContradiction(
    val contradictionId: String,
    val type: EngineContradictionType,
    val severity: EngineSeverity,
    val confidence: EngineConfidence,
    val detectedFact: DetectedFact,
    val logicalPattern: LogicalPattern,
    val legalHypothesis: LegalHypothesis? = null,
    val propositionAText: String = "",
    val propositionBText: String = "",
    val propositionAActor: String = "",
    val propositionBActor: String = "",
    val temporalAnalysis: Map<String, String>? = null,
    val conflictDescription: String = "",
    val verificationStatus: Map<String, String> = emptyMap()
)

/** Per-actor profile with dishonesty scoring. */
@Serializable
data class ActorProfile(
    val name: String,
    val dishonestyScore: Int = 0, // 0-100, capped
    val flags: List<String> = emptyList(),
    val contradictions: List<String> = emptyList(),
    val statementsMade: Int = 0,
    val statementsDenied: Int = 0
)

/** Triple verification result — Thesis/Antithesis/Synthesis. */
@Serializable
data class EngineTripleVerification(
    val gemma3Status: String = "CONCURS",
    val phi3Status: String = "CONCURS",
    val nineBrainStatus: String = "CONCURS",
    val quorumMet: Boolean = true,
    val discrepancies: List<String> = emptyList()
)

/** Complete forensic report output. */
@Serializable
data class EngineForensicReport(
    val caseId: String,
    val contradictions: List<EngineContradiction>,
    val actorProfiles: List<ActorProfile>,
    val tripleVerification: EngineTripleVerification,
    val corpusHash: String,
    val confidenceCalibration: Map<String, String> = emptyMap(),
    val generatedAt: Long = 0L
)

/** Per-case keyword configuration for entity recognition. */
@Serializable
data class CaseConfig(
    val name: String,
    val liabilityAdmit: List<String>,
    val liabilityDeny: List<String>,
    val liabilityConceal: List<String>,
    val topicKeywords: List<String>,
    val entityKeywords: List<String>,
    val legalSubjects: Map<String, List<String>>
)
