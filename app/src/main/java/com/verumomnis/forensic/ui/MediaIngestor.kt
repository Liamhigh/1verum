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
import com.verumomnis.forensic.engine.PdfOcrExtractor
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
    // Default extractor OCRs image-only pages on-device (ML Kit) and falls back
    // to the embedded text layer where present. Tests can inject a fake.
    private val pdfExtractor: PdfTextExtractor = PdfOcrExtractor(context)
) {

    companion object {
        /**
         * 150 MB, matching verumglobal.foundation's "Max 150MB total".
         *
         * This was temporarily 50 MB because the old path held the file as a
         * ByteArray, let PDFBox parse a second copy and let OCR spill a third —
         * several multiples of the file size, which overran the heap. Documents
         * now stream to the vault and are read from disk, so the peak is roughly
         * one 64 KB buffer regardless of size and the site's limit is safe.
         */
        const val MAX_FILE_SIZE_BYTES = 150L * 1024 * 1024

        /**
         * Head bytes read for the EICAR probe. The marker is 68 bytes; 4 KB gives
         * ample margin without pulling a large artifact into memory.
         */
        private const val EICAR_PROBE_BYTES = 4096
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

        // Stream the ORIGINAL into the vault, hashing in the same pass. A case
        // bundle can run to 150 MB; reading it into a ByteArray first is what put
        // this path within reach of an OutOfMemoryError. Peak cost here is one
        // 64 KB buffer, and the bytes stored are unaltered as custody requires.
        val stream = resolver.openInputStream(uri)
            ?: return IngestResult.Error.ReadFailed("could not open input stream")
        val (stored, hash) = runCatching { vault.storeEvidenceStreaming(fileName, stream) }
            .getOrElse { return IngestResult.Error.ReadFailed(it.message ?: "could not store evidence") }

        if (stored.length() > MAX_FILE_SIZE_BYTES) {
            stored.delete()
            return IngestResult.Error.TooLarge(MAX_FILE_SIZE_BYTES)
        }
        // EICAR is a 68-byte marker at the start of the file, so only the head
        // needs reading — the whole artifact never has to be resident.
        if (EicarScanner.isEicar(stored.readHead(EICAR_PROBE_BYTES))) {
            stored.delete()
            return IngestResult.Error.MalwareDetected()
        }

        val text = when {
            mime.startsWith("text") -> stored.readText(Charsets.UTF_8)
            mime.startsWith("application/pdf") -> {
                val extracted = pdfExtractor.extractText(stored).trim()
                if (extracted.isNotBlank()) extracted else "(PDF sealed and vaulted; no extractable text layer found.)"
            }
            else -> "(Binary document sealed and vaulted; on-device text extraction pending for $mime.)"
        }
        return IngestResult.DocumentSuccess(fileName, mime, text, hash, stored.length())
    }

    /** Compute SHA-512 of a picked file for seal verification. Streams; never resident. */
    fun hashOf(uri: Uri): Pair<String, String> {
        val hash = context.contentResolver.openInputStream(uri)?.use { Sha512.hash(it) }
            ?: Sha512.hash(ByteArray(0))
        return (displayName(uri) ?: "document") to hash
    }

    /** Reads at most [max] bytes from the head of a file, for cheap signature probes. */
    private fun java.io.File.readHead(max: Int): ByteArray = inputStream().use { input ->
        val buffer = ByteArray(max)
        val read = input.read(buffer)
        if (read <= 0) ByteArray(0) else buffer.copyOf(read)
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
