package com.verumomnis.forensic

import com.verumomnis.forensic.engine.ForensicService
import com.verumomnis.forensic.model.GpsRecord
import com.verumomnis.forensic.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MediaEvidenceTest {

    private val now = Instant.parse("2026-07-06T14:33:00Z")
    private val deviceGps = GpsRecord(-30.7667, 30.4000, timestamp = now.toString())
    private val exifGps = GpsRecord(-30.7669, 30.3998, timestamp = "2026-01-14T09:15:00Z")

    private fun photo(exif: GpsRecord? = exifGps) = ForensicService.ingestMedia(
        id = "MED001", fileName = "scene.jpg", kind = MediaKind.IMAGE,
        bytes = "photographic-evidence".toByteArray(), mimeType = "image/jpeg",
        capturedAt = now.toString(), deviceGps = deviceGps, exifGps = exif,
        exifTimestamp = "2026:01:14 09:15:00", width = 4032, height = 3024
    )

    @Test
    fun mediaIsHashedAndGpsAnchored() {
        val m = photo()
        assertEquals(128, m.sha512.length) // sealed
        assertTrue(m.exifGps != null)      // GPS anchored
    }

    @Test
    fun exhibitPrefersExifGpsAndRecordsSource() {
        val result = ForensicService.scan(emptyList(), emptyList(), listOf(photo()), now)
        val ex = result.findings.mediaExhibits.single()
        assertEquals("EX-001", ex.exhibitId)
        assertEquals(MediaKind.IMAGE, ex.kind)
        assertEquals("exif", ex.gpsSource)
        assertEquals(-30.7669, ex.gps!!.latitude, 1e-6)
        assertEquals("ZA-KZN", ex.jurisdiction)
        assertEquals(128, ex.sha512.length)
    }

    @Test
    fun fallsBackToDeviceGpsWhenNoExif() {
        val result = ForensicService.scan(emptyList(), emptyList(), listOf(photo(exif = null)), now)
        val ex = result.findings.mediaExhibits.single()
        assertEquals("device", ex.gpsSource)
        assertEquals(-30.7667, ex.gps!!.latitude, 1e-6)
    }

    @Test
    fun mediaIsSealedIntoTheEvidenceCorpus() {
        val a = ForensicService.scan(emptyList(), emptyList(), listOf(photo()), now).seal.sha512
        val b = ForensicService.scan(emptyList(), emptyList(), emptyList(), now).seal.sha512
        assertTrue("Adding media must change the sealed corpus hash", a != b)
    }

    @Test
    fun reportAnnexureListsExhibitWithGpsAndTimestamp() {
        val findings = ForensicService.scan(emptyList(), emptyList(), listOf(photo()), now).findings
        val report = com.verumomnis.forensic.engine.ReportGenerator.generate(findings, "AllFuels", now)
        assertTrue(report.mediaExhibits.isNotEmpty())
        assertTrue(report.body.contains("EVIDENCE EXHIBITS"))
        assertTrue(report.body.contains("scene.jpg"))
        assertTrue(report.body.contains("GPS:"))
        assertTrue(report.body.contains("source: exif"))
    }
}
