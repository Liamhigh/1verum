package com.verumomnis.forensic.pdf

import java.io.File
import java.text.Normalizer

/** Readable, filesystem-safe filenames for sealed originals and their reports. */
object SealedOutputNames {
    private const val MAX_SOURCE_STEM = 96

    fun sourceStem(sourceName: String?): String {
        val leaf = sourceName.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
        val normalized = Normalizer.normalize(leaf, Normalizer.Form.NFC)
        val withoutPdfExtensions = normalized.replace(Regex("(?i)(?:\\.pdf)+$"), "")
        val safe = withoutPdfExtensions
            .replace(Regex("[\\p{Cc}/\\\\:*?\"<>|]+"), "_")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("_+"), "_")
            .trim(' ', '.', '_')
            .takeCodePoints(MAX_SOURCE_STEM)
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
        require(requestedName.isNotBlank() && File(requestedName).name == requestedName) {
            "Export filename must be a single file name."
        }
        directory.mkdirs()
        val base = requestedName.substringBeforeLast('.', requestedName)
        val extension = requestedName.substringAfterLast('.', "").takeIf { it.isNotEmpty() }
            ?.let { ".$it" }.orEmpty()
        var suffix = 1
        while (true) {
            val numbered = if (suffix == 1) "" else "_$suffix"
            val candidate = File(directory, "$base$numbered$extension")
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
        .take(80)

    private fun String.takeCodePoints(maximum: Int): String {
        val points = codePoints().limit(maximum.toLong()).toArray()
        return String(points, 0, points.size)
    }

    private val WINDOWS_RESERVED_NAME =
        Regex("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$")
}
