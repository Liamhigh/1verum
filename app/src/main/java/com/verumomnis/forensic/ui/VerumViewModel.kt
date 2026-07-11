package com.verumomnis.forensic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verumomnis.forensic.blockchain.OpenTimestampsService
import com.verumomnis.forensic.core.Constitution
import com.verumomnis.forensic.core.DeviceTier
import com.verumomnis.forensic.core.Llm
import com.verumomnis.forensic.core.ModelLoader
import com.verumomnis.forensic.crypto.VerificationResult
import com.verumomnis.forensic.engine.AntiHarassmentMonitor
import com.verumomnis.forensic.engine.AudioEvidence
import com.verumomnis.forensic.engine.EmailModule
import com.verumomnis.forensic.engine.EvidenceDocument
import com.verumomnis.forensic.engine.ForensicService
import com.verumomnis.forensic.engine.MediaEvidence
import com.verumomnis.forensic.engine.ReportGenerator
import com.verumomnis.forensic.engine.ScanResult
import com.verumomnis.forensic.engine.TaxModule
import com.verumomnis.forensic.model.ForensicReport
import com.verumomnis.forensic.model.GpsRecord
import com.verumomnis.forensic.model.HarassmentVerdict
import com.verumomnis.forensic.model.OtsAnchorResult
import com.verumomnis.forensic.model.OtsStatus
import com.verumomnis.forensic.model.SealedEmail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class FileEntry(
    val name: String,
    val type: String,
    val status: String,
    val sha512: String = "",
    val gps: String = ""
)

data class ChatMessage(val author: String, val text: String, val fromUser: Boolean)

data class UiState(
    val deviceRamGb: Int = 6,
    val deviceTier: DeviceTier = DeviceTier.STANDARD,
    val models: List<Llm> = emptyList(),
    val communicator: String = "",
    val reportWriter: String = "",
    val gps: GpsRecord? = null,
    val jurisdiction: String = "ZA-KZN",
    val files: List<FileEntry> = emptyList(),
    val scanning: Boolean = false,
    val scanResult: ScanResult? = null,
    val scanLog: String = "Awaiting evidence. Upload documents to begin a forensic scan.",
    val chat: List<ChatMessage> = emptyList(),
    val report: ForensicReport? = null,
    val emails: List<SealedEmail> = emptyList(),
    val emailStatus: String = "Draft a sealed forensic email. Every draft is delivered as a sealed PDF and logged.",
    val otsResult: OtsAnchorResult? = null,
    val otsStatus: String = "Evidence seal not yet anchored to Bitcoin (OpenTimestamps).",
    val anchoring: Boolean = false
)

class VerumViewModel : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val documents = mutableListOf<EvidenceDocument>()
    private val audios = mutableListOf<AudioEvidence>()
    private val medias = mutableListOf<MediaEvidence>()
    private val harassmentMonitor = AntiHarassmentMonitor()

    fun mediaCount(): Int = medias.size

    init {
        configureDevice(6)
        seedSampleCase()
        _state.update {
            it.copy(
                chat = listOf(
                    ChatMessage(
                        author = "Verum Omnis",
                        text = "Truth for All. I am the Verum Omnis communicator (Constitution v${Constitution.VERSION}).\n\n" +
                            "How this works: anything you add with + goes straight to the forensic engine — it is " +
                            "SHA-512 sealed, GPS-anchored and stored in the vault with its findings JSON before I ever see it. " +
                            "I only read the SEALED case file, then help with the narrative, the timeline and the legal strategy. " +
                            "Nothing leaves here unsealed.",
                        fromUser = false
                    )
                )
            )
        }
    }

    private fun postEngine(text: String) {
        _state.update { it.copy(chat = it.chat + ChatMessage("Forensic Engine", text, fromUser = false)) }
    }

    private fun postAi(text: String) {
        _state.update { it.copy(chat = it.chat + ChatMessage(_state.value.communicator, text, fromUser = false)) }
    }

    /** A document picked from the + menu goes to the engine (not the chat AI). */
    fun ingestDocument(fileName: String, mimeType: String, text: String) {
        val type = when {
            mimeType.contains("pdf") -> "pdf"
            mimeType.startsWith("text") -> "document"
            else -> "document"
        }
        ingestText(fileName, type, text)
    }

    /**
     * Seal everything added so far through the forensic engine and store it in the
     * vault (findings JSON + forensic report). Only now may the AI read the case.
     */
    fun sealCase(now: Instant = Instant.now()) {
        if (documents.isEmpty() && audios.isEmpty() && medias.isEmpty()) {
            postEngine("Nothing to seal yet — add evidence with the + button first.")
            return
        }
        runScan(now)
        generateReport(now = now)
        val r = _state.value.scanResult
        postEngine(
            "Sealed ${_state.value.files.size} item(s) into the vault. " +
                "SHA-512 + GPS recorded, findings JSON written, forensic report generated" +
                (r?.let { " (seal ${it.seal.shortcode})" } ?: "") +
                ". The AI may now read the sealed case file."
        )
    }

    /** Verify an uploaded file's hash against the sealed vault. */
    fun verifyUploaded(fileName: String, sha512: String) {
        val vaulted = documents.map { it.sha512 } + audios.map { it.sha512 } + medias.map { it.sha512 }
        val msg = when {
            vaulted.contains(sha512) -> "VERIFIED · $fileName matches a sealed vault record (SHA-512 ${sha512.take(12)}…)."
            _state.value.scanResult?.seal?.sha512 == sha512 -> "VERIFIED · matches the case seal."
            else -> "NOT FOUND · $fileName (SHA-512 ${sha512.take(12)}…) is not in this sealed vault. If it claims a Verum seal, the content may be TAMPERED."
        }
        postEngine(msg)
    }

    /** Deep research: the AI reads the SEALED case file and drafts narrative help. */
    fun deepResearch(now: Instant = Instant.now()) {
        if (_state.value.scanResult == null) sealCase(now)
        val f = _state.value.scanResult?.findings ?: return
        postAi(
            "Deep research over the sealed case file: ${f.documentsAnalyzed} evidence item(s), " +
                "${f.contradictions.size} contradiction(s) anchored to person + page + statute, " +
                "${f.timeline.size} timeline event(s), jurisdiction ${f.jurisdiction}. " +
                "I can now help draft the narrative and legal strategy, and flag anything the engine may have missed. " +
                "Every claim I make cites sealed evidence; ordinal confidence only."
        )
    }

    /** Tax return: Verum fee is 50% of the local accountant benchmark. */
    fun runTaxReturn(entityType: String, jurisdiction: String, revenue: Double, expenses: Double, income: Double, age: Int) {
        val (label, tax) = if (entityType == "individual") {
            "Individual" to TaxModule.calculateIndividualTaxZA(income, age)
        } else {
            "Company" to TaxModule.calculateCompanyTaxZA(revenue, expenses)
        }
        val accountant = TaxModule.estimateAccountantFee(jurisdiction, "tax_return", "moderate", if (entityType == "individual") "individual" else "corporate")
        val verum = TaxModule.verumServiceFee(jurisdiction, "tax_return", "moderate", if (entityType == "individual") "individual" else "corporate")
        postEngine(
            "$label tax (${tax.jurisdiction}): liability R%,.2f. ".format(tax.taxLiability) +
                "Accountant benchmark in $jurisdiction ≈ R%,.0f; Verum Omnis fee is 50%% = R%,.0f. Sealed to the vault."
                    .format(accountant.estimatedFee, verum.estimatedFee)
        )
    }

    fun configureDevice(ramGb: Int) {
        val models = ModelLoader.loadModels(ramGb)
        _state.update {
            it.copy(
                deviceRamGb = ramGb,
                deviceTier = DeviceTier.forRam(ramGb),
                models = models,
                communicator = ModelLoader.communicator(models).name,
                reportWriter = ModelLoader.reportWriter(models).name
            )
        }
    }

    fun setGps(gps: GpsRecord) {
        _state.update { it.copy(gps = gps) }
    }

    fun addDocument(doc: EvidenceDocument) {
        documents += doc
        val gpsLabel = doc.gps?.let { "%.4f, %.4f".format(it.latitude, it.longitude) } ?: "NOT RECORDED"
        _state.update {
            it.copy(
                files = it.files + FileEntry(
                    name = doc.fileName,
                    type = doc.type,
                    status = "queued",
                    sha512 = doc.sha512,
                    gps = gpsLabel
                ),
                scanLog = "${doc.fileName} ingested · SHA-512 ${doc.sha512.take(12)}… · GPS $gpsLabel"
            )
        }
    }

    fun addMedia(media: MediaEvidence) {
        medias += media
        val gps = media.exifGps ?: media.deviceGps
        val label = gps?.let { "%.4f, %.4f".format(it.latitude, it.longitude) } ?: "NOT RECORDED"
        _state.update {
            it.copy(
                files = it.files + FileEntry(media.fileName, media.kind.name.lowercase(), "queued", media.sha512, label),
                scanLog = "${media.fileName} sealed · SHA-512 ${media.sha512.take(12)}… · GPS $label (${if (media.exifGps != null) "EXIF" else "device"})"
            )
        }
    }

    fun ingestText(fileName: String, type: String, content: String) {
        val doc = ForensicService.ingest(
            evidenceId = "DOC%03d".format(documents.size + 1),
            fileName = fileName,
            type = type,
            bytes = content.toByteArray(),
            gps = _state.value.gps
        )
        addDocument(doc)
    }

    fun runScan(now: Instant = Instant.now()) {
        if (documents.isEmpty() && audios.isEmpty() && medias.isEmpty()) {
            _state.update { it.copy(scanLog = "No evidence to scan. Upload documents first.") }
            return
        }
        _state.update { it.copy(scanning = true, scanLog = "Nine-Brain forensic analysis in progress…") }
        val result = ForensicService.scan(documents, audios, medias, now)
        _state.update { s ->
            s.copy(
                scanning = false,
                scanResult = result,
                jurisdiction = result.findings.jurisdiction,
                files = s.files.map { it.copy(status = "scanned") },
                scanLog = buildString {
                    append("Scan complete · ${result.findings.documentsAnalyzed} documents · ")
                    append("${result.findings.contradictions.size} contradictions · ")
                    append("${result.findings.timeline.size} timeline events · ")
                    append("seal ${result.seal.shortcode}")
                },
                chat = s.chat + ChatMessage(
                    author = "Forensic Scan",
                    text = "Analyzed ${result.findings.documentsAnalyzed} documents. " +
                        "Detected ${result.findings.contradictions.size} contradiction(s) and " +
                        "${result.findings.legalMappings.size} legal mapping(s). " +
                        "Evidence sealed: ${result.seal.sealFooter()}",
                    fromUser = false
                )
            )
        }
    }

    fun generateReport(caseName: String = "AllFuels Matter", now: Instant = Instant.now()) {
        val findings = _state.value.scanResult?.findings ?: run {
            runScan(now)
            _state.value.scanResult?.findings
        } ?: return
        val report = ReportGenerator.generate(findings, caseName, now)
        _state.update {
            it.copy(
                report = report,
                chat = it.chat + ChatMessage(
                    author = "Report Writer (Gemma 3)",
                    text = "Generated sealed report ${report.reference} with ${report.contradictions.size} " +
                        "anchored contradiction(s). Seal: ${report.seal.sealFooter()}",
                    fromUser = false
                )
            )
        }
    }

    fun draftAndSendEmail(
        recipient: String,
        subject: String,
        points: List<String> = emptyList(),
        now: Instant = Instant.now()
    ) {
        if (recipient.isBlank()) {
            _state.update { it.copy(emailStatus = "Recipient required.") }
            return
        }
        val draft = EmailModule.draft(recipient, subject, points, _state.value.report?.reference)
        val sealed = EmailModule.sealAndSend(draft, harassmentMonitor, now)
        val verdict = sealed.assessment.verdict
        val status = when (verdict) {
            HarassmentVerdict.ALLOW -> "Sent sealed PDF ${sealed.sealedPdfFile} to $recipient."
            HarassmentVerdict.WARN -> "Sent with WARNING: ${sealed.assessment.reasons.joinToString("; ")}"
            HarassmentVerdict.BLOCK -> "BLOCKED (not delivered): ${sealed.assessment.reasons.joinToString("; ")}. Sealed for audit."
        }
        _state.update { it.copy(emails = it.emails + sealed, emailStatus = status) }
    }

    fun verifyCurrentSeal(): VerificationResult? {
        val result = _state.value.scanResult ?: return null
        // The seal is computed over the corpus fingerprint; re-supply the same bytes.
        val corpus = (documents.map { it.sha512 } + audios.map { it.sha512 } + medias.map { it.sha512 }).joinToString("|")
        return ForensicService.verify(corpus.toByteArray(), result.seal)
    }

    /** Anchor the current report/evidence seal to Bitcoin via OpenTimestamps (network I/O). */
    fun anchorSealToBitcoin() {
        val seal = _state.value.report?.seal ?: _state.value.scanResult?.seal
        if (seal == null) {
            _state.update { it.copy(otsStatus = "Run a scan (or generate a report) before anchoring.") }
            return
        }
        if (_state.value.anchoring) return
        _state.update { it.copy(anchoring = true, otsStatus = "Submitting SHA-256 digest to OpenTimestamps calendars…") }
        viewModelScope.launch(Dispatchers.IO) {
            val res = runCatching { OpenTimestampsService.anchor(seal.sha512) }.getOrElse {
                OtsAnchorResult(
                    status = OtsStatus.FAILED, sha512 = seal.sha512, sha256Digest = "",
                    calendarUrls = emptyList(), submittedAt = java.time.Instant.now().toString(),
                    message = it.message ?: "Anchoring failed"
                )
            }
            val status = when (res.status) {
                OtsStatus.PENDING -> "PENDING · digest ${res.sha256Digest.take(12)}… submitted to ${res.calendarUrls.size} calendar(s). Proof: ${res.otsProofFile}"
                OtsStatus.OFFLINE -> "OFFLINE · ${res.message}"
                OtsStatus.CONFIRMED -> "CONFIRMED on Bitcoin."
                OtsStatus.FAILED -> "FAILED · ${res.message}"
            }
            _state.update { it.copy(anchoring = false, otsResult = res, otsStatus = status) }
        }
    }

    fun sendChat(text: String) {
        if (text.isBlank()) return
        _state.update { it.copy(chat = it.chat + ChatMessage("You", text, fromUser = true)) }
        val reply = respond(text)
        _state.update { it.copy(chat = it.chat + ChatMessage(_state.value.communicator, reply, fromUser = false)) }
    }

    /**
     * Deterministic, evidence-grounded reply. Ordinal confidence only; every claim
     * points back to sealed evidence (spec 8.1 / 8.4). This stands in for the
     * on-device communicator LLM.
     */
    private fun respond(query: String): String {
        val findings = _state.value.scanResult?.findings
            ?: return "INSUFFICIENT: no forensic scan has been run yet. Upload evidence and start a scan."
        val q = query.lowercase()
        return when {
            "contradict" in q -> if (findings.contradictions.isEmpty()) {
                "No material contradictions detected. Confidence: HIGH."
            } else findings.contradictions.joinToString("\n") { c ->
                "${c.contradictionId} [${c.severity}] ${c.claimA.text} ↔ ${c.claimB.text} " +
                    "(${c.claimA.source} p${c.claimA.page} / ${c.claimB.source} p${c.claimB.page}). " +
                    "Law: ${c.applicableLaw.joinToString(", ")}."
            }
            "timeline" in q || "chronolog" in q -> if (findings.timeline.isEmpty()) {
                "INSUFFICIENT: no dated events extracted."
            } else findings.timeline.joinToString("\n") { "${it.dateTime}: ${it.description}" }
            "law" in q || "legal" in q || "statute" in q ->
                "Applicable framework: " + findings.legalMappings.joinToString("; ")
            "tax" in q || "financ" in q ->
                findings.financial?.companyTax?.let {
                    "Company tax (${it.jurisdiction}): taxable R%,.2f at %.0f%% = R%,.2f. Confidence: HIGH."
                        .format(it.taxableIncome, it.rate * 100, it.taxLiability)
                } ?: "INSUFFICIENT: no financial figures present in the evidence."
            "seal" in q || "verify" in q ->
                _state.value.scanResult?.seal?.let {
                    "Seal ${it.sealId}, status ${it.status}. Footer: ${it.sealFooter()}"
                } ?: "No seal available."
            else -> "Evidence set: ${findings.documentsAnalyzed} docs, jurisdiction ${findings.jurisdiction}. " +
                "Ask about contradictions, timeline, legal framework, tax, or the seal."
        }
    }

    private fun seedSampleCase() {
        setGps(GpsRecord(latitude = -30.7667, longitude = 30.4000, accuracy = 5.2, timestamp = "2026-07-06T14:32:00Z"))
        ingestText(
            fileName = "cct_affidavit.txt",
            type = "affidavit",
            content = "From: AllFuels\n" +
                "Sworn before the Constitutional Court (CCT237/20, para 27): operators have no compensable goodwill.\n" +
                "The MOU was never countersigned by AllFuels.\n" +
                "We comply with PPA requirements in all franchise matters.\n" +
                "Gary was grateful and agreed to exit gracefully."
        )
        ingestText(
            fileName = "contemporaneous_record.txt",
            type = "email",
            content = "From: AllFuels\n" +
                "Date: 14 January 2026\n" +
                "AllFuels demanded R3.8M extension fee and drafted a clause forcing Gary to forfeit goodwill.\n" +
                "AllFuels collected rent under the binding MOU terms for 7 years.\n" +
                "The lease was presented as binding to Gary.\n" +
                "There was no Section 12B referral to Gary Highcock.\n" +
                "Gary was non-committal and negotiated for more time; three executives removed his only witness."
        )
        addAudioNote()
        addPhotoNote()
    }

    private fun addPhotoNote() {
        // A seeded photographic exhibit anchored to GPS + capture time (EXIF).
        val media = ForensicService.ingestMedia(
            id = "MED001",
            fileName = "site_photo_allfuels.jpg",
            kind = com.verumomnis.forensic.model.MediaKind.IMAGE,
            bytes = "seed-photographic-evidence".toByteArray(),
            mimeType = "image/jpeg",
            capturedAt = "2026-07-06T14:32:10Z",
            deviceGps = _state.value.gps,
            exifGps = GpsRecord(-30.7669, 30.3998, accuracy = 4.0, timestamp = "2026-01-14T09:15:00Z"),
            exifTimestamp = "2026:01:14 09:15:00",
            width = 4032,
            height = 3024
        )
        addMedia(media)
    }

    private fun addAudioNote() {
        val transcript = """
            [00:12] Speaker A: I'm mentally broken. I can't take this anymore.
            [00:18] Speaker B: Calm down. It's just business.
            [00:22] Speaker A: You took everything. The site, the goodwill... my wife left.
            [00:28] Speaker B: The goodwill has no value. You know that.
            [00:33] Speaker A: Then why did you make me sign the forfeiture clause? Why did I pay R3.8 million?
            [00:39] Speaker B: That was for the extension. Different thing entirely.
        """.trimIndent()
        val audio = ForensicService.ingestAudio(
            id = "AUD001",
            fileName = "voice_note_allfuels.m4a",
            bytes = transcript.toByteArray(),
            gps = _state.value.gps,
            transcript = transcript,
            creationDateMillis = 1_700_000_000_000,
            modificationDateMillis = 1_700_000_500_000, // modified after creation -> tamper signal
            sampleRates = listOf(44_100, 48_000),        // splice signal
            silenceGapsSec = listOf(0.2, 1.4)            // edit-point signal
        )
        audios += audio
        _state.update {
            it.copy(files = it.files + FileEntry(audio.fileName, "audio", "queued", audio.sha512, it.gps?.let { g -> "%.4f, %.4f".format(g.latitude, g.longitude) } ?: ""))
        }
    }
}
