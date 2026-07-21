package com.verumomnis.forensic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.CormorantDisplay
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoSurface
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary

/**
 * The forensic scan initiation screen.
 *
 * This is the user's home after the story intro. It drives the proper
 * pipeline: select a case file → optionally name the case → start the
 * forensic analysis → view the sealed report. No pre-generated report is
 * shown until the user has initiated a scan.
 */
@Composable
fun ScanHomeScreen(
    state: UiState,
    onSelectFile: () -> Unit,
    onStartScan: (caseName: String) -> Unit,
    onNewScan: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenReport: () -> Unit,
    onSealDocument: () -> Unit = {},
    onVerifyDocument: () -> Unit = {},
    onOpenConstitution: () -> Unit = {}
) {
    var caseName by remember { mutableStateOf("") }
    var confirmNewScan by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val pending = state.pendingFiles.firstOrNull()
    val hasFile = pending != null
    val isScanning = state.sealStage != SealStage.IDLE && state.sealStage != SealStage.DONE && state.sealStage != SealStage.ERROR
    val scanDone = state.sealStage == SealStage.DONE || state.report != null

    if (confirmNewScan) {
        VoConfirmDialog(
            title = "Start a new scan?",
            message = "This clears the current case from this session. Sealed files already in the vault are kept. This cannot be undone.",
            confirmLabel = "New scan",
            onConfirm = {
                caseName = ""
                onNewScan()
            },
            onDismiss = { confirmNewScan = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "FORENSIC SCAN",
            color = VoGold,
            fontSize = 12.sp,
            letterSpacing = 4.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Start a new case",
            style = CormorantDisplay.copy(fontSize = 42.sp),
            color = VoTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Select a document and let the Nine-Brain engine seal, analyse and " +
                "anchor the evidence before generating a court-ready report.",
            color = VoTextMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))
        HowItWorksStrip()
        Spacer(Modifier.height(20.dp))
        CapabilityGrid(
            onSealDocument = onSealDocument,
            onVerifyDocument = onVerifyDocument,
            onOpenVault = onOpenVault,
            onOpenConstitution = onOpenConstitution
        )

        Spacer(Modifier.height(32.dp))

        // Case file selection
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VoSurface, RoundedCornerShape(12.dp))
                .border(1.dp, VoBorder, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.UploadFile,
                    contentDescription = "Case file",
                    tint = VoGold,
                    modifier = Modifier.height(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "CASE FILE",
                    color = VoGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            Spacer(Modifier.height(16.dp))

            VerumSecondaryButton(
                label = if (hasFile) "Change file" else "Select case file",
                onClick = onSelectFile,
                modifier = Modifier.fillMaxWidth()
            )

            pending?.let { file ->
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VoBackground, RoundedCornerShape(12.dp))
                        .border(1.dp, VoBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    // Primary line: name · size. The raw SHA-512 stays behind Details.
                    Text(file.fileName, color = VoTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "%.1f MB".format(file.sizeBytes / (1024f * 1024f)),
                        color = VoTextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    HashDetailsExpander(label = "SHA-512", hash = file.sha512)
                }
            } ?: run {
                Spacer(Modifier.height(12.dp))
                Text(
                    "No file selected. Choose a PDF, text or Word document to begin.",
                    color = VoTextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = caseName,
                onValueChange = { caseName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Case name (optional)", color = VoTextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(16.dp))
            VerumPrimaryButton(
                label = if (isScanning) "Analysing…" else "Start Forensic Analysis",
                onClick = { onStartScan(caseName.ifBlank { "Matter" }) },
                enabled = hasFile && !isScanning,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Progress / result card
        if (isScanning || scanDone) {
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VoSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, VoBorder, RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                Text(
                    "SCAN STATUS",
                    color = VoGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    state.sealStage.label,
                    color = VoTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (isScanning) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { state.sealStage.progress },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        color = VoGold,
                        trackColor = VoBackground
                    )
                }
                if (scanDone && state.report != null) {
                    Spacer(Modifier.height(14.dp))
                    VerumPrimaryButton(
                        label = "View sealed report",
                        onClick = onOpenReport,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    VerumSecondaryButton(
                        label = "New scan",
                        onClick = { confirmNewScan = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Secondary navigation
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(
                icon = Icons.AutoMirrored.Filled.Chat,
                label = "Chat",
                onClick = onOpenChat,
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                icon = Icons.Filled.Folder,
                label = "Vault",
                onClick = onOpenVault,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Every file is SHA-512 sealed, GPS-anchored and stored in the vault before the AI reads it.",
            color = VoTextMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

/** Three-step "How it works" strip: Seal → Vault → Verify. */
@Composable
private fun HowItWorksStrip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoSurface, RoundedCornerShape(12.dp))
            .border(1.dp, VoBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 14.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        HowStep("1", "Seal")
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = VoGold, modifier = Modifier.width(14.dp))
        HowStep("2", "Vault")
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = VoGold, modifier = Modifier.width(14.dp))
        HowStep("3", "Verify")
    }
}

@Composable
private fun HowStep(number: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(number, color = VoGold, fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        Spacer(Modifier.width(6.dp))
        Text(label, color = VoTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Four-tile capability grid: Seal, Verify, Vault, Constitution. */
@Composable
private fun CapabilityGrid(
    onSealDocument: () -> Unit,
    onVerifyDocument: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenConstitution: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CapabilityTile(Icons.Filled.Verified, "Seal", "Tamper-evident PDF seal", onSealDocument, Modifier.weight(1f))
            CapabilityTile(Icons.Filled.VerifiedUser, "Verify", "Check any sealed file", onVerifyDocument, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CapabilityTile(Icons.Filled.Folder, "Vault", "Your sealed evidence", onOpenVault, Modifier.weight(1f))
            CapabilityTile(Icons.Filled.AccountBalance, "Constitution", "Governing principles", onOpenConstitution, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CapabilityTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(VoSurface)
            .border(1.dp, VoBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Icon(icon, contentDescription = title, tint = VoGold, modifier = Modifier.height(22.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, color = VoTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = VoTextMuted, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun SecondaryButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VoBorder)
    ) {
        Icon(icon, contentDescription = null, tint = VoGold, modifier = Modifier.padding(end = 6.dp))
        Text(label, color = VoTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
