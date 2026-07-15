package com.verumomnis.forensic.trust

import com.verumomnis.forensic.model.Confidence
import kotlinx.serialization.Serializable

/** The dimensions that contribute to a sealed case's trust score. */
enum class TrustFactorType {
    EVIDENCE_QUALITY,
    CONSENSUS_QUORUM,
    IDENTITY_ATTESTATION,
    BLOCKCHAIN_ANCHOR,
    AUDIO_INTEGRITY,
    GPS_CONSISTENCY
}

@Serializable
data class TrustFactor(
    val type: TrustFactorType,
    val confidence: Confidence,
    val weight: Double,
    val note: String
)
