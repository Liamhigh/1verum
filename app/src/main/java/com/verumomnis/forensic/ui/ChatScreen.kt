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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoPrimary
import com.verumomnis.forensic.ui.theme.VoSurface
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary

@Composable
fun ChatScreen(state: UiState, viewModel: VerumViewModel) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.chat.size) {
        if (state.chat.isNotEmpty()) listState.animateScrollToItem(state.chat.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "AI Legal Strategy & Evidence Chat",
            color = VoTextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(4.dp))
        Text("Communicator: ${state.communicator} · 9-Brain verifier active", color = VoTextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.chat) { msg -> ChatBubble(msg) }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { viewModel.sendChat("Show me the contradictions") }) {
                Icon(Icons.Filled.TravelExplore, contentDescription = null, tint = VoPrimary)
                Spacer(Modifier.width(4.dp))
                Text("Deep Research", color = VoPrimary, fontSize = 12.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about evidence, timeline, legal strategy…") },
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                viewModel.sendChat(input)
                input = ""
            }) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = VoPrimary)
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
                Text(msg.author, color = VoPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(msg.text, color = VoTextPrimary, fontSize = 13.sp)
            }
        }
    }
}
