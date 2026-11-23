package com.verumomnis.forensic

import com.verumomnis.forensic.engine.EvidenceDocument
import com.verumomnis.forensic.engine.FindingsJsonEmitter
import com.verumomnis.forensic.engine.ForensicService
import com.verumomnis.forensic.engine.JurisdictionService
import com.verumomnis.forensic.engine.ReportGenerator
import com.verumomnis.forensic.model.GpsRecord
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class JurisdictionSelectionTest {
    private val now = Instant.parse("2026-11-23T10:00:00Z")
    private val londonGps = GpsRecord(51.5074, -0.1278, timestamp = now.toString())

    @Test
    fun investigatorVenueOverridesGpsWithoutDiscardingLocationContext() {
        val selected = JurisdictionService.resolve(londonGps, "ZA-KZN")

        assertEquals("ZA-KZN", selected.jurisdiction)
        assertEquals("South Africa — KwaZulu-Natal", selected.venueLabel)
        assertEquals("INVESTIGATOR_SELECTED", selected.selectionBasis)
        assertEquals(londonGps, selected.gps)
    }

    @Test
    fun unsupportedVenueIsRejectedAndGpsFallbackRemainsExplicit() {
        assertTrue(JurisdictionService.supportedVenues().any { it.code == "UK" })
        assertTrue(runCatching { JurisdictionService.resolve(londonGps, "NOT-A-VENUE") }.isFailure)
        assertEquals("GPS_SUGGESTION", JurisdictionService.resolve(londonGps).selectionBasis)
        assertEquals("NO_GPS_FALLBACK", JurisdictionService.resolve(null).selectionBasis)
    }

    @Test
    fun selectedVenuePersistsIntoReportAndFindingsJson() {
        val document = EvidenceDocument(
            evidenceId = "DOC001",
            fileName = "venue-fixture.txt",
            type = "document",
            text = "Synthetic venue-selection test evidence.",
            sha512 = "a".repeat(128),
            gps = londonGps
        )
        val findings = ForensicService.scan(
            documents = listOf(document),
            now = now,
            selectedVenueCode = "ZA-KZN"
        ).findings
        val report = ReportGenerator.generate(findings, "VenueCase", now)

        assertEquals("ZA-KZN", findings.jurisdiction)
        assertEquals("INVESTIGATOR_SELECTED", findings.jurisdictionSource?.selectionBasis)
        assertEquals(londonGps, findings.jurisdictionSource?.gps)
        assertTrue(report.body.contains("Governing venue: South Africa — KwaZulu-Natal (ZA-KZN)"))
        assertTrue(report.body.contains("Investigator-selected; GPS is supporting context"))

        val serializedReport = Json.encodeToString(report)
        val restoredReport = Json.decodeFromString<com.verumomnis.forensic.model.ForensicReport>(serializedReport)
        assertEquals("ZA-KZN", restoredReport.jurisdictionSource?.jurisdiction)
        assertEquals("INVESTIGATOR_SELECTED", restoredReport.jurisdictionSource?.selectionBasis)
        assertEquals(londonGps, restoredReport.jurisdictionSource?.gps)

        val jsonDocument = Json.decodeFromString<FindingsJsonEmitter.FindingsJsonDocument>(
            FindingsJsonEmitter.emit(findings, "VenueCase", now)
        )
        assertEquals("1.1.0", jsonDocument.findings_json_version)
        assertEquals("ZA-KZN", jsonDocument.jurisdiction)
        assertEquals("South Africa — KwaZulu-Natal", jsonDocument.jurisdiction_source?.venueLabel)
        assertEquals("INVESTIGATOR_SELECTED", jsonDocument.jurisdiction_source?.selectionBasis)
        assertEquals(londonGps, jsonDocument.jurisdiction_source?.gps)
    }
}
