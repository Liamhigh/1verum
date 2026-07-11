package com.verumomnis.forensic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.model.HarassmentVerdict
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoGreen
import com.verumomnis.forensic.ui.theme.VoRed
import com.verumomnis.forensic.ui.theme.VoSurfaceAlt
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary

@Composable
fun EmailScreen(state: UiState, viewModel: VerumViewModel) {
    var recipient by remember { mutableStateOf("investigator@saps.gov.za") }
    var subject by remember { mutableStateOf("Sealed forensic report") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        VoCard(title = "COMPOSE SEALED EMAIL", icon = Icons.Filled.Email) {
            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Recipient") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Subject") },
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            Text(state.emailStatus, color = VoTextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        viewModel.draftAndSendEmail(
                            recipient, subject,
                            points = listOf(
                                "Every contradiction is anchored to a person, page and statute.",
                                "Evidence sealed with SHA-512 and Constitution v5.2.7."
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VoGold, contentColor = Color.Black)
                ) { Text("Draft & Send Sealed PDF") }
                OutlinedButton(onClick = {
                    // Trigger the anti-harassment monitor by repeated sends.
                    repeat(4) { viewModel.draftAndSendEmail(recipient, subject) }
                }) { Text("Simulate Repeat Sends") }
            }
        }

        VoCard(title = "DISTRIBUTION AUDIT TRAIL", icon = Icons.Filled.Shield) {
            if (state.emails.isEmpty()) {
                Text("No emails sent yet.", color = VoTextMuted, fontSize = 13.sp)
            }
            state.emails.forEachIndexed { index, sealed ->
                val color = when (sealed.assessment.verdict) {
                    HarassmentVerdict.ALLOW -> VoGreen
                    HarassmentVerdict.WARN -> VoGold
                    HarassmentVerdict.BLOCK -> VoRed
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(VoSurfaceAlt, RoundedCornerShape(12.dp))
                        .border(1.dp, VoBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("#${index + 1} ${sealed.draft.recipient}", color = VoTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(sealed.assessment.verdict.name + if (sealed.delivered) " · SENT" else " · HELD", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(sealed.sealedPdfFile, color = VoGold, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    Text("Seal ${sealed.seal.shortcode} · 24h count ${sealed.assessment.recipientSendCount24h}", color = VoTextMuted, fontSize = 10.sp)
                    if (sealed.assessment.reasons.isNotEmpty()) {
                        Text(sealed.assessment.reasons.joinToString("; "), color = color, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
