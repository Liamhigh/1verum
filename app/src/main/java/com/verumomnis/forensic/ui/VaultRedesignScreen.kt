package com.verumomnis.forensic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.R
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoBlueBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoGoldDark
import com.verumomnis.forensic.ui.theme.VoGreen
import com.verumomnis.forensic.ui.theme.VoHeading
import com.verumomnis.forensic.ui.theme.VoPanel
import com.verumomnis.forensic.ui.theme.VoPanelBorder
import com.verumomnis.forensic.ui.theme.VoPanelRaised
import com.verumomnis.forensic.ui.theme.VoRed
import com.verumomnis.forensic.ui.theme.VoRedText
import com.verumomnis.forensic.ui.theme.VoTextBody
import com.verumomnis.forensic.ui.theme.VoTextFaint
import com.verumomnis.forensic.ui.theme.VoTextMuted

/**
 * Evidence Vault, matching the Verum Vault mock-up (`isHome`) — spec §3.2/§4.1.
 *
 * Rows are scan sets, not loose files: a scan writes a sealed original, a report
 * and a findings JSON, and showing those as three entries makes ten scans look
 * like thirty unrelated documents. Expanding a row reveals the artifacts.
 */
@Composable
fun VaultRedesignScreen(
    sets: List<ScanSet>,
    onOpenArtifact: (VaultArtifact) -> Unit,
    onDeleteSet: (ScanSet) -> Unit,
    onEmptyVault: () -> Unit,
    onNewDocument: () -> Unit
) {
    var folder by remember { mutableStateOf(VaultFolder.ALL) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }

    val counts = VaultFolder.entries.associateWith { f ->
        if (f == VaultFolder.ALL) sets.size else sets.count { it.folder == f }
    }
    val filtered = if (folder == VaultFolder.ALL) sets else sets.filter { it.folder == folder }

    Box(modifier = Modifier.fillMaxSize().background(VoBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            VaultHeader()
            FolderChipRow(
                selected = folder,
                counts = counts,
                showEmptyVault = sets.isNotEmpty(),
                onSelect = { folder = it },
                onEmptyVault = { confirmEmpty = true }
            )
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(VoPanel)
                            .border(1.dp, VoPanelBorder, RoundedCornerShape(18.dp))
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            tint = VoAccentBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (sets.isEmpty()) "Your vault is ready"
                            else "Nothing in ${folder.label.lowercase()} yet",
                            color = VoHeading,
                            fontFamily = Cormorant,
                            fontSize = 23.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (sets.isEmpty()) {
                                "Seal a document or add evidence to start building your protected case file."
                            } else {
                                "Try another folder, or return to the full vault."
                            },
                            color = VoTextBody,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(18.dp))
                        if (sets.isEmpty()) {
                            VerumPrimaryButton(
                                label = "Seal your first document",
                                onClick = onNewDocument,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            TextButton(
                                onClick = { folder = VaultFolder.ALL },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("View all documents", color = VoAccentBlue, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Bottom padding clears the FAB so the last row is never
                    // trapped underneath it.
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp)
                ) {
                    items(filtered, key = { it.id }) { set ->
                        ScanSetRow(
                            set = set,
                            expanded = expanded == set.id,
                            onToggle = { expanded = if (expanded == set.id) null else set.id },
                            onOpenArtifact = onOpenArtifact,
                            onDelete = { onDeleteSet(set) }
                        )
                    }
                }
            }
        }

        NewDocumentFab(
            onClick = onNewDocument,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 24.dp)
        )

        if (confirmEmpty) {
            EmptyVaultDialog(
                onCancel = { confirmEmpty = false },
                onConfirm = { confirmEmpty = false; onEmptyVault() }
            )
        }
    }
}

@Composable
private fun fixedSp(dp: Float) = with(LocalDensity.current) { dp.dp.toSp() }

@Composable
private fun VaultHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.vo_banner),
                contentDescription = "Verum Omnis",
                modifier = Modifier.height(26.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                "VAULT",
                fontFamily = JetBrainsMono,
                fontSize = fixedSp(10f),
                letterSpacing = 1.5.sp,
                color = VoAccentBlue
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Evidence Vault",
            fontFamily = Cormorant,
            fontSize = 26.sp,
            fontWeight = FontWeight.Light,
            color = VoHeading
        )
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(VoPanelBorder))
}

@Composable
private fun FolderChipRow(
    selected: VaultFolder,
    counts: Map<VaultFolder, Int>,
    showEmptyVault: Boolean,
    onSelect: (VaultFolder) -> Unit,
    onEmptyVault: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VaultFolder.entries.forEach { f ->
                FolderChip(
                    label = f.label,
                    count = counts[f] ?: 0,
                    selected = f == selected,
                    onClick = { onSelect(f) }
                )
            }
        }
        if (showEmptyVault) {
            Spacer(Modifier.width(8.dp))
            Text(
                "Empty Vault",
                fontFamily = JetBrainsMono,
                fontSize = fixedSp(10f),
                color = VoRedText,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clickable { onEmptyVault() }.padding(vertical = 6.dp, horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun FolderChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) VoGold.copy(alpha = 0.15f) else VoPanel)
            .border(
                1.dp,
                if (selected) VoGold else VoPanelBorder,
                RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) VoGold else VoTextBody,
            fontSize = fixedSp(11.5f),
            maxLines = 1,
            softWrap = false
        )
        Spacer(Modifier.width(5.dp))
        Text(
            count.toString(),
            fontFamily = JetBrainsMono,
            color = if (selected) VoGold.copy(alpha = 0.7f) else VoTextMuted,
            fontSize = fixedSp(11f)
        )
    }
}

@Composable
private fun ScanSetRow(
    set: ScanSet,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenArtifact: (VaultArtifact) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (expanded) VoPanelRaised else Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(vertical = 14.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VoPanelRaised)
                    .border(1.dp, VoPanelBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    set.initial,
                    fontFamily = Cormorant,
                    color = VoAccentBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                // Status dot: red when the engine flagged contradictions, green
                // when the set is sealed and clean.
                if (set.flagged || set.folder == VaultFolder.SEALED) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (set.flagged) VoRed else if (set.analysisKnown) VoGreen else VoAccentBlue)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    set.name,
                    color = VoHeading,
                    fontSize = 14.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                set.sourceSha512?.let {
                    Text(
                        "Source ${it.take(12)}… · ${set.id.removePrefix("linked:").take(8)}",
                        color = VoTextMuted, fontFamily = JetBrainsMono, fontSize = 10.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    set.statusLabel,
                    color = if (set.flagged) VoRedText else VoAccentBlue,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${set.artifacts.size} file(s) · ${formatBytes(set.sizeBytes)} · ${set.folder.label.lowercase()}",
                    fontFamily = JetBrainsMono,
                    color = VoTextFaint,
                    fontSize = fixedSp(9.5f),
                    maxLines = 1
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "Delete scan set",
                    tint = VoTextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 64.dp, end = 14.dp, bottom = 12.dp)) {
                set.sourceSha512?.let {
                    Text("Linked outputs · ${set.timestamp}", color = VoTextMuted, fontSize = 11.sp)
                }
                set.linkNotes.forEach { note ->
                    Text(note, color = VoTextMuted, fontSize = 11.sp)
                }
                set.artifacts.forEach { artifact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = artifact.available) { onOpenArtifact(artifact) }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                artifact.kind.label,
                                color = VoAccentBlue,
                                fontFamily = JetBrainsMono,
                                fontSize = fixedSp(9.5f),
                                letterSpacing = 0.4.sp
                            )
                            Text(
                                artifact.fileName,
                                color = VoTextBody,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!artifact.available) {
                                Text("Missing or empty file", color = VoRedText, fontSize = 11.sp)
                            }
                        }
                        Text(
                            formatBytes(artifact.sizeBytes),
                            fontFamily = JetBrainsMono,
                            color = VoTextMuted,
                            fontSize = fixedSp(10f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewDocumentFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(VoGold, VoGoldDark)))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text("+", fontSize = 28.sp, color = VoBackground, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun EmptyVaultDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(30.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(VoPanelRaised)
                .border(1.dp, VoRed.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .background(VoBackground.copy(alpha = 0.9f))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Empty the vault?", fontFamily = Cormorant, fontSize = 19.sp, color = VoHeading)
            Spacer(Modifier.height(8.dp))
            Text(
                "This removes all documents from this device. Sealed proofs already " +
                    "anchored on-chain are unaffected, and the integrity manifest is kept.",
                color = VoTextBody,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, VoBlueBorder.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .clickable { onCancel() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Cancel", color = VoTextBody, fontSize = 12.5.sp) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VoRed)
                        .clickable { onConfirm() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Delete All", color = Color.White, fontSize = 12.5.sp) }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
