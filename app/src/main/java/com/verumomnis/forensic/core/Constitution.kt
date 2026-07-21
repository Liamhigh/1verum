package com.verumomnis.forensic.core

/**
 * Verum Omnis Constitution v6.0 — hard-coded, immutable governance constants.
 *
 * Per the specification these are COMPILE-TIME CONSTANTS: they are not stored in
 * config files or databases. Changing them requires recompiling from source, and
 * doing so invalidates every existing seal (hash mismatch). They therefore act as
 * the machine-readable ruleset embedded into every cryptographic seal.
 */
object Constitution {
    const val VERSION = "6.0"
    const val FINAL = true

    const val PROFIT_TO_FOUNDATION = 99      // 99% to Verum Foundation
    const val ETHICS_HALT_THRESHOLD = 0.003  // 0.3% bias = halt everything
    const val DEAD_MAN_SWITCH_HOURS = 72     // 72h inactivity = INTERPOL release
    const val BRAIN_COUNT = 9                // Exactly 9 brains
    const val GUARDIAN_COUNCIL_SIZE = 7      // 7 oversight members
    const val COMMISSION_PERCENT = 20        // 20% of recovered fraud
    const val CITIZEN_ACCESS_FREE = true     // Always free for citizens

    // AI Behaviour — Constitutional Prime Directives
    const val TRUTH_OVER_PROBABILITY = true          // Ordinal confidence only
    const val EVIDENCE_BEFORE_NARRATIVE = true       // No anchor = no sentence
    const val MANDATORY_CONTRADICTION_DISCLOSURE = true
    const val DETERMINISM_REQUIRED = true            // No randomness, no Date.now()
    const val CHAIN_OF_CUSTODY_IS_LAW = true
    const val FAILURE_MODE_DISCLOSURE = true
    const val ANTI_COERCION = true
    const val NON_OWNERSHIP = true
    const val ANTI_WAR_DOCTRINE = true               // Article X — supreme hierarchy

    // Sealing
    const val HASH_ALGORITHM = "SHA-512"
    const val PDF_STANDARD = "PDF/A-3B"
    const val ENCRYPTION = "AES-256-GCM"
    const val BLOCKCHAIN_NETWORK = "bitcoin"

    // Signed rule updates (verum-rules worker, rule-format.md v1).
    // The public key is public BY DESIGN (SubjectPublicKeyInfo DER, base64) —
    // it can only verify signatures, never create them. publicKeyId: vo-master-1.
    const val RULE_MANIFEST_URL = "https://verumglobal.foundation/api/v1/rules/manifest"
    const val RULES_PUBLIC_KEY_DER_B64 = "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA9FQPTWCsFh1qMs/mrOOgZvdjCh8APmlsJlallCm3CmWgMoFAyRHRAauvXWFoBoiaUQGGx7OGtZ6eBCpBlOGLxSnVk0T2hBgd6kxZwj1vHEITw9KmXMjy5qmUY1hd3BO3y4aAfrPKu+6ENSJo7Ax77fvBnHPG1oL8m3724oqU913HYI7Miob+CdL0Oi36oCBKhlw5sCYH+evMPU1PmOqTrmz8zUkDk4osqX8INTIchmk2j3BguMw8sjmKRnrB//t6LPYme4motggMPVMNR3hLJHX+ehCYDUtJLshZq1MPLjTT7aK36gCIPg2ja6BxWfYdx7ZzSFVcL+gapy4pA7VnDrhQ7jb10ojGnofssEbQEi7k9FpswMFegmGNmKEH5TQcKlI4VJvQcZddbhZXYwpfgsL/raEFMChEuzR3A49oIXgBBmi9AdQtdEHpfb2i9/PimxsilhDxa8Pi+8cEQUMbHcPeodfX/IWf+wotnc3VKGoffVL/8+hSU/voPhxfXyOcnbRYkFGeOZhcrE/u4Nh6Vkq6y1+cpVUtrIzOnaeNbNF248ZS7f65IZci8MTeo4nAqkWGmXcZHrZLT7YIvHSyAryYzBNoofm2uTuiTxp8Oiwa2yfU2UMQfg0eGZa0LBHCLbG72pxiVd2TGvdHh3QguO1/zM5NNRtoUnqHfuLBOJECAwEAAQ=="

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
        append("citizenFree=$CITIZEN_ACCESS_FREE|")
        append("truthOverProb=$TRUTH_OVER_PROBABILITY|evidenceFirst=$EVIDENCE_BEFORE_NARRATIVE|")
        append("contradictionDisclosure=$MANDATORY_CONTRADICTION_DISCLOSURE|determinism=$DETERMINISM_REQUIRED|")
        append("chainOfCustody=$CHAIN_OF_CUSTODY_IS_LAW|antiCoercion=$ANTI_COERCION|antiWar=$ANTI_WAR_DOCTRINE|")
        append("hash=$HASH_ALGORITHM|pdf=$PDF_STANDARD|enc=$ENCRYPTION|chain=$BLOCKCHAIN_NETWORK")
    }
}
