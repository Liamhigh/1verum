package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.DocumentExtractionCoverage
import com.verumomnis.forensic.model.ForensicReport
import com.verumomnis.forensic.model.PdfExtractionCoverage
import com.verumomnis.forensic.model.PdfRetryAttempt
import com.verumomnis.forensic.vault.EvidenceVault
import java.io.File
import java.time.Instant
import java.util.UUID

data class PdfRetryResult(
    val record: DocumentExtractionCoverage,
    val report: ForensicReport? = null,
    val reportFile: File? = null,
    val message: String
)

/** Separate PDF-only analysis: never mutates the active case or any earlier sealed artefact. */
class PdfRetryService(
    private val vault: EvidenceVault,
    private val cacheDir: File,
    private val extract: (File, PdfExtractionCoverage, PdfRetryAttempt) -> PdfExtractionResult,
    private val writePdf: (ForensicReport, String?) -> File,
    private val signReport: (ForensicReport) -> ForensicReport = { it }
) {
    fun retry(
        source: DocumentExtractionCoverage,
        selectedPages: List<Int>,
        priorReportReference: String?,
        now: Instant = Instant.now()
    ): PdfRetryResult {
        val attempt = PdfRetryAttempt(
            UUID.randomUUID().toString(), now.toString(), selectedPages.sorted(),
            source.coverage.retryAttempt?.id, priorReportReference
        )
        // Work from a saved snapshot, not a record supplied by an arbitrary caller.
        check(vault.listPdfExtractions().any { it == source }) { "Saved page coverage is unavailable. Re-import the original PDF." }
        val temp = File.createTempFile("pdf-retry-", ".pdf", cacheDir)
        val extracted = try {
            vault.materializePdfSource(source, temp)
            extract(temp, source.coverage, attempt)
        } finally { temp.delete() }
        check(extracted.coverage.retryAttempt == attempt) { "OCR retry provenance was not recorded." }
        val record = vault.storePdfRetry(source.copy(coverage = extracted.coverage))
        if (extracted.text.isBlank()) return PdfRetryResult(record,
            message = "Retry saved, but no readable text was extracted. No findings or report were generated. This is not a clean result.")

        val caseName = "OCR retry ${attempt.id}"
        val scan = ForensicService.scan(listOf(EvidenceDocument(
            evidenceId = source.evidenceId.ifBlank { "DOC001" }, fileName = source.fileName,
            type = "pdf", text = extracted.text, sha512 = source.sha512, extractionCoverage = extracted.coverage
        )), now = now)
        val findingsName = "ocr_retry_${attempt.id}_findings.json"
        vault.storeFinding(findingsName, FindingsJsonEmitter.emit(
            scan.findings, caseName, now, corpusSha512 = scan.seal.sha512,
            extraRecords = scan.g3Registry?.allRecords().orEmpty().map { FindingsJsonEmitter.fromContractRecord(it) }
        ))
        val report = signReport(ReportGenerator.generate(
            scan.findings, caseName, now, findingsJsonPath = File(vault.findings, findingsName).absolutePath
        ))
        vault.storeReport(report)
        // PDF export may fail independently; retain the new report snapshot and extraction.
        return try {
            val file = writePdf(report, source.fileName)
            check(file.isFile && file.length() > 0) { "Report PDF was not saved." }
            PdfRetryResult(record, report, file,
                "New PDF-only OCR retry report saved. Earlier sealed reports and original evidence are unchanged.")
        } catch (_: Exception) {
            PdfRetryResult(record, report, message =
                "Retry findings and report snapshot saved, but the new PDF could not be exported. Check storage and retry export. Earlier reports are unchanged.")
        }
    }
}
