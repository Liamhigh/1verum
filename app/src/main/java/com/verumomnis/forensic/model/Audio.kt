package com.verumomnis.forensic.model

import kotlinx.serialization.Serializable

/** A tamper signal from B8 audio forensics. */
@Serializable
data class AudioTamperSignal(
    val fileId: String,
    val type: String,
    val severity: Severity,
    val description: String
)

/** A voice-stress finding from B8. */
@Serializable
data class VoiceStressFinding(
    val fileId: String,
    val speaker: String,
    val level: Confidence,
    val description: String,
    val timestamp: String
)

/** One diarised utterance (who said what, when). */
@Serializable
data class SpeakerSegment(
    val index: Int,
    val speaker: String,
    val text: String,
    val startTime: Double,
    val endTime: Double,
    val confidence: Confidence = Confidence.HIGH
)

/** Full B8 audio analysis result. */
@Serializable
data class AudioAnalysis(
    val filesAnalyzed: Int,
    val transcriptionAvailable: Boolean,
    val speakerCount: Int,
    val segments: List<SpeakerSegment>,
    val tamperSignals: List<AudioTamperSignal>,
    val voiceStress: List<VoiceStressFinding>,
    val fullTranscript: String
) {
    fun isEmpty(): Boolean =
        segments.isEmpty() && tamperSignals.isEmpty() && voiceStress.isEmpty()
}
