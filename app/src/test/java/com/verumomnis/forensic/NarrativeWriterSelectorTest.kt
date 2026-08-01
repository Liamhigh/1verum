package com.verumomnis.forensic

import com.verumomnis.forensic.engine.DeterministicReportWriter
import com.verumomnis.forensic.engine.Gemma3ReportWriter
import com.verumomnis.forensic.engine.NarrativeWriterSelector
import com.verumomnis.forensic.engine.llm.Gemma3NativeReportWriter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Writer-selection logic for the hybrid report pipeline.
 *
 * Running Gemma 3 for real needs an 8 GB+ device and a 2.5 GB model download, so
 * it cannot be covered here. What *can* be covered — and matters most — is that
 * the selection degrades safely: when no model is loaded a report must still be
 * produced by a fallback writer, never left blank and never crashing.
 */
class NarrativeWriterSelectorTest {

    @Test
    fun `no loaded model falls back to the deterministic writer`() {
        val writer = NarrativeWriterSelector.select(reportWriterModel = null, hasResearch = false)
        assertSame(DeterministicReportWriter, writer)
    }

    @Test
    fun `no loaded model with research falls back to the Gemma prompt writer`() {
        val writer = NarrativeWriterSelector.select(reportWriterModel = null, hasResearch = true)
        assertSame(Gemma3ReportWriter, writer)
    }

    @Test
    fun `a null model never yields the native writer`() {
        // The regression that matters: a failed download or an OOM-ed load must
        // not leave a Gemma3NativeReportWriter wrapping a null model.
        listOf(true, false).forEach { hasResearch ->
            val writer = NarrativeWriterSelector.select(null, hasResearch)
            assertFalse(
                "native writer selected with no loaded model (hasResearch=$hasResearch)",
                writer is Gemma3NativeReportWriter
            )
            assertFalse(NarrativeWriterSelector.isNativeNarrative(writer))
        }
    }

    @Test
    fun `fallback writers are recognised as non-native narratives`() {
        assertFalse(NarrativeWriterSelector.isNativeNarrative(DeterministicReportWriter))
        assertFalse(NarrativeWriterSelector.isNativeNarrative(Gemma3ReportWriter))
    }

    @Test
    fun `unavailable note discloses the failure honestly`() {
        // Constitution Prime Directive 6: failure-mode disclosure. The note must
        // say the narrative is missing AND that the sealed findings are intact,
        // so absence of prose is never read as absence of findings.
        val note = NarrativeWriterSelector.UNAVAILABLE_NOTE
        assertTrue("must state the narrative is unavailable", note.contains("unavailable", ignoreCase = true))
        assertTrue("must confirm the findings are unaffected", note.contains("unaffected", ignoreCase = true))
        assertTrue("must not surface a model name", !note.contains("Gemma") && !note.contains("Phi"))
    }
}
