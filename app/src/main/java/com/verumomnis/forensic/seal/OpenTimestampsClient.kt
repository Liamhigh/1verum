package com.verumomnis.forensic.seal

import com.verumomnis.forensic.blockchain.OpenTimestampsService
import com.verumomnis.forensic.model.OtsStatus
import java.util.Base64

/**
 * VO-DSS-1.2 step 3 — OpenTimestamps submission.
 *
 * Thin adapter over the canonical anchoring implementation,
 * [com.verumomnis.forensic.blockchain.OpenTimestampsService], so there is exactly
 * ONE place that computes the OTS digest (SHA-256 of the document SHA-512),
 * talks to the public calendars (alice/bob.btc.calendar.opentimestamps.org) and
 * assembles the `.ots` proof. This object only maps the service's result onto
 * the seal-layer [OtsResult] API used by [DocumentSealer] and the seal pipeline.
 *
 * Honesty contract: a [OtsResult.Success] means the digest was really accepted
 * by a calendar and carries a real pending proof — the Bitcoin attestation is
 * still outstanding (status PENDING). When no calendar is reachable the result
 * is [OtsResult.Failure] with an explicit offline message. Success is never
 * fabricated.
 */
object OpenTimestampsClient {

    sealed class OtsResult {
        /**
         * The digest was accepted by at least one calendar. [calendar] is the
         * first calendar that responded; [proof] is the pending `.ots` proof.
         * NOTE: this is a PENDING anchor — Bitcoin attestation follows later.
         */
        data class Success(val calendar: String, val proof: ByteArray) : OtsResult() {
            override fun equals(other: Any?): Boolean =
                other is Success && calendar == other.calendar && proof.contentEquals(other.proof)
            override fun hashCode(): Int = 31 * calendar.hashCode() + proof.contentHashCode()
        }

        /** Anchoring did not complete. [error] states why (e.g. offline). */
        data class Failure(val error: String) : OtsResult()
    }

    /**
     * Anchor a document's SHA-512 forensic fingerprint to Bitcoin via the
     * canonical [OpenTimestampsService]. Blocking network I/O — call off the
     * main thread (the seal pipeline already runs on Dispatchers.IO).
     *
     * @param sha512Hex the document SHA-512 (128 hex chars)
     * @return [OtsResult.Success] with the pending proof when at least one
     *         calendar accepted the digest, otherwise [OtsResult.Failure]
     *         carrying the honest OFFLINE/FAILED message.
     */
    fun submit(sha512Hex: String): OtsResult {
        val anchor = runCatching { OpenTimestampsService.anchor(sha512Hex) }.getOrElse {
            return OtsResult.Failure("OpenTimestamps error — ${it.message ?: "unknown"}. Not anchored.")
        }
        return when (anchor.status) {
            OtsStatus.PENDING, OtsStatus.CONFIRMED -> {
                val proof = anchor.otsProofBase64
                    ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
                if (proof != null) {
                    OtsResult.Success(
                        calendar = anchor.calendarUrls.firstOrNull().orEmpty(),
                        proof = proof
                    )
                } else {
                    OtsResult.Failure(anchor.message.ifBlank { "No OTS proof returned. Not anchored." })
                }
            }
            OtsStatus.OFFLINE -> OtsResult.Failure(
                anchor.message.ifBlank { "Offline — digest not submitted; retry when online." }
            )
            OtsStatus.FAILED -> OtsResult.Failure(
                anchor.message.ifBlank { "OpenTimestamps anchoring failed." }
            )
        }
    }
}
