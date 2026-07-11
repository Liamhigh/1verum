package com.verumomnis.forensic.model

import kotlinx.serialization.Serializable

enum class HarassmentVerdict { ALLOW, WARN, BLOCK }

/** Result of the anti-harassment assessment (spec 10.2). */
@Serializable
data class HarassmentAssessment(
    val verdict: HarassmentVerdict,
    val reasons: List<String>,
    val recipientSendCount24h: Int,
    val totalRecipients: Int
)

@Serializable
data class EmailDraft(
    val recipient: String,
    val subject: String,
    val body: String,
    val reportReference: String? = null
)

/** An email whose body/attachment has been sealed as a PDF (spec 10.1). */
@Serializable
data class SealedEmail(
    val draft: EmailDraft,
    val sealedPdfFile: String,
    val seal: SealRecord,
    val assessment: HarassmentAssessment,
    val sentAt: String,
    val delivered: Boolean
)

@Serializable
data class EmailLogEntry(
    val recipient: String,
    val subject: String,
    val reportReference: String?,
    val sealId: String,
    val verdict: HarassmentVerdict,
    val timestampMillis: Long
)
