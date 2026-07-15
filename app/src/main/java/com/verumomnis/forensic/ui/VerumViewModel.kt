package com.verumomnis.forensic.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verumomnis.forensic.blockchain.BlockchainService
import com.verumomnis.forensic.blockchain.OpenTimestampsService
import com.verumomnis.forensic.core.Constitution
import com.verumomnis.forensic.core.ConstitutionalPrompt
import com.verumomnis.forensic.core.DeviceTier
import com.verumomnis.forensic.core.Llm
import com.verumomnis.forensic.core.ModelLoader
import com.verumomnis.forensic.crypto.VerificationResult
import com.verumomnis.forensic.identity.IdentityService
import com.verumomnis.forensic.engine.AntiHarassmentMonitor
import com.verumomnis.forensic.engine.AudioEvidence
import com.verumomnis.forensic.engine.EmailModule
import com.verumomnis.forensic.engine.EvidenceDocument
import com.verumomnis.forensic.engine.FindingsJsonEmitter
import com.verumomnis.forensic.engine.ForensicService
import com.verumomnis.forensic.engine.GuardianBrain
import com.verumomnis.forensic.engine.MediaEvidence
import com.verumomnis.forensic.engine.ReportGenerator
import com.verumomnis.forensic.engine.ScanResult
import com.verumomnis.forensic.engine.TaxModule
import com.verumomnis.forensic.model.ForensicReport
import com.verumomnis.forensic.model.GuardianAssessment
import com.verumomnis.forensic.model.GpsRecord
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.verumomnis.forensic.model.HarassmentVerdict
import com.verumomnis.forensic.model.OtsAnchorResult
import com.verumomnis.forensic.model.OtsStatus
import com.verumomnis.forensic.model.SealedEmail
import com.verumomnis.forensic.trust.TrustEngine
import com.verumomnis.forensic.trust.TrustScore
import com.verumomnis.forensic.pdf.SealedPdfExporter
import com.verumomnis.forensic.security.SilenceLedger
import com.verumomnis.forensic.seal.DocumentSealer
import com.verumomnis.forensic.seal.SealMetadataCodec
import com.verumomnis.forensic.vault.EvidenceVault
import com.verumomnis.forensic.work.ScanWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.TimeZone

data class FileEntry(
    val name: String,
    val type: String,
    val status: String,
    val sha512: String = "",
    val gps: String = ""
)

data class ChatMessage(val author: String, val text: String, val fromUser: Boolean)

/**
 * A file that has been picked and validated but not yet added to the sealed case.
 * The user sees a preview and must confirm before sealing.
 */
data class PendingFilePreview(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha512: String,
    val displayText: String,
    val isMedia: Boolean,
    val mediaEvidence: MediaEvidence? = null,
    val documentText: String = ""
)

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
    val scanLog: String = "Awaiting evidence. Select a case file to begin a forensic scan.",
    val chat: List<ChatMessage> = emptyList(),
    val report: ForensicReport? = null,
    val emails: List<SealedEmail> = emptyList(),
    val emailStatus: String = "Draft a sealed forensic email. Every draft is delivered as a sealed PDF and logged.",
    val otsResult: OtsAnchorResult? = null,
    val otsStatus: String = "Evidence seal not yet anchored to Bitcoin (OpenTimestamps).",
    val anchoring: Boolean = false,
    val findingsJsonPath: String = "",
    val trustScore: TrustScore? = null,
    val identityStatus: String = "Identity not initialized.",
    val identityFingerprint: String = "",
    val systemPrompt: String = ConstitutionalPrompt.coreDirective(),
    val pendingFiles: List<PendingFilePreview> = emptyList(),
    val sealStage: SealStage = SealStage.IDLE,
    val guardianBlock: GuardianAssessment? = null,
    /** Last PDF sealed with the website-compatible VO-DSS-1.2 layer. */
    val websiteSealedFile: java.io.File? = null,
    val websiteSealStatus: String = ""
)

class VerumViewModel(
    application: Application,
    private val identityService: IdentityService = IdentityService(application),
    private val blockchainService: BlockchainService = OpenTimestampsService,
    private val vault: EvidenceVault = EvidenceVault(application),
    seedSampleCase: Boolean = false
) : AndroidViewModel(application) {

    /** Constructor used by Android's default ViewModelProvider factory. */
    constructor(application: Application) : this(
        application,
        IdentityService(application),
        OpenTimestampsService,
        EvidenceVault(application),
        false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val documents = mutableListOf<EvidenceDocument>()
    private val audios = mutableListOf<AudioEvidence>()
    private val medias = mutableListOf<MediaEvidence>()
    private val harassmentMonitor = AntiHarassmentMonitor()
    private val silenceLedger = SilenceLedger(context = getApplication())

    fun mediaCount(): Int = medias.size

    private fun initializeIdentity() {
        val device = identityService.initializeDevice()
        _state.update {
            it.copy(
                identityStatus = "Device identity ${device.deviceId.take(8)}… initialized",
                identityFingerprint = device.publicKeyFingerprint.take(16)
            )
        }
    }

    /** Sign a seal with the device identity and compute the resulting trust score. */
    private fun signAndUpdateSeal(seal: com.verumomnis.forensic.model.SealRecord): com.verumomnis.forensic.model.SealRecord {
        val proof = identityService.signSeal(seal) ?: return seal
        return seal.copy(identityProof = proof)
    }

    private fun computeTrustScore() {
        val findings = _state.value.scanResult?.findings ?: return
        val identityProof = _state.value.report?.seal?.identityProof
            ?: _state.value.scanResult?.seal?.identityProof
        val score = TrustEngine.compute(findings, identityProof, _state.value.otsResult)
        _state.update { it.copy(trustScore = score) }
    }

    init {
        initializeIdentity()
        configureDevice(6)
        if (seedSampleCase) seedSampleCase()
        _state.update { s ->
            s.copy(
                chat = listOf(
                    ChatMessage(
                        author = "Verum Omnis",
                        text = "Truth for All. I am ${s.communicator}, the Verum Omnis communicator (Constitution v${Constitution.VERSION}).\n\n" +
                            "Communication mode: UNRESTRICTED under the Constitution. I will answer directly and cite sealed evidence. " +
                            "Anything you add with + goes straight to the forensic engine — SHA-512 sealed, GPS-anchored and stored " +
                            "in the vault before I ever see it. I only read the SEALED case file, then help with the narrative, " +
                            "timeline and legal strategy. Nothing leaves here unsealed.",
                        fromUser = false
                    )
                )
            )
        }
    }

    fun postEngine(text: String) {
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
    fun sealCase(now: Instant = Instant.now(), caseName: String = "Matter") {
        if (documents.isEmpty() && audios.isEmpty() && medias.isEmpty()) {
            postEngine("Nothing to seal yet — add evidence with the + button first.")
            _state.update { it.copy(sealStage = SealStage.IDLE) }
            return
        }
        _state.update { it.copy(sealStage = SealStage.SCANNING) }
        runScan(now)
        _state.update { it.copy(sealStage = SealStage.SEALING) }
        storeFindingsJson()
        generateReport(caseName = caseName, now = now)
        signSealsAndAnchor()
        _state.update { it.copy(sealStage = SealStage.ANCHORING) }
        val r = _state.value.scanResult
        postEngine(
            "Sealed ${_state.value.files.size} item(s) into the vault. " +
                "SHA-512 + GPS recorded, findings JSON written, forensic report generated" +
                (r?.let { " (seal ${it.seal.shortcode})" } ?: "") +
                ". The AI may now read the sealed case file."
        )
    }

    /** Sign the current scan and report seals with the device identity, then anchor. */
    private fun signSealsAndAnchor() {
        _state.value.scanResult?.let { result ->
            val signed = signAndUpdateSeal(result.seal)
            _state.update { it.copy(scanResult = result.copy(seal = signed)) }
        }
        _state.value.report?.let { report ->
            val signed = signAndUpdateSeal(report.seal)
            _state.update { it.copy(report = report.copy(seal = signed)) }
        }
        computeTrustScore()
        anchorSealToBitcoin(auto = true)
    }

    /** Serialize and vault the current findings as a machine-readable JSON bundle. */
    private fun storeFindingsJson() {
        val findings = _state.value.scanResult?.findings ?: return
        val reference = _state.value.scanResult?.seal?.documentReference ?: "findings"
        val fileName = "${reference}_findings.json"
        val json = Json { prettyPrint = true }.encodeToString(findings)
        vault.storeFinding(fileName, json)
        _state.update { it.copy(findingsJsonPath = File(vault.findings, fileName).absolutePath) }
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
        val communicator = ModelLoader.communicator(models)
        val prompt = when {
            communicator.name == "Gemma 4" && communicator.flagship ->
                ConstitutionalPrompt.communicatorFlagship()
            communicator.name == "Phi-3" ->
                ConstitutionalPrompt.communicatorStandard()
            else ->
                ConstitutionalPrompt.coreDirective()
        }
        _state.update {
            it.copy(
                deviceRamGb = ramGb,
                deviceTier = DeviceTier.forRam(ramGb),
                models = models,
                communicator = communicator.name,
                reportWriter = ModelLoader.reportWriter(models).name,
                systemPrompt = prompt
            )
        }
    }

    fun setGps(gps: GpsRecord) {
        _state.update { it.copy(gps = gps) }
    }

    fun setSealStage(stage: SealStage) {
        _state.update { it.copy(sealStage = stage) }
    }

    fun clearGuardianBlock() {
        _state.update { it.copy(guardianBlock = null) }
    }

    /** Log a B9 hard-stop to the Silence Ledger and block the UI until the user acts. */
    private fun blockForGuardian(assessment: GuardianAssessment, now: Instant = Instant.now()) {
        if (assessment.violations.isNotEmpty()) {
            silenceLedger.appendAll(assessment, now)
        }
        _state.update { it.copy(guardianBlock = assessment) }
    }

    /** Show picked files to the user for preview before sealing. */
    fun setPendingFiles(files: List<PendingFilePreview>) {
        _state.update { it.copy(pendingFiles = files, sealStage = SealStage.IDLE) }
    }

    /** Discard the pending preview without sealing. */
    fun clearPendingFiles() {
        _state.update { it.copy(pendingFiles = emptyList(), sealStage = SealStage.IDLE) }
    }

    private fun websiteSealDir(): File =
        File(getApplication<Application>().filesDir, "vault/reports/sealed").apply { mkdirs() }

    private fun deviceString(): String {
        val cores = Runtime.getRuntime().availableProcessors()
        return "Android|$cores|${TimeZone.getDefault().id}"
    }

    private fun uriFileName(uri: Uri, context: Context): String {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "sealed_document.pdf"
    }

    /**
     * Seal an arbitrary PDF with the website-compatible VO-DSS-1.2 layer.
     * The sealed PDF is written to vault/reports/sealed and exposed for sharing.
     */
    fun sealDocumentWebsiteFormat(uri: Uri, context: Context, now: Instant = Instant.now()) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(websiteSealStatus = "Reading PDF…", websiteSealedFile = null) }
            runCatching {
                val originalName = uriFileName(uri, context)
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read $originalName")
                val gps = _state.value.gps
                val result = DocumentSealer.seal(
                    originalPdfBytes = bytes,
                    options = DocumentSealer.SealOptions(
                        timestampMs = now.toEpochMilli(),
                        sealType = "private",
                        gpsLat = gps?.latitude?.toString(),
                        gpsLng = gps?.longitude?.toString(),
                        gpsAccuracyM = gps?.accuracy?.toInt(),
                        device = deviceString(),
                        originalName = originalName,
                        anchorToBlockchain = false
                    )
                )
                val outFile = File(websiteSealDir(), "sealed_${result.sealId}_${now.toEpochMilli()}.pdf")
                outFile.writeBytes(result.sealedPdf)
                _state.update {
                    it.copy(
                        websiteSealedFile = outFile,
                        websiteSealStatus = "Sealed ${originalName} → ${result.sealId} (${result.pageCount} page(s)). Saved to vault/reports/sealed."
                    )
                }
                postEngine("Website-format seal complete: ${result.sealId} · ${result.verifyUrl}")
            }.onFailure { e ->
                _state.update { it.copy(websiteSealStatus = "Seal failed: ${e.message}", websiteSealedFile = null) }
                postEngine("Website-format seal failed: ${e.message}")
            }
        }
    }

    /** Share the last website-format sealed PDF via the system's PDF chooser. */
    fun shareWebsiteSealedFile(context: Context) {
        _state.value.websiteSealedFile?.let { SealedPdfExporter(context).share(it) }
    }

    /**
     * Start the forensic scan from files already picked and previewed on the scan home.
     * Runs the seal pipeline on a background coroutine.
     */
    fun startForensicScan(caseName: String = "Matter", now: Instant = Instant.now()) {
        confirmAndSeal(caseName, now)
    }

    /** Add pending files to the case, clear the preview, and run the seal pipeline. */
    fun confirmAndSeal(caseName: String = "Matter", now: Instant = Instant.now()) {
        val pending = _state.value.pendingFiles
        if (pending.isEmpty()) return
        // Schedule a WorkManager background scan so the process survives backgrounding.
        ScanWorkScheduler.schedule(getApplication(), caseName, pending)
        _state.update { it.copy(sealStage = SealStage.SCANNING, pendingFiles = emptyList()) }
        viewModelScope.launch(Dispatchers.IO) {
            pending.forEach { preview ->
                if (preview.isMedia && preview.mediaEvidence != null) {
                    addMedia(preview.mediaEvidence)
                } else {
                    val doc = EvidenceDocument(
                        evidenceId = "DOC%03d".format(documents.size + 1),
                        fileName = preview.fileName,
                        type = when {
                            preview.mimeType.contains("pdf") -> "pdf"
                            preview.mimeType.startsWith("text") -> "document"
                            else -> "document"
                        },
                        text = preview.documentText,
                        sha512 = preview.sha512,
                        gps = _state.value.gps
                    )
                    addDocument(doc)
                }
            }
            sealCase(now, caseName)
        }
    }

    /** Clear the current case so the user can start a new scan from an empty vault. */
    fun clearCase() {
        documents.clear()
        audios.clear()
        medias.clear()
        _state.update {
            it.copy(
                files = emptyList(),
                scanResult = null,
                report = null,
                pendingFiles = emptyList(),
                sealStage = SealStage.IDLE,
                scanLog = "Awaiting evidence. Select a case file to begin a forensic scan.",
                trustScore = null,
                otsResult = null,
                otsStatus = "Evidence seal not yet anchored to Bitcoin (OpenTimestamps).",
                findingsJsonPath = "",
                guardianBlock = null
            )
        }
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

    fun runScan(now: Instant = Instant.now(), caseName: String = "Matter") {
        if (documents.isEmpty() && audios.isEmpty() && medias.isEmpty()) {
            _state.update { it.copy(scanLog = "No evidence to scan. Upload documents first.") }
            return
        }
        _state.update { it.copy(scanning = true, scanLog = "Nine-Brain forensic analysis in progress…") }
        val result = ForensicService.scan(documents, audios, medias, now, vault = vault, caseName = caseName)
        val findingsJsonName = FindingsJsonEmitter.findingsFileName(caseName, now)
        val findingsJsonPath = java.io.File(vault.findings, findingsJsonName).absolutePath
        val guardian = result.findings.guardian
        if (guardian?.hardStopRequired == true) {
            blockForGuardian(guardian, now)
        }
        _state.update { s ->
            s.copy(
                scanning = false,
                scanResult = result,
                findingsJsonPath = findingsJsonPath,
                jurisdiction = result.findings.jurisdiction,
                files = s.files.map { it.copy(status = "scanned") },
                scanLog = buildString {
                    append("Scan complete · ${result.findings.documentsAnalyzed} documents · ")
                    append("${result.findings.contradictions.size} contradictions · ")
                    append("${result.findings.timeline.size} timeline events · ")
                    append("seal ${result.seal.shortcode}")
                    if (guardian?.hardStopRequired == true) append(" · GUARDIAN HARD STOP")
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

    fun generateReport(caseName: String = "Matter", now: Instant = Instant.now()) {
        val findings = _state.value.scanResult?.findings ?: run {
            runScan(now)
            _state.value.scanResult?.findings
        } ?: return
        val device = identityService.deviceIdentity()
        val report = ReportGenerator.generate(
            findings = findings,
            caseName = caseName,
            now = now,
            deviceId = device?.deviceId ?: "",
            publicKeyFingerprint = device?.publicKeyFingerprint ?: "",
            findingsJsonPath = _state.value.findingsJsonPath
        )
        val signedReport = report.copy(seal = signAndUpdateSeal(report.seal))
        _state.update {
            it.copy(
                report = signedReport,
                chat = it.chat + ChatMessage(
                    author = "Report Writer (Gemma 3)",
                    text = "Generated sealed report ${signedReport.reference} with ${signedReport.contradictions.size} " +
                        "anchored contradiction(s). Seal: ${signedReport.seal.extendedFooter()}",
                    fromUser = false
                )
            )
        }
        computeTrustScore()
        anchorSealToBitcoin(auto = true)
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

    /**
     * Anchor the current report/evidence seal to Bitcoin via OpenTimestamps.
     * When [auto] is true the call is silent on missing seals (e.g. invoked from
     * generateReport before any evidence is present).
     */
    fun anchorSealToBitcoin(auto: Boolean = false) {
        val seal = _state.value.report?.seal ?: _state.value.scanResult?.seal
        if (seal == null) {
            if (!auto) _state.update { it.copy(otsStatus = "Run a scan (or generate a report) before anchoring.") }
            return
        }
        if (_state.value.anchoring) return
        _state.update { it.copy(anchoring = true, otsStatus = "Submitting SHA-256 digest to OpenTimestamps calendars…") }
        viewModelScope.launch(Dispatchers.IO) {
            val res = runCatching { blockchainService.anchor(seal.sha512) }.getOrElse {
                OtsAnchorResult(
                    status = OtsStatus.FAILED, sha512 = seal.sha512, sha256Digest = "",
                    calendarUrls = emptyList(), submittedAt = java.time.Instant.now().toString(),
                    message = it.message ?: "Anchoring failed"
                )
            }
            res.otsProofBase64?.let { vault.storeOtsProof(seal.shortcode, it) }
            updateSealAfterAnchor(res)
            val status = when (res.status) {
                OtsStatus.PENDING -> "PENDING · digest ${res.sha256Digest.take(12)}… submitted to ${res.calendarUrls.size} calendar(s). Proof: ${res.otsProofFile}"
                OtsStatus.OFFLINE -> "OFFLINE · ${res.message}"
                OtsStatus.CONFIRMED -> "CONFIRMED on Bitcoin."
                OtsStatus.FAILED -> "FAILED · ${res.message}"
            }
            val stage = when (res.status) {
                OtsStatus.CONFIRMED, OtsStatus.PENDING -> SealStage.DONE
                else -> SealStage.ERROR
            }
            _state.update { it.copy(anchoring = false, otsResult = res, otsStatus = status, sealStage = stage) }
            computeTrustScore()
        }
    }

    private fun updateSealAfterAnchor(res: OtsAnchorResult) {
        _state.value.report?.let { report ->
            if (report.seal.sha512 == res.sha512) {
                val updated = report.seal.copy(
                    status = res.status.name,
                    otsProofFile = res.otsProofFile,
                    blockchain = com.verumomnis.forensic.model.BlockchainAnchor(
                        network = "bitcoin",
                        blockHeight = res.calendarUrls.size.toLong()
                    )
                )
                _state.update { it.copy(report = report.copy(seal = updated)) }
            }
        }
        _state.value.scanResult?.let { scan ->
            if (scan.seal.sha512 == res.sha512) {
                val updated = scan.seal.copy(
                    status = res.status.name,
                    otsProofFile = res.otsProofFile
                )
                _state.update { it.copy(scanResult = scan.copy(seal = updated)) }
            }
        }
    }

    fun sendChat(text: String) {
        if (text.isBlank()) return
        val promptAssessment = GuardianBrain.validatePrompt(text)
        if (promptAssessment.hardStopRequired) {
            blockForGuardian(promptAssessment)
            return
        }
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

    /** Seed the AllFuels sample case. Public so tests and demo builds can opt in explicitly. */
    fun seedSampleCase() {
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
