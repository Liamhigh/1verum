package com.verumomnis.forensic.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
fun VerumApp(
    viewModel: VerumViewModel,
    onCaptureLocation: () -> Unit = {},
    onExportReport: (com.verumomnis.forensic.model.ForensicReport) -> Unit = {},
    onExportEmail: (com.verumomnis.forensic.model.SealedEmail) -> Unit = {},
    initialTab: Int = 0
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val tabs: List<Pair<String, ImageVector>> = listOf(
        "Dashboard" to Icons.Filled.Gavel,
        "Report" to Icons.Filled.Description,
        "Chat" to Icons.AutoMirrored.Filled.Chat,
        "Email" to Icons.Filled.Email,
        "Vault" to Icons.Filled.Lock
    )

    val context = LocalContext.current
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) onCaptureLocation() }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            runCatching { MediaIngestor(context).ingest(uri, state.gps, viewModel.mediaCount() + 1) }
                .onSuccess { viewModel.addMedia(it) }
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) onCaptureLocation() else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = VoBackground) {
        Box(modifier = Modifier.fillMaxSize()) {
            ConstellationBackground(modifier = Modifier.fillMaxSize())
            Scaffold(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = { VerumHeader(state) }
            ) { padding ->
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = VoGold,
                        edgePadding = 16.dp,
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, (title, icon) ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                selectedContentColor = VoGold,
                                unselectedContentColor = VoTextMuted,
                                text = { Text(title, letterSpacing = 0.5.sp) },
                                icon = { Icon(icon, contentDescription = title, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                    when (selectedTab) {
                        0 -> DashboardScreen(state, viewModel, onAddMedia = { mediaPicker.launch(arrayOf("image/*", "video/*")) })
                        1 -> ReportScreen(state, viewModel, onExportReport)
                        2 -> ChatScreen(state, viewModel)
                        3 -> EmailScreen(state, viewModel, onExportEmail)
                        else -> VaultScreen(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun VerumHeader(state: UiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Image(
            painter = painterResource(R.drawable.vo_banner),
            contentDescription = "Verum Omnis — AI Forensics for Truth",
            modifier = Modifier.height(40.dp).clip(RoundedCornerShape(8.dp))
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${state.deviceTier.label} · ${state.deviceRamGb}GB",
                color = VoGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = JetBrainsMono
            )
            Text("Comm · ${state.communicator}", color = VoTextMuted, fontSize = 9.sp, fontFamily = JetBrainsMono)
        }
    }
}
