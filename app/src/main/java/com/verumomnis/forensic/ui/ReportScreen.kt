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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.verumomnis.forensic.engine.contradiction.FindingsJsonEmitter
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoBlueBorder
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoHeading
import com.verumomnis.forensic.ui.theme.VoNavy2
import com.verumomnis.forensic.ui.theme.VoPanelInput
import com.verumomnis.forensic.ui.theme.VoRed
import com.verumomnis.forensic.ui.theme.VoSurfaceAlt
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary
import com.verumomnis.forensic.ui.theme.severityColor

@Composable
private fun TrustCard(state: UiState) {
    VoCard(title = "TRUST & IDENTITY", icon = Icons.Filled.Person) {
        InfoRow("Device fingerprint", state.identityFingerprint.ifEmpty { "—" })
        InfoRow("Identity", state.identityStatus)
        state.trustScore?.let { score ->
            Spacer(Modifier.height(4.dp))
            // PD16: ordinal confidence only — never a numeric score or percentage.
            Text(
                "Trust: ${score.confidence} · ${score.factors.size} factor(s)",
                color = VoGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
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

/**
 * Human sign-off surface for the GHRP two-tier rule: lists every candidate
 * Gemma 3 raised during vault review, with promote (candidate becomes a local
 * engine rule, detected deterministically on the next scan) and reject
 * (reason sealed with the record — never deleted).
 */
@Composable
private fun G3CandidateCard(state: UiState, viewModel: VerumViewModel) {
    var rejectTarget by remember { mutableStateOf<String?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    VoCard(title = "G3 CANDIDATES — PENDING VERIFICATION", icon = Icons.Filled.Description) {
        Text(
            "Contradictions raised by Gemma 3 during vault review that the engine did not emit. " +
                "They are anchored and hashed but are never engine-verified until promoted here " +
                "(human sign-off) or re-detected by the engine. Promotion adds the proposition pair " +
                "to the engine as a local rule, applied on the next scan.",
            color = VoTextMuted,
            fontSize = 10.sp
        )
        Spacer(Modifier.height(6.dp))
        state.g3Candidates.forEach { c ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(VoSurfaceAlt, RoundedCornerShape(12.dp))
                    .border(1.dp, VoBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(c.contradictionId, color = VoGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        c.severity.toString().replace('_', ' '),
                        color = severityColor(c.severity.toString()),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp
                    )
                    Text(c.type.toString(), color = VoTextMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text("A (${c.propositionAActor}): \"${c.propositionAText}\"", color = VoTextPrimary, fontSize = 12.sp)
                Text("B (${c.propositionBActor}): \"${c.propositionBText}\"", color = VoTextPrimary, fontSize = 12.sp)
                Text(c.conflictDescription, color = VoTextMuted, fontSize = 11.sp)
                Text(
                    "Source: ${c.sourceDocument} p${c.sourcePage} · SHA-512 ${c.sha512Anchor.take(12)}…",
                    color = VoTextMuted, fontSize = 10.sp
                )
                Spacer(Modifier.height(6.dp))
                if (c.verificationStatus == FindingsJsonEmitter.STATUS_G3_CANDIDATE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VerumPrimaryButton(
                            label = "Promote",
                            onClick = { viewModel.promoteG3Candidate(c.contradictionId) },
                            modifier = Modifier.weight(1f)
                        )
                        VerumSecondaryButton(
                            label = "Reject",
                            onClick = {
                                rejectTarget = c.contradictionId
                                rejectReason = ""
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    val statusColor =
                        if (c.verificationStatus == FindingsJsonEmitter.STATUS_CANDIDATE_PROMOTED) VoGold else VoRed
                    Text(c.verificationStatus, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    c.rejectionReason?.let {
                        Text("Reason: $it", color = VoTextMuted, fontSize = 10.sp)
                    }
                }
            }
        }
    }

    rejectTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            containerColor = VoNavy2,
            titleContentColor = VoGold,
            textContentColor = VoTextPrimary,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    "Reject $target",
                    fontFamily = Cormorant,
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.sp,
                    color = VoGold
                )
            },
            text = {
                Column {
                    Text(
                        "A rejection reason is required — it is sealed with the record and never deleted.",
                        fontSize = 12.sp,
                        color = VoTextMuted
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        label = { Text("Reason", color = VoAccentBlue) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VoGold,
                            unfocusedBorderColor = VoBlueBorder,
                            focusedTextColor = VoHeading,
                            unfocusedTextColor = VoHeading,
                            cursorColor = VoGold,
                            focusedContainerColor = VoPanelInput,
                            unfocusedContainerColor = VoPanelInput
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.rejectG3Candidate(target, rejectReason.trim())
                        rejectTarget = null
                    },
                    enabled = rejectReason.isNotBlank()
                ) { Text("REJECT CANDIDATE", fontFamily = JetBrainsMono, fontSize = 12.sp, letterSpacing = 1.sp, color = if (rejectReason.isNotBlank()) VoGold else VoTextMuted) }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }) {
                    Text("CANCEL", fontFamily = JetBrainsMono, fontSize = 12.sp, letterSpacing = 1.sp, color = VoAccentBlue)
                }
            }
        )
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
        // §3.6 section heading: mono kicker over a serif title with gold rule.
        Column {
            VoKicker("Nine-Brain Engine — Sealed Output")
            Spacer(Modifier.height(6.dp))
            VoSerifHeading("Forensic Report", fontSize = 32)
            Spacer(Modifier.height(10.dp))
            VoGoldRule()
        }

        // Primary CTA: gold gradient (§3.4); siblings are blue outlines.
        VerumPrimaryButton(
            label = "Generate Sealed Report",
            onClick = { viewModel.generateReport() },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.report?.let { rpt ->
                VerumSecondaryButton(
                    label = "Export Sealed PDF",
                    onClick = { onExportReport(rpt) },
                    modifier = Modifier.weight(1f)
                )
            }
            VerumSecondaryButton(
                label = "New Scan",
                onClick = onNewScan,
                modifier = Modifier.weight(1f)
            )
        }

        // Candidates surface as soon as a scan has run — they must never wait
        // on (or be buried inside) the sealed report.
        if (state.g3Candidates.isNotEmpty()) {
            G3CandidateCard(state, viewModel)
        }

        val report = state.report
        if (report == null) {
            VoCard(title = "FORENSIC REPORT", icon = Icons.Filled.Description) {
                Text(
                    "No report yet. Start a forensic scan from the home screen, then return here to view the sealed report.",
                    color = VoTextMuted, fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                VerumPrimaryButton(label = "Start New Scan", onClick = onNewScan)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(c.contradictionId, color = VoGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        // Severity as TEXT in the site's accent palette (PD16 — no scores).
                        Text(
                            c.severity.name.replace('_', ' '),
                            color = severityColor(c.severity),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
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

        if (report.gemmaNarrative.isNotBlank()) {
            VoCard(title = "NARRATIVE ANALYSIS (GEMMA 3)", icon = Icons.Filled.Description) {
                Text(
                    "Unsealed appendix — narrative written by the on-device report writer from the sealed findings JSON. " +
                        "The sealed report body above remains the sole evidentiary record.",
                    color = VoTextMuted,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(report.gemmaNarrative, color = VoTextPrimary, fontSize = 12.sp)
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
            VerumPrimaryButton(
                label = if (state.anchoring) "Anchoring…" else "Anchor Seal to Bitcoin",
                onClick = { viewModel.anchorSealToBitcoin() },
                enabled = !state.anchoring,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
