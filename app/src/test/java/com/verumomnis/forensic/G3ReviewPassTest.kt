package com.verumomnis.forensic

import com.verumomnis.forensic.engine.EvidenceDocument
import com.verumomnis.forensic.engine.FindingsJsonEmitter
import com.verumomnis.forensic.engine.G3ReviewPass
import com.verumomnis.forensic.llm.Gemma3Runtime
import com.verumomnis.forensic.llm.UnavailableGemma3Runtime
import com.verumomnis.forensic.model.ForensicFindings
import com.verumomnis.forensic.model.JurisdictionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class G3ReviewPassTest {

    private val now = Instant.parse("2026-07-14T09:00:00Z")

    private fun doc(fileName: String, text: String) = EvidenceDocument(
        evidenceId = "DOC001",
        fileName = fileName,
        type = "document",
        text = text,
        sha512 = "f".repeat(128)
    )

    private fun findings() = ForensicFindings(
        documentsAnalyzed = 1,
        evidenceAtoms = emptyList(),
        contradictions = emptyList(),
        timeline = emptyList(),
        legalMappings = emptyList(),
        jurisdiction = "ZA-KZN",
        jurisdictionSource = JurisdictionSource(jurisdiction = "ZA-KZN"),
        brainVerdicts = emptyMap()
    )

    private class FakeRuntime(private val response: String?) : Gemma3Runtime {
        override val modelName = "gemma-3-4b-it (test)"
        override fun isAvailable() = true
        override fun generate(prompt: String, maxTokens: Int): String? = response
    }

    @Test
    fun review_isNoOp_whenRuntimeUnavailable() {
        val outcome = G3ReviewPass.review(
            documents = listOf(doc("a.pdf", "some text")),
            findings = findings(),
            now = now,
            runtime = UnavailableGemma3Runtime
        )
        assertTrue(outcome.candidateRecords.isEmpty())
        assertTrue(outcome.registry.allRecords().isEmpty())
    }

    @Test
    fun review_raisesAnchoredCandidate_fromModelResponse() {
        val response = """
            Here are the missed contradictions:
            [{"type":"OMISSION","proposition_a_text":"Payment was made in full",
              "proposition_a_actor":"Respondent","proposition_b_text":"No payment record exists",
              "proposition_b_actor":"Bank statement","conflict_description":"Claimed payment absent from records.",
              "source_document":"a.pdf","source_page":2,"severity":"HIGH","confidence":"MODERATE"}]
        """.trimIndent()
        val outcome = G3ReviewPass.review(
            documents = listOf(doc("a.pdf", "Payment was made in full. No payment record exists.")),
            findings = findings(),
            now = now,
            runtime = FakeRuntime(response)
        )
        assertEquals(1, outcome.candidateRecords.size)
        val record = outcome.candidateRecords.first()
        assertEquals(FindingsJsonEmitter.STATUS_G3_CANDIDATE, record.verification_status)
        assertEquals("a.pdf", record.source_document)
        // Anchor must come from the ingest record, never from the model.
        assertEquals("f".repeat(128), record.sha512_anchor)
        assertEquals(1, outcome.registry.pending().size)
    }

    @Test
    fun review_discardsCandidate_namingUnknownDocument() {
        val response = """[{"type":"OMISSION","proposition_a_text":"A","proposition_a_actor":"X",
            "proposition_b_text":"B","proposition_b_actor":"Y","conflict_description":"c",
            "source_document":"never_ingested.pdf","source_page":1,"severity":"HIGH","confidence":"HIGH"}]"""
        val outcome = G3ReviewPass.review(
            documents = listOf(doc("a.pdf", "text")),
            findings = findings(),
            now = now,
            runtime = FakeRuntime(response)
        )
        assertTrue(outcome.candidateRecords.isEmpty())
    }

    @Test
    fun review_survivesMalformedResponse() {
        val outcome = G3ReviewPass.review(
            documents = listOf(doc("a.pdf", "text")),
            findings = findings(),
            now = now,
            runtime = FakeRuntime("I could not find any JSON to give you, sorry!")
        )
        assertTrue(outcome.candidateRecords.isEmpty())
    }

    @Test
    fun emit_mergesCandidates_andCountsTiers() {
        val response = """[{"type":"OMISSION","proposition_a_text":"Payment was made in full",
            "proposition_a_actor":"Respondent","proposition_b_text":"No payment record exists",
            "proposition_b_actor":"Bank","conflict_description":"c","source_document":"a.pdf",
            "source_page":2,"severity":"HIGH","confidence":"MODERATE"}]"""
        val outcome = G3ReviewPass.review(
            documents = listOf(doc("a.pdf", "text")),
            findings = findings(),
            now = now,
            runtime = FakeRuntime(response)
        )
        val json = FindingsJsonEmitter.emit(
            findings(), "TestCase", now,
            corpusSha512 = "0".repeat(128),
            extraRecords = outcome.candidateRecords
        )
        assertTrue(json.contains("\"g3_candidate_count\":1"))
        assertTrue(json.contains("\"corpus_sha512\":\"${"0".repeat(128)}\""))
        assertTrue(json.contains(FindingsJsonEmitter.STATUS_G3_CANDIDATE))
    }
}
