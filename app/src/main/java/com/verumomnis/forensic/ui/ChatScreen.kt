package com.verumomnis.forensic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.R
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoBlueBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoHeading
import com.verumomnis.forensic.ui.theme.VoPanel
import com.verumomnis.forensic.ui.theme.VoPanelBorder
import com.verumomnis.forensic.ui.theme.VoSurface
import com.verumomnis.forensic.ui.theme.VoTextBody
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary

@Composable
fun ChatScreen(state: UiState, viewModel: VerumViewModel, onPlus: () -> Unit = {}) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.chat.size) {
        if (state.chat.isNotEmpty()) listState.animateScrollToItem(state.chat.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.chat) { msg -> ChatBubble(msg) }
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
                placeholder = { Text("Message Verum Omnis…", color = VoTextMuted, fontSize = 14.sp) },
                textStyle = LocalTextStyle.current.copy(color = VoTextPrimary, fontSize = 14.sp),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VoGold,
                    unfocusedBorderColor = VoBlueBorder,
                    focusedContainerColor = VoSurface,
                    unfocusedContainerColor = VoSurface,
                    cursorColor = VoGold
                ),
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
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
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = VoGold)
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
            Text(preview.fileName, color = VoTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "%.1f MB · SHA-512 ${preview.sha512.take(12)}…".format(preview.sizeBytes / (1024f * 1024f)),
                color = VoTextMuted, fontSize = 11.sp
            )
            Text(
                preview.displayText,
                color = VoTextMuted, fontSize = 11.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
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

/**
 * One chat row, matching the mock-up: the user speaks on the right in a blue
 * bubble; Verum Omnis answers on the left behind the blue-circle badge.
 *
 * The avatar is the identity — there is no author label, because every non-user
 * message is [VERUM_OMNIS] and the underlying model is never disclosed (spec §1).
 * Corner radii are the mock-up's asymmetric bubbles: the corner nearest the
 * speaker is squared off (3.dp).
 */
@Composable
private fun ChatBubble(msg: ChatMessage) {
    if (msg.fromUser) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(
                        VoAccentBlue.copy(alpha = 0.18f),
                        RoundedCornerShape(14.dp, 14.dp, 3.dp, 14.dp)
                    )
                    .border(
                        1.dp,
                        VoBlueBorder.copy(alpha = 0.6f),
                        RoundedCornerShape(14.dp, 14.dp, 3.dp, 14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(msg.text, color = VoHeading, fontSize = 13.sp)
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            Image(
                painter = painterResource(R.drawable.vo_badge),
                contentDescription = VERUM_OMNIS,
                modifier = Modifier.size(26.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(VoPanel, RoundedCornerShape(3.dp, 14.dp, 14.dp, 14.dp))
                    .border(
                        1.dp,
                        VoPanelBorder,
                        RoundedCornerShape(3.dp, 14.dp, 14.dp, 14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(msg.text, color = VoTextBody, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
    }
}
