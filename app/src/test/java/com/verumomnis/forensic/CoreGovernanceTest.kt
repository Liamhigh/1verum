package com.verumomnis.forensic

import com.verumomnis.forensic.core.Constitution
import com.verumomnis.forensic.core.DeviceTier
import com.verumomnis.forensic.core.LlmRole
import com.verumomnis.forensic.core.ModelLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreGovernanceTest {

    @Test
    fun constitutionConstantsAreImmutableValues() {
        assertEquals("5.2.8", Constitution.VERSION)
        assertTrue(Constitution.FINAL)
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

    @Test
    fun gemma3IsAlwaysBundledAndReportWriter() {
        val gemma = ModelLoader.loadModels(1).first()
        assertEquals("Gemma 3", gemma.name)
        assertTrue(gemma.bundled)
        assertEquals(LlmRole.REPORT_WRITER, gemma.role)
    }
}
