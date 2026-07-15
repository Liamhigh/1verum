package com.verumomnis.forensic.engine

import com.verumomnis.forensic.crypto.EvidenceSealer
import com.verumomnis.forensic.crypto.Sha512
import com.verumomnis.forensic.crypto.VerificationResult
import com.verumomnis.forensic.model.ForensicFindings
import com.verumomnis.forensic.model.GpsRecord
import com.verumomnis.forensic.model.MediaKind
import com.verumomnis.forensic.model.SealRecord
import com.verumomnis.forensic.vault.EvidenceVault
import java.time.Instant

/** Result of a complete forensic scan: findings plus the seal over the evidence set. */
data class ScanResult(
    val findings: ForensicFindings,
    val seal: SealRecord
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
        vault?.takeIf { caseName.isNotBlank() }?.let {
            val fileName = FindingsJsonEmitter.findingsFileName(caseName, now)
            val findingsJson = FindingsJsonEmitter.emit(councilFindings, caseName, now)
            it.storeFinding(fileName, findingsJson)
        }
        return ScanResult(councilFindings, seal)
    }

    fun verify(bytes: ByteArray, seal: SealRecord): VerificationResult =
        EvidenceSealer.verify(bytes, seal)
}
