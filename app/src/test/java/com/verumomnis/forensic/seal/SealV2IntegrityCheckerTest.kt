package com.verumomnis.forensic.seal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class SealV2IntegrityCheckerTest {

    private val placeholder =
        "0000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000000000000000000000"

    @Test
    fun genuineV2SealPassesIntegrityCheck() {
        val embeddedHash = createSyntheticV2Seal().first
        val sealedBytes = createSyntheticV2Seal().second

        val result = SealV2IntegrityChecker.check(sealedBytes, embeddedHash)

        assertTrue(result.feasible)
        assertTrue(result.match)
        assertEquals(embeddedHash, result.recomputed)
    }

    @Test
    fun tamperedV2SealFailsIntegrityCheck() {
        val (embeddedHash, sealedBytes) = createSyntheticV2Seal()
        val tampered = sealedBytes.copyOf()
        // Flip a byte outside the VO-SEAL2 subject region to simulate tampering.
        tampered[0] = (tampered[0].toInt() xor 0xFF).toByte()

        val result = SealV2IntegrityChecker.check(tampered, embeddedHash)

        assertTrue(result.feasible)
        assertFalse(result.match)
    }

    @Test
    fun infeasibleWhenNeedleMissing() {
        val result = SealV2IntegrityChecker.check("no seal here".toByteArray(), placeholder)
        assertFalse(result.feasible)
        assertNull(result.recomputed)
    }

    @Test
    fun infeasibleWhenMultipleNeedles() {
        val (_, once) = createSyntheticV2Seal()
        val twice = once + once
        val result = SealV2IntegrityChecker.check(twice, placeholder)
        assertFalse(result.feasible)
    }

    /** Build a minimal synthetic VO-SEAL2 byte sequence that the integrity check will accept. */
    private fun createSyntheticV2Seal(): Pair<String, ByteArray> {
        // Start with the placeholder subject so that the integrity check can
        // restore it to compute the original hash.
        val subject = "VO-SEAL2|$placeholder"
        val prefix = "SOMEPREFIX".toByteArray(Charsets.UTF_8)
        val suffix = "SOMESUFFIX".toByteArray(Charsets.UTF_8)
        val subjectBytes = voUtf16Hex(subject).toByteArray(Charsets.US_ASCII)
        val restored = prefix + subjectBytes + suffix
        val embeddedHash = sha512Hex(restored)

        // Now seal it by replacing the placeholder hash with the real hash.
        val sealedSubject = voUtf16Hex("VO-SEAL2|$embeddedHash")
        val sealed = prefix + sealedSubject.toByteArray(Charsets.US_ASCII) + suffix
        return embeddedHash to sealed
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
