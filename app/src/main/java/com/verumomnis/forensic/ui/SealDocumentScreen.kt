package com.verumomnis.forensic.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.core.Constitution
import com.verumomnis.forensic.R
import com.verumomnis.forensic.pdf.QrCodeGenerator
import com.verumomnis.forensic.seal.SealMetadataCodec
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.SourceSans
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoBlueBorder
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoGoldDark
import com.verumomnis.forensic.ui.theme.VoGoldSoft
import com.verumomnis.forensic.ui.theme.VoHeading
import com.verumomnis.forensic.ui.theme.VoGreen
import com.verumomnis.forensic.ui.theme.VoRed
import com.verumomnis.forensic.ui.theme.VoSurfaceAlt
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary
import com.verumomnis.forensic.ui.theme.VoTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SealDocumentScreen(
    state: UiState,
    viewModel: VerumViewModel,
    onBack: () -> Unit,
    onNavigateReport: () -> Unit = {},
    onNavigateDocuments: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val name = viewModel.uriFileName(it, context)
                val bytes = context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() }
                bytes?.let { b -> viewModel.selectPdfForSealing(b, name, b.size.toLong()) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                NavLink("Seal Document", selected = true, onClick = {})
                Spacer(Modifier.width(24.dp))
                NavLink("Verify on Website", selected = false, onClick = { openWebsiteVerify(context) })
            }
            Spacer(Modifier.height(8.dp))
            HeaderSection()
            Spacer(Modifier.height(20.dp))
            LawEnforcementBanner()
            Spacer(Modifier.height(20.dp))
            UploadZone(
                hasFile = state.sealPdfBytes != null,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
            if (state.sealPdfBytes != null) {
                Spacer(Modifier.height(12.dp))
                FileInfoCard(name = state.sealPdfName, size = formatFileSize(state.sealPdfSize), onClear = { viewModel.clearSealDocument() })
            }
            Spacer(Modifier.height(20.dp))
            SealTypeSelector(selected = state.sealType, onSelect = viewModel::setSealType)
            Spacer(Modifier.height(20.dp))
            SealingModeSelector(selected = state.sealMode, onSelect = viewModel::setSealMode)
            if (state.sealType == "commercial") {
                Spacer(Modifier.height(16.dp))
                Label("Organisation Name")
                VoTextField(value = state.sealOrganisation, onValueChange = viewModel::setSealOrganisation, placeholder = "e.g., Standard Bank, FNB, Legal Firm…")
            }
            Spacer(Modifier.height(20.dp))
            IdentitySection(state.sealIdentity, viewModel::setIdentity)
            Spacer(Modifier.height(20.dp))
            PasswordSection(
                enabled = state.passwordProtect,
                onToggle = viewModel::setPasswordProtect,
                password = state.sealPassword,
                confirm = state.sealPasswordConfirm,
                onPasswordChange = { p, c -> viewModel.setPassword(p, c) }
            )
            Spacer(Modifier.height(24.dp))
            SealButton(
                label = if (state.sealMode == "forensic") "Seal + Forensic Scan" else "Seal Document",
                enabled = state.sealPdfBytes != null && !state.sealBusy,
                busy = state.sealBusy,
                onClick = { viewModel.sealDocument() }
            )
            if (state.sealError.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(state.sealError, color = VoRed, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            if (state.sealPipeline.any { it.status != SealPipelineStepStatus.PENDING } || state.sealBusy) {
                Spacer(Modifier.height(24.dp))
                PipelineSection(state.sealPipeline)
            }
            if (state.sealMode == "forensic") {
                ForensicScanStatus(state = state, onOpenReport = onNavigateReport)
            }
            state.sealResult?.let { result ->
                Spacer(Modifier.height(24.dp))
                ResultsCard(result = result, documentName = state.sealPdfName, viewModel = viewModel, context = context, onVerifyClick = { openWebsiteVerify(context) })
            }
            Spacer(Modifier.height(32.dp))
            InfoSection()
            Spacer(Modifier.height(32.dp))
            Footer()
    }
}

@Composable
private fun NavLink(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label.uppercase(),
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        color = if (selected) VoGold else VoAccentBlue,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun HeaderSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(R.drawable.vo_banner),
            contentDescription = "Verum Omnis",
            modifier = Modifier.fillMaxWidth(0.7f)
        )
        Spacer(Modifier.height(16.dp))
        // Eyebrow kicker (§2.1): mono, uppercase, ls 0.15em, gold.
        VoKicker("AI Forensics for Truth — Client-Side — Nothing Leaves Your Device", center = true)
        Spacer(Modifier.height(12.dp))
        // Page h1 (§2.2): serif light, tight tracking, off-white.
        VoSerifHeading("Document Sealing Service", fontSize = 34, center = true)
        Spacer(Modifier.height(8.dp))
        Text(
            "Apply a forensic-grade cryptographic seal with A4 watermark, clean QR code, SHA-512 fingerprint, identity verification, GPS/device tracking, optional password protection, and Bitcoin blockchain anchoring via OpenTimestamps.",
            fontFamily = SourceSans,
            fontSize = 15.sp,
            color = VoTextMuted,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.95f)
        )
    }
}

/**
 * Upload zone mirroring the website: dashed blue border that turns gold once
 * a file is selected, 16dp radius, faint blue-tinted background.
 */
@Composable
private fun UploadZone(hasFile: Boolean, onClick: () -> Unit) {
    val borderColor = if (hasFile) VoGold else VoBlueBorder
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12.dp.toPx() / 2, 8.dp.toPx() / 2))
                    ),
                    cornerRadius = CornerRadius(16.dp.toPx())
                )
            }
            .clip(RoundedCornerShape(16.dp))
            .background(VoSurfaceAlt.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.FileUpload, contentDescription = null, tint = if (hasFile) VoGold else VoAccentBlue, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            "Upload PDF Document(s) — sealed as one",
            fontFamily = Cormorant,
            fontSize = 22.sp,
            color = VoHeading,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        // Website parity, and the engine's real limit. This read "Max 50MB" while
        // MediaIngestor.MAX_FILE_SIZE_BYTES has been 150 MB — the screen turned
        // away case bundles the app could already seal, and contradicted the
        // figure verumglobal.foundation quotes for the same service.
        Text(
            "Tap to browse — up to 10 PDFs. Multiple documents are merged and " +
                "sealed as ONE: a single sealed document and a single certificate, " +
                "not one per file. Max 150MB total.",
            fontSize = 13.sp,
            color = VoAccentBlue,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.85f)
        )
    }
}

/**
 * LE banner (§3.9): 135° blue→gold wash gradient, 2dp solid blue border,
 * 12dp radius, mono green uppercase title.
 */
@Composable
private fun LawEnforcementBanner() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(VoAccentBlue.copy(alpha = 0.15f), VoGold.copy(alpha = 0.10f))
                ),
                RoundedCornerShape(12.dp)
            )
            .border(2.dp, VoAccentBlue, RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "FREE FOR LAW ENFORCEMENT & PRIVATE CITIZENS",
            color = VoGreen,
            fontFamily = JetBrainsMono,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Secure document sealing with forensic verification — powered by the Verum Omnis cryptographic platform. Commercial sealing requires licensing.",
            color = VoTextMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Sealing mode selector mirroring the website's mode cards:
 * "Seal Only — Tamper Protection" (default) vs "Seal + Forensic Analysis".
 */
@Composable
private fun SealingModeSelector(selected: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Label("Sealing Mode")
        ModeCard(
            icon = Icons.Filled.Shield,
            title = "Seal Only — Tamper Protection (default)",
            body = "Cryptographic seal + blockchain timestamp only. No content is analysed and nothing leaves this device. You receive the sealed PDF and a Seal Certificate.",
            active = selected == "seal-only",
            onClick = { onSelect("seal-only") }
        )
        Spacer(Modifier.height(10.dp))
        ModeCard(
            icon = Icons.Filled.Search,
            title = "Seal + Forensic Analysis",
            body = "Everything in Seal Only, plus an on-device forensic scan by the Nine-Brain engine (findings for human review) and a sealed forensic report.",
            active = selected == "forensic",
            onClick = { onSelect("forensic") }
        )
    }
}

@Composable
private fun ModeCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) VoGold.copy(alpha = 0.08f) else VoSurfaceAlt.copy(alpha = 0.08f))
            .border(1.dp, if (active) VoGold else VoBlueBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (active) VoGold else VoAccentBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title,
                color = if (active) VoGold else VoTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(body, color = if (active) VoTextPrimary else VoTextMuted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

/**
 * Live status of the Nine-Brain forensic scan that runs after sealing in
 * "Seal + Forensic Analysis" mode, with a link to the sealed report.
 */
@Composable
private fun ForensicScanStatus(state: UiState, onOpenReport: () -> Unit) {
    val scanning = state.sealStage != SealStage.IDLE && state.sealStage != SealStage.DONE && state.sealStage != SealStage.ERROR
    val done = state.report != null
    if (!scanning && !done) return
    Spacer(Modifier.height(24.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoSurfaceAlt.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .border(1.dp, VoBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text("Forensic Scan", fontFamily = Cormorant, fontSize = 20.sp, color = VoGold)
        Spacer(Modifier.height(10.dp))
        if (scanning) {
            Text(state.sealStage.label, color = VoTextPrimary, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { state.sealStage.progress },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                color = VoGold,
                trackColor = VoBackground
            )
        }
        if (done) {
            Text(
                "Scan complete. Findings are forensic indicators for human review — not determinations of fraud.",
                color = VoTextMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            VerumPrimaryButton(
                label = "View Forensic Report",
                onClick = onOpenReport,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * The website is the central verification hub (Constitution §7) — verify
 * actions open the canonical URL embedded in every seal QR.
 */
private fun openWebsiteVerify(context: Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SealMetadataCodec.VERIFY_BASE_URL)))
    }
}

@Composable
private fun FileInfoCard(name: String, size: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoSurfaceAlt.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .border(1.dp, VoBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.AttachFile, contentDescription = null, tint = VoGold, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(name, color = VoTextPrimary, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(size, color = VoAccentBlue, fontSize = 13.sp)
        }
        IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = VoAccentBlue)
        }
    }
}

@Composable
private fun SealTypeSelector(selected: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
        SealTypeChip(icon = Icons.Filled.Home, label = "Private (Free)", active = selected == "private", onClick = { onSelect("private") })
        SealTypeChip(icon = Icons.Filled.Business, label = "Commercial", active = selected == "commercial", onClick = { onSelect("commercial") })
    }
}

/** Seal-type chip (§3.9 pill): mono uppercase, gold when active. */
@Composable
private fun SealTypeChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .border(1.dp, if (active) VoGold.copy(alpha = 0.4f) else VoBorder, RoundedCornerShape(999.dp))
            .background(if (active) VoGold.copy(alpha = 0.10f) else Color.Transparent, RoundedCornerShape(999.dp))
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (active) VoGold else VoAccentBlue, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label.uppercase(),
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
            letterSpacing = 1.2.sp,
            color = if (active) VoGold else VoAccentBlue,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun IdentitySection(identity: SealIdentityInput, onIdentityChange: (SealIdentityInput) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expanded = !expanded }) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = VoAccentBlue, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add Sender Identity (optional — for affidavit pre-fill, chain of custody)", fontFamily = JetBrainsMono, fontSize = 11.sp, color = VoAccentBlue)
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VoSurfaceAlt.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .border(1.dp, VoBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Label("Full Name")
                VoTextField(
                    value = identity.fullName,
                    onValueChange = { onIdentityChange(identity.copy(fullName = it)) },
                    placeholder = "e.g., John van der Merwe"
                )
                Spacer(Modifier.height(10.dp))
                Label("ID / Passport Number")
                VoTextField(
                    value = identity.idNumber,
                    onValueChange = { onIdentityChange(identity.copy(idNumber = it)) },
                    placeholder = "e.g., 760101 1234 087"
                )
                Spacer(Modifier.height(10.dp))
                Label("Physical Address")
                VoTextField(
                    value = identity.address,
                    onValueChange = { onIdentityChange(identity.copy(address = it)) },
                    placeholder = "e.g., 12 Main Street, Sandton, Johannesburg"
                )
                Spacer(Modifier.height(10.dp))
                Label("Contact Email")
                VoTextField(
                    value = identity.email,
                    onValueChange = { onIdentityChange(identity.copy(email = it)) },
                    placeholder = "e.g., john@email.com",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done)
                )
            }
        }
    }
}

@Composable
private fun PasswordSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    password: String,
    confirm: String,
    onPasswordChange: (String, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onToggle(!enabled); expanded = !enabled }) {
            Checkbox(
                checked = enabled,
                onCheckedChange = { onToggle(it); expanded = it },
                colors = CheckboxDefaults.colors(checkedColor = VoGold, checkmarkColor = VoBackground, uncheckedColor = VoBorder)
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.Lock, contentDescription = null, tint = VoGold, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Password protect this document (delivery receipt mode)", fontSize = 14.sp, color = VoTextMuted)
        }
        if (enabled) {
            Spacer(Modifier.height(12.dp))
            Column {
                Text(
                    "The recipient must contact you to request this password before they can open the document. You will know they received it and opened it. This is your delivery receipt.",
                    fontSize = 12.sp,
                    color = VoTextSecondary,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                Label("Document Password")
                VoTextField(
                    value = password,
                    onValueChange = { onPasswordChange(it, confirm) },
                    placeholder = "Min 8 characters",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Spacer(Modifier.height(10.dp))
                Label("Confirm Password")
                VoTextField(
                    value = confirm,
                    onValueChange = { onPasswordChange(password, it) },
                    placeholder = "Re-enter password",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        fontFamily = JetBrainsMono,
        fontSize = 10.sp,
        letterSpacing = 0.8.sp,
        color = VoAccentBlue,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun VoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = VoAccentBlue.copy(alpha = 0.5f)) },
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VoGold,
            unfocusedBorderColor = VoBorder,
            focusedTextColor = VoTextPrimary,
            unfocusedTextColor = VoTextPrimary,
            focusedContainerColor = VoSurfaceAlt.copy(alpha = 0.15f),
            unfocusedContainerColor = VoSurfaceAlt.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Seal CTA (§3.4): `linear-gradient(135deg, #D4A843, #b8942a)` fill, navy
 * mono uppercase text (never white), 12dp radius, 0.4 opacity when disabled.
 */
@Composable
private fun SealButton(label: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .alpha(if (enabled) 1f else 0.4f),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(VoGold, VoGoldDark))),
            contentAlignment = Alignment.Center
        ) {
            if (busy) {
                CircularProgressIndicator(color = VoBackground, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    label.uppercase(),
                    color = VoBackground,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}

@Composable
private fun PipelineSection(pipeline: List<SealPipelineStep>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoSurfaceAlt.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .border(1.dp, VoBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text("Sealing Pipeline", fontFamily = Cormorant, fontSize = 20.sp, color = VoGold)
        Spacer(Modifier.height(14.dp))
        pipeline.forEach { step ->
            PipelineStepRow(step)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PipelineStepRow(step: SealPipelineStep) {
    val (iconColor, icon) = when (step.status) {
        SealPipelineStepStatus.PENDING -> VoBorder to "○"
        SealPipelineStepStatus.PROCESSING -> VoGold to "◐"
        SealPipelineStepStatus.COMPLETE -> VoGreen to "✓"
        SealPipelineStepStatus.ERROR -> VoRed to "✕"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(2.dp, iconColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, color = iconColor, fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(step.name, color = VoTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(step.detail, color = VoAccentBlue, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ResultsCard(result: SealResult, documentName: String, viewModel: VerumViewModel, context: Context, onVerifyClick: () -> Unit) {
    // Sealed-at display timestamp — the actual sealing time carried by the
    // seal metadata (SealMetadata.t), never the time this card composes.
    val sealedAt = remember(result.sealedAtMs) {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date(result.sealedAtMs))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoSurfaceAlt.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .border(1.dp, VoBorder, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        VoKicker("Seal Applied")
        Spacer(Modifier.height(6.dp))
        Text("Document Sealed", fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 24.sp, color = VoGold)
        Spacer(Modifier.height(14.dp))
        VoHonestyNote(
            title = "OpenTimestamps proof pending",
            text = "The Bitcoin blockchain confirmation typically completes within 1–2 hours. " +
                "The SHA-512 fingerprint already proves document integrity."
        )
        Spacer(Modifier.height(16.dp))
        // Document metadata as .id-field label/value rows (§3.3).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VoSurfaceAlt.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .border(1.dp, VoBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            VoIdField("Document", documentName.ifBlank { "Sealed document" })
            VoIdField("SHA-512", result.sha512.take(16) + "…" + result.sha512.takeLast(16), valueColor = VoGreen)
            VoIdField("Sealed", sealedAt)
            VoIdField("Seal ID", result.sealId, valueColor = VoGold, lastRow = result.priorChain.isEmpty())
            if (result.priorChain.isNotEmpty()) {
                VoIdField("Chain of Custody", result.priorChain.joinToString(", "), valueColor = VoAccentBlue, lastRow = true)
            }
        }
        Spacer(Modifier.height(16.dp))
        PreviewSection()
        Spacer(Modifier.height(16.dp))
        QrPreview(result.verifyUrl)
        Spacer(Modifier.height(16.dp))
        HashRow("SHA-256 (OpenTimestamps)", result.sha256, VoGreen)
        Spacer(Modifier.height(12.dp))
        HashRow("SHA-512 (Verum Fingerprint)", result.sha512, VoGreen)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onVerifyClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VoGold),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(VoBorder, VoBorder)))
        ) {
            Icon(Icons.Filled.Visibility, contentDescription = null, tint = VoGold)
            Spacer(Modifier.width(8.dp))
            Text("Verify on verumglobal.foundation", color = VoGold)
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DownloadButton(
                label = "Download Sealed PDF",
                primary = true,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.shareWebsiteSealedFile(context) }
            )
            DownloadButton(
                label = "Download .OTS Proof",
                primary = false,
                modifier = Modifier.weight(1f),
                onClick = { shareOtsProof(context, result) }
            )
        }
    }
}

@Composable
private fun PreviewSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoBackground.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .border(1.dp, VoBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text("What Your Sealed Document Looks Like", fontFamily = Cormorant, fontSize = 18.sp, color = VoGold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Every page has the A4 watermark as a background layer at 20% opacity. Your document content stays at full size. A clean QR code with a subtle gray panel sits in the top-right corner. The seal footer with your SHA-512 fingerprint appears at the bottom of every page.",
            fontSize = 13.sp,
            color = VoTextMuted,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun QrPreview(verifyUrl: String) {
    val context = LocalContext.current
    val qrBitmap = remember(verifyUrl) { generateQrBitmap(context, verifyUrl) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        qrBitmap?.let {
            androidx.compose.foundation.Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "QR preview",
                modifier = Modifier.size(160.dp)
            )
        } ?: Box(modifier = Modifier.size(160.dp).background(Color.White, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.QrCode2, contentDescription = null, tint = Color.Black)
        }
        Spacer(Modifier.height(8.dp))
        Text("Clean QR — no border, no interfering box. Scans instantly.", fontSize = 12.sp, color = VoAccentBlue)
    }
}

private fun generateQrBitmap(context: Context, content: String): Bitmap? {
    return try {
        QrCodeGenerator.generate(content, 400)
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun HashRow(label: String, value: String, valueColor: Color) {
    Column {
        Text(label.uppercase(), fontFamily = JetBrainsMono, fontSize = 10.sp, color = VoAccentBlue, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(VoBackground.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .border(1.dp, VoBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            Text(value, fontFamily = JetBrainsMono, fontSize = 12.sp, color = valueColor)
        }
    }
}

@Composable
private fun DownloadButton(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (primary) Brush.horizontalGradient(listOf(VoGold, VoGoldSoft.copy(alpha = 0.85f))) else null
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Color.Transparent else VoGold.copy(alpha = 0.08f),
            contentColor = if (primary) VoBackground else VoGold
        ),
        border = if (!primary) androidx.compose.foundation.BorderStroke(1.dp, VoGold) else null,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        Box(
            modifier = if (bg != null) Modifier.fillMaxSize().background(bg) else Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold)
        }
    }
}

private fun shareOtsProof(context: Context, result: SealResult) {
    result.otsProof?.let { proof ->
        val dir = File(context.filesDir, "vault/reports/sealed").apply { mkdirs() }
        val file = File(dir, "sealed_${result.sealId}.ots")
        file.writeBytes(proof)
        com.verumomnis.forensic.pdf.SealedPdfExporter(context).share(file)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        VoKicker("The Sealing Pipeline")
        Spacer(Modifier.height(6.dp))
        Text("How Document Sealing Works", fontFamily = Cormorant, fontWeight = FontWeight.Normal, fontSize = 26.sp, color = VoGold)
        Spacer(Modifier.height(8.dp))
        VoGoldRule()
        Spacer(Modifier.height(12.dp))
        Text(
            "Every document sealed by Verum Omnis receives a forensic-grade cryptographic seal that makes it tamper-evident and court-admissible.",
            fontSize = 15.sp,
            color = VoTextMuted,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(16.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2
        ) {
            InfoItem("A4 Watermark Background", "The full-page Verum Omnis watermark is applied at 20% opacity behind your content. Your document stays at full original size for maximum readability when printed.", Modifier.weight(1f))
            InfoItem("Clean QR Code", "QR code modules only — no border, no box, no interfering elements. Positioned top-right with natural white quiet zone and subtle gray panel. Scans instantly with any phone.", Modifier.weight(1f))
            InfoItem("Dual Hash", "SHA-256 for OpenTimestamps blockchain anchoring. SHA-512 as the Verum forensic fingerprint. Two independent hashes, zero trust required.", Modifier.weight(1f))
            InfoItem("Bitcoin Blockchain", "OpenTimestamps anchors the SHA-256 hash into Bitcoin. Once confirmed (~1-2 hours), the timestamp is permanently and independently verifiable.", Modifier.weight(1f))
            InfoItem("Identity Pipeline", "Optional sender identity (name, ID, address, email) encoded into the QR code for affidavit pre-fill and chain of custody.", Modifier.weight(1f))
            InfoItem("GPS + Device", "Automatic geolocation and device fingerprint capture. Proves where and from what device the seal was applied.", Modifier.weight(1f))
            InfoItem("Password Protection", "Optional AES-256 password protection with delivery receipt cover page. Recipient must email sender for password — that email IS the read receipt.", Modifier.weight(1f))
            InfoItem("Tamper Detection", "Recipient uploads document to the Verification Hub. If SHA-512 doesn't match — 'TAMPERED — DO NOT ACCEPT'. Cryptographically impossible to forge.", Modifier.weight(1f))
        }
    }
}

@Composable
private fun InfoItem(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(VoSurfaceAlt.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .border(1.dp, VoBorder.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(title, fontFamily = Cormorant, fontSize = 17.sp, color = VoGold, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 12.sp, color = VoAccentBlue, lineHeight = 18.sp)
    }
}

@Composable
private fun Footer() {
    // Canonical seal footer bar (§3.7): top hairline + centered mono blue copy.
    VoSealFooter()
}

private fun formatFileSize(size: Long): String {
    val mb = size / (1024 * 1024)
    return if (mb > 0) "%.2f MB".format(size / (1024.0 * 1024.0)) else "%.0f KB".format(size / 1024.0)
}
