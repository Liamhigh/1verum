package com.verumomnis.forensic.trust

import com.verumomnis.forensic.identity.IdentityProof
import com.verumomnis.forensic.model.Confidence
import com.verumomnis.forensic.model.ForensicFindings
import com.verumomnis.forensic.model.OtsAnchorResult
import com.verumomnis.forensic.model.OtsStatus
import com.verumomnis.forensic.model.Severity

/**
 * Computes a [TrustScore] from forensic findings, identity attestation, and
 * blockchain anchor status. The score is ordinal-only at the edges; the numeric
 * value is for UI gradation.
 */
object TrustEngine {

    fun compute(
        findings: ForensicFindings,
        identityProof: IdentityProof? = null,
        otsResult: OtsAnchorResult? = null
    ): TrustScore {
        val factors = mutableListOf<TrustFactor>()

        // Evidence quality: contradictions present + severity.
        val evidenceConfidence = when {
            findings.contradictions.any { it.severity == Severity.CRITICAL || it.severity == Severity.VERY_HIGH } -> Confidence.VERY_HIGH
            findings.contradictions.any { it.severity == Severity.HIGH } -> Confidence.HIGH
            findings.contradictions.isNotEmpty() -> Confidence.MODERATE
            findings.documentsAnalyzed > 0 -> Confidence.LOW
            else -> Confidence.INSUFFICIENT
        }
        factors += TrustFactor(
            TrustFactorType.EVIDENCE_QUALITY,
            evidenceConfidence,
            0.25,
            "${findings.contradictions.size} contradiction(s), ${findings.documentsAnalyzed} document(s)"
        )

        // Consensus quorum: all contradictions must have triple-AI consensus.
        val consensusOk = findings.contradictions.all { it.tripleAiConsensus.quorum }
        val consensusConfidence = if (findings.contradictions.isEmpty()) {
            Confidence.INSUFFICIENT
        } else if (consensusOk) {
            Confidence.HIGH
        } else {
            Confidence.LOW
        }
        factors += TrustFactor(
            TrustFactorType.CONSENSUS_QUORUM,
            consensusConfidence,
            0.20,
            if (consensusOk) "All contradictions passed Triple-AI consensus" else "Some contradictions lack consensus"
        )

        // Identity attestation.
        val identityConfidence = when {
            identityProof == null -> Confidence.INSUFFICIENT
            identityProof.publicKeyFingerprint.isBlank() -> Confidence.LOW
            else -> Confidence.HIGH
        }
        factors += TrustFactor(
            TrustFactorType.IDENTITY_ATTESTATION,
            identityConfidence,
            0.20,
            identityProof?.let { "Device ${it.deviceId.take(8)}… attested" } ?: "No identity attestation"
        )

        // Blockchain anchor.
        val anchorConfidence = when (otsResult?.status) {
            OtsStatus.CONFIRMED -> Confidence.VERY_HIGH
            OtsStatus.PENDING -> Confidence.HIGH
            OtsStatus.OFFLINE -> Confidence.MODERATE
            OtsStatus.FAILED -> Confidence.LOW
            null -> Confidence.INSUFFICIENT
        }
        factors += TrustFactor(
            TrustFactorType.BLOCKCHAIN_ANCHOR,
            anchorConfidence,
            0.20,
            otsResult?.status?.name ?: "Not anchored"
        )

        // Audio integrity: penalise tamper signals.
        val audioTamper = findings.audio?.tamperSignals?.any { it.severity == Severity.CRITICAL || it.severity == Severity.HIGH } == true
        val audioConfidence = when {
            findings.audio == null -> Confidence.INSUFFICIENT
            audioTamper -> Confidence.LOW
            findings.audio.tamperSignals.isNotEmpty() -> Confidence.MODERATE
            else -> Confidence.HIGH
        }
        factors += TrustFactor(
            TrustFactorType.AUDIO_INTEGRITY,
            audioConfidence,
            0.10,
            findings.audio?.let { "${it.tamperSignals.size} tamper signal(s)" } ?: "No audio analysed"
        )

        // GPS consistency: every media exhibit should carry a GPS anchor.
        val exhibitsWithGps = findings.mediaExhibits.count { it.gps != null }
        val gpsConfidence = when {
            findings.mediaExhibits.isEmpty() -> Confidence.INSUFFICIENT
            exhibitsWithGps == findings.mediaExhibits.size -> Confidence.HIGH
            exhibitsWithGps > 0 -> Confidence.MODERATE
            else -> Confidence.LOW
        }
        factors += TrustFactor(
            TrustFactorType.GPS_CONSISTENCY,
            gpsConfidence,
            0.05,
            "$exhibitsWithGps/${findings.mediaExhibits.size} exhibits GPS-anchored"
        )

        val numeric = factors.sumOf { confidenceValue(it.confidence) * it.weight }
        val confidence = when {
            numeric >= 0.85 -> Confidence.VERY_HIGH
            numeric >= 0.65 -> Confidence.HIGH
            numeric >= 0.45 -> Confidence.MODERATE
            numeric >= 0.25 -> Confidence.LOW
            else -> Confidence.INSUFFICIENT
        }
        return TrustScore(score = numeric.coerceIn(0.0, 1.0), confidence = confidence, factors = factors)
    }

    private fun confidenceValue(c: Confidence): Double = when (c) {
        Confidence.VERY_HIGH -> 1.0
        Confidence.HIGH -> 0.75
        Confidence.MODERATE -> 0.5
        Confidence.LOW -> 0.25
        Confidence.INSUFFICIENT -> 0.0
    }
}
