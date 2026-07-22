package com.verumomnis.forensic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verumomnis.forensic.engine.HybridForensicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * ViewModel for managing deep research state and interactions.
 * Handles research sessions, progress tracking, and results.
 */
class DeepResearchViewModel : ViewModel() {

    sealed class ResearchState {
        object Idle : ResearchState()
        data class QueryInput(
            val query: String = "",
            val selectedEvidenceIds: List<String> = emptyList(),
            val userContext: String = ""
        ) : ResearchState()
        data class InProgress(
            val sessionId: String,
            val query: String,
            val startedAt: Instant
        ) : ResearchState()
        data class Complete(
            val sessionId: String,
            val narrative: String,
            val contradictionsFound: Int,
            val newRulesSuggested: Int,
            val confidence: String,
            val verificationUrl: String,
            val completedAt: Instant
        ) : ResearchState()
        data class Error(val message: String) : ResearchState()
    }

    private val _state = MutableStateFlow<ResearchState>(ResearchState.Idle)
    val state: StateFlow<ResearchState> = _state.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    /**
     * Initialize research input screen
     */
    fun showQueryInput() {
        _state.update { ResearchState.QueryInput() }
    }

    /**
     * Start a deep research query
     */
    fun startResearch(
        query: String,
        vaultEvidenceIds: List<String>,
        jurisdiction: String,
        userContext: String = ""
    ) {
        if (query.isBlank() || vaultEvidenceIds.isEmpty()) {
            _state.update { ResearchState.Error("Query and evidence selection required") }
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Start research via HybridForensicService
                val result = HybridForensicService.startDeepResearch(
                    query = query,
                    vaultEvidenceIds = vaultEvidenceIds,
                    jurisdiction = jurisdiction,
                    userContext = userContext
                )

                _currentSessionId.update { result.sessionId }

                // Update to progress state
                _state.update {
                    ResearchState.InProgress(
                        sessionId = result.sessionId,
                        query = query,
                        startedAt = Instant.now()
                    )
                }

                // Poll for results
                pollForResults(result.sessionId)

            } catch (e: Exception) {
                _state.update { ResearchState.Error("Failed to start research: ${e.message}") }
            }
        }
    }

    /**
     * Poll for research results
     */
    private suspend fun pollForResults(sessionId: String) {
        repeat(120) { // Poll up to 120 times (2 minutes with 1s delays)
            delay(1000)

            val status = HybridForensicService.getResearchStatus(sessionId)

            if (status.status == "COMPLETED") {
                val result = HybridForensicService.getResearchResult(sessionId)
                if (result != null) {
                    _state.update {
                        ResearchState.Complete(
                            sessionId = sessionId,
                            narrative = result.narrative,
                            contradictionsFound = result.contradictionsFound,
                            newRulesSuggested = result.newRulesSuggested,
                            confidence = result.confidence,
                            verificationUrl = result.verificationUrl,
                            completedAt = result.timestamp
                        )
                    }
                    return
                }
            } else if (status.status == "FAILED") {
                _state.update { ResearchState.Error("Research failed") }
                return
            }
        }

        _state.update { ResearchState.Error("Research timeout") }
    }

    /**
     * Submit user verification feedback
     */
    fun submitVerificationFeedback(
        userApproved: Boolean,
        corrections: List<String> = emptyList(),
        comments: String = ""
    ) {
        val sessionId = _currentSessionId.value ?: return

        viewModelScope.launch(Dispatchers.Default) {
            try {
                HybridForensicService.submitVerificationFeedback(
                    sessionId = sessionId,
                    userApproved = userApproved,
                    corrections = corrections,
                    comments = comments
                )

                // After submitting feedback, could return to results
                // or show confirmation
            } catch (e: Exception) {
                _state.update { ResearchState.Error("Failed to submit feedback: ${e.message}") }
            }
        }
    }

    /**
     * Get current research session status
     */
    fun getSessionStatus(sessionId: String): HybridForensicService.ResearchStatusResult {
        return HybridForensicService.getResearchStatus(sessionId)
    }

    /**
     * Get research result
     */
    fun getSessionResult(sessionId: String): HybridForensicService.ResearchResultData? {
        return HybridForensicService.getResearchResult(sessionId)
    }

    /**
     * Cancel current research
     */
    fun cancelResearch() {
        _state.update { ResearchState.Idle }
        _currentSessionId.update { null }
    }

    /**
     * Reset to idle state
     */
    fun reset() {
        _state.update { ResearchState.Idle }
        _currentSessionId.update { null }
    }

    /**
     * Export research report as sealed PDF
     */
    fun exportResearchReport(sessionId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // In production, this would generate and seal a PDF report
                // For now, just log
                println("Exporting research report for session: $sessionId")
            } catch (e: Exception) {
                _state.update { ResearchState.Error("Failed to export: ${e.message}") }
            }
        }
    }
}

/**
 * ViewModel for engine statistics and sync operations
 */
class EngineStatsViewModel : ViewModel() {

    data class SyncState(
        val isSyncing: Boolean = false,
        val lastSyncTime: Instant? = null,
        val lastSyncError: String? = null,
        val packetsSent: Int = 0
    )

    data class RuleUpdateState(
        val isChecking: Boolean = false,
        val newRulesAvailable: Int = 0,
        val lastCheckTime: Instant? = null,
        val lastCheckError: String? = null
    )

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _ruleUpdateState = MutableStateFlow(RuleUpdateState())
    val ruleUpdateState: StateFlow<RuleUpdateState> = _ruleUpdateState.asStateFlow()

    /**
     * Sync feedback to website
     */
    fun syncFeedbackToWebsite() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                _syncState.update { it.copy(isSyncing = true) }

                val result = HybridForensicService.syncFeedbackToWebsite()

                _syncState.update {
                    it.copy(
                        isSyncing = false,
                        lastSyncTime = result.timestamp,
                        packetsSent = result.packetsSent,
                        lastSyncError = null
                    )
                }
            } catch (e: Exception) {
                _syncState.update {
                    it.copy(
                        isSyncing = false,
                        lastSyncError = e.message
                    )
                }
            }
        }
    }

    /**
     * Check for rule updates
     */
    fun checkForRuleUpdates(jurisdiction: String = "SA") {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                _ruleUpdateState.update { it.copy(isChecking = true) }

                val result = HybridForensicService.checkAndApplyRuleUpdates(jurisdiction)

                val newRulesCount = when (result.status) {
                    "UP_TO_DATE" -> 0
                    "READY_FOR_DOWNLOAD" -> 1 // placeholder
                    else -> 0
                }

                _ruleUpdateState.update {
                    it.copy(
                        isChecking = false,
                        newRulesAvailable = newRulesCount,
                        lastCheckTime = result.timestamp,
                        lastCheckError = null
                    )
                }
            } catch (e: Exception) {
                _ruleUpdateState.update {
                    it.copy(
                        isChecking = false,
                        lastCheckError = e.message
                    )
                }
            }
        }
    }

    /**
     * Get engine statistics
     */
    fun getEngineStats(): HybridForensicService.EngineStatistics {
        return HybridForensicService.getEngineStats()
    }
}
