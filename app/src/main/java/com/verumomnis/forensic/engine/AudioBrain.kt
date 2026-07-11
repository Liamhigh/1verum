package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.AudioAnalysis
import com.verumomnis.forensic.model.AudioTamperSignal
import com.verumomnis.forensic.model.Confidence
import com.verumomnis.forensic.model.Severity
import com.verumomnis.forensic.model.SpeakerSegment
import com.verumomnis.forensic.model.VoiceStressFinding
import java.util.Locale

/**
 * B8 Audio Brain (build spec Section 16 + appendix §2). Detects audio tampering
 * (splice / metadata / silence-gap signals), transcribes with speaker diarization
 * via a pluggable [Transcriber], and flags voice-stress markers. The diarised
 * transcript is also exposed so the contradiction engine can cross-reference what
 * was said against the documentary record.
 */
object AudioBrain {

    private val STRESS_MARKERS = listOf(
        "mentally broken", "can't take", "cannot take", "i can't take this",
        "desperate", "broken", "destroyed", "devastated", "help me", "please stop"
    )
    private val DISMISSIVE_MARKERS = listOf(
        "calm down", "it's just business", "you're overreacting", "don't take it personally"
    )

    fun analyze(files: List<AudioEvidence>, transcriber: Transcriber = ProvidedTranscriptTranscriber): AudioAnalysis {
        val tamper = mutableListOf<AudioTamperSignal>()
        val stress = mutableListOf<VoiceStressFinding>()
        val segments = mutableListOf<SpeakerSegment>()
        var transcriptionAvailable = false

        files.forEach { file ->
            tamper += detectTampering(file)

            val fileSegments = transcriber.transcribe(file)
            if (fileSegments.isNotEmpty()) transcriptionAvailable = true
            segments += fileSegments

            fileSegments.forEach { seg ->
                val lower = seg.text.lowercase(Locale.ROOT)
                STRESS_MARKERS.filter { lower.contains(it) }.forEach {
                    stress += VoiceStressFinding(
                        fileId = file.id, speaker = seg.speaker, level = Confidence.VERY_HIGH,
                        description = "Extreme distress indicator: \"$it\"", timestamp = "${seg.startTime}s"
                    )
                }
                DISMISSIVE_MARKERS.filter { lower.contains(it) }.forEach {
                    stress += VoiceStressFinding(
                        fileId = file.id, speaker = seg.speaker, level = Confidence.HIGH,
                        description = "Dismissive/manipulative language: \"$it\"", timestamp = "${seg.startTime}s"
                    )
                }
            }
        }

        val speakerCount = segments.map { it.speaker }.distinct().size
        val fullTranscript = segments.joinToString("\n") {
            "[${formatTs(it.startTime)}] ${it.speaker}: ${it.text}"
        }
        return AudioAnalysis(
            filesAnalyzed = files.size,
            transcriptionAvailable = transcriptionAvailable,
            speakerCount = speakerCount,
            segments = segments,
            tamperSignals = tamper,
            voiceStress = stress,
            fullTranscript = fullTranscript
        )
    }

    private fun detectTampering(file: AudioEvidence): List<AudioTamperSignal> {
        val signals = mutableListOf<AudioTamperSignal>()
        if (file.sampleRates.distinct().size > 1) {
            signals += AudioTamperSignal(
                file.id, "SAMPLE_RATE_INCONSISTENCY", Severity.CRITICAL,
                "Sample rate changes mid-recording — indicates splicing (${file.sampleRates.distinct()})"
            )
        }
        if (file.creationDateMillis != null && file.modificationDateMillis != null &&
            file.modificationDateMillis != file.creationDateMillis
        ) {
            signals += AudioTamperSignal(
                file.id, "METADATA_MODIFICATION", Severity.HIGH,
                "Audio file metadata modified after creation"
            )
        }
        file.silenceGapsSec.filter { it > 0.5 }.forEach { gap ->
            signals += AudioTamperSignal(
                file.id, "UNNATURAL_SILENCE_GAP", Severity.MODERATE,
                "Unnatural silence of ${gap}s — possible edit point"
            )
        }
        return signals
    }

    private fun formatTs(seconds: Double): String {
        val s = seconds.toInt()
        return "%02d:%02d".format(s / 60, s % 60)
    }
}
