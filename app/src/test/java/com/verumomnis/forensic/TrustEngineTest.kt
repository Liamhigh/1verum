package com.verumomnis.forensic

import com.verumomnis.forensic.identity.IdentityProof
import com.verumomnis.forensic.model.AudioAnalysis
import com.verumomnis.forensic.model.AudioTamperSignal
import com.verumomnis.forensic.model.BehavioralAnalysis
import com.verumomnis.forensic.model.Confidence
import com.verumomnis.forensic.model.Contradiction
import com.verumomnis.forensic.model.ContradictionCategory
import com.verumomnis.forensic.model.ContradictionClaim
import com.verumomnis.forensic.model.ContradictionType
import com.verumomnis.forensic.model.ForensicFindings
import com.verumomnis.forensic.model.OtsAnchorResult
import com.verumomnis.forensic.model.OtsStatus
import com.verumomnis.forensic.model.Severity
import com.verumomnis.forensic.model.SpeakerSegment
import com.verumomnis.forensic.model.TimelineEvent
import com.verumomnis.forensic.trust.TrustEngine
import com.verumomnis.forensic.trust.TrustFactorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustEngineTest {

    private fun contradiction(severity: Severity): Contradiction = Contradiction(
        contradictionId = "C-001",
        brainSource = "B1",
        category = ContradictionCategory.GOODWILL_VALUE,
        type = ContradictionType.DIRECT_NEGATION,
        respondent = "AllFuels",
        claimA = ContradictionClaim("claim A", "doc.txt", "E1", sha512 = "a".repeat(128)),
        claimB = ContradictionClaim("claim B", "doc.txt", "E1", sha512 = "b".repeat(128)),
        severity = severity,
        legalSignificance = "significance",
        applicableLaw = listOf("SA Common Law - Fraud"),
        confidence = Confidence.HIGH,
        timestamp = "2026-07-12T10:00:00Z"
    )

    private fun findings(contradictions: List<Contradiction>, audio: AudioAnalysis? = null): ForensicFindings =
        ForensicFindings(
            documentsAnalyzed = 2,
            evidenceAtoms = emptyList(),
            contradictions = contradictions,
            timeline = listOf(TimelineEvent("E1", "2026-01-14", "event", evidenceId = "E1", sha512 = "c".repeat(128))),
            legalMappings = listOf("SA Common Law"),
            jurisdiction = "ZA-KZN",
            financial = null,
            behavioral = BehavioralAnalysis(),
            audio = audio,
            documentForensics = emptyList(),
            communications = emptyList(),
            rndValidation = emptyList(),
            mediaExhibits = emptyList(),
            brainVerdicts = mapOf("B1" to "FOUND")
        )

    @Test
    fun highSeverityContradictionYieldsHighEvidenceConfidence() {
        val f = findings(listOf(contradiction(Severity.HIGH)))
        val score = TrustEngine.compute(f)
        // The overall score is dragged down by absent identity/anchor/audio factors;
        // the evidence-quality factor itself is HIGH.
        assertTrue(score.factors.any { it.type == TrustFactorType.EVIDENCE_QUALITY && it.confidence == Confidence.HIGH })
    }

    @Test
    fun identityProofBoostsIdentityFactor() {
        val f = findings(emptyList())
        val proof = IdentityProof(
            deviceId = "device-123",
            userFingerprint = "user-fp",
            sealSha512 = "d".repeat(128),
            timestamp = "2026-07-12T10:00:00Z",
            signatureBase64 = "c2ln",
            publicKeyFingerprint = "pkfp"
        )
        val score = TrustEngine.compute(f, proof)
        val identityFactor = score.factors.first { it.type == TrustFactorType.IDENTITY_ATTESTATION }
        assertEquals(Confidence.HIGH, identityFactor.confidence)
    }

    @Test
    fun confirmedOtsAnchorYieldsVeryHighAnchorFactor() {
        val f = findings(emptyList())
        val ots = OtsAnchorResult(
            status = OtsStatus.CONFIRMED,
            sha512 = "e".repeat(128),
            sha256Digest = "digest",
            calendarUrls = listOf("https://alice.btc.calendar.opentimestamps.org"),
            submittedAt = "2026-07-12T10:00:00Z"
        )
        val score = TrustEngine.compute(f, otsResult = ots)
        val anchorFactor = score.factors.first { it.type == TrustFactorType.BLOCKCHAIN_ANCHOR }
        assertEquals(Confidence.VERY_HIGH, anchorFactor.confidence)
    }

    @Test
    fun audioTamperLowersAudioIntegrity() {
        val audio = AudioAnalysis(
            filesAnalyzed = 1,
            speakerCount = 2,
            segments = emptyList(),
            tamperSignals = listOf(AudioTamperSignal("AUD001", "SAMPLE_RATE_INCONSISTENCY", Severity.CRITICAL, "splicing")),
            voiceStress = emptyList(),
            fullTranscript = "",
            transcriptionAvailable = true
        )
        val f = findings(emptyList(), audio)
        val score = TrustEngine.compute(f)
        val audioFactor = score.factors.first { it.type == TrustFactorType.AUDIO_INTEGRITY }
        assertEquals(Confidence.LOW, audioFactor.confidence)
    }
}
