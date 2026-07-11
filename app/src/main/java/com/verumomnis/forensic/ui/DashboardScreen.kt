package com.verumomnis.forensic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoAmber
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoGreen
import com.verumomnis.forensic.ui.theme.VoPrimary
import com.verumomnis.forensic.ui.theme.VoSurface
import com.verumomnis.forensic.ui.theme.VoSurfaceAlt
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary

@Composable
fun DashboardScreen(state: UiState, viewModel: VerumViewModel, onAddMedia: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("AI FORENSICS FOR TRUTH", color = VoGold, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Truth for All", color = VoTextPrimary, fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 40.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "A constitutional forensic AI platform built to democratise justice — evidence sealed, GPS-anchored, and court-ready. Free for every citizen.",
                color = VoTextMuted, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center
            )
        }

        VoCard(title = "DOCUMENT UPLOAD", icon = Icons.Filled.Add) {
            state.files.forEach { file ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(VoSurfaceAlt, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(file.name, color = VoTextPrimary, fontSize = 13.sp)
                        Text(
                            file.status,
                            color = if (file.status == "scanned") VoGreen else VoAmber,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        "GPS ${file.gps} · SHA-512 ${file.sha512.take(12)}…",
                        color = VoTextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAddMedia,
                    colors = ButtonDefaults.buttonColors(containerColor = VoAccentBlue)
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Photo / Video")
                }
                Button(
                    onClick = { viewModel.runScan() },
                    enabled = !state.scanning,
                    colors = ButtonDefaults.buttonColors(containerColor = VoPrimary, contentColor = Color.Black)
                ) {
                    Icon(Icons.Filled.Biotech, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.scanning) "Scanning…" else "Start Forensic Scan")
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VoSurfaceAlt, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    val gps = state.gps
                    Text(
                        "GPS: " + (gps?.let { "%.4f, %.4f".format(it.latitude, it.longitude) } ?: "acquiring…"),
                        color = VoPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp
                    )
                    Text("Jurisdiction: ${state.jurisdiction}", color = VoTextMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(state.scanLog, color = VoTextMuted, fontSize = 12.sp)
                }
            }
        }

        VoCard(title = "CASE INTELLIGENCE", icon = Icons.Filled.Gavel) {
            val findings = state.scanResult?.findings
            if (findings == null) {
                Text("Run a forensic scan to populate case intelligence.", color = VoTextMuted, fontSize = 13.sp)
            } else {
                InfoRow("Documents analyzed", findings.documentsAnalyzed.toString())
                InfoRow("Contradictions", findings.contradictions.size.toString())
                InfoRow("Timeline events", findings.timeline.size.toString())
                InfoRow("Legal mappings", findings.legalMappings.size.toString())
                findings.financial?.companyTax?.let {
                    InfoRow("Company tax (${it.jurisdiction})", "R %,.0f".format(it.taxLiability))
                }
                Spacer(Modifier.height(8.dp))
                findings.contradictions.forEach { c ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(VoSurfaceAlt, RoundedCornerShape(10.dp))
                            .border(1.dp, VoBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text("${c.contradictionId} · ${c.severity}", color = VoAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("A: ${c.claimA.text}", color = VoTextPrimary, fontSize = 12.sp)
                            Text("B: ${c.claimB.text}", color = VoTextPrimary, fontSize = 12.sp)
                            Text(c.applicableLaw.joinToString(", "), color = VoTextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        state.scanResult?.seal?.let { seal ->
            VoCard(title = "CRYPTOGRAPHIC SEAL", icon = Icons.Filled.Verified) {
                Text(seal.sealFooter(), color = VoPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                InfoRow("Status", seal.status)
                InfoRow("Constitution", "v${seal.constitutionVersion}")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.verifyCurrentSeal() }) {
                    Text("Verify Seal: ${viewModel.verifyCurrentSeal()}")
                }
            }
        }
    }
}

