package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.BehavioralAnalysis
import com.verumomnis.forensic.model.Confidence
import com.verumomnis.forensic.model.Contradiction
import com.verumomnis.forensic.model.ContradictionCategory
import com.verumomnis.forensic.model.ContradictionClaim
import com.verumomnis.forensic.model.ContradictionType
import com.verumomnis.forensic.model.EvidenceAtom
import com.verumomnis.forensic.model.FinancialAnalysis
import com.verumomnis.forensic.model.ForensicFindings
import com.verumomnis.forensic.model.Severity
import com.verumomnis.forensic.model.TimelineEvent
import com.verumomnis.forensic.engine.contradiction.VerumContradictionEngine
import com.verumomnis.forensic.engine.contradiction.formatReport
import java.time.Instant

/**
 * The 9-Brain forensic engine (build spec Sections 5, 8). Deterministic and
 * always-on — it is the third verifier in Triple-AI consensus on every device.
 *
 *  - B1 Contradiction   -> [VerumContradictionEngine v5.2.9] + legacy fallback
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
        now: Instant = Instant.now(),
        caseName: String = ""  // "allfuels" or "greensky" for v5.2.9 case config
    ): ForensicFindings {
        val timestamp = now.toString()

        // B8 audio: tamper/voice-stress + diarised transcript
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
        val jurisdiction = detectJurisdiction(allDocs)

        val atoms = buildEvidenceAtoms(allDocs, jurisdiction, timestamp)

        // B1: v5.2.9 Contradiction Engine (primary) + legacy fallback
        val v529Report = runV529Engine(allDocs, caseName, now)
        val legacyContradictions = ContradictionExtractor.extract(allDocs, now)

        // Merge: v5.2.9 contradictions take priority, fill gaps with legacy
        val contradictions = mergeContradictions(v529Report, legacyContradictions, timestamp)

        val documentForensics = documentBrain(allDocs)
        val timeline = reconstructTimeline(allDocs)
        val communications = communicationsBrain(timeline)
        val behavioral = BehavioralBrain.analyze(allDocs)
        val financial = analyzeFinancials(allDocs)
        val legalMappings = mapLegalFramework(allDocs, jurisdiction)
        val rndValidation = rndBrain(contradictions, financial, behavioral) +
            v529Report.actorProfiles.map { "PROFILE: ${it.name} dishonesty=${it.dishonestyScore}/100" }

        val brainVerdicts = linkedMapOf(
            "B1-Contradiction" to if (contradictions.isEmpty()) "CLEAR" else "${contradictions.size} FOUND (v5.2.9)",
            "B2-Document" to if (documentForensics.isEmpty()) "NO TAMPER" else "${documentForensics.size} SIGNALS",
            "B3-Communications" to if (communications.isEmpty()) "NO GAPS" else "${communications.size} GAPS",
            "B4-Behavioral" to if (behavioral.isEmpty()) "CLEAR" else "score ${"%.2f".format(behavioral.score)}",
            "B5-Timeline" to "${timeline.size} EVENTS",
            "B6-Financial" to if (financial?.flaggedAnomalies.isNullOrEmpty()) "NO ANOMALIES" else "${financial!!.flaggedAnomalies.size} FLAGGED",
            "B7-LegalMapping" to "${legalMappings.size} MAPPINGS",
            "B8-Audio" to audioVerdict(audio, audioAnalysis),
            "B9-RnD" to "${rndValidation.size} NOTES"
        )

        return ForensicFindings(
            documentsAnalyzed = allDocs.size,
            evidenceAtoms = atoms,
            contradictions = contradictions,
            timeline = timeline,
            legalMappings = legalMappings,
            jurisdiction = jurisdiction,
            financial = financial,
            behavioral = behavioral.takeUnless { it.isEmpty() },
            audio = audioAnalysis.takeUnless { it.isEmpty() },
            documentForensics = documentForensics,
            communications = communications,
            rndValidation = rndValidation,
            mediaExhibits = buildMediaExhibits(media, jurisdiction),
            brainVerdicts = brainVerdicts
        )
    }

    /**
     * Run the v5.2.9 contradiction engine on the document set.
     * Converts EvidenceDocuments to text strings for the engine.
     */
    private fun runV529Engine(
        documents: List<EvidenceDocument>,
        caseName: String,
        now: Instant
    ): com.verumomnis.forensic.engine.contradiction.EngineForensicReport {
        val texts = documents.map { it.text }
        val sources = documents.map { it.fileName }
        return VerumContradictionEngine(
            caseId = "VO-${now.toEpochMilli()}",
            caseName = caseName,
            injectedTimestamp = now.toEpochMilli()
        ).processFromTexts(texts, sources)
    }

    /**
     * Merge v5.2.9 engine contradictions with legacy extractor results.
     * v5.2.9 takes priority; legacy fills any gaps.
     */
    private fun mergeContradictions(
        v529Report: com.verumomnis.forensic.engine.contradiction.EngineForensicReport,
        legacy: List<Contradiction>,
        timestamp: String
    ): List<Contradiction> {
        // Convert v5.2.9 contradictions to legacy format
        val v529Converted = v529Report.contradictions.map { ec ->
            val category = when {
                ec.type.name.contains("GOODWILL") || ec.propositionAText.contains("goodwill", true) ->
                    ContradictionCategory.GOODWILL_VALUE
                ec.type.name.contains("CONTRACT") || ec.propositionAText.contains("contract", true) ->
                    ContradictionCategory.CONTRACT_VALIDITY
                ec.type.name.contains("SIGNATURE") || ec.propositionAText.contains("signature", true) ->
                    ContradictionCategory.SIGNATURE_STATUS
                ec.type.name.contains("PERJURY") || ec.type.name.contains("JUDICIAL") ->
                    ContradictionCategory.PERJURY
                ec.type.name.contains("COERCION") || ec.propositionAText.contains("threat", true) ->
                    ContradictionCategory.COERCION
                ec.type.name.contains("RACKETEERING") -> ContradictionCategory.COERCION
                else -> ContradictionCategory.OTHER
            }

            val legacyType = when {
                ec.type == com.verumomnis.forensic.engine.contradiction.EngineContradictionType.STATEMENT_VS_STATEMENT ->
                    ContradictionType.DIRECT_NEGATION
                ec.type == com.verumomnis.forensic.engine.contradiction.EngineContradictionType.TEMPORAL_CONTRADICTION ->
                    ContradictionType.TEMPORAL_SHIFT
                ec.type == com.verumomnis.forensic.engine.contradiction.EngineContradictionType.BEHAVIORAL ->
                    ContradictionType.ROLE_INCONSISTENCY
                else -> ContradictionType.DIRECT_NEGATION
            }

            val severity = when (ec.severity) {
                com.verumomnis.forensic.engine.contradiction.EngineSeverity.VERY_HIGH -> Severity.VERY_HIGH
                com.verumomnis.forensic.engine.contradiction.EngineSeverity.HIGH -> Severity.HIGH
                com.verumomnis.forensic.engine.contradiction.EngineSeverity.MODERATE -> Severity.MODERATE
                com.verumomnis.forensic.engine.contradiction.EngineSeverity.LOW -> Severity.LOW
                com.verumomnis.forensic.engine.contradiction.EngineSeverity.INSUFFICIENT -> Severity.LOW
            }

            Contradiction(
                contradictionId = ec.contradictionId,
                brainSource = "B1-v5.2.9",
                category = category,
                type = legacyType,
                respondent = ec.propositionAActor,
                claimA = ContradictionClaim(
                    text = ec.propositionAText,
                    source = ec.detectedFact.sourceDocument,
                    evidenceId = "",
                    page = ec.detectedFact.sourcePage,
                    sha512 = ec.detectedFact.sha512Hash
                ),
                claimB = ContradictionClaim(
                    text = ec.propositionBText,
                    source = ec.detectedFact.sourceDocument,
                    evidenceId = "",
                    page = 0,
                    sha512 = ec.detectedFact.sha512Hash
                ),
                severity = severity,
                description = ec.conflictDescription,
                legalSignificance = ec.legalHypothesis?.suggestedOffence ?: "",
                confidence = when (ec.confidence) {
                    com.verumomnis.forensic.engine.contradiction.EngineConfidence.DETERMINISTIC -> Confidence.VERY_HIGH
                    com.verumomnis.forensic.engine.contradiction.EngineConfidence.VERY_HIGH -> Confidence.VERY_HIGH
                    com.verumomnis.forensic.engine.contradiction.EngineConfidence.HIGH -> Confidence.HIGH
                    com.verumomnis.forensic.engine.contradiction.EngineConfidence.MODERATE -> Confidence.MODERATE
                    com.verumomnis.forensic.engine.contradiction.EngineConfidence.LOW -> Confidence.LOW
                    com.verumomnis.forensic.engine.contradiction.EngineConfidence.INSUFFICIENT -> Confidence.LOW
                },
                patternIndicator = ec.type.name.contains("RACKETEERING"),
                timestamp = timestamp
            )
        }

        // If v5.2.9 found contradictions, use those (they're more detailed)
        // Otherwise fall back to legacy
        return if (v529Converted.isNotEmpty()) v529Converted else legacy
    }

    // ==================== B2-B9 (unchanged) ====================

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

    private fun jurisdictionFor(gps: com.verumomnis.forensic.model.GpsRecord): String = when {
        gps.latitude in -35.0..-22.0 && gps.longitude in 16.0..33.0 -> "ZA-KZN"
        gps.latitude in 22.0..26.5 && gps.longitude in 51.0..56.5 -> "UAE-RAKEZ"
        else -> "INTL"
    }

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
            timestamp = timestamp
        )
    }

    private fun documentBrain(documents: List<EvidenceDocument>): List<String> {
        val signals = mutableListOf<String>()
        documents.forEach { doc ->
            val creator = doc.creatorTool ?: return@forEach
            val kind = (doc.documentKind ?: doc.type).lowercase()
            if (creator.contains("photoshop", ignoreCase = true) &&
                (kind.contains("bank") || kind.contains("statement") || kind.contains("invoice"))
            ) {
                signals += "CREATOR_TOOL_MISMATCH: ${doc.fileName} (${doc.documentKind ?: doc.type}) created with '$creator'"
            }
        }
        return signals
    }

    private fun communicationsBrain(timeline: List<TimelineEvent>): List<String> {
        val dated = timeline.mapNotNull { ev ->
            EntityExtractor.extractDate(ev.dateTime)?.let { it to ev }
        }.sortedBy { it.first }
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

    private fun rndBrain(
        contradictions: List<Contradiction>,
        financial: FinancialAnalysis?,
        behavioral: BehavioralAnalysis
    ): List<String> {
        val notes = mutableListOf<String>()
        if (contradictions.isEmpty() && !behavioral.isEmpty()) {
            notes += "B1 found no contradictions but B4 flagged behavioural signals — recommend deeper B1 pass."
        }
        if (contradictions.any { it.applicableLaw.any { l -> l.contains("Fraud") } } &&
            financial?.flaggedAnomalies.isNullOrEmpty()
        ) {
            notes += "Fraud mapped by B7 but no financial anomaly from B6 — recommend deeper financial audit."
        }
        if (contradictions.any { it.patternIndicator }) {
            notes += "Racketeering pattern indicator present — recommend POCA 121/1998 enterprise-liability review."
        }
        return notes
    }

    private fun reconstructTimeline(documents: List<EvidenceDocument>): List<TimelineEvent> {
        val events = mutableListOf<TimelineEvent>()
        var counter = 1
        documents.forEach { doc ->
            doc.text.lines().forEach { line ->
                DATE_REGEX.find(line)?.let { m ->
                    events += TimelineEvent(
                        eventId = "T-%03d".format(counter++),
                        dateTime = m.value,
                        description = line.trim().take(160),
                        evidenceId = doc.evidenceId,
                        page = 1,
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
        val text = documents.joinToString("\n") { it.text }.lowercase()
        val mappings = linkedSetOf<String>()
        if (jurisdiction.startsWith("ZA")) mappings += "South African Common Law"
        if (jurisdiction.startsWith("UAE")) mappings += "UAE Federal Law (CCL, Cybercrime, Commercial Fraud)"
        if (Regex("petroleum|fuel|diesel|engen").containsMatchIn(text))
            mappings += "Petroleum Products Act 120 of 1977, s.12B"
        if (Regex("fraud|misrepresent|deceiv").containsMatchIn(text))
            mappings += "Four Pillars of Fraud (common law fraud elements)"
        if (Regex("shareholder|director|company|companies").containsMatchIn(text))
            mappings += "Companies Act 71 of 2008 (oppression, fiduciary duty)"
        if (Regex("racketeer|pattern of|enterprise").containsMatchIn(text))
            mappings += "Prevention of Organised Crime Act 121 of 1998 (racketeering)"
        if (text.contains("constitutional court"))
            mappings += "Constitutional Court case database"
        return mappings.toList()
    }

    private fun analyzeFinancials(documents: List<EvidenceDocument>): FinancialAnalysis? {
        val anomalies = mutableListOf<String>()
        val amounts = documents.flatMap { doc ->
            MONEY_REGEX.findAll(doc.text).map {
                it.groupValues[1].replace(Regex("[,\\s]"), "").toDoubleOrNull() ?: 0.0
            }
        }.filter { it > 0 }
        amounts.groupBy { it }.filter { it.value.size > 1 }.forEach { (amount, occ) ->
            anomalies += "Duplicate amount R%,.2f appears %d times".format(amount, occ.size)
        }
        val financialDoc = documents.firstOrNull { it.revenue != null && it.expenses != null }
        val companyTax = financialDoc?.let { TaxModule.calculateCompanyTaxZA(it.revenue!!, it.expenses!!) }
        if (anomalies.isEmpty() && companyTax == null) return null
        return FinancialAnalysis(companyTax = companyTax, flaggedAnomalies = anomalies)
    }

    private fun detectJurisdiction(documents: List<EvidenceDocument>): String {
        val gps = documents.firstNotNullOfOrNull { it.gps } ?: return "ZA-KZN"
        return when {
            gps.latitude in -35.0..-22.0 && gps.longitude in 16.0..33.0 -> "ZA-KZN"
            gps.latitude in 22.0..26.5 && gps.longitude in 51.0..56.5 -> "UAE-RAKEZ"
            else -> "INTL"
        }
    }
}
