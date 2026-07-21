package com.verumomnis.forensic.engine

import com.verumomnis.forensic.core.Constitution
import com.verumomnis.forensic.crypto.EvidenceSealer
import com.verumomnis.forensic.model.BehavioralFinding
import com.verumomnis.forensic.model.Contradiction
import com.verumomnis.forensic.model.ExtractedPerson
import com.verumomnis.forensic.model.ForensicFindings
import com.verumomnis.forensic.model.ForensicReport
import com.verumomnis.forensic.model.OffenceRow
import com.verumomnis.forensic.model.Severity
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
        now: Instant,
        deviceId: String = "",
        publicKeyFingerprint: String = "",
        findingsJsonPath: String = "",
        narrativeWriter: ReportWriter = DeterministicReportWriter
    ): ForensicReport {
        val reference = "VO-${caseName.uppercase().take(6).replace(" ", "")}-${now.toString().take(10).replace("-", "")}-FOR"
        val title = "Forensic Analysis Report — $caseName"
        val classification = "CONFIDENTIAL — LAW ENFORCEMENT SENSITIVE"

        // Doctrine: only council-ACCEPTED contradictions may be sealed as findings.
        val acceptedContradictions = findings.contradictions.filter { it.councilStatus == "ACCEPTED" }
        val offenceMatrix = acceptedContradictions.map { it.toOffenceRow() }
        val executiveSummary = buildExecutiveSummary(findings)
        val body = renderBody(reference, title, classification, findings, acceptedContradictions, offenceMatrix, now, deviceId, publicKeyFingerprint)
        val gemmaNarrative = narrativeWriter.writeNarrative(findings, caseName, findingsJsonPath)

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
            mediaExhibits = findings.mediaExhibits,
            findingsJsonPath = findingsJsonPath,
            jurisdictionSource = findings.jurisdictionSource,
            seal = seal,
            body = body,
            gemmaNarrative = gemmaNarrative
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
        if (findings.extractedPersons.isNotEmpty()) {
            append("${findings.extractedPersons.size} person(s) were extracted from the evidence. ")
        }
        append("Every reported finding was accepted by the Nine-Brain council (B1 plus at least two corroborating brains). ")
        append("Unconfirmed indicators are excluded from the findings and listed separately for transparency. ")
        append("Sealed under Constitution v${Constitution.VERSION}.")
    }

    private fun renderBody(
        reference: String,
        title: String,
        classification: String,
        findings: ForensicFindings,
        acceptedContradictions: List<Contradiction>,
        offenceMatrix: List<OffenceRow>,
        now: Instant,
        deviceId: String,
        publicKeyFingerprint: String
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
        appendLine("2. EXTRACTED PERSONS")
        if (findings.extractedPersons.isEmpty()) {
            appendLine("   No structured person records extracted.")
        } else {
            findings.extractedPersons.forEach { p ->
                appendLine("   - ${p.name}" + (p.age?.let { " · age $it" } ?: "") + " · ${p.role}" +
                    (p.idNumber?.let { " · ID $it" } ?: "") + (p.address?.let { " · $it" } ?: ""))
            }
        }
        appendLine()
        findings.jurisdictionSource?.let { js ->
            appendLine("2a. JURISDICTION SOURCE")
            js.gps?.let { g ->
                appendLine("   Coordinates: %.6f, %.6f".format(g.latitude, g.longitude))
            }
            appendLine("   Statutes applied:")
            js.statutes.forEach { appendLine("      - $it") }
            appendLine()
        }
        appendLine("2b. ACTOR PROFILES")
        val actorProfiles = buildActorProfiles(findings)
        if (actorProfiles.isEmpty()) {
            appendLine("   No actor profiles available.")
        } else {
            actorProfiles.forEach { (person, score, flags) ->
                appendLine("   - $person · Severity indicator (heuristic): ${"%.1f" .format(score)}/10 · Flags: ${flags.joinToString(", ")}")
            }
        }
        appendLine()
        appendLine("3. CONTRADICTION MATRIX (person · page · statute) — council-ACCEPTED findings only")
        if (acceptedContradictions.isEmpty()) {
            appendLine("   No council-confirmed contradictions detected.")
        } else {
            acceptedContradictions.forEach { c ->
                appendLine("   ${c.contradictionId} [${c.severity}] ${c.category} / ${c.type} — Respondent: ${c.respondent}")
                appendLine("      Claim A: \"${c.claimA.text}\"")
                appendLine("               (${c.claimA.source}, p${c.claimA.page}, ln${c.claimA.line}, SHA-512 ${c.claimA.sha512.take(12)}…)")
                appendLine("      Claim B: \"${c.claimB.text}\"")
                appendLine("               (${c.claimB.source}, p${c.claimB.page}, ln${c.claimB.line}, SHA-512 ${c.claimB.sha512.take(12)}…)")
                appendLine("      Legal significance: ${c.legalSignificance}")
                appendLine("      Applicable law: ${c.applicableLaw.joinToString("; ")}")
                appendLine("      Confidence: ${c.confidence} · Council: ACCEPTED (${c.confirmingBrains.joinToString(", ").ifEmpty { "B1 only" }})")
                appendLine()
            }
        }
        appendLine("3a. 7-CATEGORY CONTRADICTION TABLE (council-ACCEPTED only)")
        val categoryTable = buildCategoryTable(acceptedContradictions)
        if (categoryTable.isEmpty()) {
            appendLine("   No contradictions to summarise.")
        } else {
            categoryTable.forEach { (category, count, maxSeverity, persons) ->
                appendLine("   ${category.name.replace("_", " ")}: $count · Max severity $maxSeverity · Persons: ${persons.joinToString(", ")}")
            }
        }
        appendLine()
        val notConfirmed = findings.contradictions.filter { it.councilStatus != "ACCEPTED" }
        if (notConfirmed.isNotEmpty()) {
            appendLine("3b. NOT CONFIRMED — EXCLUDED FROM FINDINGS")
            appendLine("   The following indicator(s) did NOT reach council concurrence (B1 plus two corroborating brains).")
            appendLine("   They are recorded for transparency only and are NOT findings of this report.")
            notConfirmed.forEach { c ->
                appendLine("   ${c.contradictionId} [${c.severity}] ${c.category} / ${c.type} — Respondent: ${c.respondent} — Council status: ${c.councilStatus}")
            }
            appendLine()
        }
        appendLine("4. CHRONOLOGY")
        if (findings.timeline.isEmpty()) appendLine("   No dated events extracted.")
        else findings.timeline.forEach { appendLine("   ${it.dateTime} — ${it.description} (${it.evidenceId})") }
        appendLine()
        appendLine("5. LEGAL FRAMEWORK")
        findings.legalMappings.forEach { appendLine("   - $it") }
        appendLine()
        appendLine("6. OFFENCE MATRIX")
        offenceMatrix.forEach {
            appendLine("   - ${it.person}: ${it.offence}")
            appendLine("     Law: ${it.applicableLaw.joinToString("; ")} | Anchor: ${it.evidenceAnchor} | ${it.confidence}")
        }
        appendLine()
        findings.financial?.let { fin ->
            appendLine("7. FINANCIAL ANALYSIS")
            fin.companyTax?.let { appendLine("   Company tax (${it.jurisdiction}): taxable R%,.2f @ %.0f%% = R%,.2f".format(it.taxableIncome, it.rate * 100, it.taxLiability)) }
            fin.flaggedAnomalies.forEach { appendLine("   Anomaly: $it") }
            appendLine()
        }
        findings.behavioral?.let { b ->
            appendLine("8. BEHAVIOURAL ANALYSIS (B4)")
            (b.gaslighting + b.manipulation + b.stress).forEach {
                appendLine("   [${it.severity}] ${it.type}: \"${it.trigger}\" — ${it.evidenceId}")
            }
            appendLine("   Behavioural score: ${"%.2f".format(b.score)}")
            appendLine()
        }
        findings.audio?.let { a ->
            appendLine("8b. AUDIO FORENSICS (B8)")
            appendLine("   Files: ${a.filesAnalyzed} · Speakers: ${a.speakerCount} · Utterances: ${a.segments.size} · Transcript: ${if (a.transcriptionAvailable) "available" else "INSUFFICIENT"}")
            a.tamperSignals.forEach { appendLine("   [${it.severity}] ${it.type}: ${it.description}") }
            a.voiceStress.forEach { appendLine("   Voice stress (${it.speaker}) @${it.timestamp}: ${it.description}") }
            if (a.fullTranscript.isNotBlank()) {
                appendLine("   Transcript:")
                a.fullTranscript.lines().take(12).forEach { appendLine("     $it") }
            }
            appendLine()
        }
        if (findings.documentForensics.isNotEmpty() || findings.communications.isNotEmpty()) {
            appendLine("9. DOCUMENT & COMMUNICATIONS FORENSICS (B2/B3)")
            findings.documentForensics.forEach { appendLine("   $it") }
            findings.communications.forEach { appendLine("   $it") }
            appendLine()
        }
        findings.guardian?.let { g ->
            appendLine("9a. B9 GUARDIAN ASSESSMENT")
            if (g.hardStopRequired) {
                appendLine("   *** ARTICLE X HARD STOP ACTIVE ***")
            }
            if (g.violations.isEmpty()) {
                appendLine("   No constitutional violations detected.")
            } else {
                g.violations.forEach { v ->
                    appendLine("   [${v.severity}] ${v.type}: ${v.description}" + (v.evidenceId.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""))
                }
            }
            if (g.notes.isNotEmpty()) {
                appendLine("   Cross-brain notes:")
                g.notes.forEach { appendLine("     - $it") }
            }
            appendLine()
        }
        appendLine("10. NINE-BRAIN VERDICTS")
        findings.brainVerdicts.forEach { (brain, verdict) -> appendLine("   $brain: $verdict") }
        if (findings.rndValidation.isNotEmpty()) {
            appendLine("   R&D (B9) notes:")
            findings.rndValidation.forEach { appendLine("     - $it") }
        }
        appendLine()
        if (findings.mediaExhibits.isNotEmpty()) {
            appendLine("11. EVIDENCE EXHIBITS — PHOTOGRAPHIC / VIDEO (ANNEXURE)")
            findings.mediaExhibits.forEach { ex ->
                appendLine("   ${ex.exhibitId} [${ex.kind}] ${ex.fileName} (${ex.mimeType})")
                appendLine("      SHA-512: ${ex.sha512.take(32)}…")
                val g = ex.gps
                appendLine("      GPS: " + (g?.let { "%.6f, %.6f (source: ${ex.gpsSource})".format(it.latitude, it.longitude) } ?: "NOT RECORDED"))
                appendLine("      Captured (upload): ${ex.capturedAt}" + (ex.exifTimestamp?.let { " · EXIF: $it" } ?: ""))
                appendLine("      Jurisdiction: ${ex.jurisdiction}")
                appendLine()
            }
        }
        appendLine("12. DECLARATION")
        appendLine("   Reviewed by the Nine-Brain council: each finding above was proposed by B1 and corroborated by at least two other brains (B2–B8); B9 validates after the vote. No external AI model forms part of this determination. Evidence before narrative.")
        appendLine("   Ordinal confidence only. Same evidence yields the same result (determinism).")
        if (deviceId.isNotBlank() || publicKeyFingerprint.isNotBlank()) {
            appendLine()
            appendLine("13. OPERATOR / DEVICE IDENTITY")
            if (deviceId.isNotBlank()) appendLine("   Device ID: ${deviceId.take(16)}…")
            if (publicKeyFingerprint.isNotBlank()) appendLine("   Public key fingerprint: ${publicKeyFingerprint.take(24)}…")
            appendLine("   Cryptographic identity proof is bound to the seal record covering this report.")
        }
    }

    private data class CategoryRow(
        val category: com.verumomnis.forensic.model.ContradictionCategory,
        val count: Int,
        val maxSeverity: Severity,
        val persons: Set<String>
    )

    private fun buildCategoryTable(contradictions: List<Contradiction>): List<CategoryRow> =
        contradictions
            .groupBy { it.category }
            .map { (category, list) ->
                CategoryRow(
                    category = category,
                    count = list.size,
                    maxSeverity = list.maxByOrNull { severityScore(it.severity) }?.severity ?: Severity.LOW,
                    persons = list.map { it.respondent }.filter { it.isNotBlank() }.toSortedSet()
                )
            }
            .sortedByDescending { it.count }

    private data class ActorProfile(
        val person: String,
        val score: Double,
        val flags: List<String>
    )

    private fun buildActorProfiles(findings: ForensicFindings): List<ActorProfile> {
        val persons = findings.extractedPersons.map { it.name } +
            findings.contradictions.map { it.respondent }
        val uniquePersons = persons.filter { it.isNotBlank() }.toSortedSet()
        val behavioralByPerson = mutableMapOf<String, MutableList<BehavioralFinding>>()
        findings.behavioral?.let { b ->
            (b.gaslighting + b.stress + b.manipulation).forEach { f ->
                behavioralByPerson.getOrPut(f.evidenceId) { mutableListOf() } += f
            }
        }
        return uniquePersons.map { name ->
            val contradictionScore = findings.contradictions
                .filter { it.respondent.equals(name, ignoreCase = true) }
                .sumOf { severityScore(it.severity).toDouble() }
            val behavioralScore = findings.contradictions
                .filter { it.respondent.equals(name, ignoreCase = true) }
                .flatMap { c -> behavioralByPerson[c.claimA.evidenceId].orEmpty() + behavioralByPerson[c.claimB.evidenceId].orEmpty() }
                .distinct()
                .size * 0.5
            val score = minOf(10.0, contradictionScore + behavioralScore)
            val flags = buildList {
                if (findings.contradictions.any { it.respondent.equals(name, ignoreCase = true) && it.severity == Severity.CRITICAL }) add("CRITICAL contradiction")
                if (findings.contradictions.any { it.respondent.equals(name, ignoreCase = true) && it.category.name.contains("COERCION") }) add("Coercion")
                if (findings.contradictions.any { it.respondent.equals(name, ignoreCase = true) && it.category.name.contains("PERJURY") }) add("Perjury indicator")
            }
            ActorProfile(name, score, flags)
        }.sortedByDescending { it.score }
    }

    private fun severityScore(severity: Severity): Int = when (severity) {
        Severity.CRITICAL -> 5
        Severity.VERY_HIGH -> 4
        Severity.HIGH -> 3
        Severity.MODERATE -> 2
        Severity.LOW -> 1
    }
}
