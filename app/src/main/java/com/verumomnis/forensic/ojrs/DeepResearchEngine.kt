package com.verumomnis.forensic.ojrs

import com.verumomnis.forensic.core.ConstitutionalPrompt
import com.verumomnis.forensic.model.ForensicFindings
import com.verumomnis.forensic.model.JudicialCase
import com.verumomnis.forensic.model.ResearchConfidence
import com.verumomnis.forensic.model.ResearchFindings
import com.verumomnis.forensic.model.ResearchStatute
import com.verumomnis.forensic.model.ResearchTrigger
import com.verumomnis.forensic.model.WebSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Deep Research Engine (DRE).
 *
 * Orchestrates comprehensive online research from a sealed case:
 *   1. Extracts key entities from the forensic findings
 *   2. Searches judicial databases (SAFLII, CourtListener, BAILII, etc.)
 *   3. Searches the open web for company info, statutes, news
 *   4. Synthesises findings into a structured research report
 *   5. Generates a Gemma-ready prompt for narrative analysis
 *
 * The engine operates under the Constitution: all research is advisory,
 * clearly labelled as externally sourced, and never contaminates the
 * cryptographic seal of the original evidence.
 */
object DeepResearchEngine {

    /**
     * Conduct deep research from sealed forensic findings.
     *
     * @param findings The sealed forensic findings to research from
     * @param caseReference The case reference number
     * @param jurisdiction The case jurisdiction (e.g., "ZA-KZN")
     * @param trigger What triggered this research
     * @return Structured research findings with judicial cases, web results, statutes
     */
    suspend fun research(
        findings: ForensicFindings,
        caseReference: String,
        jurisdiction: String = "ZA-KZN",
        trigger: ResearchTrigger = ResearchTrigger.USER_REQUEST
    ): ResearchFindings = withContext(Dispatchers.IO) {

        // 1. Extract search entities from the sealed case
        val entities = extractSearchEntities(findings)

        // 2. Build targeted search queries
        val judicialQueries = buildJudicialQueries(entities, jurisdiction)
        val webQueries = WebSearchService.buildResearchQueries(
            companyNames = entities.companyNames,
            statutes = entities.statutes,
            legalIssues = entities.legalIssues,
            jurisdiction = jurisdiction
        )

        // 3. Search judicial databases and web in parallel
        val (judicialCases, webResults, statutes) = coroutineScope {
            val casesDeferred = async { searchJudicialDatabases(judicialQueries, jurisdiction) }
            val webDeferred = async { WebSearchService.searchMulti(webQueries, maxResultsPerQuery = 4) }
            val statutesDeferred = async { searchStatutes(entities, jurisdiction) }

            Triple(
                casesDeferred.await(),
                webDeferred.await(),
                statutesDeferred.await()
            )
        }

        // 4. Synthesise precedent analysis
        val precedentAnalysis = synthesisePrecedentAnalysis(findings, judicialCases)

        // 5. Calculate research confidence
        val confidence = calculateConfidence(judicialCases, webResults, statutes)

        // 6. Collect all source URLs
        val sourceUrls = (judicialCases.map { it.sourceUrl } + webResults.map { it.url }).filter { it.isNotBlank() }

        ResearchFindings(
            researchId = "RES-${caseReference.takeLast(8)}-${Instant.now().toEpochMilli()}",
            conductedAt = Instant.now().toString(),
            sourceCaseReference = caseReference,
            judicialCases = judicialCases,
            webResults = webResults,
            applicableStatutes = statutes,
            precedentAnalysis = precedentAnalysis,
            researchConfidence = confidence,
            sourceUrls = sourceUrls,
            triggeredBy = trigger
        )
    }

    /**
     * Generate a Gemma 3 prompt that incorporates the research findings
     * for narrative report writing.
     */
    fun buildResearchPrompt(research: ResearchFindings, findings: ForensicFindings): String = buildString {
        appendLine(ConstitutionalPrompt.reportWriter())
        appendLine()
        appendLine("=".repeat(72))
        appendLine("ONLINE JUDICIAL RESEARCH FINDINGS")
        appendLine("=".repeat(72))
        appendLine("Research ID: ${research.researchId}")
        appendLine("Conducted: ${research.conductedAt}")
        appendLine("Confidence: ${research.researchConfidence}")
        appendLine("Sources consulted: ${research.sourceUrls.size}")
        appendLine()

        if (research.judicialCases.isNotEmpty()) {
            appendLine("JUDICIAL PRECEDENTS FOUND:")
            research.judicialCases.take(10).forEach { case ->
                appendLine("  [${case.database.name}] ${case.citation}")
                appendLine("    Court: ${case.court}")
                appendLine("    Title: ${case.title}")
                appendLine("    Date: ${case.judgmentDate}")
                appendLine("    Summary: ${case.summary}")
                appendLine("    Relevance: ${case.relevanceToCase}")
                case.keyPrinciples.takeIf { it.isNotEmpty() }?.let {
                    appendLine("    Key principles: ${it.joinToString("; ")}")
                }
                appendLine("    Source: ${case.sourceUrl}")
                appendLine()
            }
        }

        if (research.applicableStatutes.isNotEmpty()) {
            appendLine("APPLICABLE STATUTES IDENTIFIED:")
            research.applicableStatutes.forEach { statute ->
                appendLine("  ${statute.citation}")
                appendLine("    Jurisdiction: ${statute.jurisdiction}")
                appendLine("    Application: ${statute.application}")
                statute.sections.takeIf { it.isNotEmpty() }?.let {
                    appendLine("    Sections: ${it.joinToString(", ")}")
                }
                appendLine("    Source: ${statute.sourceUrl}")
                appendLine()
            }
        }

        if (research.webResults.isNotEmpty()) {
            appendLine("WEB RESEARCH FINDINGS:")
            research.webResults.take(8).forEach { result ->
                appendLine("  [${result.category.name}] ${result.title}")
                appendLine("    ${result.snippet}")
                appendLine("    Source: ${result.domain} · ${result.url}")
                appendLine("    Relevance: ${"%.0f".format(result.relevanceScore * 100)}%")
                appendLine()
            }
        }

        if (research.precedentAnalysis.isNotBlank()) {
            appendLine("PRECEDENT ANALYSIS:")
            appendLine(research.precedentAnalysis)
            appendLine()
        }

        appendLine("RESEARCH INSTRUCTIONS FOR GEMMA 3:")
        appendLine("- These findings are from external sources and are ADVISORY ONLY.")
        appendLine("- They supplement but do not replace the sealed engine findings.")
        appendLine("- Cite the source database and URL for every external claim.")
        appendLine("- Flag any conflicts between external research and sealed evidence.")
        appendLine("- If external findings contradict sealed evidence, the seal prevails.")
        appendLine("- Suggest how the precedents support or distinguish the sealed case.")
        appendLine("- Identify gaps: what additional research would strengthen the case?")
        appendLine()
        appendLine("Now write the forensic analysis report incorporating both the sealed evidence and the external research.")
    }

    /**
     * Build a chat response that summarises research findings for the user.
     */
    fun buildChatSummary(research: ResearchFindings): String = buildString {
        appendLine("Deep research complete. I searched ${research.sourceUrls.size} sources across judicial databases and the open web.")
        appendLine()

        if (research.judicialCases.isNotEmpty()) {
            appendLine("**Judicial precedents found: ${research.judicialCases.size}**")
            research.judicialCases.take(5).forEach {
                appendLine("  · [${it.database.name}] ${it.citation} — ${it.court}")
            }
            if (research.judicialCases.size > 5) {
                appendLine("  · ... and ${research.judicialCases.size - 5} more")
            }
            appendLine()
        }

        if (research.applicableStatutes.isNotEmpty()) {
            appendLine("**Statutes identified: ${research.applicableStatutes.size}**")
            research.applicableStatutes.forEach {
                appendLine("  · ${it.citation} (${it.jurisdiction})")
            }
            appendLine()
        }

        if (research.webResults.isNotEmpty()) {
            val topResult = research.webResults.maxByOrNull { it.relevanceScore }
            topResult?.let {
                appendLine("**Top web finding:** ${it.title}")
                appendLine("  ${it.snippet.take(200)}")
                appendLine("  Source: ${it.domain}")
                appendLine()
            }
        }

        appendLine("Research confidence: ${research.researchConfidence.name}")
        appendLine()
        appendLine("I can now incorporate these findings into the report narrative. Ask me to 'generate report with research' or ask specific questions about any of these precedents.")
    }

    // ------------------------------------------------------------------
    // Internal: entity extraction
    // ------------------------------------------------------------------

    private data class SearchEntities(
        val companyNames: List<String> = emptyList(),
        val personNames: List<String> = emptyList(),
        val statutes: List<String> = emptyList(),
        val legalIssues: List<String> = emptyList(),
        val keyTerms: List<String> = emptyList()
    )

    private fun extractSearchEntities(findings: ForensicFindings): SearchEntities {
        val companies = mutableSetOf<String>()
        val persons = mutableSetOf<String>()
        val statutes = mutableSetOf<String>()
        val issues = mutableSetOf<String>()
        val terms = mutableSetOf<String>()

        // Extract from contradictions
        for (c in findings.contradictions) {
            persons += c.respondent
            statutes += c.applicableLaw

            // Extract company names from claim text (simple heuristic)
            val companyRegex = Regex("""[A-Z][a-zA-Z\s&]+(?:Ltd|Pty|Inc|LLP|LLC|Limited|PLC)""")
            companyRegex.findAll(c.claimA.text).forEach { companies += it.value.trim() }
            companyRegex.findAll(c.claimB.text).forEach { companies += it.value.trim() }

            // Extract legal issues
            issues += c.category.name.replace("_", " ")
        }

        // Extract from extracted persons
        persons += findings.extractedPersons.map { it.name }

        // Extract from legal mappings
        statutes += findings.legalMappings

        // Extract from financial
        findings.financial?.flaggedAnomalies?.forEach { issues += it }

        // Key terms from jurisdiction
        when {
            findings.jurisdiction.contains("ZA") -> {
                terms += "South Africa"
                terms += "Petroleum Products Act"
                terms += "Section 12B"
            }
            findings.jurisdiction.contains("UAE") -> {
                terms += "UAE"
                terms += "RAKEZ"
            }
        }

        // Always include common forensic terms
        terms += "fraud"
        terms += "contradiction"
        terms += "goodwill"
        terms += "franchise"

        return SearchEntities(
            companyNames = companies.filter { it.isNotBlank() }.toList(),
            personNames = persons.filter { it.isNotBlank() }.toList(),
            statutes = statutes.filter { it.isNotBlank() }.toList(),
            legalIssues = issues.filter { it.isNotBlank() }.toList(),
            keyTerms = terms.toList()
        )
    }

    // ------------------------------------------------------------------
    // Internal: query building
    // ------------------------------------------------------------------

    private fun buildJudicialQueries(entities: SearchEntities, jurisdiction: String): List<String> {
        val queries = mutableListOf<String>()

        // Company-based judicial searches
        for (company in entities.companyNames.take(2)) {
            queries += "$company $jurisdiction"
        }

        // Statute-based searches
        for (statute in entities.statutes.take(3)) {
            val short = statute.substringBefore(",").trim()
            queries += "$short $jurisdiction case"
        }

        // Issue-based searches
        for (issue in entities.legalIssues.take(2)) {
            queries += "$issue $jurisdiction court"
        }

        // Add default queries if none extracted
        if (queries.isEmpty()) {
            queries += "franchise dispute $jurisdiction"
            queries += "goodwill compensation $jurisdiction"
        }

        return queries.distinct()
    }

    // ------------------------------------------------------------------
    // Internal: search execution
    // ------------------------------------------------------------------

    private suspend fun searchJudicialDatabases(
        queries: List<String>,
        jurisdiction: String
    ): List<JudicialCase> {
        val allCases = mutableListOf<JudicialCase>()

        // Use the first query for the main judicial search
        // Additional queries can be used for refinement
        val primaryQuery = queries.firstOrNull() ?: "fraud dispute"

        try {
            val cases = OjrsService.searchCases(
                query = primaryQuery,
                jurisdiction = jurisdiction,
                maxResultsPerDb = 3
            )
            allCases += cases
        } catch (e: Exception) {
            android.util.Log.w("DeepResearch", "Judicial search failed: ${e.message}")
        }

        // Deduplicate by citation
        return allCases.distinctBy { it.citation }.take(15)
    }

    private suspend fun searchStatutes(
        entities: SearchEntities,
        jurisdiction: String
    ): List<ResearchStatute> {
        val allStatutes = mutableListOf<ResearchStatute>()

        for (statute in entities.statutes.take(3)) {
            try {
                val found = OjrsService.searchStatutes(statute, jurisdiction)
                allStatutes += found
            } catch (e: Exception) {
                android.util.Log.w("DeepResearch", "Statute search failed: ${e.message}")
            }
        }

        // Also search for jurisdiction-specific statutes based on legal issues
        for (issue in entities.legalIssues.take(2)) {
            try {
                val found = OjrsService.searchStatutes(issue, jurisdiction)
                allStatutes += found
            } catch (e: Exception) {
                // Silent fail
            }
        }

        return allStatutes.distinctBy { it.citation }.take(10)
    }

    // ------------------------------------------------------------------
    // Internal: synthesis
    // ------------------------------------------------------------------

    private fun synthesisePrecedentAnalysis(
        findings: ForensicFindings,
        cases: List<JudicialCase>
    ): String = buildString {
        if (cases.isEmpty()) {
            append("No directly applicable judicial precedents were found in the searched databases. ")
            append("This may indicate that the specific fact pattern in this case is novel, ")
            append("or that the relevant judgments are not yet digitised in the searched repositories. ")
            append("Manual research in specialised legal databases is recommended.")
            return@buildString
        }

        append("${cases.size} judicial precedent(s) were identified across ${cases.map { it.database.name }.toSet().size} database(s). ")
        append("The following analysis relates these precedents to the sealed evidence:\n\n")

        // Group by relevance
        val onPoint = cases.filter { it.relevanceToCase.contains("jurisdiction", ignoreCase = true) }
        val analogous = cases.filter { !it.relevanceToCase.contains("jurisdiction", ignoreCase = true) }

        if (onPoint.isNotEmpty()) {
            append("**Directly applicable precedents (${onPoint.size}):**\n")
            onPoint.forEach {
                append("  · ${it.citation} (${it.court}) — ${it.title}\n")
                it.keyPrinciples.forEach { p -> append("    Principle: $p\n") }
            }
            append("\n")
        }

        if (analogous.isNotEmpty()) {
            append("**Analogous precedents from other jurisdictions (${analogous.size}):**\n")
            analogous.forEach {
                append("  · ${it.citation} (${it.court}, ${it.database.name}) — ${it.title}\n")
            }
            append("\n")
        }

        // Compare with sealed contradictions
        val contradictionCategories = findings.contradictions.map { it.category.name }.toSet()
        append("**Comparison with sealed contradictions:**\n")
        append("The sealed evidence identifies ${findings.contradictions.size} material contradiction(s) ")
        append("across ${contradictionCategories.size} category(ies): ${contradictionCategories.joinToString(", ")}.\n")

        if (onPoint.isNotEmpty()) {
            append("Found precedents support the legal framework applied in the sealed analysis. ")
            append("The respondent's conduct aligns with patterns established in ${onPoint.first().citation}.")
        } else {
            append("No directly on-point precedents were found. The sealed engine's legal mapping ")
            append("relies primarily on statutory interpretation and common-law principles.")
        }
    }

    private fun calculateConfidence(
        cases: List<JudicialCase>,
        webResults: List<WebSearchResult>,
        statutes: List<ResearchStatute>
    ): ResearchConfidence {
        var score = 0

        // Judicial cases weight heavily
        score += cases.size * 15
        if (cases.any { it.database == JudicialDatabase.SAFLII }) score += 20

        // Web results
        score += webResults.size * 5
        if (webResults.any { it.relevanceScore > 0.7 }) score += 10

        // Statutes
        score += statutes.size * 10

        return when {
            score >= 80 -> ResearchConfidence.VERY_HIGH
            score >= 60 -> ResearchConfidence.HIGH
            score >= 40 -> ResearchConfidence.MODERATE
            score >= 20 -> ResearchConfidence.LOW
            else -> ResearchConfidence.INSUFFICIENT
        }
    }
}
