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
val VoTextPrimary = Color(0xFFE8E6E1)  // primary text
val VoTextMuted = Color(0xFF8A94A6)    // muted text
val VoTextSecondary = Color(0xFF94a3b8) // supporting text
/** Standard border: gold at 25% alpha (verumglobal.foundation). */
val VoBorder = VoGold.copy(alpha = 0.25f)

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
