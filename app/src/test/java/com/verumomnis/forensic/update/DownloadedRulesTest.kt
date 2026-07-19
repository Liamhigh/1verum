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
              {"id": "BM02", "keywords": ["confidential"]}
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

        assertEquals(listOf("urgent", "act now", "confidential"), rules.behavioralKeywords)

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
        assertEquals(0, rules.contradictionPatternCount)
        assertEquals(0, rules.serialPatternCount)
    }
}
