package com.verumomnis.forensic.engine.contradiction

/** AllFuels Energy (Pty) Ltd — Petroleum franchise fraud. */
fun allfuelsConfig() = CaseConfig(
    name = "AllFuels Energy (Pty) Ltd",
    liabilityAdmit = listOf("admit", "confess", "yes it was", "i did", "my fault"),
    liabilityDeny = listOf(
        "deny", "not true", "false", "never happened", "didn't",
        "i reject", "no goodwill", "never existed", "cancelled"
    ),
    liabilityConceal = listOf(
        "hidden", "withheld", "didn't tell", "omitted", "bcc",
        "blind copy", "never told"
    ),
    topicKeywords = listOf(
        "goodwill", "franchise", "petroleum", "section 12B",
        "eviction", "rent", "clause 7", "MOU", "AllFuels"
    ),
    entityKeywords = listOf("AllFuels", "Palmbili", "Zeyd Timol", "Petroleum Products Act"),
    legalSubjects = mapOf(
        "Goodwill Value" to listOf("goodwill", "compensable", "entrenched value", "brand fee"),
        "Contract Validity" to listOf("contract", "agreement", "binding", "countersign"),
        "Signature Status" to listOf("signature", "signed", "blank", "unsigned"),
        "Section 12B" to listOf("section 12B", "arbitration", "referral", "Business Zone"),
        "Compensation" to listOf("fee", "payment", "rent", "compensation", "deposit"),
        "Perjury" to listOf("perjury", "Constitutional Court", "sworn", "CCT")
    )
)

/** Greensky/GreenSky — RAKEZ shareholder oppression (UAE). */
fun greenskyConfig() = CaseConfig(
    name = "Greensky (RAKEZ Case #1295911)",
    liabilityAdmit = listOf(
        "admit", "confess", "yes it was", "i did", "my fault",
        "proceeded", "went ahead", "executed"
    ),
    liabilityDeny = listOf(
        "deny", "not true", "false", "never happened", "didn't",
        "i reject", "no exclusivity", "never existed", "cancelled", "fell through"
    ),
    liabilityConceal = listOf(
        "hidden", "withheld", "didn't tell", "omitted", "bcc",
        "copied you in", "blind copy", "never told"
    ),
    topicKeywords = listOf(
        "deal", "order", "invoice", "shipment", "payment", "profit",
        "goodwill", "agreement", "exclusivity", "meeting", "access",
        "email", "camera", "theft", "fraud"
    ),
    entityKeywords = listOf(
        "RAKEZ", "Greensky", "Article 84", "Article 110",
        "Marius", "Kevin", "Liam", "30%", "exclusivity"
    ),
    legalSubjects = mapOf(
        "Shareholder Oppression" to listOf(
            "meeting", "excluded", "private meeting",
            "shareholder", "denied", "no vote", "kept out"
        ),
        "Breach of Fiduciary Duty" to listOf(
            "duty", "loyalty", "good faith", "fiduciary", "trust", "best interest"
        ),
        "Fraudulent Evidence" to listOf(
            "screenshot", "whatsapp", "fake", "doctored", "fabricated", "cropped", "missing context"
        ),
        "Cybercrime" to listOf(
            "gmail", "access", "hack", "unauthorized", "archive", "device", "google account"
        ),
        "Emotional Exploitation" to listOf(
            "mental", "emotional", "gaslight", "vulnerable", "trauma", "broken", "manipulate"
        ),
        "Concealment" to listOf(
            "withheld", "hid", "didn't tell", "secret", "copied", "bcc", "blind copied"
        )
    )
)

/** Get configuration by case name — defaults to AllFuels. */
fun getCaseConfig(caseName: String): CaseConfig {
    val normalized = caseName.lowercase().trim()
    return if ("greensky" in normalized || "green sky" in normalized || "rakez" in normalized)
        greenskyConfig()
    else
        allfuelsConfig()
}
