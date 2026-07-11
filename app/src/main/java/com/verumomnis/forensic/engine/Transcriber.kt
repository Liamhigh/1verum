package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.Confidence
import com.verumomnis.forensic.model.SpeakerSegment

/**
 * Speech-to-text + speaker diarization abstraction (build spec appendix §2). A
 * real on-device Whisper.cpp model implements [transcribe]; when no model is
 * bundled the pipeline stays honest and returns no segments (INSUFFICIENT).
 */
interface Transcriber {
    fun transcribe(audio: AudioEvidence): List<SpeakerSegment>
}

/** No on-device model available — returns nothing (INSUFFICIENT), never fabricates. */
object NoModelTranscriber : Transcriber {
    override fun transcribe(audio: AudioEvidence): List<SpeakerSegment> = emptyList()
}

/**
 * Uses an imported/provided diarised transcript in the standard
 * `[mm:ss] Speaker X: text` form. This is what feeds the contradiction engine.
 */
object ProvidedTranscriptTranscriber : Transcriber {

    private val LINE = Regex("""^\[(\d{1,2}):(\d{2})]\s*([^:]+):\s*(.+)$""")

    override fun transcribe(audio: AudioEvidence): List<SpeakerSegment> {
        val transcript = audio.transcript?.takeIf { it.isNotBlank() } ?: return emptyList()
        val lines = transcript.lines().mapNotNull { LINE.find(it.trim()) }
        return lines.mapIndexed { index, m ->
            val start = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt().toDouble()
            val nextStart = lines.getOrNull(index + 1)?.let {
                it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt().toDouble()
            } ?: (start + 5.0)
            SpeakerSegment(
                index = index,
                speaker = m.groupValues[3].trim(),
                text = m.groupValues[4].trim(),
                startTime = start,
                endTime = nextStart,
                confidence = Confidence.HIGH
            )
        }
    }
}
