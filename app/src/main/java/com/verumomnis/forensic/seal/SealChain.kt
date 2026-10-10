package com.verumomnis.forensic.seal

/**
 * VO-DSS-1.2 §8 — Seal Chain of Custody.
 * A sealed PDF carries the seal in its Subject metadata:
 *   legacy: VO-SEAL|<ORIGINAL_SHA512_128_HEX>|<SEAL_ID>[|CHAIN:VO-OLD1,VO-OLD2]
 *   v2:     VO-SEAL2|<SEALED-FILE_SHA512_128_HEX>|<SEAL_ID>|ORIG:<ORIGINAL_SHA512>[|CHAIN:...]
 * Re-sealing a previously sealed document preserves the prior seal IDs so
 * the full investigation timeline stays independently verifiable.
 */
object SealChain {

    const val SUBJECT_MAGIC = "VO-SEAL|"
    const val SUBJECT_MAGIC_V2 = "VO-SEAL2|"
    const val KEYWORDS_PREFIX = "verum,seal,"
    const val PRODUCER = "Verum Omnis Document Sealing Service v1.2"

    data class ParsedSealSubject(
        val scheme: String,      // "v2" or "legacy"
        val sha512: String,      // sealed-file hash for v2, original-content hash for legacy
        val sealId: String,
        val chain: List<String>, // previous seal IDs, excluding the current one
        val origHash: String? = null // original-content hash for v2 (optional)
    )

    /** Parse a VO-SEAL or VO-SEAL2 subject string. Returns null when the document is unsealed. */
    fun parseSubject(subject: String?): ParsedSealSubject? {
        if (subject == null) return null
        val isV2 = subject.startsWith(SUBJECT_MAGIC_V2)
        val isLegacy = subject.startsWith(SUBJECT_MAGIC)
        if (!isV2 && !isLegacy) return null
        val parts = subject.split("|")
        if (parts.size < 3) return null
        val hash = parts[1]
        if (!Regex("^[0-9a-fA-F]{128}$").matches(hash)) return null
        if (!Regex("^VO-[0-9A-Za-z-]+$").matches(parts[2])) return null

        var chain: List<String> = emptyList()
        var origHash: String? = null
        for (i in 3 until parts.size) {
            when {
                parts[i].startsWith("ORIG:") -> origHash = parts[i].substring(5).lowercase()
                parts[i].startsWith("CHAIN:") -> chain = parts[i].substring(6).split(",").filter { it.isNotEmpty() }
            }
        }

        return ParsedSealSubject(
            scheme = if (isV2) "v2" else "legacy",
            sha512 = hash.lowercase(),
            sealId = parts[2],
            chain = chain,
            origHash = origHash
        )
    }

    /**
     * Build the subject string for a new seal, preserving the prior chain.
     * For scheme "v2" (default, matching the website) [sha512] is the sealed-file
     * hash (or its placeholder at seal time) and [origHash] carries the original
     * pre-seal content hash: VO-SEAL2|<sealed-hash>|<sealId>|ORIG:<orig-hash>.
     */
    fun buildSubject(
        sha512: String,
        sealId: String,
        previousChain: List<String>,
        scheme: String = "v2",
        origHash: String? = null
    ): String {
        val prior = previousChain.filter { it.isNotEmpty() && it != sealId }
        val chainStr = if (prior.isNotEmpty()) "|CHAIN:" + prior.joinToString(",") else ""
        return if (scheme == "v2") {
            val origStr = origHash?.let { "|ORIG:$it" } ?: ""
            "$SUBJECT_MAGIC_V2$sha512|$sealId$origStr$chainStr"
        } else {
            "$SUBJECT_MAGIC$sha512|$sealId$chainStr"
        }
    }

    /** Keywords string, matching the web sealer. */
    fun buildKeywords(sha512: String, sealType: String): String =
        "$KEYWORDS_PREFIX${sha512.substring(0, 16)},$sealType"

    /** All prior seal IDs carried by a subject (excluding the new seal's own ID), deduplicated. */
    fun chainIdsFrom(parsed: ParsedSealSubject?, newSealId: String): List<String> {
        if (parsed == null) return emptyList()
        val ids = listOf(parsed.sealId) + parsed.chain
        return ids.filter { it.isNotEmpty() && it != newSealId }.distinct()
    }
}
