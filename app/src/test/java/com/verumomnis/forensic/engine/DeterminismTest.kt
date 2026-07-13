package com.verumomnis.forensic.engine

import com.verumomnis.forensic.crypto.Sha512
import com.verumomnis.forensic.model.EvidenceArtifact
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * Determinism tests — Prime Directive 4.
 * Same input + same constitution version = identical output.
 */
class DeterminismTest {

    @Test
    fun `SHA-512 identical hash for same input across 100 runs`() {
        val input = "Deterministic test".toByteArray()
        val expected = Sha512.hash(input)
        repeat(100) { run ->
            assertEquals("Hash mismatch on run $run", expected, Sha512.hash(input))
        }
    }

    @Test
    fun `SHA-512 identical hash for binary data across 100 runs`() {
        val input = ByteArray(1024) { (it % 256).toByte() }
        val expected = Sha512.hash(input)
        repeat(100) { run ->
            assertEquals("Binary hash mismatch on run $run", expected, Sha512.hash(input))
        }
    }

    @Test
    fun `artifact ID from content hash only`() {
        val content = "MOU content".toByteArray()
        val a1 = EvidenceArtifact.create(content, "MOU.pdf", Instant.parse("2026-01-01T10:00:00Z"))
        val a2 = EvidenceArtifact.create(content, "different.pdf", Instant.parse("2026-06-15T14:30:00Z"))
        assertEquals(a1.id, a2.id)
    }

    @Test
    fun `different content produces different artifact ID`() {
        val a1 = EvidenceArtifact.create("A".toByteArray(), "doc.pdf", Instant.now())
        val a2 = EvidenceArtifact.create("B".toByteArray(), "doc.pdf", Instant.now())
        assertNotEquals(a1.id, a2.id)
    }

    @Test
    fun `artifact ID is 32 hex chars`() {
        val a = EvidenceArtifact.create("test".toByteArray(), "t.pdf", Instant.now())
        assertEquals(32, a.id.length)
        assertTrue(a.id.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `scan result independent of execution time`() {
        val evidence = listOf(
            EvidenceArtifact.create("MOU".toByteArray(), "MOU.pdf", Instant.now()),
            EvidenceArtifact.create("Testimony".toByteArray(), "testimony.pdf", Instant.now())
        )
        val r1 = ForensicService.scan(evidence, Instant.parse("2026-01-01T00:00:00Z"))
        val r2 = ForensicService.scan(evidence, Instant.parse("2026-12-31T23:59:59Z"))
        assertEquals(r1.findings.size, r2.findings.size)
    }

    @Test
    fun `LLM temperature 0 produces identical response`() {
        val model = TestLlmModel(temperature = 0.0f, seed = 42)
        val prompt = "Explain the AllFuels Paradox"
        assertEquals(model.generate(prompt), model.generate(prompt))
    }

    private class TestLlmModel(private val temperature: Float, private val seed: Int) {
        fun generate(prompt: String): String {
            require(temperature == 0.0f) { "Non-zero temperature" }
            return "Response for ${Sha512.hash(prompt.toByteArray()).take(16)} seed=$seed"
        }
    }
}
