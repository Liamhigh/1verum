package com.verumomnis.forensic.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.verumomnis.forensic.seal.QrScanPayload
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoGreen
import com.verumomnis.forensic.ui.theme.VoSurfaceAlt
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * QR seal scanner — website-first verification flow.
 *
 * 1. Requests camera permission and shows a live CameraX + ML Kit scanner.
 * 2. A VO-DSS-1.2 verify-URL QR (verumglobal.foundation/verify.html?h=…&m=…)
 *    decodes inline; the canonical check is the website verifier, opened via
 *    ACTION_VIEW. An honestly-labelled on-device format check (select the
 *    sealed PDF) remains available.
 * 3. A legacy raw-JSON seal QR ({scheme:"verum-omnis-seal", …}) is copied to
 *    the clipboard and the site's verify page opened for pasting.
 * 4. Offline: the decoded payload is shown with an honest offline note.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanSealScreen(
    state: UiState,
    viewModel: VerumViewModel,
    onBack: () -> Unit,
    onResult: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    var rawQr by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    val online = remember { isOnline(context) }

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val name = viewModel.uriFileName(it, context)
                val bytes = context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() }
                bytes?.let { b ->
                    viewModel.selectScanSealPdf(b, name)
                    viewModel.verifyScanSeal()
                    scope.launch(Dispatchers.Main) { onResult() }
                }
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
        ScanSealHeader()
        Spacer(Modifier.height(20.dp))

        when {
            !cameraPermission.status.isGranted -> CameraPermissionCard(
                onRequest = { cameraPermission.launchPermissionRequest() }
            )
            rawQr == null -> CameraPreviewBox(
                onQr = { value ->
                    rawQr = value
                    QrScanPayload.parseOrNull(value)?.let { payload ->
                        viewModel.setScanSealQr(payload)
                    }
                }
            )
        }

        val payload = state.scanSealQrPayload
        val raw = rawQr
        when {
            // VO-DSS-1.2 verify URL — the website verifier is canonical.
            payload != null -> {
                Spacer(Modifier.height(20.dp))
                QrPayloadCard(payload)
                Spacer(Modifier.height(16.dp))
                if (online) {
                    VerumPrimaryButton(
                        label = "Verify on verumglobal.foundation",
                        onClick = { openUrl(context, payload.rawUrl) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                } else {
                    OfflineNote()
                    Spacer(Modifier.height(10.dp))
                }
                VerumSecondaryButton(
                    label = "On-device format check (select sealed PDF)",
                    onClick = { pdfPicker.launch(arrayOf("application/pdf", "*/*")) },
                    enabled = !state.scanSealBusy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                ScanAgainButton {
                    rawQr = null
                    copied = false
                    viewModel.clearScanSeal()
                }
            }
            // Legacy raw-JSON seal QR — the site verify page has a paste panel.
            raw != null && raw.trimStart().startsWith("{") && raw.contains("verum-omnis-seal") -> {
                Spacer(Modifier.height(20.dp))
                LegacyPayloadCard(raw)
                Spacer(Modifier.height(16.dp))
                if (online) {
                    VerumPrimaryButton(
                        label = "Copy payload & open verify.html",
                        onClick = {
                            copyToClipboard(context, raw)
                            copied = true
                            openUrl(context, "https://verumglobal.foundation/verify.html")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (copied) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Payload copied — paste it into the site's verification panel.",
                            color = VoGreen,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    OfflineNote()
                    Spacer(Modifier.height(10.dp))
                    VerumSecondaryButton(
                        label = "Copy payload",
                        onClick = {
                            copyToClipboard(context, raw)
                            copied = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(6.dp))
                ScanAgainButton {
                    rawQr = null
                    copied = false
                }
            }
            // Anything else is not a Verum seal QR.
            raw != null -> {
                Spacer(Modifier.height(20.dp))
                UnknownQrCard(raw)
                Spacer(Modifier.height(6.dp))
                ScanAgainButton {
                    rawQr = null
                    copied = false
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "The website verifier is canonical. On-device checks are format-level indicators, " +
                "not determinations — nothing leaves your device unless you choose to verify online.",
            color = VoTextMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ScanSealHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.Filled.QrCodeScanner,
            contentDescription = "Scan seal",
            tint = VoAccentBlue,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Scan Seal QR",
            fontFamily = Cormorant,
            fontWeight = FontWeight.Light,
            fontSize = 30.sp,
            color = VoTextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan the QR code printed on a sealed document to verify it.",
            fontSize = 15.sp,
            color = VoTextMuted,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.95f)
        )
    }
}

@Composable
private fun OfflineNote() {
    Text(
        "Offline — showing the decoded seal payload. Verify online for the full check.",
        color = VoGold,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ScanAgainButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            "SCAN AGAIN",
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold,
            color = VoAccentBlue
        )
    }
}

@Composable
private fun QrInfoCard(label: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoSurfaceAlt.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .border(1.dp, VoBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Text(label, fontFamily = JetBrainsMono, fontSize = 11.sp, color = VoGold, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        Text(body, fontFamily = JetBrainsMono, fontSize = 10.sp, color = VoTextPrimary)
    }
}

@Composable
private fun LegacyPayloadCard(raw: String) {
    QrInfoCard(
        label = "LEGACY SEAL PAYLOAD",
        body = raw.take(220) + if (raw.length > 220) "…" else ""
    )
}

@Composable
private fun UnknownQrCard(raw: String) {
    Column {
        QrInfoCard(
            label = "QR CONTENT — NOT A VERUM SEAL",
            body = raw.take(220) + if (raw.length > 220) "…" else ""
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "This QR code is not a Verum Omnis seal.",
            color = VoTextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CameraPermissionCard(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(VoSurfaceAlt.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .border(1.dp, VoBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Camera permission required", color = VoTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text("The scanner needs camera access to read seal QR codes.", color = VoTextMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        VerumPrimaryButton(
            label = "Grant permission",
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth(0.8f)
        )
    }
}

@Composable
private fun CameraPreviewBox(onQr: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var detected by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, VoGold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .background(VoBackground),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                if (detected) return@AndroidView
                val executor = ContextCompat.getMainExecutor(context)
                val providerFuture = ProcessCameraProvider.getInstance(context)
                providerFuture.addListener({
                    val cameraProvider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(executor, QrAnalyzer { url ->
                                if (!detected) {
                                    detected = true
                                    onQr(url)
                                }
                            })
                        }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    } catch (e: Exception) {
                        // Camera binding may fail on emulators or devices without a camera.
                    }
                }, executor)
            }
        )

        if (detected) {
            Box(
                modifier = Modifier.fillMaxSize().background(VoBackground.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = VoGold, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("QR code detected", color = VoGold, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun QrPayloadCard(payload: QrScanPayload) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoSurfaceAlt.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .border(1.dp, VoBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Text("QR PAYLOAD", fontFamily = JetBrainsMono, fontSize = 11.sp, color = VoGold, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        InfoRow("Hash prefix", payload.hashPrefix)
        payload.metadata?.let { meta ->
            InfoRow("Version", meta.v)
            InfoRow("Seal type", meta.type)
            meta.id?.n?.let { InfoRow("Sender", it) }
            meta.gps?.let { InfoRow("GPS", it) }
            meta.dev?.let { InfoRow("Device", it) }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private class QrAnalyzer(private val onQr: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient()

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue?.let(onQr)
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}

private fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("Verum seal payload", text))
}
