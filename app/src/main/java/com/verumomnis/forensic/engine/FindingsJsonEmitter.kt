package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.Contradiction
import com.verumomnis.forensic.model.ForensicFindings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant

/**
 * Engine-to-G3 contract serializer for the G3 Hybrid Report Pipeline (GHRP).
 *
 * Serializes deterministic [ForensicFindings] into the Findings JSON schema
 * (v1.0.0) that Gemma 3 narrates from. Every record is anchored to a source
 * document, page/line, and SHA-512 hash. G3-raised candidates are recorded in
 * the identical format but labelled as pending verification.
 */
object FindingsJsonEmitter {

    const val FINDINGS_JSON_VERSION = "1.0.0"
    const val ENGINE_VERSION = "5.3.1c"

    const val STATUS_ENGINE_VERIFIED = "ENGINE-VERIFIED"
    const val STATUS_G3_CANDIDATE = "G3-RAISED CANDIDATE - PENDING VERIFICATION"
    const val STATUS_CANDIDATE_PROMOTED = "CANDIDATE PROMOTED - ENGINE-VERIFIED"
    const val STATUS_CANDIDATE_REJECTED = "CANDIDATE REJECTED - REASON LOGGED"

    @Serializable
    data class FindingsJsonDocument(
        val engine_version: String,
        val findings_json_version: String,
        val generated_utc: String,
        val source_bundle: String,
        val case_ids: List<String> = emptyList(),
        val supplement_date: String? = null,
        val new_contradictions: Int? = null,
        val re_anchored_known: Int? = null,
        val integrity_findings: List<String> = emptyList(),
        val contradictions: List<FindingsJsonRecord>
    )

    @Serializable
    data class FindingsJsonRecord(
        val contradiction_id: String,
        val type: String,
        val severity: String,
        val confidence: String,
        val proposition_a_text: String,
        val proposition_a_actor: String,
        val proposition_b_text: String,
        val proposition_b_actor: String,
        val conflict_description: String,
        val source_document: String = "",
        val source_page: Int = 0,
        val source_line: Int = 0,
        val sha512_anchor: String = "",
        val extraction_method: String = "",
        val temporal_analysis: JsonElement? = null,
        val detected_fact: JsonElement? = null,
        val logical_pattern: JsonElement? = null,
        val legal_hypothesis: JsonElement? = null,
        val verification_status: String
    )

    /** Vault filename for the findings JSON artefact produced by a scan. */
    fun findingsFileName(caseName: String, now: Instant): String {
        val timestamp = now.toString().take(19).replace(":", "-")
        return "findings_${caseName}_${timestamp}.json"
    }

    /** Serialize a full [ForensicFindings] object into the findings JSON contract. */
    fun emit(findings: ForensicFindings, caseName: String, now: Instant): String {
        val records = findings.contradictions.map { contradictionToRecord(it) }
        val document = FindingsJsonDocument(
            engine_version = ENGINE_VERSION,
            findings_json_version = FINDINGS_JSON_VERSION,
            generated_utc = now.toString(),
            source_bundle = caseName,
            case_ids = listOf(caseName),
            integrity_findings = emptyList(),
            contradictions = records
        )
        return Json.encodeToString(document)
    }

    /** Convert one deterministic-engine [Contradiction] into a findings JSON record. */
    fun contradictionToRecord(c: Contradiction): FindingsJsonRecord {
        val type = c.engineType.takeIf { it.isNotBlank() } ?: c.type.name
        val actorA = c.respondent.takeIf { it.isNotBlank() } ?: c.claimA.source
        val actorB = c.respondent.takeIf { it.isNotBlank() } ?: c.claimB.source

        val detectedFact = buildJsonObject {
            put("fact_text", JsonPrimitive(c.description.ifBlank { c.claimA.text }))
            put("source_document", JsonPrimitive(c.claimA.source))
            put("source_page", JsonPrimitive(c.claimA.page))
            put("source_line", JsonPrimitive(c.claimA.line))
            put("sha512_hash", JsonPrimitive(c.claimA.sha512))
            put("extraction_method", JsonPrimitive("engine"))
            put("confidence", JsonPrimitive(c.confidence.name))
        }

        val logicalPattern = buildJsonObject {
            put("pattern_type", JsonPrimitive(type))
            put("pattern_description", JsonPrimitive(c.description.ifBlank { "Contradiction between ${c.claimA.source} and ${c.claimB.source}" }))
            put("supporting_facts", buildJsonArray {
                add(JsonPrimitive(c.claimA.text))
                add(JsonPrimitive(c.claimB.text))
            })
            put("contradiction_score", JsonNull)
            put("detector_version", JsonPrimitive(ENGINE_VERSION))
        }

        return FindingsJsonRecord(
            contradiction_id = c.contradictionId,
            type = type,
            severity = c.severity.name,
            confidence = c.confidence.name,
            proposition_a_text = c.claimA.text,
            proposition_a_actor = actorA,
            proposition_b_text = c.claimB.text,
            proposition_b_actor = actorB,
            conflict_description = c.description,
            source_document = c.claimA.source,
            source_page = c.claimA.page,
            source_line = c.claimA.line,
            sha512_anchor = c.claimA.sha512,
            extraction_method = "engine",
            temporal_analysis = null,
            detected_fact = detectedFact,
            logical_pattern = logicalPattern,
            legal_hypothesis = null,
            verification_status = STATUS_ENGINE_VERIFIED
        )
    }

    /**
     * Record a contradiction G3 spotted in the sealed vault that the engine missed.
     *
     * The returned record is labelled [STATUS_G3_CANDIDATE] so it can never be
     * mistaken for an engine-verified finding. Promotion to engine-verified status
     * happens only after engine re-run or human sign-off.
     */
    fun raiseG3Candidate(
        candidateId: String,
        contradictionType: String,
        propositionAText: String,
        propositionBText: String,
        propositionAActor: String,
        propositionBActor: String,
        conflictDescription: String,
        sourceDocument: String,
        sourcePage: Int,
        sha512Anchor: String,
        severity: String = "MODERATE",
        confidence: String = "MODERATE",
        sourceLine: Int = 0,
        g3Model: String = "gemma-3-4b-it"
    ): FindingsJsonRecord {
        val extractionMethod = "G3 vault review ($g3Model) over sealed bundle"
        val detectedFact = buildJsonObject {
            put("fact_text", JsonPrimitive(conflictDescription))
            put("source_document", JsonPrimitive(sourceDocument))
            put("source_page", JsonPrimitive(sourcePage))
            put("source_line", JsonPrimitive(sourceLine))
            put("sha512_hash", JsonPrimitive(sha512Anchor))
            put("extraction_method", JsonPrimitive(extractionMethod))
            put("confidence", JsonPrimitive(confidence))
        }
        val logicalPattern = buildJsonObject {
            put("pattern_type", JsonPrimitive(contradictionType))
            put("pattern_description", JsonPrimitive(conflictDescription))
            put("supporting_facts", buildJsonArray {
                add(JsonPrimitive(propositionAText))
                add(JsonPrimitive(propositionBText))
            })
            put("contradiction_score", JsonNull)
            put("detector_version", JsonPrimitive("G3-CANDIDATE ($g3Model)"))
        }
        return FindingsJsonRecord(
            contradiction_id = candidateId,
            type = contradictionType,
            severity = severity,
            confidence = confidence,
            proposition_a_text = propositionAText,
            proposition_a_actor = propositionAActor,
            proposition_b_text = propositionBText,
            proposition_b_actor = propositionBActor,
            conflict_description = conflictDescription,
            source_document = sourceDocument,
            source_page = sourcePage,
            source_line = sourceLine,
            sha512_anchor = sha512Anchor,
            extraction_method = extractionMethod,
            temporal_analysis = null,
            detected_fact = detectedFact,
            logical_pattern = logicalPattern,
            legal_hypothesis = null,
            verification_status = STATUS_G3_CANDIDATE
        )
    }
}
