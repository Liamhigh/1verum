@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.verumomnis.forensic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.R

/** Brand fonts mirroring www.verumglobal.foundation. */
val Cormorant = FontFamily(
    Font(R.font.cormorant_garamond, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.cormorant_garamond, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.cormorant_garamond, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.cormorant_garamond, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

val SourceSans = FontFamily(
    Font(R.font.source_sans3, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.source_sans3, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.source_sans3, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.source_sans3, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.jetbrains_mono, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500)))
)

/**
 * Cormorant Light display style for big headlines (verdicts, hero titles),
 * tracking tightened to -0.03em per the verumglobal.foundation design lock.
 */
val CormorantDisplay = TextStyle(
    fontFamily = Cormorant,
    fontWeight = FontWeight.Light,
    letterSpacing = (-0.03).em
)

val VerumTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = Cormorant, fontWeight = FontWeight.Light, letterSpacing = (-0.03).em),
        displayMedium = base.displayMedium.copy(fontFamily = Cormorant, fontWeight = FontWeight.Light, letterSpacing = (-0.03).em),
        displaySmall = base.displaySmall.copy(fontFamily = Cormorant, fontWeight = FontWeight.Light, letterSpacing = (-0.03).em),
        headlineLarge = base.headlineLarge.copy(fontFamily = Cormorant, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = Cormorant, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Cormorant, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleMedium = base.titleMedium.copy(fontFamily = SourceSans, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = SourceSans, fontWeight = FontWeight.SemiBold),
        // 1.6 line-height on body copy for readability (website rhythm).
        bodyLarge = base.bodyLarge.copy(fontFamily = SourceSans, lineHeight = 1.6.em),
        bodyMedium = base.bodyMedium.copy(fontFamily = SourceSans, lineHeight = 1.6.em),
        bodySmall = base.bodySmall.copy(fontFamily = SourceSans),
        labelLarge = base.labelLarge.copy(fontFamily = SourceSans, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = SourceSans),
        labelSmall = base.labelSmall.copy(fontFamily = SourceSans)
    )
}

/** Monospace, uppercase-ready, letter-spaced label style for field & hash labels. */
val MonoLabel = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    letterSpacing = 1.5.sp
)
