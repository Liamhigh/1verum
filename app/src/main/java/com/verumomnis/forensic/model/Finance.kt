package com.verumomnis.forensic.model

import kotlinx.serialization.Serializable

@Serializable
data class TaxResult(
    val taxableIncome: Double,
    val rate: Double,
    val taxLiability: Double,
    val currency: String,
    val jurisdiction: String
)

@Serializable
data class FeeEstimate(
    val estimatedFee: Double,
    val baseRate: Double,
    val hours: Double,
    val jurisdiction: String
)

@Serializable
data class Commission(
    val fraudAmount: Double,
    val verumCommission: Double,
    val institutionKeeps: Double,
    val embeddedAt: Long
)

@Serializable
data class FinancialAnalysis(
    val companyTax: TaxResult? = null,
    val commission: Commission? = null,
    val flaggedAnomalies: List<String> = emptyList()
)
