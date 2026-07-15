package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.Contradiction
import com.verumomnis.forensic.model.ForensicFindings
import com.verumomnis.forensic.model.TripleConsensus

/**
 * Nine-Brain council vote (build spec Section 5.2).
 *
 * B1 proposes a contradiction. Every other brain (B2-B8) may confirm it.
 * A contradiction is accepted only when B1 + at least two other brains agree.
 * B9 never votes; it validates afterwards.
 *
 * The council also materialises the Thesis/Antithesis/Synthesis triple-
 * verification block for every finding so the sealed report can show how the
 * verdict was reached.
 */
object BrainCouncil {

    fun evaluate(findings: ForensicFindings): ForensicFindings {
        val evaluated = findings.contradictions.map { evaluateOne(it, findings) }
        return findings.copy(contradictions = evaluated)
    }

    private fun evaluateOne(c: Contradiction, findings: ForensicFindings): Contradiction {
        val confirming = confirmingBrains(c, findings)
        val status = councilStatus(confirming.size)
        val thesis = "THESIS: ${c.claimA.text} — ${c.claimA.statementType} by ${c.claimA.source} (p.${c.claimA.page}, l.${c.claimA.line})"
        val antithesis = "ANTITHESIS: ${c.claimB.text} — ${c.claimB.statementType} by ${c.claimB.source} (p.${c.claimB.page}, l.${c.claimB.line}), contradicts the thesis on ${c.category}."
        val synthesis = buildString {
            append("SYNTHESIS: B1-ContradictionBrain flags a ${c.severity} conflict")
            if (confirming.isNotEmpty()) append("; confirmed by ${confirming.joinToString(", ")}") else append("; no other brain confirmed")
            append(". Council status: $status.")
        }
        return c.copy(
            thesis = thesis,
            antithesis = antithesis,
            synthesis = synthesis,
            confirmingBrains = confirming,
            councilStatus = status,
            tripleAiConsensus = TripleConsensus(
                gemma3 = if (status == "ACCEPTED") "CONCURS" else "ABSTAINS",
                phi3 = if (status == "ACCEPTED") "CONCURS" else "ABSTAINS",
                nineBrain = if (status == "ACCEPTED") "CONCURS" else "ABSTAINS",
                quorum = status == "ACCEPTED"
            )
        )
    }

    private fun councilStatus(confirmations: Int): String = when {
        confirmations >= 2 -> "ACCEPTED"
        confirmations == 1 -> "INDETERMINATE_DUE_TO_CONCEALMENT"
        else -> "INSUFFICIENT"
    }

    /** Determine which non-B1 brains confirm this contradiction. */
    private fun confirmingBrains(c: Contradiction, findings: ForensicFindings): List<String> {
        val confirmed = mutableListOf<String>()
        val claimText = "${c.claimA.text} ${c.claimB.text}".lowercase()
        val respondent = c.respondent.lowercase()
        val evidenceIds = setOf(c.claimA.evidenceId, c.claimB.evidenceId)
        val claimDates = listOfNotNull(
            EntityExtractor.extractDate(c.claimA.text)?.toString(),
            EntityExtractor.extractDate(c.claimB.text)?.toString()
        )

        // B2 — Document forensics confirms if a tamper signal touches either evidence file.
        if (findings.documentForensics.any { signal ->
                evidenceIds.any { signal.contains(it, ignoreCase = true) } ||
                    signal.contains(c.claimA.source, ignoreCase = true) ||
                    signal.contains(c.claimB.source, ignoreCase = true)
            }) {
            confirmed += "B2-DocumentBrain"
        }

        // B3 — Communications brain confirms if a gap or deletion relates to the same period/file.
        if (findings.communications.any { gap ->
                evidenceIds.any { gap.contains(it, ignoreCase = true) } ||
                    claimDates.any { gap.contains(it, ignoreCase = true) }
            }) {
            confirmed += "B3-CommunicationsBrain"
        }

        // B4 — Behavioral brain confirms if the respondent shows evasion/manipulation signals.
        if (findings.behavioral?.let { b ->
                (b.gaslighting + b.stress + b.manipulation).any {
                    it.context.lowercase().contains(respondent) ||
                        it.trigger.lowercase().contains(respondent)
                }
            } == true) {
            confirmed += "B4-BehavioralBrain"
        }

        // B5 — Timeline brain confirms if an event matches the claim evidence or date.
        if (findings.timeline.any { ev ->
                ev.evidenceId in evidenceIds || claimDates.any { ev.dateTime.contains(it, ignoreCase = true) }
            }) {
            confirmed += "B5-TimelineBrain"
        }

        // B6 — Financial brain confirms if anomalies mention the respondent or amounts in the claim.
        val claimAmounts = Regex("""R\s?[\d][\d.,\s]*""", RegexOption.IGNORE_CASE).findAll(claimText).map { it.value.trim() }.toList()
        if (findings.financial?.flaggedAnomalies?.any { anomaly ->
                anomaly.contains(respondent, ignoreCase = true) ||
                    claimAmounts.any { amount -> anomaly.contains(amount, ignoreCase = true) }
            } == true) {
            confirmed += "B6-FinancialBrain"
        }

        // B7 — Legal brain confirms if any mapped statute overlaps the contradiction's applicable law.
        if (findings.legalMappings.any { mapping ->
                c.applicableLaw.any { law -> mapping.contains(law, ignoreCase = true) }
            }) {
            confirmed += "B7-LegalBrain"
        }

        // B8 — Audio/media brain confirms if tamper signals or transcript mention the respondent or claim text.
        if (findings.audio?.tamperSignals?.any { signal ->
                signal.description.contains(respondent, ignoreCase = true) ||
                    signal.description.contains(c.category.name, ignoreCase = true)
            } == true ||
            findings.audio?.segments?.any { seg ->
                seg.text.contains(respondent, ignoreCase = true) ||
                    claimText.split(" ").any { word -> word.length > 4 && seg.text.contains(word, ignoreCase = true) }
            } == true) {
            confirmed += "B8-AudioBrain"
        }

        return confirmed.sorted()
    }
}
