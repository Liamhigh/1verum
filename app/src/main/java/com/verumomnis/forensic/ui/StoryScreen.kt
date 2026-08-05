package com.verumomnis.forensic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.core.Constitution
import com.verumomnis.forensic.R
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoSurface
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary

@Composable
fun StoryScreen(onEnter: () -> Unit, onReadConstitution: () -> Unit = {}) {
    // This full-bleed hero screen sits outside the Scaffold, so it must inset itself or
    // the banner renders under the status bar and the footer is clipped by the nav bar.
    // Inset BEFORE verticalScroll, so the scrolling viewport itself sits between the
    // system bars. With the order reversed the viewport stays full-bleed and content
    // scrolls underneath the opaque navigation bar.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.vo_banner),
            contentDescription = "Verum Omnis",
            modifier = Modifier.fillMaxWidth(0.82f).clip(RoundedCornerShape(14.dp))
        )
        Spacer(Modifier.height(28.dp))
        // 4sp tracking at 12sp overflowed to a second line ("TRUTH" alone) on a 411dp
        // phone; 2.5sp keeps the site's letter-spaced eyebrow look on one line.
        Text(
            "AI FORENSICS FOR TRUTH",
            color = VoGold,
            fontSize = 11.sp,
            letterSpacing = 2.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text("Truth for All", color = VoTextPrimary, fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 52.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "A constitutional forensic AI platform built to democratise justice. " +
                "Evidence sealed, GPS-anchored and court-ready. Free for every citizen.",
            color = VoTextMuted, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))
        Text(
            "Born from necessity. Built on a phone.",
            color = VoTextPrimary, fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 26.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Verum Omnis was not created in a lab or funded by venture capital. It was born after a " +
                "devastating cross-border fraud, built on a phone at zero cost by a man who had lost " +
                "everything — and it became the first AI-assisted application ever filed in the " +
                "Constitutional Court of South Africa.",
            color = VoTextMuted, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))
        // 2x2 rather than 4-across: four cards on a phone leave ~70dp of text width, which
        // wrapped the labels mid-word ("contradi/ctions").
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("528", "pages sealed", Modifier.weight(1f))
                Stat("111", "contradictions", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("9", "forensic brains", Modifier.weight(1f))
                Stat("R0", "cost to litigant", Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VoSurface, RoundedCornerShape(16.dp))
                .border(1.dp, VoBorder, RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            Text(
                "\"The truth does not require belief. It requires only that you look.\"",
                color = VoTextPrimary, fontFamily = Cormorant, fontStyle = FontStyle.Italic, fontSize = 20.sp,
                lineHeight = 27.sp
            )
            Spacer(Modifier.height(8.dp))
            Text("— Verum Omnis · Constitution v${Constitution.VERSION} FINAL", color = VoGold, fontSize = 11.sp, letterSpacing = 1.sp)
        }

        Spacer(Modifier.height(30.dp))
        VerumPrimaryButton(
            label = "Enter · Truth for All",
            onClick = onEnter,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        VerumSecondaryButton(
            label = "Read Constitution",
            onClick = onReadConstitution,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Text("Free for every citizen · Available on Android", color = VoTextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(VoSurface, RoundedCornerShape(12.dp))
            .border(1.dp, VoBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            color = VoGold,
            fontFamily = Cormorant,
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
            maxLines = 1,
            // Cormorant Garamond defaults to old-style (text) figures, which set digits at
            // x-height with descenders — "111" read as "III" and "R0" as "Ro" on device.
            // Lining + tabular figures give the even, full-height numerals a stat needs.
            style = TextStyle(fontFeatureSettings = "lnum, tnum")
        )
        Spacer(Modifier.height(6.dp))
        Text(label, color = VoTextMuted, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 14.sp)
    }
}
