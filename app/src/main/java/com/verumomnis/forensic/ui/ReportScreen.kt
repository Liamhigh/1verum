package com.verumomnis.forensic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoRed
import com.verumomnis.forensic.ui.theme.VoSurfaceAlt
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary

@Composable
fun ReportScreen(state: UiState, viewModel: VerumViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = { viewModel.generateReport() },
            colors = ButtonDefaults.buttonColors(containerColor = VoGold, contentColor = Color.Black)
        ) { Text("Generate Sealed Forensic Report") }

        val report = state.report
        if (report == null) {
            VoCard(title = "FORENSIC REPORT", icon = Icons.Filled.Description) {
                Text(
                    "No report yet. Generate a report to anchor every contradiction to a person, a page, and a statute.",
                    color = VoTextMuted, fontSize = 13.sp
                )
            }
            return@Column
        }

        VoCard(title = report.reference, icon = Icons.Filled.Description) {
            Text(report.title, color = VoTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(report.classification, color = VoRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            InfoRow("Jurisdiction", report.jurisdiction)
            InfoRow("Contradictions", report.contradictions.size.toString())
            InfoRow("Seal status", report.seal.status)
            Spacer(Modifier.height(6.dp))
            Text(report.seal.sealFooter(), color = VoGold, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
            Text(report.executiveSummary, color = VoTextMuted, fontSize = 12.sp)
        }

        VoCard(title = "CONTRADICTION MATRIX", icon = Icons.Filled.Person) {
            if (report.contradictions.isEmpty()) {
                Text("No contradictions detected.", color = VoTextMuted, fontSize = 13.sp)
            }
            report.contradictions.forEach { c ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(VoSurfaceAlt, RoundedCornerShape(12.dp))
                        .border(1.dp, VoBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text("${c.contradictionId} · ${c.severity}", color = VoGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Person: ${c.respondent}", color = VoTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("A: \"${c.claimA.text}\"", color = VoTextPrimary, fontSize = 12.sp)
                    Text("   ${c.claimA.source} · p${c.claimA.page} · ln${c.claimA.line}", color = VoTextMuted, fontSize = 10.sp)
                    Text("B: \"${c.claimB.text}\"", color = VoTextPrimary, fontSize = 12.sp)
                    Text("   ${c.claimB.source} · p${c.claimB.page} · ln${c.claimB.line}", color = VoTextMuted, fontSize = 10.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Statute: ${c.applicableLaw.joinToString("; ")}", color = VoGold, fontSize = 11.sp)
                    Text(c.legalSignificance, color = VoTextMuted, fontSize = 11.sp)
                }
            }
        }
    }
}
