package com.verumomnis.forensic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoGreen
import com.verumomnis.forensic.ui.theme.VoRed
import com.verumomnis.forensic.ui.theme.VoSurface
import com.verumomnis.forensic.ui.theme.VoSurfaceAlt
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Modern Chat Screen - Enhanced AI Chat Experience
 *
 * Features:
 * - Gemma 3 vs Gemma 4 routing (4 is flagship)
 * - Triple verification with silent Constitutional enforcement
 * - Deep research + normal chat in one interface
 * - Shows which AI is responding and their role
 * - No UI restrictions (Constitution runs backend)
 */

data class ChatMessage(
    val id: String,
    val author: String,
    val aiRole: String?, // "Communicator" (G4), "Forensic" (G3), "Verifier" (G4 again)
    val text: String,
    val timestamp: Instant,
    val isUserMessage: Boolean,
    val confidence: String? = null,
    val isDeepResearch: Boolean = false
)

@Composable
fun ModernChatScreen(
    sessionId: String = "CHAT-${System.currentTimeMillis()}",
    onSendMessage: (String, Boolean) -> Unit = { _, _ -> },
    onRequestDeepResearch: (String) -> Unit = { _ -> }
) {
    var messages by remember { mutableStateOf(emptyList<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var activeAI by remember { mutableStateOf("Gemma 4") }
    val listState = rememberLazyListState()

    // Auto-scroll to latest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoBackground)
    ) {
        // Header with AI status
        ChatHeader(sessionId = sessionId, activeAI = activeAI)

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages) { msg ->
                ChatMessageBubble(message = msg)
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        LoadingIndicator()
                    }
                }
            }
        }

        Divider(modifier = Modifier.fillMaxWidth(), color = VoSurfaceAlt, thickness = 1.dp)

        // Input area with AI selector
        ChatInputArea(
            inputText = inputText,
            onInputChange = { inputText = it },
            onSendMessage = {
                if (inputText.isNotBlank()) {
                    // Detect deep research requests
                    val isDeepResearch = inputText.contains(Regex("research|analyze|investigate", RegexOption.IGNORE_CASE))

                    val newMsg = ChatMessage(
                        id = "MSG-${System.currentTimeMillis()}",
                        author = "You",
                        aiRole = null,
                        text = inputText,
                        timestamp = Instant.now(),
                        isUserMessage = true,
                        isDeepResearch = isDeepResearch
                    )
                    messages = messages + newMsg
                    inputText = ""

                    isLoading = true

                    // Route to appropriate AI
                    if (isDeepResearch) {
                        onRequestDeepResearch(newMsg.text)
                    } else {
                        onSendMessage(newMsg.text, false)
                    }

                    // Simulate AI response after delay (in production, this comes from backend)
                    // For now: Gemma 4 for communicating, Gemma 3 for deep research
                    val responseAI = if (isDeepResearch) "Gemma 3" else "Gemma 4"
                    val responseRole = if (isDeepResearch) "Forensic Analysis" else "Communicator"

                    // Fake response (would come from actual AI in production)
                    messages = messages + ChatMessage(
                        id = "MSG-${System.currentTimeMillis()}-RESP",
                        author = responseAI,
                        aiRole = responseRole,
                        text = generateMockResponse(newMsg.text, responseAI),
                        timestamp = Instant.now(),
                        isUserMessage = false,
                        confidence = if (isDeepResearch) "HIGH" else null
                    )

                    isLoading = false
                    activeAI = responseAI
                }
            },
            activeAI = activeAI,
            isLoading = isLoading
        )
    }
}

@Composable
private fun ChatHeader(sessionId: String, activeAI: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoSurface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Verum Omnis Chat",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = VoTextPrimary
                )
                Text(
                    text = "AI Forensics for Truth",
                    fontSize = 12.sp,
                    color = VoTextMuted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(VoGold)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = activeAI,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = VoBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Constitution status (silently running)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(VoSurfaceAlt.copy(alpha = 0.5f))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = VoGreen,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "Constitution v6.0 • Triple Verification • No Restrictions",
                fontSize = 11.sp,
                color = VoGreen,
                fontFamily = JetBrainsMono
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Session: $sessionId",
            fontSize = 10.sp,
            color = VoTextMuted,
            fontFamily = JetBrainsMono
        )
    }
}

@Composable
private fun ChatMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUserMessage) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUserMessage) {
            // AI avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        when (message.author) {
                            "Gemma 4" -> VoGold
                            "Gemma 3" -> VoAccentBlue
                            else -> VoSurfaceAlt
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (message.author) {
                        "Gemma 4" -> Icons.Filled.Psychology
                        "Gemma 3" -> Icons.Filled.Description
                        else -> Icons.Filled.SmartToy
                    },
                    contentDescription = null,
                    tint = if (message.author == "Gemma 4") VoBackground else VoTextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Card(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUserMessage) VoGold else VoSurface
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!message.isUserMessage) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = message.author,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (message.isUserMessage) VoBackground else VoAccentBlue
                            )
                            if (message.aiRole != null) {
                                Text(
                                    text = message.aiRole,
                                    fontSize = 9.sp,
                                    color = VoTextMuted
                                )
                            }
                        }
                        if (message.confidence != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(VoGreen)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = message.confidence,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VoBackground
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Text(
                    text = message.text,
                    fontSize = 13.sp,
                    color = if (message.isUserMessage) VoBackground else VoTextPrimary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = formatTime(message.timestamp),
                    fontSize = 9.sp,
                    color = if (message.isUserMessage) VoBackground.copy(alpha = 0.7f) else VoTextMuted,
                    fontFamily = JetBrainsMono
                )
            }
        }

        if (message.isUserMessage) {
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun ChatInputArea(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    activeAI: String,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoBackground)
            .padding(12.dp)
    ) {
        // Hint about what you can do
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = VoTextMuted,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "Ask anything. Say 'research' for deep analysis. No restrictions—Constitution runs silently.",
                fontSize = 10.sp,
                color = VoTextMuted,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp)),
                placeholder = {
                    Text(
                        "Message Verum Omnis… (Ask for 'deep research' for analysis)",
                        color = VoTextMuted,
                        fontSize = 12.sp
                    )
                },
                maxLines = 3,
                enabled = !isLoading,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = VoSurface,
                    unfocusedContainerColor = VoSurface,
                    focusedTextColor = VoTextPrimary,
                    unfocusedTextColor = VoTextPrimary,
                    focusedIndicatorColor = VoGold
                )
            )

            IconButton(
                onClick = onSendMessage,
                enabled = inputText.isNotBlank() && !isLoading,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (inputText.isNotBlank() && !isLoading) VoGold else VoSurfaceAlt)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (inputText.isNotBlank() && !isLoading) VoBackground else VoTextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(VoGold),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Psychology,
                contentDescription = null,
                tint = VoBackground,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = "Gemma thinking…",
            fontSize = 12.sp,
            color = VoTextMuted,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

private fun generateMockResponse(userMessage: String, respondingAI: String): String {
    return when {
        userMessage.contains(Regex("research|analyze|investigate", RegexOption.IGNORE_CASE)) && respondingAI == "Gemma 3" ->
            "I've initiated deep forensic analysis on the uploaded evidence. Analyzing for contradictions, timeline gaps, and financial anomalies across multiple jurisdiction frameworks. Results will be anchored to evidence sources with confidence scoring."

        respondingAI == "Gemma 4" ->
            "I'm analyzing this in context of our fraud detection patterns. This appears relevant to your investigation. Would you like me to research specific aspects or cross-reference with your evidence vault?"

        else ->
            "Understood. Processing your request through the forensic pipeline. All findings are being verified against the Constitutional framework to ensure truth over probability."
    }
}

private fun formatTime(instant: Instant): String {
    val formatter = DateTimeFormatter
        .ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}

// Extensions for common UI imports
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.foundation.layout.widthIn
