package com.verumomnis.forensic.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.verumomnis.forensic.model.DocumentExtractionCoverage
import com.verumomnis.forensic.model.PageExtraction
import com.verumomnis.forensic.ui.theme.*
import kotlinx.coroutines.CancellationException

@Composable
internal fun PdfPageComparisonDialog(
    record: DocumentExtractionCoverage,
    page: PageExtraction,
    loadPreview: suspend (DocumentExtractionCoverage, Int) -> Bitmap,
    onDismiss: () -> Unit
) {
    var image by remember(record, page.page) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(record, page.page) { mutableStateOf(false) }
    LaunchedEffect(record, page.page) {
        try {
            image = loadPreview(record, page.page)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp), color = VoNavy2, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Original page ${page.page} · saved text", color = VoHeading, fontSize = 18.sp)
                Text(record.fileName, color = VoTextPrimary, fontSize = 12.sp)
                Text("Read-only comparison. No OCR is run; original evidence and saved text are unchanged.",
                    color = VoTextMuted, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 500.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text("ORIGINAL · PAGE ${page.page}", color = VoAccentBlue, fontSize = 11.sp)
                        when {
                            failed -> Text("Original page unavailable. The source may be missing, changed, or unreadable. Saved text is still shown; no evidence was changed.",
                                color = VoGold, fontSize = 12.sp)
                            image == null -> CircularProgressIndicator(color = VoGold)
                            else -> Image(requireNotNull(image).asImageBitmap(),
                                contentDescription = "Original PDF page ${page.page}",
                                modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text("SAVED TEXT · PAGE ${page.page}", color = VoAccentBlue, fontSize = 11.sp)
                        Text("Text source: ${when (page.status) {
                            com.verumomnis.forensic.model.PageReadStatus.TEXT_LAYER -> "Embedded PDF text"
                            com.verumomnis.forensic.model.PageReadStatus.OCR -> "On-device OCR"
                            else -> "No extracted text saved"
                        }}", color = VoTextMuted, fontSize = 11.sp)
                        SelectionContainer {
                            Text(page.extractedText?.takeIf { it.isNotEmpty() }
                                ?: "No saved page text is available to preview.",
                                color = VoTextPrimary, fontSize = 13.sp)
                        }
                    }
                }
                TextButton(onClick = onDismiss) { Text("Back to pages", color = VoAccentBlue) }
            }
        }
    }
}
