package com.verumomnis.forensic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.model.Contradiction
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

/**
 * Research Results Screen - Displays Gemma 3 findings including contradictions
 * and suggested rules. User can review and provide verification feedback.
 */
@Composable
fun ResearchResultsScreen(
    sessionId: String,
    narrative: String,
    contradictionsFound: Int,
    newRulesSuggested: Int,
    confidence: String,
    verificationUrl: String,
    onSubmitFeedback: (Boolean, List<String>, String) -> Unit = { _, _, _ -> },
    onExportReport: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }
    var feedbackNotes by remember { mutableStateOf("") }
    var userApproved by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoBackground)
    ) {
        // Header
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Research Complete",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = VoTextPrimary
            )
            Text(
                text = "Session: $sessionId",
                fontSize = 12.sp,
                color = VoTextMuted,
                fontFamily = JetBrainsMono,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Confidence Badge
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    when (confidence) {
                        "VERY_HIGH" -> VoGreen
                        "HIGH" -> VoGold
                        "MODERATE" -> VoAccentBlue
                        else -> VoTextMuted
                    }
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Confidence: $confidence",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = VoBackground
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Bar
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            containerColor = VoBackground,
            contentColor = VoGold,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    height = 3.dp,
                    color = VoGold
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Narrative", fontSize = 12.sp)
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Contradictions ($contradictionsFound)", fontSize = 12.sp)
                    }
                }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rules ($newRulesSuggested)", fontSize = 12.sp)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(1.dp))

        // Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> ResearchNarrativeTab(narrative)
                1 -> ResearchContradictionsTab(contradictionsFound)
                2 -> ResearchRulesTab(newRulesSuggested)
            }
        }

        // Action Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Button(
                onClick = { showFeedbackDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VoGold)
            ) {
                Icon(Icons.Filled.ThumbUp, contentDescription = null, tint = VoBackground)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Submit Verification Feedback", fontWeight = FontWeight.Bold, color = VoBackground)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onExportReport,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VoAccentBlue)
            ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export Sealed Report", fontSize = 14.sp)
            }
        }
    }

    // Feedback Dialog
    if (showFeedbackDialog) {
        FeedbackDialog(
            onSubmit = { approved, corrections ->
                onSubmitFeedback(approved, corrections, feedbackNotes)
                showFeedbackDialog = false
            },
            onDismiss = { showFeedbackDialog = false },
            feedbackNotes = feedbackNotes,
            onNotesChange = { feedbackNotes = it }
        )
    }
}

@Composable
private fun ResearchNarrativeTab(narrative: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = VoSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Research Narrative",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = VoGold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                text = narrative,
                fontSize = 13.sp,
                color = VoTextPrimary,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun ResearchContradictionsTab(count: Int) {
    Column {
        repeat(count.coerceAtMost(3)) { idx ->
            ContradictionCard(
                number = idx + 1,
                title = "Contradiction ${idx + 1}",
                respondent = "Person Name",
                evidenceAnchor = "Doc-001 p12",
                confidence = when (idx) {
                    0 -> "VERY_HIGH"
                    1 -> "HIGH"
                    else -> "MODERATE"
                },
                onClick = { /* Detail view */ }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (count > 3) {
            Text(
                text = "+${count - 3} more contradictions",
                fontSize = 12.sp,
                color = VoAccentBlue,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun ResearchRulesTab(count: Int) {
    Column {
        repeat(count.coerceAtMost(3)) { idx ->
            RuleCard(
                number = idx + 1,
                ruleName = "Rule ${idx + 1}: Pattern Detection",
                pattern = "Wire transfer to offshore account with shell company",
                confidence = when (idx) {
                    0 -> "VERY_HIGH"
                    1 -> "HIGH"
                    else -> "MODERATE"
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (count > 3) {
            Text(
                text = "+${count - 3} more rules will be generated",
                fontSize = 12.sp,
                color = VoAccentBlue,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun ContradictionCard(
    number: Int,
    title: String,
    respondent: String,
    evidenceAnchor: String,
    confidence: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = VoSurfaceAlt)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VoTextPrimary
                    )
                    Text(
                        text = respondent,
                        fontSize = 11.sp,
                        color = VoTextMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                ConfidenceBadge(confidence)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = VoAccentBlue,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = evidenceAnchor,
                    fontSize = 11.sp,
                    color = VoAccentBlue,
                    fontFamily = JetBrainsMono
                )
            }
        }
    }
}

@Composable
private fun RuleCard(
    number: Int,
    ruleName: String,
    pattern: String,
    confidence: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = VoSurfaceAlt)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ruleName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VoTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                ConfidenceBadge(confidence)
            }
            Text(
                text = pattern,
                fontSize = 12.sp,
                color = VoTextMuted,
                modifier = Modifier.padding(top = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                when (confidence) {
                    "VERY_HIGH" -> VoGreen
                    "HIGH" -> VoGold
                    "MODERATE" -> VoAccentBlue
                    else -> VoTextMuted
                }
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = confidence.replace("_", " "),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = VoBackground
        )
    }
}

@Composable
private fun FeedbackDialog(
    onSubmit: (Boolean, List<String>) -> Unit,
    onDismiss: () -> Unit,
    feedbackNotes: String,
    onNotesChange: (String) -> Unit
) {
    var userApproved by remember { mutableStateOf(true) }
    var corrections by remember { mutableStateOf(emptyList<String>()) }
    var newCorrection by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verification Feedback", color = VoTextPrimary) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = userApproved,
                        onCheckedChange = { userApproved = it },
                        colors = CheckboxDefaults.colors(checkedColor = VoGreen)
                    )
                    Text("I approve these findings", color = VoTextPrimary)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Comments",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VoGold
                )
                TextField(
                    value = feedbackNotes,
                    onValueChange = onNotesChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = VoSurfaceAlt,
                        unfocusedContainerColor = VoSurfaceAlt,
                        focusedTextColor = VoTextPrimary
                    ),
                    placeholder = { Text("Add any feedback or corrections...", color = VoTextMuted) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(userApproved, corrections) },
                colors = ButtonDefaults.buttonColors(containerColor = VoGold)
            ) {
                Text("Submit", color = VoBackground)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VoTextMuted)
            }
        },
        containerColor = VoSurface
    )
}
