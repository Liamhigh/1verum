package com.verumomnis.forensic

import com.verumomnis.forensic.core.Constitution
import com.verumomnis.forensic.core.DeadManSwitch
import com.verumomnis.forensic.core.Safeguards
import com.verumomnis.forensic.vault.VaultEncryption
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SafeguardsTest {

    // ---- Ethics kill switch ----
    @Test fun ethicsHaltAboveThreshold() {
        assertFalse(Safeguards.ethicsHalt(0.002))
        assertTrue(Safeguards.ethicsHalt(0.004))
        assertFalse(Safeguards.ethicsHalt(Constitution.ETHICS_HALT_THRESHOLD)) // not strictly greater
    }

    // ---- Profit firewall + commission ----
    @Test fun profitFirewallRoutes99Percent() {
        val split = Safeguards.profitSplit(1_000_000.0)
        assertEquals(990_000.0, split.toFoundation, 0.001)
        assertEquals(10_000.0, split.retained, 0.001)
    }

    @Test fun commissionIsTwentyPercent() {
        assertEquals(100_000.0, Safeguards.commission(500_000.0), 0.001)
    }

    // ---- No privatization / anti-war ----
    @Test fun privatizationAttemptsRejected() {
        assertTrue(Safeguards.isPrivatizationAttempt("We want to acquire equity stake in Verum"))
        assertFalse(Safeguards.isPrivatizationAttempt("Please analyse this contract"))
    }

    @Test fun antiWarDoctrineBlocksWeaponization() {
        assertTrue(Safeguards.violatesAntiWarDoctrine("Integrate this into the missile kill chain"))
        assertFalse(Safeguards.violatesAntiWarDoctrine("Analyse this fraud evidence"))
    }

    @Test fun constitutionIsFinal() {
        assertTrue(Safeguards.constitutionIsFinal())
    }

    // ---- Dead-Man Switch ----
    @Test fun deadManSwitchFiresAfter72h() {
        val start = Instant.parse("2026-07-01T00:00:00Z")
        val dms = DeadManSwitch(start)
        assertFalse(dms.isTriggered(start.plusSeconds(71 * 3600)))
        assertTrue(dms.isTriggered(start.plusSeconds(72 * 3600)))
        assertNull(dms.checkAndBuildAlert(start.plusSeconds(1 * 3600)))
        val alert = dms.checkAndBuildAlert(start.plusSeconds(80 * 3600))
        assertNotNull(alert)
        assertEquals("AUTO_RELEASE_TO_INTERPOL", alert!!.action)
    }

    @Test fun recordActivityResetsTimer() {
        val start = Instant.parse("2026-07-01T00:00:00Z")
        val dms = DeadManSwitch(start)
        dms.recordActivity(start.plusSeconds(71 * 3600))
        assertFalse(dms.isTriggered(start.plusSeconds(72 * 3600)))
    }

    // ---- Vault AES-256-GCM ----
    @Test fun aesGcmRoundTrips() {
        val key = VaultEncryption.generateKey()
        val plain = "sealed chat session — confidential".toByteArray()
        val enc = VaultEncryption.encrypt(plain, key)
        assertFalse(enc.contentEquals(plain))
        assertArrayEquals(plain, VaultEncryption.decrypt(enc, key))
    }

    @Test(expected = Exception::class)
    fun aesGcmRejectsWrongKey() {
        val enc = VaultEncryption.encrypt("secret".toByteArray(), VaultEncryption.generateKey())
        VaultEncryption.decrypt(enc, VaultEncryption.generateKey()) // different key -> tag failure
    }
}
