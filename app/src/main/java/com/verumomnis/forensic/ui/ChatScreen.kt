package com.verumomnis.forensic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoSurface
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary

/** Suggested first questions — each maps to a real engine query. */
private val SuggestedPrompts = listOf(
    "What contradictions were found?",
    "Show me the timeline",
    "Which laws apply?"
)

@Composable
fun ChatScreen(state: UiState, viewModel: VerumViewModel, onPlus: () -> Unit = {}) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val thinking = state.researching || state.scanning
    // "Empty" means the user has not said anything yet — only the welcome
    // bubble (and engine notices) are present.
    val showSuggestions = state.chat.none { it.fromUser }

    LaunchedEffect(state.chat.size, thinking) {
        if (state.chat.isNotEmpty()) listState.animateScrollToItem(state.chat.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.chat) { msg -> ChatBubble(msg) }

            if (showSuggestions) {
                item {
                    SuggestedPromptsCard(
                        prompts = SuggestedPrompts,
                        onPick = { viewModel.sendChat(it) }
                    )
                }
            }
            if (thinking) {
                item { ThinkingBubble() }
            }
        }

        if (state.sealStage != SealStage.IDLE) {
            SealProgressCard(state.sealStage)
        }
        if (state.pendingFiles.isNotEmpty()) {
            PendingPreviewCard(
                previews = state.pendingFiles,
                onConfirm = { viewModel.confirmAndSeal() },
                onCancel = { viewModel.clearPendingFiles() }
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPlus,
                modifier = Modifier.size(46.dp).clip(CircleShape).background(VoGold)
            ) { Icon(Icons.Filled.Add, contentDescription = "Add sealed action", tint = VoBackground) }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Verum Omnis…") },
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                enabled = input.isNotBlank(),
                onClick = {
                    val text = input
                    if (text.isNotBlank()) {
                        if (Regex("draft.*email", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                            viewModel.sendChat(text); viewModel.draftAndSendEmail("admin@verumglobal.foundation", "Sealed forensic report")
                        } else if (Regex("deep research|research", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                            viewModel.sendChat(text); viewModel.deepResearch()
                        } else {
                            viewModel.sendChat(text)
                        }
                    }
                    input = ""
                }
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (input.isNotBlank()) VoGold else VoTextMuted.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun SuggestedPromptsCard(prompts: List<String>, onPick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoSurface, RoundedCornerShape(12.dp))
            .border(1.dp, VoBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            "TRY ASKING",
            color = VoGold,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(8.dp))
        prompts.forEach { prompt ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, VoBorder, RoundedCornerShape(10.dp))
                    .clickable { onPick(prompt) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(prompt, color = VoTextPrimary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .background(VoSurface, RoundedCornerShape(16.dp))
                .border(1.dp, VoBorder, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = VoGold, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Verum Omnis is thinking…", color = VoTextMuted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SealProgressCard(stage: SealStage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(VoSurface, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(stage.label, color = VoTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { stage.progress },
            modifier = Modifier.fillMaxWidth(),
            color = VoGold,
            trackColor = VoBackground
        )
    }
}

@Composable
private fun PendingPreviewCard(
    previews: List<PendingFilePreview>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(VoSurface, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            "READY TO SEAL",
            color = VoGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(8.dp))
        previews.forEach { preview ->
            // Primary line: name · size. Raw SHA-512 lives behind Details.
            Text(preview.fileName, color = VoTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "%.1f MB".format(preview.sizeBytes / (1024f * 1024f)),
                color = VoTextMuted, fontSize = 11.sp
            )
            if (preview.displayText.isNotBlank()) {
                Text(
                    preview.displayText,
                    color = VoTextMuted, fontSize = 11.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            HashDetailsExpander(label = "SHA-512", hash = preview.sha512)
            Spacer(Modifier.height(8.dp))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onCancel) { Text("Cancel", color = VoTextMuted) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = VoGold)) {
                Text("Seal", color = VoBackground)
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val alignment = if (msg.fromUser) Alignment.End else Alignment.Start
    val bubbleModifier = if (msg.fromUser) {
        // User: subtle blue tint.
        Modifier.background(VoAccentBlue.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
    } else {
        // Assistant: navy surface + 25% gold border.
        Modifier
            .background(VoSurface, RoundedCornerShape(16.dp))
            .border(1.dp, VoBorder, RoundedCornerShape(16.dp))
    }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .then(bubbleModifier)
                .padding(12.dp)
        ) {
            Column {
                Text(msg.author, color = VoGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(msg.text, color = VoTextPrimary, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}
