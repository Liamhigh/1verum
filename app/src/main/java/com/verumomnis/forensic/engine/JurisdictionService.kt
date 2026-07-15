package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.ContradictionCategory
import com.verumomnis.forensic.model.GpsRecord
import com.verumomnis.forensic.model.JurisdictionSource

/**
 * Maps GPS coordinates to a jurisdiction and the statutes that apply to each
 * canonical contradiction category in that jurisdiction.
 *
 * The service is deterministic and offline. New regions are added by extending
 * the [REGIONS] list; if no region matches, the result falls back to a
 * keyword-derived international framework.
 */
object JurisdictionService {

    data class Region(
        val code: String,
        val label: String,
        val contains: (GpsRecord) -> Boolean,
        val statutes: Map<ContradictionCategory, List<String>>
    )

    private val REGIONS = listOf(
        Region(
            code = "ZA-KZN",
            label = "South Africa — KwaZulu-Natal",
            contains = { it.latitude in -35.0..-22.0 && it.longitude in 16.0..33.0 },
            statutes = mapOf(
                ContradictionCategory.GOODWILL_VALUE to listOf(
                    "SA Common Law - Fraud",
                    "Petroleum Products Act 120 of 1977, s.12B"
                ),
                ContradictionCategory.CONTRACT_VALIDITY to listOf(
                    "Contract Law - Common Law",
                    "Companies Act 71 of 2008"
                ),
                ContradictionCategory.SIGNATURE_STATUS to listOf(
                    "Contract Law - Common Law",
                    "Electronic Communications and Transactions Act 25 of 2002"
                ),
                ContradictionCategory.SECTION_12B to listOf(
                    "Petroleum Products Act 120 of 1977, s.12B"
                ),
                ContradictionCategory.COMPENSATION to listOf(
                    "SA Common Law - Fraud"
                ),
                ContradictionCategory.PERJURY to listOf(
                    "SA Common Law - Perjury",
                    "Constitution of the Republic of South Africa, 1996, s.34"
                ),
                ContradictionCategory.COERCION to listOf(
                    "SA Common Law - Fraud",
                    "Prevention of Organised Crime Act 121 of 1998 (racketeering)"
                ),
                ContradictionCategory.OTHER to listOf("SA Common Law - Fraud")
            )
        ),
        Region(
            code = "ZA-GP",
            label = "South Africa — Gauteng",
            contains = { it.latitude in -35.0..-22.0 && it.longitude in 25.0..30.0 },
            statutes = mapOf(
                ContradictionCategory.GOODWILL_VALUE to listOf(
                    "SA Common Law - Fraud",
                    "Companies Act 71 of 2008"
                ),
                ContradictionCategory.CONTRACT_VALIDITY to listOf(
                    "Contract Law - Common Law",
                    "Companies Act 71 of 2008"
                ),
                ContradictionCategory.SIGNATURE_STATUS to listOf(
                    "Contract Law - Common Law",
                    "Electronic Communications and Transactions Act 25 of 2002"
                ),
                ContradictionCategory.SECTION_12B to listOf(
                    "Petroleum Products Act 120 of 1977, s.12B"
                ),
                ContradictionCategory.COMPENSATION to listOf("SA Common Law - Fraud"),
                ContradictionCategory.PERJURY to listOf(
                    "SA Common Law - Perjury",
                    "Constitution of the Republic of South Africa, 1996, s.34"
                ),
                ContradictionCategory.COERCION to listOf(
                    "SA Common Law - Fraud",
                    "Prevention of Organised Crime Act 121 of 1998 (racketeering)"
                ),
                ContradictionCategory.OTHER to listOf("SA Common Law - Fraud")
            )
        ),
        Region(
            code = "UAE-RAKEZ",
            label = "United Arab Emirates — RAKEZ",
            contains = { it.latitude in 22.0..26.5 && it.longitude in 51.0..56.5 },
            statutes = mapOf(
                ContradictionCategory.GOODWILL_VALUE to listOf(
                    "UAE Federal Law No. 5 of 1985 (Civil Transactions Code), Art. 84",
                    "UAE Federal Law No. 18 of 1993 (Commercial Transactions Code)"
                ),
                ContradictionCategory.CONTRACT_VALIDITY to listOf(
                    "UAE Federal Law No. 5 of 1985, Arts. 125-129",
                    "UAE Federal Law No. 18 of 1993"
                ),
                ContradictionCategory.SIGNATURE_STATUS to listOf(
                    "UAE Federal Law No. 5 of 1985, Art. 129",
                    "UAE Electronic Transactions and E-Commerce Law No. 1 of 2006"
                ),
                ContradictionCategory.SECTION_12B to listOf(
                    "UAE Commercial Agencies Law (Federal Law No. 11 of 1973 as amended)"
                ),
                ContradictionCategory.COMPENSATION to listOf(
                    "UAE Federal Law No. 5 of 1985, Art. 84",
                    "UAE Commercial Fraud"
                ),
                ContradictionCategory.PERJURY to listOf(
                    "UAE Penal Code (Federal Decree-Law No. 31 of 2021), perjury provisions"
                ),
                ContradictionCategory.COERCION to listOf(
                    "UAE Penal Code (Federal Decree-Law No. 31 of 2021), coercion / duress",
                    "UAE Federal Law No. 4 of 2016 (financial fraud / cybercrime)"
                ),
                ContradictionCategory.OTHER to listOf("UAE Federal Law No. 5 of 1985")
            )
        ),
        Region(
            code = "UAE-DIFC",
            label = "Dubai International Financial Centre",
            contains = { it.latitude in 24.9..25.3 && it.longitude in 55.1..55.3 },
            statutes = mapOf(
                ContradictionCategory.GOODWILL_VALUE to listOf("DIFC Contract Law DIFC Law No. 6 of 2004"),
                ContradictionCategory.CONTRACT_VALIDITY to listOf("DIFC Contract Law DIFC Law No. 6 of 2004"),
                ContradictionCategory.SIGNATURE_STATUS to listOf("DIFC Electronic Transactions Law DIFC Law No. 2 of 2017"),
                ContradictionCategory.COMPENSATION to listOf("DIFC Contract Law DIFC Law No. 6 of 2004"),
                ContradictionCategory.PERJURY to listOf("DIFC Court Regulations"),
                ContradictionCategory.COERCION to listOf("DIFC Contract Law DIFC Law No. 6 of 2004"),
                ContradictionCategory.OTHER to listOf("DIFC Contract Law DIFC Law No. 6 of 2004")
            )
        ),
        Region(
            code = "US",
            label = "United States",
            contains = { it.latitude in 18.0..72.0 && it.longitude in -180.0..-60.0 },
            statutes = mapOf(
                ContradictionCategory.GOODWILL_VALUE to listOf("Uniform Commercial Code (UCC) Article 2"),
                ContradictionCategory.CONTRACT_VALIDITY to listOf("Restatement (Second) of Contracts"),
                ContradictionCategory.SIGNATURE_STATUS to listOf("E-SIGN Act 15 U.S.C. § 7001"),
                ContradictionCategory.COMPENSATION to listOf("UCC Article 2"),
                ContradictionCategory.PERJURY to listOf("18 U.S.C. § 1621 (perjury)"),
                ContradictionCategory.COERCION to listOf("18 U.S.C. § 1951 (Hobbs Act)"),
                ContradictionCategory.OTHER to listOf("Restatement (Second) of Contracts")
            )
        ),
        Region(
            code = "EU",
            label = "European Union",
            contains = { it.latitude in 36.0..71.0 && it.longitude in -10.0..40.0 },
            statutes = mapOf(
                ContradictionCategory.GOODWILL_VALUE to listOf("EU Directive 2011/7/EU (late payment)"),
                ContradictionCategory.CONTRACT_VALIDITY to listOf("Principles of European Contract Law (PECL)"),
                ContradictionCategory.SIGNATURE_STATUS to listOf("eIDAS Regulation (EU) No 910/2014"),
                ContradictionCategory.COMPENSATION to listOf("EU Directive 2011/7/EU"),
                ContradictionCategory.PERJURY to listOf("National criminal codes (perjury)"),
                ContradictionCategory.COERCION to listOf("EU Directive 2017/541 (terrorism / organised crime framework)"),
                ContradictionCategory.OTHER to listOf("Principles of European Contract Law")
            )
        ),
        Region(
            code = "UK",
            label = "United Kingdom",
            contains = { it.latitude in 49.0..59.0 && it.longitude in -8.0..2.0 },
            statutes = mapOf(
                ContradictionCategory.GOODWILL_VALUE to listOf("UK Common Law - Fraud", "Contracts (Rights of Third Parties) Act 1999"),
                ContradictionCategory.CONTRACT_VALIDITY to listOf("UK Contract Law - Common Law"),
                ContradictionCategory.SIGNATURE_STATUS to listOf("Electronic Communications Act 2000"),
                ContradictionCategory.COMPENSATION to listOf("UK Common Law - Fraud"),
                ContradictionCategory.PERJURY to listOf("Perjury Act 1911"),
                ContradictionCategory.COERCION to listOf("UK Common Law - Duress", "Fraud Act 2006"),
                ContradictionCategory.OTHER to listOf("UK Common Law - Fraud")
            )
        ),
        Region(
            code = "AU",
            label = "Australia",
            contains = { it.latitude in -44.0..-10.0 && it.longitude in 113.0..154.0 },
            statutes = mapOf(
                ContradictionCategory.GOODWILL_VALUE to listOf("Australian Consumer Law (ACL)", "Corporations Act 2001"),
                ContradictionCategory.CONTRACT_VALIDITY to listOf("Australian Contract Law - Common Law"),
                ContradictionCategory.SIGNATURE_STATUS to listOf("Electronic Transactions Act 1999 (Cth)"),
                ContradictionCategory.COMPENSATION to listOf("Australian Consumer Law"),
                ContradictionCategory.PERJURY to listOf("Crimes Act 1914 (Cth) - perjury"),
                ContradictionCategory.COERCION to listOf("Commonwealth Criminal Code - fraud"),
                ContradictionCategory.OTHER to listOf("Australian Consumer Law")
            )
        ),
        Region(
            code = "CA",
            label = "Canada",
            contains = { it.latitude in 41.0..84.0 && it.longitude in -141.0..-52.0 },
            statutes = mapOf(
                ContradictionCategory.GOODWILL_VALUE to listOf("Canadian Common Law - Fraud"),
                ContradictionCategory.CONTRACT_VALIDITY to listOf("Canadian Contract Law - Common Law"),
                ContradictionCategory.SIGNATURE_STATUS to listOf("Personal Information Protection and Electronic Documents Act"),
                ContradictionCategory.COMPENSATION to listOf("Canadian Common Law - Fraud"),
                ContradictionCategory.PERJURY to listOf("Criminal Code (Canada) s.131 (perjury)"),
                ContradictionCategory.COERCION to listOf("Criminal Code (Canada) - fraud / extortion"),
                ContradictionCategory.OTHER to listOf("Canadian Common Law - Fraud")
            )
        ),
        Region(
            code = "IN",
            label = "India",
            contains = { it.latitude in 6.0..38.0 && it.longitude in 68.0..97.0 },
            statutes = mapOf(
                ContradictionCategory.GOODWILL_VALUE to listOf("Indian Contract Act 1872"),
                ContradictionCategory.CONTRACT_VALIDITY to listOf("Indian Contract Act 1872"),
                ContradictionCategory.SIGNATURE_STATUS to listOf("Information Technology Act 2000"),
                ContradictionCategory.COMPENSATION to listOf("Indian Contract Act 1872"),
                ContradictionCategory.PERJURY to listOf("Indian Penal Code 1860 - perjury"),
                ContradictionCategory.COERCION to listOf("Indian Penal Code 1860 - cheating / criminal breach of trust"),
                ContradictionCategory.OTHER to listOf("Indian Contract Act 1872")
            )
        )
    )

    /** Resolve a jurisdiction record from GPS, or return an international fallback. */
    fun resolve(gps: GpsRecord?): JurisdictionSource {
        gps ?: return JurisdictionSource("INTL")
        val region = REGIONS.firstOrNull { it.contains(gps) }
        return if (region != null) {
            JurisdictionSource(
                jurisdiction = region.code,
                gps = gps,
                statutes = region.statutes.values.flatten().distinct()
            )
        } else {
            JurisdictionSource("INTL", gps)
        }
    }

    /** Statutes that apply to a specific contradiction category in the resolved jurisdiction. */
    fun statutesFor(jurisdictionCode: String, category: ContradictionCategory): List<String> {
        val region = REGIONS.firstOrNull { it.code == jurisdictionCode }
        return region?.statutes?.get(category) ?: listOf("International common-law fraud framework")
    }
}
