package com.verumomnis.forensic.core

/**
 * Constitutional safeguards (build spec Section 25) enforced in code, not config.
 * All thresholds come from [Constitution] compile-time constants.
 */
object Safeguards {

    /** Ethics kill switch: systemic bias above 0.3% halts ALL operations. */
    fun ethicsHalt(biasRatio: Double): Boolean = biasRatio > Constitution.ETHICS_HALT_THRESHOLD

    /** Profit firewall: 99% of revenue routed to the Verum Foundation. */
    data class ProfitSplit(val toFoundation: Double, val retained: Double)

    fun profitSplit(revenue: Double): ProfitSplit {
        val toFoundation = revenue * Constitution.PROFIT_TO_FOUNDATION / 100.0
        return ProfitSplit(toFoundation, revenue - toFoundation)
    }

    /** 20% commission on recovered fraud, calculated by code (never by AI). */
    fun commission(recoveredFraud: Double): Double = recoveredFraud * Constitution.COMMISSION_PERCENT / 100.0

    private val PRIVATIZATION = Regex(
        """\b(sell|sale|acquire|acquisition|buy\s?out|privatis|privatiz|equity\s+stake|ipo|shares?\s+in\s+verum)\b""",
        RegexOption.IGNORE_CASE
    )

    /** No privatization: equity-sale attempts are a Constitutional violation. */
    fun isPrivatizationAttempt(text: String): Boolean = PRIVATIZATION.containsMatchIn(text)

    private val WEAPONIZATION = Regex(
        """\b(lethal\s+targeting|battlefield|weapon|munition|missile|drone\s+strike|kill\s+chain|warfare)\b""",
        RegexOption.IGNORE_CASE
    )

    /** Anti-War Doctrine (Article X): refuse warfare / weapons integration. Supreme hierarchy. */
    fun violatesAntiWarDoctrine(request: String): Boolean =
        Constitution.ANTI_WAR_DOCTRINE && WEAPONIZATION.containsMatchIn(request)

    /** Immutable Constitution: any change invalidates seals (see Constitution.rulesetFingerprint). */
    fun constitutionIsFinal(): Boolean = Constitution.FINAL && Constitution.VERSION == "5.2.8"
}
