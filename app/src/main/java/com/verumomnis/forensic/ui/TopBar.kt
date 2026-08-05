package com.verumomnis.forensic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.R
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoBlueBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoHeading
import com.verumomnis.forensic.ui.theme.VoTextMuted

/**
 * Shared Verum Omnis top bar, mirroring the verumglobal.foundation site header:
 * circular logo badge, gold serif "VERUM OMNIS" wordmark, the current screen title
 * as a letter-spaced mono label, and a hairline rule separating it from content.
 *
 * The bar owns the status-bar inset ([windowInsetsPadding]) because the app runs
 * edge-to-edge (MainActivity.enableEdgeToEdge). Without it the wordmark renders
 * underneath the system clock. The opaque background also stops the constellation
 * background and scrolling content bleeding through behind the status bar.
 */
@Composable
fun VerumTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(VoBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = VoGold)
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            // The globe travels with the wordmark on every screen — it is the mark
            // people recognise from verumglobal.foundation, and dropping it on
            // back-arrow screens made chat look like a different product. It was
            // dropped because badge + wordmark + two trailing actions truncated the
            // wordmark to "VERUM O…"; the badge and its gap are tightened here to
            // buy that width back rather than sacrificing the mark.
            Image(
                painter = painterResource(R.drawable.vo_badge),
                contentDescription = null,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                // White, as on the website and in the logo lockup. Gold is the CTA
                // colour on both surfaces; spending it on the wordmark in the bar
                // was the single biggest reason the app read as a different brand.
                Text(
                    "VERUM OMNIS",
                    fontFamily = Cormorant,
                    fontWeight = FontWeight.Bold,
                    color = VoHeading,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // The subtitle names the screen. On chat the screen *is* "Verum
                // Omnis", so it rendered the brand twice, one above the other,
                // while the wordmark itself was truncated to "VERUM OMN…".
                if (title.isNotBlank() && !title.equals("Verum Omnis", ignoreCase = true)) {
                    Text(
                        title.uppercase(),
                        fontFamily = JetBrainsMono,
                        color = VoTextMuted,
                        fontSize = 9.sp,
                        letterSpacing = 1.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(VoBlueBorder)
        )
    }
}
