package com.verumomnis.forensic

import com.verumomnis.forensic.core.Constitution
import com.verumomnis.forensic.core.DeviceTier
import com.verumomnis.forensic.core.LlmRole
import com.verumomnis.forensic.core.ModelLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreGovernanceTest {

    @Test
    fun constitutionConstantsAreImmutableValues() {
        // v6.1: adds Prime Directive 16 (findings stated as fact) and drops the
        // unsupported "Court-Validated" status. Sealed 5 Aug 2026, VO-9E51D3F507E6.
        assertEquals("6.1", Constitution.VERSION)
        assertTrue(Constitution.FINAL)
        assertTrue(Constitution.FINDINGS_STATED_AS_FACT)
        assertEquals(9, Constitution.BRAIN_COUNT)
        assertEquals(20, Constitution.COMMISSION_PERCENT)
        assertEquals(72, Constitution.DEAD_MAN_SWITCH_HOURS)
        assertEquals(7, Constitution.GUARDIAN_COUNCIL_SIZE)
        assertEquals(0.003, Constitution.ETHICS_HALT_THRESHOLD, 0.0)
    }

    @Test
    fun lowEndDeviceLoadsGemma3AndPhi3Only() {
        val models = ModelLoader.loadModels(3)
        assertEquals(DeviceTier.LOW_END, DeviceTier.forRam(3))
        assertEquals(listOf("Gemma 3", "Phi-3"), models.map { it.name })
        assertEquals("Phi-3", ModelLoader.communicator(models).name)
        assertEquals("Gemma 3", ModelLoader.reportWriter(models).name)
    }

    @Test
    fun premiumDeviceLoadsLegalAndRnd() {
        val models = ModelLoader.loadModels(8)
        assertEquals(DeviceTier.PREMIUM, DeviceTier.forRam(8))
        assertTrue(models.any { it.role == LlmRole.LEGAL })
        assertTrue(models.any { it.role == LlmRole.RND })
        assertEquals("Gemma 4", ModelLoader.communicator(models).name)
    }

    /**
     * The report writer is offered on every device — but it is not bundled.
     *
     * This test previously asserted `bundled == true`, locking in a claim the
     * APK never satisfied: no model ships inside it. The writer is downloaded
     * and hash-verified like every other model, which is exactly why it has a
     * URL and a SHA-256 in [com.verumomnis.forensic.core.Constitution]. A test
     * that guards the false version of a fact keeps the product honest about
     * nothing, so it now guards the true one.
     */
    @Test
    fun gemma3IsOfferedOnEveryDeviceAsReportWriterAndIsNotBundled() {
        val gemma = ModelLoader.loadModels(1).first()
        assertEquals("Gemma 3", gemma.name)
        assertFalse("no model ships inside the APK — it is downloaded and verified", gemma.bundled)
        assertEquals(LlmRole.REPORT_WRITER, gemma.role)
    }

    /**
     * A model that is installed must be reachable by chat.
     *
     * [ModelLoader.communicator] names Phi-3 on a mid-range device whether or not
     * Phi-3 was ever downloaded, so the communicator slot can name a model that
     * does not exist. That is legitimate — this test records it — but it means
     * the caller must fall back to a model that *is* loaded, or chat answers
     * from canned text while a working model sits idle in memory.
     */
    @Test
    fun theNamedCommunicatorMayNotBeTheModelThatIsActuallyInstalled() {
        val models = ModelLoader.loadModels(5)
        assertEquals("Phi-3", ModelLoader.communicator(models).name)
        assertEquals("Gemma 3", ModelLoader.reportWriter(models).name)
    }
}
