package com.verumomnis.forensic.identity

import kotlinx.serialization.Serializable

/**
 * Device-bound identity backed by Android Keystore. The [deviceId] is derived
 * from the public key fingerprint so it is stable across app reinstalls on the
 * same device (assuming the Keystore key alias is not deleted).
 */
@Serializable
data class DeviceIdentity(
    val deviceId: String,
    val publicKeyFingerprint: String,
    val attestation: String = "",
    val createdAt: String = ""
)
