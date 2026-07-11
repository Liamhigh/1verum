package com.verumomnis.forensic.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.verumomnis.forensic.R
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary

private enum class Screen { STORY, CHAT, REPORT, EMAIL, TAX, VAULT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerumApp(
    viewModel: VerumViewModel,
    onCaptureLocation: () -> Unit = {},
    onExportReport: (com.verumomnis.forensic.model.ForensicReport) -> Unit = {},
    onExportEmail: (com.verumomnis.forensic.model.SealedEmail) -> Unit = {},
    initialScreen: String = "STORY",
    initialMenuOpen: Boolean = false
) {
    val state by viewModel.state.collectAsState()
    var screen by remember { mutableStateOf(runCatching { Screen.valueOf(initialScreen) }.getOrDefault(Screen.STORY)) }
    var showMenu by remember { mutableStateOf(initialMenuOpen) }
    val context = LocalContext.current

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) onCaptureLocation() }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) onCaptureLocation() else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Everything picked goes to the FORENSIC ENGINE (sealed + vaulted), never to the chat AI.
    val sealPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                val mime = context.contentResolver.getType(uri) ?: ""
                if (mime.startsWith("image") || mime.startsWith("video")) {
                    runCatching { MediaIngestor(context).ingest(uri, state.gps, viewModel.mediaCount() + 1) }
                        .onSuccess { viewModel.addMedia(it) }
                } else {
                    runCatching { MediaIngestor(context).ingestDocument(uri) }
                        .onSuccess { viewModel.ingestDocument(it.fileName, it.mime, it.text) }
                }
            }
            viewModel.sealCase()
        }
    }
    val verifyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { MediaIngestor(context).hashOf(it) }
                .onSuccess { (name, hash) -> viewModel.verifyUploaded(name, hash) }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = VoBackground) {
        Box(modifier = Modifier.fillMaxSize()) {
            ConstellationBackground(modifier = Modifier.fillMaxSize())

            if (screen == Screen.STORY) {
                StoryScreen(onEnter = { screen = Screen.CHAT })
            } else {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        VerumTopBar(
                            state = state,
                            screen = screen,
                            onHome = { screen = Screen.CHAT },
                            onVault = { screen = Screen.VAULT },
                            onReport = { screen = Screen.REPORT }
                        )
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                        when (screen) {
                            Screen.CHAT -> ChatScreen(state, viewModel, onPlus = { showMenu = true })
                            Screen.REPORT -> ReportScreen(state, viewModel, onExportReport)
                            Screen.EMAIL -> EmailScreen(state, viewModel, onExportEmail)
                            Screen.TAX -> TaxScreen(state, viewModel)
                            Screen.VAULT -> VaultScreen(state)
                            Screen.STORY -> {}
                        }
                    }
                }
            }

            if (showMenu) {
                ModalBottomSheet(
                    onDismissRequest = { showMenu = false },
                    sheetState = rememberModalBottomSheetState(),
                    containerColor = VoBackground
                ) {
                    ActionsSheet(
                        onSealDocument = { showMenu = false; sealPicker.launch(arrayOf("application/pdf", "text/*", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) },
                        onAddMedia = { showMenu = false; sealPicker.launch(arrayOf("image/*", "video/*")) },
                        onVerify = { showMenu = false; verifyPicker.launch(arrayOf("application/pdf", "*/*")) },
                        onDeepResearch = { showMenu = false; viewModel.deepResearch() },
                        onDraftEmail = { showMenu = false; screen = Screen.EMAIL },
                        onTax = { showMenu = false; screen = Screen.TAX },
                        onReport = { showMenu = false; screen = Screen.REPORT },
                        onVault = { showMenu = false; screen = Screen.VAULT }
                    )
                }
            }
        }
    }
}

@Composable
private fun VerumTopBar(
    state: UiState,
    screen: Screen,
    onHome: () -> Unit,
    onVault: () -> Unit,
    onReport: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (screen == Screen.CHAT) {
            Image(
                painter = painterResource(R.drawable.vo_banner),
                contentDescription = "Verum Omnis",
                modifier = Modifier.height(38.dp).clip(RoundedCornerShape(8.dp))
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${state.deviceTier.label}·${state.deviceRamGb}GB",
                    color = VoTextMuted, fontFamily = JetBrainsMono, fontSize = 9.sp
                )
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = onReport) { Icon(Icons.Filled.Description, contentDescription = "Report", tint = VoGold) }
                IconButton(onClick = onVault) { Icon(Icons.Filled.Lock, contentDescription = "Vault", tint = VoGold) }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onHome) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = VoGold) }
                Spacer(Modifier.width(4.dp))
                Text(
                    when (screen) {
                        Screen.REPORT -> "Forensic Report"
                        Screen.EMAIL -> "Sealed Email"
                        Screen.TAX -> "Tax Return"
                        Screen.VAULT -> "Evidence Vault"
                        else -> ""
                    },
                    color = VoTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                )
            }
            IconButton(onClick = onVault) { Icon(Icons.Filled.Lock, contentDescription = "Vault", tint = VoGold) }
        }
    }
}

@Composable
private fun ActionsSheet(
    onSealDocument: () -> Unit,
    onAddMedia: () -> Unit,
    onVerify: () -> Unit,
    onDeepResearch: () -> Unit,
    onDraftEmail: () -> Unit,
    onTax: () -> Unit,
    onReport: () -> Unit,
    onVault: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("SEALED ACTIONS", color = VoGold, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text(
            "Everything you add is sealed by the forensic engine and stored in the vault before the AI can read it.",
            color = VoTextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        ActionRow(Icons.Filled.UploadFile, "Seal a document", "PDF / text → engine → vault", onSealDocument)
        ActionRow(Icons.Filled.PhotoCamera, "Add photo / video", "GPS + timestamp anchored, sealed", onAddMedia)
        ActionRow(Icons.Filled.VerifiedUser, "Verify a document", "Check a file against the sealed vault", onVerify)
        ActionRow(Icons.Filled.TravelExplore, "Deep research", "AI reads the sealed case file", onDeepResearch)
        ActionRow(Icons.Filled.Email, "Draft sealed email", "AI-drafted, delivered as a sealed PDF", onDraftEmail)
        ActionRow(Icons.Filled.Calculate, "Tax return", "Company or individual · 50% of accountant fee", onTax)
        ActionRow(Icons.Filled.Description, "View sealed report", "Anchored contradictions, exhibits, seal", onReport)
        ActionRow(Icons.Filled.Lock, "Open vault", "Sealed evidence, findings & seals", onVault)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = VoGold, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = VoTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = VoTextMuted, fontSize = 11.sp)
        }
    }
}
