package com.verumomnis.forensic

import com.verumomnis.forensic.engine.contradiction.FindingsJsonEmitter
import com.verumomnis.forensic.engine.contradiction.G3CandidateRegistry
import com.verumomnis.forensic.engine.contradiction.VerumContradictionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GHRP integration tests — the engine emits the findings JSON contract and
 * the candidate registry enforces the two-tier rule end to end.
 *
 * Status: RATIFIED — BINDING (founder directive, 2026-07-14).
 */
class GhrpIntegrationTest {

    private val demoTexts = listOf(
        "On 10 March 2025 Marius stated the deal fell through and Greensky never invoiced the client.",
        "On 6 April 2025 Marius admitted that Kevin completed the deal on 13 March 2025.",
        "Standard Bank promised on 19 June 2025 that the client would be contacted by next week.",
        "Standard Bank never contacted the client. No one called. No letter arrived.",
        "The cease and desist letter was served on 23 April 2025 according to the attorney.",
        "The cease and desist letter is dated 30 April 2025 on its face."
    )

    @Test
    fun engineScanEmitsFindingsJsonContract() {
        val engine = VerumContradictionEngine(caseId = "VO-GHRP-TEST-001", injectedTimestamp = 1L)
        val report = engine.processFromTexts(demoTexts)
        assertTrue("engine must detect contradictions in the demo corpus", report.contradictions.isNotEmpty())

        val findings = FindingsJsonEmitter.fromReport(
            report,
            sourceBundle = "ghrp_demo.txt",
            caseIds = listOf("VO-GHRP-TEST-001"),
            generatedUtc = "2026-07-14T00:00:00Z"
        )

        assertEquals("1.0.0", findings.findingsJsonVersion)
        assertEquals(report.corpusHash, findings.corpusSha512)
        assertEquals(
            findings.contradictions.size,
            findings.engineVerifiedCount + findings.g3CandidateCount
        )
        for (record in findings.contradictions) {
            assertTrue("record must be anchored", record.sha512Anchor.isNotEmpty())
            assertEquals(FindingsJsonEmitter.STATUS_ENGINE_VERIFIED, record.verificationStatus)
        }
    }

    @Test
    fun findingsJsonSerializes() {
        val engine = VerumContradictionEngine(caseId = "VO-GHRP-TEST-002", injectedTimestamp = 1L)
        val report = engine.processFromTexts(demoTexts.take(2))
        val findings = FindingsJsonEmitter.fromReport(report, generatedUtc = "2026-07-14T00:00:00Z")
        val text = FindingsJsonEmitter.toJson(findings)
        assertTrue(text.contains("\"findings_json_version\""))
        assertTrue(text.contains("\"corpus_sha512\""))
        assertTrue(text.contains("\"contradiction_id\""))
    }

    @Test
    fun candidateRaisedIsLabelledPendingVerification() {
        val registry = G3CandidateRegistry()
        val record = registry.raiseCandidate(
            contradictionType = "OMISSION",
            propositionAText = "Reply promised by next week",
            propositionBText = "No reply exists anywhere in the sealed vault",
            propositionAActor = "Standard Bank",
            propositionBActor = "Sealed record",
            conflictDescription = "Undertaking never honoured in the record.",
            sourceDocument = "ghrp_demo.txt",
            sourcePage = 3,
            sha512Anchor = "ab".repeat(64)
        )
        assertEquals(FindingsJsonEmitter.STATUS_G3_CANDIDATE, record.verificationStatus)
        assertEquals(1, registry.pending().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unanchoredCandidateIsRefused() {
        G3CandidateRegistry().raiseCandidate(
            contradictionType = "OMISSION",
            propositionAText = "x",
            propositionBText = "y",
            propositionAActor = "a",
            propositionBActor = "b",
            conflictDescription = "unanchored",
            sourceDocument = "",
            sourcePage = 0,
            sha512Anchor = ""
        )
    }

    @Test
    fun promoteAndRejectLifecycleIsAudited() {
        val registry = G3CandidateRegistry()
        val promoted = registry.raiseCandidate(
            contradictionType = "OMISSION",
            propositionAText = "A", propositionBText = "B",
            propositionAActor = "X", propositionBActor = "Y",
            conflictDescription = "first",
            sourceDocument = "d.txt", sourcePage = 1, sha512Anchor = "ab".repeat(64)
        )
        registry.promote(promoted.contradictionId, method = "engine_rerun")

        val rejected = registry.raiseCandidate(
            contradictionType = "OMISSION",
            propositionAText = "C", propositionBText = "D",
            propositionAActor = "X", propositionBActor = "Y",
            conflictDescription = "second",
            sourceDocument = "d.txt", sourcePage = 2, sha512Anchor = "cd".repeat(64)
        )
        registry.reject(rejected.contradictionId, reason = "Duplicate of engine finding")

        assertEquals(
            FindingsJsonEmitter.STATUS_CANDIDATE_PROMOTED,
            registry.allRecords().first { it.contradictionId == promoted.contradictionId }.verificationStatus
        )
        assertEquals(
            FindingsJsonEmitter.STATUS_CANDIDATE_REJECTED,
            registry.allRecords().first { it.contradictionId == rejected.contradictionId }.verificationStatus
        )
        assertEquals(2, registry.allRecords().size)
        assertEquals(
            listOf("PROMOTED", "RAISED", "RAISED", "REJECTED"),
            registry.auditTrail().map { it.action }.sorted()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectionWithoutReasonIsRefused() {
        val registry = G3CandidateRegistry()
        val record = registry.raiseCandidate(
            contradictionType = "OMISSION",
            propositionAText = "A", propositionBText = "B",
            propositionAActor = "X", propositionBActor = "Y",
            conflictDescription = "first",
            sourceDocument = "d.txt", sourcePage = 1, sha512Anchor = "ab".repeat(64)
        )
        registry.reject(record.contradictionId, reason = "")
    }

    @Test
    fun mergeRecountsTiersAndExcludesRejected() {
        val engine = VerumContradictionEngine(caseId = "VO-GHRP-TEST-003", injectedTimestamp = 1L)
        val report = engine.processFromTexts(demoTexts.take(2))
        val findings = FindingsJsonEmitter.fromReport(report, generatedUtc = "2026-07-14T00:00:00Z")

        val registry = G3CandidateRegistry()
        val promoted = registry.raiseCandidate(
            contradictionType = "OMISSION",
            propositionAText = "A", propositionBText = "B",
            propositionAActor = "X", propositionBActor = "Y",
            conflictDescription = "promoted candidate",
            sourceDocument = "d.txt", sourcePage = 1, sha512Anchor = "ab".repeat(64)
        )
        registry.promote(promoted.contradictionId)
        val rejected = registry.raiseCandidate(
            contradictionType = "OMISSION",
            propositionAText = "C", propositionBText = "D",
            propositionAActor = "X", propositionBActor = "Y",
            conflictDescription = "rejected candidate",
            sourceDocument = "d.txt", sourcePage = 2, sha512Anchor = "cd".repeat(64)
        )
        registry.reject(rejected.contradictionId, reason = "Not supported by the sealed record")

        val merged = registry.mergeInto(findings)
        val ids = merged.contradictions.map { it.contradictionId }
        assertTrue(ids.contains(promoted.contradictionId))
        assertFalse(ids.contains(rejected.contradictionId))
        assertEquals(merged.contradictions.size, merged.engineVerifiedCount + merged.g3CandidateCount)
        assertNotEquals(0, merged.engineVerifiedCount)
    }
}
