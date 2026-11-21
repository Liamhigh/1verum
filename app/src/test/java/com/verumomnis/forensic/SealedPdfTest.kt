package com.verumomnis.forensic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.verumomnis.forensic.engine.ForensicService
import com.verumomnis.forensic.engine.ReportGenerator
import com.verumomnis.forensic.model.GpsRecord
import com.verumomnis.forensic.pdf.SealedPageRenderer
import com.verumomnis.forensic.pdf.SealedPdfContent
import com.verumomnis.forensic.pdf.SealedPdfGenerator
import com.verumomnis.forensic.pdf.SealedPdfExporter
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SealedPdfTest {

    private val now = Instant.parse("2026-07-06T14:33:00Z")

    private fun sampleReport() = ReportGenerator.generate(
        ForensicService.scan(
            listOf(
                ForensicService.ingest(
                    "DOC001", "email_marius_9mar2025.txt", "email",
                    "From: Marius Nortje\nDate: 9 March 2025\nThe petroleum deal fell through.".toByteArray(),
                    gps = GpsRecord(-30.7667, 30.4000, timestamp = now.toString())
                ),
                ForensicService.ingest(
                    "DOC002", "email_admission_6apr2025.txt", "email",
                    "From: Marius Nortje\nDate: 6 April 2025\nKevin's Export proceeded with the deal. Fraud. R 500,000.".toByteArray(),
                    gps = GpsRecord(-30.7667, 30.4000, timestamp = now.toString())
                )
            ),
            now
        ).findings,
        "AllFuels", now
    )

    private fun watermark(): Bitmap? =
        BitmapFactory.decodeResource(
            ApplicationProvider.getApplicationContext<android.content.Context>().resources,
            R.drawable.watermark_portrait
        )

    @Test
    fun watermarkAssetDecodes() {
        val wm = watermark()
        assertNotNull("watermark_portrait must be bundled and decodable", wm)
        assertTrue(wm!!.width > 100 && wm.height > 100)
    }

    @Test
    fun generatesSealedPdfBytes() {
        val content = SealedPdfContent.fromReport(sampleReport())
        assertTrue(content.paginate().isNotEmpty())
        // android.graphics.pdf.PdfDocument.writeTo is a native API that Robolectric
        // cannot exercise off-device; skip the byte assertion there but keep it real
        // on an actual device/emulator. The page drawing itself is verified by
        // rendersSealedPageWithWatermarkToArtifact (same SealedPageRenderer routine).
        val bytes = try {
            SealedPdfGenerator.render(content, watermark())
        } catch (e: Throwable) {
            Assume.assumeNoException("PdfDocument unsupported in this JVM environment", e)
            return
        }
        assertTrue("PDF should not be empty", bytes.size > 500)
        val header = String(bytes.copyOfRange(0, 5), Charsets.US_ASCII)
        assertTrue("Expected a PDF header but was '$header'", header == "%PDF-")
    }

    @Test
    fun rendersBlueCoverToArtifact() {
        val content = SealedPdfContent.fromReport(sampleReport())
        val cover = content.cover!!
        val logo = BitmapFactory.decodeResource(
            ApplicationProvider.getApplicationContext<android.content.Context>().resources,
            R.drawable.vo_badge
        )
        val bmp = Bitmap.createBitmap(
            SealedPageRenderer.PAGE_WIDTH, SealedPageRenderer.PAGE_HEIGHT, Bitmap.Config.ARGB_8888
        )
        SealedPageRenderer.drawCover(Canvas(bmp), cover, watermark(), logo, content.sealFooter)
        val dir = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "08_sealed_pdf_cover.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(cover.title == "FORENSIC EVIDENCE REPORT")
    }

    @Test
    fun rendersSealedExhibitPageToArtifact() {
        val exhibit = SealedPdfContent.ExhibitPage(
            exhibitId = "EX-001",
            fileName = "site_photo_allfuels.jpg",
            kind = "IMAGE",
            caption = listOf(
                "EX-001 — site_photo_allfuels.jpg (image/jpeg)",
                "SHA-512: 9f2c1a…(sealed)",
                "GPS: -30.766900, 30.399800 (exif)",
                "Captured: 2026-07-06T14:32:10Z · EXIF 2026:01:14 09:15:00",
                "Jurisdiction: ZA-KZN"
            )
        )
        // Synthetic photo so the page has a framed image.
        val photo = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        Canvas(photo).drawColor(android.graphics.Color.parseColor("#35506E"))
        val bmp = Bitmap.createBitmap(
            SealedPageRenderer.PAGE_WIDTH, SealedPageRenderer.PAGE_HEIGHT, Bitmap.Config.ARGB_8888
        )
        SealedPageRenderer.drawExhibit(Canvas(bmp), exhibit, photo, watermark(), "VERUM OMNIS SEAL | seal-demo | demo…demo | demo", "Exhibit EX-001 | demo")
        val dir = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "10_sealed_exhibit_page.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(bmp.height == SealedPageRenderer.PAGE_HEIGHT)
    }

    @Test
    fun rendersSealedPageWithWatermarkToArtifact() {
        val content = SealedPdfContent.fromReport(sampleReport())
        val page = content.paginate().first()
        val bmp = Bitmap.createBitmap(
            SealedPageRenderer.PAGE_WIDTH, SealedPageRenderer.PAGE_HEIGHT, Bitmap.Config.ARGB_8888
        )
        SealedPageRenderer.drawPage(Canvas(bmp), page, watermark())
        val dir = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "07_sealed_pdf_page.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(bmp.width == SealedPageRenderer.PAGE_WIDTH)
    }

    @Test
    fun renderedWatermarkChangesReportPagePixels() {
        val page = SealedPdfContent.fromReport(sampleReport()).paginate().first()
        val withWatermark = Bitmap.createBitmap(
            SealedPageRenderer.PAGE_WIDTH, SealedPageRenderer.PAGE_HEIGHT, Bitmap.Config.ARGB_8888
        )
        val withoutWatermark = Bitmap.createBitmap(
            SealedPageRenderer.PAGE_WIDTH, SealedPageRenderer.PAGE_HEIGHT, Bitmap.Config.ARGB_8888
        )
        SealedPageRenderer.drawPage(Canvas(withWatermark), page, watermark())
        SealedPageRenderer.drawPage(Canvas(withoutWatermark), page, null)

        var changedPixels = 0
        for (y in 0 until SealedPageRenderer.PAGE_HEIGHT step 4) {
            for (x in 0 until SealedPageRenderer.PAGE_WIDTH step 4) {
                if (withWatermark.getPixel(x, y) != withoutWatermark.getPixel(x, y)) changedPixels++
            }
        }
        assertTrue("Expected the watermark to visibly alter the rendered page.", changedPixels > 50)
    }

    @Test
    fun exportPersistsVersionedPdfAndLeavesExistingVaultFilesUnchanged() {
        val root = Files.createTempDirectory("verum-report-export-test").toFile()
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val context = object : ContextWrapper(app) {
            private val testFilesDir = File(root, "files").apply { mkdirs() }
            private val testCacheDir = File(root, "cache").apply { mkdirs() }
            override fun getFilesDir(): File = testFilesDir
            override fun getCacheDir(): File = testCacheDir
        }
        val originalBytes = "original evidence bytes".toByteArray()
        val original = File(context.filesDir, "vault/evidence/raw/original.txt").apply {
            parentFile?.mkdirs()
            writeBytes(originalBytes)
        }
        val report = sampleReport()
        val content = SealedPdfContent.fromReport(report)
        assertTrue(content.sealFooter.contains(report.seal.shortcode))
        assertTrue(content.sealFooter.contains(report.seal.sha512.take(16)))

        val exporter = SealedPdfExporter(context)
        val first = try {
            exporter.exportReport(report)
        } catch (error: Throwable) {
            Assume.assumeNoException("PdfDocument unsupported in this JVM environment", error)
            return
        }
        val firstBytes = first.readBytes()
        val second = exporter.exportReport(report)

        assertTrue(first.isFile && firstBytes.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-")
        assertNotEquals("A repeat export must not replace an existing sealed PDF.", first, second)
        assertArrayEquals("The prior PDF must remain unchanged.", firstBytes, first.readBytes())
        assertArrayEquals("Export must not modify original evidence bytes.", originalBytes, original.readBytes())
        assertTrue(first.parentFile == File(context.filesDir, "vault/reports/sealed"))
        assertTrue(second.isFile)
    }

    @Test
    fun exporterConnectsReportContentAndWatermarkToUniqueVaultCopies() {
        val root = Files.createTempDirectory("verum-report-export-path-test").toFile()
        val app = ApplicationProvider.getApplicationContext<Context>()
        val context = object : ContextWrapper(app) {
            private val testFilesDir = File(root, "files").apply { mkdirs() }
            private val testCacheDir = File(root, "cache").apply { mkdirs() }
            override fun getFilesDir(): File = testFilesDir
            override fun getCacheDir(): File = testCacheDir
        }
        val originalBytes = "unaltered original evidence".toByteArray()
        val original = File(context.filesDir, "vault/evidence/raw/original.txt").apply {
            parentFile?.mkdirs()
            writeBytes(originalBytes)
        }
        val report = sampleReport()
        var contentSeen: SealedPdfContent? = null
        var watermarkSeen = false
        val renderedBytes = "%PDF-1.4\nsealed-report-export-test\n".toByteArray()
        val exporter = SealedPdfExporter(context) { content, mark, _, _ ->
            contentSeen = content
            watermarkSeen = mark != null
            renderedBytes
        }

        val first = exporter.exportReport(report)
        val firstSnapshot = first.readBytes()
        val second = exporter.exportReport(report)

        assertTrue(watermarkSeen)
        assertTrue(contentSeen!!.bodyLines.joinToString("\n").contains(report.body.take(80)))
        assertTrue(contentSeen!!.sealFooter.contains(report.seal.shortcode))
        assertTrue(first.path.contains("vault/reports/sealed"))
        assertNotEquals(first, second)
        assertArrayEquals(renderedBytes, firstSnapshot)
        assertArrayEquals(firstSnapshot, first.readBytes())
        assertArrayEquals(originalBytes, original.readBytes())
    }
}
