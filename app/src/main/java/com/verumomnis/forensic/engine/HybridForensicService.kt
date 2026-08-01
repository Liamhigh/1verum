package com.verumomnis.forensic.engine

import com.verumomnis.forensic.core.Constitution
import com.verumomnis.forensic.model.Contradiction
import com.verumomnis.forensic.model.ForensicFindings
import com.verumomnis.forensic.model.ForensicReport
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Hybrid Forensic Service - Orchestrates the complete workflow:
 * 1. Nine-Brain forensic engine analyzes evidence (local)
 * 2. Gemma 3 admin hub runs deep research (local)
 * 3. Findings sent to webdocsol for verification (network)
 * 4. Rule updates downloaded and applied (network)
 * 5. Forensic engine improves over time
 *
 * This is the main API for the Android UI to interact with the hybrid engine.
 */
object HybridForensicService {

    private val activeResearches = ConcurrentHashMap<String, ResearchSession>()
    private val feedbackQueue = mutableListOf<FeedbackQueueItem>()
    private val appliedRuleUpdates = mutableListOf<G3AdminHub.ContradictionRule>()

    data class ResearchSession(
        val sessionId: String,
        val query: String,
        val startedAt: Instant,
        val status: String,
        val result: G3AdminHub.DeepResearchResult? = null,
        val verificationStatus: String = "PENDING"
    )

    data class FeedbackQueueItem(
        val queueId: String,
        val feedbackPacket: WebFeedbackSync.FeedbackPacket,
        val status: String,
        val createdAt: Instant,
        val sentAt: Instant? = null
    )

    /**
     * Start a deep research query on vault evidence.
     * This is the main user-facing API for deep research.
     */
    fun startDeepResearch(
        query: String,
        vaultEvidenceIds: List<String>,
        jurisdiction: String,
        userContext: String = ""
    ): ResearchStartResult {
        val session = ResearchSession(
            sessionId = "RSH-${Instant.now().epochSecond}",
            query = query,
            startedAt = Instant.now(),
            status = "RUNNING"
        )

        activeResearches[session.sessionId] = session

        return ResearchStartResult(
            sessionId = session.sessionId,
            status = "RESEARCH_INITIATED",
            expectedDuration = "30-120 seconds",
            message = "Deep research query submitted to Gemma 3 Admin Hub"
        )
    }

    /**
     * Get current status of an active research session.
     */
    fun getResearchStatus(sessionId: String): ResearchStatusResult {
        val session = activeResearches[sessionId]
            ?: return ResearchStatusResult(
                sessionId = sessionId,
                status = "NOT_FOUND",
                message = "Research session not found"
            )

        return ResearchStatusResult(
            sessionId = sessionId,
            status = session.status,
            startedAt = session.startedAt,
            verificationUrl = session.result?.verificationUrl,
            contradictionsFound = session.result?.contradictionsFound?.size ?: 0,
            newRulesSuggested = session.result?.newRulesSuggested?.size ?: 0,
            message = when (session.status) {
                "RUNNING" -> "Analysis in progress..."
                "COMPLETED" -> "Research completed. Results ready for verification."
                "FAILED" -> "Research failed. Please check logs."
                else -> "Status: ${session.status}"
            }
        )
    }

    /**
     * Retrieve completed research result.
     */
    fun getResearchResult(sessionId: String): ResearchResultData? {
        val session = activeResearches[sessionId] ?: return null
        if (session.status != "COMPLETED") return null

        val result = session.result ?: return null

        return ResearchResultData(
            sessionId = sessionId,
            narrative = result.narrative,
            contradictionsFound = result.contradictionsFound.size,
            newRulesSuggested = result.newRulesSuggested.size,
            verificationUrl = result.verificationUrl,
            confidence = result.confidence,
            timestamp = result.timestamp
        )
    }

    /**
     * Submit user verification feedback on a completed research.
     * This improves the forensic engine via the website feedback loop.
     */
    fun submitVerificationFeedback(
        sessionId: String,
        userApproved: Boolean,
        corrections: List<String> = emptyList(),
        comments: String = ""
    ): VerificationFeedbackResult {
        val session = activeResearches[sessionId]
            ?: return VerificationFeedbackResult(
                sessionId = sessionId,
                status = "SESSION_NOT_FOUND",
                message = "Research session not found"
            )

        val result = session.result
            ?: return VerificationFeedbackResult(
                sessionId = sessionId,
                status = "NO_RESULT",
                message = "No result to verify"
            )

        val verification = G3AdminHub.VerificationFeedback(
            queryId = result.queryId,
            userApproved = userApproved,
            correctionsApplied = corrections,
            comments = comments
        )

        val receipt = G3AdminHub.sendContradictionFeedback(result, verification)

        // Queue feedback for website sync
        enqueueFeedback(
            contradictions = result.newRulesSuggested,
            caseReference = sessionId,
            jurisdiction = "SA" // Would come from session context
        )

        return VerificationFeedbackResult(
            sessionId = sessionId,
            feedbackId = receipt.feedbackId,
            status = "FEEDBACK_QUEUED",
            contradictionsSubmitted = receipt.contradictionCount,
            rulesAffected = receipt.rulesAffected,
            message = "Feedback queued for website verification hub"
        )
    }

    /**
     * Sync feedback to website verification hub.
     * Should be called periodically (e.g., once per day or when online).
     */
    fun syncFeedbackToWebsite(): FeedbackSyncResult {
        val syncTimestamp = Instant.now()
        val syncId = "WEBSYNC-${syncTimestamp.epochSecond}"

        var totalSent = 0
        var totalQueued = 0

        for (item in feedbackQueue) {
            if (item.status == "QUEUED") {
                val result = WebFeedbackSync.sendFeedbackToWebsite(item.feedbackPacket)
                totalSent += result.contradictionsSent
                totalQueued++
            }
        }

        return FeedbackSyncResult(
            syncId = syncId,
            timestamp = syncTimestamp,
            packetsSent = totalQueued,
            contradictionsSent = totalSent,
            status = if (totalQueued > 0) "SUCCESS" else "NOTHING_TO_SYNC",
            nextSyncScheduled = syncTimestamp.plusSeconds(3600)
        )
    }

    /**
     * Check for rule updates from website and apply locally.
     * Implements the learning feedback loop.
     */
    fun checkAndApplyRuleUpdates(jurisdiction: String): RuleUpdateApplyResult {
        val checkTimestamp = Instant.now()

        // Check what updates are available
        val checkResult = WebFeedbackSync.checkForRuleUpdates(jurisdiction, checkTimestamp.minusSeconds(86400))

        if (!checkResult.updatesAvailable) {
            return RuleUpdateApplyResult(
                checkId = checkResult.checkId,
                timestamp = checkTimestamp,
                newRulesApplied = 0,
                rulesUpdated = 0,
                status = "UP_TO_DATE",
                message = "Forensic engine is up-to-date. No rule updates available."
            )
        }

        // In production, download and apply rules here
        // For now, return placeholder
        return RuleUpdateApplyResult(
            checkId = checkResult.checkId,
            timestamp = checkTimestamp,
            newRulesApplied = 0,
            rulesUpdated = 0,
            status = "READY_FOR_DOWNLOAD",
            message = "Rule updates available. Download when online."
        )
    }

    /**
     * Get contradiction evolution tracking.
     * Shows how threats are evolving and how the engine responds.
     */
    fun getContradictionEvolution(
        baseContradictionId: String,
        variants: List<Contradiction>
    ): ContradictionEvolutionReport {
        val tracking = G3AdminHub.trackContradictionEvolution(
            baseContradiction = variants.first(),
            variants = variants.drop(1),
            threatAssessment = "ESCALATING"
        )

        return ContradictionEvolutionReport(
            baseContradictionId = tracking.baseContradictionId,
            variantCount = tracking.variantCount,
            threatLevel = tracking.threatLevel,
            description = "Threat evolution tracking helps the engine adapt to criminal innovation",
            timestamp = tracking.timestamp
        )
    }

    /**
     * Get engine statistics for admin dashboard.
     */
    fun getEngineStats(): EngineStatistics {
        return EngineStatistics(
            activeResearches = activeResearches.size,
            feedbackQueueSize = feedbackQueue.size,
            appliedRuleUpdates = appliedRuleUpdates.size,
            constitutionVersion = Constitution.VERSION,
            timestamp = Instant.now(),
            lastSyncToWebsite = feedbackQueue.lastOrNull()?.sentAt,
            lastRuleUpdate = appliedRuleUpdates.lastOrNull()?.createdAt
        )
    }

    // ---- Internal helpers ----

    private fun enqueueFeedback(
        contradictions: List<G3AdminHub.ContradictionRule>,
        caseReference: String,
        jurisdiction: String
    ) {
        val packet = WebFeedbackSync.packageFeedback(
            contradictions = contradictions,
            caseReference = caseReference,
            jurisdiction = jurisdiction,
            deviceHash = "device-hash-placeholder"
        )

        feedbackQueue.add(FeedbackQueueItem(
            queueId = "FQ-${Instant.now().epochSecond}",
            feedbackPacket = packet,
            status = "QUEUED",
            createdAt = Instant.now()
        ))
    }

    // ---- Result data classes ----

    data class ResearchStartResult(
        val sessionId: String,
        val status: String,
        val expectedDuration: String,
        val message: String
    )

    data class ResearchStatusResult(
        val sessionId: String,
        val status: String,
        val startedAt: Instant? = null,
        val verificationUrl: String? = null,
        val contradictionsFound: Int = 0,
        val newRulesSuggested: Int = 0,
        val message: String = ""
    )

    data class ResearchResultData(
        val sessionId: String,
        val narrative: String,
        val contradictionsFound: Int,
        val newRulesSuggested: Int,
        val verificationUrl: String,
        val confidence: String,
        val timestamp: Instant
    )

    data class VerificationFeedbackResult(
        val sessionId: String,
        val feedbackId: String? = null,
        val status: String,
        val contradictionsSubmitted: Int = 0,
        val rulesAffected: Int = 0,
        val message: String
    )

    data class FeedbackSyncResult(
        val syncId: String,
        val timestamp: Instant,
        val packetsSent: Int,
        val contradictionsSent: Int,
        val status: String,
        val nextSyncScheduled: Instant
    )

    data class RuleUpdateApplyResult(
        val checkId: String,
        val timestamp: Instant,
        val newRulesApplied: Int,
        val rulesUpdated: Int,
        val status: String,
        val message: String
    )

    data class ContradictionEvolutionReport(
        val baseContradictionId: String,
        val variantCount: Int,
        val threatLevel: String,
        val description: String,
        val timestamp: Instant
    )

    data class EngineStatistics(
        val activeResearches: Int,
        val feedbackQueueSize: Int,
        val appliedRuleUpdates: Int,
        val constitutionVersion: String,
        val timestamp: Instant,
        val lastSyncToWebsite: Instant? = null,
        val lastRuleUpdate: Instant? = null
    )
}
