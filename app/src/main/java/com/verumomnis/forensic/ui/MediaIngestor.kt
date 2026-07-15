package com.verumomnis.forensic.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.verumomnis.forensic.crypto.Sha512
import com.verumomnis.forensic.engine.ForensicService
import com.verumomnis.forensic.engine.MediaEvidence
import com.verumomnis.forensic.engine.PdfBoxTextExtractor
import com.verumomnis.forensic.engine.PdfTextExtractor
import com.verumomnis.forensic.model.GpsRecord
import com.verumomnis.forensic.model.MediaKind
import com.verumomnis.forensic.security.EicarScanner
import com.verumomnis.forensic.vault.EvidenceVault
import java.io.ByteArrayInputStream
import java.time.Instant

/**
 * Reads a picked photo/video/document: validates size, checks for the EICAR test
 * pattern, preserves the original bytes in the vault, computes the SHA-512, and
 * captures GPS/timestamp so evidence is anchored to place/time.
 */
class MediaIngestor(
    private val context: Context,
    private val pdfExtractor: PdfTextExtractor = PdfBoxTextExtractor()
) {

    companion object {
        const val MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024
    }

    private val vault = EvidenceVault(context)

    fun ingest(uri: Uri, deviceGps: GpsRecord?, index: Int, now: Instant = Instant.now()): IngestResult {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val fileName = displayName(uri) ?: "evidence_${now.toEpochMilli()}"

        val size = fileSize(uri)
        if (size != null && size > MAX_FILE_SIZE_BYTES) {
            return IngestResult.Error.TooLarge(MAX_FILE_SIZE_BYTES)
        }

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return IngestResult.Error.ReadFailed("could not open input stream")

        if (bytes.size > MAX_FILE_SIZE_BYTES) {
            return IngestResult.Error.TooLarge(MAX_FILE_SIZE_BYTES)
        }
        if (EicarScanner.isEicar(bytes)) {
            return IngestResult.Error.MalwareDetected()
        }

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

        val evidence = ForensicService.ingestMedia(
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
        return IngestResult.MediaSuccess(evidence, bytes.size.toLong())
    }

    /** Seal a non-media document: validate, preserve original in the vault, return preview info. */
    fun ingestDocument(uri: Uri, now: Instant = Instant.now()): IngestResult {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val fileName = displayName(uri) ?: "document_${now.toEpochMilli()}"

        val size = fileSize(uri)
        if (size != null && size > MAX_FILE_SIZE_BYTES) {
            return IngestResult.Error.TooLarge(MAX_FILE_SIZE_BYTES)
        }

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return IngestResult.Error.ReadFailed("could not open input stream")

        if (bytes.size > MAX_FILE_SIZE_BYTES) {
            return IngestResult.Error.TooLarge(MAX_FILE_SIZE_BYTES)
        }
        if (EicarScanner.isEicar(bytes)) {
            return IngestResult.Error.MalwareDetected()
        }

        // Preserve the ORIGINAL unaltered in the vault (chain of custody).
        vault.storeEvidence(fileName, bytes)

        val text = when {
            mime.startsWith("text") -> String(bytes, Charsets.UTF_8)
            mime.startsWith("application/pdf") -> {
                val extracted = pdfExtractor.extractText(bytes).trim()
                if (extracted.isNotBlank()) extracted else "(PDF sealed and vaulted; no extractable text layer found.)"
            }
            else -> "(Binary document sealed and vaulted; on-device text extraction pending for $mime.)"
        }
        val hash = Sha512.hash(bytes)
        return IngestResult.DocumentSuccess(fileName, mime, text, hash, bytes.size.toLong())
    }

    /** Compute SHA-512 of a picked file for seal verification. */
    fun hashOf(uri: Uri): Pair<String, String> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        return (displayName(uri) ?: "document") to Sha512.hash(bytes)
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun fileSize(uri: Uri): Long? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        }
    }
}
