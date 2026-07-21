package com.verumomnis.forensic.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoGoldDark
import com.verumomnis.forensic.ui.theme.VoSurface
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary

/**
 * Standard Verum card (verumglobal.foundation): #0A1526 surface, 1dp
 * gold-alpha border, 12dp radius, 16dp padding. Flat — no shadow.
 */
@Composable
fun VoCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoSurface, RoundedCornerShape(12.dp))
            .border(1.dp, VoBorder, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = title, tint = VoGold, modifier = Modifier.width(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = VoGold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** Section label: monospace, uppercase, letter-spaced, muted gold. */
@Composable
fun VoSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        color = VoGold.copy(alpha = 0.75f),
        modifier = modifier
    )
}

/** Primary button: gold fill, navy text, uppercase, letter-spaced, 48dp. */
@Composable
fun VerumPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = VoGold,
            contentColor = VoBackground,
            disabledContainerColor = VoGoldDark.copy(alpha = 0.4f),
            disabledContentColor = VoBackground.copy(alpha = 0.7f)
        )
    ) {
        Text(label.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
    }
}

/** Secondary button: transparent, 1dp gold border, gold text, 48dp. */
@Composable
fun VerumSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, VoGold),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = VoGold)
    ) {
        Text(label.uppercase(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = VoTextMuted, fontSize = 13.sp)
        Text(value, color = VoTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Shared navy/gold confirmation dialog for destructive actions
 * (clear case, new scan, delete). Gold confirm, muted cancel.
 */
@Composable
fun VoConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VoSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(title, fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = VoGold)
        },
        text = {
            Text(message, color = VoTextMuted, fontSize = 14.sp, lineHeight = 21.sp)
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VoGold, contentColor = VoBackground)
            ) {
                Text(confirmLabel.uppercase(), fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = VoTextMuted, fontSize = 12.sp, letterSpacing = 1.sp)
            }
        }
    )
}

/**
 * "Details" expander that keeps raw hashes out of the novice view: the full
 * SHA-512 is only revealed on tap, and tapping the revealed hash copies it.
 */
@Composable
fun HashDetailsExpander(label: String, hash: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Hide details" else "Show details",
                tint = VoGold,
                modifier = Modifier.width(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "DETAILS · ${label.uppercase()}",
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                color = VoTextMuted
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VoBackground.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .border(1.dp, VoBorder, RoundedCornerShape(8.dp))
                    .clickable {
                        clipboard.setText(AnnotatedString(hash))
                        copied = true
                    }
                    .padding(10.dp)
            ) {
                Text(hash, fontFamily = JetBrainsMono, fontSize = 11.sp, color = VoTextPrimary, lineHeight = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (copied) "Copied" else "Tap to copy",
                    fontFamily = JetBrainsMono,
                    fontSize = 9.sp,
                    color = if (copied) VoGold else VoTextMuted
                )
            }
        }
    }
}
