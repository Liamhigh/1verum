package com.verumomnis.forensic.vault

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.DigestOutputStream

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
    private val EVIDENCE_HEADER = byteArrayOf(0x56, 0x4f, 0x45, 0x56, 0x01)
    private const val BUFFER_BYTES = 64 * 1024
    const val EVIDENCE_OVERHEAD_BYTES = 5 + IV_BYTES + 16

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

    /**
     * Versioned on-disk evidence format: [VOEV magic + version][12-byte IV]
     * [AES-GCM ciphertext + 128-bit tag]. The header is authenticated as AAD.
     */
    fun encryptEvidence(plaintext: ByteArray, key: SecretKey): ByteArray {
        val output = ByteArrayOutputStream()
        encryptEvidence(ByteArrayInputStream(plaintext), output, key)
        return output.toByteArray()
    }

    /** Streams plaintext to the encrypted evidence format and returns its SHA-512. */
    fun encryptEvidence(input: InputStream, output: OutputStream, key: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val iv = cipher.iv
        check(iv != null && iv.size == IV_BYTES) { "Invalid provider-generated GCM IV" }
        cipher.updateAAD(EVIDENCE_HEADER)
        output.write(EVIDENCE_HEADER)
        output.write(iv)

        val digest = MessageDigest.getInstance("SHA-512")
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
            cipher.update(buffer, 0, read)?.let { output.write(it) }
        }
        output.write(cipher.doFinal())
        output.flush()
        return digest.digest().toHex()
    }

    fun isEncryptedEvidence(file: File): Boolean =
        file.inputStream().use { input ->
            val header = ByteArray(EVIDENCE_HEADER.size)
            var offset = 0
            while (offset < header.size) {
                val read = input.read(header, offset, header.size - offset)
                if (read < 0) return@use false
                offset += read
            }
            header.contentEquals(EVIDENCE_HEADER)
        }

    fun decryptEvidence(payload: ByteArray, key: SecretKey): ByteArray {
        val output = ByteArrayOutputStream()
        decryptEvidence(ByteArrayInputStream(payload), output, key)
        return output.toByteArray()
    }

    /**
     * Decrypts and authenticates a streamed evidence payload. Callers must not
     * expose [output] until this returns: GCM verifies its tag at end-of-stream.
     */
    fun decryptEvidence(input: InputStream, output: OutputStream, key: SecretKey) {
        val header = ByteArray(EVIDENCE_HEADER.size)
        input.readFully(header)
        require(header.contentEquals(EVIDENCE_HEADER)) { "Unsupported evidence encryption format" }
        val iv = ByteArray(IV_BYTES)
        input.readFully(iv)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            updateAAD(EVIDENCE_HEADER)
        }
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            cipher.update(buffer, 0, read)?.let { output.write(it) }
        }
        output.write(cipher.doFinal())
        output.flush()
    }

    /** Authenticates encrypted evidence while hashing its plaintext without a temp copy. */
    fun hashEvidence(file: File, key: SecretKey): String {
        val digest = MessageDigest.getInstance("SHA-512")
        val sink = DigestOutputStream(OutputStream.nullOutputStream(), digest)
        file.inputStream().use { input -> sink.use { decryptEvidence(input, it, key) } }
        return digest.digest().toHex()
    }

    private fun InputStream.readFully(destination: ByteArray) {
        var offset = 0
        while (offset < destination.size) {
            val read = read(destination, offset, destination.size - offset)
            require(read >= 0) { "Truncated evidence encryption header" }
            offset += read
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
