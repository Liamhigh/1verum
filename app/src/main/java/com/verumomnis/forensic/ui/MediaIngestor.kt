package com.verumomnis.forensic.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.verumomnis.forensic.engine.ForensicService
import com.verumomnis.forensic.engine.MediaEvidence
import com.verumomnis.forensic.model.GpsRecord
import com.verumomnis.forensic.model.MediaKind
import com.verumomnis.forensic.vault.EvidenceVault
import java.io.ByteArrayInputStream
import java.time.Instant

/**
 * Reads a picked photo/video: preserves the original bytes in the vault, computes
 * the SHA-512, and captures GPS (EXIF location if present, else the device GPS at
 * upload) plus the timestamp — so photographic evidence is anchored to place/time.
 */
class MediaIngestor(private val context: Context) {

    private val vault = EvidenceVault(context)

    fun ingest(uri: Uri, deviceGps: GpsRecord?, index: Int, now: Instant = Instant.now()): MediaEvidence {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        val fileName = displayName(uri) ?: "evidence_${now.toEpochMilli()}"
        val kind = if (mime.startsWith("video")) MediaKind.VIDEO else MediaKind.IMAGE

        // Preserve the ORIGINAL unaltered in the vault (chain of custody).
        vault.storeEvidence(fileName, bytes)

        var exifGps: GpsRecord? = null
        var exifTimestamp: String? = null
        var width: Int? = null
        var height: Int? = null
        var durationMs: Long? = null

        if (kind == MediaKind.IMAGE && bytes.isNotEmpty()) {
            runCatching {
                val exif = ExifInterface(ByteArrayInputStream(bytes))
                exif.latLong?.let { exifGps = GpsRecord(it[0], it[1], timestamp = now.toString()) }
                exifTimestamp = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            }
            runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                width = opts.outWidth.takeIf { it > 0 }
                height = opts.outHeight.takeIf { it > 0 }
            }
        } else if (kind == MediaKind.VIDEO) {
            runCatching {
                MediaMetadataRetriever().use { r ->
                    resolver.openFileDescriptor(uri, "r")?.use { pfd -> r.setDataSource(pfd.fileDescriptor) }
                    durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    width = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    height = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                }
            }
        }

        return ForensicService.ingestMedia(
            id = "MED%03d".format(index),
            fileName = fileName,
            kind = kind,
            bytes = bytes,
            mimeType = mime,
            capturedAt = now.toString(),
            deviceGps = deviceGps,
            exifGps = exifGps,
            exifTimestamp = exifTimestamp,
            width = width,
            height = height,
            durationMs = durationMs
        )
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
