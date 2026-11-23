package com.verumomnis.forensic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.engine.SavedPdfPageSearchGroup
import com.verumomnis.forensic.engine.searchSavedPdfPages
import com.verumomnis.forensic.model.DocumentExtractionCoverage
import com.verumomnis.forensic.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only inline search; selecting a hit opens the exact saved extraction page. */
@Composable
internal fun SavedPdfPageSearchPanel(
    records: List<DocumentExtractionCoverage>,
    onOpenPage: (DocumentExtractionCoverage, Int) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var groups by remember(query, records) { mutableStateOf<List<SavedPdfPageSearchGroup>?>(null) }
    LaunchedEffect(query, records) {
        groups = withContext(Dispatchers.Default) { searchSavedPdfPages(records, query) }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Search saved PDF text", color = VoHeading, style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Name or phrase") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = VoTextPrimary,
                unfocusedTextColor = VoTextPrimary,
                focusedBorderColor = VoAccentBlue,
                unfocusedBorderColor = VoTextMuted,
                focusedLabelColor = VoAccentBlue,
                unfocusedLabelColor = VoTextMuted,
                cursorColor = VoAccentBlue
            )
        )
        Text(
            "Searches saved extraction text only; no OCR is run. Missing matches do not prove a name or phrase is absent from unread or misread pages.",
            color = VoTextMuted, fontSize = 11.sp
        )
        val result = groups
        when {
            query.isBlank() -> Text("Enter a name or phrase to find its original page.", color = VoTextMuted)
            result == null -> Text("Searching saved text…", color = VoTextMuted)
            result.isEmpty() -> Text("No matches in saved page text.", color = VoTextMuted)
            else -> {
                Text(
                    "${result.sumOf { it.matches.size }} matching page(s) across ${result.size} saved extraction(s)",
                    color = VoAccentBlue, fontSize = 12.sp
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)
                        .testTag("saved-pdf-search-results"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    result.forEachIndexed { groupIndex, group ->
                        val record = group.record
                        val attempt = record.coverage.retryAttempt
                        item(key = "header-$groupIndex") {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(record.fileName, color = VoTextPrimary, fontSize = 13.sp)
                                Text(
                                    if (attempt == null) "Original extraction · revision ${record.revision}"
                                    else "Retry ${attempt.id} · ${attempt.createdAt} · revision ${record.revision}",
                                    color = VoAccentBlue, fontSize = 11.sp
                                )
                                Text("SHA-512 ${record.sha512.take(16)}…", color = VoTextMuted, fontSize = 10.sp)
                            }
                        }
                        items(group.matches, key = { "$groupIndex-page-${it.originalPage}" }) { match ->
                            Surface(
                                onClick = { onOpenPage(record, match.originalPage) },
                                color = VoNavy1,
                                modifier = Modifier.fillMaxWidth().semantics {
                                    contentDescription = "Open ${record.fileName}, " +
                                        (attempt?.let { "retry ${it.id}" } ?: "original extraction revision ${record.revision}") +
                                        ", original page ${match.originalPage}"
                                }
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "Original page ${match.originalPage} · ${match.occurrences} match(es)",
                                        color = VoAccentBlue, fontSize = 12.sp
                                    )
                                    Text(
                                        buildAnnotatedString {
                                            append(match.snippet)
                                            addStyle(
                                                SpanStyle(color = VoHeading, background = VoNavy2),
                                                match.highlightStart, match.highlightEnd
                                            )
                                        },
                                        color = VoTextPrimary, fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
