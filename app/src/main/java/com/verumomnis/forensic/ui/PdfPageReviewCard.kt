package com.verumomnis.forensic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.engine.PdfOcrExtractor
import com.verumomnis.forensic.engine.PdfRetryRecoveryStage
import com.verumomnis.forensic.model.DocumentExtractionCoverage
import com.verumomnis.forensic.pdf.SealedPdfExporter
import com.verumomnis.forensic.ui.theme.*

/** Shared by ingest and report so even an all-unread PDF has an actionable review path. */
@Composable
fun PdfPageReviewCard(state: UiState, viewModel: VerumViewModel) {
    LaunchedEffect(state.pendingFiles.size, state.sealBusy) { viewModel.loadPdfPageReview() }
    var review by remember { mutableStateOf<DocumentExtractionCoverage?>(null) }
    var expandedReports by remember { mutableStateOf(emptySet<String>()) }
    var shareError by remember { mutableStateOf("") }
    val context = LocalContext.current
    val latest = state.pdfReviewRecords.groupBy { it.sha512 }.values.map { records ->
        records.maxWithOrNull(compareBy<DocumentExtractionCoverage> { it.revision }
            .thenBy { it.recordedAt ?: it.coverage.retryAttempt?.createdAt.orEmpty() })!!
    }
    // Keep earlier attempts actionable even when a later re-import becomes the
    // latest extraction snapshot for the same source PDF.
    val visibleRecords = (latest + state.pdfReviewRecords.filter { it.coverage.retryAttempt != null })
        .distinctBy { record ->
            record.coverage.retryAttempt?.id?.let { "retry:$it" } ?: "${record.sha512}:${record.revision}"
        }
    if (latest.isEmpty() && state.pdfRetryMessage.isBlank()) return
    val busy = state.pdfRetryBusy || state.sealBusy || state.scanning || state.reportPdfGenerating
    VoCard(title = "PDF PAGE REVIEW & RETRY", icon = Icons.Filled.Description) {
        Text("Review original page numbers and extraction gaps. A retry creates a new, labelled PDF-only analysis; it does not revise earlier sealed reports or re-analyse other case evidence.",
            color = VoTextMuted, fontSize = 12.sp)
        if (state.pdfRetryCanCancel) {
            Spacer(Modifier.height(8.dp))
            VerumSecondaryButton(
                label = "Cancel OCR retry",
                onClick = { viewModel.cancelPdfRetry() },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (state.pdfRetryCancelRequested) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Stopping after the current page. Unprocessed pages will not be marked as read.",
                color = VoGold,
                fontSize = 12.sp
            )
        }
        visibleRecords.forEach { record ->
            val attempt = record.coverage.retryAttempt
            val recovery = attempt?.let { id ->
                state.pdfRetryRecoveries.firstOrNull {
                    it.record.coverage.retryAttempt?.id == id.id
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(record.fileName, color = VoTextPrimary, fontSize = 14.sp)
            Text("${record.coverage.remainingPages.size} remaining page(s) · ${record.coverage.totalPages ?: "unknown"} total · SHA-512 ${record.sha512.take(16)}…",
                color = VoTextMuted, fontSize = 11.sp)
            record.coverage.retryAttempt?.let {
                val attemptLabel = if (it.cancelled) {
                    "CANCELLED · ${it.processedPages.size}/${it.selectedPages.size} selected pages processed"
                } else {
                    "Latest retry: ${it.createdAt} · ${it.id}"
                }
                Text(attemptLabel, color = if (it.cancelled) VoGold else VoAccentBlue, fontSize = 10.sp)
            }
            VerumSecondaryButton(label = "Review pages: ${record.fileName}", enabled = !busy,
                onClick = { review = record }, modifier = Modifier.fillMaxWidth())
            if (attempt != null) {
                val stage = recovery?.stage ?: PdfRetryRecoveryStage.EXTRACTION_ONLY
                val stageLabel = if (attempt.cancelled) {
                    val progress = "${attempt.processedPages.size}/${attempt.selectedPages.size} selected pages processed"
                    val hasUnprocessedPages = attempt.processedPages.size < attempt.selectedPages.size
                    when (stage) {
                        PdfRetryRecoveryStage.EXTRACTION_ONLY -> if (hasUnprocessedPages) {
                            "CANCELLED · partial OCR saved · $progress"
                        } else {
                            "CANCELLED · OCR complete; report stopped · $progress"
                        }
                        PdfRetryRecoveryStage.FINDINGS_READY -> "CANCELLED · saved report · $progress"
                        PdfRetryRecoveryStage.PDF_READY -> "CANCELLED · saved retry PDF · $progress"
                    }
                } else when (stage) {
                    PdfRetryRecoveryStage.EXTRACTION_ONLY -> "OCR pages saved · report not generated"
                    PdfRetryRecoveryStage.FINDINGS_READY -> "Findings and report saved · PDF export pending"
                    PdfRetryRecoveryStage.PDF_READY -> "Retry PDF ready"
                }
                Spacer(Modifier.height(6.dp))
                Text(stageLabel, color = VoGold, fontSize = 11.sp)
                when (stage) {
                    PdfRetryRecoveryStage.EXTRACTION_ONLY -> {
                        if (recovery?.canResumeReport == true) {
                            VerumSecondaryButton(
                                "Resume report generation",
                                enabled = !busy,
                                onClick = { viewModel.resumePdfRetryReport(record) }
                            )
                        } else {
                            Text(
                                "No saved page text is available to resume. Choose pages for another OCR retry.",
                                color = VoTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    PdfRetryRecoveryStage.FINDINGS_READY -> {
                        VerumSecondaryButton(
                            "Retry new PDF export",
                            enabled = !busy,
                            onClick = { viewModel.retryPdfReviewExport(record) }
                        )
                    }
                    PdfRetryRecoveryStage.PDF_READY -> {
                        val file = recovery?.reportFile
                        if (file != null) {
                            VerumSecondaryButton("Share new retry PDF", onClick = {
                                shareError = try {
                                    SealedPdfExporter(context).share(file)
                                    ""
                                } catch (_: Exception) {
                                    "The new PDF could not be shared. It is still saved in the vault."
                                }
                            })
                        }
                    }
                }
                recovery?.report?.let { report ->
                    Text("NEW OCR RETRY REPORT · ${report.reference}", color = VoGold, fontSize = 12.sp)
                    Text("Source SHA-512: ${record.sha512}", color = VoTextMuted, fontSize = 10.sp)
                    TextButton(onClick = {
                        val id = attempt.id
                        expandedReports = if (id in expandedReports) expandedReports - id else expandedReports + id
                    }) {
                        Text(
                            if (attempt.id in expandedReports) "Hide new report" else "Read new retry report",
                            color = VoAccentBlue
                        )
                    }
                    if (attempt.id in expandedReports) {
                        Text(report.body, color = VoTextPrimary, fontSize = 12.sp)
                    }
                }
            }
        }
        if (state.pdfRetryMessage.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(state.pdfRetryMessage, color = VoTextPrimary, fontSize = 12.sp)
        }
        if (shareError.isNotBlank()) Text(shareError, color = VoRed, fontSize = 12.sp)
    }
    review?.let { record ->
        PdfPageReviewDialog(record, busy, onDismiss = { review = null }, onRetry = { pages ->
            viewModel.retryPdfPages(record, pages)
            review = null
        })
    }
}

@Composable
internal fun PdfPageReviewDialog(
    record: DocumentExtractionCoverage,
    busy: Boolean,
    onDismiss: () -> Unit,
    onRetry: (List<Int>) -> Unit
) {
    var selected by remember(record) { mutableStateOf(setOf<Int>()) }
    var expandedTextPages by remember(record) { mutableStateOf(emptySet<Int>()) }
    val coverage = record.coverage
    val limit = PdfOcrExtractor.DEFAULT_MAX_OCR_PAGES
    val remaining = coverage.remainingPages.map { it.page }.toSet()
    val usable = coverage.totalPages != null && coverage.totalPages > 0 &&
        coverage.pages.map { it.page }.sorted() == (1..coverage.totalPages).toList() &&
        coverage.pages.none { it.extractedChars > 0 && it.extractedText == null }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = VoNavy2,
        title = { Text("Review PDF pages", color = VoHeading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(record.fileName, color = VoTextPrimary)
                Text(
                    "Saved page text is read-only. Check embedded PDF text and on-device OCR separately before resuming report generation.",
                    color = VoTextMuted,
                    fontSize = 12.sp
                )
                Text("${selected.size} selected · at most $limit pages per fresh attempt. OCR stays on this device and may still fail or miss content.",
                    color = VoTextMuted, fontSize = 12.sp)
                if (!usable) Text("Page count or saved page text is unavailable. Re-import the original PDF to establish coverage before retrying.",
                    color = VoGold, fontSize = 12.sp)
                coverage.documentFailure?.let { Text(it, color = VoGold, fontSize = 12.sp) }
                if (remaining.isEmpty() && usable) Text("No unread, failed or skipped pages remain. OCR accuracy still needs manual review.", color = VoTextMuted)
                LazyColumn(Modifier.heightIn(max = 330.dp)) {
                    items(coverage.pages.sortedBy { it.page }, key = { it.page }) { page ->
                        Row(Modifier.fillMaxWidth()) {
                            Checkbox(
                                modifier = Modifier.semantics { contentDescription = "Select original page ${page.page}, ${page.status}" },
                                checked = page.page in selected,
                                enabled = usable && !busy && page.page in remaining && (page.page in selected || selected.size < limit),
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + page.page else selected - page.page
                                }
                            )
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text("Page ${page.page} · ${page.status}", color = VoTextPrimary, fontSize = 13.sp)
                                val textSource = when (page.status) {
                                    com.verumomnis.forensic.model.PageReadStatus.TEXT_LAYER -> "Embedded PDF text"
                                    com.verumomnis.forensic.model.PageReadStatus.OCR -> "On-device OCR"
                                    else -> "No extracted text saved"
                                }
                                Text("Text source: $textSource", color = VoAccentBlue, fontSize = 11.sp)
                                Text(page.reason.replace('_', ' '), color = VoTextMuted, fontSize = 11.sp)
                                val savedText = page.extractedText
                                if (!savedText.isNullOrBlank()) {
                                    val expanded = page.page in expandedTextPages
                                    if (savedText.length > 360) {
                                        TextButton(
                                            onClick = {
                                                expandedTextPages = if (expanded) {
                                                    expandedTextPages - page.page
                                                } else {
                                                    expandedTextPages + page.page
                                                }
                                            }
                                        ) {
                                            Text(
                                                if (expanded) "Show less saved text" else "Show full saved text",
                                                color = VoAccentBlue,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Text(
                                        if (savedText.length > 360 && !expanded) {
                                            "${savedText.take(360).trimEnd()}…"
                                        } else {
                                            savedText
                                        },
                                        color = VoTextPrimary,
                                        fontSize = 12.sp
                                    )
                                } else {
                                    Text(
                                        if (page.extractedChars > 0) {
                                            "Text was detected, but this saved record does not contain the text."
                                        } else {
                                            "No saved page text is available to preview."
                                        },
                                        color = VoTextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
                Text("Original bytes and hashes stay unchanged. Previous reports are retained. New findings and a separately sealed report are clearly labelled as a retry.",
                    color = VoTextMuted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = usable && !busy && selected.isNotEmpty(), onClick = { onRetry(selected.sorted()) }) {
                Text("Start fresh OCR retry", color = if (usable && !busy && selected.isNotEmpty()) VoGold else VoTextMuted)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = VoAccentBlue) } }
    )
}
