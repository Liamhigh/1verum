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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.model.SealScanVerdict
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoGreen
import com.verumomnis.forensic.ui.theme.VoRed
import com.verumomnis.forensic.ui.theme.VoSurfaceAlt
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary

/**
 * Displays the result of a QR-aware seal verification (MATCH, TAMPERED,
 * SEAL_PRESENT, LEGACY, or NO_SEAL). Mirrors the verdict cards shown on the
 * live webdocsol verify.html.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSealResultScreen(
    state: UiState,
    onBack: () -> Unit,
    onScanAgain: () -> Unit
) {
    val scrollState = rememberScrollState()
    val result = state.scanSealResult

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("VERUM", fontFamily = Cormorant, fontWeight = FontWeight.Bold, color = VoGold, fontSize = 20.sp)
                        Text(" OMNIS", fontFamily = Cormorant, fontWeight = FontWeight.Light, color = VoTextPrimary, fontSize = 20.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = VoGold) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VoBackground.copy(alpha = 0.9f), titleContentColor = VoTextPrimary)
            )
        },
        containerColor = VoBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            result?.let { ScanSealVerdictCard(it) } ?: ScanSealNoResultCard()

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onScanAgain,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VoGold, contentColor = VoBackground)
            ) {
                Text("Scan another seal", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Verification is performed entirely on your device. No document content or hashes are sent to any server.",
                color = VoTextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ScanSealVerdictCard(result: com.verumomnis.forensic.model.SealScanResult) {
    val (icon, title, color) = when (result.verdict) {
        SealScanVerdict.MATCH -> Triple(Icons.Filled.CheckCircle, "Seal Verified — Genuine", VoGreen)
        SealScanVerdict.TAMPERED -> Triple(Icons.Filled.Error, "Seal Invalid / Tampered", VoRed)
        SealScanVerdict.SEAL_PRESENT -> Triple(Icons.Filled.HelpOutline, "Seal Present — Check Not Applicable", VoAccentBlue)
        SealScanVerdict.LEGACY -> Triple(Icons.Filled.Warning, "Seal Present — Legacy Format", VoGold)
        SealScanVerdict.NO_SEAL -> Triple(Icons.Filled.Error, "No Seal Found", VoRed)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, fontFamily = Cormorant, fontSize = 26.sp, color = color, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(result.reason, fontSize = 15.sp, color = color, lineHeight = 22.sp, textAlign = TextAlign.Center)

        result.seal?.let { seal ->
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VoBackground.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text("SEAL DETAILS", fontFamily = JetBrainsMono, fontSize = 11.sp, color = VoGold, letterSpacing = 1.sp)
                Spacer(Modifier.height(12.dp))
                InfoRow("Scheme", seal.scheme)
                InfoRow("Seal ID", seal.sealId)
                if (seal.embeddedHash.length >= 32) {
                    InfoRow("Embedded hash", "${seal.embeddedHash.take(16)}…${seal.embeddedHash.takeLast(16)}")
                }
                seal.originalHash?.let {
                    InfoRow("Original content", "${it.take(16)}…${it.takeLast(16)}")
                }
                if (seal.chain.isNotEmpty()) {
                    InfoRow("Chain", seal.chain.joinToString(", "))
                }
            }
        }

        result.scannedHashPrefix?.let { prefix ->
            Spacer(Modifier.height(12.dp))
            Text(
                "QR hash prefix: $prefix",
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
                color = VoTextMuted
            )
        }

        result.fileHash?.let { hash ->
            Spacer(Modifier.height(8.dp))
            Text(
                "File SHA-512: ${hash.take(16)}…${hash.takeLast(16)}",
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
                color = VoTextMuted
            )
        }
    }
}

@Composable
private fun ScanSealNoResultCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoSurfaceAlt.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .border(1.dp, VoBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "No verification result yet.",
            fontFamily = Cormorant,
            fontSize = 22.sp,
            color = VoTextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan a QR code and select the sealed PDF to see the verdict.",
            fontSize = 14.sp,
            color = VoTextMuted,
            textAlign = TextAlign.Center
        )
    }
}
