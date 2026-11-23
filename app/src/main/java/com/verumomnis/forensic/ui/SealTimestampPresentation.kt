package com.verumomnis.forensic.ui

import com.verumomnis.forensic.model.OtsStatus
import com.verumomnis.forensic.model.OtsVerifyResult

internal enum class SealTimestampState { NOT_SUBMITTED, PENDING, CONFIRMED, FAILED }

internal data class SealTimestampPresentation(
    val state: SealTimestampState,
    val title: String,
    val description: String,
    val canDownloadProof: Boolean
)

/** Uses only the current document's records, never the active case/report's anchor. */
internal fun sealTimestampPresentation(
    submission: OtsStatus?,
    verification: OtsVerifyResult?,
    hasProofBytes: Boolean
): SealTimestampPresentation {
    val state = when {
        verification?.status == OtsStatus.FAILED -> SealTimestampState.FAILED
        !hasProofBytes && (submission == OtsStatus.PENDING || submission == OtsStatus.CONFIRMED ||
            verification?.bitcoinAttested == true) -> SealTimestampState.FAILED
        hasProofBytes && verification?.status == OtsStatus.CONFIRMED &&
            verification.bitcoinAttested -> SealTimestampState.CONFIRMED
        submission == OtsStatus.FAILED -> SealTimestampState.FAILED
        hasProofBytes && (submission == OtsStatus.PENDING || submission == OtsStatus.CONFIRMED ||
            verification?.status == OtsStatus.PENDING) -> SealTimestampState.PENDING
        else -> SealTimestampState.NOT_SUBMITTED
    }
    val (title, detail) = when (state) {
        SealTimestampState.NOT_SUBMITTED -> {
            if (hasProofBytes) {
                "Timestamp submission not confirmed" to
                    "Proof bytes are saved, but no successful submission or Bitcoin confirmation is recorded for this document."
            } else {
                "Timestamp offline / not submitted" to
                    "No timestamp proof was saved for this document. Its hash has not established an independent timestamp."
            }
        }
        SealTimestampState.PENDING ->
            "Timestamp proof pending" to
                ("A proof is saved, but Bitcoin confirmation has not been recorded for this document. " +
                    "Confirmation timing varies; no completion time is guaranteed." +
                    if (verification?.status == OtsStatus.OFFLINE) " The latest proof check was unavailable." else "")
        SealTimestampState.CONFIRMED ->
            "Bitcoin confirmation recorded" to
                "The recorded proof check reports a Bitcoin attestation. Independent validation of the proof and its document binding is still required."
        SealTimestampState.FAILED ->
            "Timestamp proof failed" to
                if (!hasProofBytes) "Timestamp submission did not produce a usable proof. This document has no saved timestamp proof."
                else "The recorded submission or proof check failed. Saved proof bytes remain available for inspection, but confirmation must not be assumed."
    }
    return SealTimestampPresentation(
        state, title,
        "$detail A hash alone is not independent proof of time or legal admissibility.",
        hasProofBytes
    )
}
