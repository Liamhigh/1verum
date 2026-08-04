package com.verumomnis.forensic.seal

import com.verumomnis.forensic.blockchain.OpenTimestampsService
import com.verumomnis.forensic.model.OtsStatus
import com.verumomnis.forensic.vault.EvidenceVault

/**
 * Completes the OpenTimestamps anchor for seals that are still pending.
 *
 * VO-DSS-1.2 §3 submits a digest to the calendars, which returns a *pending*
 * proof — the Bitcoin attestation follows an hour or two later. Turning that
 * pending proof into a confirmed one requires a second call to the calendars'
 * upgrade endpoint.
 *
 * That call existed on [OpenTimestampsService] and was never made from anywhere
 * in the app. Every seal therefore stayed PENDING forever, which meant the
 * platform's central promise — anchored to Bitcoin, independently verifiable by
 * anyone — was not true of any document it had ever produced. This class is the
 * missing step.
 *
 * It is deliberately conservative: a proof is replaced on disk only when the
 * calendars return an upgraded one, and CONFIRMED is only ever reported when the
 * upgraded proof actually carries a Bitcoin attestation. A failed or offline
 * upgrade leaves the pending proof exactly as it was, so nothing is lost by
 * trying and nothing is claimed by failing.
 */
class AnchorUpgrader(
    private val vault: EvidenceVault,
    private val service: OpenTimestampsService = OpenTimestampsService
) {

    data class Outcome(
        val shortcode: String,
        val status: OtsStatus,
        val message: String,
        /** True when this run moved the seal from pending to confirmed. */
        val newlyConfirmed: Boolean
    )

    /**
     * Attempts to upgrade one stored proof.
     *
     * @return null when no proof is stored for [shortcode] — nothing to upgrade.
     */
    fun upgrade(shortcode: String): Outcome? {
        val stored = vault.loadOtsProof(shortcode) ?: return null

        // Already confirmed? Don't touch the calendars again; a confirmed proof
        // is final and re-submitting would only risk replacing it with a worse one.
        val before = runCatching { service.verifyBase64(stored, checkBitcoin = false) }.getOrNull()
        if (before?.status == OtsStatus.CONFIRMED) {
            return Outcome(shortcode, OtsStatus.CONFIRMED, "Bitcoin attestation already present.", false)
        }

        val result = runCatching { service.upgrade(stored) }.getOrNull()
            ?: return Outcome(
                shortcode,
                OtsStatus.PENDING,
                "Calendars unreachable — seal remains pending Bitcoin confirmation.",
                false
            )

        // Nullable: the calendars may accept the request without returning a
        // proof, in which case there is nothing new to persist.
        val upgradedProof = result.otsProofBase64.orEmpty()
        if (upgradedProof.isNotBlank() && upgradedProof != stored) {
            // Persist whatever the calendars returned, even when still pending:
            // an upgraded pending proof is strictly closer to confirmation than
            // the original, and keeping it shortens the next attempt.
            runCatching { vault.storeOtsProof(shortcode, upgradedProof) }
        }

        return Outcome(
            shortcode = shortcode,
            status = result.status,
            message = result.message,
            newlyConfirmed = result.status == OtsStatus.CONFIRMED
        )
    }

    /**
     * Upgrades every pending proof in the vault.
     *
     * Called on launch and on demand, so a seal created yesterday reaches
     * confirmed state without the user knowing an upgrade step exists.
     */
    fun upgradeAllPending(): List<Outcome> =
        vault.listOtsShortcodes().mapNotNull { upgrade(it) }
}
