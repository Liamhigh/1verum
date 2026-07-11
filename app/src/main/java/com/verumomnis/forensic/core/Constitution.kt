package com.verumomnis.forensic.core

/**
 * Verum Omnis Constitution v5.2.7 — hard-coded, immutable governance constants.
 *
 * Per the specification these are COMPILE-TIME CONSTANTS: they are not stored in
 * config files or databases. Changing them requires recompiling from source, and
 * doing so invalidates every existing seal (hash mismatch). They therefore act as
 * the machine-readable ruleset embedded into every cryptographic seal.
 */
object Constitution {
    const val VERSION = "5.2.7"
    const val FINAL = true

    const val PROFIT_TO_FOUNDATION = 99      // 99% to Verum Foundation
    const val ETHICS_HALT_THRESHOLD = 0.003  // 0.3% bias = halt everything
    const val DEAD_MAN_SWITCH_HOURS = 72     // 72h inactivity = INTERPOL release
    const val BRAIN_COUNT = 9                // Exactly 9 brains
    const val GUARDIAN_COUNCIL_SIZE = 7      // 7 oversight members
    const val COMMISSION_PERCENT = 20        // 20% of recovered fraud
    const val CITIZEN_ACCESS_FREE = true     // Always free for citizens

    const val NINE_BRAIN_VERSION = "v1.0"
    const val SEALING_PROTOCOL = "verum-omnis-seal v1.0"
    const val TAGLINE = "AI Forensics for Truth"

    /** Machine-readable ruleset embedded into every seal. */
    fun rulesetFingerprint(): String = buildString {
        append("VO-CONSTITUTION|")
        append("v=$VERSION|final=$FINAL|")
        append("profit=$PROFIT_TO_FOUNDATION|ethicsHalt=$ETHICS_HALT_THRESHOLD|")
        append("deadman=$DEAD_MAN_SWITCH_HOURS|brains=$BRAIN_COUNT|")
        append("council=$GUARDIAN_COUNCIL_SIZE|commission=$COMMISSION_PERCENT|")
        append("citizenFree=$CITIZEN_ACCESS_FREE")
    }
}
