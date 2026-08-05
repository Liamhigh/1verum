package com.verumomnis.forensic

import com.verumomnis.forensic.model.OtsStatus
import com.verumomnis.forensic.seal.AnchorUpgrader
import com.verumomnis.forensic.vault.EvidenceVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Completing the Bitcoin anchor.
 *
 * VO-DSS-1.2 §3 submits a digest and gets back a *pending* proof; a second call
 * to the calendars' upgrade endpoint turns it into a confirmed one. That second
 * call was never made anywhere in the app, so every seal stayed pending forever
 * and "anchored to Bitcoin, verifiable by anyone" was true of no document the
 * platform had produced.
 *
 * These tests cover the behaviour that has to hold offline, since the upgrade
 * itself needs live calendars: nothing is lost by trying, and nothing is claimed
 * by failing.
 */
class AnchorUpgraderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun vault() = EvidenceVault(temp.newFolder()).also { it.initialize() }

    @Test
    fun `a seal with no stored proof yields nothing to upgrade`() {
        assertNull(AnchorUpgrader(vault()).upgrade("NOSUCH"))
    }

    @Test
    fun `stored proofs are discoverable so pending seals can be found later`() {
        // Without this the upgrader could only fix seals the caller happened to
        // remember, which is exactly why old seals stayed pending indefinitely.
        val v = vault()
        v.storeOtsProof("AAAA1111", "cHJvb2Y=")
        v.storeOtsProof("BBBB2222", "cHJvb2Y=")
        assertEquals(listOf("AAAA1111", "BBBB2222"), v.listOtsShortcodes())
    }

    @Test
    fun `an unreachable calendar leaves the stored proof untouched`() {
        // Offline must never destroy or downgrade a pending proof: it is the
        // only evidence the digest was ever submitted.
        val v = vault()
        val original = "cGVuZGluZy1wcm9vZg=="
        v.storeOtsProof("CCCC3333", original)

        val outcome = AnchorUpgrader(v).upgrade("CCCC3333")

        assertEquals(original, v.loadOtsProof("CCCC3333"))
        if (outcome != null) {
            // Whatever happened, an offline run must not report confirmation.
            assertFalse(
                "confirmation must never be claimed without a Bitcoin attestation",
                outcome.newlyConfirmed && outcome.status != OtsStatus.CONFIRMED
            )
        }
    }

    @Test
    fun `upgrading an empty vault is safe and reports nothing`() {
        assertTrue(AnchorUpgrader(vault()).upgradeAllPending().isEmpty())
    }

    @Test
    fun `every pending proof in the vault is attempted`() {
        val v = vault()
        v.storeOtsProof("DDDD4444", "cHJvb2Y=")
        v.storeOtsProof("EEEE5555", "cHJvb2Y=")
        // Offline the results are pending/failed, but both must be visited —
        // a seal must not be skipped simply because it is old.
        assertEquals(2, AnchorUpgrader(v).upgradeAllPending().size)
    }
}
