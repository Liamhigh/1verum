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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
private fun TrustCard(state: UiState) {
    VoCard(title = "TRUST & IDENTITY", icon = Icons.Filled.Person) {
        InfoRow("Device fingerprint", state.identityFingerprint.ifEmpty { "—" })
        InfoRow("Identity", state.identityStatus)
        state.trustScore?.let { score ->
            Spacer(Modifier.height(4.dp))
            Text(score.summary(), color = VoGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            score.factors.forEach { factor ->
                Text(
                    "${factor.type.name}: ${factor.confidence}",
                    color = VoTextMuted,
                    fontSize = 10.sp
                )
            }
        } ?: Text("Trust score computed after sealing.", color = VoTextMuted, fontSize = 12.sp)
    }
}

@Composable
fun ReportScreen(
    state: UiState,
    viewModel: VerumViewModel,
    onExportReport: (com.verumomnis.forensic.model.ForensicReport) -> Unit = {},
    onNewScan: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.generateReport() },
                colors = ButtonDefaults.buttonColors(containerColor = VoGold, contentColor = Color.Black)
            ) { Text("Generate Sealed Report") }
            state.report?.let { rpt ->
                OutlinedButton(onClick = { onExportReport(rpt) }) { Text("Export Sealed PDF") }
            }
            OutlinedButton(onClick = onNewScan) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("New Scan")
            }
        }

        val report = state.report
        if (report == null) {
            VoCard(title = "FORENSIC REPORT", icon = Icons.Filled.Description) {
                Text(
                    "No report yet. Start a forensic scan from the home screen, then return here to view the sealed report.",
                    color = VoTextMuted, fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onNewScan,
                    colors = ButtonDefaults.buttonColors(containerColor = VoGold, contentColor = Color.Black)
                ) { Text("Start New Scan") }
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
            Text(report.seal.extendedFooter(), color = VoGold, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
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

        state.scanResult?.findings?.audio?.let { a ->
            VoCard(title = "AUDIO FORENSICS (B8)", icon = Icons.Filled.GraphicEq) {
                InfoRow("Files", a.filesAnalyzed.toString())
                InfoRow("Speakers", a.speakerCount.toString())
                InfoRow("Transcript", if (a.transcriptionAvailable) "available" else "INSUFFICIENT")
                a.tamperSignals.forEach {
                    Text("[${it.severity}] ${it.type}", color = VoRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                a.voiceStress.forEach {
                    Text("Voice stress (${it.speaker}) @${it.timestamp}: ${it.description}", color = VoTextMuted, fontSize = 10.sp)
                }
                if (a.fullTranscript.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(a.fullTranscript, color = VoTextPrimary, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                }
            }
        }

        val exhibits = state.scanResult?.findings?.mediaExhibits.orEmpty()
        if (exhibits.isNotEmpty()) {
            VoCard(title = "EVIDENCE EXHIBITS (PHOTO / VIDEO)", icon = Icons.Filled.PhotoCamera) {
                exhibits.forEach { ex ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(VoSurfaceAlt, RoundedCornerShape(12.dp))
                            .border(1.dp, VoBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text("${ex.exhibitId} · ${ex.kind} · ${ex.fileName}", color = VoGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("SHA-512 ${ex.sha512.take(24)}…", color = VoTextMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                        val g = ex.gps
                        Text(
                            "GPS " + (g?.let { "%.5f, %.5f (${ex.gpsSource})".format(it.latitude, it.longitude) } ?: "NOT RECORDED"),
                            color = VoTextPrimary, fontSize = 11.sp
                        )
                        Text("Captured ${ex.capturedAt}" + (ex.exifTimestamp?.let { " · EXIF $it" } ?: ""), color = VoTextMuted, fontSize = 10.sp)
                        Text("Jurisdiction ${ex.jurisdiction}", color = VoTextMuted, fontSize = 10.sp)
                    }
                }
            }
        }

        TrustCard(state)

        VoCard(title = "BITCOIN ANCHOR (OpenTimestamps)", icon = Icons.Filled.Link) {
            Text(state.otsStatus, color = VoTextMuted, fontSize = 12.sp)
            state.otsResult?.let { ots ->
                Spacer(Modifier.height(4.dp))
                Text("SHA-256 digest: ${ots.sha256Digest}", color = VoGold, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                Text("Calendars: ${ots.calendarUrls.joinToString(", ").ifEmpty { "—" }}", color = VoTextMuted, fontSize = 10.sp)
                Text("Proof: ${ots.otsProofFile}", color = VoTextMuted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.anchorSealToBitcoin() },
                enabled = !state.anchoring,
                colors = ButtonDefaults.buttonColors(containerColor = VoGold, contentColor = Color.Black)
            ) { Text(if (state.anchoring) "Anchoring…" else "Anchor Seal to Bitcoin") }
        }
    }
}
