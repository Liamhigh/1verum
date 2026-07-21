package com.verumomnis.forensic.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
 * Result of the on-device, format-level seal check. Honestly labelled:
 * these are indicators, not determinations — the canonical verifier is
 * verumglobal.foundation/verify.html, reachable via the online button when
 * a scanned QR payload is present.
 */
@Composable
fun ScanSealResultScreen(
    state: UiState,
    onBack: () -> Unit,
    onScanAgain: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val result = state.scanSealResult

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "ON-DEVICE FORMAT CHECK — INDICATORS, NOT DETERMINATIONS",
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = VoGold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))
        result?.let { ScanSealVerdictCard(it) } ?: ScanSealNoResultCard()

        state.scanSealQrPayload?.let { payload ->
            Spacer(Modifier.height(16.dp))
            VerumPrimaryButton(
                label = "Verify online at verumglobal.foundation",
                onClick = { openVerifier(context, payload.rawUrl) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(12.dp))
        VerumSecondaryButton(
            label = "Scan another seal",
            onClick = onScanAgain,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "This on-device check is format-level only. The canonical verifier is " +
                "verumglobal.foundation/verify.html. No document content leaves your device.",
            color = VoTextMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ScanSealVerdictCard(result: com.verumomnis.forensic.model.SealScanResult) {
    val (icon, title, color) = when (result.verdict) {
        SealScanVerdict.MATCH -> Triple(Icons.Filled.CheckCircle, "Format Check Passed", VoGreen)
        SealScanVerdict.TAMPERED -> Triple(Icons.Filled.Error, "Hash Mismatch Detected", VoRed)
        SealScanVerdict.SEAL_PRESENT -> Triple(Icons.Filled.HelpOutline, "Seal Found — Format Check Only", VoAccentBlue)
        SealScanVerdict.LEGACY -> Triple(Icons.Filled.Warning, "Seal Found — Legacy Format", VoGold)
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
            "Scan a QR code and select the sealed PDF to run the on-device format check.",
            fontSize = 14.sp,
            color = VoTextMuted,
            textAlign = TextAlign.Center
        )
    }
}

private fun openVerifier(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
