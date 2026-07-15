package com.verumomnis.forensic.identity

import kotlinx.serialization.Serializable

/**
 * A signed attestation binding the device, the operator, and a specific seal.
 *
 * The signature covers: deviceId | user fingerprint | seal SHA-512 | timestamp.
 * This lets a third party verify that a known device+operator produced the seal
 * at the claimed time, without the device private key ever leaving the phone.
 */
@Serializable
data class IdentityProof(
    val deviceId: String,
    val userFingerprint: String,
    val sealSha512: String,
    val timestamp: String,
    val signatureBase64: String,
    val publicKeyFingerprint: String
) {
    /** Short fingerprint for report footers and UI badges. */
    fun shortFingerprint(): String = publicKeyFingerprint.take(12)
}
