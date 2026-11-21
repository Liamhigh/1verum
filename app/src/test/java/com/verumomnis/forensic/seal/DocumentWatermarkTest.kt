package com.verumomnis.forensic.seal

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.verumomnis.forensic.R
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DocumentWatermarkTest {
    @Test
    fun bundledWatermarkIsEmbeddedOnEverySealedPageWithoutChangingOriginalBytes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = PDDocument().use { pdf ->
            repeat(2) { index ->
                val page = PDPage(PDRectangle.A4)
                pdf.addPage(page)
                PDPageContentStream(pdf, page).use { stream ->
                    // An opaque scan/background must not obscure the seal watermark.
                    stream.setNonStrokingColor(1f, 1f, 1f)
                    stream.addRect(0f, 0f, PDRectangle.A4.width, PDRectangle.A4.height)
                    stream.fill()
                    stream.setNonStrokingColor(0f, 0f, 0f)
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(70f, 700f)
                    stream.showText("Original evidence page ${index + 1}")
                    stream.endText()
                }
            }
            ByteArrayOutputStream().also { pdf.save(it) }.toByteArray()
        }
        val snapshot = original.copyOf()
        val expected = requireNotNull(
            BitmapFactory.decodeResource(context.resources, R.drawable.watermark_portrait)
        )
        val expectedWidth = expected.width
        val expectedHeight = expected.height
        expected.recycle()

        val result = DocumentSealer.sealWithBundledWatermark(
            context,
            original,
            DocumentSealer.SealOptions(
                timestampMs = 1795276800000L,
                originalName = "synthetic-evidence.pdf",
                anchorToBlockchain = false
            )
        )

        assertArrayEquals(snapshot, original)
        assertEquals(SealHasher.sha512Hex(snapshot), result.sha512)
        assertEquals(0, result.pageErrors)
        assertTrue(result.sealedPdf.toString(Charsets.ISO_8859_1).startsWith("%PDF-"))
        PDDocument.load(result.sealedPdf).use { sealed ->
            assertEquals(2, sealed.numberOfPages)
            for (page in sealed.pages) {
                val images = page.resources.xObjectNames.mapNotNull { name ->
                    page.resources.getXObject(name) as? PDImageXObject
                }
                assertTrue("Every page must contain the bundled watermark image.",
                    images.any { it.width == expectedWidth && it.height == expectedHeight })
                assertTrue("Every page must also contain its verification QR.",
                    images.any { it.width == 400 && it.height == 400 })
                val parser = PDFStreamParser(page)
                parser.parse()
                val draws = parser.tokens.withIndex().mapNotNull { (index, token) ->
                    if (token !is Operator || token.name != "Do") return@mapNotNull null
                    val name = parser.tokens.getOrNull(index - 1) as? COSName
                        ?: return@mapNotNull null
                    page.resources.getXObject(name)
                }
                val originalIndex = draws.indexOfFirst {
                    it is com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
                }
                val watermarkIndex = draws.indexOfFirst {
                    it is PDImageXObject && it.width == expectedWidth && it.height == expectedHeight
                }
                assertTrue("Watermark must be painted over the original, not hidden behind it.",
                    originalIndex >= 0 && watermarkIndex > originalIndex)
            }
            val text = PDFTextStripper().getText(sealed)
            assertTrue(text.contains("Original evidence page 1"))
            assertTrue(text.contains("Original evidence page 2"))
            assertTrue(text.contains(result.sealId))
        }
    }
}
