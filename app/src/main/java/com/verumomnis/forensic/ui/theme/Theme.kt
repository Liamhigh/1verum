package com.verumomnis.forensic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.verumomnis.forensic.model.Severity

// ---------------------------------------------------------------------------
// Canonical palette — VERUM_UI_TOKENS.md §1 (verumglobal.foundation).
// The theme is permanently dark navy; there is no light mode.
// ---------------------------------------------------------------------------

// §1.1 Navy backgrounds
val VoBackground = Color(0xFF040D1B)   // navy-0 — page background, text on gold
val VoNavy1 = Color(0xFF0A1628)        // navy-1 — alternate section / modal input bg
val VoNavy2 = Color(0xFF0F1F3A)        // navy-2 — raised section / stat card bg
val VoSurface = VoNavy1
val VoSurfaceAlt = Color(0xFF0F3460)   // base for the rgba(15,52,96,x) panel washes

// §1.2 Panel / card fills (translucent washes over navy-0)
val VoPanel = VoSurfaceAlt.copy(alpha = 0.12f)        // primary card
val VoPanelDim = VoSurfaceAlt.copy(alpha = 0.08f)     // secondary panels
val VoPanelRaised = VoSurfaceAlt.copy(alpha = 0.10f)
val VoPanelInput = VoSurfaceAlt.copy(alpha = 0.15f)   // text input bg
val VoWell = VoBackground.copy(alpha = 0.6f)          // sunken well: hash displays
val VoWellDim = VoBackground.copy(alpha = 0.4f)       // preview / table-head bg

// §1.3 Borders
val VoBlueBorder = Color(0xFF1A2E52)                   // `border` — solid
val VoBorderDim = Color(0xFF2A4A82)                    // `border-dim` — ghost text tone
val VoBorder = VoBlueBorder.copy(alpha = 0.5f)         // `border-soft` — most common
val VoBorderSofter = VoBlueBorder.copy(alpha = 0.4f)   // meta-card / info-item
val VoBorderRow = VoBlueBorder.copy(alpha = 0.3f)      // .id-field row divider
val VoPanelBorder = VoBorder

// §1.4 Accents
val VoGold = Color(0xFFD4A843)         // brand gold — CTAs, kickers, card headings
val VoGoldSoft = Color(0xFFE8C567)     // gold hover
val VoGoldDark = Color(0xFFB8942A)     // dark end of the gold CTA gradient
val VoPrimary = VoGold
val VoAmber = VoGold
val VoAccentBlue = Color(0xFF4A7EC7)   // links, field labels, secondary text
val VoBlueDeep = Color(0xFF1A5F9E)     // dark end of the blue gradient button
val VoBlueSoft = Color(0xFF7EA8E0)     // legacy / neutral result headings
/** Gold washes (honesty-note bg etc.). */
val VoGoldWash = VoGold.copy(alpha = 0.06f)
val VoGoldWashStrong = VoGold.copy(alpha = 0.08f)
val VoGoldBorder = VoGold.copy(alpha = 0.35f)

// §1.5 Text — never pure white
val VoHeading = Color(0xFFF8F9FA)      // offwhite — headings, strong text
val VoTextPrimary = Color(0xFFD5D8DD)  // body — default body text
val VoTextBody = VoTextPrimary
val VoTextMuted = Color(0xFF94A3B8)    // muted descriptions
val VoTextSecondary = VoTextMuted
val VoTextFaint = Color(0xFF3D4C63)    // faint/disabled label

// §1.6 Status
val VoGreen = Color(0xFF22C55E)        // success — hash values, complete steps
val VoGreenBright = Color(0xFF4ADE80)  // VERIFIED heading, match badge
val VoRed = Color(0xFFEF4444)          // error — pipeline error fill
val VoRedText = Color(0xFFF87171)      // error-bright — TAMPERED heading, hits

/**
 * Severity accents per PD16 / the site pattern: severity is TEXT
 * (Critical/High/Medium/Low), never a score or percentage, and the accents
 * stay on the site palette — red family for critical, gold for elevated,
 * blue for low. No off-palette oranges.
 */
fun severityColor(severity: Severity): Color = when (severity) {
    Severity.CRITICAL -> VoRedText
    Severity.VERY_HIGH -> VoRed
    Severity.HIGH -> VoGold
    Severity.MODERATE -> VoGoldSoft
    Severity.LOW -> VoAccentBlue
}

/** Same mapping keyed by the badge strings some screens compute. */
fun severityColor(name: String): Color {
    // Delegates to the enum mapping above so the palette is defined once: a new
    // severity can never end up coloured one way from the enum and another way
    // from a badge string. Only the string-to-enum normalisation lives here.
    val normalised = name.trim().uppercase().replace(' ', '_')
    val severity = when (normalised) {
        "MEDIUM" -> Severity.MODERATE
        else -> runCatching { Severity.valueOf(normalised) }.getOrNull()
    }
    return severity?.let { severityColor(it) } ?: VoTextMuted
}

/** Gold CTA fill — `linear-gradient(135deg, #D4A843 0%, #b8942a 100%)`. */
val VoGoldGradient = Brush.linearGradient(listOf(VoGold, VoGoldDark))

private val VerumColorScheme = darkColorScheme(
    primary = VoGold,
    onPrimary = VoBackground,
    secondary = VoAccentBlue,
    onSecondary = VoBackground,
    background = VoBackground,
    onBackground = VoTextPrimary,
    surface = VoSurface,
    onSurface = VoTextPrimary,
    surfaceVariant = VoNavy2,
    onSurfaceVariant = VoTextMuted,
    outline = VoBorder,
    outlineVariant = VoBorderRow,
    error = VoRed,
    onError = VoHeading
)

@Composable
fun VerumOmnisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VerumColorScheme,
        typography = VerumTypography,
        content = content
    )
}
