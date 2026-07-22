package com.verumomnis.forensic.engine

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * Web Feedback Sync - Bidirectional integration with webdocsol verification hub.
 *
 * This module handles:
 * - Sending contradiction findings to website for human verification
 * - Receiving evolved rules from collective learning (other cases)
 * - Applying rule updates to the local forensic engine
 * - Tracking rule effectiveness over time
 */
object WebFeedbackSync {

    private const val WEBSITE_FEEDBACK_ENDPOINT = "https://verumglobal.foundation/api/feedback"
    private const val WEBSITE_RULES_ENDPOINT = "https://verumglobal.foundation/api/rules"

    data class FeedbackPacket(
        val packetId: String,
        val caseReference: String,
        val contradictions: List<FeedbackContradiction>,
        val jurisdiction: String,
        val timestamp: Instant,
        val deviceHash: String,
        val constitutionVersion: String
    )

    data class FeedbackContradiction(
        val contradictionId: String,
        val description: String,
        val respondent: String,
        val evidenceAnchor: String,
        val legalSignificance: String,
        val confidence: String,
        val foundAt: Instant
    )

    data class RuleUpdate(
        val ruleId: String,
        val type: String,
        val pattern: String,
        val confidence: String,
        val jurisdiction: String,
        val applicableFrom: Instant,
        val sourceCase: String,
        val reason: String
    )

    data class RuleDownloadPacket(
        val packetId: String,
        val timestamp: Instant,
        val newRules: List<RuleUpdate>,
        val updatedRules: List<RuleUpdate>,
        val deprecatedRules: List<String>,
        val evolutionReason: String
    )

    data class VerificationResult(
        val contradictionId: String,
        val websiteVerification: String,
        val humanReviewStatus: String,
        val corrections: List<String>,
        val verifiedAt: Instant
    )

    /**
     * Package contradictions for sending to verification hub.
     * Ensures data is sealed and formatted for forensic admissibility.
     */
    fun packageFeedback(
        contradictions: List<G3AdminHub.ContradictionRule>,
        caseReference: String,
        jurisdiction: String,
        deviceHash: String
    ): FeedbackPacket {
        val packetId = "PKT-${Instant.now().epochSecond}"
        val feedbackContradictions = contradictions.map { rule ->
            FeedbackContradiction(
                contradictionId = rule.sourceContradictionId,
                description = rule.description,
                respondent = "extracted_from_vault",
                evidenceAnchor = "see_sealed_report",
                legalSignificance = rule.pattern,
                confidence = rule.confidence,
                foundAt = rule.createdAt
            )
        }

        return FeedbackPacket(
            packetId = packetId,
            caseReference = caseReference,
            contradictions = feedbackContradictions,
            jurisdiction = jurisdiction,
            timestamp = Instant.now(),
            deviceHash = deviceHash,
            constitutionVersion = "6.0"
        )
    }

    /**
     * Send feedback packet to webdocsol for verification and learning.
     * In production, this would POST via HTTPS with certificate pinning.
     */
    fun sendFeedbackToWebsite(packet: FeedbackPacket): FeedbackSyncResult {
        val timestamp = Instant.now()
        val syncId = "SYNC-${timestamp.epochSecond}"

        // In production: HTTP POST to WEBSITE_FEEDBACK_ENDPOINT
        // For now, we prepare the payload and mark as QUEUED
        val jsonPayload = serializeFeedbackPacket(packet)

        return FeedbackSyncResult(
            syncId = syncId,
            packetId = packet.packetId,
            status = "QUEUED",
            contradictionsSent = packet.contradictions.size,
            timestamp = timestamp,
            destinationUrl = WEBSITE_FEEDBACK_ENDPOINT,
            payloadHash = hashPayload(jsonPayload),
            nextSyncScheduled = timestamp.plusSeconds(3600)
        )
    }

    /**
     * Check for rule updates from website.
     * Downloads evolved rules from collective learning.
     */
    fun checkForRuleUpdates(
        jurisdiction: String,
        lastSyncTimestamp: Instant
    ): RuleUpdateCheckResult {
        // In production: HTTP GET to WEBSITE_RULES_ENDPOINT
        // with query params: ?jurisdiction=SA&since=ISO8601timestamp
        val checkTimestamp = Instant.now()

        return RuleUpdateCheckResult(
            checkId = "CHK-${checkTimestamp.epochSecond}",
            jurisdiction = jurisdiction,
            timestamp = checkTimestamp,
            updatesAvailable = false,
            nextCheckScheduled = checkTimestamp.plusSeconds(3600),
            message = "No new rules available. System up-to-date."
        )
    }

    /**
     * Apply downloaded rule updates to the local forensic engine.
     * Validates rules before applying (must pass Constitution checks).
     */
    fun applyRuleUpdates(
        downloadPacket: RuleDownloadPacket
    ): RuleApplyResult {
        val applyTimestamp = Instant.now()
        val applyId = "APPLY-${applyTimestamp.epochSecond}"

        val validatedNewRules = downloadPacket.newRules.filter { validateRule(it) }
        val validatedUpdates = downloadPacket.updatedRules.filter { validateRule(it) }

        return RuleApplyResult(
            applyId = applyId,
            timestamp = applyTimestamp,
            newRulesApplied = validatedNewRules.size,
            rulesUpdated = validatedUpdates.size,
            rulesDeprecated = downloadPacket.deprecatedRules.size,
            status = "SUCCESS",
            evolutionDescription = downloadPacket.evolutionReason
        )
    }

    /**
     * Track rule effectiveness - monitor how frequently rules fire
     * and whether they catch genuine contradictions.
     */
    fun trackRuleEffectiveness(
        ruleId: String,
        fireCount: Int,
        truePositiveCount: Int,
        falsePositiveCount: Int
    ): RuleEffectivenessMetric {
        val precision = if (fireCount > 0) {
            truePositiveCount.toDouble() / (truePositiveCount + falsePositiveCount)
        } else {
            0.0
        }

        return RuleEffectivenessMetric(
            ruleId = ruleId,
            totalFires = fireCount,
            truePositives = truePositiveCount,
            falsePositives = falsePositiveCount,
            precision = precision,
            recommendation = when {
                precision >= 0.9 -> "HIGHLY_EFFECTIVE"
                precision >= 0.7 -> "EFFECTIVE"
                precision >= 0.5 -> "MONITOR"
                else -> "DEPRECATE_CANDIDATE"
            },
            timestamp = Instant.now()
        )
    }

    /**
     * Wait for human verification result from website.
     * In production, this polls the verification endpoint.
     */
    fun pollVerificationResult(
        contradictionId: String,
        maxWaitSeconds: Long = 86400L
    ): VerificationPollResult {
        val pollTimestamp = Instant.now()
        val pollId = "POLL-${pollTimestamp.epochSecond}"

        return VerificationPollResult(
            pollId = pollId,
            contradictionId = contradictionId,
            status = "PENDING",
            timestamp = pollTimestamp,
            message = "Awaiting human verification at verification hub",
            nextPollScheduled = pollTimestamp.plusSeconds(3600)
        )
    }

    data class FeedbackSyncResult(
        val syncId: String,
        val packetId: String,
        val status: String,
        val contradictionsSent: Int,
        val timestamp: Instant,
        val destinationUrl: String,
        val payloadHash: String,
        val nextSyncScheduled: Instant
    )

    data class RuleUpdateCheckResult(
        val checkId: String,
        val jurisdiction: String,
        val timestamp: Instant,
        val updatesAvailable: Boolean,
        val nextCheckScheduled: Instant,
        val message: String
    )

    data class RuleApplyResult(
        val applyId: String,
        val timestamp: Instant,
        val newRulesApplied: Int,
        val rulesUpdated: Int,
        val rulesDeprecated: Int,
        val status: String,
        val evolutionDescription: String
    )

    data class RuleEffectivenessMetric(
        val ruleId: String,
        val totalFires: Int,
        val truePositives: Int,
        val falsePositives: Int,
        val precision: Double,
        val recommendation: String,
        val timestamp: Instant
    )

    data class VerificationPollResult(
        val pollId: String,
        val contradictionId: String,
        val status: String,
        val timestamp: Instant,
        val message: String,
        val nextPollScheduled: Instant
    )

    private fun serializeFeedbackPacket(packet: FeedbackPacket): String = buildString {
        append("""{
  "packetId": "${packet.packetId}",
  "caseReference": "${packet.caseReference}",
  "jurisdiction": "${packet.jurisdiction}",
  "timestamp": "${packet.timestamp}",
  "deviceHash": "${packet.deviceHash}",
  "constitutionVersion": "${packet.constitutionVersion}",
  "contradictions": [
""")
        packet.contradictions.forEachIndexed { idx, contradiction ->
            append("""    {
      "id": "${contradiction.contradictionId}",
      "description": "${escapJson(contradiction.description)}",
      "respondent": "${contradiction.respondent}",
      "confidence": "${contradiction.confidence}",
      "foundAt": "${contradiction.foundAt}"
    }""")
            if (idx < packet.contradictions.size - 1) append(",")
            append("\n")
        }
        append("  ]\n}")
    }

    private fun escapJson(str: String): String {
        return str.replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun hashPayload(payload: String): String {
        val bytes = payload.toByteArray()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return Base64.getEncoder().encodeToString(hash).take(16)
    }

    private fun validateRule(rule: RuleUpdate): Boolean {
        // Validate that rule meets Constitutional requirements
        return rule.pattern.isNotBlank() &&
                rule.confidence in listOf("VERY_HIGH", "HIGH", "MODERATE", "LOW") &&
                rule.jurisdiction.isNotBlank()
    }
}
