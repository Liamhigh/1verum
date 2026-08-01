package com.verumomnis.forensic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Deep Research Navigation & Screen Routing
 *
 * Integrates all deep research screens:
 * 1. QueryInput → Research Query Entry
 * 2. InProgress → Live Progress Tracking
 * 3. Complete → Results Review & Feedback
 * 4. EngineStats → Admin Dashboard
 */

sealed class DeepResearchRoute {
    object QueryInput : DeepResearchRoute()
    data class InProgress(val sessionId: String) : DeepResearchRoute()
    data class Results(val sessionId: String) : DeepResearchRoute()
    object Stats : DeepResearchRoute()
}

/**
 * Main Deep Research Navigator
 * Routes between different screens based on state
 */
@Composable
fun DeepResearchNavigator(
    currentRoute: DeepResearchRoute = DeepResearchRoute.QueryInput,
    vaultEvidenceIds: List<String> = emptyList(),
    onNavigate: (DeepResearchRoute) -> Unit = {}
) {
    val researchVm = viewModel<DeepResearchViewModel>()
    val state by researchVm.state.collectAsState()

    when (state) {
        is DeepResearchViewModel.ResearchState.Idle -> {
            DeepResearchScreen(
                vaultEvidenceIds = vaultEvidenceIds,
                onStartResearch = { query, evidenceIds, jurisdiction ->
                    researchVm.startResearch(
                        query = query,
                        vaultEvidenceIds = evidenceIds,
                        jurisdiction = jurisdiction
                    )
                }
            )
        }

        is DeepResearchViewModel.ResearchState.QueryInput -> {
            val inputState = state as DeepResearchViewModel.ResearchState.QueryInput
            DeepResearchScreen(
                vaultEvidenceIds = vaultEvidenceIds,
                onStartResearch = { query, evidenceIds, jurisdiction ->
                    researchVm.startResearch(
                        query = query,
                        vaultEvidenceIds = evidenceIds,
                        jurisdiction = jurisdiction
                    )
                }
            )
        }

        is DeepResearchViewModel.ResearchState.InProgress -> {
            val inProgressState = state as DeepResearchViewModel.ResearchState.InProgress
            ResearchProgressScreen(
                sessionId = inProgressState.sessionId,
                query = inProgressState.query,
                startedAt = inProgressState.startedAt,
                onCancel = { researchVm.cancelResearch() }
            )
        }

        is DeepResearchViewModel.ResearchState.Complete -> {
            val completeState = state as DeepResearchViewModel.ResearchState.Complete
            ResearchResultsScreen(
                sessionId = completeState.sessionId,
                narrative = completeState.narrative,
                contradictionsFound = completeState.contradictionsFound,
                newRulesSuggested = completeState.newRulesSuggested,
                confidence = completeState.confidence,
                verificationUrl = completeState.verificationUrl,
                onSubmitFeedback = { approved, corrections, comments ->
                    researchVm.submitVerificationFeedback(approved, corrections, comments)
                    researchVm.reset()
                },
                onExportReport = {
                    researchVm.exportResearchReport(completeState.sessionId)
                }
            )
        }

        is DeepResearchViewModel.ResearchState.Error -> {
            val errorState = state as DeepResearchViewModel.ResearchState.Error
            ErrorScreen(
                message = errorState.message,
                onRetry = { researchVm.showQueryInput() },
                onBack = { researchVm.reset() }
            )
        }
    }
}

/**
 * Engine Statistics Navigator
 */
@Composable
fun EngineStatsNavigator() {
    val statsVm = viewModel<EngineStatsViewModel>()

    EngineStatsScreen(
        onSyncToWebsite = {
            statsVm.syncFeedbackToWebsite()
        },
        onCheckRuleUpdates = {
            statsVm.checkForRuleUpdates()
        }
    )
}

/**
 * Error Screen - Shown when research encounters an error
 */
@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.foundation.layout.Modifier
            .fillMaxSize()
            .background(com.verumomnis.forensic.ui.theme.VoBackground)
            .padding(16.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        androidx.compose.material.icons.Icons.Filled.Error.let { icon ->
            androidx.compose.material3.Icon(
                icon,
                contentDescription = null,
                tint = com.verumomnis.forensic.ui.theme.VoRed,
                modifier = androidx.compose.foundation.layout.Modifier.size(64.dp)
            )
        }

        androidx.compose.material3.Text(
            text = "Research Error",
            fontSize = 20.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = com.verumomnis.forensic.ui.theme.VoTextPrimary,
            modifier = androidx.compose.foundation.layout.Modifier.padding(top = 16.dp)
        )

        androidx.compose.material3.Text(
            text = message,
            fontSize = 14.sp,
            color = com.verumomnis.forensic.ui.theme.VoTextMuted,
            modifier = androidx.compose.foundation.layout.Modifier.padding(top = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Modifier.height(24.dp))

        androidx.compose.material3.Button(
            onClick = onRetry,
            modifier = androidx.compose.foundation.layout.Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = com.verumomnis.forensic.ui.theme.VoGold
            )
        ) {
            androidx.compose.material3.Text("Try Again", color = com.verumomnis.forensic.ui.theme.VoBackground)
        }

        androidx.compose.material3.OutlinedButton(
            onClick = onBack,
            modifier = androidx.compose.foundation.layout.Modifier.fillMaxWidth()
        ) {
            androidx.compose.material3.Text("Go Back")
        }
    }
}

// Import statements for inline usage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoRed
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary
