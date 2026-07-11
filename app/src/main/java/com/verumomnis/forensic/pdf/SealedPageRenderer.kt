package com.verumomnis.forensic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Draws a single sealed-report page onto a [Canvas]. The Verum Omnis portrait
 * watermark is rendered FIRST as a low-opacity, page-centred underlay (per the
 * Brand Asset Guide: ~65% page width, 15-20% opacity, behind the text), followed
 * by the header, body text and the per-page seal footer.
 *
 * The same routine backs both the on-device PDF page canvas and the JVM preview
 * bitmap used in tests, so what is verified is exactly what is written.
 */
object SealedPageRenderer {

    // A4 at 72 dpi (points).
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f
    private const val WATERMARK_WIDTH_RATIO = 0.65f
    private const val WATERMARK_ALPHA = 46 // ~18% of 255

    private val navy = Color.parseColor("#0F1D2E")
    private val gold = Color.parseColor("#B8862F")
    private val bodyColor = Color.parseColor("#2A2A2A")
    private val mutedColor = Color.parseColor("#6A7F9A")
    private val red = Color.parseColor("#B3261E")

    // Cover palette
    private val coverBlue = Color.parseColor("#0E2749")
    private val coverGold = Color.parseColor("#C9A24A")
    private val coverWhite = Color.parseColor("#F5F7FA")
    private val coverMuted = Color.parseColor("#AFC0D6")

    /**
     * Branded blue front cover (build spec Section 20): deep-blue page, gold frame,
     * the Verum Omnis logo badge, title, report metadata and the faint watermark.
     */
    fun drawCover(
        canvas: Canvas,
        cover: SealedPdfContent.Cover,
        watermark: Bitmap?,
        logo: Bitmap?,
        sealFooter: String,
        pageWidth: Int = PAGE_WIDTH,
        pageHeight: Int = PAGE_HEIGHT
    ) {
        canvas.drawColor(coverBlue)

        // Faint watermark underlay.
        if (watermark != null) {
            val targetW = pageWidth * 0.7f
            val scale = targetW / watermark.width
            val targetH = watermark.height * scale
            val left = (pageWidth - targetW) / 2f
            val top = (pageHeight - targetH) / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 24 }
            canvas.drawBitmap(watermark, Rect(0, 0, watermark.width, watermark.height),
                RectF(left, top, left + targetW, top + targetH), paint)
        }

        // Gold frame (double line).
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = coverGold; strokeWidth = 1.6f
        }
        canvas.drawRect(28f, 28f, pageWidth - 28f, pageHeight - 28f, frame)
        frame.strokeWidth = 0.8f
        canvas.drawRect(34f, 34f, pageWidth - 34f, pageHeight - 34f, frame)

        // Logo badge centred near the top.
        if (logo != null) {
            val size = 96f
            val left = (pageWidth - size) / 2f
            canvas.drawBitmap(logo, Rect(0, 0, logo.width, logo.height),
                RectF(left, 70f, left + size, 70f + size), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }

        val center = pageWidth / 2f

        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coverGold; textSize = 10f; letterSpacing = 0.22f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("CONSTITUTIONAL FORENSIC AI  ·  V5.2.7", center, 200f, caption)

        goldDivider(canvas, center, 218f)

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coverWhite; textSize = 30f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText(cover.title, center, 268f, title)

        val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coverGold; textSize = 13f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        var y = 292f
        wrapText(cover.subtitle, 52).forEach { canvas.drawText(it, center, y, subtitle); y += 18f }

        goldDivider(canvas, center, y + 6f)
        y += 34f

        // Metadata block.
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coverWhite; textSize = 11f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coverMuted; textSize = 11f; typeface = Typeface.SANS_SERIF
        }
        val mx = 70f
        fun row(l: String, v: String) {
            canvas.drawText(l, mx, y, label)
            canvas.drawText(v, mx + 130f, y, value)
            y += 20f
        }
        row("Report Reference:", cover.reference)
        row("Date:", cover.date)
        row("Jurisdiction:", cover.jurisdiction)
        row("Summary:", "")
        val summaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = coverMuted; textSize = 10f; typeface = Typeface.SANS_SERIF }
        cover.summary.forEach { canvas.drawText(it, mx, y, summaryPaint); y += 14f }

        // Classification + seal footer near the bottom.
        val cls = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coverGold; textSize = 10f; letterSpacing = 0.12f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(cover.classification, center, pageHeight - 70f, cls)
        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coverMuted; textSize = 7f; textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE
        }
        canvas.drawText(sealFooter, center, pageHeight - 50f, footer)
    }

    private fun goldDivider(canvas: Canvas, center: Float, y: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = coverGold; strokeWidth = 1.5f }
        canvas.drawLine(center - 40f, y, center + 40f, y, p)
    }

    private fun wrapText(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val out = mutableListOf<String>(); var rem = text
        while (rem.length > max) {
            var cut = rem.lastIndexOf(' ', max); if (cut <= 0) cut = max
            out += rem.substring(0, cut); rem = rem.substring(cut).trimStart()
        }
        if (rem.isNotEmpty()) out += rem
        return out
    }

    fun drawPage(
        canvas: Canvas,
        page: SealedPdfContent.Page,
        watermark: Bitmap?,
        pageWidth: Int = PAGE_WIDTH,
        pageHeight: Int = PAGE_HEIGHT
    ) {
        canvas.drawColor(Color.WHITE)
        drawWatermark(canvas, watermark, pageWidth, pageHeight)

        var y = MARGIN + 8f

        // Brand line
        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gold; textSize = 10f; letterSpacing = 0.15f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("VERUM OMNIS · AI FORENSICS FOR TRUTH", MARGIN, y, brand)
        y += 22f

        // Title
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy; textSize = 15f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText(page.title, MARGIN, y, titlePaint)
        y += 16f

        if (page.classification.isNotEmpty()) {
            val cls = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = red; textSize = 8.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            canvas.drawText(page.classification, MARGIN, y, cls)
            y += 14f
        }

        // Divider
        val line = Paint().apply { color = Color.parseColor("#D9DEE6"); strokeWidth = 1f }
        canvas.drawLine(MARGIN, y, pageWidth - MARGIN, y, line)
        y += 16f

        // Body
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bodyColor; textSize = 10f
            typeface = Typeface.MONOSPACE
        }
        val lineHeight = 14f
        for (text in page.lines) {
            if (y > pageHeight - MARGIN - 30f) break
            canvas.drawText(text, MARGIN, y, body)
            y += lineHeight
        }

        drawFooter(canvas, page, pageWidth, pageHeight)
    }

    private fun drawWatermark(canvas: Canvas, watermark: Bitmap?, pageWidth: Int, pageHeight: Int) {
        if (watermark == null) return
        val targetW = pageWidth * WATERMARK_WIDTH_RATIO
        val scale = targetW / watermark.width
        val targetH = watermark.height * scale
        val left = (pageWidth - targetW) / 2f
        val top = (pageHeight - targetH) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = WATERMARK_ALPHA }
        canvas.drawBitmap(watermark, Rect(0, 0, watermark.width, watermark.height),
            RectF(left, top, left + targetW, top + targetH), paint)
    }

    private fun drawFooter(canvas: Canvas, page: SealedPdfContent.Page, pageWidth: Int, pageHeight: Int) {
        val footerY = pageHeight - MARGIN + 12f
        val sep = Paint().apply { color = Color.parseColor("#D9DEE6"); strokeWidth = 1f }
        canvas.drawLine(MARGIN, footerY - 14f, pageWidth - MARGIN, footerY - 14f, sep)

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedColor; textSize = 7f; typeface = Typeface.MONOSPACE
        }
        canvas.drawText(page.sealFooter, MARGIN, footerY, footerPaint)

        val pageLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedColor; textSize = 7f; typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(page.pageLabel, pageWidth - MARGIN, footerY, pageLabelPaint)
    }
}
