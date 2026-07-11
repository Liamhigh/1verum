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
