package com.verumomnis.forensic

import com.verumomnis.forensic.engine.TaxModule
import org.junit.Assert.assertEquals
import org.junit.Test

class TaxModuleTest {

    @Test
    fun companyTaxZaLargeBracket() {
        val result = TaxModule.calculateCompanyTaxZA(revenue = 1_000_000.0, expenses = 400_000.0)
        assertEquals(600_000.0, result.taxableIncome, 0.001)
        assertEquals(0.28, result.rate, 0.0001)
        assertEquals(168_000.0, result.taxLiability, 0.001)
        assertEquals("ZAR", result.currency)
    }

    @Test
    fun companyTaxZaSmallBusiness() {
        val result = TaxModule.calculateCompanyTaxZA(revenue = 90_000.0, expenses = 0.0)
        assertEquals(0.07, result.rate, 0.0001)
        assertEquals(6_300.0, result.taxLiability, 0.001)
    }

    @Test
    fun individualTaxZaSecondBracketWithRebate() {
        val result = TaxModule.calculateIndividualTaxZA(taxableIncome = 300_000.0, age = 40)
        // 42678 + (300000 - 237100) * 0.26 - 17235 = 41797
        assertEquals(41_797.0, result.taxLiability, 0.001)
    }

    @Test
    fun individualTaxZaSeniorRebate() {
        val young = TaxModule.calculateIndividualTaxZA(200_000.0, 30).taxLiability
        val senior = TaxModule.calculateIndividualTaxZA(200_000.0, 70).taxLiability
        assertEquals(200_000.0 * 0.18 - 17_235, young, 0.001)
        assertEquals(200_000.0 * 0.18 - 23_425, senior, 0.001)
    }

    @Test
    fun accountantFeeKznCorporateModerate() {
        val fee = TaxModule.estimateAccountantFee("ZA-KZN", "tax_return", "moderate", "corporate")
        assertEquals(16_000.0, fee.estimatedFee, 0.001)
        assertEquals(800.0, fee.baseRate, 0.001)
    }

    @Test
    fun verumServiceFeeIsHalfOfAccountantBenchmark() {
        val accountant = TaxModule.estimateAccountantFee("ZA-KZN", "tax_return", "moderate", "individual")
        val verum = TaxModule.verumServiceFee("ZA-KZN", "tax_return", "moderate", "individual")
        assertEquals(accountant.estimatedFee * 0.5, verum.estimatedFee, 0.001)
    }

    @Test
    fun commissionIsTwentyPercent() {
        val c = TaxModule.calculateCommission(500_000.0, embeddedAt = 0L)
        assertEquals(100_000.0, c.verumCommission, 0.001)
        assertEquals(400_000.0, c.institutionKeeps, 0.001)
    }
}
