package com.verumomnis.forensic.identity

import kotlinx.serialization.Serializable

/**
 * Operator-provided identity. Kept deliberately minimal to avoid collecting
 * unnecessary PII. The forensic trust model is evidence-based; this identity
 * only binds a human operator to a sealed device key.
 */
@Serializable
data class UserIdentity(
    val displayName: String = "anonymous-operator",
    val role: String = "forensic-operator",
    val publicKeyFingerprint: String = ""
)
