package com.verumomnis.forensic.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing tests for the verified-package -> rules projection consumed by the
 * contradiction engine. Tolerant parsing: unknown or structured fields
 * (items/groups) must not break extraction of the flat lists.
 */
class DownloadedRulesTest {

    private val packageJson = """
        {
          "version": "1.2.3",
          "published_at": "2026-07-19T00:00:00Z",
          "rules": {
            "contradiction_patterns": [{"id": "CT01"}, {"id": "CT02"}],
            "fraud_keywords": [
              {"id": "FK01", "pairs": [["paid", "not paid"], ["valid", "invalid"]]},
              {"id": "FK03", "terms": [["co-occurring set"], "single-term"]},
              {"id": "FK04", "items": [{"role": "director", "check": "resolution"}]},
              {"id": "FK05", "groups": [["registered", "deregistered"]]}
            ],
            "behavioral_markers": [
              {"id": "BM01", "keywords": ["urgent", "act now"]},
              {"id": "BM02", "keywords": ["confidential"]},
              {"id": "BM11", "name": "Stonewalling", "keywords": ["unable to comment", "query is noted", "in due course"]},
              {"id": "BM12", "name": "Reverse victim", "keywords": ["injured party", "harassment of"], "produces": "CT49", "min_cooccur": 2}
            ],
            "serial_patterns": [{"id": "SP01"}],
            "case_configs": []
          }
        }
    """.trimIndent()

    @Test
    fun extractsPairsTermsMarkersAndCounts() {
        val rules = DownloadedRules.fromPackageJson(packageJson)

        assertEquals("1.2.3", rules.version)
        assertEquals("2026-07-19T00:00:00Z", rules.publishedAt)

        assertEquals(2, rules.fraudPairs.size)
        assertEquals(DownloadedRules.FraudPair("FK01", "paid", "not paid"), rules.fraudPairs[0])
        assertEquals(DownloadedRules.FraudPair("FK01", "valid", "invalid"), rules.fraudPairs[1])

        // Only flat string terms are extracted; co-occurrence arrays are skipped.
        assertEquals(listOf("single-term"), rules.fraudTerms)

        assertEquals(listOf("urgent", "act now", "confidential", "unable to comment", "query is noted", "in due course", "injured party", "harassment of"), rules.behavioralKeywords)

        // The seed's own BM01/BM02 are the built-in detectors' vocabulary and
        // are skipped; curated markers (BM11+) compile for execution.
        assertEquals(2, rules.behavioralMarkers.size)
        assertEquals(DownloadedRules.BehavioralMarker("BM11", "Stonewalling", listOf("unable to comment", "query is noted", "in due course")), rules.behavioralMarkers[0])
        assertEquals("CT49", rules.behavioralMarkers[1].produces)
        assertEquals(2, rules.behavioralMarkers[1].minCooccur)

        assertEquals(2, rules.contradictionPatternCount)
        assertEquals(1, rules.serialPatternCount)
    }

    @Test
    fun toleratesMissingOptionalSections() {
        val rules = DownloadedRules.fromPackageJson("""{"version": "2.0.0"}""")
        assertEquals("2.0.0", rules.version)
        assertTrue(rules.fraudPairs.isEmpty())
        assertTrue(rules.fraudTerms.isEmpty())
        assertTrue(rules.behavioralKeywords.isEmpty())
        assertTrue(rules.behavioralMarkers.isEmpty())
        assertEquals(0, rules.contradictionPatternCount)
        assertEquals(0, rules.serialPatternCount)
    }
}
