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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.TaskAlt
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private enum class Screen { STORY, SCAN_HOME, CHAT, REPORT, EMAIL, TAX, VAULT, SEAL_DOCUMENT, VERIFY_DOCUMENT, SCAN_SEAL, SCAN_SEAL_RESULT, CONSTITUTION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerumApp(
    viewModel: VerumViewModel,
    onCaptureLocation: () -> Unit = {},
    onExportReport: (com.verumomnis.forensic.model.ForensicReport) -> Unit = {},
    onExportEmail: (com.verumomnis.forensic.model.SealedEmail) -> Unit = {},
    onReadConstitution: () -> Unit = {},
    initialScreen: String = "STORY",
    initialMenuOpen: Boolean = false
) {
    val state by viewModel.state.collectAsState()
    var screen by remember { mutableStateOf(runCatching { Screen.valueOf(initialScreen) }.getOrDefault(Screen.SCAN_HOME)) }
    var showMenu by remember { mutableStateOf(initialMenuOpen) }
    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) onCaptureLocation() }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) onCaptureLocation() else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // A constitutional hard stop dismisses any open menu and blocks the entire UI.
    LaunchedEffect(state.guardianBlock) {
        if (state.guardianBlock != null) showMenu = false
    }

    // Auto-advance from the scan home to the report once the seal pipeline completes.
    LaunchedEffect(state.sealStage) {
        if (screen == Screen.SCAN_HOME && state.sealStage == SealStage.DONE && state.report != null) {
            screen = Screen.REPORT
        }
    }

    // Everything picked is validated, hashed and previewed before the user confirms the seal.
    val sealPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val gps = state.gps
        val resolver = context.contentResolver
        scope.launch(Dispatchers.IO) {
            viewModel.setSealStage(SealStage.HASHING)
            val ingestor = MediaIngestor(context)
            val previews = mutableListOf<PendingFilePreview>()
            val errors = mutableListOf<String>()
            val baseMediaCount = viewModel.mediaCount()
            uris.forEachIndexed { index, uri ->
                val mime = resolver.getType(uri) ?: ""
                val result = runCatching {
                    if (mime.startsWith("image") || mime.startsWith("video")) {
                        ingestor.ingest(uri, gps, baseMediaCount + index + 1)
                    } else {
                        ingestor.ingestDocument(uri)
                    }
                }.getOrElse { IngestResult.Error.ReadFailed(it.message ?: "unknown error") }

                when (result) {
                    is IngestResult.DocumentSuccess -> previews += PendingFilePreview(
                        fileName = result.fileName,
                        mimeType = result.mimeType,
                        sizeBytes = result.sizeBytes,
                        sha512 = result.sha512,
                        displayText = result.text.take(280) + if (result.text.length > 280) "…" else "",
                        isMedia = false,
                        documentText = result.text
                    )
                    is IngestResult.MediaSuccess -> previews += PendingFilePreview(
                        fileName = result.evidence.fileName,
                        mimeType = result.evidence.mimeType,
                        sizeBytes = result.sizeBytes,
                        sha512 = result.evidence.sha512,
                        displayText = buildString {
                            append(result.evidence.kind.name)
                            result.evidence.width?.let { append(" · ${it}×${result.evidence.height ?: 0}") }
                            result.evidence.durationMs?.let { append(" · ${it / 1000}s") }
                        },
                        isMedia = true,
                        mediaEvidence = result.evidence
                    )
                    is IngestResult.Error -> errors += "${uri.lastPathSegment ?: result.javaClass.simpleName}: ${result.message}"
                }
            }
            launch(Dispatchers.Main) {
                if (previews.isNotEmpty()) {
                    viewModel.setPendingFiles(previews)
                } else {
                    viewModel.setSealStage(SealStage.IDLE)
                }
                errors.forEach { viewModel.postEngine("Upload error · $it") }
            }
        }
    }
    val verifyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { MediaIngestor(context).hashOf(it) }
                .onSuccess { (name, hash) -> viewModel.verifyUploaded(name, hash) }
        }
    }
    val websiteSealPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.sealDocumentWebsiteFormat(it, context) }
    }
    val sealDocumentPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val name = viewModel.uriFileName(it, context)
                val bytes = context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() }
                bytes?.let { b ->
                    viewModel.selectPdfForSealing(b, name, b.size.toLong())
                    launch(Dispatchers.Main) { screen = Screen.SEAL_DOCUMENT }
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = VoBackground) {
        Box(modifier = Modifier.fillMaxSize()) {
            ConstellationBackground(modifier = Modifier.fillMaxSize())

            if (screen == Screen.STORY) {
                StoryScreen(
                    onEnter = { screen = Screen.SCAN_HOME },
                    onReadConstitution = { screen = Screen.CONSTITUTION }
                )
            } else {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        VerumTopBar(
                            state = state,
                            screen = screen,
                            onHome = { screen = Screen.SCAN_HOME },
                            onVault = { screen = Screen.VAULT },
                            onReport = { screen = Screen.REPORT }
                        )
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                        when (screen) {
                            Screen.SCAN_HOME -> ScanHomeScreen(
                                state = state,
                                onSelectFile = { sealPicker.launch(arrayOf("application/pdf", "text/*", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) },
                                onStartScan = { caseName -> viewModel.startForensicScan(caseName) },
                                onNewScan = { viewModel.clearCase(); screen = Screen.SCAN_HOME },
                                onOpenChat = { screen = Screen.CHAT },
                                onOpenVault = { screen = Screen.VAULT },
                                onOpenReport = { screen = Screen.REPORT }
                            )
                            Screen.CHAT -> ChatScreen(state, viewModel, onPlus = { showMenu = true })
                            Screen.REPORT -> ReportScreen(
                                state = state,
                                viewModel = viewModel,
                                onExportReport = onExportReport,
                                onNewScan = { viewModel.clearCase(); screen = Screen.SCAN_HOME }
                            )
                            Screen.EMAIL -> EmailScreen(state, viewModel, onExportEmail)
                            Screen.TAX -> TaxScreen(viewModel)
                            Screen.VAULT -> VaultScreen(state)
                            Screen.STORY -> {}
                            Screen.SEAL_DOCUMENT -> SealDocumentScreen(
                                state = state,
                                viewModel = viewModel,
                                onBack = { screen = Screen.SCAN_HOME },
                                onNavigateVerify = { screen = Screen.VERIFY_DOCUMENT },
                                onNavigateDocuments = { /* no-op — documents screen not implemented */ }
                            )
                            Screen.VERIFY_DOCUMENT -> VerifyDocumentScreen(
                                state = state,
                                viewModel = viewModel,
                                onBack = { screen = Screen.SCAN_HOME },
                                onNavigateSeal = { screen = Screen.SEAL_DOCUMENT },
                                onNavigateDocuments = { }
                            )
                            Screen.SCAN_SEAL -> ScanSealScreen(
                                state = state,
                                viewModel = viewModel,
                                onBack = { screen = Screen.SCAN_HOME },
                                onResult = { screen = Screen.SCAN_SEAL_RESULT }
                            )
                            Screen.SCAN_SEAL_RESULT -> ScanSealResultScreen(
                                state = state,
                                onBack = { screen = Screen.SCAN_HOME },
                                onScanAgain = {
                                    viewModel.clearScanSeal()
                                    screen = Screen.SCAN_SEAL
                                }
                            )
                            Screen.CONSTITUTION -> ConstitutionScreen(
                                onBack = { screen = Screen.SCAN_HOME }
                            )
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
                        onSealDocumentScreen = { showMenu = false; sealDocumentPdfPicker.launch(arrayOf("application/pdf")) },
                        onShareWebsiteSeal = { showMenu = false; viewModel.shareWebsiteSealedFile(context) },
                        shareWebsiteSealEnabled = state.websiteSealedFile != null,
                        onAddMedia = { showMenu = false; sealPicker.launch(arrayOf("image/*", "video/*")) },
                        onVerify = { showMenu = false; verifyPicker.launch(arrayOf("application/pdf", "*/*")) },
                        onVerifyScreen = { showMenu = false; screen = Screen.VERIFY_DOCUMENT },
                        onScanSeal = { showMenu = false; screen = Screen.SCAN_SEAL },
                        onDeepResearch = { showMenu = false; viewModel.deepResearch() },
                        onDraftEmail = { showMenu = false; screen = Screen.EMAIL },
                        onTax = { showMenu = false; screen = Screen.TAX },
                        onReport = {
                            showMenu = false
                            if (state.report != null) screen = Screen.REPORT
                            else viewModel.postEngine("No sealed report yet. Start a forensic scan first.")
                        },
                        onVault = { showMenu = false; screen = Screen.VAULT },
                        onReadConstitution = { showMenu = false; screen = Screen.CONSTITUTION }
                    )
                }
            }

            state.guardianBlock?.let { assessment ->
                GuardianBlockScreen(
                    assessment = assessment,
                    onClearCase = {
                        viewModel.clearCase()
                        viewModel.clearGuardianBlock()
                        screen = Screen.SCAN_HOME
                    },
                    onReadConstitution = { screen = Screen.CONSTITUTION }
                )
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
                IconButton(
                    onClick = onReport,
                    enabled = state.report != null
                ) { Icon(Icons.Filled.Description, contentDescription = "Report", tint = if (state.report != null) VoGold else VoTextMuted) }
                IconButton(onClick = onVault) { Icon(Icons.Filled.Lock, contentDescription = "Vault", tint = VoGold) }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onHome) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = VoGold) }
                Spacer(Modifier.width(4.dp))
                    Text(
                    when (screen) {
                        Screen.SCAN_HOME -> "New Forensic Scan"
                        Screen.REPORT -> "Forensic Report"
                        Screen.EMAIL -> "Sealed Email"
                        Screen.TAX -> "Tax Return"
                        Screen.VAULT -> "Evidence Vault"
                        Screen.SEAL_DOCUMENT -> "Seal Document"
                        Screen.VERIFY_DOCUMENT -> "Verify Document"
                        Screen.SCAN_SEAL -> "Scan Seal QR"
                        Screen.SCAN_SEAL_RESULT -> "Seal Verification"
                        Screen.CONSTITUTION -> "Constitution"
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
    onSealDocumentScreen: () -> Unit,
    onShareWebsiteSeal: () -> Unit,
    shareWebsiteSealEnabled: Boolean,
    onAddMedia: () -> Unit,
    onVerify: () -> Unit,
    onVerifyScreen: () -> Unit,
    onScanSeal: () -> Unit,
    onDeepResearch: () -> Unit,
    onDraftEmail: () -> Unit,
    onTax: () -> Unit,
    onReport: () -> Unit,
    onVault: () -> Unit,
    onReadConstitution: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("SEALED ACTIONS", color = VoGold, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text(
            "Everything you add is sealed by the forensic engine and stored in the vault before the AI can read it.",
            color = VoTextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        ActionRow(Icons.Filled.UploadFile, "Seal a document", "PDF / text → engine → vault", onSealDocument)
        ActionRow(Icons.Filled.Verified, "Seal Document (website format)", "VO-DSS-1.2 website-compatible seal", onSealDocumentScreen)
        if (shareWebsiteSealEnabled) {
            ActionRow(Icons.Filled.Share, "Share website-format seal", "Send the last website-sealed PDF", onShareWebsiteSeal)
        }
        ActionRow(Icons.Filled.PhotoCamera, "Add photo / video", "GPS + timestamp anchored, sealed", onAddMedia)
        ActionRow(Icons.Filled.VerifiedUser, "Verify a document", "Check a file against the sealed vault", onVerify)
        ActionRow(Icons.Filled.TaskAlt, "Verify Document (screen)", "Open the verify page", onVerifyScreen)
        ActionRow(Icons.Filled.QrCodeScanner, "Scan Seal QR", "Point camera at a sealed document QR", onScanSeal)
        ActionRow(Icons.Filled.TravelExplore, "Deep research", "AI reads the sealed case file", onDeepResearch)
        ActionRow(Icons.Filled.Email, "Draft sealed email", "AI-drafted, delivered as a sealed PDF", onDraftEmail)
        ActionRow(Icons.Filled.Calculate, "Tax return", "Company or individual · 50% of accountant fee", onTax)
        ActionRow(Icons.Filled.Description, "View sealed report", "Anchored contradictions, exhibits, seal", onReport)
        ActionRow(Icons.Filled.Lock, "Open vault", "Sealed evidence, findings & seals", onVault)
        ActionRow(Icons.Filled.AccountBalance, "Read Constitution", "Verum Omnis governing principles", onReadConstitution)
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
