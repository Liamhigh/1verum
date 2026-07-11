package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.EmailLogEntry
import com.verumomnis.forensic.model.HarassmentAssessment
import com.verumomnis.forensic.model.HarassmentVerdict

/**
 * Anti-Harassment Monitoring (spec 10.2). Stateful: keeps a distribution audit
 * trail and evaluates each prospective send against the frequency, bulk, repeat,
 * and cooldown rules. Its purpose is to protect the sender with a defensible
 * record — it documents that communication was measured, not to silence it.
 */
class AntiHarassmentMonitor {

    private val log = mutableListOf<EmailLogEntry>()
    private val noContact = mutableSetOf<String>()

    companion object {
        const val WINDOW_24H_MILLIS = 24L * 60 * 60 * 1000
        const val FREQUENCY_LIMIT = 3      // >3 to same recipient in 24h → warn
        const val BULK_LIMIT = 10          // same report to >10 recipients → flag
        const val REPEAT_LIMIT = 5         // same report to same recipient >5 → block
    }

    fun markNoContact(recipient: String) { noContact += recipient.lowercase() }

    fun auditTrail(): List<EmailLogEntry> = log.toList()

    fun assess(
        recipient: String,
        reportReference: String?,
        contentReasons: List<String>,
        nowMillis: Long
    ): HarassmentAssessment {
        val r = recipient.lowercase()
        val within24h = log.count { it.recipient.lowercase() == r && nowMillis - it.timestampMillis <= WINDOW_24H_MILLIS }
        val sameReportSameRecipient = log.count {
            it.recipient.lowercase() == r && reportReference != null && it.reportReference == reportReference
        }
        val recipientsForReport = (log.filter { reportReference != null && it.reportReference == reportReference }
            .map { it.recipient.lowercase() }.toSet() + r).size

        val reasons = mutableListOf<String>()
        reasons += contentReasons
        var verdict = HarassmentVerdict.ALLOW

        if (r in noContact) {
            verdict = HarassmentVerdict.BLOCK
            reasons += "Recipient has requested no further contact (cooldown)."
        }
        if (sameReportSameRecipient >= REPEAT_LIMIT) {
            verdict = HarassmentVerdict.BLOCK
            reasons += "Same report already sent to this recipient $sameReportSameRecipient times."
        }
        if (contentReasons.isNotEmpty()) {
            verdict = HarassmentVerdict.BLOCK
        }
        if (verdict != HarassmentVerdict.BLOCK) {
            if (within24h >= FREQUENCY_LIMIT) {
                verdict = HarassmentVerdict.WARN
                reasons += "More than $FREQUENCY_LIMIT emails to this recipient in 24 hours."
            }
            if (recipientsForReport > BULK_LIMIT) {
                verdict = HarassmentVerdict.WARN
                reasons += "This report has now been sent to $recipientsForReport recipients (bulk)."
            }
        }

        return HarassmentAssessment(
            verdict = verdict,
            reasons = reasons,
            recipientSendCount24h = within24h,
            totalRecipients = recipientsForReport
        )
    }

    fun record(entry: EmailLogEntry) { log += entry }
}
