package com.verumomnis.forensic.ui

import android.Manifest
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.verumomnis.forensic.ui.theme.VoSurfaceAlt
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalLifecycleOwner

/**
 * QR seal scanner screen.
 *
 * 1. Requests camera permission.
 * 2. Shows a live CameraX preview with an ML Kit QR analyser.
 * 3. When a Verum verify-URL QR is scanned, it is decoded and shown to the user.
 * 4. The user selects the sealed PDF; the payload + PDF are passed to the
 *    view model for QR-aware verification.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
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
            ScanSealHeader()
            Spacer(Modifier.height(20.dp))

            when {
                !cameraPermission.status.isGranted -> CameraPermissionCard(
                    onRequest = { cameraPermission.launchPermissionRequest() }
                )
                else -> CameraPreviewBox(
                    onQr = { url ->
                        QrScanPayload.parseOrNull(url)?.let { payload ->
                            viewModel.setScanSealQr(payload)
                        }
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            state.scanSealQrPayload?.let { payload ->
                QrPayloadCard(payload)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { pdfPicker.launch(arrayOf("application/pdf", "*/*")) },
                    enabled = !state.scanSealBusy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VoGold, contentColor = VoBackground)
                ) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Select sealed PDF to verify", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Point the camera at a Verum seal QR code. The QR payload and the sealed PDF never leave your device.",
                color = VoTextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
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
            "Scan the QR code printed on a sealed document to verify it against the live PDF.",
            fontSize = 15.sp,
            color = VoTextMuted,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.95f)
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
        Button(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth(0.8f).height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VoGold, contentColor = VoBackground)
        ) {
            Text("Grant permission", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
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
