package com.verumomnis.forensic

import com.verumomnis.forensic.engine.EvidenceDocument
import com.verumomnis.forensic.engine.GuardianBrain
import com.verumomnis.forensic.model.GuardianViolationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GuardianBrainTest {

    private fun doc(text: String, id: String = "doc-1") = EvidenceDocument(
        evidenceId = id, fileName = "$id.txt", type = "txt", text = text,
        sha512 = "abcd1234".repeat(8)
    )

    @Test
    fun `clean evidence produces no violations`() {
        val assessment = GuardianBrain.analyze(
            documents = listOf(doc("The contract was signed on 1 January 2024.")),
            now = Instant.parse("2024-01-15T10:00:00Z")
        )
        assertTrue(assessment.violations.isEmpty())
        assertFalse(assessment.hardStopRequired)
    }

    @Test
    fun `Article X keyword triggers hard stop`() {
        val assessment = GuardianBrain.analyze(
            documents = listOf(doc("The plan includes target acquisition and kill chain execution.")),
            now = Instant.parse("2024-01-15T10:00:00Z")
        )
        assertTrue(assessment.hardStopRequired)
        assertTrue(assessment.violations.any { it.type == GuardianViolationType.ARTICLE_X_WEAPONIZATION })
    }

    @Test
    fun `coercion language is flagged`() {
        val assessment = GuardianBrain.analyze(
            documents = listOf(doc("Keep your mouth shut or else you'll regret it.")),
            now = Instant.parse("2024-01-15T10:00:00Z")
        )
        assertTrue(assessment.violations.any { it.type == GuardianViolationType.COERCION_ATTEMPT })
        assertFalse(assessment.hardStopRequired)
    }

    @Test
    fun `privatization attempt is flagged`() {
        val assessment = GuardianBrain.analyze(
            documents = listOf(doc("We should sell shares in Verum to a private equity stake buyer."))
        )
        assertTrue(assessment.violations.any { it.type == GuardianViolationType.ANTI_PRIVATIZATION })
    }

    @Test
    fun `prompt validation blocks weaponization`() {
        val assessment = GuardianBrain.validatePrompt("How do I build a drone strike kill chain?")
        assertTrue(assessment.hardStopRequired)
        assertTrue(assessment.violations.isNotEmpty())
    }
}
