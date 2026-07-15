package com.verumomnis.forensic.identity

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Abstraction over device-bound asymmetric signing keys. Production uses the
 * Android Keystore; tests can substitute an in-memory implementation.
 */
interface IdentityKeyStore {
    /** Generate or load a persistent key pair and return the public-key fingerprint. */
    fun initialize(): String

    /** Sign [data] with the device private key. */
    fun sign(data: ByteArray): ByteArray

    /** Verify [signature] over [data] with the device public key. */
    fun verify(data: ByteArray, signature: ByteArray): Boolean

    /** Return the current public key fingerprint, or empty if not initialized. */
    fun publicKeyFingerprint(): String
}

/**
 * Android Keystore-backed implementation (ECDSA P-256). The private key never
 * leaves the secure hardware/TEE when the device supports it.
 */
class AndroidIdentityKeyStore(context: Context) : IdentityKeyStore {

    companion object {
        private const val ALIAS = "verum_omnis_identity_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PREFS = "verum_identity_prefs"
        private const val PREF_FINGERPRINT = "public_key_fingerprint"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun initialize(): String {
        if (!keyStore.containsAlias(ALIAS)) {
            generateKeyPair()
        }
        val fingerprint = computeFingerprint()
        prefs.edit().putString(PREF_FINGERPRINT, fingerprint).apply()
        return fingerprint
    }

    override fun sign(data: ByteArray): ByteArray {
        val entry = keyStore.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
        val signer = Signature.getInstance("SHA256withECDSA").apply {
            initSign(entry.privateKey)
            update(data)
        }
        return signer.sign()
    }

    override fun verify(data: ByteArray, signature: ByteArray): Boolean {
        if (!keyStore.containsAlias(ALIAS)) return false
        val entry = keyStore.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
        return runCatching {
            Signature.getInstance("SHA256withECDSA").apply {
                initVerify(entry.certificate)
                update(data)
            }.verify(signature)
        }.getOrDefault(false)
    }

    override fun publicKeyFingerprint(): String =
        prefs.getString(PREF_FINGERPRINT, null) ?: if (keyStore.containsAlias(ALIAS)) computeFingerprint() else ""

    private fun computeFingerprint(): String {
        if (!keyStore.containsAlias(ALIAS)) return ""
        val entry = keyStore.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
        val encoded = entry.certificate.publicKey.encoded
        val digest = MessageDigest.getInstance("SHA-256").digest(encoded)
        return Base64.getEncoder().encodeToString(digest)
    }

    private fun generateKeyPair() {
        val builder = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).apply {
            setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            setDigests(KeyProperties.DIGEST_SHA256)
            setRandomizedEncryptionRequired(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setInvalidatedByBiometricEnrollment(false)
            }
        }
        KeyPairGenerator.getInstance("EC", ANDROID_KEYSTORE).apply {
            initialize(builder.build())
        }.generateKeyPair()
    }
}

/**
 * In-memory key store for JVM unit tests. Deterministic when seeded with the
 * same key pair.
 */
class InMemoryIdentityKeyStore(
    private val fingerprint: String = "test-fingerprint",
    private val validSignature: ByteArray = "valid-signature".toByteArray(),
    private val invalidSignature: ByteArray = "invalid-signature".toByteArray()
) : IdentityKeyStore {

    private var initialized = false

    override fun initialize(): String {
        initialized = true
        return fingerprint
    }

    override fun sign(data: ByteArray): ByteArray = validSignature

    override fun verify(data: ByteArray, signature: ByteArray): Boolean =
        initialized && signature.contentEquals(validSignature)

    override fun publicKeyFingerprint(): String = if (initialized) fingerprint else ""
}
