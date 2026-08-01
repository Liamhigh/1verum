package com.verumomnis.forensic.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoGreen
import com.verumomnis.forensic.ui.theme.VoSurface
import com.verumomnis.forensic.ui.theme.VoSurfaceAlt
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary
import java.time.Instant

/**
 * Research Progress Screen - Shows real-time progress of Gemma 3 analysis.
 * Displays which stage of analysis is currently running.
 */
@Composable
fun ResearchProgressScreen(
    sessionId: String,
    query: String,
    startedAt: Instant = Instant.now(),
    onCancel: () -> Unit = {}
) {
    var elapsedSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            elapsedSeconds++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated loading indicator
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(VoSurfaceAlt.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            val rotation = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                rotation.animateTo(
                    targetValue = 360f,
                    animationSpec = infiniteRepeatingAnimation(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    )
                )
            }

            Icon(
                Icons.Filled.Psychology,
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .scale(rotation.value / 360f),
                tint = VoGold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Gemma 3 Analyzing...",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = VoTextPrimary
        )

        Text(
            text = "$elapsedSeconds seconds elapsed",
            fontSize = 14.sp,
            color = VoTextMuted,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Analysis stages
        AnalysisStageIndicator(
            stage = 1,
            name = "Vault Loading",
            description = "Retrieving evidence documents",
            isActive = elapsedSeconds < 10,
            isComplete = elapsedSeconds >= 10
        )

        AnalysisStageIndicator(
            stage = 2,
            name = "Query Processing",
            description = "Parsing research query with Gemma 3",
            isActive = elapsedSeconds in 10..25,
            isComplete = elapsedSeconds > 25
        )

        AnalysisStageIndicator(
            stage = 3,
            name = "Evidence Synthesis",
            description = "Synthesizing narrative from documents",
            isActive = elapsedSeconds in 26..45,
            isComplete = elapsedSeconds > 45
        )

        AnalysisStageIndicator(
            stage = 4,
            name = "Contradiction Detection",
            description = "Extracting contradictions from narrative",
            isActive = elapsedSeconds in 46..60,
            isComplete = elapsedSeconds > 60
        )

        AnalysisStageIndicator(
            stage = 5,
            name = "Rule Inference",
            description = "Inferring new contradiction detection rules",
            isActive = elapsedSeconds > 60,
            isComplete = false
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Query display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = VoSurface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Research Query",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VoGold
                )
                Text(
                    text = query,
                    fontSize = 13.sp,
                    color = VoTextPrimary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Cancel button
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VoTextMuted)
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cancel Research", fontSize = 14.sp)
        }
    }
}

@Composable
private fun AnalysisStageIndicator(
    stage: Int,
    name: String,
    description: String,
    isActive: Boolean,
    isComplete: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Stage indicator circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isComplete -> VoGreen
                        isActive -> VoGold
                        else -> VoSurfaceAlt
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isComplete) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = VoBackground,
                    modifier = Modifier.size(24.dp)
                )
            } else if (isActive) {
                Text(
                    text = "…",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = VoBackground,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = stage.toString(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = VoTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive || isComplete) VoTextPrimary else VoTextMuted
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = VoTextMuted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
