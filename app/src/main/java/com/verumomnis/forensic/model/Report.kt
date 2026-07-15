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
    val gemmaNarrative: String = ""
)
