package com.verumomnis.forensic

import com.verumomnis.forensic.engine.DeterministicReportWriter
import com.verumomnis.forensic.engine.FindingsJsonEmitter
import com.verumomnis.forensic.engine.Gemma3ReportWriter
import com.verumomnis.forensic.model.Contradiction
import com.verumomnis.forensic.model.ContradictionCategory
import com.verumomnis.forensic.model.ContradictionClaim
import com.verumomnis.forensic.model.ContradictionType
import com.verumomnis.forensic.model.Confidence
import com.verumomnis.forensic.model.ForensicFindings
import com.verumomnis.forensic.model.JurisdictionSource
import com.verumomnis.forensic.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class FindingsJsonEmitterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val now = Instant.parse("2026-07-14T09:00:00Z")

    private fun sampleClaim(text: String, source: String, sha512: String, page: Int = 1, line: Int = 1) =
        ContradictionClaim(
            text = text,
            source = source,
            evidenceId = "DOC001",
            page = page,
            line = line,
            sha512 = sha512
        )

    private fun sampleContradiction(id: String = "VO-TEST-001") = Contradiction(
        contradictionId = id,
        brainSource = "B1",
        category = ContradictionCategory.CONTRACT_VALIDITY,
        type = ContradictionType.DIRECT_NEGATION,
        respondent = "Respondent A",
        claimA = sampleClaim(
            text = "The contract was signed on 1 March 2025.",
            source = "contract.pdf",
            sha512 = "a".repeat(128),
            page = 3,
            line = 12
        ),
        claimB = sampleClaim(
            text = "No contract exists between the parties.",
            source = "denial_letter.pdf",
            sha512 = "b".repeat(128),
            page = 1,
            line = 5
        ),
        severity = Severity.HIGH,
        description = "The respondent both affirms and denies the existence of a signed contract.",
        legalSignificance = "Fraud / perjury indicator",
        applicableLaw = listOf("ZA-NCA s34"),
        confidence = Confidence.HIGH,
        timestamp = now.toString()
    )

    private fun sampleFindings(contradictions: List<Contradiction>) = ForensicFindings(
        documentsAnalyzed = 2,
        evidenceAtoms = emptyList(),
        contradictions = contradictions,
        timeline = emptyList(),
        legalMappings = listOf("ZA-NCA s34"),
        jurisdiction = "ZA-KZN",
        jurisdictionSource = JurisdictionSource(jurisdiction = "ZA-KZN"),
        brainVerdicts = mapOf("B1" to "CONTRADICTIONS_DETECTED")
    )

    @Test
    fun emit_producesEngineVerifiedStatus() {
        val findings = sampleFindings(listOf(sampleContradiction()))
        val json = FindingsJsonEmitter.emit(findings, "TestCase", now)

        assertTrue("JSON should contain engine version", json.contains("\"engine_version\":\"5.3.1c\""))
        assertTrue("JSON should contain findings_json_version", json.contains("\"findings_json_version\":\"1.0.0\""))
        assertTrue("JSON should contain source_bundle", json.contains("\"source_bundle\":\"TestCase\""))
        assertTrue("JSON should contain generated_utc", json.contains("\"generated_utc\":\"2026-07-14T09:00:00Z\""))
        assertTrue(
            "Engine-verified contradiction should be labelled correctly",
            json.contains("\"verification_status\":\"ENGINE-VERIFIED\"")
        )
        assertTrue("JSON should contain contradiction_id", json.contains("\"contradiction_id\":\"VO-TEST-001\""))
        assertTrue("JSON should contain proposition_a_text", json.contains("The contract was signed on 1 March 2025."))
        assertTrue("JSON should contain sha512_anchor", json.contains("\"sha512_anchor\":\"${"a".repeat(128)}\""))
    }

    @Test
    fun raiseG3Candidate_producesCandidateStatus() {
        val candidate = FindingsJsonEmitter.raiseG3Candidate(
            candidateId = "VO-TEST-G3-001",
            contradictionType = "OMISSION",
            propositionAText = "Reply promised by next week",
            propositionBText = "No reply exists in sealed record",
            propositionAActor = "Institution",
            propositionBActor = "Sealed record",
            conflictDescription = "Undertaking never honoured anywhere in the vault.",
            sourceDocument = "demo.pdf",
            sourcePage = 7,
            sha512Anchor = "c".repeat(128)
        )

        assertEquals("VO-TEST-G3-001", candidate.contradiction_id)
        assertEquals("OMISSION", candidate.type)
        assertEquals(
            FindingsJsonEmitter.STATUS_G3_CANDIDATE,
            candidate.verification_status
        )
        assertEquals("c".repeat(128), candidate.sha512_anchor)
    }

    @Test
    fun emit_includesG3Candidate_whenMixedWithEngineRecords() {
        val engineRecord = sampleContradiction("VO-TEST-002")
        val candidate = FindingsJsonEmitter.raiseG3Candidate(
            candidateId = "VO-TEST-G3-002",
            contradictionType = "TEMPORAL_SHIFT",
            propositionAText = "Event occurred on Monday",
            propositionBText = "Event occurred on Tuesday",
            propositionAActor = "Witness A",
            propositionBActor = "Witness B",
            conflictDescription = "Conflicting days for the same event.",
            sourceDocument = "statement.pdf",
            sourcePage = 4,
            sha512Anchor = "d".repeat(128)
        )

        val findings = sampleFindings(listOf(engineRecord))
        val document = kotlinx.serialization.json.Json.decodeFromString(
            FindingsJsonEmitter.FindingsJsonDocument.serializer(),
            FindingsJsonEmitter.emit(findings, "TestCase", now)
        )
        val records = document.contradictions + candidate

        assertEquals(2, records.size)
        assertTrue(records.any { it.verification_status == FindingsJsonEmitter.STATUS_ENGINE_VERIFIED })
        assertTrue(records.any { it.verification_status == FindingsJsonEmitter.STATUS_G3_CANDIDATE })
    }

    @Test
    fun deterministicReportWriter_labelsG3CandidatesFromFindingsJson() {
        val findings = sampleFindings(listOf(sampleContradiction()))
        val candidate = FindingsJsonEmitter.raiseG3Candidate(
            candidateId = "VO-TEST-G3-003",
            contradictionType = "OMISSION",
            propositionAText = "Promise made",
            propositionBText = "Promise broken",
            propositionAActor = "Party A",
            propositionBActor = "Party B",
            conflictDescription = "Promised action never recorded.",
            sourceDocument = "promise.pdf",
            sourcePage = 9,
            sha512Anchor = "e".repeat(128)
        )

        val document = FindingsJsonEmitter.FindingsJsonDocument(
            engine_version = FindingsJsonEmitter.ENGINE_VERSION,
            findings_json_version = FindingsJsonEmitter.FINDINGS_JSON_VERSION,
            generated_utc = now.toString(),
            source_bundle = "TestCase",
            contradictions = listOf(
                FindingsJsonEmitter.contradictionToRecord(findings.contradictions.first()),
                candidate
            )
        )

        val findingsJsonFile = tempFolder.newFile("findings_TestCase_2026-07-14T09-00-00.json")
        findingsJsonFile.writeText(
            kotlinx.serialization.json.Json.encodeToString(
                FindingsJsonEmitter.FindingsJsonDocument.serializer(),
                document
            )
        )

        val narrative = DeterministicReportWriter.writeNarrative(findings, "TestCase", findingsJsonFile.absolutePath)

        assertTrue("Narrative should label the G3 candidate section", narrative.contains("G3-RAISED CANDIDATE TIER"))
        assertTrue("Narrative should contain candidate ID", narrative.contains("VO-TEST-G3-003"))
        assertTrue("Narrative should contain candidate status", narrative.contains(FindingsJsonEmitter.STATUS_G3_CANDIDATE))
        assertTrue("Narrative should warn that candidates are pending verification", narrative.contains("pending engine or human verification"))
    }

    @Test
    fun gemma3Prompt_includesCandidateTierRule() {
        val findings = sampleFindings(emptyList())
        val prompt = Gemma3ReportWriter.buildPrompt(findings, "TestCase")

        assertTrue("Prompt should include G3 candidate tier rule", prompt.contains("G3 CANDIDATE TIER RULE"))
        assertTrue(
            "Prompt should instruct Gemma 3 to label non-engine contradictions",
            prompt.contains("Any contradiction you spot that the engine did not emit must be labelled G3-RAISED CANDIDATE and anchored/hashed")
        )
    }
}
