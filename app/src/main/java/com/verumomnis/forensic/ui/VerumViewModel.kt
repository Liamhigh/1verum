package com.verumomnis.forensic.ui

import androidx.lifecycle.ViewModel
import com.verumomnis.forensic.core.Constitution
import com.verumomnis.forensic.core.DeviceTier
import com.verumomnis.forensic.core.Llm
import com.verumomnis.forensic.core.ModelLoader
import com.verumomnis.forensic.crypto.VerificationResult
import com.verumomnis.forensic.engine.AntiHarassmentMonitor
import com.verumomnis.forensic.engine.EmailModule
import com.verumomnis.forensic.engine.EvidenceDocument
import com.verumomnis.forensic.engine.ForensicService
import com.verumomnis.forensic.engine.ReportGenerator
import com.verumomnis.forensic.engine.ScanResult
import com.verumomnis.forensic.model.ForensicReport
import com.verumomnis.forensic.model.GpsRecord
import com.verumomnis.forensic.model.HarassmentVerdict
import com.verumomnis.forensic.model.SealedEmail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val emailStatus: String = "Draft a sealed forensic email. Every draft is delivered as a sealed PDF and logged."
)

class VerumViewModel : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val documents = mutableListOf<EvidenceDocument>()
    private val harassmentMonitor = AntiHarassmentMonitor()

    init {
        configureDevice(6)
        seedSampleCase()
        _state.update {
            it.copy(
                chat = listOf(
                    ChatMessage(
                        author = "Verum Omnis",
                        text = "Evidence vault ready. Constitution v${Constitution.VERSION} loaded. " +
                            "Ask about the evidence, request a timeline, or run a forensic scan.",
                        fromUser = false
                    )
                )
            )
        }
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
        if (documents.isEmpty()) {
            _state.update { it.copy(scanLog = "No evidence to scan. Upload documents first.") }
            return
        }
        _state.update { it.copy(scanning = true, scanLog = "Nine-Brain forensic analysis in progress…") }
        val result = ForensicService.scan(documents, now)
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
        val corpus = documents.joinToString("|") { it.sha512 }
        return ForensicService.verify(corpus.toByteArray(), result.seal)
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
    }
}
