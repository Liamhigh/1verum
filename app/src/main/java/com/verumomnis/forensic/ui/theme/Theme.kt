package com.verumomnis.forensic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette aligned with webdocsol (www.verumglobal.foundation / seal-module v1.2).
val VoBackground = Color(0xFF040D1B)
val VoSurface = Color(0xFF0A1422)      // elevated panel
val VoSurfaceAlt = Color(0xFF0F3460)   // accent card base (#0F3460 at low alpha applied in modifiers)
val VoGold = Color(0xFFD4A843)         // CTA / primary accent
val VoGoldSoft = Color(0xFFE8C567)
val VoPrimary = VoGold
val VoAccentBlue = Color(0xFF4A7EC7)   // links / secondary / labels
val VoAmber = VoGold
val VoGreen = Color(0xFF22c55e)
val VoRed = Color(0xFFef4444)
val VoTextPrimary = Color(0xFFF8F9FA)  // headings
val VoTextMuted = Color(0xFFD5D8DD)    // body text
val VoTextSecondary = Color(0xFF94a3b8) // supporting text
val VoBorder = Color(0xFF1A2E52)       // borders / dashed outlines

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
