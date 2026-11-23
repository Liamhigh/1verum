package com.verumomnis.forensic

import com.verumomnis.forensic.model.OtsStatus
import com.verumomnis.forensic.model.OtsVerifyResult
import com.verumomnis.forensic.ui.SealTimestampState
import com.verumomnis.forensic.ui.sealTimestampPresentation
import org.junit.Assert.*
import org.junit.Test

class SealTimestampPresentationTest {
    @Test fun noSubmissionAndOfflineNeverClaimPendingOrOfferProof() {
        listOf(null, OtsStatus.OFFLINE).forEach {
            val display = sealTimestampPresentation(it, null, false)
            assertEquals(SealTimestampState.NOT_SUBMITTED, display.state)
            assertFalse(display.canDownloadProof)
            assertTrue(display.title.contains("not submitted"))
            assertTrue(display.description.contains("A hash alone is not independent proof of time or legal admissibility."))
        }
    }

    @Test fun pendingNeedsSavedBytesAndDoesNotPromiseAConfirmationTime() {
        val display = sealTimestampPresentation(OtsStatus.PENDING, null, true)
        assertEquals(SealTimestampState.PENDING, display.state)
        assertTrue(display.canDownloadProof)
        assertTrue(display.description.contains("no completion time is guaranteed"))
        assertFalse(display.description.contains("1–2"))
        assertEquals(SealTimestampState.FAILED, sealTimestampPresentation(OtsStatus.PENDING, null, false).state)
    }

    @Test fun confirmationNeedsRecordedBitcoinAttestationAndProofBytes() {
        val verified = OtsVerifyResult(OtsStatus.CONFIRMED, pending = false, bitcoinAttested = true)
        assertEquals(SealTimestampState.PENDING, sealTimestampPresentation(OtsStatus.CONFIRMED, null, true).state)
        assertEquals(SealTimestampState.CONFIRMED, sealTimestampPresentation(OtsStatus.PENDING, verified, true).state)
        assertEquals(SealTimestampState.FAILED, sealTimestampPresentation(OtsStatus.CONFIRMED, verified, false).state)
        assertNotEquals(SealTimestampState.CONFIRMED, sealTimestampPresentation(
            OtsStatus.CONFIRMED, verified.copy(bitcoinAttested = false), true
        ).state)
    }

    @Test fun failedCheckDoesNotDiscardSavedBytesOrClaimConfirmation() {
        val failed = OtsVerifyResult(OtsStatus.FAILED, pending = false, bitcoinAttested = false)
        val display = sealTimestampPresentation(OtsStatus.PENDING, failed, true)
        assertEquals(SealTimestampState.FAILED, display.state)
        assertTrue(display.canDownloadProof)
        assertTrue(display.description.contains("inspection"))
        assertFalse(sealTimestampPresentation(OtsStatus.FAILED, null, false).canDownloadProof)
    }

    @Test fun offlineRecheckDoesNotEraseAnEarlierPendingSubmission() {
        val unavailable = OtsVerifyResult(OtsStatus.OFFLINE, pending = false, bitcoinAttested = false)
        val display = sealTimestampPresentation(OtsStatus.PENDING, unavailable, true)
        assertEquals(SealTimestampState.PENDING, display.state)
        assertTrue(display.description.contains("latest proof check was unavailable"))
    }

    @Test fun unattributedProofBytesAreDownloadableButDoNotEstablishSubmission() {
        val display = sealTimestampPresentation(null, null, true)
        assertEquals(SealTimestampState.NOT_SUBMITTED, display.state)
        assertTrue(display.canDownloadProof)
        assertTrue(display.title.contains("not confirmed"))
    }
}
