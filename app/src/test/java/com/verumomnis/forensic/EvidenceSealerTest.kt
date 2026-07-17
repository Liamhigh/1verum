package com.verumomnis.forensic

import com.verumomnis.forensic.crypto.EvidenceSealer
import com.verumomnis.forensic.crypto.EvidenceSealer.VerificationResult
import com.verumomnis.forensic.crypto.Sha512
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceSealerTest {

    private val fixedTime = Instant.parse("2026-01-15T10:30:00Z")

    @Test
    fun sealProducesDeterministicRecord() {
        val bytes = "sealed evidence".toByteArray()
        val seal = EvidenceSealer.seal(bytes, "evidence", "VO-EV-1", fixedTime)
        assertEquals("seal_${fixedTime.toEpochMilli()}", seal.sealId)
        assertEquals(Sha512.hash(bytes), seal.sha512)
        assertTrue(seal.truncatedHash.contains("..."))
    }

    @Test
    fun verifyDetectsUntamperedSeal() {
        val bytes = "sealed evidence".toByteArray()
        val seal = EvidenceSealer.seal(bytes, "evidence", "VO-EV-1", fixedTime)
        val result = EvidenceSealer.verify(bytes, seal, blockchainConfirmed = true)
        assertEquals(VerificationResult.VERIFIED, result)
    }

    @Test
    fun verifyDetectsTampering() {
        val bytes = "sealed evidence".toByteArray()
        val seal = EvidenceSealer.seal(bytes, "evidence", "VO-EV-1", fixedTime)
        val result = EvidenceSealer.verify("edited evidence".toByteArray(), seal, blockchainConfirmed = true)
        assertEquals(VerificationResult.TAMPERED, result)
    }

    @Test
    fun verifyReportsMissingChain() {
        val bytes = "sealed evidence".toByteArray()
        val seal = EvidenceSealer.seal(bytes, "evidence", "VO-EV-1", fixedTime)
        val result = EvidenceSealer.verify(bytes, seal, blockchainConfirmed = false)
        assertEquals(VerificationResult.SEAL_FOUND_NO_CHAIN, result)
    }

    @Test
    fun verifyComparesFullHashNotJustTruncatedPrefix() {
        val bytes = "sealed evidence".toByteArray()
        val seal = EvidenceSealer.seal(bytes, "evidence", "VO-EV-1", fixedTime)
        // Forged record: identical truncated display hash (16 prefix + 8 suffix)
        // but a different middle. The old prefix/suffix-only comparison would
        // have accepted this; the full SHA-512 comparison must reject it.
        val realHash = Sha512.hash(bytes)
        val forgedMiddle = realHash.substring(16, 120)
            .map { if (it == '0') '1' else '0' }
            .joinToString("")
        val forgedHash = realHash.take(16) + forgedMiddle + realHash.takeLast(8)
        assertEquals(128, forgedHash.length)
        val forged = seal.copy(
            sha512 = forgedHash,
            truncatedHash = forgedHash.take(16) + "..." + forgedHash.takeLast(8)
        )
        assertEquals(seal.truncatedHash, forged.truncatedHash) // display form unchanged
        assertEquals(
            VerificationResult.TAMPERED,
            EvidenceSealer.verify(bytes, forged, blockchainConfirmed = true)
        )
    }

    @Test
    fun verifyFallsBackToTruncatedHashWhenRecordLacksFullHash() {
        val bytes = "sealed evidence".toByteArray()
        val seal = EvidenceSealer.seal(bytes, "evidence", "VO-EV-1", fixedTime)
        val legacy = seal.copy(sha512 = "")
        assertEquals(
            VerificationResult.SEAL_FOUND_NO_CHAIN,
            EvidenceSealer.verify(bytes, legacy, blockchainConfirmed = false)
        )
        assertEquals(
            VerificationResult.TAMPERED,
            EvidenceSealer.verify("edited evidence".toByteArray(), legacy, blockchainConfirmed = false)
        )
    }
}
