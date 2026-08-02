package com.verumomnis.forensic.ojrs

import com.verumomnis.forensic.engine.contradiction.ContradictionToForensicAdapter
import com.verumomnis.forensic.engine.contradiction.EngineContradictionType
import com.verumomnis.forensic.engine.contradiction.EngineStatementType
import com.verumomnis.forensic.engine.contradiction.VerumContradictionEngine
import com.verumomnis.forensic.model.Contradiction
import com.verumomnis.forensic.model.ForensicFindings
import com.verumomnis.forensic.model.ResearchFindings
import java.time.Instant

/**
 * Pairs live OJRS judicial research against the sealed scan's evidence atoms, so that
 * sworn court statements can be cross-referenced against the user's own documents —
 * the mechanism ONLINE_JUDICIAL_RETRIEVAL.md calls "judicial vs. documentary" pairing.
 *
 * This is opt-in and runs strictly AFTER the sealed scan (mirrors [DeepResearchEngine]'s
 * user-triggered, suspend-function shape). It never touches [com.verumomnis.forensic.engine.ForensicService.scan] or
 * the evidence corpus hash — judicial text is processed through a separate, throwaway
 * [VerumContradictionEngine] instance and the results are surfaced as an advisory,
 * separately-numbered ("JX-####") report section, never folded into the sealed
 * contradiction numbering.
 *
 * Scope note: only [EngineContradictionType.JUDICIAL_VS_DOCUMENTARY]-family results are
 * kept. CONSCIOUSNESS_OF_GUILT (the 730-day rule) is deliberately excluded here — it
 * requires both sides of a pairing to carry a real date, and the sealed-corpus atoms fed
 * into this pipeline don't carry per-atom timestamps yet (see plan doc, Phase 1 note 3).
 */
object JudicialPairingService {

    /** Cap on how many OJRS judicial-case hits get a full-text fetch per pairing run. */
    private const val MAX_CASES_TO_FETCH = 5

    /** Cap on sealed evidence atoms fed into the pairing engine, to bound detector cost. */
    private const val MAX_SEALED_ATOMS = 3000

    private val JUDICIAL_PAIRING_TYPES = setOf(
        EngineContradictionType.JUDICIAL_VS_DOCUMENTARY,
        EngineContradictionType.PERJURY_BY_TIMELINE
    )

    /**
     * Fetch full text for [research]'s judicial cases and pair them against [findings]'
     * sealed evidence atoms. Returns an empty list if no judicial cases were found, none
     * of their full text could be fetched, or no pairings were detected — never throws.
     */
    suspend fun pair(
        findings: ForensicFindings,
        research: ResearchFindings,
        caseId: String = "VO-OJRS-PAIRING"
    ): List<Contradiction> {
        val judicialInputs = research.judicialCases
            .take(MAX_CASES_TO_FETCH)
            .mapNotNull { case ->
                JudicialTextFetcher.fetchOpinionText(case)?.let { text ->
                    VerumContradictionEngine.TaggedInput(
                        text = text,
                        sourceName = "OJRS:${case.database.name}:${case.citation.ifBlank { case.title }}",
                        forcedType = EngineStatementType.JUDICIAL_RECORD
                    )
                }
            }
        if (judicialInputs.isEmpty()) return emptyList()

        val sealedInputs = findings.evidenceAtoms
            .take(MAX_SEALED_ATOMS)
            .map { atom ->
                VerumContradictionEngine.TaggedInput(
                    text = atom.content,
                    sourceName = atom.sourceFile
                )
            }
        if (sealedInputs.isEmpty()) return emptyList()

        val engine = VerumContradictionEngine(caseId = caseId)
        val report = engine.processFromTaggedInputs(judicialInputs + sealedInputs)
        val judicialOnly = report.copy(
            contradictions = report.contradictions.filter { it.type in JUDICIAL_PAIRING_TYPES }
        )
        if (judicialOnly.contradictions.isEmpty()) return emptyList()

        return ContradictionToForensicAdapter.toContradictions(
            report = judicialOnly,
            now = Instant.now(),
            brainSource = "B1-JudicialPairing",
            idPrefix = "JX"
        )
    }
}
