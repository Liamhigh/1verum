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
 * Forensic Report Generation (Part IX). Produces a reviewable report in which
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
        narrativeWriter: ReportWriter = DeterministicReportWriter,
        judicialFindings: List<Contradiction> = emptyList()
    ): ForensicReport {
        val retryIds = findings.extractionCoverage.mapNotNull { it.coverage.retryAttempt?.id }
        val reference = "VO-${caseName.uppercase().take(6).replace(" ", "")}-${now.toString().take(10).replace("-", "")}-FOR" +
            retryIds.firstOrNull()?.let { "-OCR-$it" }.orEmpty()
        val title = if (retryIds.isNotEmpty()) "NEW PDF-only OCR Retry Report — $caseName"
            else "Forensic Analysis Report — $caseName"
        val classification = "CONFIDENTIAL — LAW ENFORCEMENT SENSITIVE"

        // Doctrine: only council-ACCEPTED contradictions may be sealed as findings.
        val acceptedContradictions = findings.contradictions.filter { it.councilStatus == "ACCEPTED" }
        val offenceMatrix = acceptedContradictions.map { it.toOffenceRow() }
        val executiveSummary = buildExecutiveSummary(findings)
        // The declaration must reflect what actually ran. A deterministic writer
        // means no language model touched this report, whatever is installed.
        val aiNarrativeUsed = narrativeWriter !is DeterministicReportWriter
        val body = renderBody(
            reference, title, classification, findings, acceptedContradictions, offenceMatrix, now,
            deviceId, publicKeyFingerprint, aiNarrativeUsed
        )
        val gemmaNarrative = narrativeWriter.writeNarrative(findings, caseName, findingsJsonPath)
        val judicialCrossReferenceSection = renderJudicialCrossReferenceSection(judicialFindings)

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
            extractionCoverage = findings.extractionCoverage,
            jurisdictionSource = findings.jurisdictionSource,
            seal = seal,
            body = body,
            gemmaNarrative = gemmaNarrative,
            judicialFindings = judicialFindings,
            judicialCrossReferenceSection = judicialCrossReferenceSection
        )
    }

    /**
     * Renders the judicial-pairing findings as a distinct, clearly-labelled advisory
     * block. Deliberately kept out of [renderBody] / the sealed hash: these findings
     * depend on a live network fetch of external court records, so the same sealed
     * evidence could otherwise produce a different-looking "sealed" body run to run.
     */
    private fun renderJudicialCrossReferenceSection(judicialFindings: List<Contradiction>): String {
        if (judicialFindings.isEmpty()) return ""
        return buildString {
            appendLine()
            appendLine("JUDICIAL CROSS-REFERENCE FINDINGS (ADVISORY — EXTERNAL SOURCES)")
            appendLine("=".repeat(72))
            appendLine("The following findings pair sworn statements or court records retrieved live")
            appendLine("from public judicial databases against this case's sealed evidence. They are")
            appendLine("ENGINE-VERIFIED (the same deterministic B1 detectors, given external input) but")
            appendLine("externally sourced and NOT part of the cryptographic seal above.")
            appendLine()
            judicialFindings.forEach { c ->
                appendLine("${c.contradictionId} [${c.severity}] ${c.category} / ${c.type} — Respondent: ${c.respondent}")
                appendLine("   Claim A: \"${c.claimA.text}\"")
                appendLine("            (${c.claimA.source})")
                appendLine("   Claim B: \"${c.claimB.text}\"")
                appendLine("            (${c.claimB.source})")
                appendLine("   Legal significance: ${c.legalSignificance}")
                appendLine("   Applicable law: ${c.applicableLaw.joinToString("; ")}")
                appendLine("   Confidence: ${c.confidence}")
                appendLine()
            }
        }
    }

    private fun Contradiction.toOffenceRow(): OffenceRow = OffenceRow(
        offence = legalSignificance,
        person = respondent,
        applicableLaw = if (legalReferences.isEmpty()) {
            listOf("No verified mapping; human review required for $jurisdiction")
        } else {
            legalReferences.map { "${it.citation} — Official source: ${it.authoritativeUrl}" }
        },
        evidenceAnchor = "${formatClaimLocation(claimA.source, claimA.page, claimA.line)} / ${formatClaimLocation(claimB.source, claimB.page, claimB.line)}",
        confidence = confidence
    )

    private fun buildExecutiveSummary(findings: ForensicFindings): String = buildString {
        findings.extractionCoverage.forEach {
            append("${it.fileName}: ${it.coverage.summary()} ")
        }
        append("This report presents the screening results of the Verum Omnis Nine-Brain forensic engine ")
        append("over ${findings.documentsAnalyzed} evidence item(s) within jurisdiction ${findings.jurisdiction}. ")
        append("${findings.contradictions.size} potential contradiction indicator(s) were flagged. ")
        append("A named person is shown only when an explicit source attribution supports that link; unknown links and page locations are labelled. ")
        findings.financial?.companyTax?.let {
            append("Estimated company tax exposure: R%,.2f. ".format(it.taxLiability))
        }
        if (findings.extractedPersons.isNotEmpty()) {
            append("${findings.extractedPersons.size} person(s) were extracted from the evidence. ")
        }
        append("Indicators that passed the engine's council review are listed as engine results; this is not a ")
        append("factual or legal determination. Automated screening indicators require independent human review. ")
        append("Unconfirmed indicators are listed separately for transparency. ")
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
        publicKeyFingerprint: String,
        /** True only when a real on-device model produced the narrative appendix. */
        aiNarrativeUsed: Boolean
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
        appendLine("Document quotations and source anchors are evidence; categories, conflict descriptions, severity and legal significance are automated interpretations.")
        appendLine("This report does not determine guilt, intent, liability, or the truth of an allegation.")
        appendLine()
        if (findings.extractionCoverage.isNotEmpty()) {
            appendLine("PDF PAGE EXTRACTION PROVENANCE — original PDF page numbers")
            findings.extractionCoverage.forEach { doc ->
                appendLine("${doc.evidenceId} · ${doc.fileName} · SHA-512 ${doc.sha512}")
                appendLine(doc.coverage.summary())
                doc.coverage.pages.forEach { page ->
                    appendLine("   Page ${page.page}: ${page.status} (${page.reason}); text-layer chars ${page.textLayerChars}; extracted chars ${page.extractedChars}; OCR attempted ${page.ocrAttempted}; text-layer failed ${page.textLayerFailed}")
                }
            }
            appendLine()
        }
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
            appendLine("   GPS identifies an approximate location only; it does not determine governing law or venue.")
            appendLine("   Statutes applied:")
            if (js.statutes.isEmpty()) appendLine("      - No jurisdiction-validated statutes are attached to this source.")
            else js.statutes.forEach { appendLine("      - $it") }
            appendLine()
        }
        appendLine("2b. AUTOMATED CASE ASSOCIATION — NOT A GUILT OR CREDIBILITY ASSESSMENT")
        val actorProfiles = buildActorProfiles(findings)
        if (actorProfiles.isEmpty()) {
            appendLine("   No actor profiles available.")
        } else {
            actorProfiles.forEach { (person, score, flags) ->
                appendLine("   - $person · Case-association indicator (heuristic only): ${"%.1f" .format(score)}/10 · Flags: ${flags.joinToString(", ")}")
            }
        }
        appendLine()
        appendLine("3. CONTRADICTION INDICATORS (person link · source page · legal review) — council-ACCEPTED only")
        if (acceptedContradictions.isEmpty()) {
            appendLine("   No council-confirmed contradictions detected in extracted text only. This is not assurance about unread material.")
        } else {
            acceptedContradictions.forEach { c ->
                appendLine("   ${c.contradictionId} [${c.severity}] ${c.category} / ${c.type} — Respondent: ${c.respondent}")
                appendLine("      Person link: ${c.personLinkStatus}")
                appendLine("      Claim A evidence: \"${c.claimA.text}\"")
                appendLine("               (${formatClaimLocation(c.claimA.source, c.claimA.page, c.claimA.line)}, SHA-512 ${c.claimA.sha512.take(12)}…)")
                appendLine("      Claim B evidence: \"${c.claimB.text}\"")
                appendLine("               (${formatClaimLocation(c.claimB.source, c.claimB.page, c.claimB.line)}, SHA-512 ${c.claimB.sha512.take(12)}…)")
                appendLine("      Automated interpretation — conflict: ${c.description}")
                appendLine("      Automated interpretation — legal significance: ${c.legalSignificance}")
                if (c.legalReferences.isEmpty()) {
                    appendLine("      Applicable law: no verified mapping for ${c.jurisdiction}; human legal review required.")
                } else {
                    c.legalReferences.forEach { legal ->
                        appendLine("      Legal hypothesis: ${legal.citation} (${legal.jurisdiction})")
                        appendLine("         Authoritative source: ${legal.authoritativeUrl}")
                    }
                }
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
                appendLine("      Person link: ${c.personLinkStatus} · Jurisdiction: ${c.jurisdiction}")
                appendLine("      Claim A evidence: \"${c.claimA.text}\" (${formatClaimLocation(c.claimA.source, c.claimA.page, c.claimA.line)})")
                appendLine("      Claim B evidence: \"${c.claimB.text}\" (${formatClaimLocation(c.claimB.source, c.claimB.page, c.claimB.line)})")
                if (c.legalReferences.isEmpty()) {
                    appendLine("      Legal review: no verified mapping for this jurisdiction.")
                } else {
                    c.legalReferences.forEach { legal ->
                        appendLine("      Legal hypothesis: ${legal.citation} (${legal.jurisdiction}) — ${legal.authoritativeUrl}")
                    }
                }
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
        appendLine("6. POTENTIAL LEGAL HYPOTHESES — SCREENING INDICATORS, NOT LEGAL FINDINGS")
        offenceMatrix.forEach {
            appendLine("   - ${it.person}: automated interpretation — ${it.offence}")
            appendLine("     Legal hypothesis / review: ${it.applicableLaw.joinToString("; ")} | Evidence: ${it.evidenceAnchor} | ${it.confidence}")
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
            appendLine("   Safety screening indicators are keyword matches only; they do not establish intent, wrongdoing, or a legal finding.")
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
        appendLine("   These are automated screening results, not proven facts, legal findings, or legal advice. Human review of the cited source material is required. The council records corroboration among engine components; it is not independent verification by a court or investigator.")
        // States what actually ran, never what was hoped for. This previously read
        // "Triple-verified (Gemma 3 · communicator · Nine-Brain)" on every report,
        // including reports produced with no model loaded at all — the AI verdicts
        // in BrainCouncil are derived from the nine-brain confirmation count, not
        // from any language model. A sealed forensic document asserting a
        // verification that did not occur is the single easiest thing for opposing
        // counsel to attack, and it would taint the findings that are sound.
        if (aiNarrativeUsed) {
            appendLine("   Deterministic Nine-Brain analysis, with an on-device AI narrative appendix.")
            appendLine("   The narrative is advisory and unsealed; the findings above are the sealed record.")
        } else {
            appendLine("   Deterministic Nine-Brain analysis. AI narrative not performed on this device.")
            appendLine("   No language-model verification is claimed for these findings.")
        }
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

    private fun formatClaimLocation(source: String, page: Int, line: Int): String = buildString {
        append(source)
        append(if (page > 0) ", page $page" else ", page unknown")
        if (line > 0) append(", line $line")
    }
}
