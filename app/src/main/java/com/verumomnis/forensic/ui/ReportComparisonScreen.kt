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
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.model.Contradiction
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
fun ReportComparisonScreen(state: UiState, viewModel: VerumViewModel) {
    val report = state.report
    val pastReports = remember(state.report) { viewModel.pastReports() }
    var selected by remember(pastReports) { mutableStateOf(pastReports.firstOrNull()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        VoCard(title = "REPORT COMPARISON", icon = Icons.Filled.CompareArrows) {
            Text(
                "Compare multiple forensic reports side-by-side to identify new contradictions,",
                color = VoTextMuted, fontSize = 12.sp, lineHeight = 16.sp
            )
            Text(
                "resolved issues, and changes in severity across timeline.",
                color = VoTextMuted, fontSize = 12.sp, lineHeight = 16.sp
            )
        }

        if (report == null) {
            VoCard(title = "STATUS", icon = Icons.Filled.CompareArrows) {
                Text(
                    "No report generated yet. Start a forensic scan to enable comparison.",
                    color = VoTextMuted, fontSize = 13.sp
                )
            }
            return@Column
        }

        // Current report summary
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .background(VoSurface, RoundedCornerShape(12.dp))
                .border(1.dp, VoBorder, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("CURRENT REPORT", color = VoGold, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.sp, fontFamily = JetBrainsMono)
            Text(
                report.reference,
                color = VoTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, fontFamily = JetBrainsMono
            )
            Text(
                report.createdAt.take(10),
                color = VoTextMuted, fontSize = 11.sp
            )

            Spacer(Modifier.height(4.dp))
            ReportStatsRow(report)
        }

        VoCard(title = "MULTI-REPORT COMPARISON", icon = Icons.Filled.CompareArrows) {
            if (pastReports.isEmpty()) {
                Text(
                    "No earlier reports saved yet. Every report you generate from now on is kept " +
                    "so future reports can be compared against it.",
                    color = VoTextMuted, fontSize = 12.sp, lineHeight = 16.sp
                )
            } else {
                Text("Compare against:", color = VoTextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pastReports.forEach { candidate ->
                        val isSelected = candidate.reference == selected?.reference
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = candidate }
                                .background(
                                    if (isSelected) VoGold.copy(alpha = 0.12f) else VoSurface,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(1.dp, if (isSelected) VoGold else VoBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(candidate.reference, color = VoTextPrimary, fontSize = 11.sp, fontFamily = JetBrainsMono)
                            Text(candidate.createdAt.take(10), color = VoTextMuted, fontSize = 10.sp)
                        }
                    }
                }
                selected?.let { previous ->
                    Spacer(Modifier.height(12.dp))
                    ReportDiffSection(current = report, previous = previous)
                }
            }
        }

        // Contradiction breakdown by severity
        VoSectionLabel("Contradiction Distribution")
        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Severity.values().forEach { severity ->
                val count = report.contradictions.count { it.severity == severity }
                if (count > 0) {
                    ContradictionBreakdownRow(severity, count)
                }
            }
        }

        if (report.contradictions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Total: ${report.contradictions.size} contradictions · Persons: ${report.extractedPersons.size}",
                color = VoTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, fontFamily = JetBrainsMono
            )
        }
    }
}

/** Diffs [current] against [previous] by contradiction ID: new, resolved, and severity changes. */
@Composable
private fun ReportDiffSection(current: ForensicReport, previous: ForensicReport) {
    val currentById = current.contradictions.associateBy { it.contradictionId }
    val previousById = previous.contradictions.associateBy { it.contradictionId }

    val added = current.contradictions.filter { it.contradictionId !in previousById }
    val resolved = previous.contradictions.filter { it.contradictionId !in currentById }
    val severityChanged = current.contradictions.filter { c ->
        previousById[c.contradictionId]?.let { it.severity != c.severity } == true
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatItem("New", added.size.toString())
            StatItem("Resolved", resolved.size.toString())
            StatItem("Severity Δ", severityChanged.size.toString())
        }
        if (added.isNotEmpty()) {
            DiffList("NEW CONTRADICTIONS", added, VoRed) { "[${it.severity}] ${it.contradictionId} — ${it.respondent}" }
        }
        if (resolved.isNotEmpty()) {
            DiffList("RESOLVED SINCE ${previous.reference}", resolved, VoGold) { "${it.contradictionId} — ${it.respondent}" }
        }
        if (severityChanged.isNotEmpty()) {
            DiffList("SEVERITY CHANGED", severityChanged, VoTextPrimary) {
                "${it.contradictionId}: ${previousById[it.contradictionId]?.severity} → ${it.severity}"
            }
        }
        if (added.isEmpty() && resolved.isEmpty() && severityChanged.isEmpty()) {
            Text("No changes in contradictions between these two reports.", color = VoTextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DiffList(label: String, items: List<Contradiction>, color: Color, line: (Contradiction) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = color, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, fontFamily = JetBrainsMono, letterSpacing = 1.sp)
        items.take(10).forEach { c ->
            Text("· ${line(c)}", color = VoTextMuted, fontSize = 10.sp, lineHeight = 13.sp)
        }
        if (items.size > 10) {
            Text("… and ${items.size - 10} more", color = VoTextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ReportStatsRow(report: ForensicReport) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatItem("Total", report.contradictions.size.toString())
        StatItem("Critical", report.contradictions.count { it.severity == Severity.CRITICAL }.toString())
        StatItem("High", report.contradictions.count { it.severity == Severity.HIGH }.toString())
        StatItem("Persons", report.extractedPersons.size.toString())
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = VoGold, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = JetBrainsMono)
        Text(label, color = VoTextMuted, fontSize = 9.sp)
    }
}

@Composable
private fun ContradictionBreakdownRow(severity: Severity, count: Int) {
    // Site palette only (PD16): red family / gold / blue — no off-palette oranges.
    val color = com.verumomnis.forensic.ui.theme.severityColor(severity)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(8.dp))
            .background(VoSurface, RoundedCornerShape(8.dp))
            .border(1.dp, VoBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(severity.name, color = color, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, fontFamily = JetBrainsMono)
        Text("$count", color = VoTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
