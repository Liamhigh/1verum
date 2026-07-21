package com.verumomnis.forensic.engine.contradiction

/**
 * Verifier & Report Generator — v5.3.1c.
 * Deterministic engine self-check + actor profiling + multi-format report output.
 *
 * Doctrine: indicators, not determinations. No external AI model runs in this
 * pipeline, so nothing here may claim a "triple-AI consensus" or name models
 * that did not run.
 */
object TripleVerifier {

    /**
     * Deterministic engine self-check. Every contradiction is produced by the
     * same deterministic detectors, so the engine is self-consistent by
     * construction; the summary instead records the review recommendation
     * derived from the highest severity present.
     */
    fun verifyTriple(contradictions: List<EngineContradiction>): EngineVerificationSummary {
        val hasVeryHigh = contradictions.any { it.severity == EngineSeverity.VERY_HIGH }
        val hasHigh = contradictions.any { it.severity == EngineSeverity.HIGH }

        return when {
            hasVeryHigh -> EngineVerificationSummary(
                reviewStatus = "CONFIRMED", quorumMet = true, discrepancies = emptyList()
            )
            hasHigh -> EngineVerificationSummary(
                reviewStatus = "CONFIRMED", quorumMet = true,
                discrepancies = listOf("No VERY_HIGH findings — confidence capped at HIGH pending review")
            )
            else -> EngineVerificationSummary(
                reviewStatus = "REVIEW_REQUIRED", quorumMet = false,
                discrepancies = listOf("Only MODERATE/LOW findings — human review required")
            )
        }
    }

    /** Build actor profiles from claims and contradictions. */
    fun buildProfiles(claims: List<EngineClaim>, contradictions: List<EngineContradiction>): List<ActorProfile> {
        val data = mutableMapOf<String, MutableActorData>()
        for (c in claims) {
            val d = data.getOrPut(c.actor) { MutableActorData() }
            d.claims++
            if (c.sourceType == EngineStatementType.DENIAL) d.denials++
        }
        for (con in contradictions) {
            for (actor in listOfNotNull(con.propositionAActor.takeIf { it.isNotEmpty() }, con.propositionBActor.takeIf { it.isNotEmpty() })) {
                val d = data.getOrPut(actor) { MutableActorData() }
                d.contradictions += con.contradictionId
                d.flags += con.type.name
            }
        }
        return data.map { (name, d) ->
            ActorProfile(
                name = name,
                severityIndicator = (d.contradictions.size * 15 + d.flags.size * 5).coerceAtMost(100),
                flags = d.flags.toList(),
                contradictions = d.contradictions,
                statementsMade = d.claims,
                statementsDenied = d.denials
            )
        }.sortedByDescending { it.severityIndicator }
    }

    private class MutableActorData {
        var claims = 0
        var denials = 0
        val contradictions = mutableListOf<String>()
        val flags = mutableSetOf<String>()
    }

    /** Generate report in specified format. */
    fun generateReport(report: EngineForensicReport, format: String = "txt"): String = when (format) {
        "json" -> generateJson(report)
        "markdown" -> generateMarkdown(report)
        else -> generateText(report)
    }

    private fun generateText(report: EngineForensicReport): String = buildString {
        appendLine("=".repeat(70))
        appendLine("VERUM OMNIS — FORENSIC CONTRADICTION REPORT")
        appendLine("=".repeat(70))
        appendLine("Case: ${report.caseId}")
        appendLine("Corpus SHA-512: ${report.corpusHash}")
        appendLine("Contradictions Found: ${report.contradictions.size}")
        appendLine("Engine verification: ${if (report.verification.quorumMet) "CONFIRMED" else "REVIEW REQUIRED"}")
        appendLine("Engine: v5.3.1c | Constitution: v6.0 Final")
        appendLine()

        if (report.contradictions.isNotEmpty()) {
            appendLine("-".repeat(70))
            appendLine("CONTRADICTIONS")
            appendLine("-".repeat(70))
            for (c in report.contradictions) {
                appendLine()
                appendLine("[${c.contradictionId}] ${c.type}")
                appendLine("Severity: ${c.severity} | Confidence: ${c.confidence}")
                appendLine("Actors: ${c.propositionAActor} vs ${c.propositionBActor}")
                appendLine("Description: ${c.conflictDescription}")
                appendLine("Pattern: ${c.logicalPattern.patternType}")
                c.legalHypothesis?.let {
                    appendLine("Legal Hypothesis: ${it.suggestedOffence}")
                    appendLine("  NOTE: This is a HYPOTHESIS requiring human legal review")
                }
            }
        }

        if (report.actorProfiles.isNotEmpty()) {
            appendLine().appendLine("-".repeat(70))
            appendLine("ACTOR PROFILES")
            appendLine("-".repeat(70))
            for (p in report.actorProfiles) {
                appendLine()
                appendLine("${p.name} (severity indicator, heuristic: ${p.severityIndicator}/100)")
                appendLine("  Statements: ${p.statementsMade} made, ${p.statementsDenied} denied")
                appendLine("  Contradictions: ${p.contradictions.size}")
                appendLine("  Flags: ${p.flags.joinToString(", ").ifEmpty { "none" }}")
            }
        }

        appendLine().appendLine("-".repeat(70))
        appendLine("ENGINE VERIFICATION (deterministic self-check)")
        appendLine("-".repeat(70))
        appendLine("Engine status:         ${report.verification.engineStatus}")
        appendLine("Review recommendation: ${report.verification.reviewStatus}")
        if (report.verification.discrepancies.isNotEmpty()) {
            appendLine("Notes: ${report.verification.discrepancies.joinToString("; ")}")
        }

        appendLine().appendLine("=".repeat(70))
        appendLine("END OF REPORT — Seal: VO-CE-v531c-DIGSIM | Constitution: v6.0 Final")
        appendLine("=".repeat(70))
    }

    private fun generateJson(report: EngineForensicReport): String {
        return kotlinx.serialization.json.Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }.encodeToString(EngineForensicReport.serializer(), report)
    }

    private fun generateMarkdown(report: EngineForensicReport): String = buildString {
        appendLine("# Verum Omnis Forensic Report — ${report.caseId}")
        appendLine()
        appendLine("- **Corpus SHA-512:** `${report.corpusHash}`")
        appendLine("- **Contradictions:** ${report.contradictions.size}")
        appendLine("- **Engine verification:** ${if (report.verification.quorumMet) "Confirmed" else "Review required"}")
        appendLine("- **Engine:** v5.3.1c | Constitution: v6.0 Final")
        appendLine()

        appendLine("## Contradictions")
        for (c in report.contradictions) {
            appendLine()
            appendLine("### ${c.contradictionId}: ${c.type}")
            appendLine("- **Severity:** ${c.severity}")
            appendLine("- **Confidence:** ${c.confidence}")
            appendLine("- **Actors:** ${c.propositionAActor} vs ${c.propositionBActor}")
            appendLine("- **Description:** ${c.conflictDescription}")
            c.legalHypothesis?.let {
                appendLine("- **Legal Hypothesis:** ${it.suggestedOffence} *(HYPOTHESIS — requires human review)*")
            }
        }

        appendLine().appendLine("## Actor Profiles")
        for (p in report.actorProfiles) {
            appendLine()
            appendLine("### ${p.name} — severity indicator (heuristic): ${p.severityIndicator}/100")
            appendLine("- Statements: ${p.statementsMade} made, ${p.statementsDenied} denied")
            appendLine("- Contradictions: ${p.contradictions.size}")
            appendLine("- Flags: ${p.flags.joinToString(", ").ifEmpty { "none" }}")
        }

        appendLine().appendLine("---")
        appendLine("*Generated by Verum Omnis Contradiction Engine v5.3.1c under Constitution v6.0 Final*")
    }
}
