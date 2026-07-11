package com.verumomnis.forensic

import com.verumomnis.forensic.blockchain.OpenTimestampsService
import com.verumomnis.forensic.crypto.Sha512
import com.verumomnis.forensic.model.OtsStatus
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenTimestampsServiceTest {

    private val sha512 = Sha512.hash("verum omnis evidence")

    @Test
    fun sha256OfSha512IsThirtyTwoBytesAndDeterministic() {
        val a = OpenTimestampsService.sha256OfSha512(sha512)
        val b = OpenTimestampsService.sha256OfSha512(sha512)
        assertEquals(32, a.size)
        assertArrayEquals(a, b)
    }

    @Test
    fun proofHasValidOtsHeaderAndDigest() {
        val digest = OpenTimestampsService.sha256OfSha512(sha512)
        val fakeCalendarTs = byteArrayOf(0x01, 0x02, 0x03) // stand-in timestamp body
        val proof = OpenTimestampsService.buildProof(digest, fakeCalendarTs)
        // Magic + version(0x01) + op(0x08) + 32-byte digest + body
        val magic = OpenTimestampsService.hexToBytes("004f70656e54696d657374616d7073000050726f6f6600bf89e2e884e89294")
        assertArrayEquals(magic, proof.copyOfRange(0, magic.size))
        assertEquals(0x01.toByte(), proof[magic.size])       // version
        assertEquals(0x08.toByte(), proof[magic.size + 1])   // SHA256 op
        assertArrayEquals(digest, proof.copyOfRange(magic.size + 2, magic.size + 2 + 32))
    }

    @Test
    fun verifyDetectsPendingAttestation() {
        // Build a proof whose body contains the PendingAttestation tag.
        val digest = OpenTimestampsService.sha256OfSha512(sha512)
        val pendingTag = OpenTimestampsService.hexToBytes("83dfe30d2ef90c8e")
        val proof = OpenTimestampsService.buildProof(digest, pendingTag + "https://alice…".toByteArray())
        val res = OpenTimestampsService.verify(proof, checkBitcoin = false)
        assertEquals(OtsStatus.PENDING, res.status)
        assertTrue(res.pending)
        assertFalse(res.bitcoinAttested)
    }

    @Test
    fun anchorOfflineFallbackWhenNoNetwork() {
        // Zero timeout forces the calendar POSTs to fail -> OFFLINE, still deterministic digest.
        val res = OpenTimestampsService.anchor(sha512, timeoutMs = 1)
        assertEquals(OtsStatus.OFFLINE, res.status)
        assertEquals(64, res.sha256Digest.length)
        assertTrue(res.otsProofBase64 == null)
    }
}
