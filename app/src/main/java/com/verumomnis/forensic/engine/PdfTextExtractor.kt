package com.verumomnis.forensic.engine

/**
 * Extracts plain text from a PDF byte array.
 *
 * The interface keeps the engine testable off-device: Robolectric and unit tests
 * can supply a fake implementation, while the real Android app uses PDFBox.
 */
interface PdfTextExtractor {
    /** @return extracted text, or an empty string if no text could be extracted. */
    fun extractText(bytes: ByteArray): String
}

/** Test/production fake that treats the bytes as UTF-8 text (useful for plain-text tests). */
object PlainTextExtractor : PdfTextExtractor {
    override fun extractText(bytes: ByteArray): String =
        bytes.toString(Charsets.UTF_8)
}

/** Production extractor backed by Apache PDFBox for Android. */
class PdfBoxTextExtractor : PdfTextExtractor {
    override fun extractText(bytes: ByteArray): String = try {
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(bytes).use { doc ->
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            stripper.getText(doc) ?: ""
        }
    } catch (e: Exception) {
        ""
    }
}
