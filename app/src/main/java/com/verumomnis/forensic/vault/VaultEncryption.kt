package com.verumomnis.forensic.vault

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption for vault-at-rest (build spec Section 22). In production
 * the key is hardware-backed (Android Keystore); this class provides the AEAD
 * primitive and is JVM-unit-testable. Output layout: [12-byte IV][ciphertext+tag].
 */
object VaultEncryption {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_BITS = 256
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun generateKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(KEY_BITS) }.generateKey()

    fun keyFromBytes(bytes: ByteArray): SecretKey {
        require(bytes.size == KEY_BITS / 8) { "AES-256 key must be 32 bytes" }
        return SecretKeySpec(bytes, "AES")
    }

    /**
     * Encrypts with a provider-generated IV.
     *
     * The IV is deliberately NOT supplied by this code. Android Keystore rejects
     * a caller-provided IV outright — `InvalidAlgorithmParameterException:
     * Caller-provided IV not permitted` — because reusing an IV under the same
     * GCM key is catastrophic, so the platform insists on generating it. Passing
     * our own worked against a software key in unit tests and failed on every
     * real device, which is why encrypted chat sessions never persisted on
     * hardware despite the tests passing.
     *
     * Letting the provider choose is also the stronger position: with a
     * hardware-backed key the uniqueness guarantee comes from the Keystore
     * rather than from this class remembering to randomise correctly.
     *
     * Layout is unchanged — [12-byte IV][ciphertext+tag] — so anything already
     * written stays readable.
     */
    fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
        val iv = cipher.iv
        check(iv != null && iv.size == IV_BYTES) {
            "expected a $IV_BYTES-byte GCM IV from the provider, got ${iv?.size}"
        }
        return iv + cipher.doFinal(plaintext)
    }

    fun decrypt(payload: ByteArray, key: SecretKey): ByteArray {
        require(payload.size > IV_BYTES) { "Payload too short" }
        val iv = payload.copyOfRange(0, IV_BYTES)
        val ciphertext = payload.copyOfRange(IV_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        return cipher.doFinal(ciphertext)
    }
}
