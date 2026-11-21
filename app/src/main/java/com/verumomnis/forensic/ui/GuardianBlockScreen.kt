package com.verumomnis.forensic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.model.GuardianAssessment
import com.verumomnis.forensic.model.GuardianViolation
import com.verumomnis.forensic.model.ForensicReport
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoRed
import com.verumomnis.forensic.ui.theme.VoSurface
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary
import java.io.File

/**
 * Full-screen B9 constitutional hard stop.
 *
 * Displayed only when the Guardian Brain detects an Article X anti-weaponization
 * violation — the single non-negotiable hard stop in the Constitution. Lesser
 * flags (for example coercion language) are reported in the assessment but never
 * trigger this screen. The user cannot interact with the rest of the app until
 * they acknowledge the block.
 */
@Composable
fun GuardianBlockScreen(
    assessment: GuardianAssessment,
    onClearCase: () -> Unit,
    onReadConstitution: () -> Unit,
    report: ForensicReport? = null,
    reportPdfFile: File? = null,
    reportPdfGenerating: Boolean = false,
    reportPdfError: String = "",
    onRetryPdf: () -> Unit = {},
    onSharePdf: (File) -> String? = { null },
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showReport by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoBackground)
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = "Constitutional hard stop",
            tint = VoRed,
            modifier = Modifier.size(72.dp)
        )

        Spacer(Modifier.height(20.dp))

        Text(
            "CONSTITUTIONAL HARD STOP",
            color = VoRed,
            fontSize = 13.sp,
            letterSpacing = 3.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Article X — Anti-Weaponization",
            color = VoTextPrimary,
            fontFamily = Cormorant,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "The safety screen was triggered because a keyword in the scanned text matched an " +
                "Article X screening rule. A keyword match alone does not establish intent, " +
                "wrongdoing, or that the source advocates harm. This app cannot be used to plan, " +
                "target, or coordinate harm.\n\n" +
                "You can review the report produced from this scan below, or clear the active case " +
                "from this session. Clearing the session does not delete original evidence or " +
                "saved reports from the vault.",
            color = VoTextMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        if (report != null) {
            Spacer(Modifier.height(18.dp))
            Text(
                "SCAN REPORT",
                color = VoAccentBlue,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "The report is available for read-only review. Safety keyword matches remain screening indicators, not proven legal findings.",
                color = VoTextMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Start
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showReport = !showReport },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VoAccentBlue)
            ) {
                Text(
                    if (showReport) "Hide report" else "Review sealed report",
                    color = VoAccentBlue,
                    fontSize = 14.sp
                )
            }
            if (reportPdfFile != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { shareError = onSharePdf(reportPdfFile) ?: "" },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VoAccentBlue)
                ) {
                    Text("Share sealed PDF · ${reportPdfFile.name}", color = VoAccentBlue, fontSize = 12.sp)
                }
            } else if (reportPdfGenerating) {
                Spacer(Modifier.height(10.dp))
                Text("Creating watermarked, sealed PDF…", color = VoTextMuted, fontSize = 12.sp)
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    reportPdfError.ifBlank { "The report PDF is not ready yet." },
                    color = if (reportPdfError.isBlank()) VoTextMuted else VoRed,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                OutlinedButton(
                    onClick = onRetryPdf,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VoAccentBlue)
                ) {
                    Text("Retry PDF export", color = VoAccentBlue, fontSize = 14.sp)
                }
            }
            if (shareError.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(shareError, color = VoRed, fontSize = 12.sp)
            }
            if (showReport) {
                Spacer(Modifier.height(12.dp))
                Text(report.title, color = VoTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Reference ${report.reference} · ${report.seal.extendedFooter()}", color = VoTextMuted, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        report.body,
                        color = VoTextPrimary,
                        fontFamily = JetBrainsMono,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "The scan report is still being prepared. It will appear here when ready.",
                color = VoTextMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }

        Text(
            "VIOLATIONS (${assessment.violations.size})",
            color = VoAccentBlue,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        assessment.violations.forEach { violation ->
            ViolationCard(violation)
            Spacer(Modifier.height(10.dp))
        }

        if (assessment.notes.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "B9 NOTES",
                color = VoAccentBlue,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            assessment.notes.forEach { note ->
                Text(
                    "• $note",
                    color = VoTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onClearCase,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VoRed, contentColor = VoTextPrimary)
        ) {
            Text("Clear active case", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onReadConstitution,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VoAccentBlue)
        ) {
            Text("Read Constitution", color = VoAccentBlue, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ViolationCard(violation: GuardianViolation) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(VoSurface)
            .border(1.dp, VoBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                violation.type.name.replace('_', ' '),
                color = VoRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                violation.severity.name,
                color = VoGold,
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            violation.description,
            color = VoTextPrimary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        if (violation.trigger.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Trigger: \"${violation.trigger}\"",
                color = VoTextMuted,
                fontFamily = JetBrainsMono,
                fontSize = 10.sp
            )
        }
    }
}
