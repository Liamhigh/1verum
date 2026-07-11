package com.verumomnis.forensic

import com.verumomnis.forensic.crypto.EvidenceSealer
import com.verumomnis.forensic.crypto.Sha512
import com.verumomnis.forensic.crypto.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EvidenceSealerTest {

    private val fixedTime = Instant.parse("2026-07-06T14:35:00Z")

    @Test
    fun sealFooterMatchesSpecFormat() {
        val bytes = "forensic report body".toByteArray()
        val seal = EvidenceSealer.seal(bytes, "forensic_report", "VO-AF-2026-0608-FOR", fixedTime)
        val hash = Sha512.hash(bytes)

        assertEquals(hash.take(8), seal.shortcode)
        assertEquals(hash.take(16) + "..." + hash.takeLast(8), seal.truncatedHash)
        val footer = seal.sealFooter()
        assertTrue(footer.startsWith("VERUM OMNIS SEAL | seal-${seal.shortcode} | "))
        assertTrue(footer.endsWith("| ${seal.shortcode}"))
    }

    @Test
    fun verifyReturnsVerifiedWhenChainConfirmed() {
        val bytes = "sealed evidence".toByteArray()
        val seal = EvidenceSealer.seal(bytes, "evidence", "VO-EV-1", fixedTime)
        val result = EvidenceSealer.verify(bytes, seal, blockchainConfirmed = true)
        assertEquals(VerificationResult.VERIFIED, result)
    }

    @Test
    fun verifyDetectsTampering() {
        val bytes = "sealed evidence".toByteArray()
        val seal = EvidenceSealer.seal(bytes, "evidence", "VO-EV-1", fixedTime)
        val tampered = "sealed evidence (edited)".toByteArray()
        val result = EvidenceSealer.verify(tampered, seal, blockchainConfirmed = true)
        assertEquals(VerificationResult.TAMPERED, result)
    }

    @Test
    fun verifyReportsMissingChain() {
        val bytes = "sealed evidence".toByteArray()
        val seal = EvidenceSealer.seal(bytes, "evidence", "VO-EV-1", fixedTime)
        val result = EvidenceSealer.verify(bytes, seal, blockchainConfirmed = false)
        assertEquals(VerificationResult.SEAL_FOUND_NO_CHAIN, result)
    }
}
