package com.verumomnis.forensic

import com.verumomnis.forensic.engine.AntiHarassmentMonitor
import com.verumomnis.forensic.engine.EmailModule
import com.verumomnis.forensic.engine.ForensicService
import com.verumomnis.forensic.engine.ReportGenerator
import com.verumomnis.forensic.model.GpsRecord
import com.verumomnis.forensic.model.HarassmentVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReportAndEmailTest {

    private val now = Instant.parse("2026-07-06T14:33:00Z")
    private val kznGps = GpsRecord(latitude = -30.7667, longitude = 30.4000, timestamp = now.toString())

    private fun findings() = ForensicService.scan(
        listOf(
            ForensicService.ingest(
                "DOC001", "email_marius_9mar2025.txt", "email",
                ("From: Marius Nortje\nDate: 9 March 2025\nThe petroleum deal fell through.").toByteArray(),
                gps = kznGps
            ),
            ForensicService.ingest(
                "DOC002", "email_admission_6apr2025.txt", "email",
                ("From: Marius Nortje\nDate: 6 April 2025\nKevin's Export proceeded with the deal. " +
                    "Fraud by the shareholders. R 500,000.").toByteArray(),
                gps = kznGps
            )
        ),
        now
    ).findings

    @Test
    fun reportAnchorsContradictionToPersonPageAndStatute() {
        val report = ReportGenerator.generate(findings(), "AllFuels", now)
        assertTrue(report.contradictions.isNotEmpty())
        val c = report.contradictions.first()
        // person
        assertEquals("Marius Nortje", c.respondent)
        // page anchor
        assertTrue(c.claimA.page > 0 && c.claimB.page > 0)
        // statute
        assertTrue(c.applicableLaw.isNotEmpty())
        // Rendered body contains all three anchors and is sealed. Council-ACCEPTED
        // contradictions appear in §3; unconfirmed ones only in the 3b appendix.
        if (c.councilStatus == "ACCEPTED") {
            assertTrue(report.body.contains("Respondent: Marius Nortje"))
            assertTrue(report.body.contains("Applicable law:"))
            assertTrue(report.body.contains("p${c.claimA.page}"))
        } else {
            assertTrue(report.body.contains("NOT CONFIRMED — EXCLUDED FROM FINDINGS"))
        }
        assertEquals(128, report.seal.sha512.length)
        assertTrue(report.seal.sealFooter().startsWith("VERUM OMNIS SEAL |"))
    }

    @Test
    fun offenceMatrixHasPersonAndLaw() {
        val report = ReportGenerator.generate(findings(), "AllFuels", now)
        // Doctrine: the offence matrix contains council-ACCEPTED findings only.
        val accepted = report.contradictions.filter { it.councilStatus == "ACCEPTED" }
        assertEquals(accepted.size, report.offenceMatrix.size)
        if (accepted.isNotEmpty()) {
            val row = report.offenceMatrix.first()
            assertEquals("Marius Nortje", row.person)
            assertTrue(row.applicableLaw.isNotEmpty())
            assertTrue(row.evidenceAnchor.contains("p"))
        }
    }

    @Test
    fun emailIsSealedAsPdf() {
        val monitor = AntiHarassmentMonitor()
        val draft = EmailModule.draft("clerk@court.gov.za", "Sealed report", listOf("See attached."), "VO-REF-1")
        val sealed = EmailModule.sealAndSend(draft, monitor, now)
        assertTrue(sealed.sealedPdfFile.endsWith(".pdf"))
        assertTrue(sealed.sealedPdfFile.contains(sealed.seal.shortcode))
        assertEquals(128, sealed.seal.sha512.length)
        assertTrue(sealed.delivered)
        assertEquals(HarassmentVerdict.ALLOW, sealed.assessment.verdict)
        assertEquals(1, monitor.auditTrail().size)
    }

    @Test
    fun frequencyLimitTriggersWarnAfterThreeSends() {
        val monitor = AntiHarassmentMonitor()
        val r = "target@example.com"
        var t = now
        val verdicts = (1..4).map {
            val d = EmailModule.draft(r, "Report", emptyList(), "VO-REF-1")
            val res = EmailModule.sealAndSend(d, monitor, t).assessment.verdict
            t = t.plusSeconds(60)
            res
        }
        // First three ALLOW, fourth (>3 in 24h) escalates to WARN.
        assertEquals(HarassmentVerdict.ALLOW, verdicts[0])
        assertEquals(HarassmentVerdict.WARN, verdicts[3])
    }

    @Test
    fun repeatSameReportSameRecipientEventuallyBlocks() {
        val monitor = AntiHarassmentMonitor()
        val r = "target@example.com"
        var t = now
        var last = HarassmentVerdict.ALLOW
        repeat(6) {
            val d = EmailModule.draft(r, "Report", emptyList(), "VO-REF-1")
            last = EmailModule.sealAndSend(d, monitor, t).assessment.verdict
            t = t.plusSeconds(60)
        }
        assertEquals(HarassmentVerdict.BLOCK, last)
    }

    @Test
    fun threateningContentIsBlockedButStillSealed() {
        val monitor = AntiHarassmentMonitor()
        val draft = EmailModule.draft("target@example.com", "You will regret this", listOf("or else"), "VO-REF-2")
        val sealed = EmailModule.sealAndSend(draft, monitor, now)
        assertEquals(HarassmentVerdict.BLOCK, sealed.assessment.verdict)
        assertFalse(sealed.delivered)
        assertNotNull(sealed.seal)
        assertTrue(sealed.assessment.reasons.any { it.contains("threatening", ignoreCase = true) })
    }

    @Test
    fun noContactRecipientIsBlocked() {
        val monitor = AntiHarassmentMonitor()
        monitor.markNoContact("stop@example.com")
        val draft = EmailModule.draft("stop@example.com", "Report", emptyList(), "VO-REF-3")
        val sealed = EmailModule.sealAndSend(draft, monitor, now)
        assertEquals(HarassmentVerdict.BLOCK, sealed.assessment.verdict)
    }
}
