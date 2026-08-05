package com.verumomnis.forensic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.R
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoBlueBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoGoldDark
import com.verumomnis.forensic.ui.theme.VoGreen
import com.verumomnis.forensic.ui.theme.VoHeading
import com.verumomnis.forensic.ui.theme.VoPanel
import com.verumomnis.forensic.ui.theme.VoPanelBorder
import com.verumomnis.forensic.ui.theme.VoPanelRaised
import com.verumomnis.forensic.ui.theme.VoRedText
import com.verumomnis.forensic.ui.theme.VoTextBody

/**
 * Landing screen, matching the Verum Vault mock-up (`isLanding`) and the
 * verumglobal.foundation theme.
 *
 * Replaces the long-form story page. Every colour here is a theme token verified
 * against the site's own CSS: `#040D1B` background, `#D4A843` gold,
 * `rgba(15,52,96,0.08)` panels, `rgba(26,46,82,0.5)` borders.
 *
 * The three stat tiles are bound to real vault state — spec §3.1 requires counts,
 * not the literals the mock-up shows.
 */
@Composable
fun LandingScreen(
    sealedCount: Int,
    verifiedCount: Int,
    flaggedCount: Int,
    recent: List<RecentActivity>,
    onVerify: () -> Unit,
    onSeal: () -> Unit,
    onAskVerum: () -> Unit,
    onViewAll: () -> Unit,
    onOpenRecent: (RecentActivity) -> Unit,
    onConstitution: () -> Unit,
    onDocuments: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoBackground)
            // This screen renders outside the Scaffold, so it gets no insets from
            // it: without this the footer and Recent Activity sit underneath the
            // system navigation bar.
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
    ) {
        Hero()
        Spacer(Modifier.height(20.dp))
        StatRow(sealedCount, verifiedCount, flaggedCount)
        Spacer(Modifier.height(20.dp))
        CtaRow(onVerify = onVerify, onSeal = onSeal)
        Spacer(Modifier.height(22.dp))
        AskVerumCard(onClick = onAskVerum)
        Spacer(Modifier.height(22.dp))
        RecentActivitySection(recent, onViewAll = onViewAll, onOpen = onOpenRecent)
        Spacer(Modifier.height(20.dp))
        FooterLinks(onConstitution = onConstitution, onDocuments = onDocuments)
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * A text size that ignores the system font scale.
 *
 * The mock-up's chrome — 9.5px mono labels, button captions — is laid out to the
 * pixel. On a device with display size or font size turned up, `sp` scales those
 * captions until "VERIFIED" wraps to "VERIFIE / D" and the tiles go ragged, which
 * is exactly how this screen looked on the test handset.
 *
 * Applied only to fixed chrome sitting beside something larger and legible (a
 * count, a button). Body copy and headings keep `sp` and scale normally, so a
 * user who needs large text still gets it where it carries meaning.
 */
@Composable
private fun fixedSp(dp: Float) = with(LocalDensity.current) { dp.dp.toSp() }

/** One row of Recent Activity. Kept UI-only so the screen stays previewable. */
data class RecentActivity(
    val id: String,
    val name: String,
    val subtitle: String,
    /** True when the subtitle reports a flagged/adverse state — rendered red. */
    val flagged: Boolean = false
) {
    /** Serif initial shown in the tile, mirroring the mock-up. */
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"
}

@Composable
private fun Hero() {
    // The mock-up's radial gold wash behind the banner. Compose has no radial
    // gradient brush for a box this shape, so a soft vertical fade reproduces
    // the same effect at the top of the screen.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(VoGold.copy(alpha = 0.08f), VoBackground)
                )
            )
            .padding(top = 32.dp, start = 20.dp, end = 20.dp, bottom = 20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.vo_banner),
                contentDescription = "Verum Omnis",
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Truth for All",
                fontFamily = Cormorant,
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
                color = VoHeading
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "A constitutional forensic AI platform built to democratise justice. " +
                    "Free for every citizen.",
                color = VoTextBody,
                fontSize = 12.5.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.85f)
            )
        }
    }
}

@Composable
private fun StatRow(sealed: Int, verified: Int, flagged: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatTile("Sealed", sealed, VoGold, Modifier.weight(1f))
        StatTile("Verified", verified, VoGreen, Modifier.weight(1f))
        StatTile("Flagged", flagged, VoRedText, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: Int, valueColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(VoPanel)
            .border(1.dp, VoPanelBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mono, not Cormorant: the serif renders 1 as I and 0 as O, which is
        // unreadable in a tile whose entire job is a count. Clarity of figures
        // outranks font fidelity here.
        Text(
            value.toString(),
            fontFamily = JetBrainsMono,
            fontSize = fixedSp(22f),
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label.uppercase(),
            fontFamily = JetBrainsMono,
            fontSize = fixedSp(9.5f),
            letterSpacing = 0.4.sp,
            color = VoAccentBlue,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible
        )
    }
}

@Composable
private fun CtaRow(onVerify: () -> Unit, onSeal: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Outline CTA
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, VoBlueBorder.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .clickable { onVerify() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "VERIFY DOCUMENT",
                color = VoHeading,
                fontSize = fixedSp(12.5f),
                letterSpacing = 0.4.sp,
                maxLines = 1,
                softWrap = false
            )
        }
        // Gold gradient CTA, matching the site's linear-gradient(135deg,#D4A843,#b8942a)
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(VoGold, VoGoldDark)))
                .clickable { onSeal() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "SEAL DOCUMENT",
                color = VoBackground,
                fontSize = fixedSp(12.5f),
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun AskVerumCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VoPanelRaised)
            .border(1.dp, VoPanelBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.vo_badge),
            contentDescription = null,
            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(50))
        )
        Spacer(Modifier.width(12.dp))
        Column {
            // Spec §1: the AI is "Verum Omnis" — the mock-up's "Ask Verum AI" and
            // "9-Brain engine" wording predates the locked branding.
            Text("Ask Verum Omnis", color = VoHeading, fontSize = 13.5.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                "Chat with the on-device forensic engine across your sealed vault.",
                color = VoAccentBlue,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun RecentActivitySection(
    recent: List<RecentActivity>,
    onViewAll: () -> Unit,
    onOpen: (RecentActivity) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "RECENT ACTIVITY",
                fontFamily = JetBrainsMono,
                fontSize = fixedSp(10.5f),
                letterSpacing = 1.sp,
                color = VoAccentBlue
            )
            Text(
                "View All",
                fontFamily = JetBrainsMono,
                fontSize = fixedSp(10.5f),
                color = VoGold,
                modifier = Modifier.clickable { onViewAll() }
            )
        }
        Spacer(Modifier.height(10.dp))
        if (recent.isEmpty()) {
            // Deliberately about *reports*, not sealing. The Sealed tile counts
            // vaulted artifacts, so "nothing sealed yet" would contradict a
            // non-zero count on the same screen — which is exactly what it did.
            Text(
                "No forensic reports yet. Run a scan and it will appear here.",
                color = VoTextBody,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            recent.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpen(item) }
                        .padding(vertical = 12.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(VoPanelRaised)
                            .border(1.dp, VoPanelBorder, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            item.initial,
                            fontFamily = Cormorant,
                            color = VoGold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.name,
                            color = VoHeading,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            item.subtitle,
                            color = if (item.flagged) VoRedText else VoAccentBlue,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FooterLinks(onConstitution: () -> Unit, onDocuments: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            "CONSTITUTION",
            fontFamily = JetBrainsMono,
            fontSize = fixedSp(10.5f),
            letterSpacing = 0.8.sp,
            color = VoAccentBlue,
            modifier = Modifier.clickable { onConstitution() }
        )
        Spacer(Modifier.width(18.dp))
        Text(
            "DOCUMENTS",
            fontFamily = JetBrainsMono,
            fontSize = fixedSp(10.5f),
            letterSpacing = 0.8.sp,
            color = VoAccentBlue,
            modifier = Modifier.clickable { onDocuments() }
        )
    }
}
