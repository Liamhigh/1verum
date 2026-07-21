package com.verumomnis.forensic.ui

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoSurfaceAlt
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary
import java.io.File
import java.util.Date

/**
 * Evidence Vault — a functional file browser over the app's real on-device
 * vault (filesDir/vault, as laid out by EvidenceVault). Every file row opens
 * (or shares) the sealed artefact via the app FileProvider; the seal ledger
 * entries open their sealed files or jump to verification.
 */

/** Vault directory layout (mirrors EvidenceVault), shown in this order. */
private val VAULT_SECTIONS = listOf(
    "evidence/raw",
    "evidence/processed",
    "findings",
    "reports/sealed",
    "reports/draft",
    "seals",
    "chat_sessions",
    "research",
    "config"
)

private class VaultFile(val file: File) {
    val name: String = file.name
    val sizeBytes: Long = file.length()
    val lastModified: Long = file.lastModified()
}

@Composable
fun VaultScreen(state: UiState, onVerify: () -> Unit = {}) {
    val context = LocalContext.current
    // Re-enumerate the on-disk vault whenever case state changes so freshly
    // sealed files show up.
    val sections = remember(state.files, state.report, state.emails, state.websiteSealedFile) {
        listVaultSections(context)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        VoCard(title = "EVIDENCE VAULT", icon = Icons.Filled.Lock) {
            InfoRow("Evidence files", state.files.size.toString())
            InfoRow("Media files", state.files.count { it.isMedia }.toString())
            InfoRow("Sealed reports", if (state.report != null) "1" else "0")
            InfoRow("Sealed emails", state.emails.size.toString())
            InfoRow("Findings sealed", if (state.scanResult != null) "yes" else "no")
        }

        VoCard(title = "VAULT FILES", icon = Icons.Filled.Folder) {
            Text(
                "Everything the engine seals is written here. Tap a file to open or share it.",
                color = VoTextMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            val nonEmpty = sections.filter { it.second.isNotEmpty() }
            if (nonEmpty.isEmpty()) {
                Text(
                    "Vault is empty — seal a document or run a forensic scan first.",
                    color = VoTextMuted,
                    fontSize = 13.sp
                )
            }
            nonEmpty.forEach { (path, files) ->
                VoSectionLabel("vault/$path")
                Spacer(Modifier.height(6.dp))
                files.forEach { vaultFile ->
                    VaultFileRow(vaultFile, onClick = { openVaultFile(context, vaultFile.file) })
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        if (state.report != null || state.scanResult != null || state.emails.isNotEmpty()) {
            VoCard(title = "SEAL LEDGER", icon = Icons.Filled.Verified) {
                state.report?.seal?.let { seal ->
                    LedgerEntry("Report", seal.sealFooter()) { openNewestSealedReport(context) }
                }
                state.scanResult?.seal?.let { seal ->
                    LedgerEntry("Evidence", seal.sealFooter(), onVerify)
                }
                state.emails.forEach { email ->
                    LedgerEntry("Email → ${email.draft.recipient}", email.seal.sealFooter()) {
                        openSealedEmail(context, email.sealedPdfFile, onVerify)
                    }
                }
            }
        }

        Text(
            "Seals prove integrity and time. Findings are indicators, not determinations.",
            color = VoTextMuted,
            fontSize = 11.sp,
            fontFamily = JetBrainsMono,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun VaultFileRow(file: VaultFile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(VoSurfaceAlt.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Description,
            contentDescription = null,
            tint = VoGold,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.name,
                color = VoTextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${formatSize(file.sizeBytes)} · ${DateFormat.format("yyyy-MM-dd HH:mm", Date(file.lastModified))}",
                color = VoTextMuted,
                fontSize = 10.sp,
                fontFamily = JetBrainsMono
            )
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun LedgerEntry(label: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(VoSurfaceAlt)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(label, color = VoGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = VoTextPrimary, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Text("Tap to open / verify", color = VoTextMuted, fontSize = 9.sp)
    }
    Spacer(Modifier.height(6.dp))
}

private fun listVaultSections(context: Context): List<Pair<String, List<VaultFile>>> {
    val root = File(context.filesDir, "vault")
    return VAULT_SECTIONS.map { path ->
        val files = File(root, path).listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.map { VaultFile(it) }
            .orEmpty()
        path to files
    }
}

/** Opens a vault file with an external app via FileProvider; falls back to share. */
private fun openVaultFile(context: Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = mimeFor(file.name)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (view.resolveActivity(context.packageManager) != null) {
            context.startActivity(Intent.createChooser(view, "Open sealed file"))
        } else {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share sealed file"))
        }
    }
}

private fun openNewestSealedReport(context: Context) {
    val newest = File(context.filesDir, "vault/reports/sealed").listFiles()
        ?.filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
        ?.maxByOrNull { it.lastModified() }
    if (newest != null) openVaultFile(context, newest)
}

private fun openSealedEmail(context: Context, sealedPdfFile: String, onMissing: () -> Unit) {
    val file = File(File(context.filesDir, "vault/reports/sealed"), sealedPdfFile)
    if (file.exists()) openVaultFile(context, file) else onMissing()
}

private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "pdf" -> "application/pdf"
    "json" -> "application/json"
    "txt", "log", "md" -> "text/plain"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "mp4" -> "video/mp4"
    "mp3" -> "audio/mpeg"
    "wav" -> "audio/wav"
    else -> "*/*"
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
