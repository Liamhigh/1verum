package com.verumomnis.forensic.pdf

import java.io.File
import java.text.Normalizer

/** Readable, filesystem-safe filenames for sealed originals and their reports. */
object SealedOutputNames {
    private const val MAX_SOURCE_BYTES = 120

    fun sourceStem(sourceName: String?): String {
        val leaf = sourceName.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
        val normalized = Normalizer.normalize(leaf, Normalizer.Form.NFC)
        val withoutPdfExtensions = normalized.trim().replace(Regex("(?i)(?:\\.pdf)+$"), "")
        val safe = withoutPdfExtensions
            .replace(Regex("[\\p{Cc}\\p{Cf}/\\\\:*?\"<>|]+"), "_")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("_+"), "_")
            .trim(' ', '.', '_')
            .takeUtf8Bytes(MAX_SOURCE_BYTES)
            .trimEnd(' ', '.', '_')
        val stem = safe.ifBlank { "document" }
        return if (WINDOWS_RESERVED_NAME.matches(stem)) "_$stem" else stem
    }

    fun sealedDocument(sourceName: String?, sealId: String, timestampMs: Long): String =
        "${sourceStem(sourceName)}_sealed_${safeToken(sealId)}_${timestampMs}.pdf"

    fun timestampProof(sourceName: String?, sealId: String, timestampMs: Long): String =
        "${sourceStem(sourceName)}_sealed_${safeToken(sealId)}_${timestampMs}.ots"

    fun forensicReport(sourceName: String?, reportReference: String): String =
        if (sourceName.isNullOrBlank()) {
            "${safeToken(reportReference)}_forensic_report.pdf"
        } else {
            "${sourceStem(sourceName)}_forensic_report_${safeToken(reportReference)}.pdf"
        }

    /** Create without overwriting an earlier export; add a counter only on collision. */
    fun createUniqueFile(directory: File, requestedName: String): File {
        require(
            requestedName.isNotBlank() && requestedName !in setOf(".", "..") &&
                File(requestedName).name == requestedName && !requestedName.contains('\\')
        ) {
            "Export filename must be a single file name."
        }
        directory.mkdirs()
        var suffix = 1
        while (true) {
            val candidate = File(directory, numbered(requestedName, suffix))
            if (candidate.createNewFile()) return candidate
            suffix++
        }
    }

    fun numbered(requestedName: String, collisionNumber: Int): String {
        require(collisionNumber >= 1)
        if (collisionNumber == 1) return requestedName
        val base = requestedName.substringBeforeLast('.', requestedName)
        val extension = requestedName.substringAfterLast('.', "").takeIf { it.isNotEmpty() }
            ?.let { ".$it" }.orEmpty()
        return "${base}_$collisionNumber$extension"
    }

    private fun safeToken(value: String): String = value
        .replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")
        .trim('.', '_', '-')
        .ifBlank { "seal" }
        .takeUtf8Bytes(60)

    private fun String.takeUtf8Bytes(maximum: Int): String {
        var end = 0
        var bytes = 0
        while (end < length) {
            val point = codePointAt(end)
            val width = when {
                point <= 0x7f -> 1
                point <= 0x7ff -> 2
                point <= 0xffff -> 3
                else -> 4
            }
            if (bytes + width > maximum) break
            bytes += width
            end += Character.charCount(point)
        }
        return substring(0, end)
    }

    private val WINDOWS_RESERVED_NAME =
        Regex("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$")
}
