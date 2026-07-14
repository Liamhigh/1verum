package com.verumomnis.forensic.seal

import java.security.MessageDigest

/**
 * VO-DSS-1.2 — Verum Omnis Document Sealing Standard v1.2 (Android port).
 * Source of truth: Liamhigh/webdocsol (seal-module/web). Interoperable with
 * the website and the Firewall: identical hashes, Seal IDs, metadata, and
 * Subject formats on every platform.
 *
 * Dual-hash rule: SHA-256 anchors to Bitcoin via OpenTimestamps;
 * SHA-512 is the Verum forensic fingerprint (footer + QR + Seal ID).
 */
object SealHasher {

    /** SHA-256 of document bytes — used for the OpenTimestamps submission. */
    fun sha256Hex(bytes: ByteArray): String = digestHex("SHA-256", bytes)

    /** SHA-512 of document bytes — the Verum forensic fingerprint. */
    fun sha512Hex(bytes: ByteArray): String = digestHex("SHA-512", bytes)

    /** Seal ID derivation — identical on all platforms: VO- + first 12 hex chars, uppercased. */
    fun sealIdFromSha512(sha512: String): String =
        "VO-" + sha512.substring(0, 12).uppercase()

    /** Hex string -> raw bytes (for OTS digest submission). */
    fun hexToBytes(hex: String): ByteArray {
        val out = ByteArray(hex.length / 2)
        var i = 0
        while (i < hex.length) {
            out[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
            i += 2
        }
        return out
    }

    private fun digestHex(algorithm: String, bytes: ByteArray): String {
        val digest = MessageDigest.getInstance(algorithm).digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
