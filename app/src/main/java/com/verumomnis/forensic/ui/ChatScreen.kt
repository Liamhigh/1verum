package com.verumomnis.forensic.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoSurface
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.chat) { msg -> ChatBubble(msg) }
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
private fun ChatBubble(msg: ChatMessage) {
    val alignment = if (msg.fromUser) Alignment.End else Alignment.Start
    val bubble = if (msg.fromUser) VoAccentBlue else VoSurface
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(bubble, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Column {
                Text(msg.author, color = VoGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(msg.text, color = VoTextPrimary, fontSize = 13.sp)
            }
        }
    }
}
