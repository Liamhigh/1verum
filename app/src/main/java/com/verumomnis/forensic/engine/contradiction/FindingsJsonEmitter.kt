package com.verumomnis.forensic.engine.contradiction

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * G3 Hybrid Report Pipeline (GHRP) — Findings JSON emitter for Android.
 *
 * Status: RATIFIED — BINDING (founder directive, 2026-07-14).
 * Spec: G3_HYBRID_REPORT_PIPELINE.md | Schema: FINDINGS_JSON_SCHEMA.json
 *
 * Builds on VerumContradictionEngine. Deletes nothing, changes nothing.
 * After a scan, this emits the Findings JSON contract v1.0.0 that Gemma 3
 * narrates from the sealed vault: one record per contradiction, no prose,
 * no percentages, ordinal confidence only, every record anchored.
 *
 * If it is not anchored, it is not emitted.
 */
object FindingsJsonEmitter {

    const val GHRP_VERSION = "1.0.0"
    const val FINDINGS_JSON_VERSION = "1.0.0"

    const val STATUS_ENGINE_VERIFIED = "ENGINE-VERIFIED"
    const val STATUS_G3_CANDIDATE = "G3-RAISED CANDIDATE - PENDING VERIFICATION"
    const val STATUS_CANDIDATE_PROMOTED = "CANDIDATE PROMOTED - ENGINE-VERIFIED"
    const val STATUS_CANDIDATE_REJECTED = "CANDIDATE REJECTED - REASON LOGGED"

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /** One contradiction, one record — snake_case per the findings schema. */
    @Serializable
    data class FindingsContradictionRecord(
        @SerialName("contradiction_id") val contradictionId: String,
        @SerialName("type") val type: String,
        @SerialName("severity") val severity: String,
        @SerialName("confidence") val confidence: String,
        @SerialName("proposition_a_text") val propositionAText: String,
        @SerialName("proposition_a_actor") val propositionAActor: String,
        @SerialName("proposition_b_text") val propositionBText: String,
        @SerialName("proposition_b_actor") val propositionBActor: String,
        @SerialName("conflict_description") val conflictDescription: String,
        @SerialName("source_document") val sourceDocument: String,
        @SerialName("source_page") val sourcePage: Int,
        @SerialName("source_line") val sourceLine: Int,
        @SerialName("sha512_anchor") val sha512Anchor: String,
        @SerialName("extraction_method") val extractionMethod: String,
        @SerialName("legal_hypothesis") val legalHypothesis: FindingsLegalHypothesis? = null,
        @SerialName("verification_status") val verificationStatus: String,
        @SerialName("rejection_reason") val rejectionReason: String? = null
    )

    @Serializable
    data class FindingsLegalHypothesis(
        @SerialName("suggested_offence") val suggestedOffence: String,
        @SerialName("legal_basis") val legalBasis: String,
        @SerialName("jurisdictional_note") val jurisdictionalNote: String,
        @SerialName("required_additional_evidence") val requiredAdditionalEvidence: List<String>,
        @SerialName("is_hypothesis") val isHypothesis: Boolean = true,
        @SerialName("requires_human_review") val requiresHumanReview: Boolean = true
    )

    /** The complete Findings JSON document for one engine scan. */
    @Serializable
    data class FindingsJsonDocument(
        @SerialName("engine_version") val engineVersion: String,
        @SerialName("findings_json_version") val findingsJsonVersion: String = FINDINGS_JSON_VERSION,
        @SerialName("generated_utc") val generatedUtc: String,
        @SerialName("source_bundle") val sourceBundle: String,
        @SerialName("case_id") val caseId: String,
        @SerialName("case_ids") val caseIds: List<String>,
        @SerialName("corpus_sha512") val corpusSha512: String,
        @SerialName("engine_verified_count") val engineVerifiedCount: Int,
        @SerialName("g3_candidate_count") val g3CandidateCount: Int,
        @SerialName("integrity_findings") val integrityFindings: List<String>,
        @SerialName("contradictions") val contradictions: List<FindingsContradictionRecord>
    )

    /** Convert one engine contradiction into a findings record. */
    fun recordFromContradiction(contradiction: EngineContradiction): FindingsContradictionRecord {
        require(contradiction.detectedFact.sha512Hash.isNotEmpty()) {
            "GHRP: contradiction ${contradiction.contradictionId} carries no SHA-512 anchor. " +
                "If it is not anchored, it is not emitted."
        }
        val fact = contradiction.detectedFact
        val status = contradiction.verificationStatus["status"] ?: STATUS_ENGINE_VERIFIED
        return FindingsContradictionRecord(
            contradictionId = contradiction.contradictionId,
            type = contradiction.type.name,
            severity = contradiction.severity.name,
            confidence = contradiction.confidence.name,
            propositionAText = contradiction.propositionAText,
            propositionAActor = contradiction.propositionAActor,
            propositionBText = contradiction.propositionBText,
            propositionBActor = contradiction.propositionBActor,
            conflictDescription = contradiction.conflictDescription,
            sourceDocument = fact.sourceDocument,
            sourcePage = fact.sourcePage,
            sourceLine = fact.sourceLine,
            sha512Anchor = fact.sha512Hash,
            extractionMethod = fact.extractionMethod,
            legalHypothesis = contradiction.legalHypothesis?.let {
                FindingsLegalHypothesis(
                    suggestedOffence = it.suggestedOffence,
                    legalBasis = it.legalBasis,
                    jurisdictionalNote = it.jurisdictionalNote,
                    requiredAdditionalEvidence = it.requiredAdditionalEvidence,
                    isHypothesis = it.isHypothesis,
                    requiresHumanReview = it.requiresHumanReview
                )
            },
            verificationStatus = status
        )
    }

    /** Build the complete Findings JSON document from an engine report. */
    fun fromReport(
        report: EngineForensicReport,
        engineVersion: String = "5.3.1c",
        sourceBundle: String = "",
        caseIds: List<String> = emptyList(),
        integrityFindings: List<String> = emptyList(),
        extraRecords: List<FindingsContradictionRecord> = emptyList(),
        generatedUtc: String = java.time.Instant.now().toString()
    ): FindingsJsonDocument {
        require(generatedUtc.isNotBlank()) {
            "generatedUtc must be a non-blank UTC timestamp (ISO-8601). Inject a fixed value for deterministic tests."
        }
        val records = report.contradictions.map { recordFromContradiction(it) } + extraRecords
        val candidateCount = records.count { it.verificationStatus == STATUS_G3_CANDIDATE }
        return FindingsJsonDocument(
            engineVersion = engineVersion,
            generatedUtc = generatedUtc,
            sourceBundle = sourceBundle.ifEmpty { report.caseId },
            caseId = report.caseId,
            caseIds = caseIds,
            corpusSha512 = report.corpusHash,
            engineVerifiedCount = records.size - candidateCount,
            g3CandidateCount = candidateCount,
            integrityFindings = integrityFindings,
            contradictions = records
        )
    }

    /** Serialize a Findings JSON document for sealing into the vault. */
    fun toJson(document: FindingsJsonDocument): String =
        json.encodeToString(document)
}
