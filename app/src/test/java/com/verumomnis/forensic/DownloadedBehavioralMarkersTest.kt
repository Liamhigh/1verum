package com.verumomnis.forensic

import com.verumomnis.forensic.engine.contradiction.ContradictionDetectors
import com.verumomnis.forensic.engine.contradiction.EngineClaim
import com.verumomnis.forensic.engine.contradiction.EngineContradictionType
import com.verumomnis.forensic.engine.contradiction.EngineSeverity
import com.verumomnis.forensic.update.DownloadedRules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parity with the website engine's signed-rule behavioural markers: curated
 * `behavioral_markers` rules fire when enough distinct keywords co-occur in
 * one claim; the seed's own BM01-BM10 never reach this detector (filtered at
 * parse time); every finding carries the doctrine wording.
 */
class DownloadedBehavioralMarkersTest {

    private fun rulesWith(markers: List<DownloadedRules.BehavioralMarker>) = DownloadedRules(
        version = "1.3.0",
        publishedAt = "",
        fraudPairs = emptyList(),
        fraudTerms = emptyList(),
        behavioralKeywords = emptyList(),
        contradictionPatternCount = 0,
        serialPatternCount = 0,
        behavioralMarkers = markers
    )

    private fun claim(id: String, value: String) = EngineClaim(
        id = id, subject = "S", predicate = "stated", value = value, actor = "A"
    )

    @After
    fun resetProvider() {
        ContradictionDetectors.downloadedRulesProvider = { null }
    }

    @Test
    fun noPackageMeansNoFindings() {
        ContradictionDetectors.downloadedRulesProvider = { null }
        assertTrue(ContradictionDetectors.detectDownloadedBehavioralMarkers(
            listOf(claim("1", "We are unable to comment and your query is noted."))
        ).isEmpty())
    }

    @Test
    fun markerFiresOnDistinctKeywordCooccurrence() {
        ContradictionDetectors.downloadedRulesProvider = {
            rulesWith(listOf(DownloadedRules.BehavioralMarker("BM11", "Stonewalling", listOf("unable to comment", "query is noted", "in due course"))))
        }
        val found = ContradictionDetectors.detectDownloadedBehavioralMarkers(
            listOf(
                claim("1", "We are unable to comment and your query is noted."),
                claim("2", "Nothing of note here.")
            )
        )
        assertEquals(1, found.size)
        val f = found[0]
        assertEquals(EngineContradictionType.BEHAVIORAL, f.type)
        assertEquals(EngineSeverity.MODERATE, f.severity)
        assertEquals("DOWNLOADED_MARKER_BM11", f.logicalPattern.patternType)
        assertTrue(f.logicalPattern.patternDescription.contains("behavioural marker \"Stonewalling\" matched 2 keyword(s)"))
        assertTrue(f.logicalPattern.patternDescription.contains("rules v1.3.0"))
        assertTrue(f.logicalPattern.patternDescription.contains("for human review, not a determination"))
        assertTrue(f.logicalPattern.supportingFacts.first().contains("unable to comment"))
    }

    @Test
    fun oneKeywordOfMultiKeywordMarkerNeverFires() {
        ContradictionDetectors.downloadedRulesProvider = {
            rulesWith(listOf(DownloadedRules.BehavioralMarker("BM11", "Stonewalling", listOf("unable to comment", "query is noted"))))
        }
        assertTrue(ContradictionDetectors.detectDownloadedBehavioralMarkers(
            listOf(claim("1", "Your query is noted."))
        ).isEmpty())
    }

    @Test
    fun singleKeywordMarkerFiresOnItsKeyword() {
        ContradictionDetectors.downloadedRulesProvider = {
            rulesWith(listOf(DownloadedRules.BehavioralMarker("BM12", "Urgency push", listOf("act now"))))
        }
        assertEquals(1, ContradictionDetectors.detectDownloadedBehavioralMarkers(
            listOf(claim("1", "Act now to secure your allocation."))
        ).size)
    }

    @Test
    fun producesIsNamedInTheDescription() {
        ContradictionDetectors.downloadedRulesProvider = {
            rulesWith(listOf(DownloadedRules.BehavioralMarker("BM13", "Reverse victim", listOf("injured party", "harassment of"), produces = "CT49")))
        }
        val found = ContradictionDetectors.detectDownloadedBehavioralMarkers(
            listOf(claim("1", "We are the injured party given your harassment of our team."))
        )
        assertEquals(1, found.size)
        assertTrue(found[0].logicalPattern.patternDescription.contains("(labels CT49)"))
    }

    @Test
    fun wordBoundariesRespected() {
        ContradictionDetectors.downloadedRulesProvider = {
            rulesWith(listOf(DownloadedRules.BehavioralMarker("BM14", "Risk", listOf("risk free", "guaranteed"))))
        }
        // "risk freedom" is not "risk free"; "guaranteed" alone is below threshold.
        assertTrue(ContradictionDetectors.detectDownloadedBehavioralMarkers(
            listOf(claim("1", "This is a risk freedom guaranteed."))
        ).isEmpty())
    }

    @Test
    fun detectionIsDeterministic() {
        ContradictionDetectors.downloadedRulesProvider = {
            rulesWith(listOf(DownloadedRules.BehavioralMarker("BM11", "Stonewalling", listOf("unable to comment", "query is noted"))))
        }
        val claims = listOf(claim("1", "We are unable to comment and your query is noted."))
        val a = ContradictionDetectors.detectDownloadedBehavioralMarkers(claims)
        val b = ContradictionDetectors.detectDownloadedBehavioralMarkers(claims)
        // contradictionId carries a global counter, so compare content, not IDs.
        assertEquals(a.map { it.logicalPattern.toString() }, b.map { it.logicalPattern.toString() })
    }
}
