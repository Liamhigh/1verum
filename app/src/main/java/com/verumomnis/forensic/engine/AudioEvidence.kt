package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.GpsRecord

/**
 * Input to the B8 Audio Brain. A transcript may be supplied (from an on-device
 * Whisper model or an imported transcript); the forensic signals (sample rates,
 * silence gaps, metadata dates) come from the decoded audio container.
 */
data class AudioEvidence(
    val id: String,
    val fileName: String,
    val sha512: String,
    val gps: GpsRecord? = null,
    val transcript: String? = null,
    val creationDateMillis: Long? = null,
    val modificationDateMillis: Long? = null,
    val sampleRates: List<Int> = emptyList(),
    val silenceGapsSec: List<Double> = emptyList()
)
