package com.verumomnis.forensic.model

import kotlinx.serialization.Serializable

/**
 * Findings from online judicial and web research conducted by the
 * Deep Research Engine (DRE). These supplement the sealed evidence
 * with external court precedents, company data, and statutory analysis.
 *
 * Research findings are NOT part of the cryptographic seal — they are
 * advisory only and clearly labelled as externally sourced.
 */
@Serializable
data class ResearchFindings(
    /** Unique identifier for this research session. */
    val researchId: String = "",
    /** Timestamp when research was conducted. */
    val conductedAt: String = "",
    /** The sealed case that triggered this research. */
    val sourceCaseReference: String = "",
    /** Court cases found from judicial databases (SAFLII, CourtListener, etc.). */
    val judicialCases: List<JudicialCase> = emptyList(),
    /** Web search results for company, statute, or problem research. */
    val webResults: List<WebSearchResult> = emptyList(),
    /** Statutes and regulations found applicable to the case. */
    val applicableStatutes: List<ResearchStatute> = emptyList(),
    /** Precedent analysis: how found cases relate to the sealed evidence. */
    val precedentAnalysis: String = "",
    /** Confidence in the research completeness. */
    val researchConfidence: ResearchConfidence = ResearchConfidence.MODERATE,
    /** URLs of all sources consulted. */
    val sourceUrls: List<String> = emptyList(),
    /** Whether the research was conducted automatically or via user request. */
    val triggeredBy: ResearchTrigger = ResearchTrigger.USER_REQUEST
)

@Serializable
data class JudicialCase(
    /** Case citation (e.g., "[2024] ZACC 12"). */
    val citation: String = "",
    /** Case title. */
    val title: String = "",
    /** Court that decided the case. */
    val court: String = "",
    /** Date of judgment. */
    val judgmentDate: String = "",
    /** Brief summary of the case holding. */
    val summary: String = "",
    /** How this case relates to the sealed evidence. */
    val relevanceToCase: String = "",
    /** Key legal principles established. */
    val keyPrinciples: List<String> = emptyList(),
    /** Full URL to the judgment. */
    val sourceUrl: String = "",
    /** The database this case was retrieved from. */
    val database: JudicialDatabase = JudicialDatabase.UNKNOWN
)

@Serializable
data class WebSearchResult(
    /** Title of the search result. */
    val title: String = "",
    /** Brief snippet or summary. */
    val snippet: String = "",
    /** Source URL. */
    val url: String = "",
    /** Domain of the source (e.g., "saflii.org"). */
    val domain: String = "",
    /** Relevance score (0.0 - 1.0). */
    val relevanceScore: Double = 0.0,
    /** Category: company, statute, legal_article, news, etc. */
    val category: WebResultCategory = WebResultCategory.OTHER
)

@Serializable
data class ResearchStatute(
    /** Full citation of the statute. */
    val citation: String = "",
    /** Title of the act/regulation. */
    val title: String = "",
    /** Relevant section(s). */
    val sections: List<String> = emptyList(),
    /** Jurisdiction. */
    val jurisdiction: String = "",
    /** Summary of how it applies to the case. */
    val application: String = "",
    /** URL to full text. */
    val sourceUrl: String = ""
)

enum class ResearchConfidence {
    VERY_HIGH, HIGH, MODERATE, LOW, INSUFFICIENT
}

enum class ResearchTrigger {
    USER_REQUEST,    // User clicked "Deep Research"
    AUTO_SUGGEST,    // App suggested research based on scan findings
    REPORT_APPENDIX  // Research triggered during report generation
}

enum class JudicialDatabase {
    SAFLII,          // South African Legal Information Institute
    COURTLISTENER,   // US CourtListener (Free Law Project)
    BAILII,          // British and Irish Legal Information Institute
    CANLII,          // Canadian Legal Information Institute
    AUSTLII,         // Australasian Legal Information Institute
    EUR_LEX,         // EU Official Journal
    INDIAN_KANOON,   // Indian Kanoon
    OPENLAW,         // Other open legal databases
    UNKNOWN
}

enum class WebResultCategory {
    COMPANY_PROFILE,      // Information about a company
    STATUTE_REGULATION,   // Laws and regulations
    LEGAL_ARTICLE,        // Legal commentary/analysis
    COURT_CASE,           // Court judgment
    NEWS_REPORT,          // News article
    ACADEMIC_PAPER,       // Academic/scholarly source
    GOVERNMENT_SOURCE,    // Government publication
    OTHER
}
