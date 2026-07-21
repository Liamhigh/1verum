package com.verumomnis.forensic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.R
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoSurface
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary

@Composable
fun StoryScreen(onEnter: () -> Unit, onReadConstitution: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
        Text("AI FORENSICS FOR TRUTH", color = VoGold, fontSize = 12.sp, letterSpacing = 4.sp, fontWeight = FontWeight.SemiBold)
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("528", "pages sealed", Modifier.weight(1f))
            Stat("111", "contradictions", Modifier.weight(1f))
            Stat("9", "forensic brains", Modifier.weight(1f))
            Stat("R0", "cost to litigant", Modifier.weight(1f))
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
            Text("— Verum Omnis · Constitution v6.0 FINAL", color = VoGold, fontSize = 11.sp, letterSpacing = 1.sp)
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
        Text(value, color = VoGold, fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 26.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, color = VoTextMuted, fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 12.sp)
    }
}
