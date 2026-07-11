package com.verumomnis.forensic.vault

import java.security.SecureRandom
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

    fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
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
