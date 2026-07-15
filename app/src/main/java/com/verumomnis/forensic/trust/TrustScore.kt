package com.verumomnis.forensic.trust

import com.verumomnis.forensic.model.Confidence
import kotlinx.serialization.Serializable

/**
 * Overall trust assessment for a sealed forensic case.
 *
 * [score] is a 0.0–1.0 numeric aggregate used only for UI rendering; the
 * authoritative expression is the ordinal [confidence] (spec 8.1).
 */
@Serializable
data class TrustScore(
    val score: Double,
    val confidence: Confidence,
    val factors: List<TrustFactor>
) {
    fun summary(): String =
        "Trust ${"%.0f".format(score * 100)}% · $confidence · ${factors.size} factor(s)"
}
