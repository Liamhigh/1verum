package com.verumomnis.forensic

import android.app.Application
import com.verumomnis.forensic.engine.EngineUpdateReceiver
import com.verumomnis.forensic.model.EngineUpdate
import com.verumomnis.forensic.model.FraudKeywordPatch
import com.verumomnis.forensic.model.UpdatePatches
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/**
 * The rule-update signature check used to accept ANY update with a blank signature — see
 * EngineUpdateReceiver.applyUpdate step 3. This verifies the real RSA/SHA256withRSA check
 * that replaced it, using a throwaway test key pair (the real production private key for
 * Constitution.RULES_PUBLIC_KEY_DER_B64 is obviously not available to tests).
 */
@RunWith(RobolectricTestRunner::class)
class EngineUpdateSignatureTest {

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val testPublicKeyDerB64: String = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    private fun receiver(): EngineUpdateReceiver =
        EngineUpdateReceiver(RuntimeEnvironment.getApplication() as Application)

    private fun sign(bytes: ByteArray): String {
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
            update(bytes)
        }.sign()
        return Base64.getEncoder().encodeToString(signature)
    }

    private fun sampleUpdate(signature: String) = EngineUpdate(
        updateId = "UPD-20260726-001",
        version = "5.3.2",
        issuedAt = "2026-07-26T00:00:00Z",
        patches = UpdatePatches(fraudKeywords = listOf(FraudKeywordPatch(keyword = "wire immediately", weight = 30))),
        signature = signature
    )

    @Test
    fun validSignatureVerifies() {
        val receiver = receiver()
        val unsigned = sampleUpdate(signature = "")
        val signature = sign(receiver.canonicalSigningPayload(unsigned))
        val signed = unsigned.copy(signature = signature)

        assertTrue(receiver.verifySignature(signed, testPublicKeyDerB64))
    }

    @Test
    fun blankSignatureIsRejected() {
        val receiver = receiver()
        val update = sampleUpdate(signature = "")

        assertFalse(receiver.verifySignature(update, testPublicKeyDerB64))
    }

    @Test
    fun tamperedPatchAfterSigningIsRejected() {
        val receiver = receiver()
        val unsigned = sampleUpdate(signature = "")
        val signature = sign(receiver.canonicalSigningPayload(unsigned))
        val tampered = unsigned.copy(
            signature = signature,
            patches = UpdatePatches(fraudKeywords = listOf(FraudKeywordPatch(keyword = "wire immediately", weight = 100)))
        )

        assertFalse(receiver.verifySignature(tampered, testPublicKeyDerB64))
    }

    @Test
    fun signatureFromWrongKeyIsRejected() {
        val receiver = receiver()
        val otherKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val unsigned = sampleUpdate(signature = "")
        val wrongSignature = Signature.getInstance("SHA256withRSA").apply {
            initSign(otherKeyPair.private)
            update(receiver.canonicalSigningPayload(unsigned))
        }.sign()
        val update = unsigned.copy(signature = Base64.getEncoder().encodeToString(wrongSignature))

        assertFalse(receiver.verifySignature(update, testPublicKeyDerB64))
    }
}
