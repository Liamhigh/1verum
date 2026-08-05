package com.verumomnis.forensic.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.model.Severity
import com.verumomnis.forensic.model.TimelineEvent
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoRed
import com.verumomnis.forensic.ui.theme.VoSurface
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun TimelineVisualizationScreen(state: UiState) {
    val report = state.report

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (report == null) {
            VoCard(title = "TIMELINE", icon = Icons.Filled.AccessTime) {
                Text(
                    "No report generated yet. Start a forensic scan to see the event timeline.",
                    color = VoTextMuted, fontSize = 13.sp, lineHeight = 16.sp
                )
            }
            return@Column
        }

        VoCard(title = "RECONSTRUCTED TIMELINE", icon = Icons.Filled.AccessTime) {
            Text(
                "Chronological reconstruction of events extracted from sealed evidence.",
                color = VoTextMuted, fontSize = 12.sp, lineHeight = 16.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Red flags (▲) indicate >730-day gaps (consciousness-of-guilt indicator).",
                color = VoGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = JetBrainsMono
            )
        }

        if (report.timeline.isEmpty()) {
            VoCard(title = "EVENTS", icon = Icons.Filled.AccessTime) {
                Text("No events reconstructed.", color = VoTextMuted, fontSize = 13.sp)
            }
            return@Column
        }

        val sortedEvents = report.timeline.sortedBy { it.dateTime }

        // Detect gaps >730 days between events
        val eventsWithGaps = mutableListOf<Pair<TimelineEvent, Boolean>>()
        sortedEvents.forEachIndexed { index, event ->
            val hasLargeGapBefore = if (index > 0) {
                val prevDate = parseDateTime(sortedEvents[index - 1].dateTime)
                val currDate = parseDateTime(event.dateTime)
                prevDate != null && currDate != null && ChronoUnit.DAYS.between(prevDate, currDate) > 730
            } else false
            eventsWithGaps.add(Pair(event, hasLargeGapBefore))
        }

        VoSectionLabel("${eventsWithGaps.size} events · ${eventsWithGaps.count { it.second }} large gaps")
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(eventsWithGaps) { (event, hasLargeGapBefore) ->
                if (hasLargeGapBefore) {
                    GapIndicator()
                }
                TimelineEventCard(event)
            }
        }
    }
}

@Composable
private fun TimelineEventCard(event: TimelineEvent) {
    // Site palette only (PD16): red family / gold / blue — no off-palette oranges.
    val severityColor = com.verumomnis.forensic.ui.theme.severityColor(event.severity)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .background(VoSurface, RoundedCornerShape(12.dp))
            .border(1.dp, VoBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    event.dateTime.take(10), // YYYY-MM-DD
                    color = VoGold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, fontFamily = JetBrainsMono
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    event.description,
                    color = VoTextPrimary, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Row(
                modifier = Modifier
                    .background(severityColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .border(1.dp, severityColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(8.dp, 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    event.severity.name.replace('_', ' '),
                    color = severityColor, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp, fontFamily = JetBrainsMono
                )
            }
        }

        if (event.legalSignificance.isNotEmpty()) {
            Text(
                "Legal: ${event.legalSignificance}",
                color = VoGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
            )
        }

        if (event.page > 0) {
            Text(
                "Source · Page ${event.page} · SHA-512: ${event.sha512.take(20)}…",
                color = VoTextMuted, fontSize = 10.sp, fontFamily = JetBrainsMono
            )
        }
    }
}

@Composable
private fun GapIndicator() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VoRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .background(VoRed.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "▲ TEMPORAL GAP EXCEEDS 730 DAYS",
            color = VoRed, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = JetBrainsMono
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Consciousness-of-guilt indicator: party concealed timeline",
            color = VoTextMuted, fontSize = 10.sp
        )
    }
}

private fun parseDateTime(dateTimeStr: String): LocalDateTime? = runCatching {
    LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_DATE_TIME)
}.getOrNull()
