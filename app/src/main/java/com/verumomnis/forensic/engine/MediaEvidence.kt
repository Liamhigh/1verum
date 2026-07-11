package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.GpsRecord
import com.verumomnis.forensic.model.MediaKind

/**
 * Input to the forensic engine for photographic / video evidence. The original
 * bytes are hashed and preserved in the vault unaltered (chain of custody); the
 * sealed, watermarked exhibit is a derivative rendered for the report.
 */
data class MediaEvidence(
    val id: String,
    val fileName: String,
    val kind: MediaKind,
    val sha512: String,
    val mimeType: String,
    val capturedAt: String,
    val deviceGps: GpsRecord? = null,
    val exifGps: GpsRecord? = null,
    val exifTimestamp: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null
)
