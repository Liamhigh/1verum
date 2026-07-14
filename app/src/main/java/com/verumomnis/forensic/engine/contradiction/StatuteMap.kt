package com.verumomnis.forensic.engine.contradiction

import kotlinx.serialization.Serializable

/**
 * Verum Omnis v6 — local-statute enrichment layer (Kotlin).
 * Status: RATIFIED — BINDING (founder directive, 2026-07-14).
 *
 * Forensic chain (ratified):
 *   extraction -> contradiction -> PERSON -> PAGE -> LOCAL LAW (statute)
 *
 * Adds the final anchor — the LOCAL STATUTE / LAW a contradiction engages — to
 * each legal hypothesis.  Pure read/transform over the findings contract; the
 * sealed engine is never modified.  Citations are hypotheses, not legal advice.
 *
 * Statute accuracy verified against the Acts themselves (South Africa):
 *  - Perjury: common-law; s 9 Justices of the Peace and Commissioners of Oaths
 *    Act 16 of 1963; s 319 Criminal Procedure Act 51 of 1977.
 *  - Cybercrime: Cybercrimes Act 19 of 2020 (ss 2-12, in force 1 Dec 2021);
 *    RICA 70 of 2002 s 2; ECTA 25 of 2002 s 86 (legacy).
 *  - Extortion/leverage: Cybercrimes Act s 10; extortion (common law);
 *    Intimidation Act 72 of 1982 s 1; defeating the ends of justice (common law);
 *    PRECCA 12 of 2004 (gratification).
 */
object StatuteMap {

    const val TYPE_SWORN_VS_SWORN = "SWORN_VS_SWORN"
    const val TYPE_DEVICE_ATTRIBUTION_CHAIN = "DEVICE_ATTRIBUTION_CHAIN"
    const val TYPE_CRIMINAL_CHARGE_AS_LEVERAGE = "CRIMINAL_CHARGE_AS_LEVERAGE"

    const val JURISDICTION_ZA = "ZA"
    const val JURISDICTION_GENERIC = "GENERIC"
    const val DEFAULT_JURISDICTION = JURISDICTION_ZA

    @Serializable
    data class StatuteCitation(
        val instrument: String,
        val citation: String,
        val note: String = "",
        val statutory: Boolean = true,
    )

    private fun cite(
        instrument: String,
        citation: String,
        note: String,
        statutory: Boolean = true,
    ) = StatuteCitation(instrument, citation, note, statutory)

    private val ZA: Map<String, List<StatuteCitation>> = mapOf(
        TYPE_SWORN_VS_SWORN to listOf(
            cite(
                "Justices of the Peace and Commissioners of Oaths Act 16 of 1963",
                "s 9",
                "False statement in an affidavit/affirmation/solemn declaration, made knowing it to be false; liable to perjury penalties.",
            ),
            cite(
                "Perjury (common law)",
                "common-law offence",
                "Wilfully making a false statement under oath; up to 10 years imprisonment.",
                statutory = false,
            ),
            cite(
                "Criminal Procedure Act 51 of 1977",
                "s 319",
                "Admissibility/proof of a witness's previous inconsistent statement.",
            ),
        ),
        TYPE_DEVICE_ATTRIBUTION_CHAIN to listOf(
            cite("Cybercrimes Act 19 of 2020", "s 2", "Unlawful and intentional access to a computer system or data."),
            cite("Cybercrimes Act 19 of 2020", "s 3", "Unlawful and intentional interception of data."),
            cite("Cybercrimes Act 19 of 2020", "s 5", "Unlawful interference with data or a computer program."),
            cite(
                "Cybercrimes Act 19 of 2020",
                "s 11",
                "Aggravated offence where the target is a restricted computer system (financial institution / organ of state).",
            ),
            cite(
                "Regulation of Interception of Communications Act 70 of 2002 (RICA)",
                "s 2",
                "Prohibition on interception of communications without authority.",
            ),
            cite(
                "Electronic Communications and Transactions Act 25 of 2002 (ECTA)",
                "s 86",
                "Legacy unauthorised access/interception provision (pre-Dec-2021 conduct).",
            ),
        ),
        TYPE_CRIMINAL_CHARGE_AS_LEVERAGE to listOf(
            cite("Cybercrimes Act 19 of 2020", "s 10", "Cyber extortion where the threat/pressure is applied via data message."),
            cite(
                "Extortion (common law)",
                "common-law offence",
                "Unlawfully and intentionally applying pressure to induce a person to submit or hand over an advantage.",
                statutory = false,
            ),
            cite("Intimidation Act 72 of 1982", "s 1", "Intimidating conduct intended to compel a course of action."),
            cite(
                "Defeating or obstructing the course of justice (common law)",
                "common-law offence",
                "Abusing a criminal charge/report to derail a lawful process.",
                statutory = false,
            ),
            cite(
                "Prevention and Combating of Corrupt Activities Act 12 of 2004 (PRECCA)",
                "ss 3-7",
                "Applies where the leverage involves an offer/demand of a gratification.",
            ),
        ),
        // Existing engine types — close the local-law anchor where a confident map exists.
        "CONTRADICTORY_STATEMENTS" to listOf(
            cite("Criminal Procedure Act 51 of 1977", "s 319", "Previous inconsistent statement by a witness."),
        ),
        "SWORN_VS_UNSIGNED" to listOf(
            cite("Justices of the Peace and Commissioners of Oaths Act 16 of 1963", "s 9", "False statement in a sworn affidavit."),
        ),
        "DOCUMENT_ALTERATION" to listOf(
            cite("Cybercrimes Act 19 of 2020", "s 9", "Cyber forgery and uttering."),
            cite("Forgery and uttering (common law)", "common-law offence", "Unlawful falsification of a document with intent to defraud.", statutory = false),
        ),
        "CONTEMPT_OF_COURT_ORDER" to listOf(
            cite("Contempt of court (common law)", "common-law offence", "Wilful disobedience of a court order (e.g. judgment H208/25).", statutory = false),
        ),
        "BREACH_OF_COURT_ORDER" to listOf(
            cite("Contempt of court (common law)", "common-law offence", "Wilful disobedience of a court order (e.g. judgment H208/25).", statutory = false),
        ),
    )

    private val GENERIC: Map<String, List<StatuteCitation>> = mapOf(
        TYPE_SWORN_VS_SWORN to listOf(
            cite(
                "Perjury (common law)",
                "common-law offence",
                "Two mutually exclusive sworn statements on one material fact; one deponent has sworn falsely. Confirm the local perjury statute.",
                statutory = false,
            ),
        ),
        TYPE_DEVICE_ATTRIBUTION_CHAIN to listOf(
            cite(
                "Computer-misuse / unlawful-access legislation",
                "jurisdiction-specific",
                "Unauthorised access to or interception of data is an offence in virtually all jurisdictions; cite the local computer-misuse act.",
            ),
        ),
        TYPE_CRIMINAL_CHARGE_AS_LEVERAGE to listOf(
            cite(
                "Extortion / blackmail (common law)",
                "common-law offence",
                "Applying pressure (including a threatened criminal charge) to extract an advantage. Confirm the local extortion/blackmail law.",
                statutory = false,
            ),
            cite(
                "Perverting / defeating the course of justice (common law)",
                "common-law offence",
                "Abusing a criminal process to obstruct a lawful one.",
                statutory = false,
            ),
        ),
    )

    /** Resolve a user-supplied jurisdiction code to a known map key. */
    fun normaliseJurisdiction(code: String?): String {
        if (code.isNullOrBlank()) return DEFAULT_JURISDICTION
        return when (code.trim().uppercase()) {
            "ZA", "RSA", "SOUTH AFRICA", "ZAF", "SA" -> JURISDICTION_ZA
            "GENERIC" -> JURISDICTION_GENERIC
            else -> JURISDICTION_GENERIC
        }
    }

    /** Ordered statute citations for a contradiction type, with GENERIC fallback. */
    fun statutesForType(contradictionType: String, jurisdiction: String? = null): List<StatuteCitation> {
        val j = normaliseJurisdiction(jurisdiction)
        val primary = if (j == JURISDICTION_ZA) ZA else GENERIC
        primary[contradictionType]?.let { if (it.isNotEmpty()) return it }
        if (j != JURISDICTION_GENERIC) {
            GENERIC[contradictionType]?.let { if (it.isNotEmpty()) return it }
        }
        return emptyList()
    }

    fun jurisdictionalNote(jurisdiction: String?): String =
        when (normaliseJurisdiction(jurisdiction)) {
            JURISDICTION_ZA ->
                "Local law: Republic of South Africa. Statute citations are hypotheses for legal review, not legal advice."
            else ->
                "Local law: common-law baseline. Confirm the equivalent statute in the governing jurisdiction."
        }
}
