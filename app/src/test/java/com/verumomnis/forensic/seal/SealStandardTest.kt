package com.verumomnis.forensic.seal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VO-DSS-1.2 — JVM unit tests for the platform-independent seal logic.
 * PDFBox/Bitmap-dependent sealing is exercised on-device; everything here
 * (hashing, metadata codec, chain format, verify URL) is the interoperable
 * contract shared with the website and the Firewall.
 */
class SealStandardTest {

    private val fixedTs = 1720934400000L // deterministic — constitution PD4

    @Test
    fun `dual hashes are deterministic and correct length`() {
        val bytes = "standard bank evidence".toByteArray(Charsets.UTF_8)
        assertEquals(64, SealHasher.sha256Hex(bytes).length)
        assertEquals(128, SealHasher.sha512Hex(bytes).length)
        assertEquals(SealHasher.sha512Hex(bytes), SealHasher.sha512Hex(bytes))
        assertFalse(SealHasher.sha256Hex(bytes) == SealHasher.sha256Hex("other".toByteArray()))
    }

    @Test
    fun `seal id is VO- plus first 12 uppercase hex`() {
        val sha = "a1c825e8" + "0".repeat(120)
        assertEquals("VO-A1C825E80000", SealHasher.sealIdFromSha512(sha))
    }

    @Test
    fun `hexToBytes round-trips`() {
        val hex = "00ff10ab"
        assertEquals(hex, SealHasher.hexToBytes(hex).joinToString("") { "%02x".format(it) })
    }

    @Test
    fun `metadata collector builds the v1_2 schema`() {
        val meta = SealMetadataCodec.collect(
            SealMetadataCodec.SealMetadataInput(
                timestampMs = fixedTs,
                sealType = "commercial",
                org = "Standard Bank",
                identity = SealMetadataCodec.SealIdentity(
                    n = "John van der Merwe",
                    id = "760101 1234 087",
                    a = "12 Main Street, Sandton",
                    e = "john@email.com"
                ),
                lock = true,
                gpsLat = "-26.204100",
                gpsLng = "28.047300",
                gpsAccuracyM = 10,
                device = "Android|8|Africa/Johannesburg"
            )
        )
        assertEquals("1.2", meta.v)
        assertEquals(fixedTs, meta.t)
        assertEquals("commercial", meta.type)
        assertEquals("Standard Bank", meta.org)
        assertEquals(true, meta.lock)
        assertEquals("-26.204100,28.047300", meta.gps)
        assertEquals(10, meta.acc)
        assertEquals("Android|8|Africa/Johannesburg", meta.dev)
        assertEquals("John van der Merwe", meta.id?.n)
    }

    @Test
    fun `metadata omits empty identity and org for private seals`() {
        val meta = SealMetadataCodec.collect(
            SealMetadataCodec.SealMetadataInput(timestampMs = fixedTs, sealType = "private", org = "Ignored Org")
        )
        assertNull(meta.id)
        assertNull(meta.lock)
        assertNull(meta.gps)
        assertNull(meta.org) // org carried only for commercial seals
    }

    @Test
    fun `metadata round-trips through base64 and percent encoding`() {
        val meta = SealMetadataCodec.collect(
            SealMetadataCodec.SealMetadataInput(
                timestampMs = fixedTs,
                sealType = "private",
                identity = SealMetadataCodec.SealIdentity(n = "Jane", e = "jane@email.com")
            )
        )
        val encoded = SealMetadataCodec.encode(meta)
        assertEquals(meta, SealMetadataCodec.decode(encoded))
        assertNull(SealMetadataCodec.decode("!!!not-valid!!!"))
    }

    @Test
    fun `verify URL matches the website format`() {
        val sha = "f".repeat(128)
        val meta = SealMetadataCodec.SealMetadata(v = "1.2", t = fixedTs, type = "private")
        val url = SealMetadataCodec.buildVerifyUrl(sha, meta)
        assertTrue(url.startsWith("https://verumglobal.foundation/verify.html?h=" + "f".repeat(32) + "&m="))
        val mParam = url.substringAfter("&m=")
        assertEquals(meta, SealMetadataCodec.decode(mParam))
    }

    @Test
    fun `subject round-trips with chain`() {
        val subject = SealChain.buildSubject("a".repeat(128), "VO-NEW", listOf("VO-OLD1", "VO-OLD2"))
        assertEquals("VO-SEAL|${"a".repeat(128)}|VO-NEW|CHAIN:VO-OLD1,VO-OLD2", subject)
        val parsed = SealChain.parseSubject(subject)
        assertNotNull(parsed)
        assertEquals("VO-NEW", parsed!!.sealId)
        assertEquals("a".repeat(128), parsed.sha512)
        assertEquals(listOf("VO-OLD1", "VO-OLD2"), parsed.chain)
    }

    @Test
    fun `subject parser handles plain and foreign subjects`() {
        val parsed = SealChain.parseSubject(SealChain.buildSubject("b".repeat(128), "VO-SOLO", emptyList()))
        assertEquals("VO-SOLO", parsed!!.sealId)
        assertTrue(parsed.chain.isEmpty())
        assertNull(SealChain.parseSubject("Some random subject"))
        assertNull(SealChain.parseSubject(null))
    }

    @Test
    fun `chainIdsFrom excludes the new seal and deduplicates`() {
        val parsed = SealChain.parseSubject(
            "VO-SEAL|${"c".repeat(128)}|VO-PREV|CHAIN:VO-OLD,VO-PREV"
        )
        assertEquals(listOf("VO-PREV", "VO-OLD"), SealChain.chainIdsFrom(parsed, "VO-NEW"))
    }

    @Test
    fun `keywords match the web format`() {
        assertEquals("verum,seal,1234567890abcdef,private", SealChain.buildKeywords("1234567890abcdef" + "0".repeat(112), "private"))
    }
}
