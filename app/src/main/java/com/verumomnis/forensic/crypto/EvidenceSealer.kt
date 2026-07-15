package com.verumomnis.forensic.crypto

import com.verumomnis.forensic.core.Constitution
import com.verumomnis.forensic.identity.IdentityProof
import com.verumomnis.forensic.model.SealRecord
import java.time.Instant

enum class VerificationResult { VERIFIED, SEAL_FOUND_NO_CHAIN, TAMPERED, NOT_FOUND }

/**
 * Cryptographic sealing (Part VI). Computes the SHA-512 fingerprint, derives the
 * seal identifiers, embeds the Constitution ruleset and produces a [SealRecord].
 *
 * Blockchain anchoring (OpenTimestamps → Bitcoin) is represented here; the actual
 * network submission is handled asynchronously by the sealing service. This class
 * performs the deterministic, offline-verifiable portion.
 */
object EvidenceSealer {

    private const val PREFIX_LEN = 16
    private const val SUFFIX_LEN = 8
    private const val SHORTCODE_LEN = 8

    fun seal(
        bytes: ByteArray,
        documentType: String,
        documentReference: String,
        nowInstant: Instant = Instant.now(),
        identityProof: IdentityProof? = null
    ): SealRecord {
        val sha512 = Sha512.hash(bytes)
        return sealFromHash(sha512, documentType, documentReference, nowInstant, identityProof)
    }

    fun sealFromHash(
        sha512: String,
        documentType: String,
        documentReference: String,
        nowInstant: Instant = Instant.now(),
        identityProof: IdentityProof? = null
    ): SealRecord {
        require(sha512.length == 128) { "SHA-512 hash must be 128 hex chars" }
        val shortcode = sha512.take(SHORTCODE_LEN)
        val truncated = sha512.take(PREFIX_LEN) + "..." + sha512.takeLast(SUFFIX_LEN)
        return SealRecord(
            sealId = "seal-${sha512.take(24)}",
            documentType = documentType,
            documentReference = documentReference,
            sha512 = sha512,
            truncatedHash = truncated,
            shortcode = shortcode,
            constitutionVersion = Constitution.VERSION,
            constitutionRuleset = Constitution.rulesetFingerprint(),
            otsProofFile = "seal_$shortcode.ots",
            status = "PENDING",
            createdAt = nowInstant.toString(),
            identityProof = identityProof
        )
    }

    /**
     * Seal verification (spec 6.4). Recomputes the fresh SHA-512, compares against
     * the embedded prefix/suffix, and factors in whether the blockchain proof is
     * confirmed.
     */
    fun verify(
        bytes: ByteArray,
        seal: SealRecord,
        blockchainConfirmed: Boolean = seal.status == "CONFIRMED"
    ): VerificationResult {
        val fresh = Sha512.hash(bytes)
        val prefix = seal.truncatedHash.substringBefore("...")
        val suffix = seal.truncatedHash.substringAfter("...")
        val prefixMatch = fresh.startsWith(prefix)
        val suffixMatch = fresh.endsWith(suffix)
        return when {
            prefixMatch && suffixMatch && blockchainConfirmed -> VerificationResult.VERIFIED
            prefixMatch && suffixMatch && !blockchainConfirmed -> VerificationResult.SEAL_FOUND_NO_CHAIN
            !prefixMatch || !suffixMatch -> VerificationResult.TAMPERED
            else -> VerificationResult.NOT_FOUND
        }
    }
}
