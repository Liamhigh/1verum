package com.verumomnis.forensic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.verumomnis.forensic.crypto.Sha512
import com.verumomnis.forensic.engine.EvidenceDocument
import com.verumomnis.forensic.engine.ForensicService
import com.verumomnis.forensic.engine.PdfBoxTextExtractor
import com.verumomnis.forensic.engine.ReportGenerator
import com.verumomnis.forensic.model.GuardianViolationType
import com.verumomnis.forensic.pdf.SealedPageRenderer
import com.verumomnis.forensic.pdf.SealedPdfContent
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Opt-in local replay. Private evidence is supplied by a JVM property, never
 * checked into fixtures, printed to logs, or sent to a provider.
 * Synthetic Guardian regression tests remain mandatory without that property.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuardianEvidenceReplayTest {
    @Test
    fun suppliedPdfCompletesNativeScanReportAndPageRenderingWithoutFalseWeaponizationStop() {
        val path = System.getProperty("verum.evidencePdf")
        assumeNotNull(path)
        val source = File(requireNotNull(path))
        assertTrue("Replay input must be a readable local PDF.", source.isFile && source.canRead())
        val before = Sha512.hash(source.readBytes())
        val text = PdfBoxTextExtractor().extractText(source)
        assertTrue("Production PDF extraction must return substantial evidence text.", text.length > 1000)
        assertTrue("This replay must exercise the original substring false positive.",
            text.contains("ied", ignoreCase = true))

        val now = Instant.parse("2026-11-21T19:51:51Z")
        // Match the production preview -> EvidenceDocument handoff: preserve the
        // ORIGINAL PDF hash, not a hash of the derived text. On-device ML Kit OCR
        // is deliberately outside this text-layer replay's verification claim.
        val document = EvidenceDocument(
            evidenceId = "DOC001",
            fileName = "local-evidence.pdf",
            type = "pdf",
            text = text,
            sha512 = before
        )
        val result = ForensicService.scan(listOf(document), now = now)
        val guardian = result.findings.guardian
        assertNotNull(guardian)
        assertFalse("Ordinary document words must not trigger the weaponization stop.",
            guardian!!.hardStopRequired)
        assertTrue("No whole-term weapon keyword was present in this reproduction.",
            guardian.violations.none { it.type == GuardianViolationType.ARTICLE_X_WEAPONIZATION })
        val report = ReportGenerator.generate(result.findings, "LocalReplay", now)
        assertTrue(report.body.isNotBlank())
        assertFalse(report.body.contains("ARTICLE X HARD STOP ACTIVE"))
        assertEquals(result.findings.contradictions.size, report.contradictions.size)
        assertEquals("The original input must remain unchanged.", before, Sha512.hash(source.readBytes()))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val watermark = requireNotNull(
            BitmapFactory.decodeResource(context.resources, R.drawable.watermark_portrait)
        )
        val content = SealedPdfContent.fromReport(report)
        val pages = content.paginate()
        assertTrue(pages.isNotEmpty())
        val bitmap = Bitmap.createBitmap(
            SealedPageRenderer.PAGE_WIDTH, SealedPageRenderer.PAGE_HEIGHT, Bitmap.Config.ARGB_8888
        )
        SealedPageRenderer.drawPage(Canvas(bitmap), pages.first(), watermark)
        System.getProperty("verum.replayOutputDir")?.let { directory ->
            val output = File(directory).apply { mkdirs() }
            File(output, "replayed-report-page.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            File(output, "replay-summary.json").writeText(
                """{"extractedCharacters":${text.length},"contradictionIndicators":${report.contradictions.size},"guardianHardStop":${guardian.hardStopRequired},"reportPages":${pages.size},"originalPreserved":true,"ingressMode":"pdf-text-layer-only","deviceOcrTested":false,"nativePdfSerializationTested":false}"""
            )
        }
        bitmap.recycle()
        watermark.recycle()
    }
}
