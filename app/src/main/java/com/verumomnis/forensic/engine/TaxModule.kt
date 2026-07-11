package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.Commission
import com.verumomnis.forensic.model.FeeEstimate
import com.verumomnis.forensic.model.TaxResult
import kotlin.math.max

/**
 * Tax Module (Part V) — a core component of the Financial Brain (B6). Calculates
 * company tax, individual tax, benchmarks accountant fees, and computes the fixed
 * 20% recovered-fraud commission by CODE (never by AI, per spec 9.3).
 */
object TaxModule {

    /** South Africa company tax (spec 5.1). */
    fun calculateCompanyTaxZA(
        revenue: Double,
        expenses: Double,
        deductions: List<Double> = emptyList()
    ): TaxResult {
        val taxableIncome = revenue - expenses - deductions.sum()
        val rate = when {
            taxableIncome <= 0 -> 0.0
            taxableIncome <= 95_000 -> 0.07
            taxableIncome <= 365_000 -> 0.21
            taxableIncome <= 550_000 -> 0.28
            else -> 0.28
        }
        val taxLiability = max(0.0, taxableIncome) * rate
        return TaxResult(taxableIncome, rate, taxLiability, "ZAR", "SA")
    }

    /** South Africa individual income tax, 2026 brackets (spec 5.2). */
    fun calculateIndividualTaxZA(taxableIncome: Double, age: Int): TaxResult {
        val tax = when {
            taxableIncome <= 237_100 -> taxableIncome * 0.18
            taxableIncome <= 370_500 -> 42_678 + (taxableIncome - 237_100) * 0.26
            taxableIncome <= 512_800 -> 77_362 + (taxableIncome - 370_500) * 0.31
            taxableIncome <= 673_000 -> 121_475 + (taxableIncome - 512_800) * 0.36
            taxableIncome <= 857_900 -> 179_147 + (taxableIncome - 673_000) * 0.39
            taxableIncome <= 1_817_000 -> 251_258 + (taxableIncome - 857_900) * 0.41
            else -> 644_489 + (taxableIncome - 1_817_000) * 0.45
        }
        val rebate = when {
            age >= 75 -> 31_479
            age >= 65 -> 23_425
            else -> 17_235
        }
        val finalTax = max(0.0, tax - rebate)
        val effectiveRate = if (taxableIncome > 0) finalTax / taxableIncome else 0.0
        return TaxResult(taxableIncome, effectiveRate, finalTax, "ZAR", "SA")
    }

    /** Accountant fee benchmarking (spec 5.3). */
    fun estimateAccountantFee(
        jurisdiction: String,
        serviceType: String,
        complexity: String,
        entityType: String
    ): FeeEstimate {
        val baseRate = when (jurisdiction) {
            "ZA-KZN" -> 800.0
            "ZA-GP" -> 1200.0
            "ZA-WC" -> 1000.0
            "UAE-RAKEZ" -> 450.0
            else -> 800.0
        }
        val hours = when (complexity) {
            "simple" -> 2.0
            "moderate" -> 8.0
            "complex" -> 24.0
            else -> 8.0
        }
        // Forensic accounting carries additional depth.
        val serviceMultiplier = when (serviceType) {
            "forensic" -> 1.5
            "audit" -> 1.25
            else -> 1.0
        }
        val entityMultiplier = when (entityType) {
            "individual" -> 1.0
            "sme" -> 1.5
            "corporate" -> 2.5
            else -> 1.0
        }
        val estimatedFee = baseRate * hours * serviceMultiplier * entityMultiplier
        return FeeEstimate(estimatedFee, baseRate, hours, jurisdiction)
    }

    /** 20% recovered-fraud commission, embedded at seal time (spec 9.3). */
    fun calculateCommission(fraudAmount: Double, embeddedAt: Long = System.currentTimeMillis()): Commission {
        val commission = fraudAmount * 0.20
        return Commission(
            fraudAmount = fraudAmount,
            verumCommission = commission,
            institutionKeeps = fraudAmount - commission,
            embeddedAt = embeddedAt
        )
    }
}
