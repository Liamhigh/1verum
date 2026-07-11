package com.verumomnis.forensic.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

/**
 * Generates a sealed PDF using the Android native [PdfDocument]. Every page is
 * rendered by [SealedPageRenderer], which paints the Verum Omnis portrait
 * watermark as a low-opacity background underlay before the text and seal footer.
 */
object SealedPdfGenerator {

    fun render(content: SealedPdfContent, watermark: Bitmap?, logo: Bitmap? = null): ByteArray {
        val document = PdfDocument()
        var pageNumber = 1

        // Branded blue front cover (reports only).
        content.cover?.let { cover ->
            val info = PdfDocument.PageInfo.Builder(
                SealedPageRenderer.PAGE_WIDTH, SealedPageRenderer.PAGE_HEIGHT, pageNumber++
            ).create()
            val page = document.startPage(info)
            SealedPageRenderer.drawCover(page.canvas, cover, watermark, logo, content.sealFooter)
            document.finishPage(page)
        }

        content.paginate().forEach { page ->
            val info = PdfDocument.PageInfo.Builder(
                SealedPageRenderer.PAGE_WIDTH, SealedPageRenderer.PAGE_HEIGHT, pageNumber++
            ).create()
            val pdfPage = document.startPage(info)
            SealedPageRenderer.drawPage(pdfPage.canvas, page, watermark)
            document.finishPage(pdfPage)
        }

        return ByteArrayOutputStream().use { out ->
            document.writeTo(out)
            document.close()
            out.toByteArray()
        }
    }
}
