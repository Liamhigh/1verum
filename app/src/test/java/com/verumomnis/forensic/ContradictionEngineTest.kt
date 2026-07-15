package com.verumomnis.forensic

import com.verumomnis.forensic.engine.ContradictionExtractor
import com.verumomnis.forensic.engine.EntityExtractor
import com.verumomnis.forensic.engine.ForensicService
import com.verumomnis.forensic.engine.contradiction.EngineContradictionType
import com.verumomnis.forensic.engine.contradiction.EngineSeverity
import com.verumomnis.forensic.engine.contradiction.VerumContradictionEngine
import com.verumomnis.forensic.engine.contradiction.getCaseConfig
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

    // ==================== v5.3.1c ported detector tests ====================

    @Test
    fun hybridEngineDetectsAcknowledgeThenDeny() {
        val engine = VerumContradictionEngine(caseId = "VO-TEST-001", caseName = "standardbank")
        val report = engine.processFromTexts(listOf(
            "From: Marius Nortje. I deny that Kevin completed the deal on 13 March 2025.",
            "From: Marius Nortje. I admit that Kevin completed the deal on 13 March 2025."
        ), listOf("denial.txt", "admission.txt"))
        assertTrue(
            "Expected ACKNOWLEDGE_THEN_DENY",
            report.contradictions.any { it.type == EngineContradictionType.ACKNOWLEDGE_THEN_DENY }
        )
    }

    @Test
    fun hybridEngineDetectsGoodwillForfeitureSwindle() {
        val engine = VerumContradictionEngine(caseId = "VO-TEST-002", caseName = "allfuels")
        val report = engine.processFromTexts(listOf(
            "AllFuels demanded that the operator forfeit all goodwill upon termination.",
            "AllFuels demanded R150,000 from the operator personally to take over the company."
        ), listOf("forfeit.txt", "buyout.txt"))
        assertTrue(
            "Expected GOODWILL_FORFEITURE_SWINDLE",
            report.contradictions.any { it.type == EngineContradictionType.GOODWILL_FORFEITURE_SWINDLE }
        )
    }

    @Test
    fun hybridEngineDetectsFabricatedDecoyEvidence() {
        val engine = VerumContradictionEngine(caseId = "VO-TEST-003", caseName = "standardbank")
        val report = engine.processFromTexts(listOf(
            "From: Kevin Lappeman. Email containing forged WhatsApp messages and fabricated $28,000 liability."
        ), listOf("forged.txt"))
        assertTrue(
            "Expected FABRICATED_DECOY_EVIDENCE",
            report.contradictions.any { it.type == EngineContradictionType.FABRICATED_DECOY_EVIDENCE }
        )
    }

    @Test
    fun hybridEngineDetectsSpoliation() {
        val engine = VerumContradictionEngine(caseId = "VO-TEST-004", caseName = "standardbank")
        val report = engine.processFromTexts(listOf(
            "From: Custodian. We deleted the messages and wiped the device to conceal evidence."
        ), listOf("spoliation.txt"))
        assertTrue(
            "Expected SPOLIATION_OF_EVIDENCE",
            report.contradictions.any { it.type == EngineContradictionType.SPOLIATION_OF_EVIDENCE }
        )
    }

    @Test
    fun hybridEngineDetectsDefamationThreat() {
        val engine = VerumContradictionEngine(caseId = "VO-TEST-005", caseName = "standardbank")
        val report = engine.processFromTexts(listOf(
            "From: Southbridge Legal. Grounds for legal action including defamation, fraud and reputational harm. Govern yourself accordingly."
        ), listOf("threat.txt"))
        assertTrue(
            "Expected DEFAMATION_THREAT",
            report.contradictions.any { it.type == EngineContradictionType.DEFAMATION_THREAT }
        )
    }

    @Test
    fun standardbankSampleProducesCriticalOrHigh() {
        val engine = VerumContradictionEngine(caseId = "VO-SB-TEST", caseName = "standardbank")
        val evidenceText = javaClass.classLoader
            ?.getResourceAsStream("evidence/standardbank_evidence.txt")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw IllegalStateException("Missing test evidence")
        val report = engine.processFromTexts(
            evidenceText.lines().filter { it.isNotBlank() },
            evidenceText.lines().filter { it.isNotBlank() }.mapIndexed { i, _ -> "SB-${i + 1}" }
        )
        assertTrue(
            "Expected at least 5 contradictions from Standard Bank sample, got ${report.contradictions.size}",
            report.contradictions.size >= 5
        )
        assertTrue(
            "Expected at least one CRITICAL or HIGH severity contradiction",
            report.contradictions.any { it.severity == EngineSeverity.VERY_HIGH || it.severity == EngineSeverity.HIGH }
        )
        val types = report.contradictions.map { it.type }.toSet()
        assertTrue(
            "Expected ACKNOWLEDGE_THEN_DENY or FABRICATED_DECOY_EVIDENCE in types: $types",
            types.contains(EngineContradictionType.ACKNOWLEDGE_THEN_DENY) ||
                types.contains(EngineContradictionType.FABRICATED_DECOY_EVIDENCE)
        )
    }

    @Test
    fun caseConfigRoutings() {
        val standardBank = getCaseConfig("standardbank")
        assertEquals("Standard Bank Master Bundle", standardBank.name)
        assertTrue(standardBank.entityKeywords.contains("Marius Nortje"))

        val allfuels111 = getCaseConfig("allfuels-111")
        assertEquals("AllFuels 111-Contradiction Bundle", allfuels111.name)
        assertTrue(allfuels111.legalSubjects.containsKey("Coercion"))

        val defaultAllfuels = getCaseConfig("unknown")
        assertEquals("AllFuels Energy (Pty) Ltd", defaultAllfuels.name)
    }
}
