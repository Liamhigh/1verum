package com.verumomnis.forensic

import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.SourceSans
import com.verumomnis.forensic.ui.theme.VerumTypography
import org.junit.Assert.assertEquals
import org.junit.Test

class TypographyRoleTest {
    @Test fun sharedMaterialStylesUseTheSameThreeFontRolesOnEveryScreen() {
        listOf(
            VerumTypography.displayLarge,
            VerumTypography.displayMedium,
            VerumTypography.displaySmall,
            VerumTypography.headlineLarge,
            VerumTypography.headlineMedium,
            VerumTypography.headlineSmall,
            VerumTypography.titleLarge,
            VerumTypography.titleMedium
        ).forEach { assertEquals("Display and heading styles use Cormorant", Cormorant, it.fontFamily) }

        listOf(
            VerumTypography.titleSmall,
            VerumTypography.bodyLarge,
            VerumTypography.bodyMedium,
            VerumTypography.bodySmall
        ).forEach { assertEquals("Body and input styles use Source Sans", SourceSans, it.fontFamily) }

        listOf(
            VerumTypography.labelLarge,
            VerumTypography.labelMedium,
            VerumTypography.labelSmall
        ).forEach { assertEquals("Labels and button styles use JetBrains Mono", JetBrainsMono, it.fontFamily) }
    }
}
