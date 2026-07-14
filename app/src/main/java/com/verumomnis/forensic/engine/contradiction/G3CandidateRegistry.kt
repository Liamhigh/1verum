package com.verumomnis.forensic.engine.contradiction

/**
 * G3 Hybrid Report Pipeline (GHRP) — candidate registry for Android.
 *
 * Status: RATIFIED — BINDING (founder directive, 2026-07-14).
 * Spec: G3_HYBRID_REPORT_PIPELINE.md section 4 (two-tier rule).
 *
 * When Gemma 3, reading the SEALED vault, spots a contradiction the engine
 * did not emit, it is recorded here as a G3-RAISED CANDIDATE — anchored and
 * hashed like any other artefact, labelled pending verification. Promotion
 * happens by engine re-run or human sign-off. Rejected candidates are never
 * deleted; the rejection reason is sealed with them.
 */
class G3CandidateRegistry(private val g3Model: String = "gemma-3-4b-it") {

    private val candidates = linkedMapOf<String, FindingsJsonEmitter.FindingsContradictionRecord>()
    private val audit = mutableListOf<AuditEntry>()
    private var counter = 0

    data class AuditEntry(
        val action: String,
        val candidateId: String,
        val detail: String,
        val utc: String
    )

    private fun nextId(): String {
        counter += 1
        return "G3-CAND-%04d".format(counter)
    }

    /**
     * Record a G3-raised candidate. Anchored input is mandatory:
     * source document, page, and SHA-512 of the source artefact.
     */
    fun raiseCandidate(
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
        utc: String = ""
    ): FindingsJsonEmitter.FindingsContradictionRecord {
        require(sourceDocument.isNotEmpty() && sourcePage >= 0 && sha512Anchor.isNotEmpty()) {
            "GHRP two-tier rule: candidates must be anchored " +
                "(sourceDocument, sourcePage, sha512Anchor). " +
                "If it is not anchored, it is not emitted."
        }
        val id = nextId()
        val record = FindingsJsonEmitter.FindingsContradictionRecord(
            contradictionId = id,
            type = contradictionType,
            severity = severity,
            confidence = confidence,
            propositionAText = propositionAText,
            propositionAActor = propositionAActor,
            propositionBText = propositionBText,
            propositionBActor = propositionBActor,
            conflictDescription = conflictDescription,
            sourceDocument = sourceDocument,
            sourcePage = sourcePage,
            sourceLine = sourceLine,
            sha512Anchor = sha512Anchor,
            extractionMethod = "G3 vault review ($g3Model) over sealed bundle",
            legalHypothesis = null,
            verificationStatus = FindingsJsonEmitter.STATUS_G3_CANDIDATE
        )
        candidates[id] = record
        audit.add(AuditEntry("RAISED", id, contradictionType, utc))
        return record
    }

    /** Promote a candidate to engine-verified after re-run or human sign-off. */
    fun promote(candidateId: String, method: String = "human_signoff", utc: String = ""):
        FindingsJsonEmitter.FindingsContradictionRecord {
        val current = requireNotNull(candidates[candidateId]) { "Unknown candidate $candidateId" }
        val promoted = current.copy(
            verificationStatus = FindingsJsonEmitter.STATUS_CANDIDATE_PROMOTED
        )
        candidates[candidateId] = promoted
        audit.add(AuditEntry("PROMOTED", candidateId, method, utc))
        return promoted
    }

    /** Reject a candidate. Never deleted — the reason is sealed with it. */
    fun reject(candidateId: String, reason: String, utc: String = ""):
        FindingsJsonEmitter.FindingsContradictionRecord {
        require(reason.isNotEmpty()) {
            "Rejection requires a reason. The record of why is itself evidence."
        }
        val current = requireNotNull(candidates[candidateId]) { "Unknown candidate $candidateId" }
        val rejected = current.copy(
            verificationStatus = FindingsJsonEmitter.STATUS_CANDIDATE_REJECTED,
            conflictDescription = current.conflictDescription + " [REJECTED: $reason]"
        )
        candidates[candidateId] = rejected
        audit.add(AuditEntry("REJECTED", candidateId, reason, utc))
        return rejected
    }

    fun pending(): List<FindingsJsonEmitter.FindingsContradictionRecord> =
        candidates.values.filter {
            it.verificationStatus == FindingsJsonEmitter.STATUS_G3_CANDIDATE
        }

    fun allRecords(): List<FindingsJsonEmitter.FindingsContradictionRecord> =
        candidates.values.toList()

    fun auditTrail(): List<AuditEntry> = audit.toList()

    /**
     * Merge registry candidates into a findings document and recount tiers.
     * Rejected candidates stay out of the report body but remain in the registry.
     */
    fun mergeInto(document: FindingsJsonEmitter.FindingsJsonDocument):
        FindingsJsonEmitter.FindingsJsonDocument {
        val toMerge = candidates.values.filter {
            it.verificationStatus != FindingsJsonEmitter.STATUS_CANDIDATE_REJECTED
        }
        val merged = document.contradictions + toMerge
        val candidateCount = merged.count {
            it.verificationStatus == FindingsJsonEmitter.STATUS_G3_CANDIDATE
        }
        return document.copy(
            contradictions = merged,
            engineVerifiedCount = merged.size - candidateCount,
            g3CandidateCount = candidateCount
        )
    }
}
