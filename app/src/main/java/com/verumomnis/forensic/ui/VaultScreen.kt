package com.verumomnis.forensic.ui

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.verumomnis.forensic.seal.SealChain
import com.verumomnis.forensic.seal.SealVerifier
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoRed
import com.verumomnis.forensic.ui.theme.VoSurface
import com.verumomnis.forensic.ui.theme.VoSurfaceAlt
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary
import com.verumomnis.forensic.vault.EvidenceVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

/**
 * Evidence Vault — a novice-facing browser over the app's real on-device vault
 * (filesDir/vault, as laid out by EvidenceVault). Files are grouped by meaning
 * (never by raw directory path), support search + sort, and each row carries an
 * overflow menu: Open, Share, Verify, Delete. Sealed PDFs open an identity-card
 * style certificate view parsed from the embedded VO-DSS seal.
 */

/** User-facing groups — raw vault paths are never shown. */
private enum class VaultGroup(val title: String) {
    SEALED("Sealed documents"),
    REPORTS("Reports"),
    MEDIA("Photos & videos"),
    OTHER("Other")
}

private enum class VaultSort(val label: String) { DATE("Date"), NAME("Name") }

private class VaultFile(val file: File) {
    val name: String = file.name
    val sizeBytes: Long = file.length()
    val lastModified: Long = file.lastModified()
    val extension: String = file.extension.lowercase()
    val isPdf: Boolean = extension == "pdf"
    val isOts: Boolean = extension == "ots"
    val isEncrypted: Boolean = extension == "enc"
}

private val MEDIA_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "gif", "heic", "webp", "mp4", "webm", "mov", "mp3", "wav", "m4a", "aac"
)

/** Internal bookkeeping file — hidden from the novice view. */
private const val MANIFEST_FILE = "integrity_manifest.json"

private data class SealDetail(
    val fileName: String,
    val seal: SealChain.ParsedSealSubject?,
    val timestamp: Long
)

@Composable
fun VaultScreen(
    state: UiState,
    onVerify: () -> Unit = {},
    onSealDocument: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshTick by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(VaultSort.DATE) }
    var confirmDelete by remember { mutableStateOf<VaultFile?>(null) }
    var sealDetail by remember { mutableStateOf<SealDetail?>(null) }

    // Re-enumerate the on-disk vault whenever case state changes (or a delete
    // completes) so freshly sealed files show up.
    val groups = remember(state.files, state.report, state.emails, state.websiteSealedFile, refreshTick) {
        listVaultGroups(context)
    }

    val visibleGroups = groups.map { (group, files) ->
        val filtered = files.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        val sorted = when (sort) {
            VaultSort.DATE -> filtered.sortedByDescending { it.lastModified }
            VaultSort.NAME -> filtered.sortedBy { it.name.lowercase() }
        }
        group to sorted
    }.filter { it.second.isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        VoCard(title = "EVIDENCE VAULT", icon = Icons.Filled.Lock) {
            InfoRow("Evidence files", state.files.size.toString())
            InfoRow("Media files", state.files.count { it.type == "image" || it.type == "video" }.toString())
            InfoRow("Sealed reports", if (state.report != null) "1" else "0")
            InfoRow("Sealed emails", state.emails.size.toString())
            InfoRow("Findings sealed", if (state.scanResult != null) "yes" else "no")
        }

        VoCard(title = "VAULT FILES", icon = Icons.Filled.Folder) {
            Text(
                "Everything the engine seals is kept here. Tap a file to open it, or use the menu for more actions.",
                color = VoTextMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search vault", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = VoTextMuted, modifier = Modifier.size(18.dp))
                }
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SORT", color = VoTextMuted, fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 1.5.sp)
                Spacer(Modifier.width(10.dp))
                VaultSort.entries.forEach { option ->
                    val active = sort == option
                    Text(
                        option.label,
                        color = if (active) VoGold else VoTextMuted,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { sort = option }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            if (visibleGroups.isEmpty()) {
                VaultEmptyState(
                    searching = query.isNotBlank(),
                    onSealDocument = onSealDocument
                )
            } else {
                visibleGroups.forEach { (group, files) ->
                    VoSectionLabel(group.title)
                    Spacer(Modifier.height(6.dp))
                    files.forEach { vaultFile ->
                        VaultFileRow(
                            file = vaultFile,
                            onOpen = { openVaultFile(context, vaultFile.file) },
                            onShare = { shareVaultFile(context, vaultFile.file) },
                            onVerify = {
                                scope.launch(Dispatchers.IO) {
                                    val detail = runCatching {
                                        SealDetail(
                                            fileName = vaultFile.name,
                                            seal = if (vaultFile.isPdf) SealVerifier.parseSeal(vaultFile.file.readBytes()) else null,
                                            timestamp = vaultFile.lastModified
                                        )
                                    }.getOrElse { SealDetail(vaultFile.name, null, vaultFile.lastModified) }
                                    launch(Dispatchers.Main) { sealDetail = detail }
                                }
                            },
                            onDelete = { confirmDelete = vaultFile }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
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

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = VoSurface,
            title = { Text("Delete file?", color = VoTextPrimary, fontFamily = Cormorant, fontSize = 20.sp) },
            text = {
                Text(
                    "\"${target.name}\" will be permanently removed from the vault. This cannot be undone.",
                    color = VoTextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    EvidenceVault(context).delete(target.file)
                    confirmDelete = null
                    refreshTick++
                }) { Text("DELETE", color = VoRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("CANCEL", color = VoGold) }
            }
        )
    }

    sealDetail?.let { detail ->
        SealCertificateDialog(detail = detail, onDismiss = { sealDetail = null }, onVerifyFlow = {
            sealDetail = null
            onVerify()
        })
    }
}

@Composable
private fun VaultEmptyState(searching: Boolean, onSealDocument: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dashedZone(VoGold.copy(alpha = 0.45f))
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Folder,
            contentDescription = null,
            tint = VoGold.copy(alpha = 0.7f),
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (searching) "No files match your search" else "The vault is empty",
            color = VoTextPrimary,
            fontFamily = Cormorant,
            fontSize = 18.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (searching) {
                "Try a different file name."
            } else {
                "Sealed documents, reports and evidence you add will appear here."
            },
            color = VoTextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        if (!searching) {
            Spacer(Modifier.height(14.dp))
            VerumPrimaryButton(label = "Seal your first document", onClick = onSealDocument)
        }
    }
}

@Composable
private fun VaultFileRow(
    file: VaultFile,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(VoSurfaceAlt.copy(alpha = 0.12f))
            .clickable(enabled = !file.isEncrypted, onClick = onOpen)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    file.name,
                    color = VoTextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                badgeFor(file)?.let { (label, color) ->
                    Spacer(Modifier.width(8.dp))
                    VaultBadge(label, color)
                }
            }
            Text(
                buildString {
                    append(formatSize(file.sizeBytes))
                    append(" · ")
                    append(DateFormat.format("yyyy-MM-dd HH:mm", Date(file.lastModified)))
                    if (file.isEncrypted) append(" · encrypted — open from Chat")
                },
                color = VoTextMuted,
                fontSize = 10.sp,
                fontFamily = JetBrainsMono
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "File actions", tint = VoTextMuted)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.background(VoSurface)
            ) {
                DropdownMenuItem(
                    text = { Text("Open", color = if (file.isEncrypted) VoTextMuted else VoTextPrimary) },
                    enabled = !file.isEncrypted,
                    onClick = { menuOpen = false; onOpen() }
                )
                DropdownMenuItem(
                    text = { Text("Share", color = if (file.isEncrypted) VoTextMuted else VoTextPrimary) },
                    enabled = !file.isEncrypted,
                    onClick = { menuOpen = false; onShare() }
                )
                DropdownMenuItem(
                    text = { Text("Verify", color = VoTextPrimary) },
                    onClick = { menuOpen = false; onVerify() }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = VoRed) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

/** Small flat badge distinguishing sealed PDFs, OTS sidecars and encrypted files. */
@Composable
private fun VaultBadge(label: String, color: Color) {
    Text(
        label,
        color = color,
        fontFamily = JetBrainsMono,
        fontSize = 9.sp,
        letterSpacing = 1.sp,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

private fun badgeFor(file: VaultFile): Pair<String, Color>? = when {
    file.isPdf -> "PDF" to VoGold
    file.isOts -> "OTS" to VoAccentBlue
    file.isEncrypted -> "ENC" to VoTextMuted
    file.name.startsWith("seal_") && file.extension == "json" -> "SEAL" to VoAccentBlue
    else -> null
}

/** Identity-card style seal certificate (website look): mono fields, gold border. */
@Composable
private fun SealCertificateDialog(detail: SealDetail, onDismiss: () -> Unit, onVerifyFlow: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VoSurface,
        title = {
            Column {
                Text(
                    "SEAL CERTIFICATE",
                    color = VoGold,
                    fontFamily = Cormorant,
                    fontSize = 20.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    detail.fileName,
                    color = VoTextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, VoBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                val seal = detail.seal
                if (seal == null) {
                    Text(
                        "No Verum Omnis seal was detected in this file.",
                        color = VoTextMuted,
                        fontSize = 12.sp
                    )
                } else {
                    MonoField("SEAL ID", seal.sealId)
                    MonoField("FORMAT", if (seal.scheme == "v2") "VO-SEAL2 (self-integrity)" else "VO-SEAL (legacy)")
                    MonoField("SHA-512", seal.sha512)
                    seal.origHash?.let { MonoField("ORIGINAL HASH", it) }
                    if (seal.chain.isNotEmpty()) {
                        MonoField("CHAIN OF CUSTODY", seal.chain.joinToString("\n"))
                    }
                    MonoField(
                        "FILE TIMESTAMP",
                        DateFormat.format("yyyy-MM-dd HH:mm", Date(detail.timestamp)).toString()
                    )
                }
            }
        },
        confirmButton = {
            if (detail.seal != null) {
                TextButton(onClick = onVerifyFlow) { Text("FULL VERIFICATION", color = VoGold) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", color = VoTextMuted) }
        }
    )
}

@Composable
private fun MonoField(label: String, value: String) {
    Text(label, color = VoGold.copy(alpha = 0.75f), fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 1.5.sp)
    SelectionContainer {
        Text(value, color = VoTextPrimary, fontFamily = JetBrainsMono, fontSize = 11.sp)
    }
    Spacer(Modifier.height(10.dp))
}

/** Dashed-border container style (matches the website's dashed drop zones). */
private fun Modifier.dashedZone(color: Color): Modifier = this.drawBehind {
    val strokeWidth = 1.dp.toPx()
    drawRoundRect(
        color = color,
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
        ),
        cornerRadius = CornerRadius(12.dp.toPx())
    )
}

/** Group the on-disk vault by meaning; config and chat_sessions are never listed. */
private fun listVaultGroups(context: Context): List<Pair<VaultGroup, List<VaultFile>>> {
    val root = File(context.filesDir, "vault")
    fun files(rel: String): List<File> =
        File(root, rel).listFiles()?.filter { it.isFile }.orEmpty()

    val sealed = files("reports/sealed") + files("seals")
    val reports = (files("reports/draft") + files("findings")).filter { it.name != MANIFEST_FILE }
    val raw = files("evidence/raw")
    val media = raw.filter { it.extension.lowercase() in MEDIA_EXTENSIONS }
    val other = raw.filterNot { it.extension.lowercase() in MEDIA_EXTENSIONS } +
        files("evidence/processed") + files("research")

    return listOf(
        VaultGroup.SEALED to sealed,
        VaultGroup.REPORTS to reports,
        VaultGroup.MEDIA to media,
        VaultGroup.OTHER to other
    ).map { (group, groupFiles) -> group to groupFiles.map { VaultFile(it) } }
}

private fun vaultUri(context: Context, file: File) =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

/** Opens a vault file with an external app via FileProvider; falls back to share. */
private fun openVaultFile(context: Context, file: File) {
    runCatching {
        val uri = vaultUri(context, file)
        val mime = mimeFor(file.name)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (view.resolveActivity(context.packageManager) != null) {
            context.startActivity(Intent.createChooser(view, "Open sealed file"))
        } else {
            shareVaultFile(context, file)
        }
    }
}

/** Shares a vault file to any app that accepts it (mail, drive, messaging). */
private fun shareVaultFile(context: Context, file: File) {
    runCatching {
        val uri = vaultUri(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeFor(file.name)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share sealed file"))
    }
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
