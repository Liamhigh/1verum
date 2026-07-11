package com.verumomnis.forensic

import com.verumomnis.forensic.engine.BehavioralBrain
import com.verumomnis.forensic.engine.ForensicService
import org.junit.Assert.assertTrue
import org.junit.Test

class BehavioralBrainTest {

    private fun doc(text: String) =
        ForensicService.ingest("DOC001", "chat.txt", "chat_export", text.toByteArray())

    @Test
    fun detectsGaslightingStressAndManipulation() {
        val analysis = BehavioralBrain.analyze(
            listOf(
                doc(
                    "You're imagining things, that never happened. PLEASE, I am desperate and mentally broken. " +
                        "They said no lawyers, just the two of us, and removed my only witness."
                )
            )
        )
        assertTrue(analysis.gaslighting.isNotEmpty())
        assertTrue(analysis.stress.isNotEmpty())
        assertTrue(analysis.manipulation.isNotEmpty())
        assertTrue(analysis.score > 0.0)
    }

    @Test
    fun cleanTextProducesNoSignals() {
        val analysis = BehavioralBrain.analyze(listOf(doc("The invoice was received and reconciled on schedule.")))
        assertTrue(analysis.isEmpty())
    }
}
