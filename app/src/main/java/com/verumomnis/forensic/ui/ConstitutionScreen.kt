package com.verumomnis.forensic.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.ImageView
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoSurfaceAlt
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Simple in-app viewer for the bundled constitution PDF.
 *
 * The PDF is copied from assets to a cache file once, then rendered page-by-page
 * using Android's [PdfRenderer]. This keeps memory usage bounded and works
 * offline without adding a third-party PDF viewer dependency.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConstitutionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var pageCount by remember { mutableStateOf(0) }
    var currentPage by remember { mutableStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    val cacheFile = remember { File(context.cacheDir, "constitution.pdf") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (!cacheFile.exists()) {
                context.assets.open("constitution.pdf").use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            val renderer = PdfRenderer(
                ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
            )
            pageCount = renderer.pageCount
            renderer.close()
            ready = true
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
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.AccountBalance,
                contentDescription = "Constitution",
                tint = VoGold,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Constitution of Verum Omnis",
                fontFamily = Cormorant,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                color = VoTextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Canonical governing principles, sealed and available offline.",
                fontSize = 14.sp,
                color = VoTextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            if (!ready || pageCount == 0) {
                CircularProgressIndicator(color = VoGold, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Loading constitution…", color = VoTextMuted, fontSize = 14.sp)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { if (currentPage > 0) currentPage-- },
                        enabled = currentPage > 0,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VoGold),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VoGold)
                    ) { Text("Previous") }
                    Text(
                        "Page ${currentPage + 1} / $pageCount",
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = VoTextPrimary
                    )
                    OutlinedButton(
                        onClick = { if (currentPage < pageCount - 1) currentPage++ },
                        enabled = currentPage < pageCount - 1,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VoGold),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VoGold)
                    ) { Text("Next") }
                }

                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(VoSurfaceAlt.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .border(1.dp, VoBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    PdfPageView(
                        file = cacheFile,
                        page = currentPage,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfPageView(file: File, page: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file, page) {
        bitmap = null
        withContext(Dispatchers.IO) {
            val renderer = PdfRenderer(
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            )
            try {
                renderer.openPage(page).use { pdfPage ->
                    val bmp = Bitmap.createBitmap(
                        pdfPage.width,
                        pdfPage.height,
                        Bitmap.Config.ARGB_8888
                    )
                    pdfPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap = bmp
                }
            } finally {
                renderer.close()
            }
        }
    }

    bitmap?.let { bmp ->
        AndroidView(
            factory = {
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            },
            update = { it.setImageBitmap(bmp) },
            modifier = modifier
        )
    } ?: run {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = VoGold)
        }
    }
}
