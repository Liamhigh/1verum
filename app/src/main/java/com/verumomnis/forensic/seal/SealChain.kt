package com.verumomnis.forensic.seal

/**
 * VO-DSS-1.2 §8 — Seal Chain of Custody.
 * A sealed PDF carries the seal in its Subject metadata:
 *   VO-SEAL|<SHA512_128_HEX>|<SEAL_ID>[|CHAIN:VO-OLD1,VO-OLD2]
 * Re-sealing a previously sealed document preserves the prior seal IDs so
 * the full investigation timeline stays independently verifiable.
 */
object SealChain {

    const val SUBJECT_MAGIC = "VO-SEAL|"
    const val KEYWORDS_PREFIX = "verum,seal,"
    const val PRODUCER = "Verum Omnis Document Sealing Service v1.2"

    data class ParsedSealSubject(
        val sha512: String,
        val sealId: String,
        val chain: List<String> // previous seal IDs, excluding the current one
    )

    /** Parse a VO-SEAL subject string. Returns null when the document is unsealed. */
    fun parseSubject(subject: String?): ParsedSealSubject? {
        if (subject == null || !subject.startsWith(SUBJECT_MAGIC)) return null
        val parts = subject.split("|")
        if (parts.size < 3) return null
        val chain = if (parts.size >= 4 && parts[3].startsWith("CHAIN:")) {
            parts[3].substring(6).split(",").filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        return ParsedSealSubject(sha512 = parts[1], sealId = parts[2], chain = chain)
    }

    /** Build the subject string for a new seal, preserving the prior chain. */
    fun buildSubject(sha512: String, sealId: String, previousChain: List<String>): String {
        val prior = previousChain.filter { it.isNotEmpty() && it != sealId }
        val chainStr = if (prior.isNotEmpty()) "|CHAIN:" + prior.joinToString(",") else ""
        return "$SUBJECT_MAGIC$sha512|$sealId$chainStr"
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
