package com.verumomnis.forensic

import com.verumomnis.forensic.core.LlmCommunicationStyle
import com.verumomnis.forensic.core.LlmRole
import com.verumomnis.forensic.core.ModelLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelLoaderTest {

    @Test
    fun lowEndDeviceHasOnlyGemma3() {
        val models = ModelLoader.loadModels(1)
        assertEquals(listOf("Gemma 3"), models.map { it.name })
        assertEquals(LlmRole.REPORT_WRITER, models.first().role)
    }

    @Test
    fun standardDeviceHasGemma3AndPhi3() {
        val models = ModelLoader.loadModels(2)
        assertEquals(listOf("Gemma 3", "Phi-3"), models.map { it.name })
        val phi3 = models.first { it.name == "Phi-3" }
        assertEquals(LlmRole.COMMUNICATOR, phi3.role)
        assertFalse(phi3.flagship)
    }

    @Test
    fun flagshipDeviceHasGemma4() {
        val models = ModelLoader.loadModels(4)
        assertTrue(models.any { it.name == "Gemma 4" })
        val gemma4 = models.first { it.name == "Gemma 4" }
        assertEquals(LlmRole.COMMUNICATOR, gemma4.role)
        assertTrue(gemma4.flagship)
        assertEquals(LlmCommunicationStyle.UNRESTRICTED, gemma4.communicationStyle)
    }

    @Test
    fun communicatorPrefersGemma4ThenPhi3ThenGemma3() {
        val flagship = ModelLoader.communicator(ModelLoader.loadModels(8))
        assertEquals("Gemma 4", flagship.name)

        val standard = ModelLoader.communicator(ModelLoader.loadModels(2))
        assertEquals("Phi-3", standard.name)

        val lowEnd = ModelLoader.communicator(ModelLoader.loadModels(1))
        assertEquals("Gemma 3", lowEnd.name)
    }

    @Test
    fun reportWriterIsAlwaysGemma3() {
        listOf(1, 2, 4, 8, 16).forEach { ram ->
            val writer = ModelLoader.reportWriter(ModelLoader.loadModels(ram))
            assertEquals("Gemma 3", writer.name)
        }
    }
}
