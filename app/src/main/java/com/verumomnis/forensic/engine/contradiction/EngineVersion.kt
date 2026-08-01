package com.verumomnis.forensic.engine.contradiction

/**
 * The single source of truth for the contradiction engine's ruleset version.
 *
 * This string is bonded to the seal: every emitted pattern, findings-JSON
 * document and calibration report states which ruleset produced it, so a sealed
 * report can always be traced back to the exact detector set that ran. When the
 * version is duplicated as literals, the copies drift — and a report then claims
 * a ruleset that never produced it, which is a seal/ruleset bond breach under
 * Constitution v6.0.
 *
 * It had already drifted: `LogicalPattern.detectorVersion` still defaulted to
 * "v5.2.9" long after the v5.3.1c port, while the call site passed "v5.3.1c"
 * explicitly, and two further copies existed without the "v" prefix. Everything
 * now derives from [VALUE]; nothing should hard-code the version again.
 */
object EngineVersion {

    /** Bare version, e.g. `5.3.1c` — used where a parseable version is wanted. */
    const val VALUE: String = "5.3.1c"

    /** Display/stamp form, e.g. `v5.3.1c` — used in patterns and audit reports. */
    const val TAGGED: String = "v$VALUE"
}
