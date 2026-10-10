package com.verumomnis.forensic.model

import com.verumomnis.forensic.identity.IdentityProof
import kotlinx.serialization.Serializable

/** Ordinal confidence only — never probability as truth (spec 8.1). */
enum class Confidence { VERY_HIGH, HIGH, MODERATE, LOW, INSUFFICIENT }

enum class Severity { CRITICAL, VERY_HIGH, HIGH, MODERATE, LOW }

/** The 7 canonical contradiction subjects (build spec Section 9.2). */
enum class ContradictionCategory {
    GOODWILL_VALUE, CONTRACT_VALIDITY, SIGNATURE_STATUS, SECTION_12B,
    COMPENSATION, PERJURY, COERCION, OTHER
}

enum class ContradictionType {
    DIRECT_NEGATION, IMPLIED_CONTRADICTION, ACTION_VS_WORDS, TEMPORAL_SHIFT, ROLE_INCONSISTENCY
}

enum class StatementType { CLAIM, DENIAL, ADMISSION, DEMAND, PROMISE, THREAT, SWORN_STATEMENT, CONTEMPORANEOUS }

enum class PersonRole { VICTIM, RESPONDENT, WITNESS, UNKNOWN }

@Serializable
data class ExtractedPerson(
    val name: String,
    val age: Int? = null,
    val idNumber: String? = null,
    val address: String? = null,
    val role: PersonRole = PersonRole.UNKNOWN,
    val context: String = ""
)

@Serializable
data class GpsRecord(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double = 0.0,
    val altitude: Double = 0.0,
    val timestamp: String
)

/**
 * Legacy consensus record retained for schema compatibility.
 *
 * The on-device build runs NO external LLMs, so the gemma3/phi3 lanes must
 * always read "NOT_RUN". Only the Nine-Brain council vote is real
 * ("COUNCIL_ACCEPTED" / "COUNCIL_NOT_CONFIRMED"). Never write model names or
 * "CONCURS"/"VERIFIED" here — that would fabricate an AI review that did not
 * happen.
 */
@Serializable
data class TripleConsensus(
    val gemma3: String = "NOT_RUN",
    val phi3: String = "NOT_RUN",
    val nineBrain: String = "NOT_RUN",
    val quorum: Boolean = false
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
    val sha512: String,
    val statementType: StatementType = StatementType.CLAIM
)

/** Contradiction Model (spec 12.2 / build spec Section 9.4). */
@Serializable
data class Contradiction(
    val contradictionId: String,
    val brainSource: String,
    val category: ContradictionCategory = ContradictionCategory.OTHER,
    val type: ContradictionType = ContradictionType.DIRECT_NEGATION,
    val respondent: String = "",
    val anchoredPerson: ExtractedPerson? = null,
    val claimA: ContradictionClaim,
    val claimB: ContradictionClaim,
    val severity: Severity,
    val description: String = "",
    val legalSignificance: String,
    val applicableLaw: List<String> = emptyList(),
    val confidence: Confidence = Confidence.HIGH,
    val patternIndicator: Boolean = false,
    val resolutionStatus: String = "CONFIRMED",
    val tripleAiConsensus: TripleConsensus = TripleConsensus(),
    val timestamp: String,
    /** Triple-verification narrative (Thesis / Antithesis / Synthesis). */
    val thesis: String = "",
    val antithesis: String = "",
    val synthesis: String = "",
    /** Brain council vote: which brains confirmed B1's flag. */
    val confirmingBrains: List<String> = emptyList(),
    /** ACCEPTED | INDETERMINATE_DUE_TO_CONCEALMENT | INSUFFICIENT */
    val councilStatus: String = "INSUFFICIENT",
    /** Original v5.3.1c engine contradiction type, if produced by the B1-v5.3.1c engine. */
    val engineType: String = ""
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
    val tripleVerification: TripleConsensus = TripleConsensus(),
    val identityProof: IdentityProof? = null
) {
    /** Per-page seal footer (spec 6.3). */
    fun sealFooter(): String =
        "VERUM OMNIS SEAL | seal-$shortcode | $truncatedHash | $shortcode"

    /** Extended footer including identity attestation (rendered, not part of the seal hash). */
    fun extendedFooter(): String {
        val id = identityProof?.shortFingerprint()?.let { " · ID $it" } ?: ""
        return sealFooter() + id
    }
}

/** A behavioural signal (B4): gaslighting, stress or manipulation. */
@Serializable
data class BehavioralFinding(
    val type: String,
    val trigger: String,
    val context: String,
    val severity: Severity,
    val evidenceId: String,
    val page: Int
)

@Serializable
data class BehavioralAnalysis(
    val gaslighting: List<BehavioralFinding> = emptyList(),
    val stress: List<BehavioralFinding> = emptyList(),
    val manipulation: List<BehavioralFinding> = emptyList(),
    val score: Double = 0.0
) {
    fun isEmpty(): Boolean = gaslighting.isEmpty() && stress.isEmpty() && manipulation.isEmpty()
}

/** Result of a full forensic scan run by the Nine-Brain engine. */
@Serializable
data class JurisdictionSource(
    val jurisdiction: String,
    val gps: GpsRecord? = null,
    val statutes: List<String> = emptyList()
)

@Serializable
data class ForensicFindings(
    val documentsAnalyzed: Int,
    val evidenceAtoms: List<EvidenceAtom>,
    val contradictions: List<Contradiction>,
    val timeline: List<TimelineEvent>,
    val legalMappings: List<String>,
    val jurisdiction: String,
    val jurisdictionSource: JurisdictionSource? = null,
    val extractedPersons: List<ExtractedPerson> = emptyList(),
    val financial: FinancialAnalysis? = null,
    val behavioral: BehavioralAnalysis? = null,
    val audio: AudioAnalysis? = null,
    val documentForensics: List<String> = emptyList(),
    val communications: List<String> = emptyList(),
    val rndValidation: List<String> = emptyList(),
    val mediaExhibits: List<MediaExhibit> = emptyList(),
    val brainVerdicts: Map<String, String>,
    val guardian: GuardianAssessment? = null
)
