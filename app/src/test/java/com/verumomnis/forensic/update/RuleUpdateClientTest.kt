package com.verumomnis.forensic.update

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64

/**
 * Version-gating and signature-verification tests for the rule-update client.
 * Uses throwaway in-test RSA key pairs — never touches the live service.
 */
class RuleUpdateClientTest {

    // ---------- semver ----------

    @Test
    fun newerPatchVersionIsNewer() {
        assertTrue(RuleUpdateClient.isNewerVersion("1.0.1", "1.0.0"))
    }

    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(RuleUpdateClient.isNewerVersion("1.0.0", "1.0.0"))
    }

    @Test
    fun newerMajorIsNewer() {
        assertTrue(RuleUpdateClient.isNewerVersion("2.0.0", "1.9.9"))
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(RuleUpdateClient.isNewerVersion("1.0.0", "1.0.1"))
    }

    @Test
    fun anyValidVersionIsNewerThanNoInstall() {
        assertTrue(RuleUpdateClient.isNewerVersion("0.0.1", null))
    }

    @Test
    fun malformedRemoteVersionIsRejected() {
        assertFalse(RuleUpdateClient.isNewerVersion("1.0", "0.9.9"))
        assertFalse(RuleUpdateClient.isNewerVersion("not-a-version", null))
        assertNull(RuleUpdateClient.parseVersion("1.0.0-rc1"))
        assertNull(RuleUpdateClient.parseVersion("01.0.0"))
    }

    @Test
    fun parseVersionExtractsComponents() {
        assertArrayEquals(longArrayOf(12, 3, 456), RuleUpdateClient.parseVersion("12.3.456"))
    }

    // ---------- signature verification (throwaway in-test key pair) ----------

    private class TestKeyMaterial(val publicDerB64: String, val privateKey: PrivateKey)

    private fun generateKeyMaterial(): TestKeyMaterial {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()
        return TestKeyMaterial(
            Base64.getEncoder().encodeToString(pair.public.encoded),
            pair.private
        )
    }

    private fun sign(privateKey: PrivateKey, bytes: ByteArray): String {
        val signer = Signature.getInstance("SHA512withRSA")
        signer.initSign(privateKey)
        signer.update(bytes)
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    private val canonicalPackage = """{"rules":{},"version":"9.9.9"}""".toByteArray(Charsets.UTF_8)

    @Test
    fun correctlySignedPackageVerifies() {
        val key = generateKeyMaterial()
        val signature = sign(key.privateKey, canonicalPackage)
        assertTrue(RuleUpdateClient.verifySignature(canonicalPackage, signature, key.publicDerB64))
    }

    @Test
    fun tamperedPackageIsRejected() {
        val key = generateKeyMaterial()
        val signature = sign(key.privateKey, canonicalPackage)
        val tampered = """{"rules":{},"version":"9.9.8"}""".toByteArray(Charsets.UTF_8)
        assertFalse(RuleUpdateClient.verifySignature(tampered, signature, key.publicDerB64))
    }

    @Test
    fun signatureFromDifferentKeyIsRejected() {
        val signingKey = generateKeyMaterial()
        val otherKey = generateKeyMaterial()
        val signature = sign(signingKey.privateKey, canonicalPackage)
        assertFalse(RuleUpdateClient.verifySignature(canonicalPackage, signature, otherKey.publicDerB64))
    }

    @Test
    fun truncatedSignatureIsRejected() {
        val key = generateKeyMaterial()
        val signature = sign(key.privateKey, canonicalPackage)
        val truncated = signature.dropLast(8) + "AAAAAAA="
        assertFalse(RuleUpdateClient.verifySignature(canonicalPackage, truncated, key.publicDerB64))
    }
}
