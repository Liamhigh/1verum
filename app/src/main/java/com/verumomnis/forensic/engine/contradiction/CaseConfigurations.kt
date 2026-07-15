package com.verumomnis.forensic.engine.contradiction

// Engine: v5.3.1c | Seal: VO-CE-v531c-DIGSIM-20260713
// 9 cases: AllFuels, AllFuels-111, Greensky, Southbridge, Louw v Moolla,
//          Liebenberg v Standard Bank, Standard Bank, Louw v Olivier, Mostert v Digsim

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

/** AllFuels 111-contradiction bundle — 7 constitutional categories (B1_ENGINE.md). */
fun allfuels111Config() = CaseConfig(
    name = "AllFuels 111-Contradiction Bundle",
    liabilityAdmit = listOf("admit", "confess", "yes it was", "i did", "my fault", "we collected", "acknowledged"),
    liabilityDeny = listOf(
        "deny", "not true", "false", "never happened", "didn't",
        "i reject", "no goodwill", "never existed", "cancelled", "not binding"
    ),
    liabilityConceal = listOf(
        "hidden", "withheld", "didn't tell", "omitted", "bcc",
        "blind copy", "never told", "string him along"
    ),
    topicKeywords = listOf(
        "goodwill", "franchise", "petroleum", "section 12B", "CCT237/20",
        "eviction", "rent", "clause 7", "MOU", "AllFuels", " extension fee",
        "Business Zone", "license fee", "operator", "site"
    ),
    entityKeywords = listOf(
        "AllFuels", "Palmbili", "Zeyd Timol", "Petroleum Products Act",
        "Gary Highcock", "BDO", "SARS", "CCT237/20"
    ),
    legalSubjects = mapOf(
        "Goodwill Value" to listOf("goodwill", "compensable", "entrenched value", "brand fee", "quantifiable asset", "R4.2M", "BDO"),
        "Contract Validity" to listOf("contract", "agreement", "binding", "countersign", "MOU Clause 1", "tenant at will", "lawful occupation"),
        "Signature Status" to listOf("signature", "signed", "blank", "unsigned", "countersignature", "fully executed"),
        "Section 12B" to listOf("section 12B", "arbitration", "referral", "Business Zone", "mandatory arbitration"),
        "Compensation" to listOf("fee", "payment", "rent", "compensation", "deposit", "R3.8M", "extension fee", "buy-out"),
        "Perjury" to listOf("perjury", "Constitutional Court", "sworn", "CCT", "CCT237/20", "acted in good faith"),
        "Coercion" to listOf("coerce", "threat", "intimidate", "suppress", "silence", "pressured", "duress", "grateful", "sign today")
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

/** Southbridge — Cross-border systematic fraud (VO-HR-2025/11). */
fun southbridgeConfig() = CaseConfig(
    name = "Southbridge (VO-HR-2025/11)",
    liabilityAdmit = listOf("admit", "confess", "yes it was", "i did", "proceeded", "executed", "agreed", "accepted"),
    liabilityDeny = listOf("deny", "not true", "false", "never happened", "didn't", "i reject", "no such agreement", "never existed"),
    liabilityConceal = listOf("hidden", "withheld", "didn't tell", "omitted", "bcc", "blind copy", "never disclosed"),
    topicKeywords = listOf("Southbridge", "cross-border", "systematic", "fraud", "remedy", "denied", "institutional", "mandate", "abandoned", "silence", "bounce", "submission"),
    entityKeywords = listOf("Southbridge", "VO-HR-2025/11", "cross-border", "institutional silence", "mandate abandonment"),
    legalSubjects = mapOf(
        "Cross-Border Fraud" to listOf("cross-border", "jurisdiction", "international", "treaty", "extradition", "mutual legal assistance"),
        "Mandate Abandonment" to listOf("abandoned", "failed to act", "mandate", "duty", "responsibility", "negligence"),
        "Institutional Silence" to listOf("silence", "no response", "bounced", "ignored", "failed to respond", "remains silent"),
        "Effective Remedy Denial" to listOf("effective remedy", "ICCPR", "Article 2(3)", "denied remedy", "no recourse")
    )
)

/** Louw v Moolla — South Africa (SAPS 1754). */
fun louwVMoollaConfig() = CaseConfig(
    name = "Louw v Moolla (SAPS 1754)",
    liabilityAdmit = listOf("admit", "confess", "yes it was", "i did", "proceeded"),
    liabilityDeny = listOf("deny", "not true", "false", "never happened", "didn't", "i reject", "never authorized"),
    liabilityConceal = listOf("hidden", "withheld", "didn't tell", "omitted", "never disclosed", "concealed"),
    topicKeywords = listOf("Louw", "Moolla", "SAPS 1754", "South Africa", "contract", "breach", "fraud", "misrepresentation", "property", "dispute"),
    entityKeywords = listOf("Louw", "Moolla", "SAPS", "South Africa", "Section 1754"),
    legalSubjects = mapOf(
        "Contract Breach" to listOf("contract", "breach", "violation", "terms", "agreement", "failed to perform"),
        "Misrepresentation" to listOf("misrepresent", "false statement", "deceive", "fraudulent", "induce"),
        "Property Dispute" to listOf("property", "title", "ownership", "transfer", "conveyance", "deed")
    )
)

/** Liebenberg v Standard Bank — South Africa. */
fun liebenbergVStandardBankConfig() = CaseConfig(
    name = "Liebenberg v Standard Bank",
    liabilityAdmit = listOf("admit", "confess", "yes it was", "i did"),
    liabilityDeny = listOf("deny", "not true", "false", "never happened", "didn't", "i reject", "no such record"),
    liabilityConceal = listOf("hidden", "withheld", "didn't tell", "omitted", "concealed", "destroyed"),
    topicKeywords = listOf("Liebenberg", "Standard Bank", "South Africa", "banking", "fraud", "account", "transaction", "unauthorized", "debit", "credit"),
    entityKeywords = listOf("Liebenberg", "Standard Bank", "South Africa", "banking fraud", "unauthorized transaction"),
    legalSubjects = mapOf(
        "Banking Fraud" to listOf("bank", "account", "unauthorized", "debit", "credit", "transaction", "fraudulent"),
        "Financial Misconduct" to listOf("misconduct", "negligence", "breach of duty", "fiduciary", "banking regulations"),
        "Consumer Protection" to listOf("consumer", "protection", "NCA", "FAIS", "ombudsman", "complaint")
    )
)

/** Standard Bank master bundle — Greensky + Southbridge + Liebenberg (standardbank_findings_v531c.json). */
fun standardbankConfig() = CaseConfig(
    name = "Standard Bank Master Bundle",
    liabilityAdmit = listOf("admit", "confess", "yes it was", "i did", "admitted", "completed", "proceeded"),
    liabilityDeny = listOf("deny", "not true", "false", "never happened", "didn't", "i reject", "fell through", "never invoiced"),
    liabilityConceal = listOf("hidden", "withheld", "didn't tell", "omitted", "concealed", "destroyed", "backdated"),
    topicKeywords = listOf(
        "Standard Bank", "Greensky", "Southbridge", "RAKEZ", "DIFC",
        "fraud", "account", "transaction", "unauthorized", "defamation",
        "shareholder", "exclusivity", "protection order", "jurat",
        "R4M", "R4m", "loan", "credit application", "NCA"
    ),
    entityKeywords = listOf(
        "Standard Bank", "Liam Highcock", "Marius Nortje", "Kevin Lappeman",
        "Greensky Ornamentals", "Southbridge Legal", "RAKEZ", "DIFC",
        "Faimy Amar", "Devika G Kurup", "Nanzo", "Rakash Daya"
    ),
    legalSubjects = mapOf(
        "Banking Fraud" to listOf("bank", "account", "unauthorized", "debit", "credit", "transaction", "fraudulent", "R4m", "R4M"),
        "Shareholder Oppression" to listOf("shareholder", "excluded", "50%", "exclusivity", "RAKEZ", "Greensky"),
        "Cybercrime" to listOf("gmail", "access", "hack", "unauthorized", "archive", "device", "google account", "data breach"),
        "Fraudulent Evidence" to listOf("screenshot", "whatsapp", "fake", "doctored", "fabricated", "cropped", "forged", "decoy"),
        "Defamation Threat" to listOf("defamation", "cease and desist", "reputational harm", "govern yourself accordingly"),
        "Attorney Misconduct" to listOf("attorney obstruction", "continued to act", "false statements on record", "withheld mandate"),
        "Process Remedy Denial" to listOf("effective remedy", "ICCPR", "mandatory duty", "institutional silence", "no response", "denied remedy"),
        "Temporal Precedence" to listOf("served", "dated", "before", "after", "30 April", "23 April"),
        "Goodwill Forfeiture" to listOf("goodwill", "buy-out", "take over", "R150,000", "lost money")
    )
)

/** Louw v Olivier — South Africa (SAPS 147/12/2025). */
fun louwVOlivierConfig() = CaseConfig(
    name = "Louw v Olivier (SAPS 147/12/2025)",
    liabilityAdmit = listOf("admit", "confess", "yes it was", "i did", "proceeded"),
    liabilityDeny = listOf("deny", "not true", "false", "never happened", "didn't", "i reject", "no knowledge"),
    liabilityConceal = listOf("hidden", "withheld", "didn't tell", "omitted", "concealed", "never disclosed"),
    topicKeywords = listOf("Louw", "Olivier", "SAPS 147/12/2025", "South Africa", "criminal", "fraud", "theft", "forgery", "document", "false"),
    entityKeywords = listOf("Louw", "Olivier", "SAPS", "South Africa", "criminal fraud", "forgery"),
    legalSubjects = mapOf(
        "Criminal Fraud" to listOf("fraud", "criminal", "theft", "deceive", "dishonest", "unlawful"),
        "Forgery" to listOf("forge", "fabricated", "false document", "signature", "counterfeit", "altered"),
        "Document Tampering" to listOf("tamper", "alter", "modify", "destroy", "conceal document", "evidence destruction")
    )
)

/** Mostert v Digsim — Protection from Harassment Act (PHA 2026/06). */
fun mostertVDigsimConfig() = CaseConfig(
    name = "Mostert v Digsim (PHA 2026/06)",
    liabilityAdmit = listOf("admit", "confess", "yes it was", "i did", "used", "leverage", "pressure"),
    liabilityDeny = listOf("deny", "not true", "false", "never happened", "didn't", "i reject", "no protection order"),
    liabilityConceal = listOf("hidden", "withheld", "didn't tell", "omitted", "concealed purpose", "misrepresented"),
    topicKeywords = listOf(
        "Mostert", "Digsim", "PHA 2026/06", "South Africa",
        "Protection from Harassment Act", "protection order",
        "leverage", "bargaining", "commercial dispute",
        "jurat", "defective", "oath", "commissioner",
        "harassment", "interdict", "restrain",
        "character", "assassination", "credibility"
    ),
    entityKeywords = listOf("Mostert", "Digsim", "PHA", "South Africa", "Protection from Harassment Act", "defective jurat", "commissioner", "oath"),
    legalSubjects = mapOf(
        "Protection Order Abuse" to listOf("protection order", "harassment act", "interdict", "restrain", "leverage", "bargaining tool", "commercial dispute", "misuse"),
        "Defective Jurat" to listOf("jurat", "oath", "commissioner", "defective", "missing jurat", "no oath", "sworn", "before me", "Justice of the Peace"),
        "Character Assassination" to listOf("character", "reputation", "dishonest", "untrustworthy", "mental health", "emotional", "personal attack", "credibility", "irrelevant"),
        "False Allegation in Affidavit" to listOf("false allegation", "sworn", "affidavit", "contradicted", "contemporaneous evidence", "perjury"),
        "Process Remedy Denial" to listOf("effective remedy", "ICCPR", "mandatory duty", "institutional silence", "bounce", "no response", "denied remedy")
    )
)

/** Get configuration by case name — supports all 9 cases. Defaults to AllFuels. */
fun getCaseConfig(caseName: String): CaseConfig {
    val normalized = caseName.lowercase().trim()
    return when {
        "allfuels-111" in normalized || "111-contradiction" in normalized || "111 contradiction" in normalized ->
            allfuels111Config()
        "greensky" in normalized || "green sky" in normalized || "rakez" in normalized ->
            greenskyConfig()
        "southbridge" in normalized || "vo-hr-2025" in normalized ->
            southbridgeConfig()
        "louw" in normalized && "moolla" in normalized ->
            louwVMoollaConfig()
        "louw" in normalized && "olivier" in normalized ->
            louwVOlivierConfig()
        "liebenberg-standardbank" in normalized || "liebenberg standard bank" in normalized ||
            "liebenberg v standard bank" in normalized ->
            liebenbergVStandardBankConfig()
        "standardbank" in normalized || "standard bank" in normalized ->
            standardbankConfig()
        "mostert" in normalized || "digsim" in normalized || "pha 2026" in normalized ->
            mostertVDigsimConfig()
        else ->
            allfuelsConfig()
    }
}
