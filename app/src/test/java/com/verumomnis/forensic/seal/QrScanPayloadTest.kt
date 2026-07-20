package com.verumomnis.forensic.seal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrScanPayloadTest {

    private val sha512 = "4ffea8a806c1a01bd1a1541eaa212d75dd127e933d8885808c0ed90006b6de01197f317d706477ba2272e9973f8b9268dd333661355a40d70a28ccaebdc04b0a"

    @Test
    fun parseExtractsHashPrefixAndMetadata() {
        val meta = SealMetadataCodec.collect(
            SealMetadataCodec.SealMetadataInput(
                timestampMs = 1720934400000L,
                sealType = "private",
                identity = SealMetadataCodec.SealIdentity(
                    n = "Sender Name",
                    e = "sender@example.com"
                ),
                gpsLat = "-26.2041",
                gpsLng = "28.0473",
                device = "Android|8|Africa/Johannesburg"
            )
        )
        val url = SealMetadataCodec.buildVerifyUrl(sha512, meta)

        val parsed = QrScanPayload.parse(url)

        assertEquals(sha512.substring(0, 32), parsed.hashPrefix)
        assertNotNull(parsed.metadata)
        parsed.metadata!!.let {
            assertEquals("1.2", it.v)
            assertEquals(1720934400000L, it.t)
            assertEquals("private", it.type)
            assertEquals("Sender Name", it.id?.n)
            assertEquals("sender@example.com", it.id?.e)
            assertEquals("-26.2041,28.0473", it.gps)
            assertEquals("Android|8|Africa/Johannesburg", it.dev)
        }
    }

    @Test
    fun parseOrNullReturnsNullForNonVerumUrl() {
        assertNull(QrScanPayload.parseOrNull("https://example.com/verify.html?h=1234"))
    }

    @Test
    fun parseOrNullReturnsNullForMissingHash() {
        assertNull(QrScanPayload.parseOrNull("https://verumglobal.foundation/verify.html?m=abc"))
    }

    @Test
    fun parseOrNullReturnsPayloadWithoutMetadata() {
        val url = "https://verumglobal.foundation/verify.html?h=${sha512.substring(0, 32)}"
        val parsed = QrScanPayload.parseOrNull(url)
        assertNotNull(parsed)
        assertEquals(sha512.substring(0, 32), parsed!!.hashPrefix)
        assertNull(parsed.metadata)
    }

    @Test
    fun hashPrefixIsLowercased() {
        val url = "https://verumglobal.foundation/verify.html?h=${sha512.substring(0, 32).uppercase()}"
        val parsed = QrScanPayload.parse(url)
        assertEquals(sha512.substring(0, 32), parsed.hashPrefix)
    }
}
