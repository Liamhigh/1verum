package com.verumomnis.forensic.seal

import java.security.MessageDigest

/**
 * VO-SEAL2 self-integrity checker.
 *
 * A VO-SEAL2 sealed PDF stores its subject as UTF-16BE-hex ASCII in the PDF
 * Info dictionary, e.g.:
 *   VO-SEAL2|<128-hex-sealed-file-sha512>
 *
 * To verify the document has not been modified since sealing, we substitute the
 * embedded sealed-file hash back to the fixed 128-zero placeholder, recompute
 * SHA-512 over the restored bytes, and compare with the embedded hash. This is
 * the exact algorithm used by the live webdocsol verify.html.
 */
object SealV2IntegrityChecker {

    private const val VO_SEAL2_PREFIX = "VO-SEAL2|"

    /** Fixed 128-zero placeholder patched with the sealed-file hash at seal time. */
    const val HASH_PLACEHOLDER =
        "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000"

    data class Result(
        val feasible: Boolean,
        val match: Boolean,
        val recomputed: String? = null
    )

    /**
     * Check VO-SEAL2 self-integrity.
     *
     * @param pdfBytes the full sealed PDF bytes.
     * @param embeddedHash the 128-hex SHA-512 sealed-file hash from the seal metadata.
     * @return [Result] indicating whether the check was feasible and whether the
     *         recomputed hash matches the embedded hash.
     */
    fun check(pdfBytes: ByteArray, embeddedHash: String): Result {
        require(embeddedHash.length == 128) { "embeddedHash must be 128 hex chars" }

        val needle = voUtf16Hex(VO_SEAL2_PREFIX + embeddedHash)
        val needleBytes = needle.toByteArray(Charsets.US_ASCII)

        val hits = findAll(pdfBytes, needleBytes, limit = 2)
        if (hits.size != 1) return Result(feasible = false, match = false)

        val start = hits[0] + voUtf16Hex(VO_SEAL2_PREFIX).length
        val placeholder = voUtf16Hex(HASH_PLACEHOLDER)
        val placeholderBytes = placeholder.toByteArray(Charsets.US_ASCII)

        if (placeholderBytes.size != embeddedHash.length * 4) {
            return Result(feasible = false, match = false)
        }

        val restored = pdfBytes.copyOf()
        for (i in placeholderBytes.indices) {
            restored[start + i] = placeholderBytes[i]
        }

        val recomputed = sha512Hex(restored)
        return Result(feasible = true, match = recomputed == embeddedHash, recomputed = recomputed)
    }

    /**
     * Embed the sealed-file hash into a freshly saved VO-SEAL2 PDF whose Subject
     * still carries [HASH_PLACEHOLDER]. Computes the SHA-512 over the placeholder
     * bytes and patches the hex in place (length-preserving), mirroring the web
     * sealer. Returns the sealed-file hash and the patched bytes.
     */
    fun embedSealedHash(pdfBytesWithPlaceholder: ByteArray): Pair<String, ByteArray> {
        val placeholderNeedle = voUtf16Hex(VO_SEAL2_PREFIX + HASH_PLACEHOLDER).toByteArray(Charsets.US_ASCII)
        val hits = findAll(pdfBytesWithPlaceholder, placeholderNeedle, limit = 2)
        require(hits.size == 1) {
            "VO-SEAL2 placeholder subject must appear exactly once in the sealed PDF (found ${hits.size})"
        }
        val sealedHash = sha512Hex(pdfBytesWithPlaceholder)
        val hashHexAscii = voUtf16Hex(sealedHash).toByteArray(Charsets.US_ASCII)
        val start = hits[0] + voUtf16Hex(VO_SEAL2_PREFIX).length
        val patched = pdfBytesWithPlaceholder.copyOf()
        for (i in hashHexAscii.indices) {
            patched[start + i] = hashHexAscii[i]
        }
        return sealedHash to patched
    }

    /** Convert a string to its UTF-16BE-hex ASCII representation (no BOM). */
    private fun voUtf16Hex(str: String): String = buildString {
        for (c in str) {
            append(c.code.toString(16).uppercase().padStart(4, '0'))
        }
    }

    private fun sha512Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-512").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Find all start indices of [needle] in [haystack], up to [limit]. */
    private fun findAll(haystack: ByteArray, needle: ByteArray, limit: Int = Int.MAX_VALUE): List<Int> {
        if (needle.isEmpty() || haystack.isEmpty() || needle.size > haystack.size) return emptyList()
        val hits = mutableListOf<Int>()
        val max = haystack.size - needle.size
        for (i in 0..max) {
            if (haystack[i] != needle[0]) continue
            var ok = true
            for (j in 1 until needle.size) {
                if (haystack[i + j] != needle[j]) { ok = false; break }
            }
            if (ok) {
                hits.add(i)
                if (hits.size >= limit) return hits
            }
        }
        return hits
    }
}
