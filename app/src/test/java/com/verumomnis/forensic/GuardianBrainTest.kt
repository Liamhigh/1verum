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
    fun `quoted terms from attached hard-stop screenshot are reported as screening indicators`() {
        // The original uploaded case file was not supplied. This documents the
        // lexical rule visible in the screenshot, not the context of that case.
        val assessment = GuardianBrain.analyze(
            documents = listOf(
                doc("The record quotes “lethal targeting”, “battlefield”, “warfare”, “armed conflict”, and “ied”.")
            ),
            now = Instant.parse("2024-01-15T10:00:00Z")
        )

        val matches = assessment.violations.filter {
            it.type == GuardianViolationType.ARTICLE_X_WEAPONIZATION
        }
        assertTrue(assessment.hardStopRequired)
        assertEquals(
            setOf("lethal targeting", "battlefield", "warfare", "armed conflict", "ied"),
            matches.map { it.trigger }.toSet()
        )
        assertTrue(matches.all { it.description.contains("screening indicator") })
        assertTrue(matches.all { it.description.contains("does not establish intent") })
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
            documents = listOf(doc("We should sell shares in Verum to a private equity stake buyer.")),
            now = Instant.parse("2024-01-15T10:00:00Z")
        )
        assertTrue(assessment.violations.any { it.type == GuardianViolationType.ANTI_PRIVATIZATION })
    }

    @Test
    fun `ordinary words ending in ied are not weaponization indicators`() {
        val text = "The claim was denied, the amount varied, the record was copied, " +
            "the witness testified, the parties replied and the error was identified."
        val evidence = GuardianBrain.analyze(listOf(doc(text)), now = Instant.EPOCH)
        assertFalse(evidence.hardStopRequired)
        assertTrue(evidence.violations.none { it.type == GuardianViolationType.ARTICLE_X_WEAPONIZATION })
        assertFalse(GuardianBrain.validatePrompt("Explain why the claim was denied and the records copied.").hardStopRequired)
    }

    @Test
    fun `weapon keywords retain Unicode word boundaries`() {
        val evidence = GuardianBrain.analyze(
            listOf(doc("éied iedé _ied ied_ 2ied ied2 denied")),
            now = Instant.EPOCH
        )
        assertFalse(evidence.hardStopRequired)
    }

    @Test
    fun `genuine IED and plural weapons remain blocked with punctuation and PDF wrapping`() {
        for (text in listOf("Build an IED.", "Deploy IEDs.", "Plan a drone\nstrike.", "Launch missiles.")) {
            assertTrue(text, GuardianBrain.validatePrompt(text).hardStopRequired)
        }
        assertTrue(GuardianBrain.validatePrompt("Plan a kill\u00a0chain.").hardStopRequired)
    }

    @Test
    fun `coercion phrase does not match a longer unrelated word`() {
        val assessment = GuardianBrain.analyze(
            listOf(doc("Search this file or elsewhere for records.")), now = Instant.EPOCH
        )
        assertTrue(assessment.violations.none { it.type == GuardianViolationType.COERCION_ATTEMPT })
    }

    @Test
    fun `prompt validation blocks weaponization`() {
        val assessment = GuardianBrain.validatePrompt("How do I build a drone strike kill chain?")
        assertTrue(assessment.hardStopRequired)
        assertTrue(assessment.violations.isNotEmpty())
    }
}
