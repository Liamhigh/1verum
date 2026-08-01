package com.verumomnis.forensic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.engine.HybridForensicService
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
 * Deep Research Entry Point - Gemma 3 Admin Hub Interface.
 * Users enter research queries and select evidence from the vault.
 */
@Composable
fun DeepResearchScreen(
    vaultEvidenceIds: List<String> = emptyList(),
    jurisdiction: String = "SA",
    onStartResearch: (String, List<String>, String) -> Unit = { _, _, _ -> }
) {
    var query by remember { mutableStateOf("") }
    var selectedEvidenceIds by remember { mutableStateOf(emptyList<String>()) }
    var userContext by remember { mutableStateOf("") }
    var showEvidenceSelector by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Deep Research",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = VoTextPrimary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Query the vault with Gemma 3 admin hub. Discover contradictions and evolve the forensic engine.",
            fontSize = 14.sp,
            color = VoTextMuted,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Research Query Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = VoSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Research Query",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VoGold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    placeholder = {
                        Text(
                            "E.g., Find all contradictions about wire transfers to offshore accounts...",
                            color = VoTextMuted
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = VoSurfaceAlt,
                        unfocusedContainerColor = VoSurfaceAlt,
                        focusedTextColor = VoTextPrimary,
                        unfocusedTextColor = VoTextPrimary,
                        focusedIndicatorColor = VoGold
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Evidence Selection Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = VoSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Evidence Selection",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VoGold
                        )
                        Text(
                            text = "${selectedEvidenceIds.size} document(s) selected",
                            fontSize = 12.sp,
                            color = VoTextMuted
                        )
                    }
                    Button(
                        onClick = { showEvidenceSelector = true },
                        colors = ButtonDefaults.buttonColors(containerColor = VoAccentBlue)
                    ) {
                        Icon(
                            Icons.Filled.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Select", fontSize = 12.sp)
                    }
                }

                if (selectedEvidenceIds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    selectedEvidenceIds.take(3).forEach { id ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = VoGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(id, fontSize = 12.sp, color = VoTextMuted)
                        }
                    }
                    if (selectedEvidenceIds.size > 3) {
                        Text(
                            text = "+${selectedEvidenceIds.size - 3} more",
                            fontSize = 12.sp,
                            color = VoTextMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // User Context Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = VoSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Context (Optional)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VoGold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                TextField(
                    value = userContext,
                    onValueChange = { userContext = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    placeholder = {
                        Text(
                            "E.g., AllFuels fraud case, investigating shell company payments...",
                            color = VoTextMuted
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = VoSurfaceAlt,
                        unfocusedContainerColor = VoSurfaceAlt,
                        focusedTextColor = VoTextPrimary,
                        unfocusedTextColor = VoTextPrimary,
                        focusedIndicatorColor = VoGold
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons
        Button(
            onClick = {
                if (query.isNotBlank() && selectedEvidenceIds.isNotEmpty()) {
                    onStartResearch(query, selectedEvidenceIds, jurisdiction)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VoGold),
            enabled = query.isNotBlank() && selectedEvidenceIds.isNotEmpty()
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = VoBackground)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Research", fontWeight = FontWeight.Bold, color = VoBackground)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { query = ""; selectedEvidenceIds = emptyList(); userContext = "" },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VoTextMuted)
        ) {
            Text("Clear", fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tips Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = VoSurfaceAlt.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "💡 Tips",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VoGold
                )
                Text(
                    text = "• Be specific: name the fraud type or pattern you're investigating",
                    fontSize = 11.sp,
                    color = VoTextMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "• Select at least 2-3 documents for better context",
                    fontSize = 11.sp,
                    color = VoTextMuted,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = "• Include case context to help Gemma 3 understand the investigation",
                    fontSize = 11.sp,
                    color = VoTextMuted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/**
 * Evidence Selector Modal Dialog - Pick which vault documents to include.
 */
@Composable
fun EvidenceSelectorDialog(
    allEvidence: List<String>,
    selectedIds: List<String>,
    onSelectionChange: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var tempSelected by remember { mutableStateOf(selectedIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Evidence", color = VoTextPrimary) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                allEvidence.forEach { docId ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = docId in tempSelected,
                            onCheckedChange = { isChecked ->
                                tempSelected = if (isChecked) {
                                    tempSelected + docId
                                } else {
                                    tempSelected - docId
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = VoGold,
                                uncheckedColor = VoTextMuted
                            )
                        )
                        Text(docId, fontSize = 12.sp, color = VoTextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSelectionChange(tempSelected)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = VoGold)
            ) {
                Text("Confirm", color = VoBackground)
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
