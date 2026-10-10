package com.verumomnis.forensic.engine.tax

/**
 * Jurisdiction fee table for the Verum Omnis tax-return review service.
 *
 * Owner requirement: the Verum fee for reviewing a company tax return is 50% of
 * what an accountant in that jurisdiction / geolocation would typically charge
 * for the same company tax return work. This is a fee for a document-analysis
 * service only — findings are indicators, not determinations — and no
 * contingency or recovery-percentage pricing applies.
 *
 * D5 (manifest seam): the bundled table below is a starter set and is explicitly
 * marked as estimates. In a later release the table will be replaced at runtime
 * by a SIGNED RULES MANIFEST (see [RULES_MANIFEST_ASSET] and [FeeTableLoader]).
 * No networking is performed now; the loader seam exists so a signed manifest
 * can be verified and loaded without changing call sites.
 */
data class JurisdictionFee(
    /** ISO 3166-1 alpha-2 country code, or [JurisdictionFees.INTL_CODE] for the fallback. */
    val countryCode: String,
    val countryName: String,
    /** ISO 4217 currency code. */
    val currency: String,
    /** Low end of the typical accountant fee for a company tax return (estimates). */
    val typicalAccountantFeeLow: Double,
    /** High end of the typical accountant fee for a company tax return (estimates). */
    val typicalAccountantFeeHigh: Double,
    /** Plain-language basis note shown with the quote. */
    val note: String,
    /** ISO date the figures were last reviewed. */
    val updatedAt: String
)

/** Source of jurisdiction fee rows. Bundled now; signed manifest later. */
fun interface FeeTableLoader {
    fun load(): List<JurisdictionFee>
}

/** Bundled starter table (estimates). Replaced by a signed manifest loader later. */
object BundledFeeTableLoader : FeeTableLoader {
    override fun load(): List<JurisdictionFee> = JurisdictionFees.STARTER_TABLE
}

object JurisdictionFees {

    /** Fallback row code when the device locale / picker yields no match. */
    const val INTL_CODE = "INTL"

    /** Verum fee is always half of the typical accountant fee. */
    const val VERUM_FEE_RATIO = 0.5

    /**
     * Manifest seam (D5): location of the future signed fee-rules manifest.
     * No network fetch exists yet; this constant anchors the design so the
     * updater (engine/update) can verify and swap the table via [FeeTableLoader].
     */
    const val RULES_MANIFEST_ASSET = "engine/tax/fee-rules.manifest.json"

    /** Minimum schema version a future signed manifest must declare to be accepted. */
    const val RULES_MANIFEST_MIN_VERSION = 1

    /**
     * Starter table — ESTIMATES ONLY, clearly presented to the user as such.
     * South Africa first (primary market). Figures are broad market ranges for
     * a small-company tax return review/filing by an accountant, not quotations.
     */
    val STARTER_TABLE: List<JurisdictionFee> = listOf(
        JurisdictionFee(
            countryCode = "ZA",
            countryName = "South Africa",
            currency = "ZAR",
            typicalAccountantFeeLow = 5_000.0,
            typicalAccountantFeeHigh = 25_000.0,
            note = "Estimate: small-company SARS return review/filing typically ranges " +
                "R5,000–R25,000 depending on complexity.",
            updatedAt = "2026-07-22"
        ),
        JurisdictionFee(
            countryCode = "US",
            countryName = "United States",
            currency = "USD",
            typicalAccountantFeeLow = 1_000.0,
            typicalAccountantFeeHigh = 5_000.0,
            note = "Estimate: small-business corporate return (Form 1120) preparation " +
                "typically ranges $1,000–$5,000.",
            updatedAt = "2026-07-22"
        ),
        JurisdictionFee(
            countryCode = "GB",
            countryName = "United Kingdom",
            currency = "GBP",
            typicalAccountantFeeLow = 750.0,
            typicalAccountantFeeHigh = 3_000.0,
            note = "Estimate: small-company CT600 corporation tax return typically " +
                "ranges £750–£3,000.",
            updatedAt = "2026-07-22"
        ),
        JurisdictionFee(
            countryCode = "AE",
            countryName = "United Arab Emirates",
            currency = "AED",
            typicalAccountantFeeLow = 3_000.0,
            typicalAccountantFeeHigh = 15_000.0,
            note = "Estimate: UAE corporate tax return support typically ranges " +
                "AED 3,000–15,000.",
            updatedAt = "2026-07-22"
        ),
        JurisdictionFee(
            countryCode = "AU",
            countryName = "Australia",
            currency = "AUD",
            typicalAccountantFeeLow = 1_500.0,
            typicalAccountantFeeHigh = 5_000.0,
            note = "Estimate: small-company ATO company return typically ranges " +
                "A$1,500–A$5,000.",
            updatedAt = "2026-07-22"
        ),
        JurisdictionFee(
            countryCode = INTL_CODE,
            countryName = "Other / international",
            currency = "USD",
            typicalAccountantFeeLow = 1_000.0,
            typicalAccountantFeeHigh = 5_000.0,
            note = "Estimate: international fallback range; final quote confirmed " +
                "before any work begins.",
            updatedAt = "2026-07-22"
        )
    )

    /**
     * Resolve the fee row for an ISO country code (device locale or manual picker).
     * Unknown / null codes resolve to the INTL fallback. The [loader] parameter is
     * the D5 seam: today it defaults to the bundled table; later a verified signed
     * manifest loader can be injected without touching call sites.
     */
    fun resolve(countryCode: String?, loader: FeeTableLoader = BundledFeeTableLoader): JurisdictionFee {
        val table = loader.load()
        val fallback = table.firstOrNull { it.countryCode == INTL_CODE } ?: STARTER_TABLE.last()
        if (countryCode.isNullOrBlank()) return fallback
        return table.firstOrNull { it.countryCode.equals(countryCode.trim(), ignoreCase = true) } ?: fallback
    }

    /** All selectable rows for the manual country picker (fallback last). */
    fun pickerOptions(loader: FeeTableLoader = BundledFeeTableLoader): List<JurisdictionFee> = loader.load()

    /** Verum fee range = 50% of the typical accountant fee range. */
    fun verumFeeRange(fee: JurisdictionFee): Pair<Double, Double> =
        (fee.typicalAccountantFeeLow * VERUM_FEE_RATIO) to (fee.typicalAccountantFeeHigh * VERUM_FEE_RATIO)
}
