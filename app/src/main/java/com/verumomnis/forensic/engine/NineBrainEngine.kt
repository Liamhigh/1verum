package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.BehavioralAnalysis
import com.verumomnis.forensic.model.Confidence
import com.verumomnis.forensic.model.Contradiction
import com.verumomnis.forensic.model.ContradictionCategory
import com.verumomnis.forensic.model.EvidenceAtom
import com.verumomnis.forensic.model.FinancialAnalysis
import com.verumomnis.forensic.model.ForensicFindings
import com.verumomnis.forensic.model.GuardianAssessment
import com.verumomnis.forensic.model.Severity
import com.verumomnis.forensic.model.TimelineEvent
import com.verumomnis.forensic.engine.contradiction.ContradictionToForensicAdapter
import com.verumomnis.forensic.engine.contradiction.VerumContradictionEngine
import java.time.Instant

/**
 * The 9-Brain forensic engine (build spec Sections 5, 8). Deterministic and
 * always-on — it validates every finding after the B1–B8 council vote.
 *
 *  - B1 Contradiction   -> [VerumContradictionEngine] + [ContradictionExtractor] booster
 *  - B2 Document         -> creator-tool / metadata tamper signals
 *  - B3 Communications   -> timeline-gap analysis across dated statements
 *  - B4 Behavioral       -> [BehavioralBrain]
 *  - B5 Timeline         -> date extraction + ordering
 *  - B6 Financial        -> fraud amount / tax detection
 *  - B7 Legal Mapping    -> jurisdiction + statute mapping
 *  - B8 Audio            -> transcript/metadata-based (best-effort off-device)
 *  - B9 R&D              -> validation / coverage (no verdicts)
 */
object NineBrainEngine {

    private val DATE_REGEX =
        Regex("""\b(\d{1,2}\s+(?:January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{4})\b""")
    private val MONEY_REGEX =
        Regex("""\bR\s?([\d][\d,\s]{2,})(?:\.\d+)?\b""")

    fun analyze(
        documents: List<EvidenceDocument>,
        audio: List<AudioEvidence> = emptyList(),
        media: List<MediaEvidence> = emptyList(),
        now: Instant,
        selectedVenueCode: String? = null
    ): ForensicFindings {
        val timestamp = now.toString()

        // B8 audio: tamper/voice-stress + diarised transcript, which is folded into
        // the document set so B1/B4 can cross-reference what was said.
        val audioAnalysis = AudioBrain.analyze(audio)
        val audioDocs = audio.mapNotNull { a ->
            a.transcript?.takeIf { it.isNotBlank() }?.let {
                EvidenceDocument(
                    evidenceId = a.id, fileName = a.fileName, type = "audio_transcript",
                    text = it, sha512 = a.sha512, gps = a.gps, documentKind = "audio_transcript"
                )
            }
        }
        val allDocs = documents + audioDocs
        val gps = documents.firstNotNullOfOrNull { it.gps }
        val jurisdictionSource = JurisdictionService.resolve(gps, selectedVenueCode)
        val jurisdiction = jurisdictionSource.jurisdiction
        val extractedPersons = EntityExtractor.extractPersons(allDocs)

        val atoms = buildEvidenceAtoms(allDocs, jurisdiction, timestamp)
        val hybridContradictions = runHybridEngine(allDocs, now)                      // B1 primary (hybrid v5.3.1c)
        val legacyContradictions = ContradictionExtractor.extract(allDocs, now)       // B1 legacy rule booster
        val rawContradictions = mergeAndRankContradictions(hybridContradictions, legacyContradictions)
        val contradictions = attachPersonsAndStatutes(rawContradictions, extractedPersons, jurisdictionSource, allDocs)
        val documentForensics = DocumentForensicsBrain.analyze(allDocs, media).signals // B2
        val timeline = reconstructTimeline(allDocs)                                   // B5
        val communications = communicationsBrain(timeline)                            // B3
        val behavioral = BehavioralBrain.analyze(allDocs)                             // B4
        val financial = analyzeFinancials(allDocs)                                    // B6
        val legalMappings = mapLegalFramework(allDocs, jurisdiction) // B7
        val guardianAssessment = GuardianBrain.analyze(                               // B9
            documents = allDocs,
            audioTranscripts = audio.mapNotNull { it.transcript },
            contradictions = contradictions,
            financial = financial,
            behavioral = behavioral,
            now = now
        )
        val mediaExhibits = buildMediaExhibits(media, jurisdiction)                   // photographic/video evidence

        val brainVerdicts = linkedMapOf(
            "B1-Contradiction" to if (contradictions.isEmpty()) "NO INDICATORS IN EXTRACTED TEXT" else "${contradictions.size} FOUND",
            "B2-Document" to if (documentForensics.isEmpty()) "NO TAMPER SIGNALS IN AVAILABLE INPUT" else "${documentForensics.size} SIGNALS",
            "B3-Communications" to if (communications.isEmpty()) "NO GAPS DETECTED IN EXTRACTED TEXT" else "${communications.size} GAPS",
            "B4-Behavioral" to if (behavioral.isEmpty()) "NO INDICATORS IN EXTRACTED TEXT" else "score ${"%.2f".format(behavioral.score)}",
            "B5-Timeline" to "${timeline.size} EVENTS",
            "B6-Financial" to if (financial?.flaggedAnomalies.isNullOrEmpty()) "NO ANOMALIES DETECTED IN EXTRACTED TEXT" else "${financial!!.flaggedAnomalies.size} FLAGGED",
            "B7-LegalMapping" to "${legalMappings.size} MAPPINGS",
            "B8-Audio" to audioVerdict(audio, audioAnalysis),
            "B9-Guardian" to when {
                guardianAssessment.hardStopRequired -> "HARD STOP"
                guardianAssessment.violations.isNotEmpty() -> "${guardianAssessment.violations.size} VIOLATIONS"
                else -> "${guardianAssessment.notes.size} NOTES"
            }
        )

        return ForensicFindings(
            documentsAnalyzed = allDocs.size,
            evidenceAtoms = atoms,
            contradictions = contradictions,
            timeline = timeline,
            legalMappings = legalMappings,
            jurisdiction = jurisdiction,
            jurisdictionSource = jurisdictionSource,
            extractedPersons = extractedPersons,
            financial = financial,
            behavioral = behavioral.takeUnless { it.isEmpty() },
            audio = audioAnalysis.takeUnless { it.isEmpty() },
            documentForensics = documentForensics,
            communications = communications,
            rndValidation = guardianAssessment.notes,
            mediaExhibits = mediaExhibits,
            brainVerdicts = brainVerdicts,
            guardian = guardianAssessment
        )
    }

    /** Anchor each photograph/video to GPS + timestamp + SHA-512 (chain of custody). */
    private fun buildMediaExhibits(
        media: List<MediaEvidence>,
        fallbackJurisdiction: String
    ): List<com.verumomnis.forensic.model.MediaExhibit> = media.mapIndexed { i, m ->
        val gps = m.exifGps ?: m.deviceGps
        val gpsSource = if (m.exifGps != null) "exif" else "device"
        com.verumomnis.forensic.model.MediaExhibit(
            exhibitId = "EX-%03d".format(i + 1),
            evidenceId = m.id,
            fileName = m.fileName,
            kind = m.kind,
            mimeType = m.mimeType,
            sha512 = m.sha512,
            capturedAt = m.capturedAt,
            exifTimestamp = m.exifTimestamp,
            gps = gps,
            gpsSource = gpsSource,
            jurisdiction = gps?.let { jurisdictionFor(it) } ?: fallbackJurisdiction,
            width = m.width,
            height = m.height,
            durationMs = m.durationMs
        )
    }

    private fun jurisdictionFor(gps: com.verumomnis.forensic.model.GpsRecord): String =
        JurisdictionService.resolve(gps).jurisdiction

    private fun audioVerdict(audio: List<AudioEvidence>, a: com.verumomnis.forensic.model.AudioAnalysis): String = when {
        audio.isEmpty() -> "N/A (no audio)"
        a.tamperSignals.isNotEmpty() -> "${a.tamperSignals.size} TAMPER · ${a.segments.size} utterances"
        a.transcriptionAvailable -> "${a.speakerCount} speakers · ${a.segments.size} utterances"
        else -> "INSUFFICIENT (no transcript)"
    }

    private fun buildEvidenceAtoms(
        documents: List<EvidenceDocument>,
        jurisdiction: String,
        timestamp: String
    ): List<EvidenceAtom> = documents.mapIndexed { index, doc ->
        EvidenceAtom(
            atomId = "EA-%03d".format(index + 1),
            evidenceId = doc.evidenceId,
            type = doc.type,
            sourceFile = doc.fileName,
            sha512 = doc.sha512,
            content = doc.text.take(240),
            jurisdiction = jurisdiction,
            confidence = Confidence.VERY_HIGH,
            extractedBy = "B2-DocumentBrain",
            gps = doc.gps,
            timestamp = timestamp
        )
    }

    /** B3 — communications: unexplained gaps between dated events (>30 days). */
    private fun communicationsBrain(timeline: List<TimelineEvent>): List<String> {
        val dated = timeline.mapNotNull { ev -> EntityExtractor.extractDate(ev.dateTime)?.let { it to ev } }
            .sortedBy { it.first }
        val gaps = mutableListOf<String>()
        for (i in 1 until dated.size) {
            val days = dated[i - 1].first.until(dated[i].first).days +
                dated[i - 1].first.until(dated[i].first).months * 30 +
                dated[i - 1].first.until(dated[i].first).years * 365
            if (days > 30) {
                gaps += "GAP ${days}d between ${dated[i - 1].second.dateTime} and ${dated[i].second.dateTime}"
            }
        }
        return gaps
    }

    private fun reconstructTimeline(documents: List<EvidenceDocument>): List<TimelineEvent> {
        val events = mutableListOf<TimelineEvent>()
        var counter = 1
        documents.forEach { doc ->
            sourcePageLines(doc).forEach { (page, line) ->
                DATE_REGEX.find(line)?.let { m ->
                    events += TimelineEvent(
                        eventId = "T-%03d".format(counter++),
                        dateTime = m.value,
                        description = line.trim().take(160),
                        evidenceId = doc.evidenceId,
                        page = page,
                        sha512 = doc.sha512,
                        gps = doc.gps,
                        severity = Severity.MODERATE
                    )
                }
            }
        }
        return events
    }

    private fun mapLegalFramework(documents: List<EvidenceDocument>, jurisdiction: String): List<String> {
        val supported = documents.flatMap { doc ->
            val text = doc.text.lowercase()
            buildList {
                if (jurisdiction.startsWith("ZA") && Regex("""section\s*12b|s\.?\s*12b""").containsMatchIn(text)) {
                    addAll(JurisdictionService.legalReferencesFor(jurisdiction, ContradictionCategory.SECTION_12B, text))
                }
                if (jurisdiction.startsWith("ZA") && Regex("""electronic|e-sign|signature|signed|unsigned""").containsMatchIn(text)) {
                    addAll(JurisdictionService.legalReferencesFor(jurisdiction, ContradictionCategory.SIGNATURE_STATUS, text))
                }
            }
        }.distinctBy { it.citation }
        return if (supported.isEmpty()) {
            listOf("No authoritative statute mapping was validated for jurisdiction $jurisdiction; human legal review required.")
        } else {
            supported.map { "${it.citation} — Official source: ${it.authoritativeUrl}" }
        }
    }

    private fun analyzeFinancials(documents: List<EvidenceDocument>): FinancialAnalysis? {
        val anomalies = mutableListOf<String>()
        val amounts = documents.flatMap { doc ->
            MONEY_REGEX.findAll(doc.text).map { it.groupValues[1].replace(Regex("[,\\s]"), "").toDoubleOrNull() ?: 0.0 }
        }.filter { it > 0 }
        amounts.groupBy { it }.filter { it.value.size > 1 }.forEach { (amount, occ) ->
            anomalies += "Duplicate amount R%,.2f appears %d times".format(amount, occ.size)
        }
        val financialDoc = documents.firstOrNull { it.revenue != null && it.expenses != null }
        val companyTax = financialDoc?.let { TaxModule.calculateCompanyTaxZA(it.revenue!!, it.expenses!!) }
        if (anomalies.isEmpty() && companyTax == null) return null
        return FinancialAnalysis(companyTax = companyTax, flaggedAnomalies = anomalies)
    }

    private fun attachPersonsAndStatutes(
        contradictions: List<Contradiction>,
        persons: List<com.verumomnis.forensic.model.ExtractedPerson>,
        jurisdictionSource: com.verumomnis.forensic.model.JurisdictionSource,
        documents: List<EvidenceDocument>
    ): List<Contradiction> = contradictions.map { c ->
        val claimA = anchorClaim(c.claimA, documents)
        val claimB = anchorClaim(c.claimB, documents)
        val claimDocs = listOf(claimA, claimB).mapNotNull { claim ->
            documentsForSource(claim.source, claim.evidenceId, documents)
        }
        val explicitAuthors = claimDocs.mapNotNull(EntityExtractor::authorOf).distinctBy { it.lowercase() }
        // The detector's proposed actor must agree with an explicit source
        // attribution; merely being the only named speaker in a file is not enough.
        val personAuthor = explicitAuthors.firstOrNull { it.equals(c.respondent, ignoreCase = true) }
        val person = personAuthor?.let { name -> persons.firstOrNull { it.name.equals(name, true) } }
        val mentioned = persons.filter { candidate ->
            listOf(claimA.text, claimB.text).any { text ->
                Regex("""(?<![\p{L}\p{N}])${Regex.escape(candidate.name)}(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(text)
            }
        }.singleOrNull()
        val legalReferences = JurisdictionService.legalReferencesFor(
            jurisdictionSource.jurisdiction,
            c.category,
            "${claimA.text}\n${claimB.text}"
        )
        c.copy(
            anchoredPerson = person,
            respondent = person?.name ?: if (mentioned != null) "Unidentified person (possible reference: ${mentioned.name})" else "Unidentified person",
            claimA = claimA,
            claimB = claimB,
            jurisdiction = jurisdictionSource.jurisdiction,
            applicableLaw = legalReferences.map { it.citation },
            legalReferences = legalReferences,
            personLinkStatus = when {
                person != null -> "IDENTIFIED_FROM_EXPLICIT_SOURCE_ATTRIBUTION"
                mentioned != null -> "UNCERTAIN_MENTION_ONLY"
                else -> "UNIDENTIFIED"
            }
        )
    }

    private fun documentsForSource(
        source: String,
        evidenceId: String,
        docs: List<EvidenceDocument>
    ): EvidenceDocument? = docs.firstOrNull {
        it.evidenceId == evidenceId || it.evidenceId == source || it.fileName == source
    }

    private fun anchorClaim(
        claim: com.verumomnis.forensic.model.ContradictionClaim,
        documents: List<EvidenceDocument>
    ): com.verumomnis.forensic.model.ContradictionClaim {
        val preferred = documentsForSource(claim.source, claim.evidenceId, documents)
        val candidates = if (preferred != null) listOf(preferred) else documents
        val match = candidates.firstNotNullOfOrNull { doc ->
            val pageTexts = doc.extractionCoverage?.pages.orEmpty().mapNotNull { page ->
                page.extractedText?.let { Triple(page.page, it, page.status) }
            }
            val spans = if (pageTexts.isNotEmpty()) {
                pageTexts.flatMap { (page, text, _) -> text.lines().mapIndexed { index, line -> Triple(page, index + 1, line) } }
            } else {
                doc.text.lines().mapIndexed { index, line -> Triple(0, index + 1, line) }
            }
            val normalizedQuote = claim.text.replace(Regex("\\s+"), " ").trim()
            spans.firstOrNull { (_, _, line) ->
                normalizedQuote.isNotBlank() &&
                    line.replace(Regex("\\s+"), " ").trim().contains(normalizedQuote, ignoreCase = true)
            }?.let { span -> doc to span }
        }
        if (match == null) return claim.copy(page = 0, line = 0)
        val (doc, span) = match
        return claim.copy(
            source = doc.fileName,
            evidenceId = doc.evidenceId,
            page = span.first,
            line = span.second,
            sha512 = doc.sha512
        )
    }

    private fun sourcePageLines(doc: EvidenceDocument): List<Pair<Int, String>> {
        val pages = doc.extractionCoverage?.pages.orEmpty().mapNotNull { page ->
            page.extractedText?.let { page.page to it }
        }
        if (pages.isNotEmpty()) return pages.flatMap { (number, text) -> text.lines().map { number to it } }
        return doc.text.lines().map { 0 to it }
    }

    /** Primary B1 contradiction path: hybrid v5.3.1c engine over raw text evidence. */
    private fun runHybridEngine(documents: List<EvidenceDocument>, now: Instant): List<Contradiction> {
        val caseName = detectCaseName(documents)
        val texts = documents.map { it.text }
        val sources = documents.map { it.evidenceId }
        val engine = VerumContradictionEngine(caseId = "VO-NINE-BRAIN", caseName = caseName)
        val report = engine.processFromTexts(texts, sources)
        return ContradictionToForensicAdapter.toContradictions(report, now)
    }

    private fun detectCaseName(documents: List<EvidenceDocument>): String {
        val corpus = documents.joinToString("\n") { it.text }.lowercase()
        return when {
            "greensky" in corpus || "rakez" in corpus -> "greensky"
            "southbridge" in corpus || "vo-hr-2025" in corpus -> "southbridge"
            "louw" in corpus && "moolla" in corpus -> "louw v moolla"
            "louw" in corpus && "olivier" in corpus -> "louw v olivier"
            "liebenberg" in corpus || "standard bank" in corpus -> "liebenberg v standard bank"
            "mostert" in corpus || "digsim" in corpus || "pha 2026" in corpus -> "mostert v digsim"
            else -> "allfuels"
        }
    }

    /**
 * Merges contradictions from the hybrid primary engine and legacy rule booster.
 * Results are deduplicated, severity-ranked, and renumbered into the canonical
 * C-001, C-002, ... identifier space.
 */
    private fun mergeAndRankContradictions(
        hybrid: List<Contradiction>,
        legacy: List<Contradiction>
    ): List<Contradiction> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<Contradiction>()
        val addIfUnique: (Contradiction) -> Unit = { c ->
            val key = "${c.claimA.text}|${c.claimB.text}|${c.category}".lowercase()
            if (key !in seen) {
                seen += key
                out += c
            }
        }
        // Legacy explicit rules have highest priority for stable C-001 sequencing.
        legacy.forEach(addIfUnique)
        hybrid.forEach(addIfUnique)
        return out
            .sortedWith(compareBy({ it.severity.ordinal }, { it.claimA.text }))
            .mapIndexed { i, c -> c.copy(contradictionId = "C-%03d".format(i + 1)) }
    }
}
