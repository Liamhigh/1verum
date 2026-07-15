package com.verumomnis.forensic.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.model.GuardianAssessment
import com.verumomnis.forensic.model.GuardianViolation
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoRed
import com.verumomnis.forensic.ui.theme.VoSurface
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary

/**
 * Full-screen B9 constitutional hard stop.
 *
 * Displayed when the Guardian Brain detects an Article X weaponization attempt,
 * severe coercion, or another non-negotiable constitutional violation. The user
 * cannot interact with the rest of the app until they acknowledge the block.
 */
@Composable
fun GuardianBlockScreen(
    assessment: GuardianAssessment,
    onClearCase: () -> Unit,
    onReadConstitution: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoBackground)
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = "Constitutional hard stop",
            tint = VoRed,
            modifier = Modifier.size(72.dp)
        )

        Spacer(Modifier.height(20.dp))

        Text(
            "CONSTITUTIONAL HARD STOP",
            color = VoRed,
            fontSize = 13.sp,
            letterSpacing = 3.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Article X — Anti-Weaponization",
            color = VoTextPrimary,
            fontFamily = Cormorant,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "The Guardian Brain has detected content that violates the Verum Omnis Constitution. " +
                "This device cannot be used to plan, target, or coordinate harm. " +
                "The attempt has been recorded in the immutable Silence Ledger.",
            color = VoTextMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Text(
            "VIOLATIONS (${assessment.violations.size})",
            color = VoGold,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        assessment.violations.forEach { violation ->
            ViolationCard(violation)
            Spacer(Modifier.height(10.dp))
        }

        if (assessment.notes.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "B9 NOTES",
                color = VoGold,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            assessment.notes.forEach { note ->
                Text(
                    "• $note",
                    color = VoTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onClearCase,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VoRed, contentColor = VoTextPrimary)
        ) {
            Text("Clear case and close", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onReadConstitution,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VoGold)
        ) {
            Text("Read Constitution", color = VoGold, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ViolationCard(violation: GuardianViolation) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(VoSurface)
            .border(1.dp, VoBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                violation.type.name.replace('_', ' '),
                color = VoRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                violation.severity.name,
                color = VoGold,
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            violation.description,
            color = VoTextPrimary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        if (violation.trigger.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Trigger: \"${violation.trigger}\"",
                color = VoTextMuted,
                fontFamily = JetBrainsMono,
                fontSize = 10.sp
            )
        }
    }
}
