package com.verumomnis.forensic

import com.verumomnis.forensic.core.ConstitutionalPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstitutionalPromptTest {

    @Test
    fun coreDirectiveIsTenWordsOrFewer() {
        val words = ConstitutionalPrompt.coreDirective().trim().split(Regex("\\s+"))
        assertTrue("Core directive must be at most 10 words, was ${words.size}", words.size <= 10)
    }

    @Test
    fun coreDirectiveContainsKeyConcepts() {
        val directive = ConstitutionalPrompt.coreDirective()
        assertTrue(directive.contains("Truth", ignoreCase = true))
        assertTrue(directive.contains("evidence", ignoreCase = true))
    }

    @Test
    fun preambleMentionsUnrestrictedCommunication() {
        val preamble = ConstitutionalPrompt.preamble("Gemma 4", "communicator")
        assertTrue(preamble.contains("UNRESTRICTED"))
        assertTrue(preamble.contains("Constitution"))
        assertTrue(preamble.contains("Anti-War Doctrine"))
    }

    @Test
    fun reportWriterPromptNamesGemma3() {
        val prompt = ConstitutionalPrompt.reportWriter()
        assertTrue(prompt.contains("Gemma 3"))
        assertTrue(prompt.contains("forensic report writer"))
    }

    @Test
    fun communicatorPromptsDifferByTier() {
        val standard = ConstitutionalPrompt.communicatorStandard()
        val flagship = ConstitutionalPrompt.communicatorFlagship()
        assertTrue(standard.contains("Phi-3"))
        assertTrue(flagship.contains("Gemma 4"))
        assertTrue(flagship.contains("flagship"))
    }
}
