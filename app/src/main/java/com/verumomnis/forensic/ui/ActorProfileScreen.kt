package com.verumomnis.forensic.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.model.ExtractedPerson
import com.verumomnis.forensic.model.ForensicReport
import com.verumomnis.forensic.model.Severity
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoRed
import com.verumomnis.forensic.ui.theme.VoSurface
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary

@Composable
fun ActorProfileScreen(state: UiState) {
    val report = state.report

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (report == null) {
            VoCard(title = "ACTOR PROFILES", icon = Icons.Filled.Person) {
                Text(
                    "No report generated yet. Start a forensic scan to see person profiles.",
                    color = VoTextMuted, fontSize = 13.sp, lineHeight = 16.sp
                )
            }
            return@Column
        }

        VoCard(title = "ACTOR PROFILES", icon = Icons.Filled.Person) {
            Text(
                "Per-person dishonesty scorecard extracted from sealed evidence.",
                color = VoTextMuted, fontSize = 12.sp, lineHeight = 16.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Includes behavioral patterns, financial anomalies, and evidence mentions.",
                color = VoGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = JetBrainsMono
            )
        }

        if (report.extractedPersons.isEmpty()) {
            VoCard(title = "PERSONS", icon = Icons.Filled.Person) {
                Text("No persons extracted.", color = VoTextMuted, fontSize = 13.sp)
            }
            return@Column
        }

        VoSectionLabel("${report.extractedPersons.size} individuals · ${report.contradictions.size} contradictions")
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(report.extractedPersons) { person ->
                PersonProfileCard(person, report)
            }
        }
    }
}

@Composable
private fun PersonProfileCard(person: ExtractedPerson, report: ForensicReport?) {
    val dishonestBadge = buildDishonestyBadge(person, report)
    val dishonestColor = buildDishonestyColor(person, report)

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
                    person.name,
                    color = VoGold,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    fontFamily = JetBrainsMono
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    person.role.name.replace("_", " "),
                    color = VoTextMuted, fontSize = 11.sp
                )
            }
            Row(
                modifier = Modifier
                    .background(dishonestColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .border(1.dp, dishonestColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(10.dp, 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    dishonestBadge,
                    color = dishonestColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            person.age?.let {
                ActorInfoRow("Age", it.toString())
            }
            person.idNumber?.let {
                ActorInfoRow("ID", it.take(8) + "..." )
            }
            person.address?.let {
                ActorInfoRow("Address", it)
            }
            if (person.context.isNotEmpty()) {
                ActorInfoRow("Context", person.context)
            }
        }

        if (report != null) {
            Spacer(Modifier.height(4.dp))
            val contradictionCount = report.contradictions.count {
                it.anchoredPerson?.name == person.name || it.respondent == person.name
            }
            if (contradictionCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VoRed.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .border(1.dp, VoRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$contradictionCount contradiction${if (contradictionCount > 1) "s" else ""}",
                        color = VoTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "▶ Details",
                        color = VoRed, fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ActorInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label + ":", color = VoTextMuted, fontSize = 11.sp, modifier = Modifier.weight(0.35f))
        Text(value, color = VoTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.65f))
    }
}

private fun buildDishonestyBadge(person: ExtractedPerson, report: ForensicReport?): String {
    if (report == null) return "—"

    val contradictionsCount = report.contradictions.count {
        it.anchoredPerson?.name == person.name || it.respondent == person.name
    }

    val criticalCount = report.contradictions.count {
        (it.anchoredPerson?.name == person.name || it.respondent == person.name) &&
        it.severity == Severity.CRITICAL
    }

    return when {
        criticalCount > 0 -> "CRITICAL"
        contradictionsCount > 5 -> "HIGH"
        contradictionsCount > 2 -> "MODERATE"
        contradictionsCount > 0 -> "LOW"
        else -> "INSUFFICIENT"
    }
}

private fun buildDishonestyColor(person: ExtractedPerson, report: ForensicReport?): Color {
    // Site palette only (PD16): red family / gold / blue — no off-palette oranges.
    return com.verumomnis.forensic.ui.theme.severityColor(buildDishonestyBadge(person, report))
}
