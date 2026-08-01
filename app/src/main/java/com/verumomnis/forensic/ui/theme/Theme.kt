package com.verumomnis.forensic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette aligned with verumglobal.foundation CSS (design tokens, SPEC-ANDROID-UI).
val VoBackground = Color(0xFF040D1B)   // deep navy page background
val VoSurface = Color(0xFF0A1526)      // card surface
val VoSurfaceAlt = Color(0xFF0F3460)   // accent card base (applied at low alpha in modifiers)
val VoGold = Color(0xFFD4A843)         // primary gold / CTA
val VoGoldDark = Color(0xFFB8942A)     // gold-dark (pressed / disabled gold)
val VoGoldSoft = Color(0xFFE8C567)
val VoPrimary = VoGold
val VoAccentBlue = Color(0xFF4A7EC7)   // links / secondary / labels
val VoAmber = VoGold
val VoGreen = Color(0xFF22c55e)
val VoRed = Color(0xFFef4444)
val VoHeading = Color(0xFFF8F9FA)      // heading text (DESIGN_LOCK)
val VoBlueBorder = Color(0xFF1A2E52)   // borders / dividers / upload zone dash (DESIGN_LOCK)
val VoTextPrimary = Color(0xFFE8E6E1)  // primary text
val VoTextMuted = Color(0xFF8A94A6)    // muted text
val VoTextSecondary = Color(0xFF94a3b8) // supporting text
/** Standard border: gold at 25% alpha (verumglobal.foundation). */
val VoBorder = VoGold.copy(alpha = 0.25f)

// --- Mock-up tokens (docs/design/Verum-Vault-Android.dc.html, spec §0) ---
/** Body copy in the mock-up (`#D5D8DD`) — lighter than [VoTextPrimary]. */
val VoTextBody = Color(0xFFD5D8DD)
/** Faint/disabled label (`#3d4c63`) — dimmer than [VoTextMuted]. */
val VoTextFaint = Color(0xFF3D4C63)
/** Panel/card fill: `rgba(15,52,96,0.08)` — [VoSurfaceAlt] at 8%. */
val VoPanel = VoSurfaceAlt.copy(alpha = 0.08f)
/** Panel/card fill, raised variant: `rgba(15,52,96,0.10)`. */
val VoPanelRaised = VoSurfaceAlt.copy(alpha = 0.10f)
/** Panel border: `rgba(26,46,82,0.5)` — [VoBlueBorder] at 50%. */
val VoPanelBorder = VoBlueBorder.copy(alpha = 0.5f)
/** Flagged/danger text in the mock-up (`#F87171`), softer than [VoRed]. */
val VoRedText = Color(0xFFF87171)

private val VerumColorScheme = darkColorScheme(
    primary = VoGold,
    onPrimary = VoBackground,
    secondary = VoAccentBlue,
    onSecondary = VoBackground,
    background = VoBackground,
    onBackground = VoTextPrimary,
    surface = VoSurface,
    onSurface = VoTextPrimary,
    surfaceVariant = VoSurfaceAlt,
    onSurfaceVariant = VoTextMuted,
    outline = VoBorder,
    error = VoRed
)

@Composable
fun VerumOmnisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VerumColorScheme,
        typography = VerumTypography,
        content = content
    )
}
