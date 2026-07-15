package com.verumomnis.forensic.seal

/**
 * VO-DSS-1.2 step 3 — OpenTimestamps submission placeholder.
 *
 * The canonical blockchain implementation is [com.verumomnis.forensic.blockchain.OpenTimestampsService],
 * which is wired through [com.verumomnis.forensic.ui.VerumViewModel]. This seal-layer client is kept
 * only to satisfy the [DocumentSealer] API without duplicating network/calendar logic. It returns a
 * deterministic offline result so the seal still completes; callers that need a real Bitcoin anchor
 * should submit the document SHA-512 through the canonical service.
 */
object OpenTimestampsClient {

    sealed class OtsResult {
        data class Success(val calendar: String, val proof: ByteArray) : OtsResult() {
            override fun equals(other: Any?): Boolean =
                other is Success && calendar == other.calendar && proof.contentEquals(other.proof)
            override fun hashCode(): Int = 31 * calendar.hashCode() + proof.contentHashCode()
        }
        data class Failure(val error: String) : OtsResult()
    }

    /**
     * Returns an offline result so [DocumentSealer.seal] always completes.
     * Real anchoring is performed by the app's canonical OpenTimestampsService.
     */
    fun submit(sha256Hex: String): OtsResult =
        OtsResult.Failure("OpenTimestampsClient is a stub; use OpenTimestampsService for Bitcoin anchoring.")
}
