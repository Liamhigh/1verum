package com.verumomnis.forensic.engine.contradiction

import com.verumomnis.forensic.model.Contradiction
import com.verumomnis.forensic.model.ContradictionCategory
import com.verumomnis.forensic.model.ContradictionClaim
import com.verumomnis.forensic.model.ContradictionType
import com.verumomnis.forensic.model.Confidence
import com.verumomnis.forensic.model.ExtractedPerson
import com.verumomnis.forensic.model.ForensicFindings
import com.verumomnis.forensic.model.PersonRole
import com.verumomnis.forensic.model.Severity
import com.verumomnis.forensic.model.StatementType
import com.verumomnis.forensic.model.TripleConsensus
import java.time.Instant

/**
 * Adapts the hybrid [VerumContradictionEngine] output into the canonical
 * [com.verumomnis.forensic.model] types used by the Nine-Brain pipeline.
 *
 * The adapter is intentionally deterministic: the same engine report always
 * produces the same canonical findings, preserving the seal-chain guarantee.
 */
object ContradictionToForensicAdapter {

    fun toContradictions(report: EngineForensicReport, now: Instant = Instant.now()): List<Contradiction> =
        report.contradictions.map { toContradiction(it, now) }

    fun toExtractedPersons(report: EngineForensicReport): List<ExtractedPerson> =
        report.actorProfiles.map { toExtractedPerson(it) }

    /**
 * Builds a minimal canonical [ForensicFindings] from an engine report.
 * Other fields are left empty because the Nine-Brain engine fills them
 * from the remaining brains (B2-B9).
 */
    fun toForensicFindings(
        report: EngineForensicReport,
        now: Instant = Instant.now(),
        documentsAnalyzed: Int = 0
    ): ForensicFindings = ForensicFindings(
        documentsAnalyzed = documentsAnalyzed,
        evidenceAtoms = emptyList(),
        contradictions = toContradictions(report, now),
        timeline = emptyList(),
        legalMappings = emptyList(),
        jurisdiction = "",
        extractedPersons = toExtractedPersons(report),
        brainVerdicts = mapOf("B1-Contradiction" to if (report.contradictions.isEmpty()) "CLEAR" else "${report.contradictions.size} FOUND")
    )

    private fun toContradiction(c: EngineContradiction, now: Instant): Contradiction {
        val (sourceA, sourceB) = splitSources(c.detectedFact.sourceDocument)
        val claimA = ContradictionClaim(
            text = c.propositionAText.ifBlank { c.detectedFact.factText },
            source = sourceA,
            evidenceId = sourceA,
            page = c.detectedFact.sourcePage,
            line = c.detectedFact.sourceLine,
            sha512 = c.detectedFact.sha512Hash,
            statementType = StatementType.CLAIM
        )
        val claimB = ContradictionClaim(
            text = c.propositionBText.ifBlank { "(no counter-proposition recorded)" },
            source = sourceB,
            evidenceId = sourceB,
            page = c.detectedFact.sourcePage,
            line = c.detectedFact.sourceLine,
            sha512 = c.detectedFact.sha512Hash,
            statementType = StatementType.DENIAL
        )
        return Contradiction(
            contradictionId = c.contradictionId,
            brainSource = "B1-HybridContradiction",
            category = mapCategory(c.type, c.conflictDescription),
            type = mapType(c.type),
            respondent = c.propositionAActor.ifBlank { c.propositionBActor.ifBlank { "unknown" } },
            claimA = claimA,
            claimB = claimB,
            severity = mapSeverity(c.severity, c.logicalPattern.contradictionScore),
            description = c.conflictDescription,
            legalSignificance = c.logicalPattern.patternDescription,
            confidence = mapConfidence(c.confidence),
            timestamp = now.toString(),
            engineType = c.type.name,
            tripleAiConsensus = TripleConsensus(
                gemma3 = if (c.verificationStatus["gemma3"] == "CONCURS") "CONCURS" else "ABSTAINS",
                phi3 = if (c.verificationStatus["phi3"] == "CONCURS") "CONCURS" else "ABSTAINS",
                nineBrain = if (c.verificationStatus["nineBrain"] == "CONCURS") "CONCURS" else "ABSTAINS",
                quorum = c.verificationStatus["quorum"] == "true"
            )
        )
    }

    private fun splitSources(sourceDocument: String): Pair<String, String> {
        val parts = sourceDocument.split(" + ").map { it.trim() }
        return when {
            parts.size >= 2 -> parts[0] to parts[1]
            parts.size == 1 -> parts[0] to parts[0]
            else -> "unknown" to "unknown"
        }
    }

    private fun toExtractedPerson(profile: ActorProfile): ExtractedPerson = ExtractedPerson(
        name = profile.name,
        role = if (profile.dishonestyScore > 50) PersonRole.RESPONDENT else PersonRole.UNKNOWN,
        context = "Dishonesty score ${profile.dishonestyScore}/100; flags: ${profile.flags.joinToString(", ")}"
    )

    private fun mapCategory(type: EngineContradictionType, context: String): ContradictionCategory {
        val text = (type.name + " " + context).lowercase()
        return when {
            type == EngineContradictionType.TACIT_LEASE_VIOLATION ||
                type == EngineContradictionType.POST_EXPIRY_ENFORCEMENT ||
                type == EngineContradictionType.SHAM_TRANSACTION ||
                type == EngineContradictionType.ACKNOWLEDGE_THEN_DENY ||
                type == EngineContradictionType.OWNERSHIP_MISREPRESENTATION ||
                type == EngineContradictionType.NO_COUNTERSIGNATURE_TRAP ||
                type == EngineContradictionType.MANUFACTURED_CONSENT -> ContradictionCategory.CONTRACT_VALIDITY
            type == EngineContradictionType.FINANCIAL_IRREGULARITY ||
                type == EngineContradictionType.EXTORTION ||
                type == EngineContradictionType.EMBEZZLEMENT ||
                type == EngineContradictionType.TAX_EVASION ||
                type == EngineContradictionType.MONEY_LAUNDERING ||
                type == EngineContradictionType.GOODWILL_FORFEITURE_SWINDLE ||
                text.contains("fee") || text.contains("payment") || text.contains("rent") || text.contains("compensation") ->
                ContradictionCategory.COMPENSATION
            type == EngineContradictionType.PERJURY_BY_TIMELINE ||
                type == EngineContradictionType.JUDICIAL_VS_DOCUMENTARY ||
                type == EngineContradictionType.FALSE_ALLEGATION_IN_AFFIDAVIT ||
                type == EngineContradictionType.DEFECTIVE_JURAT ||
                type == EngineContradictionType.FRAUD_ON_THE_COURT ||
                type == EngineContradictionType.FABRICATED_DECOY_EVIDENCE ||
                type == EngineContradictionType.ATTORNEY_OBSTRUCTION ||
                text.contains("sworn") || text.contains("affidavit") || text.contains("jurat") || text.contains("constitutional court") ->
                ContradictionCategory.PERJURY
            type == EngineContradictionType.WITNESS_INTIMIDATION ||
                type == EngineContradictionType.PROTECTION_ORDER_AS_LEVERAGE ||
                type == EngineContradictionType.CHARACTER_ASSASSINATION ||
                type == EngineContradictionType.BRIBERY_CORRUPTION ||
                type == EngineContradictionType.EXTORTION ||
                type == EngineContradictionType.DEFAMATION_THREAT ||
                text.contains("threat") || text.contains("intimidate") || text.contains("coerce") || text.contains("suppress") ->
                ContradictionCategory.COERCION
            type == EngineContradictionType.STATEMENT_VS_EVIDENCE && text.contains("goodwill") ->
                ContradictionCategory.GOODWILL_VALUE
            text.contains("goodwill") || text.contains("franchise") || text.contains("brand") ->
                ContradictionCategory.GOODWILL_VALUE
            text.contains("signature") || text.contains("signed") || text.contains("unsigned") || text.contains("countersign") ->
                ContradictionCategory.SIGNATURE_STATUS
            text.contains("section 12b") || text.contains("arbitration") || text.contains("referral") ->
                ContradictionCategory.SECTION_12B
            else -> ContradictionCategory.OTHER
        }
    }

    private fun mapType(type: EngineContradictionType): ContradictionType = when (type) {
        EngineContradictionType.TEMPORAL_CONTRADICTION,
        EngineContradictionType.TEMPORAL_PRECEDENCE_CONFLICT,
        EngineContradictionType.CONSCIOUSNESS_OF_GUILT -> ContradictionType.TEMPORAL_SHIFT
        EngineContradictionType.STATEMENT_VS_EVIDENCE,
        EngineContradictionType.FINANCIAL_IRREGULARITY,
        EngineContradictionType.JUDICIAL_VS_DOCUMENTARY,
        EngineContradictionType.FABRICATED_DECOY_EVIDENCE,
        EngineContradictionType.DATA_BREACH_ENABLED_FRAUD,
        EngineContradictionType.SPOLIATION_OF_EVIDENCE -> ContradictionType.ACTION_VS_WORDS
        EngineContradictionType.BEHAVIORAL,
        EngineContradictionType.ACKNOWLEDGE_THEN_DENY,
        EngineContradictionType.OWNERSHIP_MISREPRESENTATION,
        EngineContradictionType.COSTING_MANIPULATION,
        EngineContradictionType.CONFLICT_OF_INTEREST,
        EngineContradictionType.ATTORNEY_OBSTRUCTION,
        EngineContradictionType.TECHNOLOGY_REFUSAL_LIABILITY -> ContradictionType.ROLE_INCONSISTENCY
        EngineContradictionType.STATEMENT_VS_STATEMENT,
        EngineContradictionType.OMISSION,
        EngineContradictionType.NO_COUNTERSIGNATURE_TRAP -> ContradictionType.DIRECT_NEGATION
        else -> ContradictionType.IMPLIED_CONTRADICTION
    }

    private fun mapSeverity(severity: EngineSeverity, score: Double): Severity = when (severity) {
        EngineSeverity.VERY_HIGH -> if (score >= 0.95) Severity.CRITICAL else Severity.VERY_HIGH
        EngineSeverity.HIGH -> Severity.HIGH
        EngineSeverity.MODERATE -> Severity.MODERATE
        EngineSeverity.LOW -> Severity.LOW
        EngineSeverity.INSUFFICIENT -> Severity.LOW
    }

    private fun mapConfidence(c: EngineConfidence): Confidence = when (c) {
        EngineConfidence.DETERMINISTIC -> Confidence.VERY_HIGH
        EngineConfidence.VERY_HIGH -> Confidence.VERY_HIGH
        EngineConfidence.HIGH -> Confidence.HIGH
        EngineConfidence.MODERATE -> Confidence.MODERATE
        EngineConfidence.LOW -> Confidence.LOW
        EngineConfidence.INSUFFICIENT -> Confidence.INSUFFICIENT
    }
}
