package com.verumomnis.forensic

import com.verumomnis.forensic.engine.ContradictionExtractor
import com.verumomnis.forensic.engine.EntityExtractor
import com.verumomnis.forensic.engine.ForensicService
import com.verumomnis.forensic.model.ContradictionCategory
import com.verumomnis.forensic.model.GpsRecord
import com.verumomnis.forensic.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Exercises the B1 contradiction engine against AllFuels-style evidence to prove
 * multi-category extraction anchored to person / page / statute (build spec §9, §28).
 */
class ContradictionEngineTest {

    private val now = Instant.parse("2026-07-06T14:33:00Z")
    private val gps = GpsRecord(-30.7667, 30.4000, timestamp = now.toString())

    // Mirrors the AllFuels reference contradictions across several categories.
    private fun allFuelsDocs() = listOf(
        ForensicService.ingest(
            "DOC001", "cct_affidavit.txt", "affidavit",
            (
                "From: AllFuels\n" +
                    "Sworn before the Constitutional Court (CCT237/20, para 27): operators have no compensable goodwill.\n" +
                    "The MOU was never countersigned by AllFuels.\n" +
                    "We comply with PPA requirements in all franchise matters.\n" +
                    "Gary was grateful and agreed to exit gracefully.\n"
                ).toByteArray(),
            gps = gps
        ),
        ForensicService.ingest(
            "DOC002", "contemporaneous_record.txt", "email",
            (
                "From: AllFuels\n" +
                    "Date: 14 January 2026\n" +
                    "AllFuels demanded R3.8M extension fee and drafted a clause forcing Gary to forfeit goodwill.\n" +
                    "AllFuels collected rent under the binding MOU terms for 7 years.\n" +
                    "The lease was presented as binding to Gary.\n" +
                    "There was no Section 12B referral to Gary Highcock.\n" +
                    "Gary was non-committal and negotiated for more time; three executives removed his only witness.\n"
                ).toByteArray(),
            gps = gps
        )
    )

    @Test
    fun extractsMultipleCategories() {
        val contradictions = ContradictionExtractor.extract(allFuelsDocs(), now)
        val categories = contradictions.map { it.category }.toSet()
        assertTrue("Expected several contradictions, got ${contradictions.size}", contradictions.size >= 4)
        // Core AllFuels categories must be represented.
        assertTrue(categories.contains(ContradictionCategory.GOODWILL_VALUE))
        assertTrue(categories.contains(ContradictionCategory.SIGNATURE_STATUS))
        assertTrue(categories.contains(ContradictionCategory.SECTION_12B))
        assertTrue(categories.contains(ContradictionCategory.COERCION))
    }

    @Test
    fun everyContradictionAnchoredToPersonPageStatute() {
        val contradictions = ContradictionExtractor.extract(allFuelsDocs(), now)
        contradictions.forEach { c ->
            assertTrue("person", c.respondent.isNotBlank())
            assertTrue("page", c.claimA.page > 0 && c.claimB.page > 0)
            assertTrue("statute", c.applicableLaw.isNotEmpty())
            assertEquals("B1-ContradictionBrain", c.brainSource)
        }
    }

    @Test
    fun swornVsContemporaneousScoresHigh() {
        val contradictions = ContradictionExtractor.extract(allFuelsDocs(), now)
        // At least one CRITICAL/VERY_HIGH given sworn + contemporaneous + financial signals.
        assertTrue(contradictions.any { it.severity == Severity.CRITICAL || it.severity == Severity.VERY_HIGH })
    }

    @Test
    fun idsAreSequentialAndDeterministic() {
        val a = ContradictionExtractor.extract(allFuelsDocs(), now)
        val b = ContradictionExtractor.extract(allFuelsDocs(), now)
        assertEquals(a.map { it.contradictionId }, b.map { it.contradictionId })
        assertEquals("C-001", a.first().contradictionId)
    }

    @Test
    fun entityExtractorFindsPeopleAmountsDates() {
        val e = EntityExtractor.extractEntities(allFuelsDocs())
        assertTrue(e.amounts.any { it >= 3_800_000 })          // R3.8M
        assertTrue(e.dates.contains("14 January 2026"))
        assertTrue(e.people.any { it.contains("Gary") })
    }
}
