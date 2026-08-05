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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.model.Contradiction
import com.verumomnis.forensic.model.Severity
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoRed
import com.verumomnis.forensic.ui.theme.VoSurface
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary

@Composable
fun ContradictionDetailScreen(state: UiState) {
    val report = state.report

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (report == null) {
            VoCard(title = "CONTRADICTIONS", icon = Icons.Filled.Difference) {
                Text(
                    "No report generated yet. Start a forensic scan to see contradictions.",
                    color = VoTextMuted, fontSize = 13.sp, lineHeight = 16.sp
                )
            }
            return@Column
        }

        VoCard(title = "CONTRADICTION ANALYSIS", icon = Icons.Filled.Difference) {
            Text(
                "Detailed breakdown of each detected contradiction with source evidence,",
                color = VoTextMuted, fontSize = 12.sp, lineHeight = 16.sp
            )
            Text(
                "brain council votes, legal significance, and ordinal confidence.",
                color = VoTextMuted, fontSize = 12.sp, lineHeight = 16.sp
            )
        }

        if (report.contradictions.isEmpty()) {
            VoCard(title = "FINDINGS", icon = Icons.Filled.Difference) {
                Text("No contradictions detected.", color = VoTextMuted, fontSize = 13.sp)
            }
            return@Column
        }

        // Summary stats
        val criticalCount = report.contradictions.count { it.severity == Severity.CRITICAL }
        val highCount = report.contradictions.count { it.severity == Severity.HIGH }
        VoSectionLabel("${report.contradictions.size} total · $criticalCount critical · $highCount high")
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(report.contradictions) { index, contradiction ->
                ContradictionCard(contradiction, index + 1)
            }
        }
    }
}

@Composable
private fun ContradictionCard(contradiction: Contradiction, index: Int) {
    // Site palette only (PD16): red family / gold / blue — no off-palette oranges.
    val severityColor = com.verumomnis.forensic.ui.theme.severityColor(contradiction.severity)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .background(VoSurface, RoundedCornerShape(12.dp))
            .border(1.dp, VoBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "#$index · ${contradiction.category.name.replace("_", " ")}",
                    color = VoGold, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, fontFamily = JetBrainsMono
                )
                Text(
                    contradiction.type.name.replace("_", " "),
                    color = VoTextMuted, fontSize = 10.sp
                )
            }
            Row(
                modifier = Modifier
                    .background(severityColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .border(1.dp, severityColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(8.dp, 6.dp)
            ) {
                Text(
                    contradiction.severity.name.replace('_', ' '),
                    color = severityColor, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp, fontFamily = JetBrainsMono
                )
            }
        }

        if (contradiction.description.isNotEmpty()) {
            Text(
                contradiction.description,
                color = VoTextPrimary, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold
            )
        }

        if (contradiction.legalSignificance.isNotEmpty()) {
            Text(
                "Legal Significance",
                color = VoGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = JetBrainsMono
            )
            Text(
                contradiction.legalSignificance,
                color = VoTextPrimary, fontSize = 11.sp, lineHeight = 14.sp
            )
        }

        // Claim A
        Text("Claim A", color = VoGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = JetBrainsMono)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VoSurface.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                .border(1.dp, VoBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "\"${contradiction.claimA.text}\"",
                color = VoTextPrimary, fontSize = 11.sp, lineHeight = 14.sp, fontStyle = FontStyle.Italic
            )
            Text(
                "${contradiction.claimA.statementType.name} · Page ${contradiction.claimA.page}",
                color = VoTextMuted, fontSize = 9.sp, fontFamily = JetBrainsMono
            )
        }

        // Claim B
        Text("Claim B", color = VoGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = JetBrainsMono)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VoSurface.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                .border(1.dp, VoBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "\"${contradiction.claimB.text}\"",
                color = VoTextPrimary, fontSize = 11.sp, lineHeight = 14.sp, fontStyle = FontStyle.Italic
            )
            Text(
                "${contradiction.claimB.statementType.name} · Page ${contradiction.claimB.page}",
                color = VoTextMuted, fontSize = 9.sp, fontFamily = JetBrainsMono
            )
        }

        // Brain council & confidence
        if (contradiction.confirmingBrains.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Confirmed: ${contradiction.confirmingBrains.take(2).joinToString(", ")}",
                    color = VoGold, fontSize = 9.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Confidence: ${contradiction.confidence.name}",
                    color = VoTextMuted, fontSize = 9.sp
                )
            }
        }
    }
}
