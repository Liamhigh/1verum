package com.verumomnis.forensic.engine

import com.verumomnis.forensic.engine.MediaEvidence
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * B2 — Document Forensics Brain (build spec Section 5 / 9).
 *
 * Detects document-integrity tamper signals from metadata hints and content
 * heuristics. Deterministic and fully unit-testable off-device.
 */
object DocumentForensicsBrain {

    private val FINANCIAL_KINDS = setOf("bank", "statement", "invoice", "ledger", "payment", "receipt", "financial")
    private val EDITING_TOOLS = listOf("photoshop", "gimp", "paint", "canva", "pixelmator")
    private val BLANK_SIGNATURE_MARKERS = listOf("blank signature", "unsigned", "not signed", "missing signature", "countersignature pending")

    data class ForensicsResult(
        val signals: List<String>,
        val tamperScore: Double
    )

    fun analyze(documents: List<EvidenceDocument>, media: List<MediaEvidence> = emptyList()): ForensicsResult {
        val signals = mutableListOf<String>()

        documents.forEach { doc ->
            val kind = (doc.documentKind ?: doc.type).lowercase()
            val creator = doc.creatorTool?.lowercase() ?: ""

            // Creator-tool mismatch for financial/contract documents.
            if (creator.isNotBlank() && isFinancial(kind)) {
                EDITING_TOOLS.forEach { tool ->
                    if (creator.contains(tool)) {
                        signals += "CREATOR_TOOL_MISMATCH: ${doc.fileName} ($kind) created with '$tool'"
                    }
                }
            }

            // Generic creator-tool oddity for any document.
            if (creator.isNotBlank() && !isExpectedDocumentTool(creator)) {
                signals += "CREATOR_TOOL_ANOMALY: ${doc.fileName} produced by '$creator'"
            }

            // Blank / missing signature detection.
            if (hasBlankSignatureMarker(doc.text)) {
                signals += "BLANK_SIGNATURE: ${doc.fileName} contains unsigned/missing signature language"
            }

            // Backdating heuristic: file name date differs from an explicit document date.
            documentDates(doc.text).firstOrNull()?.let { docDate ->
                filenameDate(doc.fileName)?.let { fileDate ->
                    if (fileDate != docDate) {
                        signals += "BACKDATING_SUSPECTED: ${doc.fileName} date ($fileDate) does not match internal date ($docDate)"
                    }
                }
            }

            // Common image-in-PDF / screenshot marker.
            if (doc.text.contains("image", ignoreCase = true) && kind.contains("pdf")) {
                signals += "IMAGE_EMBEDDED_IN_PDF: ${doc.fileName} may be a scanned image PDF (OCR not verified)"
            }
        }

        // EXIF/GPS consistency for media exhibits.
        media.forEach { m ->
            val exif = m.exifGps
            val device = m.deviceGps
            if (exif != null && device != null) {
                val km = haversine(exif.latitude, exif.longitude, device.latitude, device.longitude)
                if (km > 1.0) {
                    signals += "EXIF_GPS_MISMATCH: ${m.fileName} EXIF GPS differs from device GPS by ${"%.2f".format(km)} km"
                }
            }
            val exifTs = m.exifTimestamp
            if (exifTs != null && m.capturedAt.isNotBlank() && !m.capturedAt.startsWith(exifTs.take(10))) {
                signals += "EXIF_TIMESTAMP_MISMATCH: ${m.fileName} EXIF timestamp $exifTs differs from capture time ${m.capturedAt.take(10)}"
            }
        }

        val score = kotlin.math.min(1.0, signals.size * 0.15)
        return ForensicsResult(signals.sorted(), score)
    }

    private fun isFinancial(kind: String): Boolean = FINANCIAL_KINDS.any { kind.contains(it) }

    private fun isExpectedDocumentTool(creator: String): Boolean {
        val expected = listOf("microsoft", "word", "excel", "adobe acrobat", "libreoffice", "openoffice", "wps", "pdf")
        return expected.any { creator.contains(it) }
    }

    private fun hasBlankSignatureMarker(text: String): Boolean {
        val lower = text.lowercase()
        return BLANK_SIGNATURE_MARKERS.any { lower.contains(it) }
    }

    private fun documentDates(text: String): List<String> {
        val regex = Regex("""\b(\d{4}-\d{2}-\d{2})\b""")
        return regex.findAll(text).map { it.groupValues[1] }.toList()
    }

    private fun filenameDate(fileName: String): String? {
        val regex = Regex("""(20\d{2})[-_]?(\d{2})[-_]?(\d{2})""")
        return regex.find(fileName)?.let {
            "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}"
        }
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
