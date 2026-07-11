package com.verumomnis.forensic

import com.verumomnis.forensic.engine.ForensicService
import com.verumomnis.forensic.model.GpsRecord
import com.verumomnis.forensic.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NineBrainEngineTest {

    private val now = Instant.parse("2026-07-06T14:33:00Z")
    private val kznGps = GpsRecord(latitude = -30.7667, longitude = 30.4000, timestamp = now.toString())

    private fun sampleDocs() = listOf(
        ForensicService.ingest(
            "DOC001", "email_marius_9mar2025.txt", "email",
            ("Date: 9 March 2025\nRegarding the petroleum export: the deal fell through. " +
                "No payment was outstanding.").toByteArray(),
            gps = kznGps
        ),
        ForensicService.ingest(
            "DOC002", "email_admission_6apr2025.txt", "email",
            ("Date: 6 April 2025\nKevin's Export proceeded with the deal. " +
                "This concerns fraud by the company shareholders. Amount R 500,000.").toByteArray(),
            gps = kznGps
        )
    )

    @Test
    fun detectsCriticalContradiction() {
        val result = ForensicService.scan(sampleDocs(), now)
        val contradictions = result.findings.contradictions
        assertTrue("Expected at least one contradiction", contradictions.isNotEmpty())
        val c = contradictions.first()
        assertEquals(Severity.CRITICAL, c.severity)
        assertEquals("C-001", c.contradictionId)
        assertTrue(c.claimA.text.contains("fell through", ignoreCase = true))
        assertTrue(c.claimB.text.contains("proceeded", ignoreCase = true))
        assertEquals("DOC001", c.claimA.evidenceId)
        assertEquals("DOC002", c.claimB.evidenceId)
    }

    @Test
    fun reconstructsTimelineFromDates() {
        val result = ForensicService.scan(sampleDocs(), now)
        val dates = result.findings.timeline.map { it.dateTime }
        assertTrue(dates.contains("9 March 2025"))
        assertTrue(dates.contains("6 April 2025"))
    }

    @Test
    fun mapsLegalFrameworkAndJurisdiction() {
        val result = ForensicService.scan(sampleDocs(), now)
        assertEquals("ZA-KZN", result.findings.jurisdiction)
        val mappings = result.findings.legalMappings
        assertTrue(mappings.any { it.contains("Petroleum Products Act") })
        assertTrue(mappings.any { it.contains("fraud", ignoreCase = true) })
        assertTrue(mappings.any { it.contains("Companies Act") })
    }

    @Test
    fun detectsDuplicateAmountAnomaly() {
        val docs = listOf(
            ForensicService.ingest("DOC001", "a.txt", "doc", "Invoice total R 250,000 paid.".toByteArray()),
            ForensicService.ingest("DOC002", "b.txt", "doc", "Second invoice also R 250,000 paid.".toByteArray())
        )
        val result = ForensicService.scan(docs, now)
        val anomalies = result.findings.financial?.flaggedAnomalies ?: emptyList()
        assertTrue("Expected duplicate-amount anomaly", anomalies.any { it.contains("Duplicate amount") })
    }

    @Test
    fun everyEvidenceAtomRecordsGpsAndHash() {
        val result = ForensicService.scan(sampleDocs(), now)
        assertTrue(result.findings.evidenceAtoms.isNotEmpty())
        result.findings.evidenceAtoms.forEach { atom ->
            assertEquals(128, atom.sha512.length)
            assertTrue("Atom ${atom.evidenceId} must record GPS", atom.gps != null)
            assertEquals(-30.7667, atom.gps!!.latitude, 0.0001)
        }
    }

    @Test
    fun determinismSameEvidenceSameSeal() {
        val a = ForensicService.scan(sampleDocs(), now)
        val b = ForensicService.scan(sampleDocs(), now)
        assertEquals(a.seal.sha512, b.seal.sha512)
        assertEquals(a.findings.contradictions.size, b.findings.contradictions.size)
    }
}
