package com.verumomnis.forensic.seal

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.verumomnis.forensic.model.SealScanVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Unit tests for [SealVerifier] QR-aware + VO-SEAL2 detection parity with
 * the live webdocsol verify.html.
 */
@RunWith(RobolectricTestRunner::class)
class SealVerifierTest {

    private val placeholder =
        "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000"

    @Test
    fun parseSealV2RawDetectsUtf16HexSubject() {
        val (embeddedHash, sealedBytes) = createSyntheticV2Seal()

        val parsed = SealVerifier.parseSealV2Raw(sealedBytes)

        assertNotNull(parsed)
        assertEquals("v2", parsed!!.scheme)
        assertEquals(embeddedHash.lowercase(), parsed.sha512)
        assertTrue(parsed.sealId.startsWith("VO-"))
    }

    @Test
    fun parseSealFallsBackToV2Raw() {
        val (_, sealedBytes) = createSyntheticV2Seal()

        val parsed = SealVerifier.parseSeal(sealedBytes)

        assertNotNull(parsed)
        assertEquals("v2", parsed!!.scheme)
    }

    @Test
    fun parseSealDetectsLegacyLiteralSubject() {
        val hash = "a".repeat(128)
        val sealId = "VO-LEGACY12345"
        val subject = "(VO-SEAL|$hash|$sealId)"
        val bytes = "PREFIX${subject}SUFFIX".toByteArray(Charsets.UTF_8)

        val parsed = SealVerifier.parseSeal(bytes)

        assertNotNull(parsed)
        assertEquals("legacy", parsed!!.scheme)
        assertEquals(sealId, parsed.sealId)
        assertEquals(hash, parsed.sha512)
    }

    @Test
    fun verifyWithQrReturnsMatchForGenuineV2() {
        val (embeddedHash, sealedBytes) = createSyntheticV2Seal()

        val result = SealVerifier.verifyWithQr(sealedBytes)

        assertEquals(SealScanVerdict.MATCH, result.verdict)
        assertNotNull(result.recomputedHash)
        assertEquals(embeddedHash.lowercase(), result.recomputedHash)
        assertTrue(result.reason.contains("matches"))
    }

    @Test
    fun verifyWithQrReturnsTamperedForModifiedV2() {
        val (_, sealedBytes) = createSyntheticV2Seal()
        val tampered = sealedBytes.copyOf()
        tampered[0] = (tampered[0].toInt() xor 0xFF).toByte()

        val result = SealVerifier.verifyWithQr(tampered)

        assertEquals(SealScanVerdict.TAMPERED, result.verdict)
        assertFalse(result.recomputedHash == result.seal?.embeddedHash)
    }

    @Test
    fun verifyWithQrReturnsLegacyForLiteralSubject() {
        val hash = "b".repeat(128)
        val sealId = "VO-LEGACY12345"
        val subject = "(VO-SEAL|$hash|$sealId)"
        val bytes = "PREFIX${subject}SUFFIX".toByteArray(Charsets.UTF_8)

        val result = SealVerifier.verifyWithQr(bytes)

        assertEquals(SealScanVerdict.LEGACY, result.verdict)
        assertEquals(hash, result.originalHash)
        assertEquals(hash, result.seal!!.embeddedHash)
    }

    @Test
    fun verifyWithQrReturnsNoSealForRandomBytes() {
        val bytes = "this is not a sealed pdf".toByteArray(Charsets.UTF_8)

        val result = SealVerifier.verifyWithQr(bytes)

        assertEquals(SealScanVerdict.NO_SEAL, result.verdict)
        assertNull(result.seal)
    }

    @Test
    fun verifyWithQrDetectsFooterSeal() {
        val bytes = buildPdfWithFooter("VO-ABCDEF123456", "abcdef1234567890")

        val result = SealVerifier.verifyWithQr(bytes)

        assertEquals(SealScanVerdict.SEAL_PRESENT, result.verdict)
        val seal = result.seal!!
        assertEquals("VO-ABCDEF123456", seal.sealId)
        assertEquals("footer", seal.scheme)
        assertEquals("abcdef1234567890", seal.embeddedHash)
    }

    @Test
    fun qrMismatchMakesLegacyVerdictTampered() {
        val hash = "c".repeat(128)
        val sealId = "VO-LEGACY12345"
        val subject = "(VO-SEAL|$hash|$sealId)"
        val bytes = "PREFIX${subject}SUFFIX".toByteArray(Charsets.UTF_8)
        val qr = QrScanPayload.parse(
            "https://verumglobal.foundation/verify.html?h=${"d".repeat(32)}"
        )

        val result = SealVerifier.verifyWithQr(bytes, qr)

        assertEquals(SealScanVerdict.TAMPERED, result.verdict)
    }

    @Test
    fun qrMatchKeepsLegacyVerdict() {
        val hash = "c".repeat(128)
        val sealId = "VO-LEGACY12345"
        val subject = "(VO-SEAL|$hash|$sealId)"
        val bytes = "PREFIX${subject}SUFFIX".toByteArray(Charsets.UTF_8)
        val qr = QrScanPayload.parse(
            "https://verumglobal.foundation/verify.html?h=${hash.substring(0, 32)}"
        )

        val result = SealVerifier.verifyWithQr(bytes, qr)

        assertEquals(SealScanVerdict.LEGACY, result.verdict)
    }

    /** Build a minimal synthetic VO-SEAL2 byte sequence that the integrity check will accept. */
    private fun createSyntheticV2Seal(): Pair<String, ByteArray> {
        val sealId = "VO-TEST123456"
        val subject = "VO-SEAL2|$placeholder|$sealId"
        val prefix = "SOMEPREFIX".toByteArray(Charsets.UTF_8)
        val suffix = "SOMESUFFIX".toByteArray(Charsets.UTF_8)
        val subjectBytes = voUtf16Hex(subject).toByteArray(Charsets.US_ASCII)
        val restored = prefix + subjectBytes + suffix
        val embeddedHash = sha512Hex(restored)

        val sealedSubject = voUtf16Hex("VO-SEAL2|$embeddedHash|$sealId")
        val sealed = prefix + sealedSubject.toByteArray(Charsets.US_ASCII) + suffix
        return embeddedHash to sealed
    }

    /** Build a minimal PDF containing a Verum seal footer in page text. */
    private fun buildPdfWithFooter(sealId: String, hashPrefix: String): ByteArray {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText("Document body")
                cs.endText()
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 8f)
                cs.newLineAtOffset(50f, 50f)
                cs.showText("Seal: $sealId  |  SHA-512: $hashPrefix...")
                cs.endText()
            }
            val out = ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        }
    }

    private fun voUtf16Hex(str: String): String = buildString {
        for (c in str) {
            append(c.code.toString(16).uppercase().padStart(4, '0'))
        }
    }

    private fun sha512Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-512").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
