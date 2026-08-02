package com.verumomnis.forensic.model

import kotlinx.serialization.Serializable

/** A single row of the Offence Matrix (spec 9.1). */
@Serializable
data class OffenceRow(
    val offence: String,
    val person: String,
    val applicableLaw: List<String>,
    val evidenceAnchor: String,
    val confidence: Confidence
)

/**
 * A court-ready forensic report (Part IX). Every contradiction is anchored to a
 * person, a page number and an applicable law/statute.
 */
@Serializable
data class ForensicReport(
    val reference: String,
    val title: String,
    val classification: String,
    val createdAt: String,
    val jurisdiction: String,
    val jurisdictionSource: JurisdictionSource? = null,
    val extractedPersons: List<ExtractedPerson> = emptyList(),
    val executiveSummary: String,
    val contradictions: List<Contradiction>,
    val timeline: List<TimelineEvent>,
    val legalFramework: List<String>,
    val offenceMatrix: List<OffenceRow>,
    val financial: FinancialAnalysis? = null,
    val mediaExhibits: List<MediaExhibit> = emptyList(),
    val findingsJsonPath: String = "",
    val seal: SealRecord,
    val body: String,
    val gemmaNarrative: String = "",
    /**
     * Contradictions found by pairing live OJRS judicial research against the sealed
     * evidence (see JudicialPairingService). Advisory, externally-sourced, and — like
     * [gemmaNarrative] — deliberately NOT part of the seal: [seal] is computed over
     * [body] alone, before this section is appended.
     */
    val judicialFindings: List<Contradiction> = emptyList(),
    /** Rendered prose for [judicialFindings], ready to append after [body]. */
    val judicialCrossReferenceSection: String = ""
)
