package com.verumomnis.forensic.identity

import android.content.Context
import com.verumomnis.forensic.crypto.Sha512
import com.verumomnis.forensic.model.SealRecord
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.Base64

/**
 * Identity & Trust System entry point.
 *
 * Responsibilities:
 *  - Create/load a stable device identity backed by the Keystore.
 *  - Store an optional operator identity.
 *  - Sign seal records to produce [IdentityProof].
 *  - Verify identity proofs.
 */
class IdentityService(
    context: Context,
    private val keyStore: IdentityKeyStore = AndroidIdentityKeyStore(context),
    private val nowProvider: () -> Instant = { Instant.now() }
) {

    companion object {
        private const val PREFS = "verum_identity_prefs"
        private const val PREF_DEVICE = "device_identity"
        private const val PREF_USER = "user_identity"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /** Initialize or load the device identity. Safe to call multiple times. */
    fun initializeDevice(): DeviceIdentity {
        val existing = prefs.getString(PREF_DEVICE, null)
        if (existing != null) {
            return runCatching { json.decodeFromString<DeviceIdentity>(existing) }.getOrNull()
                ?: createAndStoreDeviceIdentity()
        }
        return createAndStoreDeviceIdentity()
    }

    private fun createAndStoreDeviceIdentity(): DeviceIdentity {
        val fingerprint = keyStore.initialize()
        val deviceId = deriveDeviceId(fingerprint)
        val identity = DeviceIdentity(
            deviceId = deviceId,
            publicKeyFingerprint = fingerprint,
            createdAt = nowProvider().toString()
        )
        prefs.edit().putString(PREF_DEVICE, json.encodeToString(identity)).apply()
        return identity
    }

    /** Return the current device identity, or null if not initialized. */
    fun deviceIdentity(): DeviceIdentity? =
        prefs.getString(PREF_DEVICE, null)?.let {
            runCatching { json.decodeFromString<DeviceIdentity>(it) }.getOrNull()
        }

    /** Set or update the operator identity. */
    fun setUser(identity: UserIdentity) {
        prefs.edit().putString(PREF_USER, json.encodeToString(identity)).apply()
    }

    /** Return the current operator identity. */
    fun userIdentity(): UserIdentity =
        prefs.getString(PREF_USER, null)?.let {
            runCatching { json.decodeFromString<UserIdentity>(it) }.getOrNull()
        } ?: UserIdentity()

    /**
     * Produce a signed [IdentityProof] binding the device, user, and seal.
     * Must be called after the device identity has been initialized.
     */
    fun signSeal(seal: SealRecord): IdentityProof? {
        val device = deviceIdentity() ?: initializeDevice()
        val user = userIdentity()
        val timestamp = nowProvider().toString()
        val payload = buildPayload(device.deviceId, user.publicKeyFingerprint, seal.sha512, timestamp)
        val signature = keyStore.sign(payload)
        val proof = IdentityProof(
            deviceId = device.deviceId,
            userFingerprint = user.publicKeyFingerprint.ifEmpty { user.displayName },
            sealSha512 = seal.sha512,
            timestamp = timestamp,
            signatureBase64 = Base64.getEncoder().encodeToString(signature),
            publicKeyFingerprint = device.publicKeyFingerprint
        )
        return proof
    }

    /** Verify that [proof] was signed by the device it claims. */
    fun verifyProof(proof: IdentityProof): Boolean {
        val payload = buildPayload(proof.deviceId, proof.userFingerprint, proof.sealSha512, proof.timestamp)
        val signature = runCatching { Base64.getDecoder().decode(proof.signatureBase64) }.getOrNull() ?: return false
        return keyStore.verify(payload, signature)
    }

    private fun buildPayload(deviceId: String, userFingerprint: String, sealSha512: String, timestamp: String): ByteArray {
        val canonical = "$deviceId|$userFingerprint|$sealSha512|$timestamp"
        return canonical.toByteArray(Charsets.UTF_8)
    }

    private fun deriveDeviceId(publicKeyFingerprint: String): String {
        // The device ID is derived from the Keystore public-key fingerprint so it
        // is stable and does not require any Android system identifiers.
        return Sha512.hash(publicKeyFingerprint.toByteArray(Charsets.UTF_8)).take(32)
    }
}
