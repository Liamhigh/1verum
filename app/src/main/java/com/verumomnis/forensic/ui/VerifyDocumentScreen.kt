package com.verumomnis.forensic.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.R
import com.verumomnis.forensic.seal.SealVerifier
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyDocumentScreen(
    state: UiState,
    viewModel: VerumViewModel,
    onBack: () -> Unit,
    onNavigateSeal: () -> Unit,
    onNavigateDocuments: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var hashInput by remember { mutableStateOf(state.verifyHashInput) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val name = viewModel.uriFileName(it, context)
                val bytes = context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() }
                bytes?.let { b -> viewModel.selectVerifyFile(b, name) }
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
                        NavLinkVerify("Seal Document", selected = false, onClick = onNavigateSeal)
                        NavLinkVerify("Verify", selected = true, onClick = {})
                        NavLinkVerify("Documents", selected = false, onClick = onNavigateDocuments)
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
            VerifyHeader()
            Spacer(Modifier.height(24.dp))
            VerifyCard(title = "Method 1: Paste SHA-512 Hash") {
                Text(
                    "If you have the SHA-512 fingerprint from a sealed document, paste it here to verify its format and check the blockchain status.",
                    fontSize = 13.sp,
                    color = VoTextMuted,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = hashInput,
                    onValueChange = { hashInput = it; viewModel.clearVerifyResult() },
                    placeholder = { Text("Paste SHA-512 hash here…", color = VoAccentBlue.copy(alpha = 0.5f)) },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VoGold,
                        unfocusedBorderColor = VoBorder,
                        focusedTextColor = VoGreen,
                        unfocusedTextColor = VoGreen,
                        focusedContainerColor = VoBackground.copy(alpha = 0.6f),
                        unfocusedContainerColor = VoBackground.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp)
                )
                Spacer(Modifier.height(12.dp))
                GoldButton("Verify Hash", enabled = hashInput.isNotBlank() && !state.verifyBusy, busy = state.verifyBusy, onClick = { viewModel.verifyHash(hashInput) })
                state.verifyResult?.takeIf { state.verifyFileBytes == null }?.let { result ->
                    Spacer(Modifier.height(16.dp))
                    ResultBox(result)
                }
            }
            Spacer(Modifier.height(20.dp))
            VerifyCard(title = "Method 2: Upload File to Compare") {
                Text(
                    "Upload the sealed PDF to extract the embedded SHA-512 fingerprint from its metadata and compare with the seal footer on your document.",
                    fontSize = 13.sp,
                    color = VoTextMuted,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(12.dp))
                UploadVerifyZone(name = state.verifyFileName, onClick = { filePicker.launch(arrayOf("application/pdf", "*/*")) })
                Spacer(Modifier.height(12.dp))
                GoldButton(
                    "Verify Document",
                    enabled = state.verifyFileBytes != null && !state.verifyBusy,
                    busy = state.verifyBusy,
                    onClick = { viewModel.verifyDocument() }
                )
                state.verifyResult?.takeIf { state.verifyFileBytes != null }?.let { result ->
                    Spacer(Modifier.height(16.dp))
                    ResultBox(result)
                }
            }
            Spacer(Modifier.height(24.dp))
            BlockchainNote()
            Spacer(Modifier.height(24.dp))
            VerifyFooter()
        }
    }
}

@Composable
private fun NavLinkVerify(label: String, selected: Boolean, onClick: () -> Unit) {
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
private fun VerifyHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Icon(
            painter = painterResource(R.drawable.vo_badge),
            contentDescription = "Verum Omnis",
            tint = VoAccentBlue,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "CLIENT-SIDE — NOTHING LEAVES YOUR DEVICE",
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
            color = VoGold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Verify Document Seal",
            fontFamily = Cormorant,
            fontWeight = FontWeight.Light,
            fontSize = 30.sp,
            color = VoTextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Verify a sealed document by its SHA-512 fingerprint or by re-computing the hash from the original file. All verification happens on your device.",
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
private fun VerifyCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoSurfaceAlt.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .border(1.dp, VoBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Text(title, fontFamily = Cormorant, fontSize = 20.sp, color = VoGold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun UploadVerifyZone(name: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .border(width = 2.dp, color = VoBorder, shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(VoBackground.copy(alpha = 0.3f))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.FileUpload, contentDescription = null, tint = VoAccentBlue, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text(if (name.isNotBlank()) name else "Drop file here or click to browse", color = VoTextPrimary, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Text("PDF or encrypted .voice — Max 50MB", fontSize = 13.sp, color = VoAccentBlue)
    }
}

@Composable
private fun GoldButton(label: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = VoGold.copy(alpha = 0.4f)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(VoGold, VoGoldSoft.copy(alpha = 0.85f)))),
            contentAlignment = Alignment.Center
        ) {
            if (busy) {
                CircularProgressIndicator(color = VoBackground, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Text(label.uppercase(), color = VoBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun ResultBox(result: VerifyResult) {
    val (background, border, textColor) = when (result.verdict) {
        SealVerifier.Verdict.VERIFIED -> Triple(VoGreen.copy(alpha = 0.08f), VoGreen.copy(alpha = 0.25f), VoGreen)
        SealVerifier.Verdict.TAMPERED -> Triple(VoRed.copy(alpha = 0.08f), VoRed.copy(alpha = 0.25f), VoRed)
        SealVerifier.Verdict.SEAL_FOUND -> Triple(VoAccentBlue.copy(alpha = 0.08f), VoAccentBlue.copy(alpha = 0.25f), VoAccentBlue)
        SealVerifier.Verdict.NO_SEAL -> Triple(VoGold.copy(alpha = 0.08f), VoGold.copy(alpha = 0.25f), VoGold)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(12.dp))
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(result.verdict.name, fontFamily = JetBrainsMono, fontSize = 12.sp, color = textColor, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(result.reason, fontSize = 14.sp, color = textColor, lineHeight = 22.sp)
        result.seal?.let { seal ->
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VoBackground.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text("Seal ID: ${seal.sealId}", fontFamily = JetBrainsMono, fontSize = 12.sp, color = VoTextPrimary)
                    Text("SHA-512: ${seal.sha512.take(24)}…", fontFamily = JetBrainsMono, fontSize = 12.sp, color = VoTextPrimary)
                    if (seal.chain.isNotEmpty()) {
                        Text("Chain: ${seal.chain.joinToString(", ")}", fontFamily = JetBrainsMono, fontSize = 12.sp, color = VoTextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockchainNote() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoGold.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .border(1.dp, VoGold.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text("⏱️ Bitcoin Blockchain Timing", fontWeight = FontWeight.Bold, color = VoGold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "OpenTimestamps anchors the SHA-256 hash into a Bitcoin transaction. This process is not instant — it typically takes 1 to 2 hours for the timestamp to be confirmed on the blockchain. During this time, the seal is still valid (the SHA-512 fingerprint proves document integrity), but the blockchain timestamp is pending. You can download the .OTS proof file and verify it independently at opentimestamps.org once confirmation is complete.",
            fontSize = 13.sp,
            color = VoTextMuted,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun VerifyFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .border(1.dp, VoBorder.copy(alpha = 0.5f), RoundedCornerShape(0.dp))
            .padding(top = 20.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Verum Omnis Foundation — Patent Pending", fontFamily = JetBrainsMono, fontSize = 11.sp, color = VoAccentBlue, letterSpacing = 0.8.sp)
        Text("Constitution v6.0 Final — Article X Non-Weaponization Doctrine", fontFamily = JetBrainsMono, fontSize = 11.sp, color = VoAccentBlue, letterSpacing = 0.8.sp)
    }
}
