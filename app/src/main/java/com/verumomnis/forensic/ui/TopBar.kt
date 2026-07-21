package com.verumomnis.forensic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.R
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoGold

/**
 * Shared Verum Omnis top bar (verumglobal.foundation style): circular logo
 * badge, gold serif "VERUM OMNIS" wordmark and the current screen title as a
 * letter-spaced mono label. Used on every screen — the story/hero screen shows
 * the full-width banner instead.
 */
@Composable
fun VerumTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = VoGold)
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Image(
            painter = painterResource(R.drawable.vo_badge),
            contentDescription = "Verum Omnis",
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "VERUM OMNIS",
                fontFamily = Cormorant,
                fontWeight = FontWeight.Bold,
                color = VoGold,
                fontSize = 16.sp,
                letterSpacing = 2.sp
            )
            if (title.isNotBlank()) {
                Text(
                    title.uppercase(),
                    fontFamily = JetBrainsMono,
                    color = VoGold.copy(alpha = 0.65f),
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp
                )
            }
        }
        trailing()
    }
}
