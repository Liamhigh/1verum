package com.verumomnis.forensic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette sampled from www.verumglobal.foundation (gold on dark navy).
val VoBackground = Color(0xFF050C15)   // hsl(214 63% 5%)
val VoSurface = Color(0xFF0B1622)      // elevated panel
val VoSurfaceAlt = Color(0xFF16263C)   // hsl(214 47% 16%) accent card
val VoGold = Color(0xFFD1A547)         // hsl(41 60% 55%) primary accent
val VoGoldSoft = Color(0xFFE3C67E)
val VoPrimary = VoGold
val VoAccentBlue = Color(0xFF4E82C6)   // hsl(214 51% 54%) ring/secondary
val VoAmber = Color(0xFFD1A547)
val VoGreen = Color(0xFF3FB950)
val VoRed = Color(0xFFE5484D)
val VoTextPrimary = Color(0xFFF5F7FA)  // hsl(210 17% 98%)
val VoTextMuted = Color(0xFF9CA3AF)
val VoBorder = Color(0xFF1C324E)       // hsl(214 47% 21%)

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
