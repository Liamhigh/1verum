package com.verumomnis.forensic.model

import kotlinx.serialization.Serializable

/** Categories of constitutional violation detected by B9. */
enum class GuardianViolationType {
    ARTICLE_X_WEAPONIZATION,
    ANTI_PRIVATIZATION,
    COERCION_ATTEMPT,
    EICAR_TEST_PATTERN,
    CONSTITUTIONAL_SAFETY
}

/** A single B9 guardian violation. */
@Serializable
data class GuardianViolation(
    val type: GuardianViolationType,
    val severity: Severity,
    val description: String,
    val evidenceId: String = "",
    val trigger: String = "",
    val timestamp: String = ""
)

/** B9 assessment: never votes on contradictions, only validates and blocks weaponization. */
@Serializable
data class GuardianAssessment(
    val violations: List<GuardianViolation> = emptyList(),
    val notes: List<String> = emptyList(),
    val hardStopRequired: Boolean = false
) {
    fun isEmpty(): Boolean = violations.isEmpty() && notes.isEmpty() && !hardStopRequired
}

/** Single append-only entry in the Silence Ledger of coercion/weaponization attempts. */
@Serializable
data class SilenceLedgerEntry(
    val sequence: Long,
    val timestamp: String,
    val type: GuardianViolationType,
    val description: String,
    val evidenceId: String = "",
    val trigger: String = "",
    val previousHash: String = "",
    val entryHash: String = ""
)
