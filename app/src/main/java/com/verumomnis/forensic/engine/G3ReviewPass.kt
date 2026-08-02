package com.verumomnis.forensic.engine

import com.verumomnis.forensic.engine.contradiction.G3CandidateRegistry
import com.verumomnis.forensic.llm.Gemma3Runtime
import com.verumomnis.forensic.llm.Gemma3RuntimeProvider
import com.verumomnis.forensic.model.ForensicFindings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant

/**
 * G3 Hybrid Report Pipeline — second-pass vault review.
 *
 * After the deterministic engine has run and sealed its findings, Gemma 3
 * re-reads the ingested evidence looking for contradictions the engine did
 * not emit. Anything it finds is recorded through [G3CandidateRegistry] as a
 * G3-RAISED CANDIDATE — anchored to a real ingested document and that
 * document's SHA-512, never to model output — and merged into the findings
 * JSON labelled pending verification.
 *
 * The anchor rule is enforced structurally: the model only *nominates* a
 * source document by name and page; the SHA-512 anchor is taken from the
 * engine's own ingest record for that document. A candidate naming a document
 * that was never ingested is discarded. If it is not anchored, it is not
 * emitted.
 *
 * When no runtime is installed this pass is a no-op and the pipeline is
 * byte-identical to the pure deterministic engine.
 */
object G3ReviewPass {

    /** Cap on how much evidence text is packed into the review prompt. */
    private const val MAX_DOC_EXCERPT_CHARS = 4000
    private const val MAX_TOTAL_PROMPT_CHARS = 24000
    private const val MAX_CANDIDATES_PER_REVIEW = 12

    data class ReviewOutcome(
        val registry: G3CandidateRegistry,
        val candidateRecords: List<FindingsJsonEmitter.FindingsJsonRecord>
    )

    fun review(
        documents: List<EvidenceDocument>,
        findings: ForensicFindings,
        now: Instant,
        runtime: Gemma3Runtime = Gemma3RuntimeProvider.runtime
    ): ReviewOutcome {
        val registry = G3CandidateRegistry(runtime.modelName)
        if (documents.isEmpty() || !runtime.isAvailable()) {
            return ReviewOutcome(registry, emptyList())
        }
        val response = runtime.generate(buildReviewPrompt(documents, findings), maxTokens = 2048)
            ?: return ReviewOutcome(registry, emptyList())

        // Ambiguous file names would anchor a candidate to the wrong hash —
        // only documents with unique names are eligible anchor targets.
        val docsByName = documents.groupBy { it.fileName }
            .filterValues { it.size == 1 }
            .mapValues { (_, v) -> v.single() }
        val records = parseCandidates(response)
            .take(MAX_CANDIDATES_PER_REVIEW)
            .mapNotNull { candidate ->
                val doc = docsByName[candidate.sourceDocument] ?: return@mapNotNull null
                if (duplicatesEngineFinding(candidate, findings)) return@mapNotNull null
                runCatching {
                    registry.raiseCandidate(
                        contradictionType = candidate.type,
                        propositionAText = candidate.propositionAText,
                        propositionBText = candidate.propositionBText,
                        propositionAActor = candidate.propositionAActor,
                        propositionBActor = candidate.propositionBActor,
                        conflictDescription = candidate.conflictDescription,
                        sourceDocument = doc.fileName,
                        sourcePage = candidate.sourcePage.coerceAtLeast(0),
                        sha512Anchor = doc.sha512,
                        severity = candidate.severity,
                        confidence = candidate.confidence,
                        utc = now.toString()
                    )
                }.getOrNull()
            }
            .map { FindingsJsonEmitter.fromContractRecord(it) }
        return ReviewOutcome(registry, records)
    }

    /** One model-nominated candidate before anchoring/validation. */
    internal data class NominatedCandidate(
        val type: String,
        val propositionAText: String,
        val propositionAActor: String,
        val propositionBText: String,
        val propositionBActor: String,
        val conflictDescription: String,
        val sourceDocument: String,
        val sourcePage: Int,
        val severity: String,
        val confidence: String
    )

    internal fun buildReviewPrompt(documents: List<EvidenceDocument>, findings: ForensicFindings): String = buildString {
        appendLine("You are the Gemma 3 vault reviewer in the Verum Omnis hybrid forensic pipeline.")
        appendLine("The deterministic engine has already run. Your ONLY job is to find contradictions it MISSED.")
        appendLine()
        appendLine("CONTRADICTIONS THE ENGINE ALREADY EMITTED (do NOT repeat these):")
        if (findings.contradictions.isEmpty()) appendLine("  (none)")
        findings.contradictions.forEach { c ->
            appendLine("  - ${c.contradictionId}: \"${c.claimA.text.take(120)}\" VS \"${c.claimB.text.take(120)}\"")
        }
        appendLine()
        appendLine("SEALED EVIDENCE DOCUMENTS:")
        var budget = MAX_TOTAL_PROMPT_CHARS - length
        for (doc in documents) {
            if (budget <= 0) break
            val excerpt = doc.text.take(minOf(MAX_DOC_EXCERPT_CHARS, budget))
            appendLine("--- DOCUMENT: ${doc.fileName} ---")
            appendLine(excerpt)
            budget -= excerpt.length
        }
        appendLine()
        appendLine("RULES:")
        appendLine("- Report ONLY genuine contradictions between two specific statements in the documents above.")
        appendLine("- source_document MUST be one of the exact document names listed above.")
        appendLine("- Every candidate will be labelled G3-RAISED CANDIDATE - PENDING VERIFICATION, never engine-verified.")
        appendLine("- severity and confidence are ordinal only: LOW, MODERATE, HIGH, VERY_HIGH, CRITICAL / LOW, MODERATE, HIGH.")
        appendLine("- If you find nothing, output an empty array: []")
        appendLine()
        appendLine("OUTPUT: a single JSON array, no prose before or after. Each element:")
        appendLine("""{"type":"...","proposition_a_text":"...","proposition_a_actor":"...","proposition_b_text":"...","proposition_b_actor":"...","conflict_description":"...","source_document":"...","source_page":1,"severity":"MODERATE","confidence":"MODERATE"}""")
    }

    /**
     * Extract the first JSON array from the model response and decode each
     * well-formed element. Malformed elements are skipped, a malformed
     * response yields no candidates — the pass never fails the scan.
     */
    internal fun parseCandidates(response: String): List<NominatedCandidate> {
        val start = response.indexOf('[')
        if (start < 0) return emptyList()
        // Bracket-match while tracking string literals so brackets inside
        // quoted evidence text (e.g. "[00:12]") don't derail the scan.
        var depth = 0
        var end = -1
        var inString = false
        var escaped = false
        for (i in start until response.length) {
            val ch = response[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
        }
        if (end < 0) return emptyList()
        val array = runCatching {
            Json.parseToJsonElement(response.substring(start, end + 1)) as? JsonArray
        }.getOrNull() ?: return emptyList()

        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            fun str(key: String): String = (obj[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val type = str("type")
            val a = str("proposition_a_text")
            val b = str("proposition_b_text")
            val sourceDoc = str("source_document")
            if (type.isBlank() || a.isBlank() || b.isBlank() || sourceDoc.isBlank()) return@mapNotNull null
            NominatedCandidate(
                type = type,
                propositionAText = a,
                propositionAActor = str("proposition_a_actor").ifBlank { "Unknown" },
                propositionBText = b,
                propositionBActor = str("proposition_b_actor").ifBlank { "Unknown" },
                conflictDescription = str("conflict_description").ifBlank { "Contradiction between the two propositions." },
                sourceDocument = sourceDoc,
                sourcePage = (obj["source_page"] as? JsonPrimitive)?.intOrNull ?: 0,
                severity = str("severity").ifBlank { "MODERATE" },
                confidence = str("confidence").ifBlank { "MODERATE" }
            )
        }
    }

    /** A candidate that restates an engine finding is a duplicate, not a catch. */
    internal fun duplicatesEngineFinding(candidate: NominatedCandidate, findings: ForensicFindings): Boolean {
        val a = candidate.propositionAText.trim().lowercase()
        val b = candidate.propositionBText.trim().lowercase()
        return findings.contradictions.any { c ->
            val ca = c.claimA.text.trim().lowercase()
            val cb = c.claimB.text.trim().lowercase()
            (overlaps(a, ca) && overlaps(b, cb)) || (overlaps(a, cb) && overlaps(b, ca))
        }
    }

    private fun overlaps(x: String, y: String): Boolean =
        x.isNotEmpty() && y.isNotEmpty() && (x.contains(y) || y.contains(x))
}
