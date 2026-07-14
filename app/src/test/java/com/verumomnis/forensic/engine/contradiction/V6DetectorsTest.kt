package com.verumomnis.forensic.engine.contradiction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verum Omnis — v6 detector + statute-chain tests (Kotlin).
 * Status: RATIFIED — BINDING (founder directive, 2026-07-14).
 * Validates the forensic chain:
 *   extraction -> contradiction -> PERSON -> PAGE -> LOCAL LAW (statute)
 */
class V6DetectorsTest {

    private val actors = listOf(
        V6Detectors.ActorLexiconEntry("Liam Highstead", listOf("Liam", "Highstead"), "complainant"),
        V6Detectors.ActorLexiconEntry("Green Sky", listOf("GreenSky", "green sky"), "institution"),
    )

    private val swornA = V6Detectors.V6TextChunk(
        "In my sworn affidavit, I Liam Highstead solemnly declare that the rental payment deposit funds were never paid into the account.",
        "case126.pdf", 3, "sha:a",
    )
    private val swornB = V6Detectors.V6TextChunk(
        "In the sworn affidavit the deponent Green Sky solemnly declares that the rental payment deposit funds were paid into the account.",
        "case126.pdf", 12, "sha:b",
    )

    @Before
    fun reset() = V6Detectors.resetCounter()

    // -- word-boundary precision ---------------------------------------------

    @Test
    fun leaseDoesNotFireInsidePlease() {
        assertFalse(V6Detectors.containsTerm("Please find attached.", "lease"))
    }

    @Test
    fun leaseFiresAsWholeWord() {
        assertTrue(V6Detectors.containsTerm("The lease was signed.", "lease"))
    }

    // -- detectors fire on anchored text --------------------------------------

    @Test
    fun swornVsSwornFiresOnOpposingSwornStatements() {
        val recs = V6Detectors.detectSwornVsSworn(listOf(swornA, swornB), actors)
        assertTrue("did not fire", recs.isNotEmpty())
        assertEquals("SWORN_VS_SWORN", recs[0].type)
        assertNotEquals(recs[0].propositionAActor, recs[0].propositionBActor)
    }

    @Test
    fun deviceAttributionMultiWordEntityRegression() {
        val decl = V6Detectors.V6TextChunk(
            "\u201cI have a PTY called South Coast Aquaculture\u2026\u201d",
            "case126.pdf", 5, "sha:d",
        )
        val act = V6Detectors.V6TextChunk(
            "The attempted Gmail intrusion originated from a device labeled \u201cSCAQUACULTURE\u201d on the network.",
            "case126.pdf", 5, "sha:e",
        )
        val recs = V6Detectors.detectDeviceAttributionChain(listOf(decl, act), actors)
        assertTrue("multi-word entity not linked to device", recs.isNotEmpty())
        assertTrue(recs[0].propositionBActor.contains("SCAQUACULTURE"))
    }

    @Test
    fun criminalChargeAsLeverageFires() {
        val charge = V6Detectors.V6TextChunk(
            "Green Sky opened a criminal case against Liam Highstead, case number CAS 142/07/2025, on 12 Jul 2025. " +
                "This was done to pressure Liam Highstead to withdraw the application and back down.",
            "case126.pdf", 20, "sha:c",
        )
        val recs = V6Detectors.detectCriminalChargeAsLeverage(listOf(charge), actors)
        assertTrue("did not fire", recs.isNotEmpty())
        assertEquals("CRIMINAL_CHARGE_AS_LEVERAGE", recs[0].type)
    }

    @Test
    fun agreementIsNotFlagged() {
        val a = V6Detectors.V6TextChunk("In my sworn affidavit I Liam Highstead declare the funds were paid on time.", "d.pdf", 1, "sha:x")
        val b = V6Detectors.V6TextChunk("In my sworn affidavit Green Sky confirms the funds were paid on time.", "d.pdf", 2, "sha:y")
        assertTrue(V6Detectors.detectSwornVsSworn(listOf(a, b), actors).isEmpty())
    }

    // -- forensic chain: person + page + local law ----------------------------

    @Test
    fun chainClosesPersonPageLocalLaw() {
        val recs = V6Detectors.runV6Detectors(listOf(swornA, swornB), actors, "ZA")
        assertTrue(recs.isNotEmpty())
        for (r in recs) {
            assertTrue(r.propositionAActor.isNotEmpty() || r.propositionBActor.isNotEmpty())
            assertTrue(r.sourcePage >= 1)
            val lh = r.legalHypothesis
            assertNotNull(lh)
            assertEquals("ZA", lh!!.jurisdiction)
            assertTrue("no local statutes", lh.localStatutes.isNotEmpty())
            assertTrue(lh.isHypothesis)
            assertTrue(lh.requiresHumanReview)
        }
    }

    @Test
    fun swornMapsToPerjuryStatute() {
        val recs = V6Detectors.runV6Detectors(listOf(swornA, swornB), actors, "ZA")
            .filter { it.type == "SWORN_VS_SWORN" }
        assertTrue(recs.isNotEmpty())
        val instruments = recs[0].legalHypothesis!!.localStatutes.map { it.instrument }
        assertTrue(instruments.any { it.contains("Justices of the Peace") })
    }

    // -- statute map ----------------------------------------------------------

    @Test
    fun zaThreeTypesHaveCitations() {
        for (t in listOf(
            StatuteMap.TYPE_SWORN_VS_SWORN,
            StatuteMap.TYPE_DEVICE_ATTRIBUTION_CHAIN,
            StatuteMap.TYPE_CRIMINAL_CHARGE_AS_LEVERAGE,
        )) {
            assertTrue("no ZA statutes for $t", StatuteMap.statutesForType(t, "ZA").isNotEmpty())
        }
    }

    @Test
    fun deviceChainCitesCybercrimesAct() {
        assertTrue(
            StatuteMap.statutesForType(StatuteMap.TYPE_DEVICE_ATTRIBUTION_CHAIN, "ZA")
                .any { it.instrument.contains("Cybercrimes") },
        )
    }

    @Test
    fun leverageCitesCyberExtortion() {
        assertTrue(
            StatuteMap.statutesForType(StatuteMap.TYPE_CRIMINAL_CHARGE_AS_LEVERAGE, "ZA")
                .any { it.citation == "s 10" },
        )
    }

    @Test
    fun jurisdictionAliasesAndGenericFallback() {
        assertEquals("ZA", StatuteMap.normaliseJurisdiction("south africa"))
        assertEquals("GENERIC", StatuteMap.normaliseJurisdiction("nowhere"))
        assertEquals("ZA", StatuteMap.normaliseJurisdiction(null))
        assertTrue(StatuteMap.statutesForType("NON_EXISTENT", "ZA").isEmpty())
    }
}
