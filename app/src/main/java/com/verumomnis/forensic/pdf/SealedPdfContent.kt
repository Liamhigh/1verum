package com.verumomnis.forensic.pdf

import com.verumomnis.forensic.model.ForensicReport
import com.verumomnis.forensic.model.SealedEmail
import com.verumomnis.forensic.seal.SealMetadataCodec
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

/** Paginated content for a sealed PDF. */
data class SealedPdfContent(
    val title: String,
    val classification: String,
    val sealFooter: String,
    val shortcode: String,
    val bodyLines: List<String>,
    val cover: Cover? = null,
    val exhibits: List<ExhibitPage> = emptyList(),
    /** Full SHA-512 seal hash used for per-page footer and QR generation. */
    val sealHash: String = "",
    /** Constitution version printed in every footer. */
    val constitutionVersion: String = "",
    /** UTC timestamp printed in every footer. */
    val createdAt: String = "",
    /** Website-compatible seal metadata encoded into the cover QR code. */
    val sealMetadata: SealMetadataCodec.SealMetadata? = null
) {
    /** A sealed photographic/video exhibit page. */
    data class ExhibitPage(
        val exhibitId: String,
        val fileName: String,
        val kind: String,
        val caption: List<String>
    )

    /** Front-cover metadata for the branded blue cover page. */
    data class Cover(
        val title: String,
        val subtitle: String,
        val entity: String,
        val reference: String,
        val date: String,
        val jurisdiction: String,
        val classification: String,
        val summary: List<String>
    )

    data class Page(
        val title: String,
        val classification: String,
        val lines: List<String>,
        val sealFooter: String,
        val pageLabel: String
    )

    fun paginate(linesPerPage: Int = 46): List<Page> {
        val chunks = if (bodyLines.isEmpty()) listOf(emptyList()) else bodyLines.chunked(linesPerPage)
        val total = chunks.size
        return chunks.mapIndexed { index, lines ->
            val pageNum = index + 1
            val pageHash = pageSha512(sealHash, pageNum, total)
            val truncated = if (pageHash.length >= 16) pageHash.take(8) + "…" + pageHash.takeLast(8) else pageHash
            val footer = buildString {
                append("VERUM OMNIS · seal-$shortcode · $truncated · ")
                append("Constitution v$constitutionVersion · ")
                append("$createdAt · Page $pageNum of $total")
            }
            Page(
                title = title,
                classification = classification,
                lines = lines,
                sealFooter = footer,
                pageLabel = "Page $pageNum of $total | $shortcode"
            )
        }
    }

    /** Deterministic per-page integrity hash: SHA-512 over the seal + page number + total pages. */
    private fun pageSha512(sealHash: String, page: Int, total: Int): String {
        if (sealHash.isBlank()) return "0".repeat(128)
        val payload = "$sealHash|$page|$total"
        return com.verumomnis.forensic.crypto.Sha512.hash(payload.toByteArray())
    }

    companion object {
        private const val WRAP = 88

        private fun deviceString(): String {
            val cores = Runtime.getRuntime().availableProcessors()
            return "Android|$cores|${TimeZone.getDefault().id}"
        }

        private fun buildSealMetadata(createdAt: String, gps: String? = null, device: String? = null): SealMetadataCodec.SealMetadata {
            val ts = runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
            return SealMetadataCodec.collect(
                SealMetadataCodec.SealMetadataInput(
                    timestampMs = ts,
                    sealType = "private",
                    gpsLat = gps?.substringBefore(","),
                    gpsLng = gps?.substringAfter(",", "")?.takeIf { it.isNotEmpty() },
                    device = device
                )
            )
        }

        private fun wrap(text: String): List<String> {
            if (text.length <= WRAP) return listOf(text)
            val out = mutableListOf<String>()
            var remaining = text
            while (remaining.length > WRAP) {
                var cut = remaining.lastIndexOf(' ', WRAP)
                if (cut <= 0) cut = WRAP
                out += remaining.substring(0, cut)
                remaining = remaining.substring(cut).trimStart()
            }
            if (remaining.isNotEmpty()) out += remaining
            return out
        }

        fun fromReport(report: ForensicReport): SealedPdfContent {
            val lines = report.body.lines().flatMap { wrap(it) }
            val cover = Cover(
                title = "FORENSIC EVIDENCE REPORT",
                subtitle = report.title,
                entity = "Contradictions: ${report.contradictions.size} · Jurisdiction: ${report.jurisdiction}",
                reference = report.reference,
                date = report.createdAt.take(10),
                jurisdiction = report.jurisdiction,
                classification = report.classification,
                summary = report.executiveSummary.let { wrap(it) }.take(6)
            )
            val exhibits = report.mediaExhibits.map { ex ->
                ExhibitPage(
                    exhibitId = ex.exhibitId,
                    fileName = ex.fileName,
                    kind = ex.kind.name,
                    caption = listOf(
                        "${ex.exhibitId} — ${ex.fileName} (${ex.mimeType})",
                        "SHA-512: ${ex.sha512.take(48)}…",
                        "GPS: " + (ex.gps?.let { "%.6f, %.6f (${ex.gpsSource})".format(it.latitude, it.longitude) } ?: "NOT RECORDED"),
                        "Captured: ${ex.capturedAt}" + (ex.exifTimestamp?.let { " · EXIF ${it}" } ?: ""),
                        "Jurisdiction: ${ex.jurisdiction}"
                    )
                )
            }
            val gps = report.jurisdictionSource?.gps?.let {
                String.format(Locale.US, "%.6f,%.6f", it.latitude, it.longitude)
            }
            return SealedPdfContent(
                title = report.title,
                classification = report.classification,
                sealFooter = report.seal.sealFooter(),
                shortcode = report.seal.shortcode,
                bodyLines = lines,
                cover = cover,
                exhibits = exhibits,
                sealHash = report.seal.sha512,
                constitutionVersion = report.seal.constitutionVersion,
                createdAt = report.seal.createdAt,
                sealMetadata = buildSealMetadata(report.seal.createdAt, gps, deviceString())
            )
        }

        fun fromEmail(email: SealedEmail): SealedPdfContent {
            val lines = buildList {
                add("To: ${email.draft.recipient}")
                add("Subject: ${email.draft.subject}")
                add("Sent: ${email.sentAt}")
                add("Delivery: ${if (email.delivered) "DELIVERED" else "HELD"} (${email.assessment.verdict})")
                add("")
                addAll(email.draft.body.lines())
            }.flatMap { wrap(it) }
            return SealedPdfContent(
                title = "Sealed Communication — ${email.draft.subject}",
                classification = "CONFIDENTIAL — DISTRIBUTION RECORD",
                sealFooter = email.seal.sealFooter(),
                shortcode = email.seal.shortcode,
                bodyLines = lines,
                sealHash = email.seal.sha512,
                constitutionVersion = email.seal.constitutionVersion,
                createdAt = email.seal.createdAt,
                sealMetadata = buildSealMetadata(email.sentAt, device = deviceString())
            )
        }
    }
}
