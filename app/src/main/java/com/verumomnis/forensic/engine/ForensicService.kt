package com.verumomnis.forensic.engine

import com.verumomnis.forensic.crypto.EvidenceSealer
import com.verumomnis.forensic.crypto.EvidenceSealer.VerificationResult
import com.verumomnis.forensic.crypto.Sha512
import com.verumomnis.forensic.model.ForensicFindings
import com.verumomnis.forensic.model.GpsRecord
import com.verumomnis.forensic.model.MediaKind
import com.verumomnis.forensic.model.SealRecord
import com.verumomnis.forensic.vault.EvidenceVault
import java.time.Instant

/**
 * Result of a complete forensic scan: findings plus the seal over the evidence
 * set. [g3Registry] holds any candidates Gemma 3 raised during the post-scan
 * vault review so they can later be promoted or rejected; it is null-safe to
 * ignore when no runtime is installed (the registry is then simply empty).
 */
data class ScanResult(
    val findings: ForensicFindings,
    val seal: SealRecord,
    val g3Registry: com.verumomnis.forensic.engine.contradiction.G3CandidateRegistry? = null
)

/**
 * Orchestrates the forensic pipeline (Part IV): ingest documents, run the
 * deterministic 9-Brain analysis, and cryptographically seal the evidence set.
 * Pure Kotlin so it is fully unit-testable off-device.
 */
object ForensicService {

    fun ingest(
        evidenceId: String,
        fileName: String,
        type: String,
        bytes: ByteArray,
        gps: GpsRecord? = null,
        revenue: Double? = null,
        expenses: Double? = null
    ): EvidenceDocument = EvidenceDocument(
        evidenceId = evidenceId,
        fileName = fileName,
        type = type,
        text = String(bytes, Charsets.UTF_8),
        sha512 = Sha512.hash(bytes),
        gps = gps,
        revenue = revenue,
        expenses = expenses
    )

    fun ingestAudio(
        id: String,
        fileName: String,
        bytes: ByteArray,
        gps: GpsRecord? = null,
        transcript: String? = null,
        creationDateMillis: Long? = null,
        modificationDateMillis: Long? = null,
        sampleRates: List<Int> = emptyList(),
        silenceGapsSec: List<Double> = emptyList()
    ): AudioEvidence = AudioEvidence(
        id = id, fileName = fileName, sha512 = Sha512.hash(bytes), gps = gps, transcript = transcript,
        creationDateMillis = creationDateMillis, modificationDateMillis = modificationDateMillis,
        sampleRates = sampleRates, silenceGapsSec = silenceGapsSec
    )

    fun ingestMedia(
        id: String,
        fileName: String,
        kind: MediaKind,
        bytes: ByteArray,
        mimeType: String,
        capturedAt: String,
        deviceGps: GpsRecord? = null,
        exifGps: GpsRecord? = null,
        exifTimestamp: String? = null,
        width: Int? = null,
        height: Int? = null,
        durationMs: Long? = null
    ): MediaEvidence = MediaEvidence(
        id = id, fileName = fileName, kind = kind, sha512 = Sha512.hash(bytes), mimeType = mimeType,
        capturedAt = capturedAt, deviceGps = deviceGps, exifGps = exifGps, exifTimestamp = exifTimestamp,
        width = width, height = height, durationMs = durationMs
    )

    /** Backward-compatible overload (documents only). */
    fun scan(documents: List<EvidenceDocument>, now: Instant): ScanResult =
        scan(documents, emptyList(), emptyList(), now)

    /** Backward-compatible overload (documents + audio). */
    fun scan(documents: List<EvidenceDocument>, audio: List<AudioEvidence>, now: Instant): ScanResult =
        scan(documents, audio, emptyList(), now)

    /**
     * Run the full forensic pipeline. When [vault] and [caseName] are supplied,
     * the engine also emits a findings.json artefact into the vault under the
     * findings directory, following the G3 Hybrid Report Pipeline contract.
     */
    fun scan(
        documents: List<EvidenceDocument>,
        audio: List<AudioEvidence> = emptyList(),
        media: List<MediaEvidence> = emptyList(),
        now: Instant = Instant.now(),
        vault: EvidenceVault? = null,
        caseName: String = ""
    ): ScanResult {
        val findings = NineBrainEngine.analyze(documents, audio, media, now)
        val councilFindings = BrainCouncil.evaluate(findings)
        // Seal the deterministic fingerprint of the entire evidence set (docs + audio + media).
        val corpusFingerprint = (documents.map { it.sha512 } + audio.map { it.sha512 } + media.map { it.sha512 })
            .joinToString("|")
        val corpusHash = Sha512.hash(corpusFingerprint)
        val reference = "VO-AF-${now.toString().take(10).replace("-", "")}-FOR"
        val seal = EvidenceSealer.sealFromHash(
            sha512 = corpusHash,
            documentType = "forensic_report",
            documentReference = reference,
            nowInstant = now
        )
        // G3 second pass: Gemma 3 reviews the sealed evidence for contradictions
        // the deterministic engine missed. No-op when no runtime is installed.
        val g3Review = G3ReviewPass.review(documents, councilFindings, now)
        vault?.takeIf { caseName.isNotBlank() }?.let {
            val fileName = FindingsJsonEmitter.findingsFileName(caseName, now)
            val findingsJson = FindingsJsonEmitter.emit(
                councilFindings, caseName, now,
                corpusSha512 = corpusHash,
                extraRecords = g3Review.candidateRecords
            )
            it.storeFinding(fileName, findingsJson)
            val auditTrail = g3Review.registry.auditTrail()
            if (auditTrail.isNotEmpty()) {
                val auditJson = buildString {
                    appendLine("[")
                    auditTrail.forEachIndexed { i, entry ->
                        append("  {\"action\":\"${entry.action}\",\"candidate_id\":\"${entry.candidateId}\",")
                        append("\"detail\":${jsonString(entry.detail)},\"utc\":\"${entry.utc}\"}")
                        appendLine(if (i < auditTrail.size - 1) "," else "")
                    }
                    append("]")
                }
                it.storeFinding("g3_audit_${FindingsJsonEmitter.findingsFileName(caseName, now).removeSuffix(".json")}.json", auditJson)
            }
        }
        return ScanResult(councilFindings, seal, g3Review.registry)
    }

    private fun jsonString(s: String): String = buildString {
        append('"')
        for (ch in s) {
            when {
                ch == '\\' -> append("\\\\")
                ch == '"' -> append("\\\"")
                ch == '\n' -> append("\\n")
                ch == '\r' -> append("\\r")
                ch == '\t' -> append("\\t")
                ch < ' ' -> append("\\u%04x".format(ch.code))
                else -> append(ch)
            }
        }
        append('"')
    }

    fun verify(bytes: ByteArray, seal: SealRecord): VerificationResult =
        EvidenceSealer.verify(bytes, seal)
}
