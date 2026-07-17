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
import com.verumomnis.forensic.model.ResearchFindings
import com.verumomnis.forensic.model.ResearchTrigger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.verumomnis.forensic.model.HarassmentVerdict
import com.verumomnis.forensic.model.OtsAnchorResult
import com.verumomnis.forensic.model.OtsStatus
import com.verumomnis.forensic.model.SealedEmail
import com.verumomnis.forensic.ojrs.DeepResearchEngine
import com.verumomnis.forensic.ojrs.WebSearchService
import com.verumomnis.forensic.trust.TrustEngine
import com.verumomnis.forensic.trust.TrustScore
import com.verumomnis.forensic.pdf.SealedPdfExporter
import com.verumomnis.forensic.security.SilenceLedger
import com.verumomnis.forensic.seal.DocumentSealer
import com.verumomnis.forensic.seal.OpenTimestampsClient
import com.verumomnis.forensic.seal.SealMetadataCodec
import com.verumomnis.forensic.seal.SealVerifier
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

enum class SealPipelineStepStatus { PENDING, PROCESSING, COMPLETE, ERROR }

data class SealPipelineStep(
    val name: String,
    val label: String,
    val detail: String = "",
    val status: SealPipelineStepStatus = SealPipelineStepStatus.PENDING
)

data class SealIdentityInput(
    val fullName: String = "",
    val idNumber: String = "",
    val address: String = "",
    val email: String = ""
)

data class SealResult(
    val sealedPdf: ByteArray,
    val otsProof: ByteArray?,
    val sealId: String,
    val sha256: String,
    val sha512: String,
    val verifyUrl: String,
    val priorChain: List<String>,
    val pageCount: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SealResult
        return sealId == other.sealId &&
            sha256 == other.sha256 &&
            sha512 == other.sha512 &&
            verifyUrl == other.verifyUrl &&
            priorChain == other.priorChain &&
            pageCount == other.pageCount &&
            sealedPdf.contentEquals(other.sealedPdf) &&
            (otsProof == null && other.otsProof == null || otsProof != null && other.otsProof != null && otsProof.contentEquals(other.otsProof))
    }
    override fun hashCode(): Int {
        var result = sealId.hashCode()
        result = 31 * result + sha256.hashCode()
        result = 31 * result + sha512.hashCode()
        result = 31 * result + verifyUrl.hashCode()
        result = 31 * result + priorChain.hashCode()
        result = 31 * result + pageCount
        result = 31 * result + sealedPdf.contentHashCode()
        result = 31 * result + (otsProof?.contentHashCode() ?: 0)
        return result
    }
}

data class VerifyResult(
    val verdict: SealVerifier.Verdict,
    val seal: com.verumomnis.forensic.seal.SealChain.ParsedSealSubject?,
    val reason: String,
    val isEncrypted: Boolean = false,
    val expectedHash: String? = null
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
    val websiteSealStatus: String = "",
    /** VO-DSS-1.2 Seal Document screen state. */
    val sealPdfBytes: ByteArray? = null,
    val sealPdfName: String = "",
    val sealPdfSize: Long = 0L,
    val sealType: String = "private",
    val sealIdentity: SealIdentityInput = SealIdentityInput(),
    val passwordProtect: Boolean = false,
    val sealPassword: String = "",
    val sealPasswordConfirm: String = "",
    val sealOrganisation: String = "",
    val sealPipeline: List<SealPipelineStep> = defaultSealPipeline(),
    val sealResult: SealResult? = null,
    val sealError: String = "",
    val sealBusy: Boolean = false,
    /** Verify Document screen state. */
    val verifyHashInput: String = "",
    val verifyFileBytes: ByteArray? = null,
    val verifyFileName: String = "",
    val verifyResult: VerifyResult? = null,
    val verifyBusy: Boolean = false,
    /** Deep research findings from OJRS + web search. */
    val researchFindings: ResearchFindings? = null,
    val researching: Boolean = false
) {
    companion object {
        fun defaultSealPipeline(): List<SealPipelineStep> = listOf(
            SealPipelineStep("GPS + Device", "GPS + Device", "Capturing location & device fingerprint"),
            SealPipelineStep("SHA-256", "SHA-256", "Computing hash for OpenTimestamps"),
            SealPipelineStep("OpenTimestamps", "OpenTimestamps", "Submitting to calendar servers"),
            SealPipelineStep("A4 Watermark", "A4 Watermark", "Full-page background at 20% opacity"),
            SealPipelineStep("Clean QR Code", "Clean QR Code", "Encoding hash + identity + GPS + device"),
            SealPipelineStep("Seal Footer", "Seal Footer", "Hash + timestamp on every page"),
            SealPipelineStep("Finalize", "Finalize", "Preparing sealed PDF package"),
            SealPipelineStep("SHA-512", "SHA-512", "Verum Forensic Fingerprint (final verification)")
        )
    }
}

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

    /**
     * Deep research: searches judicial databases (SAFLII, CourtListener, BAILII, etc.)
     * and the open web for court cases, statutes, and company information relevant
     * to the sealed evidence. Results are stored in [researchFindings] and can be
     * incorporated into reports.
     */
    fun deepResearch(now: Instant = Instant.now()) {
        if (_state.value.scanResult == null) sealCase(now)
        val findings = _state.value.scanResult?.findings ?: return
        val caseRef = _state.value.scanResult?.seal?.documentReference ?: "Matter"
        val jurisdiction = _state.value.jurisdiction

        _state.update { it.copy(researching = true) }
        postAi("Initiating deep research across judicial databases and the open web… This may take a moment.")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val research = DeepResearchEngine.research(
                    findings = findings,
                    caseReference = caseRef,
                    jurisdiction = jurisdiction,
                    trigger = ResearchTrigger.USER_REQUEST
                )

                _state.update { it.copy(researchFindings = research, researching = false) }

                // Build and post the chat summary
                val summary = DeepResearchEngine.buildChatSummary(research)
                postAi(summary)

                // Also post a brief engine message about vault storage
                val judicialCount = research.judicialCases.size
                val webCount = research.webResults.size
                val statuteCount = research.applicableStatutes.size
                postEngine(
                    "Deep research complete: $judicialCount judicial case(s), $webCount web source(s), " +
                        "$statuteCount statute(s) found. Confidence: ${research.researchConfidence.name}. " +
                        "All findings stored in the vault as advisory research only."
                )

            } catch (e: Exception) {
                _state.update { it.copy(researching = false) }
                postAi("Deep research encountered an error: ${e.message}. The sealed evidence remains intact. " +
                    "You can retry research or proceed with the existing findings.")
            }
        }
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

    fun uriFileName(uri: Uri, context: Context): String {
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

    /** Select a PDF for the VO-DSS-1.2 Seal Document screen. */
    fun selectPdfForSealing(bytes: ByteArray, name: String, size: Long) {
        _state.update {
            it.copy(
                sealPdfBytes = bytes,
                sealPdfName = name,
                sealPdfSize = size,
                sealResult = null,
                sealError = "",
                sealPipeline = UiState.defaultSealPipeline(),
                websiteSealStatus = "Selected $name (${formatFileSize(size)})"
            )
        }
    }

    fun setSealType(type: String) {
        _state.update { it.copy(sealType = type) }
    }

    fun setIdentity(identity: SealIdentityInput) {
        _state.update { it.copy(sealIdentity = identity) }
    }

    fun setPasswordProtect(enabled: Boolean) {
        _state.update { it.copy(passwordProtect = enabled, sealPassword = "", sealPasswordConfirm = "") }
    }

    fun setPassword(password: String, confirm: String) {
        _state.update { it.copy(sealPassword = password, sealPasswordConfirm = confirm) }
    }

    fun setSealOrganisation(org: String) {
        _state.update { it.copy(sealOrganisation = org) }
    }

    fun clearSealDocument() {
        _state.update {
            it.copy(
                sealPdfBytes = null,
                sealPdfName = "",
                sealPdfSize = 0L,
                sealResult = null,
                sealError = "",
                sealPipeline = UiState.defaultSealPipeline(),
                sealBusy = false,
                passwordProtect = false,
                sealPassword = "",
                sealPasswordConfirm = "",
                sealOrganisation = ""
            )
        }
    }

    private fun updatePipelineStep(name: String, status: SealPipelineStepStatus, detail: String = "") {
        _state.update { s ->
            s.copy(sealPipeline = s.sealPipeline.map { step ->
                if (step.name == name) step.copy(status = status, detail = detail) else step
            })
        }
    }

    /**
     * Seal the selected PDF using the VO-DSS-1.2 pipeline.
     * Mirrors the web sealer flow and stores the sealed PDF for sharing/download.
     */
    fun sealDocument(now: Instant = Instant.now()) {
        val bytes = _state.value.sealPdfBytes ?: return
        val name = _state.value.sealPdfName.ifBlank { "sealed_document.pdf" }
        val type = _state.value.sealType
        val identity = _state.value.sealIdentity
        val passwordProtect = _state.value.passwordProtect
        val password = _state.value.sealPassword.takeIf { passwordProtect && it.length >= 8 }
        val org = _state.value.sealOrganisation.takeIf { type == "commercial" && it.isNotBlank() }

        if (passwordProtect && _state.value.sealPassword != _state.value.sealPasswordConfirm) {
            _state.update { it.copy(sealError = "Passwords do not match.", sealBusy = false) }
            return
        }
        if (passwordProtect && _state.value.sealPassword.length < 8) {
            _state.update { it.copy(sealError = "Password must be at least 8 characters.", sealBusy = false) }
            return
        }

        _state.update { it.copy(sealBusy = true, sealError = "", sealResult = null, sealPipeline = UiState.defaultSealPipeline()) }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                updatePipelineStep("GPS + Device", SealPipelineStepStatus.PROCESSING)
                val gps = _state.value.gps
                updatePipelineStep("GPS + Device", SealPipelineStepStatus.COMPLETE,
                    gps?.let { "%.4f, %.4f".format(it.latitude, it.longitude) } ?: "not recorded")

                updatePipelineStep("SHA-256", SealPipelineStepStatus.PROCESSING)
                val sha256 = com.verumomnis.forensic.seal.SealHasher.sha256Hex(bytes)
                updatePipelineStep("SHA-256", SealPipelineStepStatus.COMPLETE, sha256.take(16) + "…")

                updatePipelineStep("OpenTimestamps", SealPipelineStepStatus.PROCESSING)
                // Anchor the document's SHA-512 fingerprint via the canonical OTS service.
                val sha512ForAnchor = com.verumomnis.forensic.seal.SealHasher.sha512Hex(bytes)
                val ots = runCatching { OpenTimestampsClient.submit(sha512ForAnchor) }.getOrNull()
                updatePipelineStep(
                    "OpenTimestamps",
                    if (ots is OpenTimestampsClient.OtsResult.Success) SealPipelineStepStatus.COMPLETE else SealPipelineStepStatus.ERROR,
                    when (ots) {
                        is OpenTimestampsClient.OtsResult.Success -> "pending Bitcoin attestation"
                        is OpenTimestampsClient.OtsResult.Failure -> ots.error
                        else -> "offline — not anchored"
                    }
                )

                updatePipelineStep("A4 Watermark", SealPipelineStepStatus.PROCESSING)
                updatePipelineStep("A4 Watermark", SealPipelineStepStatus.COMPLETE)

                updatePipelineStep("Clean QR Code", SealPipelineStepStatus.PROCESSING)
                updatePipelineStep("Clean QR Code", SealPipelineStepStatus.COMPLETE)

                updatePipelineStep("Seal Footer", SealPipelineStepStatus.PROCESSING)
                updatePipelineStep("Seal Footer", SealPipelineStepStatus.COMPLETE)

                updatePipelineStep("Finalize", SealPipelineStepStatus.PROCESSING)
                val result = DocumentSealer.seal(
                    originalPdfBytes = bytes,
                    options = DocumentSealer.SealOptions(
                        timestampMs = now.toEpochMilli(),
                        sealType = type,
                        org = org,
                        identity = SealMetadataCodec.SealIdentity(
                            n = identity.fullName.takeIf { it.isNotBlank() },
                            id = identity.idNumber.takeIf { it.isNotBlank() },
                            a = identity.address.takeIf { it.isNotBlank() },
                            e = identity.email.takeIf { it.isNotBlank() }
                        ),
                        gpsLat = gps?.latitude?.toString(),
                        gpsLng = gps?.longitude?.toString(),
                        gpsAccuracyM = gps?.accuracy?.toInt(),
                        device = deviceString(),
                        originalName = name,
                        anchorToBlockchain = false,
                        password = password
                    )
                )
                updatePipelineStep("Finalize", SealPipelineStepStatus.COMPLETE)

                updatePipelineStep("SHA-512", SealPipelineStepStatus.PROCESSING)
                updatePipelineStep("SHA-512", SealPipelineStepStatus.COMPLETE, result.sha512.take(16) + "…")

                val outFile = File(websiteSealDir(), "sealed_${result.sealId}_${now.toEpochMilli()}.pdf")
                outFile.writeBytes(result.sealedPdf)
                // The pipeline's own OTS result carries the pending proof
                // (DocumentSealer ran with anchorToBlockchain = false, so
                // result.ots is null by design — do not read it here).
                val otsProofBytes = (ots as? OpenTimestampsClient.OtsResult.Success)?.proof
                otsProofBytes?.let { proof ->
                    File(websiteSealDir(), "sealed_${result.sealId}_${now.toEpochMilli()}.ots").apply { writeBytes(proof) }
                }

                _state.update {
                    it.copy(
                        sealBusy = false,
                        sealResult = SealResult(
                            sealedPdf = result.sealedPdf,
                            otsProof = otsProofBytes,
                            sealId = result.sealId,
                            sha256 = result.sha256,
                            sha512 = result.sha512,
                            verifyUrl = result.verifyUrl,
                            priorChain = result.priorChain,
                            pageCount = result.pageCount
                        ),
                        websiteSealedFile = outFile,
                        websiteSealStatus = "Sealed $name → ${result.sealId} (${result.pageCount} page(s)). Saved to vault/reports/sealed."
                    )
                }
                postEngine("Website-format seal complete: ${result.sealId} · ${result.verifyUrl}")
            }.onFailure { e ->
                _state.update { it.copy(sealBusy = false, sealError = "Seal failed: ${e.message}") }
                postEngine("Seal failed: ${e.message}")
            }
        }
    }

    /** Verify a pasted SHA-512 hash (format + blockchain status check). */
    fun verifyHash(hash: String) {
        val cleaned = hash.trim().lowercase().replace(Regex("\\s+"), "")
        val result = when {
            cleaned.isEmpty() -> VerifyResult(
                SealVerifier.Verdict.NO_SEAL, null, "Please enter a SHA-512 hash.", expectedHash = cleaned
            )
            !Regex("^[0-9a-f]{128}$").matches(cleaned) -> VerifyResult(
                SealVerifier.Verdict.NO_SEAL, null,
                when {
                    cleaned.length < 128 -> "Hash too short. SHA-512 must be exactly 128 hexadecimal characters (you provided ${cleaned.length})."
                    cleaned.length > 128 -> "Hash too long. SHA-512 must be exactly 128 hexadecimal characters (you provided ${cleaned.length})."
                    else -> "Invalid characters. SHA-512 must contain only hexadecimal characters (0-9, a-f)."
                },
                expectedHash = cleaned
            )
            else -> {
                // 128 hex chars proves format only; an authenticity claim is made
                // solely when the hash matches a real seal record on this device.
                val check = SealVerifier.checkHashAgainstRecords(
                    cleaned,
                    listOf(_state.value.report?.seal, _state.value.scanResult?.seal),
                    runCatching { vault.integrityManifest() }.getOrElse { emptyList() }
                )
                VerifyResult(check.verdict, null, check.message, expectedHash = cleaned)
            }
        }
        _state.update { it.copy(verifyResult = result, verifyHashInput = hash) }
    }

    /** Select a file for verification. */
    fun selectVerifyFile(bytes: ByteArray, name: String) {
        _state.update { it.copy(verifyFileBytes = bytes, verifyFileName = name, verifyResult = null) }
    }

    /** Verify an uploaded file (sealed PDF) against an optional expected hash. */
    fun verifyDocument(expectedHash: String? = null) {
        val bytes = _state.value.verifyFileBytes ?: return
        _state.update { it.copy(verifyBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val verification = SealVerifier.verify(bytes, expectedHash)
            _state.update {
                it.copy(
                    verifyBusy = false,
                    verifyResult = VerifyResult(
                        verdict = verification.verdict,
                        seal = verification.seal,
                        reason = verification.reason,
                        isEncrypted = verification.isEncrypted,
                        expectedHash = verification.expectedHash
                    )
                )
            }
        }
    }

    fun clearVerifyResult() {
        _state.update { it.copy(verifyResult = null, verifyHashInput = "", verifyFileBytes = null, verifyFileName = "") }
    }

    private fun formatFileSize(size: Long): String {
        val mb = size / (1024 * 1024)
        return if (mb > 0) "%.2f MB".format(size / (1024.0 * 1024.0)) else "%.0f KB".format(size / 1024.0)
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
                guardianBlock = null,
                researchFindings = null
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

        // If research findings exist, use the Gemma3ReportWriter with research prompt
        val research = _state.value.researchFindings
        val narrativeWriter = if (research != null) {
            com.verumomnis.forensic.engine.Gemma3ReportWriter
        } else {
            com.verumomnis.forensic.engine.DeterministicReportWriter
        }

        val report = ReportGenerator.generate(
            findings = findings,
            caseName = caseName,
            now = now,
            deviceId = device?.deviceId ?: "",
            publicKeyFingerprint = device?.publicKeyFingerprint ?: "",
            findingsJsonPath = _state.value.findingsJsonPath,
            narrativeWriter = narrativeWriter
        )

        // If research exists, build the research prompt and store it alongside
        val reportWithResearch = if (research != null) {
            val researchPrompt = DeepResearchEngine.buildResearchPrompt(research, findings)
            report.copy(
                gemmaNarrative = report.gemmaNarrative + "\n\n" + researchPrompt
            )
        } else report

        val signedReport = reportWithResearch.copy(seal = signAndUpdateSeal(reportWithResearch.seal))
        _state.update {
            it.copy(
                report = signedReport,
                chat = it.chat + ChatMessage(
                    author = "Report Writer (Gemma 3)",
                    text = "Generated sealed report ${signedReport.reference} with ${signedReport.contradictions.size} " +
                        "anchored contradiction(s)." + (if (research != null) " External research from ${research.sourceUrls.size} sources included." else "") +
                        " Seal: ${signedReport.seal.extendedFooter()}",
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
                // Pending: no block height/tx/confirmations until the Bitcoin
                // attestation confirms; status + calendarUrls carry the truth.
                val updated = report.seal.copy(
                    status = res.status.name,
                    otsProofFile = res.otsProofFile,
                    calendarUrls = res.calendarUrls,
                    blockchain = com.verumomnis.forensic.model.BlockchainAnchor(network = "bitcoin")
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
     *
     * Now includes research keywords: 'research', 'precedent', 'case law', 'search',
     * 'company', 'statute' trigger responses about available deep research.
     */
    private fun respond(query: String): String {
        val findings = _state.value.scanResult?.findings
            ?: return "INSUFFICIENT: no forensic scan has been run yet. Upload evidence and start a scan."
        val research = _state.value.researchFindings
        val q = query.lowercase()

        return when {
            // Research-related queries
            "research" in q || "precedent" in q || "case law" in q || "search cases" in q -> {
                if (research == null) {
                    "No research has been conducted yet. Say 'deep research' to search judicial databases (SAFLII, CourtListener, BAILII, etc.) " +
                        "and the web for relevant court cases, statutes, and company information."
                } else {
                    buildString {
                        append("Research results (${research.researchConfidence.name} confidence):\n")
                        append("${research.judicialCases.size} judicial case(s), ${research.webResults.size} web source(s), " +
                            "${research.applicableStatutes.size} statute(s).\n")
                        research.judicialCases.take(3).forEach {
                            append("  · [${it.database.name}] ${it.citation} — ${it.court}\n")
                        }
                        append("Say 'full research' for complete details or 'generate report with research' to include in the report.")
                    }
                }
            }
            "full research" in q -> {
                research?.let { DeepResearchEngine.buildChatSummary(it) }
                    ?: "No research available. Run 'deep research' first."
            }
            "generate report with research" in q -> {
                if (research != null) {
                    generateReport()
                    "Report generated with external research incorporated. Check the Reports tab."
                } else {
                    "No research findings to include. Run 'deep research' first, then try again."
                }
            }
            "company" in q || "corporate" in q -> {
                if (research == null) {
                    "Say 'deep research' to search for company records, corporate filings, and related cases."
                } else {
                    val companyResults = research.webResults.filter { it.category.name.contains("COMPANY") }
                    if (companyResults.isEmpty()) "No company-specific findings in research. Try 'deep research' for updated results."
                    else companyResults.take(3).joinToString("\n") { "  · ${it.title} (${it.domain})" }
                }
            }
            "statute" in q || "act" in q || "regulation" in q -> {
                if (research?.applicableStatutes.isNullOrEmpty()) {
                    "No statutes identified yet. Say 'deep research' to search applicable laws for jurisdiction ${findings.jurisdiction}."
                } else {
                    research!!.applicableStatutes.take(5).joinToString("\n") {
                        "  · ${it.citation} (${it.jurisdiction})"
                    }
                }
            }

            // Original queries
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
            "law" in q || "legal" in q || "framework" in q ->
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
            "deep research" in q -> {
                deepResearch()
                "Deep research initiated…"
            }
            else -> "Evidence set: ${findings.documentsAnalyzed} docs, jurisdiction ${findings.jurisdiction}. " +
                "Ask about contradictions, timeline, legal framework, tax, the seal, or say 'deep research' for external case research."
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
