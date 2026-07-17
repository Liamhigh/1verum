package com.verumomnis.forensic

import com.verumomnis.forensic.model.JudicialDatabase
import com.verumomnis.forensic.ojrs.OjrsService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the current [OjrsService] singleton API.
 *
 * Replaces the previous stale test, which targeted a removed class-based
 * `OjrsService(enabled, client)` / `OjrsClient` injection API and no longer
 * compiled. Network search paths are exercised on-device; these tests cover
 * the deterministic, offline-verifiable parts of the service: database
 * selection per jurisdiction, citation/court extraction, and CourtListener
 * response parsing.
 */
class OjrsServiceTest {

    @Test
    fun `selectDatabases maps jurisdiction to its primary database`() {
        assertEquals(JudicialDatabase.SAFLII, OjrsService.selectDatabases("ZA").first())
        assertEquals(JudicialDatabase.SAFLII, OjrsService.selectDatabases("ZA-KZN").first())
        assertEquals(JudicialDatabase.COURTLISTENER, OjrsService.selectDatabases("US").first())
        assertEquals(JudicialDatabase.BAILII, OjrsService.selectDatabases("UK").first())
        assertEquals(JudicialDatabase.CANLII, OjrsService.selectDatabases("CA").first())
        assertEquals(JudicialDatabase.AUSTLII, OjrsService.selectDatabases("AU").first())
        assertEquals(JudicialDatabase.EUR_LEX, OjrsService.selectDatabases("EU").first())
        // Null jurisdiction searches the full database set.
        assertTrue(OjrsService.selectDatabases(null).size >= 6)
    }

    @Test
    fun `extractCitation finds citation pattern or falls back to title`() {
        assertEquals(
            "[2024] ZACC 12",
            OjrsService.extractCitation("Smith v Jones [2024] ZACC 12 (CC)")
        )
        // No citation pattern: falls back to the (60-char-truncated) title.
        assertEquals("No citation in this title", OjrsService.extractCitation("No citation in this title"))
    }

    @Test
    fun `extractCourtFromTitle recognises court names`() {
        assertEquals(
            "Constitutional Court",
            OjrsService.extractCourtFromTitle("Judgment of the Constitutional Court of South Africa")
        )
        assertEquals("High Court", OjrsService.extractCourtFromTitle("KwaZulu-Natal High Court, Durban"))
        assertEquals("Supreme Court", OjrsService.extractCourtFromTitle("Supreme Court of Appeal"))
        assertEquals("Unknown Court", OjrsService.extractCourtFromTitle("Some tribunal decision"))
    }

    @Test
    fun `parseCourtListenerResults maps api payload to judicial cases`() {
        val json = """
            {"count": 2, "results": [
              {"caseName": "Smith v. Jones", "court": "SCOTUS", "dateFiled": "2024-05-01", "absolute_url": "/opinion/1/smith/"},
              {"caseName": "Doe v. Roe", "court": "CA9", "dateFiled": "2023-11-20", "absolute_url": "/opinion/2/doe/"}
            ]}
        """.trimIndent()

        val cases = OjrsService.parseCourtListenerResults(json, 5)

        assertEquals(2, cases.size)
        val first = cases[0]
        assertEquals("Smith v. Jones", first.citation)
        assertEquals("SCOTUS", first.court)
        assertEquals("2024-05-01", first.judgmentDate)
        assertEquals("https://www.courtlistener.com/opinion/1/smith/", first.sourceUrl)
        assertEquals(JudicialDatabase.COURTLISTENER, first.database)
    }

    @Test
    fun `parseCourtListenerResults is honest on empty or malformed payloads`() {
        assertTrue(OjrsService.parseCourtListenerResults("{}", 5).isEmpty())
        assertTrue(OjrsService.parseCourtListenerResults("not json at all", 5).isEmpty())
    }
}
