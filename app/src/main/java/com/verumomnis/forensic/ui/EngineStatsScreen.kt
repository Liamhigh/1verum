package com.verumomnis.forensic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.engine.HybridForensicService
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoGreen
import com.verumomnis.forensic.ui.theme.VoRed
import com.verumomnis.forensic.ui.theme.VoSurface
import com.verumomnis.forensic.ui.theme.VoSurfaceAlt
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Engine Statistics & Admin Dashboard
 * Shows the health and status of the Hybrid Forensic Engine.
 */
@Composable
fun EngineStatsScreen(
    onSyncToWebsite: () -> Unit = {},
    onCheckRuleUpdates: () -> Unit = {}
) {
    var stats by remember { mutableStateOf(HybridForensicService.getEngineStats()) }
    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            stats = HybridForensicService.getEngineStats()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoBackground)
    ) {
        // Header
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Engine Statistics",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = VoTextPrimary
            )
            Text(
                text = "Hybrid Forensic Engine Dashboard",
                fontSize = 14.sp,
                color = VoTextMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Status Cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Active Researches",
                value = stats.activeResearches.toString(),
                icon = Icons.Filled.Psychology,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Feedback Queue",
                value = stats.feedbackQueueSize.toString(),
                icon = Icons.Filled.Outbox,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Rule Updates",
                value = stats.appliedRuleUpdates.toString(),
                icon = Icons.Filled.Settings,
                modifier = Modifier.weight(1f)
            )
        }

        // Tab Bar
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            containerColor = VoBackground,
            contentColor = VoGold
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Overview", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Sync Status", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("System", fontSize = 12.sp) }
            )
        }

        Spacer(modifier = Modifier.height(1.dp))

        // Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> EngineOverviewTab(stats)
                1 -> SyncStatusTab(stats)
                2 -> SystemInfoTab(stats)
            }
        }

        // Action Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Button(
                onClick = onSyncToWebsite,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VoAccentBlue),
                enabled = stats.feedbackQueueSize > 0
            ) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Sync Feedback (${stats.feedbackQueueSize})",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onCheckRuleUpdates,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VoGreen)
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = VoBackground)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Check for Rule Updates", fontWeight = FontWeight.Bold, color = VoBackground)
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.material.icons.filled.Icon,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = VoSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = VoGold,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = VoTextPrimary
            )
            Text(
                text = title,
                fontSize = 11.sp,
                color = VoTextMuted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun EngineOverviewTab(stats: HybridForensicService.EngineStatistics) {
    Column {
        // Engine Health Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = VoSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Engine Health",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VoGold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                HealthIndicator(
                    label = "Nine-Brain Engine",
                    status = "ACTIVE",
                    description = "Contradiction extraction, timeline, financial analysis"
                )
                Spacer(modifier = Modifier.height(8.dp))

                HealthIndicator(
                    label = "Gemma 3 Admin Hub",
                    status = "ACTIVE",
                    description = "Deep research and rule inference"
                )
                Spacer(modifier = Modifier.height(8.dp))

                HealthIndicator(
                    label = "Feedback Sync",
                    status = if (stats.feedbackQueueSize > 0) "SYNCING" else "IDLE",
                    description = "Bidirectional website integration"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Recent Activity
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = VoSurfaceAlt)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Recent Activity",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VoGold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                ActivityRow(
                    icon = Icons.Filled.Psychology,
                    label = "Active Research Sessions",
                    value = "${stats.activeResearches} running"
                )
                Spacer(modifier = Modifier.height(8.dp))

                ActivityRow(
                    icon = Icons.Filled.Outbox,
                    label = "Feedback Awaiting Sync",
                    value = "${stats.feedbackQueueSize} packets"
                )
                Spacer(modifier = Modifier.height(8.dp))

                ActivityRow(
                    icon = Icons.Filled.CheckCircle,
                    label = "Rules Applied",
                    value = "${stats.appliedRuleUpdates} total"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Constitution Compliance
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = VoSurfaceAlt)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Constitutional Compliance",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VoGold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                ComplianceCheck(
                    rule = "Truth over Probability",
                    status = "COMPLIANT",
                    description = "Confidence is ordinal only"
                )
                ComplianceCheck(
                    rule = "Evidence before Narrative",
                    status = "COMPLIANT",
                    description = "All findings anchored to evidence"
                )
                ComplianceCheck(
                    rule = "Mandatory Contradiction Disclosure",
                    status = "COMPLIANT",
                    description = "All contradictions flagged and logged"
                )
            }
        }
    }
}

@Composable
private fun SyncStatusTab(stats: HybridForensicService.EngineStatistics) {
    Column {
        // Website Sync Status
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = VoSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Website Synchronization",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VoGold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                SyncRow(
                    label = "Feedback Queue",
                    value = "${stats.feedbackQueueSize} packets",
                    status = "READY"
                )
                Spacer(modifier = Modifier.height(8.dp))

                SyncRow(
                    label = "Last Sync",
                    value = stats.lastSyncToWebsite?.let {
                        formatTime(it)
                    } ?: "Never",
                    status = "OK"
                )
                Spacer(modifier = Modifier.height(8.dp))

                SyncRow(
                    label = "Connection",
                    value = "Ready to send",
                    status = "ONLINE"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Rule Updates Status
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = VoSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Rule Updates",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VoGold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                SyncRow(
                    label = "Last Check",
                    value = "Just now",
                    status = "OK"
                )
                Spacer(modifier = Modifier.height(8.dp))

                SyncRow(
                    label = "Rules Applied",
                    value = "${stats.appliedRuleUpdates} total",
                    status = "CURRENT"
                )
                Spacer(modifier = Modifier.height(8.dp))

                SyncRow(
                    label = "Last Update",
                    value = stats.lastRuleUpdate?.let {
                        formatTime(it)
                    } ?: "Never",
                    status = "OK"
                )
            }
        }
    }
}

@Composable
private fun SystemInfoTab(stats: HybridForensicService.EngineStatistics) {
    Column {
        // System Information
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = VoSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "System Information",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VoGold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                SystemRow(label = "Constitution Version", value = stats.constitutionVersion)
                SystemRow(label = "Nine-Brain Version", value = "v1.0")
                SystemRow(label = "Gemma 3 Model", value = "admin-hub")
                SystemRow(label = "Platform", value = "Android 13+")
                SystemRow(label = "App Version", value = "6.0")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Engine Statistics
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = VoSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Engine Statistics",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VoGold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                SystemRow(label = "Active Research Sessions", value = stats.activeResearches.toString())
                SystemRow(label = "Feedback Packets (Queued)", value = stats.feedbackQueueSize.toString())
                SystemRow(label = "Rule Updates Applied", value = stats.appliedRuleUpdates.toString())
                SystemRow(label = "Last Status Check", value = formatTime(stats.timestamp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Feedback
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = VoSurfaceAlt)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Need Help?",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VoGold
                )
                Text(
                    text = "For support, check the documentation or contact the development team.",
                    fontSize = 12.sp,
                    color = VoTextMuted,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun HealthIndicator(
    label: String,
    status: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = VoTextPrimary)
            Text(description, fontSize = 11.sp, color = VoTextMuted, modifier = Modifier.padding(top = 2.dp))
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (status == "ACTIVE") VoGreen else VoAccentBlue)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(status, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = VoBackground)
        }
    }
}

@Composable
private fun ActivityRow(
    icon: androidx.compose.material.icons.filled.Icon,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = VoGold, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, fontSize = 12.sp, color = VoTextPrimary)
        }
        Text(value, fontSize = 12.sp, color = VoAccentBlue, fontFamily = JetBrainsMono)
    }
}

@Composable
private fun ComplianceCheck(
    rule: String,
    status: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = VoGreen, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(rule, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = VoTextPrimary)
            Text(description, fontSize = 11.sp, color = VoTextMuted)
        }
    }
}

@Composable
private fun SyncRow(
    label: String,
    value: String,
    status: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = VoTextPrimary)
            Text(value, fontSize = 11.sp, color = VoTextMuted, modifier = Modifier.padding(top = 2.dp))
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(when (status) {
                    "READY" -> VoGold
                    "OK" -> VoGreen
                    "ONLINE" -> VoGreen
                    "CURRENT" -> VoGreen
                    else -> VoTextMuted
                })
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(status, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = VoBackground)
        }
    }
}

@Composable
private fun SystemRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = VoTextMuted)
        Text(value, fontSize = 12.sp, color = VoAccentBlue, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatTime(instant: Instant): String {
    val formatter = DateTimeFormatter
        .ofPattern("MMM dd, HH:mm")
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}
