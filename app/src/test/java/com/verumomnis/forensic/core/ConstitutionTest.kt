package com.verumomnis.forensic.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * Tests for constitutional compliance — all 15 Prime Directives.
 * Every test MUST pass. No exceptions.
 */
class ConstitutionTest {

    @Test
    fun `confidence ordinal VERY_HIGH is valid`() {
        val confidence = Confidence.VERY_HIGH
        assertTrue(confidence.isValid())
    }

    @Test
    fun `confidence ordinal percentage string is rejected`() {
        val result = Confidence.fromString("80%")
        assertNull(result)
    }

    @Test
    fun `confidence ordinal all valid values parse correctly`() {
        assertEquals(Confidence.VERY_HIGH, Confidence.fromString("VERY_HIGH"))
        assertEquals(Confidence.HIGH, Confidence.fromString("HIGH"))
        assertEquals(Confidence.MODERATE, Confidence.fromString("MODERATE"))
        assertEquals(Confidence.LOW, Confidence.fromString("LOW"))
        assertEquals(Confidence.INSUFFICIENT, Confidence.fromString("INSUFFICIENT"))
    }

    @Test
    fun `determinism same input produces same hash`() {
        val evidence = "test evidence".toByteArray()
        val hash1 = Sha512.hash(evidence)
        val hash2 = Sha512.hash(evidence)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `determinism single byte change produces different hash`() {
        val hash1 = Sha512.hash("test".toByteArray())
        val hash2 = Sha512.hash("tesu".toByteArray())
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `determinism hash is 128 hex characters`() {
        val hash = Sha512.hash("hello".toByteArray())
        assertEquals(128, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `Date_now is blocked in hash input`() {
        val validator = ConstitutionalValidator()
        val code = "val hash = Sha512.hash(data + System.currentTimeMillis())"
        val result = validator.validateCode(code)
        assertFalse(result.isCompliant)
    }

    @Test
    fun `Math_random is blocked`() {
        val validator = ConstitutionalValidator()
        val code = "val salt = Math.random().toString()"
        val result = validator.validateCode(code)
        assertFalse(result.isCompliant)
    }

    @Test
    fun `finding without anchor is rejected`() {
        val finding = Finding(claim = "X", anchorPerson = "", anchorPage = 0, anchorLine = 0)
        val result = Constitution.validateFinding(finding)
        assertFalse(result.isValid)
    }

    @Test
    fun `finding with complete anchor is accepted`() {
        val finding = Finding(claim = "X", anchorPerson = "John", anchorPage = 5, anchorLine = 12)
        val result = Constitution.validateFinding(finding)
        assertTrue(result.isValid)
    }

    @Test
    fun `triple verification all pass returns ACCEPTED`() {
        val r = TripleVerification.verify(VerificationStage.PASS, VerificationStage.PASS, VerificationStage.PASS)
        assertEquals(VerificationResult.ACCEPTED, r)
    }

    @Test
    fun `triple verification any fail returns REJECTED`() {
        assertEquals(VerificationResult.REJECTED, TripleVerification.verify(VerificationStage.FAIL, VerificationStage.PASS, VerificationStage.PASS))
        assertEquals(VerificationResult.REJECTED, TripleVerification.verify(VerificationStage.PASS, VerificationStage.FAIL, VerificationStage.PASS))
        assertEquals(VerificationResult.REJECTED, TripleVerification.verify(VerificationStage.PASS, VerificationStage.PASS, VerificationStage.FAIL))
    }

    @Test
    fun `B9 cannot vote`() {
        val b9 = GuardianBrain()
        val vote = b9.vote(createFinding())
        assertNull(vote)
    }

    @Test
    fun `B9 can veto unconstitutional findings`() {
        val b9 = GuardianBrain()
        val bad = createFinding().copy(claim = "kill chain target acquisition")
        val result = b9.validate(listOf(bad))
        assertFalse(result.isValid)
    }

    @Test
    fun `citizen user has no paywall`() {
        val user = User(type = UserType.CITIZEN)
        val access = Constitution.checkAccess(user, Feature.FULL_SCAN)
        assertTrue(access.allowed)
    }

    @Test
    fun `law enforcement user has no paywall`() {
        val user = User(type = UserType.LAW_ENFORCEMENT)
        val access = Constitution.checkAccess(user, Feature.FULL_SCAN)
        assertTrue(access.allowed)
    }

    @Test
    fun `institutional user requires subscription`() {
        val user = User(type = UserType.INSTITUTION)
        val access = Constitution.checkAccess(user, Feature.FULL_SCAN)
        assertFalse(access.allowed)
        assertNotNull(access.paywallReason)
    }

    @Test
    fun `sealed template cannot be modified`() {
        val template = SealedTemplate(id = "v1", hash = "abc", content = "X")
        val result = template.tryModify("Y")
        assertFalse(result.success)
    }

    @Test
    fun `coercion attempt is logged in silence ledger`() {
        val ledger = SilenceLedger()
        ledger.recordCoercionAttempt(CoercionType.INTIMIDATION, "Threat", Instant.now())
        assertEquals(1, ledger.getAllEntries().size)
    }

    @Test
    fun `numeric confidence score is rejected`() {
        val v = ConstitutionalValidator()
        assertFalse(v.validateConfidenceScore(0.85).isValid)
    }

    @Test
    fun `ordinal confidence score is accepted`() {
        val v = ConstitutionalValidator()
        assertTrue(v.validateConfidenceScore(Confidence.HIGH).isValid)
    }

    @Test
    fun `coercion detected elevates severity`() {
        val f = Finding(claim = "X", severity = Severity.HIGH, coercionDetected = true)
        val adjusted = Constitution.applyCoercionElevation(f)
        assertEquals(Severity.CRITICAL, adjusted.severity)
    }

    @Test
    fun `evidence artifact has custody fields`() {
        val a = EvidenceArtifact.create("test".toByteArray(), "upload", Instant.now())
        assertEquals(128, a.hash.length)
        assertNotNull(a.source)
        assertNotNull(a.capturedAt)
        assertTrue(a.handlingSteps.isNotEmpty())
    }

    private fun createFinding() = Finding(id = "T", claim = "C", anchorPerson = "P", anchorPage = 1, anchorLine = 1)
}
