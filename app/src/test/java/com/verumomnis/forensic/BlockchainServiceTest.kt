package com.verumomnis.forensic

import com.verumomnis.forensic.blockchain.BlockchainService
import com.verumomnis.forensic.blockchain.OpenTimestampsService
import com.verumomnis.forensic.model.OtsAnchorResult
import com.verumomnis.forensic.model.OtsStatus
import com.verumomnis.forensic.model.OtsVerifyResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockchainServiceTest {

    /** A deterministic in-memory [BlockchainService] for unit tests. */
    private class FakeBlockchainService : BlockchainService {
        override fun anchor(sha512Hex: String): OtsAnchorResult =
            OtsAnchorResult(
                status = OtsStatus.PENDING,
                sha512 = sha512Hex,
                sha256Digest = "fake-digest-${sha512Hex.take(8)}",
                calendarUrls = listOf("https://fake.calendar"),
                otsProofBase64 = "ZmFrZS1wcm9vZg==",
                otsProofFile = "seal_${sha512Hex.take(8)}.ots",
                submittedAt = "2026-07-12T10:00:00Z"
            )

        override fun verify(otsProofBase64: String): OtsVerifyResult =
            OtsVerifyResult(
                status = OtsStatus.PENDING,
                pending = true,
                bitcoinAttested = false,
                message = "Fake pending verification"
            )

        override fun upgrade(otsProofBase64: String): OtsAnchorResult =
            OtsAnchorResult(
                status = OtsStatus.CONFIRMED,
                sha512 = "",
                sha256Digest = "fake-digest",
                calendarUrls = emptyList(),
                otsProofBase64 = otsProofBase64,
                otsProofFile = "seal_confirmed.ots",
                submittedAt = "2026-07-12T10:00:00Z",
                message = "Fake upgrade confirmed"
            )
    }

    @Test
    fun openTimestampsServiceImplementsBlockchainService() {
        val service: BlockchainService = OpenTimestampsService
        val sha512 = "a".repeat(128)
        val result = service.anchor(sha512)
        assertTrue(result.status == OtsStatus.OFFLINE || result.status == OtsStatus.PENDING)
        assertEquals(sha512, result.sha512)
    }

    @Test
    fun fakeBlockchainServiceReturnsPendingAnchor() {
        val service: BlockchainService = FakeBlockchainService()
        val result = service.anchor("b".repeat(128))
        assertEquals(OtsStatus.PENDING, result.status)
        assertTrue(result.calendarUrls.isNotEmpty())
    }

    @Test
    fun fakeBlockchainServiceUpgradesToConfirmed() {
        val service: BlockchainService = FakeBlockchainService()
        val upgraded = service.upgrade("ZmFrZS1wcm9vZg==")
        assertEquals(OtsStatus.CONFIRMED, upgraded.status)
    }
}
