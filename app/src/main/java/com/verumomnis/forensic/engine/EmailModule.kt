package com.verumomnis.forensic.engine

import com.verumomnis.forensic.crypto.EvidenceSealer
import com.verumomnis.forensic.model.EmailDraft
import com.verumomnis.forensic.model.EmailLogEntry
import com.verumomnis.forensic.model.HarassmentVerdict
import com.verumomnis.forensic.model.SealedEmail
import java.time.Instant

/**
 * Email & Distribution (Part X). The AI drafts emails; every draft is delivered
 * as a SEALED PDF (SHA-512 + Constitution). Before sending, the draft passes the
 * anti-harassment monitor and a content check. Blocked emails are still sealed
 * for the audit trail but not delivered.
 */
object EmailModule {

    private val THREATENING = Regex(
        """\b(kill|threat|threaten|harm|destroy you|or else|regret|revenge|expose you|ruin you)\b""",
        RegexOption.IGNORE_CASE
    )

    /** Deterministic AI-style draft grounded in the report reference. */
    fun draft(recipient: String, subject: String, points: List<String>, reportReference: String?): EmailDraft {
        val body = buildString {
            appendLine("Dear ${recipient.substringBefore("@")},")
            appendLine()
            appendLine("Please find enclosed the sealed forensic report" + (reportReference?.let { " ($it)" } ?: "") + ".")
            if (points.isNotEmpty()) {
                appendLine()
                points.forEach { appendLine("• $it") }
            }
            appendLine()
            appendLine("This communication and its attachment are cryptographically sealed and form part of a")
            appendLine("permanent, court-admissible distribution record.")
            appendLine()
            appendLine("Regards,")
            appendLine("Verum Omnis — AI Forensics for Truth")
        }
        return EmailDraft(recipient = recipient, subject = subject, body = body, reportReference = reportReference)
    }

    fun contentReasons(draft: EmailDraft): List<String> {
        val reasons = mutableListOf<String>()
        if (THREATENING.containsMatchIn(draft.body) || THREATENING.containsMatchIn(draft.subject)) {
            reasons += "Draft contains potentially threatening or harassing language."
        }
        return reasons
    }

    /** Seal the draft as a PDF and, subject to monitoring, deliver it. */
    fun sealAndSend(
        draft: EmailDraft,
        monitor: AntiHarassmentMonitor,
        now: Instant = Instant.now()
    ): SealedEmail {
        val nowMillis = now.toEpochMilli()
        val assessment = monitor.assess(
            recipient = draft.recipient,
            reportReference = draft.reportReference,
            contentReasons = contentReasons(draft),
            nowMillis = nowMillis
        )

        val sealBytes = (draft.subject + "\n" + draft.body).toByteArray(Charsets.UTF_8)
        val seal = EvidenceSealer.seal(
            bytes = sealBytes,
            documentType = "sealed_email",
            documentReference = draft.reportReference ?: "VO-EMAIL",
            nowInstant = now
        )
        val pdfFile = "sealed-email-${seal.shortcode}-$nowMillis.pdf"
        val delivered = assessment.verdict != HarassmentVerdict.BLOCK

        monitor.record(
            EmailLogEntry(
                recipient = draft.recipient,
                subject = draft.subject,
                reportReference = draft.reportReference,
                sealId = seal.sealId,
                verdict = assessment.verdict,
                timestampMillis = nowMillis
            )
        )

        return SealedEmail(
            draft = draft,
            sealedPdfFile = pdfFile,
            seal = seal,
            assessment = assessment,
            sentAt = now.toString(),
            delivered = delivered
        )
    }
}
