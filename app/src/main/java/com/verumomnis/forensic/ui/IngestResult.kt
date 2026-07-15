package com.verumomnis.forensic.ui

import com.verumomnis.forensic.engine.MediaEvidence

/**
 * Result of picking a file through [MediaIngestor].
 *
 * Success carries a previewable document or media item. Errors are surfaced to
 * the user before any evidence is added to the sealed case.
 */
sealed class IngestResult {

    data class DocumentSuccess(
        val fileName: String,
        val mimeType: String,
        val text: String,
        val sha512: String,
        val sizeBytes: Long
    ) : IngestResult()

    data class MediaSuccess(
        val evidence: MediaEvidence,
        val sizeBytes: Long
    ) : IngestResult()

    sealed class Error(val message: String) : IngestResult() {
        class TooLarge(maxBytes: Long) : Error(
            "File exceeds ${maxBytes / (1024 * 1024)} MB limit. Choose a smaller document."
        )

        class MalwareDetected : Error(
            "Malware test pattern detected. File blocked and not stored."
        )

        class ReadFailed(cause: String) : Error(
            "Could not read selected file: $cause"
        )
    }
}
