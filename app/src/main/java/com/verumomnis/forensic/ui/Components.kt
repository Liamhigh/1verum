package com.verumomnis.forensic.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.core.Constitution
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.MonoKicker
import com.verumomnis.forensic.ui.theme.MonoLabel
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoBorderRow
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoGoldGradient
import com.verumomnis.forensic.ui.theme.VoGoldWash
import com.verumomnis.forensic.ui.theme.VoGreen
import com.verumomnis.forensic.ui.theme.VoHeading
import com.verumomnis.forensic.ui.theme.VoPanel
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary
import com.verumomnis.forensic.ui.theme.VoWell

/**
 * Standard Verum card (§3.2): translucent blue wash `rgba(15,52,96,0.12)`,
 * `1px solid rgba(26,46,82,0.5)` border, 16dp radius, gold serif h3 title.
 */
@Composable
fun VoCard(title: String, icon: ImageVector? = null, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoPanel, RoundedCornerShape(16.dp))
            .border(1.dp, VoBorder, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = VoGold, modifier = Modifier.width(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            // Card h3: serif 500, gold (§3.2)
            Text(
                title,
                fontFamily = Cormorant,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                color = VoGold
            )
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

/** Section label: the mono-label convention (§2.1) — uppercase, letter-spaced, blue. */
@Composable
fun VoSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MonoLabel, color = VoAccentBlue, modifier = modifier)
}

/** Eyebrow kicker above a serif heading: mono 12sp, ls 0.15em, gold (§3.6). */
@Composable
fun VoKicker(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(
        text.uppercase(),
        style = MonoKicker,
        color = VoGold,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = if (center) modifier.fillMaxWidth() else modifier
    )
}

/** Page/section serif heading: Cormorant light, off-white (§2.2). */
@Composable
fun VoSerifHeading(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 32,
    color: Color = VoHeading,
    center: Boolean = false
) {
    Text(
        text,
        fontFamily = Cormorant,
        fontWeight = FontWeight.Light,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.1f).sp,
        letterSpacing = (-0.03 * fontSize).sp,
        color = color,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = if (center) modifier.fillMaxWidth() else modifier
    )
}

/**
 * ID-field row (§3.3): mono blue label left, mono body value right,
 * hairline `rgba(26,46,82,0.3)` bottom divider.
 */
@Composable
fun VoIdField(label: String, value: String, valueColor: Color = VoTextPrimary, lastRow: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(label.uppercase(), style = MonoLabel, color = VoAccentBlue, maxLines = 1)
            Text(
                value,
                fontFamily = JetBrainsMono,
                fontSize = 13.sp,
                color = valueColor,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
        if (!lastRow) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(VoBorderRow))
        }
    }
}

/**
 * Honesty-note callout (§3.5): 3dp gold left border, gold 6% wash,
 * 0/12/12/0 radius, sans body text.
 */
@Composable
fun VoHonestyNote(text: String, modifier: Modifier = Modifier, title: String? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(VoGoldWash, RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp))
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(VoGold))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 16.dp)) {
            if (title != null) {
                Text(title, color = VoHeading, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
            }
            Text(text, color = VoTextPrimary, fontSize = 14.sp, lineHeight = 22.sp)
        }
    }
}

/** Gold hairline rule fading right (§3.6 .gold-rule). */
@Composable
fun VoGoldRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(VoGold, VoGold.copy(alpha = 0.1f))
                )
            )
    )
}

/**
 * Primary gold CTA (§3.4): 135° gold gradient fill, NAVY mono uppercase
 * text (never white), 10dp radius.
 */
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
        modifier = modifier.height(48.dp).alpha(if (enabled) 1f else 0.4f),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = VoBackground,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = VoBackground
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(VoGoldGradient),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label.uppercase(),
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                color = VoBackground
            )
        }
    }
}

/** Outline sibling (§3.4): transparent, 1dp blue border, blue mono text. */
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
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (enabled) VoAccentBlue else VoAccentBlue.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = VoAccentBlue,
            disabledContentColor = VoAccentBlue.copy(alpha = 0.4f)
        )
    ) {
        Text(
            label.uppercase(),
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            letterSpacing = 1.sp
        )
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

/** Hash display well (§3.9): sunken navy well, mono green value. */
@Composable
fun VoHashWell(value: String, valueColor: Color = VoGreen, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(VoWell, RoundedCornerShape(10.dp))
            .border(1.dp, VoBorder, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Text(value, fontFamily = JetBrainsMono, fontSize = 12.sp, lineHeight = 19.sp, color = valueColor)
    }
}

/**
 * Canonical seal footer bar (§3.7): top hairline, centered mono blue
 * uppercase two-line copy.
 */
@Composable
fun VoSealFooter(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 24.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(VoBorder))
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "VERUM OMNIS FOUNDATION — PATENT PENDING",
                style = MonoLabel, color = VoAccentBlue, textAlign = TextAlign.Center
            )
            Text(
                "CONSTITUTION V${Constitution.VERSION} FINAL — ARTICLE X NON-WEAPONIZATION DOCTRINE",
                style = MonoLabel, color = VoAccentBlue, textAlign = TextAlign.Center
            )
        }
    }
}
