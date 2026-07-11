package com.verumomnis.forensic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.core.Constitution
import com.verumomnis.forensic.ui.theme.VoTextMuted

@Composable
fun VaultScreen(state: UiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        VoCard(title = "EVIDENCE VAULT", icon = Icons.Filled.Lock) {
            InfoRow("Evidence files", state.files.size.toString())
            InfoRow("Sealed reports", if (state.report != null) "1" else "0")
            InfoRow("Sealed emails", state.emails.size.toString())
            InfoRow("Findings sealed", if (state.scanResult != null) "yes" else "no")
            Spacer(Modifier.height(6.dp))
            Text("AES-256-GCM at rest · SHA-512 integrity · Android Keystore · biometric lock", color = VoTextMuted, fontSize = 11.sp)
        }

        VoCard(title = "SEAL LEDGER", icon = Icons.Filled.Verified) {
            state.scanResult?.seal?.let { Text("Evidence: ${it.sealFooter()}", color = VoTextMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
            state.report?.seal?.let { Text("Report: ${it.sealFooter()}", color = VoTextMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
            state.emails.forEach { Text("Email: ${it.seal.sealFooter()}", color = VoTextMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
            if (state.scanResult == null && state.report == null && state.emails.isEmpty()) {
                Text("No seals yet.", color = VoTextMuted, fontSize = 13.sp)
            }
        }

        VoCard(title = "CONSTITUTIONAL SAFEGUARDS", icon = Icons.Filled.Shield) {
            InfoRow("Dead-Man Switch", "${Constitution.DEAD_MAN_SWITCH_HOURS}h → INTERPOL")
            InfoRow("Ethics kill switch", "bias > ${Constitution.ETHICS_HALT_THRESHOLD * 100}% = halt")
            InfoRow("Profit firewall", "${Constitution.PROFIT_TO_FOUNDATION}% to Foundation")
            InfoRow("Recovered-fraud commission", "${Constitution.COMMISSION_PERCENT}%")
            InfoRow("Guardian Council", "${Constitution.GUARDIAN_COUNCIL_SIZE} members")
            InfoRow("Anti-War Doctrine", "Article X · supreme")
            Spacer(Modifier.height(4.dp))
            Text("Constitution v${Constitution.VERSION} · FINAL — immutable; any change invalidates all seals.", color = VoTextMuted, fontSize = 11.sp)
        }

        VoCard(title = "VAULT STRUCTURE", icon = Icons.Filled.Lock) {
            Text(
                """
                vault/
                ├── evidence/raw · processed
                ├── findings/ (contradictions, timeline, gps_records…)
                ├── reports/sealed
                ├── seals/
                ├── chat_sessions/
                ├── research/
                └── config/ (constitution.json)
                """.trimIndent(),
                color = VoTextMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp
            )
        }
    }
}
