package com.verumomnis.forensic

import com.verumomnis.forensic.engine.AudioBrain
import com.verumomnis.forensic.engine.ForensicService
import com.verumomnis.forensic.model.GpsRecord
import com.verumomnis.forensic.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AudioBrainTest {

    private val now = Instant.parse("2026-07-06T14:33:00Z")
    private val gps = GpsRecord(-30.7667, 30.4000, timestamp = now.toString())

    private val transcript = """
        [00:12] Speaker A: I'm mentally broken. I can't take this anymore.
        [00:18] Speaker B: Calm down. It's just business.
        [00:28] Speaker B: The goodwill has no value. You know that.
        [00:33] Speaker A: Then why did you make me sign the forfeiture clause? Why did I pay R3.8 million?
    """.trimIndent()

    private fun audio() = ForensicService.ingestAudio(
        id = "AUD001", fileName = "voice_note.m4a", bytes = transcript.toByteArray(), gps = gps,
        transcript = transcript,
        creationDateMillis = 1_000_000L, modificationDateMillis = 1_500_000L,
        sampleRates = listOf(44_100, 48_000), silenceGapsSec = listOf(0.2, 1.4)
    )

    @Test
    fun detectsTamperSignals() {
        val a = AudioBrain.analyze(listOf(audio()))
        val types = a.tamperSignals.map { it.type }.toSet()
        assertTrue(types.contains("SAMPLE_RATE_INCONSISTENCY"))
        assertTrue(types.contains("METADATA_MODIFICATION"))
        assertTrue(types.contains("UNNATURAL_SILENCE_GAP"))
        assertTrue(a.tamperSignals.any { it.severity == Severity.CRITICAL })
    }

    @Test
    fun transcribesWithSpeakerDiarization() {
        val a = AudioBrain.analyze(listOf(audio()))
        assertTrue(a.transcriptionAvailable)
        assertEquals(2, a.speakerCount)
        assertTrue(a.segments.any { it.speaker == "Speaker A" && it.text.contains("mentally broken") })
        assertTrue(a.fullTranscript.contains("Speaker B"))
    }

    @Test
    fun detectsVoiceStressAndDismissiveMarkers() {
        val a = AudioBrain.analyze(listOf(audio()))
        assertTrue(a.voiceStress.any { it.description.contains("mentally broken") })
        assertTrue(a.voiceStress.any { it.description.contains("it's just business", ignoreCase = true) })
    }

    @Test
    fun transcriptFeedsContradictionEngine() {
        // Audio transcript ("goodwill has no value" vs "pay R3.8 million") must yield a contradiction.
        val result = ForensicService.scan(emptyList(), listOf(audio()), now)
        assertTrue("Expected audio-derived contradiction", result.findings.contradictions.isNotEmpty())
        assertTrue(result.findings.audio != null)
        assertTrue(
            "Expected a GOODWILL_VALUE contradiction from the transcript",
            result.findings.contradictions.any { it.category == com.verumomnis.forensic.model.ContradictionCategory.GOODWILL_VALUE }
        )
    }
}
