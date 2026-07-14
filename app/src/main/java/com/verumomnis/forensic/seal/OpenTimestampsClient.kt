package com.verumomnis.forensic.seal

import java.net.HttpURLConnection
import java.net.URL

/**
 * VO-DSS-1.2 step 3 — OpenTimestamps submission.
 * Posts the raw SHA-256 digest bytes to public calendar servers; the first
 * server to answer with a non-empty proof wins. Identical server list and
 * semantics to the web submitToOTS(). Runs off the main thread — call from
 * a coroutine / background executor.
 */
object OpenTimestampsClient {

    val DEFAULT_CALENDARS = listOf(
        "https://a.pool.opentimestamps.org/digest",
        "https://b.pool.opentimestamps.org/digest",
        "https://a.pool.eternitywall.com/digest"
    )

    sealed class OtsResult {
        data class Success(val calendar: String, val proof: ByteArray) : OtsResult() {
            override fun equals(other: Any?): Boolean =
                other is Success && calendar == other.calendar && proof.contentEquals(other.proof)
            override fun hashCode(): Int = 31 * calendar.hashCode() + proof.contentHashCode()
        }
        data class Failure(val error: String) : OtsResult()
    }

    /**
     * Submit a SHA-256 hex digest to the OpenTimestamps calendars.
     * Never throws — a network failure degrades to Failure so the seal still
     * completes and the hash can be re-anchored later.
     */
    fun submit(
        sha256Hex: String,
        calendars: List<String> = DEFAULT_CALENDARS,
        timeoutMs: Int = 15000
    ): OtsResult {
        val hashBytes = SealHasher.hexToBytes(sha256Hex)
        for (url in calendars) {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("Accept", "application/vnd.opentimestamps.v1")
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    doOutput = true
                }
                try {
                    conn.outputStream.use { it.write(hashBytes) }
                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        val proof = conn.inputStream.use { it.readBytes() }
                        if (proof.isNotEmpty()) return OtsResult.Success(url, proof)
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                // Calendar unreachable — try the next one.
            }
        }
        return OtsResult.Failure("All calendar servers failed — hash recorded for retry")
    }
}
