package com.verumomnis.forensic.blockchain

import com.verumomnis.forensic.model.OtsAnchorResult
import com.verumomnis.forensic.model.OtsStatus
import com.verumomnis.forensic.model.OtsVerifyResult
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant

/**
 * Cryptographic sealing — Bitcoin anchoring via OpenTimestamps (build spec §19).
 *
 * The evidence SHA-512 is reduced to a 32-byte SHA-256 digest (OTS operates on
 * SHA-256), submitted to the public OpenTimestamps Bitcoin calendars, and the
 * returned pending timestamp is assembled into a standards-compliant detached
 * `.ots` proof: MAGIC | version(0x01) | op(0x08 = SHA256) | digest(32) | <calendar
 * timestamp>. That proof can be upgraded/verified by any OpenTimestamps client
 * once the Bitcoin attestation confirms (typically 1–2 h + 6 confirmations).
 *
 * Pure JVM (java.net) so the anchoring logic is unit-testable off-device; the
 * offline path returns an OFFLINE result and still produces a local proof stub
 * for later submission.
 */
object OpenTimestampsService {

    val CALENDAR_URLS = listOf(
        "https://alice.btc.calendar.opentimestamps.org",
        "https://bob.btc.calendar.opentimestamps.org"
    )
    private const val BITCOIN_TIP_URL = "https://mempool.space/api/blocks/tip/height"

    // .ots detached proof header.
    private val MAGIC = hexToBytes("004f70656e54696d657374616d7073000050726f6f6600bf89e2e884e89294")
    private const val VERSION: Byte = 0x01
    private const val OP_SHA256: Byte = 0x08

    // OTS attestation tags.
    private val PENDING_TAG = hexToBytes("83dfe30d2ef90c8e")
    private val BITCOIN_TAG = hexToBytes("0588960d73d71901")

    /** OTS digest = SHA-256 over the raw bytes of the SHA-512 hex (build spec sha512ToSha256). */
    fun sha256OfSha512(sha512Hex: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(hexToBytes(sha512Hex))

    /** Assemble a single-calendar detached .ots proof. */
    fun buildProof(digest: ByteArray, calendarTimestamp: ByteArray): ByteArray =
        MAGIC + byteArrayOf(VERSION, OP_SHA256) + digest + calendarTimestamp

    /**
     * Anchor an evidence SHA-512 to the Bitcoin blockchain via OpenTimestamps.
     * Network I/O — call off the main thread.
     */
    fun anchor(sha512Hex: String, now: Instant = Instant.now(), timeoutMs: Int = 20_000): OtsAnchorResult {
        val digest = sha256OfSha512(sha512Hex)
        val digestHex = toHex(digest)
        var proof: ByteArray? = null
        val usedCalendars = mutableListOf<String>()
        for (url in CALENDAR_URLS) {
            val ts = runCatching { submitDigest(url, digest, timeoutMs) }.getOrNull()
            if (ts != null && ts.isNotEmpty()) {
                usedCalendars += url
                if (proof == null) proof = buildProof(digest, ts) // primary proof from first success
            }
        }
        return if (proof != null) {
            OtsAnchorResult(
                status = OtsStatus.PENDING,
                sha512 = sha512Hex,
                sha256Digest = digestHex,
                calendarUrls = usedCalendars,
                otsProofBase64 = java.util.Base64.getEncoder().encodeToString(proof),
                otsProofFile = "seal_${sha512Hex.take(8)}.ots",
                submittedAt = now.toString(),
                message = "Pending Bitcoin attestation via ${usedCalendars.size} calendar(s)."
            )
        } else {
            OtsAnchorResult(
                status = OtsStatus.OFFLINE,
                sha512 = sha512Hex,
                sha256Digest = digestHex,
                calendarUrls = emptyList(),
                otsProofBase64 = null,
                otsProofFile = "seal_${sha512Hex.take(8)}.ots.pending",
                submittedAt = now.toString(),
                message = "Offline — digest stored locally for later submission."
            )
        }
    }

    /** Inspect/verify a detached .ots proof. Optionally checks Bitcoin reachability. */
    fun verify(otsProof: ByteArray, checkBitcoin: Boolean = true, timeoutMs: Int = 15_000): OtsVerifyResult {
        val attested = containsSubsequence(otsProof, BITCOIN_TAG)
        val pending = containsSubsequence(otsProof, PENDING_TAG)
        val tip = if (checkBitcoin) runCatching { bitcoinTipHeight(timeoutMs) }.getOrNull() else null
        val status = when {
            attested -> OtsStatus.CONFIRMED
            pending -> OtsStatus.PENDING
            else -> OtsStatus.FAILED
        }
        return OtsVerifyResult(
            status = status,
            pending = pending && !attested,
            bitcoinAttested = attested,
            bitcoinTipHeight = tip,
            message = when (status) {
                OtsStatus.CONFIRMED -> "Bitcoin block-header attestation present."
                OtsStatus.PENDING -> "Pending calendar attestation; awaiting Bitcoin confirmation."
                else -> "No recognised OTS attestation found."
            }
        )
    }

    fun verifyBase64(otsProofBase64: String, checkBitcoin: Boolean = true): OtsVerifyResult =
        verify(java.util.Base64.getDecoder().decode(otsProofBase64), checkBitcoin)

    private fun submitDigest(calendarUrl: String, digest: ByteArray, timeoutMs: Int): ByteArray? {
        val conn = (URL("$calendarUrl/digest").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Accept", "application/vnd.opentimestamps.v1")
            setRequestProperty("Content-Type", "application/octet-stream")
            setRequestProperty("User-Agent", "verum-omnis-seal/1.0")
        }
        return try {
            DataOutputStream(conn.outputStream).use { it.write(digest) }
            if (conn.responseCode in 200..299) conn.inputStream.use { it.readBytes() } else null
        } finally {
            conn.disconnect()
        }
    }

    private fun bitcoinTipHeight(timeoutMs: Int): Long? {
        val conn = (URL(BITCOIN_TIP_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = timeoutMs; readTimeout = timeoutMs
            setRequestProperty("User-Agent", "verum-omnis-seal/1.0")
        }
        return try {
            if (conn.responseCode in 200..299) conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8).trim().toLongOrNull() else null
        } finally {
            conn.disconnect()
        }
    }

    private fun containsSubsequence(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
        return out
    }

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
