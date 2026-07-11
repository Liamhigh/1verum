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

    fun render(content: SealedPdfContent, watermark: Bitmap?): ByteArray {
        val document = PdfDocument()
        val pages = content.paginate()
        pages.forEachIndexed { index, page ->
            val pageInfo = PdfDocument.PageInfo.Builder(
                SealedPageRenderer.PAGE_WIDTH,
                SealedPageRenderer.PAGE_HEIGHT,
                index + 1
            ).create()
            val pdfPage = document.startPage(pageInfo)
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
