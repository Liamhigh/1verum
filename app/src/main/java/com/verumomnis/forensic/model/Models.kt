package com.verumomnis.forensic.model

import kotlinx.serialization.Serializable

/** Ordinal confidence only — never probability as truth (spec 8.1). */
enum class Confidence { VERY_HIGH, HIGH, MODERATE, LOW, INSUFFICIENT }

enum class Severity { CRITICAL, VERY_HIGH, HIGH, MODERATE, LOW }

@Serializable
data class GpsRecord(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double = 0.0,
    val altitude: Double = 0.0,
    val timestamp: String
)

@Serializable
data class TripleConsensus(
    val gemma3: String = "VERIFIED",
    val phi3: String = "VERIFIED",
    val nineBrain: String = "VERIFIED",
    val quorum: Boolean = true
)

/** Evidence Atom Model (spec 12.1). */
@Serializable
data class EvidenceAtom(
    val atomId: String,
    val evidenceId: String,
    val type: String,
    val sourceFile: String,
    val sha512: String,
    val pageNumber: Int = 0,
    val lineRange: String = "",
    val content: String,
    val jurisdiction: String = "",
    val legalCitations: List<String> = emptyList(),
    val confidence: Confidence = Confidence.HIGH,
    val extractedBy: String,
    val gps: GpsRecord? = null,
    val tripleAiConsensus: TripleConsensus = TripleConsensus(),
    val timestamp: String
)

@Serializable
data class ContradictionClaim(
    val text: String,
    val source: String,
    val evidenceId: String,
    val page: Int = 0,
    val line: Int = 0,
    val sha512: String
)

/** Contradiction Model (spec 12.2). */
@Serializable
data class Contradiction(
    val contradictionId: String,
    val brainSource: String,
    val respondent: String = "",
    val claimA: ContradictionClaim,
    val claimB: ContradictionClaim,
    val severity: Severity,
    val legalSignificance: String,
    val applicableLaw: List<String> = emptyList(),
    val confidence: Confidence = Confidence.HIGH,
    val resolutionStatus: String = "CONFIRMED",
    val tripleAiConsensus: TripleConsensus = TripleConsensus(),
    val timestamp: String
)

@Serializable
data class TimelineEvent(
    val eventId: String,
    val dateTime: String,
    val description: String,
    val legalSignificance: String = "",
    val evidenceId: String,
    val page: Int = 0,
    val sha512: String,
    val gps: GpsRecord? = null,
    val severity: Severity = Severity.MODERATE
)

@Serializable
data class BlockchainAnchor(
    val network: String = "bitcoin",
    val transactionHash: String = "",
    val blockHeight: Long = 0,
    val blockTime: String = "",
    val confirmations: Int = 0
)

/** Seal Record Model (spec 12.3). */
@Serializable
data class SealRecord(
    val sealId: String,
    val documentType: String,
    val documentReference: String,
    val sha512: String,
    val truncatedHash: String,
    val shortcode: String,
    val constitutionVersion: String,
    val constitutionRuleset: String,
    val blockchain: BlockchainAnchor = BlockchainAnchor(),
    val calendarUrls: List<String> = listOf(
        "https://alice.btc.calendar.opentimestamps.org",
        "https://bob.btc.calendar.opentimestamps.org"
    ),
    val otsProofFile: String = "",
    val status: String = "PENDING",
    val createdAt: String,
    val confirmedAt: String = "",
    val tripleVerification: TripleConsensus = TripleConsensus()
) {
    /** Per-page seal footer (spec 6.3). */
    fun sealFooter(): String =
        "VERUM OMNIS SEAL | seal-$shortcode | $truncatedHash | $shortcode"
}

/** Result of a full forensic scan run by the Nine-Brain engine. */
@Serializable
data class ForensicFindings(
    val documentsAnalyzed: Int,
    val evidenceAtoms: List<EvidenceAtom>,
    val contradictions: List<Contradiction>,
    val timeline: List<TimelineEvent>,
    val legalMappings: List<String>,
    val jurisdiction: String,
    val financial: FinancialAnalysis? = null,
    val brainVerdicts: Map<String, String>
)
