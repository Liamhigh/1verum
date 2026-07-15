package com.verumomnis.forensic.blockchain

import com.verumomnis.forensic.model.OtsAnchorResult
import com.verumomnis.forensic.model.OtsStatus
import com.verumomnis.forensic.model.OtsVerifyResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit

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
 * Pure JVM (java.security + OkHttp) so the anchoring logic is unit-testable
 * off-device; the offline path returns an OFFLINE result and still produces a
 * local proof stub for later submission.
 */
object OpenTimestampsService : BlockchainService {

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

    private val OCTET_STREAM = "application/octet-stream".toMediaType()
    private val OTS_MEDIA_TYPE = "application/vnd.opentimestamps.v1".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

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
    override fun anchor(sha512Hex: String): OtsAnchorResult =
        anchor(sha512Hex, Instant.now(), 20_000)

    /**
     * Anchor with explicit timestamp and timeout. Used by tests and by the
     * public [BlockchainService.anchor] implementation above.
     */
    fun anchor(sha512Hex: String, now: Instant, timeoutMs: Int): OtsAnchorResult {
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

    override fun verify(otsProofBase64: String): OtsVerifyResult =
        verifyBase64(otsProofBase64)

    fun verifyBase64(otsProofBase64: String, checkBitcoin: Boolean = true): OtsVerifyResult =
        verify(java.util.Base64.getDecoder().decode(otsProofBase64), checkBitcoin)

    /**
     * Upgrade a pending .ots proof by submitting it to the calendar upgrade
     * endpoint. Returns a CONFIRMED result if Bitcoin attestation is available,
     * otherwise PENDING/OFFLINE.
     */
    override fun upgrade(otsProofBase64: String): OtsAnchorResult {
        val now = Instant.now()
        val proofBytes = runCatching { java.util.Base64.getDecoder().decode(otsProofBase64) }.getOrElse {
            return failedResult("", "Invalid Base64 proof")
        }
        val digest = extractDigest(proofBytes) ?: return failedResult("", "Could not extract digest from proof")
        val digestHex = toHex(digest)
        var upgraded: ByteArray? = null
        val usedCalendars = mutableListOf<String>()
        for (url in CALENDAR_URLS) {
            val result = runCatching { submitUpgrade(url, proofBytes, 20_000) }.getOrNull()
            if (result != null && result.isNotEmpty()) {
                usedCalendars += url
                if (upgraded == null) upgraded = result
            }
        }
        return if (upgraded != null) {
            val verified = verify(upgraded, checkBitcoin = false)
            OtsAnchorResult(
                status = verified.status,
                sha512 = "",
                sha256Digest = digestHex,
                calendarUrls = usedCalendars,
                otsProofBase64 = java.util.Base64.getEncoder().encodeToString(upgraded),
                otsProofFile = "seal_${digestHex.take(8)}.ots",
                submittedAt = now.toString(),
                message = if (verified.status == OtsStatus.CONFIRMED) {
                    "Bitcoin attestation confirmed."
                } else {
                    "Upgrade submitted; still pending Bitcoin confirmation."
                }
            )
        } else {
            OtsAnchorResult(
                status = OtsStatus.OFFLINE,
                sha512 = "",
                sha256Digest = digestHex,
                calendarUrls = emptyList(),
                otsProofBase64 = otsProofBase64,
                otsProofFile = "seal_${digestHex.take(8)}.ots.pending",
                submittedAt = now.toString(),
                message = "Offline — could not upgrade proof; will retry later."
            )
        }
    }

    private fun submitDigest(calendarUrl: String, digest: ByteArray, timeoutMs: Int): ByteArray? {
        val callClient = client.newBuilder()
            .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder()
            .url("$calendarUrl/digest")
            .post(digest.toRequestBody(OCTET_STREAM))
            .header("Accept", "application/vnd.opentimestamps.v1")
            .header("User-Agent", "verum-omnis-seal/1.0")
            .build()
        return callClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.bytes() else null
        }
    }

    private fun submitUpgrade(calendarUrl: String, proof: ByteArray, timeoutMs: Int): ByteArray? {
        val callClient = client.newBuilder()
            .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder()
            .url("$calendarUrl/upgrade")
            .post(proof.toRequestBody(OTS_MEDIA_TYPE))
            .header("Accept", "application/vnd.opentimestamps.v1")
            .header("User-Agent", "verum-omnis-seal/1.0")
            .build()
        return callClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.bytes() else null
        }
    }

    private fun bitcoinTipHeight(timeoutMs: Int): Long? {
        val callClient = client.newBuilder()
            .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder()
            .url(BITCOIN_TIP_URL)
            .header("User-Agent", "verum-omnis-seal/1.0")
            .build()
        return callClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                response.body?.string()?.trim()?.toLongOrNull()
            } else null
        }
    }

    private fun extractDigest(proof: ByteArray): ByteArray? {
        // Proof layout: MAGIC(16) | VERSION(1) | OP_SHA256(1) | DIGEST(32) | ...
        val magicSize = MAGIC.size
        val prefixSize = magicSize + 2 + 32
        if (proof.size < prefixSize) return null
        if (!containsSubsequence(proof.copyOfRange(0, magicSize), MAGIC)) return null
        if (proof[magicSize] != VERSION || proof[magicSize + 1] != OP_SHA256) return null
        return proof.copyOfRange(magicSize + 2, prefixSize)
    }

    private fun failedResult(sha512: String, message: String): OtsAnchorResult =
        OtsAnchorResult(
            status = OtsStatus.FAILED,
            sha512 = sha512,
            sha256Digest = "",
            calendarUrls = emptyList(),
            otsProofBase64 = null,
            otsProofFile = "",
            submittedAt = Instant.now().toString(),
            message = message
        )

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
