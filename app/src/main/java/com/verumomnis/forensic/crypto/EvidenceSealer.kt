package com.verumomnis.forensic.crypto

import com.verumomnis.forensic.model.SealRecord
import java.time.Instant

/**
 * Evidence sealing primitive (spec 6.1–6.4).
 */
object EvidenceSealer {

    enum class VerificationResult { VERIFIED, TAMPERED, SEAL_FOUND_NO_CHAIN, NOT_FOUND }

    /** Creates a seal record over the provided evidence bytes. */
    fun seal(
        bytes: ByteArray,
        kind: String,
        documentReference: String,
        timestamp: Instant = Instant.now()
    ): SealRecord = sealFromHash(Sha512.hash(bytes), kind, documentReference, timestamp)

    /** Creates a seal record from a precomputed hash (used by identity flows). */
    fun sealFromHash(
        sha512: String,
        kind: String,
        documentReference: String,
        timestamp: Instant = Instant.now()
    ): SealRecord {
        require(sha512.length == 128) { "SHA-512 hex must be 128 chars" }
        val sealId = "seal_${timestamp.toEpochMilli()}"
        return SealRecord(
            sealId = sealId,
            kind = kind,
            sha512 = sha512,
            truncatedHash = SealHasher.truncatedHash(sha512),
            documentReference = documentReference,
            timestamp = timestamp.toString()
        )
    }

    /**
     * Seal verification (spec 6.4). Recomputes the fresh SHA-512 and compares it
     * against the seal record. When the record carries the full 128-char SHA-512
     * the ENTIRE fingerprint is compared — the truncatedHash prefix/suffix is a
     * display form and is only used as a fallback for legacy records that lack
     * the full hash. Blockchain confirmation factors into the final verdict.
     */
    fun verify(
        bytes: ByteArray,
        seal: SealRecord,
        blockchainConfirmed: Boolean = seal.status == "CONFIRMED"
    ): VerificationResult {
        val fresh = Sha512.hash(bytes)
        val hashMatches = if (seal.sha512.length == 128) {
            fresh.equals(seal.sha512, ignoreCase = true)
        } else {
            // Fallback only: legacy records without a full SHA-512.
            val prefix = seal.truncatedHash.substringBefore("...")
            val suffix = seal.truncatedHash.substringAfter("...")
            fresh.startsWith(prefix) && fresh.endsWith(suffix)
        }
        return when {
            hashMatches && blockchainConfirmed -> VerificationResult.VERIFIED
            hashMatches && !blockchainConfirmed -> VerificationResult.SEAL_FOUND_NO_CHAIN
            !hashMatches -> VerificationResult.TAMPERED
            else -> VerificationResult.NOT_FOUND
        }
    }
}
