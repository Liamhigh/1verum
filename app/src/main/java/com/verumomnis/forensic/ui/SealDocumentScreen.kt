package com.verumomnis.forensic.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.verumomnis.forensic.R
import com.verumomnis.forensic.pdf.QrCodeGenerator
import com.verumomnis.forensic.seal.SealMetadataCodec
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.SourceSans
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoGoldSoft
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
    onNavigateVerify: () -> Unit,
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
                actions = {
                    Row(modifier = Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        NavLink("Seal Document", selected = true, onClick = {})
                        NavLink("Verify", selected = false, onClick = onNavigateVerify)
                        NavLink("Documents", selected = false, onClick = onNavigateDocuments)
                    }
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
            HeaderSection()
            Spacer(Modifier.height(24.dp))
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
            SealButton(enabled = state.sealPdfBytes != null && !state.sealBusy, busy = state.sealBusy, onClick = { viewModel.sealDocument() })
            if (state.sealError.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(state.sealError, color = VoRed, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            if (state.sealPipeline.any { it.status != SealPipelineStepStatus.PENDING } || state.sealBusy) {
                Spacer(Modifier.height(24.dp))
                PipelineSection(state.sealPipeline)
            }
            state.sealResult?.let { result ->
                Spacer(Modifier.height(24.dp))
                ResultsCard(result = result, viewModel = viewModel, context = context, onVerifyClick = onNavigateVerify)
            }
            Spacer(Modifier.height(32.dp))
            InfoSection()
            Spacer(Modifier.height(32.dp))
            Footer()
        }
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
        Icon(
            painter = painterResource(R.drawable.vo_badge),
            contentDescription = "Verum Omnis",
            tint = VoAccentBlue,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "AI FORENSICS FOR TRUTH — CLIENT-SIDE",
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
            color = VoGold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Document Sealing Service",
            fontFamily = Cormorant,
            fontWeight = FontWeight.Light,
            fontSize = 34.sp,
            color = VoTextPrimary,
            textAlign = TextAlign.Center
        )
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

@Composable
private fun UploadZone(hasFile: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .border(width = 2.dp, color = VoBorder, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(VoSurfaceAlt.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.FileUpload, contentDescription = null, tint = if (hasFile) VoGold else VoAccentBlue, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("Upload PDF Document", fontFamily = Cormorant, fontSize = 22.sp, color = VoTextPrimary)
        Spacer(Modifier.height(6.dp))
        Text("Tap to browse. Max 50MB.", fontSize = 13.sp, color = VoAccentBlue)
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
            Text("📎", fontSize = 14.sp)
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
        SealTypeChip(label = "🏠 Private (Free)", active = selected == "private", onClick = { onSelect("private") })
        SealTypeChip(label = "🏢 Commercial", active = selected == "commercial", onClick = { onSelect("commercial") })
    }
}

@Composable
private fun SealTypeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .border(1.dp, if (active) VoGold else VoBorder, RoundedCornerShape(10.dp))
            .background(if (active) VoGold.copy(alpha = 0.10f) else Color.Transparent, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Text(label, color = if (active) VoGold else VoAccentBlue, fontSize = 15.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
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
            Text("🔒 Password protect this document (delivery receipt mode)", fontSize = 14.sp, color = VoTextMuted)
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

@Composable
private fun SealButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = VoGold.copy(alpha = 0.4f)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(VoGold, VoGoldSoft.copy(alpha = 0.85f)))),
            contentAlignment = Alignment.Center
        ) {
            if (busy) {
                CircularProgressIndicator(color = VoBackground, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Text("SEAL DOCUMENT", color = VoBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp)
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
private fun ResultsCard(result: SealResult, viewModel: VerumViewModel, context: Context, onVerifyClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoSurfaceAlt.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .border(1.dp, VoBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Text("Document Sealed", fontFamily = Cormorant, fontSize = 24.sp, color = VoGold)
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(VoGold.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                .border(1.dp, VoGold.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            Text("⏳ OpenTimestamps proof pending Bitcoin blockchain confirmation (typically 1-2 hours)", color = VoGold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        PreviewSection()
        Spacer(Modifier.height(16.dp))
        QrPreview(result.verifyUrl)
        Spacer(Modifier.height(16.dp))
        HashRow("SHA-256 (OpenTimestamps)", result.sha256, VoGreen)
        Spacer(Modifier.height(12.dp))
        HashRow("SHA-512 (Verum Fingerprint)", result.sha512, VoGreen)
        Spacer(Modifier.height(12.dp))
        HashRow("Seal ID", result.sealId, VoGold)
        if (result.priorChain.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HashRow("Chain of Custody", result.priorChain.joinToString(", "), VoAccentBlue)
        }
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
        Text("How Document Sealing Works", fontFamily = Cormorant, fontSize = 26.sp, color = VoGold)
        Spacer(Modifier.height(10.dp))
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
            InfoItem("📄 A4 Watermark Background", "The full-page Verum Omnis watermark is applied at 20% opacity behind your content. Your document stays at full original size for maximum readability when printed.", Modifier.weight(1f))
            InfoItem("📱 Clean QR Code", "QR code modules only — no border, no box, no interfering elements. Positioned top-right with natural white quiet zone and subtle gray panel. Scans instantly with any phone.", Modifier.weight(1f))
            InfoItem("🔒 Dual Hash", "SHA-256 for OpenTimestamps blockchain anchoring. SHA-512 as the Verum forensic fingerprint. Two independent hashes, zero trust required.", Modifier.weight(1f))
            InfoItem("⛓️ Bitcoin Blockchain", "OpenTimestamps anchors the SHA-256 hash into Bitcoin. Once confirmed (~1-2 hours), the timestamp is permanently and independently verifiable.", Modifier.weight(1f))
            InfoItem("👤 Identity Pipeline", "Optional sender identity (name, ID, address, email) encoded into the QR code for affidavit pre-fill and chain of custody.", Modifier.weight(1f))
            InfoItem("📍 GPS + Device", "Automatic geolocation and device fingerprint capture. Proves where and from what device the seal was applied.", Modifier.weight(1f))
            InfoItem("🔐 Password Protection", "Optional AES-256 password protection with delivery receipt cover page. Recipient must email sender for password — that email IS the read receipt.", Modifier.weight(1f))
            InfoItem("🛡️ Tamper Detection", "Recipient uploads document to verify page. If SHA-512 doesn't match —> 'TAMPERED — DO NOT ACCEPT'. Cryptographically impossible to forge.", Modifier.weight(1f))
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .border(1.dp, VoBorder.copy(alpha = 0.5f), RoundedCornerShape(0.dp))
            .padding(top = 20.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Verum Omnis Foundation — Patent Pending", fontFamily = JetBrainsMono, fontSize = 11.sp, color = VoAccentBlue, letterSpacing = 0.8.sp)
        Text("Constitution v6.0 Final — Article X Non-Weaponization Doctrine", fontFamily = JetBrainsMono, fontSize = 11.sp, color = VoAccentBlue, letterSpacing = 0.8.sp)
    }
}

private fun formatFileSize(size: Long): String {
    val mb = size / (1024 * 1024)
    return if (mb > 0) "%.2f MB".format(size / (1024.0 * 1024.0)) else "%.0f KB".format(size / 1024.0)
}
