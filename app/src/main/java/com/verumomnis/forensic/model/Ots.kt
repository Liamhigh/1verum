package com.verumomnis.forensic.model

import kotlinx.serialization.Serializable

enum class OtsStatus { PENDING, CONFIRMED, OFFLINE, FAILED }

/**
 * Result of an OpenTimestamps anchoring attempt (SHA-512 evidence → SHA-256 OTS
 * digest → Bitcoin calendar). [otsProofBase64] is a standards-compliant `.ots`
 * detached proof (Base64) that can be verified/upgraded with the OpenTimestamps
 * tooling once the Bitcoin attestation confirms.
 */
@Serializable
data class OtsAnchorResult(
    val status: OtsStatus,
    val sha512: String,
    val sha256Digest: String,
    val calendarUrls: List<String>,
    val otsProofBase64: String? = null,
    val otsProofFile: String = "",
    val submittedAt: String,
    val message: String = ""
)

/** Result of verifying/inspecting an OTS proof. */
@Serializable
data class OtsVerifyResult(
    val status: OtsStatus,
    val pending: Boolean,
    val bitcoinAttested: Boolean,
    val bitcoinTipHeight: Long? = null,
    val message: String = ""
)
