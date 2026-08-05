@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.verumomnis.forensic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.R

// ---------------------------------------------------------------------------
// Typography — VERUM_UI_TOKENS.md §2. Three families, strict roles:
//   Cormorant Garamond  → ALL display headings (light 300 for page h1,
//                          400–500 for card/section headings)
//   JetBrains Mono      → ALL labels, kickers, nav links, buttons, hashes —
//                          the "forensic metadata" voice (Courier-New on site)
//   Source Sans 3       → body copy, descriptions, inputs
// ---------------------------------------------------------------------------

/** Display serif — 'Cormorant Garamond'. */
val Cormorant = FontFamily(
    Font(R.font.cormorant_garamond, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.cormorant_garamond, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.cormorant_garamond, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.cormorant_garamond, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.cormorant_garamond, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

/** Body sans — 'Source Sans 3'. */
val SourceSans = FontFamily(
    Font(R.font.source_sans3, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.source_sans3, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.source_sans3, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.source_sans3, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

/** Mono — 'JetBrains Mono' (site: Courier New). */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.jetbrains_mono, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.jetbrains_mono, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

/**
 * §2.2 size scale. Page h1 = serif light, tight tracking (−0.03em), lh 1.1;
 * card h3 = serif 500; section h2 = serif 400. Body = Source Sans lh 1.6.
 */
val VerumTypography = Typography().let { base ->
    base.copy(
        // Page h1 (hero / app pages): serif 300, ls −0.03em
        displayLarge = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Light, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1.3).sp),
        displayMedium = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Light, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-1).sp),
        displaySmall = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Light, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.9).sp),
        // Section headings: serif 400
        headlineLarge = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
        headlineMedium = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.25).sp),
        headlineSmall = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
        // Card h3: serif 500
        titleLarge = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 26.sp),
        titleMedium = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 23.sp),
        titleSmall = base.titleSmall.copy(fontFamily = SourceSans, fontWeight = FontWeight.SemiBold),
        // Body: Source Sans, lh ≈1.6
        bodyLarge = base.bodyLarge.copy(fontFamily = SourceSans, fontSize = 16.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = SourceSans, fontSize = 14.sp, lineHeight = 22.sp),
        bodySmall = base.bodySmall.copy(fontFamily = SourceSans, fontSize = 12.sp, lineHeight = 19.sp),
        // Labels: mono voice (buttons, chips)
        labelLarge = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 1.sp),
        labelMedium = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.sp),
        labelSmall = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.1.sp)
    )
}

/**
 * §2.1 the mono-label convention: mono + UPPERCASE + wide letter-spacing.
 * Field label (.id-label): 11px, ls 0.1em, blue.
 */
val MonoLabel = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    letterSpacing = 1.1.sp
)

/** Eyebrow / page-header kicker: mono 12px, ls 0.15em, gold. */
val MonoKicker = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    letterSpacing = 1.8.sp
)

/** Mono value (.id-value): 13px, body tone. */
val MonoValue = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp
)
