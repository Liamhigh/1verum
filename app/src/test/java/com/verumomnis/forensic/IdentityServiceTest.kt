package com.verumomnis.forensic

import android.app.Application
import com.verumomnis.forensic.crypto.EvidenceSealer
import com.verumomnis.forensic.identity.IdentityService
import com.verumomnis.forensic.identity.InMemoryIdentityKeyStore
import com.verumomnis.forensic.identity.UserIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class IdentityServiceTest {

    private fun service(): IdentityService {
        val app = RuntimeEnvironment.getApplication() as Application
        return IdentityService(app, InMemoryIdentityKeyStore(), nowProvider = { Instant.parse("2026-07-12T10:00:00Z") })
    }

    @Test
    fun initializeDeviceCreatesStableIdentity() {
        val service = service()
        val device = service.initializeDevice()
        assertNotNull(device.deviceId)
        assertTrue(device.deviceId.isNotBlank())
        assertTrue(device.publicKeyFingerprint.isNotBlank())

        val reloaded = service.deviceIdentity()
        assertEquals(device.deviceId, reloaded?.deviceId)
    }

    @Test
    fun signSealProducesVerifiableProof() {
        val service = service()
        service.initializeDevice()
        val seal = EvidenceSealer.sealFromHash(
            "a".repeat(128),
            documentType = "test",
            documentReference = "VO-TEST-20260712-FOR"
        )
        val proof = service.signSeal(seal)
        assertNotNull(proof)
        assertEquals(seal.sha512, proof!!.sealSha512)
        assertTrue(service.verifyProof(proof))
    }

    @Test
    fun userIdentityRoundTrip() {
        val service = service()
        service.setUser(UserIdentity(displayName = "Inspector", role = "investigator", publicKeyFingerprint = "fp-123"))
        val user = service.userIdentity()
        assertEquals("Inspector", user.displayName)
        assertEquals("investigator", user.role)
    }
}
