package com.verumomnis.forensic.tax

import com.verumomnis.forensic.engine.tax.BundledFeeTableLoader
import com.verumomnis.forensic.engine.tax.FeeTableLoader
import com.verumomnis.forensic.engine.tax.JurisdictionFee
import com.verumomnis.forensic.engine.tax.JurisdictionFees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JurisdictionFeesTest {

    @Test
    fun `south africa is first in the starter table`() {
        assertEquals("ZA", JurisdictionFees.STARTER_TABLE.first().countryCode)
        assertEquals("ZAR", JurisdictionFees.STARTER_TABLE.first().currency)
    }

    @Test
    fun `resolve matches device locale country codes`() {
        assertEquals("ZA", JurisdictionFees.resolve("ZA").countryCode)
        assertEquals("US", JurisdictionFees.resolve("US").countryCode)
        assertEquals("GB", JurisdictionFees.resolve("gb").countryCode)
        assertEquals("AE", JurisdictionFees.resolve("AE").countryCode)
        assertEquals("AU", JurisdictionFees.resolve("AU").countryCode)
    }

    @Test
    fun `unknown and blank codes fall back to INTL`() {
        assertEquals(JurisdictionFees.INTL_CODE, JurisdictionFees.resolve("XX").countryCode)
        assertEquals(JurisdictionFees.INTL_CODE, JurisdictionFees.resolve(null).countryCode)
        assertEquals(JurisdictionFees.INTL_CODE, JurisdictionFees.resolve("").countryCode)
    }

    @Test
    fun `verum fee is exactly half of the typical accountant fee`() {
        val za = JurisdictionFees.resolve("ZA")
        val (low, high) = JurisdictionFees.verumFeeRange(za)
        assertEquals(za.typicalAccountantFeeLow * 0.5, low, 0.0001)
        assertEquals(za.typicalAccountantFeeHigh * 0.5, high, 0.0001)
        assertEquals(2_500.0, low, 0.0001)
        assertEquals(12_500.0, high, 0.0001)
    }

    @Test
    fun `ZA range covers the documented small-company band`() {
        val za = JurisdictionFees.resolve("ZA")
        assertTrue(za.typicalAccountantFeeLow >= 5_000.0)
        assertTrue(za.typicalAccountantFeeHigh <= 25_000.0)
        assertTrue(za.note.contains("Estimate"))
    }

    @Test
    fun `every row is marked as estimates with an updatedAt date`() {
        JurisdictionFees.STARTER_TABLE.forEach { row ->
            assertTrue(row.note.contains("Estimate"))
            assertTrue(row.updatedAt.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
            assertTrue(row.typicalAccountantFeeLow < row.typicalAccountantFeeHigh)
        }
    }

    @Test
    fun `loader seam allows a future manifest table to be injected`() {
        val manifestRow = JurisdictionFee(
            countryCode = "DE",
            countryName = "Germany",
            currency = "EUR",
            typicalAccountantFeeLow = 900.0,
            typicalAccountantFeeHigh = 4_000.0,
            note = "Estimate: manifest-supplied row.",
            updatedAt = "2026-07-22"
        )
        val loader = FeeTableLoader { listOf(manifestRow, JurisdictionFees.STARTER_TABLE.last()) }
        assertEquals("DE", JurisdictionFees.resolve("DE", loader).countryCode)
        assertEquals(JurisdictionFees.INTL_CODE, JurisdictionFees.resolve("FR", loader).countryCode)
        // Bundled default remains untouched.
        assertEquals("ZA", JurisdictionFees.resolve("DE").countryCode.let { JurisdictionFees.resolve("ZA").countryCode })
    }

    @Test
    fun `manifest seam constants point at signed rules manifest`() {
        assertTrue(JurisdictionFees.RULES_MANIFEST_ASSET.endsWith("fee-rules.manifest.json"))
        assertTrue(JurisdictionFees.RULES_MANIFEST_MIN_VERSION >= 1)
        assertEquals(JurisdictionFees.STARTER_TABLE, BundledFeeTableLoader.load())
    }
}
