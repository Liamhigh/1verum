package com.verumomnis.forensic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.BuildConfig
import com.verumomnis.forensic.core.Constitution
import com.verumomnis.forensic.core.SettingsRepository
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoSurface
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary

@Composable
fun SettingsScreen(
    state: UiState,
    viewModel: VerumViewModel,
    onNavigateConstitution: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var ojrsEnabled by remember { mutableStateOf(settings.ojrsEnabled) }
    var autoDeleteDays by remember { mutableStateOf(settings.autoDeleteDays.toFloat()) }
    var biometricEnabled by remember { mutableStateOf(settings.biometricEnabled) }
    var cameraEnabled by remember { mutableStateOf(settings.cameraEnabled) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        VoSectionLabel("APPLICATION SETTINGS")
        Spacer(Modifier.height(8.dp))

        // OJRS Settings
        VoCard(title = "ONLINE JUDICIAL RETRIEVAL (OJRS)", icon = Icons.Filled.Settings) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Enable legal research across SAFLII, CourtListener, BAILII, CanLII, AustLII, EUR-Lex.",
                    color = VoTextMuted, fontSize = 11.sp, lineHeight = 14.sp
                )
                ToggleSetting("Enable OJRS", ojrsEnabled) {
                    ojrsEnabled = it
                    settings.ojrsEnabled = it
                }
                if (ojrsEnabled) {
                    Text(
                        "✓ OJRS active — deep research enabled",
                        color = VoGold, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, fontFamily = JetBrainsMono
                    )
                }
            }
        }

        // Security Settings
        VoCard(title = "SECURITY & AUTHENTICATION", icon = Icons.Filled.Settings) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToggleSetting("Biometric Authentication", biometricEnabled) {
                    biometricEnabled = it
                    settings.biometricEnabled = it
                }
                Text(
                    if (biometricEnabled) "Fingerprint/Face unlock enabled" else "Biometric disabled",
                    color = VoTextMuted, fontSize = 10.sp
                )
                Spacer(Modifier.height(4.dp))
                ToggleSetting("Camera Integration", cameraEnabled) {
                    cameraEnabled = it
                    settings.cameraEnabled = it
                }
                Text(
                    if (cameraEnabled) "In-app evidence capture enabled" else "Camera disabled",
                    color = VoTextMuted, fontSize = 10.sp
                )
            }
        }

        // Privacy Settings
        VoCard(title = "PRIVACY & RETENTION", icon = Icons.Filled.Settings) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Evidence Retention",
                        color = VoTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 11.sp
                    )
                    Text(
                        "${autoDeleteDays.toInt()} days",
                        color = VoGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMono
                    )
                }
                Slider(
                    value = autoDeleteDays,
                    onValueChange = {
                        autoDeleteDays = it
                        settings.autoDeleteDays = it.toInt()
                    },
                    valueRange = 7f..90f,
                    steps = 10,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Sealed evidence auto-deletes after ${ autoDeleteDays.toInt()} days.",
                    color = VoTextMuted, fontSize = 10.sp, lineHeight = 13.sp
                )
            }
        }

        // Model Management
        if (state.models.isNotEmpty()) {
            VoCard(title = "MODEL MANAGEMENT", icon = Icons.Filled.Settings) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Heading states the list, not its state: every catalogue entry
                    // is shown here whether or not it exists on the device, so
                    // "models loaded" asserted of the whole list was simply untrue —
                    // each row carries its own real status below.
                    Text(
                        "On-device models for this device:",
                        color = VoTextMuted, fontSize = 11.sp
                    )
                    state.models.forEach { model ->
                        val loaded = model.name in state.modelsLoaded
                        val progress = state.modelDownloadProgress[model.name]
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(1.dp, RoundedCornerShape(8.dp))
                                .background(VoSurface, RoundedCornerShape(8.dp))
                                .border(1.dp, VoBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(model.name, color = VoTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            Text(
                                "${model.role} · " + when {
                                    loaded -> "Loaded, running on-device"
                                    progress != null && progress < 1f -> "Downloading ${(progress * 100).toInt()}%"
                                    else -> "Not downloaded"
                                },
                                color = if (loaded) VoGold else VoTextMuted, fontSize = 8.sp, fontFamily = JetBrainsMono
                            )
                            if (progress != null && progress < 1f) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = VoGold,
                                    trackColor = VoBorder
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Downloads run over your normal connection and can be several gigabytes per model.",
                        color = VoTextMuted, fontSize = 9.sp, lineHeight = 12.sp
                    )
                    Text(
                        "▶ Download & load models",
                        color = VoGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { viewModel.downloadAndLoadModels() }
                    )
                }
            }
        }

        // Constitution Viewer
        VoCard(title = "GOVERNANCE", icon = Icons.Filled.Settings) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Verum Omnis Constitution v${Constitution.VERSION}",
                    color = VoTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, fontFamily = JetBrainsMono
                )
                Text(
                    "Read the binding principles and rules governing this forensic engine.",
                    color = VoTextMuted, fontSize = 11.sp, lineHeight = 14.sp
                )
                Text(
                    "▶ View Constitution",
                    color = VoGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onNavigateConstitution() }
                )
            }
        }

        // About
        VoCard(title = "ABOUT", icon = Icons.Filled.Settings) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsInfoRow("Version", BuildConfig.VERSION_NAME)
                SettingsInfoRow("Build", BuildConfig.BUILD_TIME.take(10))
                SettingsInfoRow("Constitution", "v${Constitution.VERSION}")
                SettingsInfoRow("Device", state.deviceTier.name)
                SettingsInfoRow("RAM", "${state.deviceRamGb} GB")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Truth for All.",
                    color = VoGold, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, fontFamily = JetBrainsMono
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ToggleSetting(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = VoTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = VoGold,
                uncheckedColor = VoBorder
            )
        )
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label + ":", color = VoTextMuted, fontSize = 11.sp, modifier = Modifier.weight(0.4f))
        Text(value, color = VoTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = JetBrainsMono, modifier = Modifier.weight(0.6f))
    }
}
