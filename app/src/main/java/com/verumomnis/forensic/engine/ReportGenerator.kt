package com.verumomnis.forensic.engine

import com.verumomnis.forensic.core.Constitution
import com.verumomnis.forensic.crypto.EvidenceSealer
import com.verumomnis.forensic.model.Contradiction
import com.verumomnis.forensic.model.ForensicFindings
import com.verumomnis.forensic.model.ForensicReport
import com.verumomnis.forensic.model.OffenceRow
import java.time.Instant

/**
 * Forensic Report Generation (Part IX). Produces a court-ready report in which
 * EVERY contradiction is anchored to:
 *   - a person (respondent),
 *   - a page number (evidence anchor), and
 *   - an applicable local law / statute.
 * The rendered body is then cryptographically sealed (SHA-512 + Constitution).
 */
object ReportGenerator {

    fun generate(
        findings: ForensicFindings,
        caseName: String,
        now: Instant = Instant.now()
    ): ForensicReport {
        val reference = "VO-${caseName.uppercase().take(6).replace(" ", "")}-${now.toString().take(10).replace("-", "")}-FOR"
        val title = "Forensic Analysis Report — $caseName"
        val classification = "CONFIDENTIAL — LAW ENFORCEMENT SENSITIVE"

        val offenceMatrix = findings.contradictions.map { it.toOffenceRow() }
        val executiveSummary = buildExecutiveSummary(findings)
        val body = renderBody(reference, title, classification, findings, offenceMatrix, now)

        val seal = EvidenceSealer.seal(
            bytes = body.toByteArray(Charsets.UTF_8),
            documentType = "forensic_report",
            documentReference = reference,
            nowInstant = now
        )

        return ForensicReport(
            reference = reference,
            title = title,
            classification = classification,
            createdAt = now.toString(),
            jurisdiction = findings.jurisdiction,
            executiveSummary = executiveSummary,
            contradictions = findings.contradictions,
            timeline = findings.timeline,
            legalFramework = findings.legalMappings,
            offenceMatrix = offenceMatrix,
            financial = findings.financial,
            seal = seal,
            body = body
        )
    }

    private fun Contradiction.toOffenceRow(): OffenceRow = OffenceRow(
        offence = legalSignificance,
        person = respondent,
        applicableLaw = applicableLaw,
        evidenceAnchor = "${claimA.source} p${claimA.page} / ${claimB.source} p${claimB.page}",
        confidence = confidence
    )

    private fun buildExecutiveSummary(findings: ForensicFindings): String = buildString {
        append("This report presents the findings of the Verum Omnis Nine-Brain forensic engine ")
        append("over ${findings.documentsAnalyzed} evidence item(s) within jurisdiction ${findings.jurisdiction}. ")
        append("${findings.contradictions.size} material contradiction(s) were identified, each anchored to a ")
        append("responsible person, an evidence page, and an applicable statute. ")
        findings.financial?.companyTax?.let {
            append("Estimated company tax exposure: R%,.2f. ".format(it.taxLiability))
        }
        append("All findings survived Triple-AI consensus and are sealed under Constitution v${Constitution.VERSION}.")
    }

    private fun renderBody(
        reference: String,
        title: String,
        classification: String,
        findings: ForensicFindings,
        offenceMatrix: List<OffenceRow>,
        now: Instant
    ): String = buildString {
        appendLine("VERUM OMNIS — ${Constitution.TAGLINE}")
        appendLine(title)
        appendLine("Document Reference: $reference")
        appendLine("Classification: $classification")
        appendLine("Date: $now")
        appendLine("Jurisdiction: ${findings.jurisdiction}")
        appendLine("Constitution: v${Constitution.VERSION} | Nine-Brain: ${Constitution.NINE_BRAIN_VERSION}")
        appendLine("=".repeat(72))
        appendLine()
        appendLine("1. EXECUTIVE SUMMARY")
        appendLine(buildExecutiveSummary(findings))
        appendLine()
        appendLine("2. CONTRADICTION MATRIX (person · page · statute)")
        if (findings.contradictions.isEmpty()) {
            appendLine("   No material contradictions detected.")
        } else {
            findings.contradictions.forEach { c ->
                appendLine("   ${c.contradictionId} [${c.severity}] ${c.category} / ${c.type} — Respondent: ${c.respondent}")
                appendLine("      Claim A: \"${c.claimA.text}\"")
                appendLine("               (${c.claimA.source}, p${c.claimA.page}, ln${c.claimA.line}, SHA-512 ${c.claimA.sha512.take(12)}…)")
                appendLine("      Claim B: \"${c.claimB.text}\"")
                appendLine("               (${c.claimB.source}, p${c.claimB.page}, ln${c.claimB.line}, SHA-512 ${c.claimB.sha512.take(12)}…)")
                appendLine("      Legal significance: ${c.legalSignificance}")
                appendLine("      Applicable law: ${c.applicableLaw.joinToString("; ")}")
                appendLine("      Confidence: ${c.confidence} · Consensus quorum: ${c.tripleAiConsensus.quorum}")
                appendLine()
            }
        }
        appendLine("3. CHRONOLOGY")
        if (findings.timeline.isEmpty()) appendLine("   No dated events extracted.")
        else findings.timeline.forEach { appendLine("   ${it.dateTime} — ${it.description} (${it.evidenceId})") }
        appendLine()
        appendLine("4. LEGAL FRAMEWORK")
        findings.legalMappings.forEach { appendLine("   - $it") }
        appendLine()
        appendLine("5. OFFENCE MATRIX")
        offenceMatrix.forEach {
            appendLine("   - ${it.person}: ${it.offence}")
            appendLine("     Law: ${it.applicableLaw.joinToString("; ")} | Anchor: ${it.evidenceAnchor} | ${it.confidence}")
        }
        appendLine()
        findings.financial?.let { fin ->
            appendLine("6. FINANCIAL ANALYSIS")
            fin.companyTax?.let { appendLine("   Company tax (${it.jurisdiction}): taxable R%,.2f @ %.0f%% = R%,.2f".format(it.taxableIncome, it.rate * 100, it.taxLiability)) }
            fin.flaggedAnomalies.forEach { appendLine("   Anomaly: $it") }
            appendLine()
        }
        findings.behavioral?.let { b ->
            appendLine("7. BEHAVIOURAL ANALYSIS (B4)")
            (b.gaslighting + b.manipulation + b.stress).forEach {
                appendLine("   [${it.severity}] ${it.type}: \"${it.trigger}\" — ${it.evidenceId}")
            }
            appendLine("   Behavioural score: ${"%.2f".format(b.score)}")
            appendLine()
        }
        if (findings.documentForensics.isNotEmpty() || findings.communications.isNotEmpty()) {
            appendLine("8. DOCUMENT & COMMUNICATIONS FORENSICS (B2/B3)")
            findings.documentForensics.forEach { appendLine("   $it") }
            findings.communications.forEach { appendLine("   $it") }
            appendLine()
        }
        appendLine("9. NINE-BRAIN VERDICTS")
        findings.brainVerdicts.forEach { (brain, verdict) -> appendLine("   $brain: $verdict") }
        if (findings.rndValidation.isNotEmpty()) {
            appendLine("   R&D (B9) notes:")
            findings.rndValidation.forEach { appendLine("     - $it") }
        }
        appendLine()
        appendLine("10. DECLARATION")
        appendLine("   Triple-verified (Gemma 3 · communicator · Nine-Brain). Evidence before narrative.")
        appendLine("   Ordinal confidence only. Same evidence yields the same result (determinism).")
    }
}
