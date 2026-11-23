package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.DocumentExtractionCoverage
import com.verumomnis.forensic.model.ForensicReport
import com.verumomnis.forensic.model.PdfExtractionCoverage
import com.verumomnis.forensic.model.PdfRetryAttempt
import com.verumomnis.forensic.pdf.SealedOutputNames
import com.verumomnis.forensic.vault.EvidenceVault
import java.io.File
import java.time.Instant
import java.util.UUID

data class PdfRetryResult(
    val record: DocumentExtractionCoverage,
    val report: ForensicReport? = null,
    val reportFile: File? = null,
    val message: String,
) {
    val stage: PdfRetryRecoveryStage
        get() = when {
            reportFile?.isFile == true && reportFile.length() > 0 -> PdfRetryRecoveryStage.PDF_READY
            report != null -> PdfRetryRecoveryStage.FINDINGS_READY
            else -> PdfRetryRecoveryStage.EXTRACTION_ONLY
        }

    val canResumeReport: Boolean
        get() = record.coverage.pages.none { it.extractedChars > 0 && it.extractedText == null } &&
            record.coverage.pages.any { !it.extractedText.isNullOrBlank() }
}

enum class PdfRetryRecoveryStage { EXTRACTION_ONLY, FINDINGS_READY, PDF_READY }

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

        return generateReport(record, extracted.text, now)
    }

    /** Rehydrate a saved attempt's report/PDF state without reading the source PDF or running OCR. */
    fun recoverSaved(
        record: DocumentExtractionCoverage,
        savedReports: List<ForensicReport> = vault.listReports()
    ): PdfRetryResult {
        requireSavedRetry(record)
        val report = findReport(record, savedReports)
        val file = report?.let { findReportPdf(record.fileName, it.reference) }
        return PdfRetryResult(
            record = record,
            report = report,
            reportFile = file,
            message = when {
                file != null -> "Retry report PDF is ready. This is a separate PDF-only analysis."
                report != null -> "Retry findings and report are saved. The PDF can be exported again."
                record.coverage.pages.any { !it.extractedText.isNullOrBlank() } ->
                    "OCR retry pages are saved. Resume report generation from the saved text; OCR will not run again."
                else -> "OCR retry attempt is saved, but it contains no reusable page text. No findings were generated."
            }
        )
    }

    /** Finish a report interrupted after page text was saved; this never invokes the OCR callback. */
    fun resumeReport(record: DocumentExtractionCoverage): PdfRetryResult {
        val saved = recoverSaved(record)
        if (saved.report != null) return saved
        val attempt = requireNotNull(record.coverage.retryAttempt)
        val text = savedText(record)
        if (text.isBlank()) return saved
        val createdAt = Instant.parse(attempt.createdAt)
        return generateReport(record, text, createdAt)
    }

    /** Export a persisted retry report, reusing its snapshot instead of regenerating findings. */
    fun retryReportPdf(record: DocumentExtractionCoverage): PdfRetryResult {
        val saved = recoverSaved(record)
        val report = saved.report ?: return saved
        if (saved.stage == PdfRetryRecoveryStage.PDF_READY) return saved
        return try {
            val file = vault.retainReportPdf(writePdf(report, record.fileName))
            check(file.isFile && file.length() > 0) { "Report PDF was not saved." }
            linkRetryReport(record, report, file)
            saved.copy(
                reportFile = file,
                message = "New PDF-only OCR retry report exported. Earlier reports and original evidence are unchanged."
            )
        } catch (_: Exception) {
            saved.copy(
                message = "Retry findings and report snapshot are saved, but the new PDF could not be exported. Check storage and retry export. Earlier reports are unchanged."
            )
        }
    }

    private fun generateReport(
        record: DocumentExtractionCoverage,
        text: String,
        now: Instant
    ): PdfRetryResult {
        val attempt = requireNotNull(record.coverage.retryAttempt)
        ensureRetryLink(record)
        val caseName = "OCR retry ${attempt.id}"
        val scan = ForensicService.scan(listOf(EvidenceDocument(
            evidenceId = record.evidenceId.ifBlank { "DOC001" },
            fileName = record.fileName,
            type = "pdf",
            text = text,
            sha512 = record.sha512,
            extractionCoverage = record.coverage
        )), now = now)
        val findingsName = "ocr_retry_${attempt.id}_findings.json"
        val findingsFile = vault.storeFindingUnique(findingsName, FindingsJsonEmitter.emit(
            scan.findings, caseName, now, corpusSha512 = scan.seal.sha512,
            extraRecords = scan.g3Registry?.allRecords().orEmpty().map { FindingsJsonEmitter.fromContractRecord(it) }
        ))
        val report = signReport(ReportGenerator.generate(
            scan.findings,
            caseName,
            now,
            findingsJsonPath = findingsFile.absolutePath
        ))
        vault.storeReport(report)
        linkRetryReport(record, report)
        return retryReportPdf(record, report)
    }

    private fun retryReportPdf(record: DocumentExtractionCoverage, report: ForensicReport): PdfRetryResult {
        val saved = PdfRetryResult(record, report = report, message = "Retry findings and report snapshot are saved.")
        return try {
            val file = vault.retainReportPdf(writePdf(report, record.fileName))
            check(file.isFile && file.length() > 0) { "Report PDF was not saved." }
            linkRetryReport(record, report, file)
            saved.copy(
                reportFile = file,
                message = "New PDF-only OCR retry report saved. Earlier sealed reports and original evidence are unchanged."
            )
        } catch (_: Exception) {
            saved.copy(
                message = "Retry findings and report snapshot saved, but the new PDF could not be exported. Check storage and retry export. Earlier reports are unchanged."
            )
        }
    }

    private fun ensureRetryLink(record: DocumentExtractionCoverage): com.verumomnis.forensic.model.LinkedDocumentRecord {
        val attempt = requireNotNull(record.coverage.retryAttempt)
        val id = "retry-${attempt.id}"
        return vault.listDocumentLinks().firstOrNull { it.id == id }
            ?: com.verumomnis.forensic.model.LinkedDocumentRecord(
                id, record.fileName, record.sha512, attempt.createdAt,
                vault.documentPath(File(vault.evidenceRaw, "pdf_source_${record.sha512}.pdf")),
                forensicRequested = true
            ).also { vault.saveDocumentLink(it) }
    }

    private fun linkRetryReport(record: DocumentExtractionCoverage, report: ForensicReport, file: File? = null) {
        val link = ensureRetryLink(record)
        vault.saveDocumentLink(link.copy(
            reportReference = report.reference,
            reportPdfPath = file?.let(vault::documentPath) ?: link.reportPdfPath
        ))
    }

    private fun requireSavedRetry(record: DocumentExtractionCoverage) {
        requireNotNull(record.coverage.retryAttempt) { "This record is not an OCR retry attempt." }
        check(vault.listPdfExtractions().any { it == record }) {
            "Saved retry page coverage is unavailable. Re-import the original PDF."
        }
    }

    private fun findReport(record: DocumentExtractionCoverage, reports: List<ForensicReport>): ForensicReport? {
        val attemptId = record.coverage.retryAttempt?.id ?: return null
        return reports.firstOrNull { report ->
            report.extractionCoverage.any { saved ->
                saved.sha512.equals(record.sha512, ignoreCase = true) &&
                    saved.coverage.retryAttempt?.id == attemptId
            }
        }
    }

    private fun findReportPdf(sourceName: String, reportReference: String): File? {
        val expectedName = SealedOutputNames.forensicReport(sourceName, reportReference)
        val stem = expectedName.removeSuffix(".pdf")
        val suffixPattern = Regex("${Regex.escape(stem)}_\\d+\\.pdf")
        return vault.reportsSealed.listFiles().orEmpty()
            .filter { file ->
                file.isFile && file.length() > 0 &&
                    (file.name == expectedName || suffixPattern.matches(file.name))
            }
            .sortedWith(compareBy<File> { if (it.name == expectedName) 0 else 1 }.thenBy { it.name })
            .firstOrNull()
    }

    private fun savedText(record: DocumentExtractionCoverage): String {
        check(record.coverage.pages.none { it.extractedChars > 0 && it.extractedText == null }) {
            "Saved retry text is incomplete. Re-import the original PDF before resuming."
        }
        return record.coverage.pages.sortedBy { it.page }.joinToString("\n") { page ->
            val text = page.extractedText.orEmpty()
            if (page.status == com.verumomnis.forensic.model.PageReadStatus.OCR && text.isNotBlank()) {
                "[OCR] $text"
            } else text
        }.trim()
    }
}
