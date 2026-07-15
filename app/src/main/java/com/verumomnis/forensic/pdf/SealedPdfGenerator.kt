package com.verumomnis.forensic.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import com.verumomnis.forensic.seal.SealMetadataCodec
import java.io.ByteArrayOutputStream

/**
 * Generates a sealed PDF using the Android native [PdfDocument]. Every page is
 * rendered by [SealedPageRenderer], which paints the Verum Omnis portrait
 * watermark as a low-opacity background underlay before the text and seal footer.
 */
object SealedPdfGenerator {

    fun render(
        content: SealedPdfContent,
        watermark: Bitmap?,
        logo: Bitmap? = null,
        exhibitImages: Map<String, Bitmap> = emptyMap()
    ): ByteArray {
        val document = PdfDocument()
        var pageNumber = 1

        // Branded blue front cover (reports only).
        content.cover?.let { cover ->
            val info = PdfDocument.PageInfo.Builder(
                SealedPageRenderer.PAGE_WIDTH, SealedPageRenderer.PAGE_HEIGHT, pageNumber++
            ).create()
            val page = document.startPage(info)
            val qrPayload = if (content.sealHash.isNotBlank()) {
                val meta = content.sealMetadata ?: SealMetadataCodec.collect(
                    SealMetadataCodec.SealMetadataInput(
                        timestampMs = System.currentTimeMillis(),
                        sealType = "private"
                    )
                )
                SealMetadataCodec.buildVerifyUrl(content.sealHash, meta)
            } else null
            val qrCode = qrPayload?.let { QrCodeGenerator.generate(it, 256) }
            SealedPageRenderer.drawCover(page.canvas, cover, watermark, logo, content.sealFooter, qrCode)
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

        // Sealed photographic/video exhibit pages (annexure).
        content.exhibits.forEach { exhibit ->
            val info = PdfDocument.PageInfo.Builder(
                SealedPageRenderer.PAGE_WIDTH, SealedPageRenderer.PAGE_HEIGHT, pageNumber
            ).create()
            val page = document.startPage(info)
            SealedPageRenderer.drawExhibit(
                page.canvas, exhibit, exhibitImages[exhibit.fileName], watermark,
                content.sealFooter, "Exhibit ${exhibit.exhibitId} | ${content.shortcode}"
            )
            document.finishPage(page)
            pageNumber++
        }

        return ByteArrayOutputStream().use { out ->
            document.writeTo(out)
            document.close()
            out.toByteArray()
        }
    }
}
