package com.verumomnis.forensic.model

import kotlinx.serialization.Serializable

enum class MediaKind { IMAGE, VIDEO }

/**
 * A sealed photographic / video exhibit (build spec §18 evidence upload). Every
 * exhibit is fingerprinted (SHA-512), GPS-anchored (EXIF location if present,
 * otherwise the device GPS captured at upload), and timestamped — so photographic
 * evidence is tied to a place and time for law-enforcement use.
 */
@Serializable
data class MediaExhibit(
    val exhibitId: String,
    val evidenceId: String,
    val fileName: String,
    val kind: MediaKind,
    val mimeType: String,
    val sha512: String,
    val capturedAt: String,            // upload timestamp (chain of custody)
    val exifTimestamp: String? = null, // original capture time from EXIF, if available
    val gps: GpsRecord? = null,        // EXIF GPS if present, else device GPS at upload
    val gpsSource: String = "device",  // "exif" or "device"
    val jurisdiction: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null
)
