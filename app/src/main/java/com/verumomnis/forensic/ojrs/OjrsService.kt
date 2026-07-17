package com.verumomnis.forensic.ojrs

import com.verumomnis.forensic.model.JudicialCase
import com.verumomnis.forensic.model.JudicialDatabase
import com.verumomnis.forensic.model.ResearchStatute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Online Judicial Retrieval Service (OJRS).
 *
 * Searches multiple free legal databases for court cases and statutes
 * relevant to the sealed evidence. Currently supports:
 *   - SAFLII (South Africa)
 *   - CourtListener (United States, free API)
 *   - BAILII (UK / Ireland)
 *   - CanLII (Canada)
 *   - AustLII (Australia)
 *   - EUR-Lex (European Union)
 *
 * All searches use public/free endpoints. No API keys required for
 * basic search functionality.
 */
object OjrsService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Search all supported judicial databases for cases matching the query.
     *
     * @param query Search terms (company name, statute, legal issue)
     * @param jurisdiction Optional filter: "ZA", "US", "UK", "CA", "AU", "EU"
     * @param maxResultsPerDb Maximum results to return per database
     * @return Combined list of judicial cases from all databases
     */
    suspend fun searchCases(
        query: String,
        jurisdiction: String? = null,
        maxResultsPerDb: Int = 5
    ): List<JudicialCase> = withContext(Dispatchers.IO) {
        val results = mutableListOf<JudicialCase>()

        // Search each database based on jurisdiction hint
        val dbs = selectDatabases(jurisdiction)

        for (db in dbs) {
            try {
                val cases = when (db) {
                    JudicialDatabase.SAFLII -> searchSaflii(query, maxResultsPerDb)
                    JudicialDatabase.COURTLISTENER -> searchCourtListener(query, maxResultsPerDb)
                    JudicialDatabase.BAILII -> searchBailii(query, maxResultsPerDb)
                    JudicialDatabase.CANLII -> searchCanlii(query, maxResultsPerDb)
                    JudicialDatabase.AUSTLII -> searchAustlii(query, maxResultsPerDb)
                    JudicialDatabase.EUR_LEX -> searchEurLex(query, maxResultsPerDb)
                    else -> emptyList()
                }
                results += cases
            } catch (e: Exception) {
                // Log but don't fail — other databases may succeed
                android.util.Log.w("OJRS", "${db.name} search failed: ${e.message}")
            }
        }

        // Sort by relevance (placeholder: by court prestige)
        results.sortedByDescending { it.court.contains("Supreme", ignoreCase = true) ||
            it.court.contains("Constitutional", ignoreCase = true) ||
            it.court.contains("High Court", ignoreCase = true) }
    }

    /**
     * Search for statutes and regulations applicable to a given legal issue
     * in a specific jurisdiction.
     */
    suspend fun searchStatutes(
        legalIssue: String,
        jurisdiction: String
    ): List<ResearchStatute> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ResearchStatute>()
        try {
            when (jurisdiction.uppercase()) {
                "ZA", "ZA-KZN", "ZA-GP" -> results += searchSafliiStatutes(legalIssue)
                "US" -> results += searchCourtListenerStatutes(legalIssue)
                "UK" -> results += searchBailiiStatutes(legalIssue)
                "CA" -> results += searchCanliiStatutes(legalIssue)
                "AU" -> results += searchAustliiStatutes(legalIssue)
                "EU" -> results += searchEurLexStatutes(legalIssue)
            }
        } catch (e: Exception) {
            android.util.Log.w("OJRS", "Statute search failed: ${e.message}")
        }
        results
    }

    // ------------------------------------------------------------------
    // SAFLII (South Africa)
    // ------------------------------------------------------------------

    private fun searchSaflii(query: String, max: Int): List<JudicialCase> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.saflii.org/cgi-bin/sinus/search.cgi?query=$encoded&mask_path=/za/cases"
        val request = Request.Builder().url(url).header("User-Agent", "VerumOmnis/5.3.1c").build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()

        return parseSafliiResults(body, max)
    }

    private fun parseSafliiResults(html: String, max: Int): List<JudicialCase> {
        val cases = mutableListOf<JudicialCase>()
        // SAFLII results are in HTML with <dt class="result_title"> patterns
        val titleRegex = Regex("""<dt class="result_title">\s*<a[^>]*href="([^"]*)">\s*([^<]+)</a>""")
        val courtRegex = Regex("""<dd class="result_court">\s*([^<]+)""")
        val dateRegex = Regex("""(\d{1,2}\s+(January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{4})""")

        val titles = titleRegex.findAll(html).toList()
        val courts = courtRegex.findAll(html).toList()

        for (i in titles.indices.take(max)) {
            val href = titles[i].groupValues[1]
            val title = titles[i].groupValues[2].trim()
            val court = courts.getOrNull(i)?.groupValues?.get(1)?.trim() ?: "South African Court"
            val dateMatch = dateRegex.find(html.substring(titles[i].range.first, (titles.getOrNull(i + 1)?.range?.first ?: html.length)))
            val date = dateMatch?.groupValues?.get(1) ?: ""

            cases += JudicialCase(
                citation = extractCitation(title),
                title = title,
                court = court,
                judgmentDate = date,
                summary = "Retrieved from SAFLII. Full judgment available at source URL.",
                relevanceToCase = "Precedent from South African jurisdiction.",
                sourceUrl = if (href.startsWith("http")) href else "https://www.saflii.org$href",
                database = JudicialDatabase.SAFLII
            )
        }
        return cases
    }

    private fun searchSafliiStatutes(query: String): List<ResearchStatute> {
        // SAFLII statute search via legislation path
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.saflii.org/cgi-bin/sinus/search.cgi?query=$encoded&mask_path=/za/legis"
        val request = Request.Builder().url(url).header("User-Agent", "VerumOmnis/5.3.1c").build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()

        val statutes = mutableListOf<ResearchStatute>()
        val regex = Regex("""<dt class="result_title">\s*<a[^>]*href="([^"]*)">\s*([^<]+)</a>""")
        regex.findAll(body).take(5).forEach { match ->
            val href = match.groupValues[1]
            val title = match.groupValues[2].trim()
            statutes += ResearchStatute(
                citation = title,
                title = title,
                sections = emptyList(),
                jurisdiction = "South Africa",
                application = "Retrieved from SAFLII legislation database.",
                sourceUrl = if (href.startsWith("http")) href else "https://www.saflii.org$href"
            )
        }
        return statutes
    }

    // ------------------------------------------------------------------
    // CourtListener (United States — free REST API)
    // ------------------------------------------------------------------

    private fun searchCourtListener(query: String, max: Int): List<JudicialCase> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.courtlistener.com/api/rest/v3/search/?q=$encoded&type=o&court=scotus%20cafc%20ca1%20ca2%20ca3%20ca4%20ca5%20ca6%20ca7%20ca8%20ca9%20ca10%20ca11&order_by=score%20desc"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "VerumOmnis/5.3.1c (research@verumglobal.foundation)")
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()

        return parseCourtListenerResults(body, max)
    }

    internal fun parseCourtListenerResults(json: String, max: Int): List<JudicialCase> {
        val cases = mutableListOf<JudicialCase>()
        // Simple JSON parsing of CourtListener response
        // Format: {"count": N, "results": [{"caseName": "...", "court": "...", "dateFiled": "...", "absolute_url": "..."}]}
        val caseNameRegex = Regex(""""caseName"\s*:\s*"([^"]*)"""")
        val courtRegex = Regex(""""court"\s*:\s*"([^"]*)"""")
        val dateRegex = Regex(""""dateFiled"\s*:\s*"([^"]*)""")
        val urlRegex = Regex(""""absolute_url"\s*:\s*"([^"]*)"""")

        val names = caseNameRegex.findAll(json).toList()
        val courts = courtRegex.findAll(json).toList()
        val dates = dateRegex.findAll(json).toList()
        val urls = urlRegex.findAll(json).toList()

        for (i in names.indices.take(max)) {
            cases += JudicialCase(
                citation = names[i].groupValues[1],
                title = names[i].groupValues[1],
                court = courts.getOrNull(i)?.groupValues?.get(1) ?: "US Federal Court",
                judgmentDate = dates.getOrNull(i)?.groupValues?.get(1) ?: "",
                summary = "Retrieved from CourtListener (Free Law Project).",
                relevanceToCase = "US federal precedent for comparative analysis.",
                sourceUrl = "https://www.courtlistener.com${urls.getOrNull(i)?.groupValues?.get(1) ?: ""}",
                database = JudicialDatabase.COURTLISTENER
            )
        }
        return cases
    }

    private fun searchCourtListenerStatutes(query: String): List<ResearchStatute> {
        // CourtListener focuses on case law; statutes via search
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.courtlistener.com/api/rest/v3/search/?q=$encoded&type=s"
        val request = Request.Builder().url(url).header("User-Agent", "VerumOmnis/5.3.1c").build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()

        val statutes = mutableListOf<ResearchStatute>()
        val titleRegex = Regex(""""title"\s*:\s*"([^"]*)"""")
        val urlRegex = Regex(""""absolute_url"\s*:\s*"([^"]*)"""")
        val titles = titleRegex.findAll(body).toList()
        val urls = urlRegex.findAll(body).toList()

        for (i in titles.indices.take(3)) {
            statutes += ResearchStatute(
                citation = titles[i].groupValues[1],
                title = titles[i].groupValues[1],
                sections = emptyList(),
                jurisdiction = "United States",
                application = "Retrieved from CourtListener.",
                sourceUrl = "https://www.courtlistener.com${urls.getOrNull(i)?.groupValues?.get(1) ?: ""}"
            )
        }
        return statutes
    }

    // ------------------------------------------------------------------
    // BAILII (UK / Ireland)
    // ------------------------------------------------------------------

    private fun searchBailii(query: String, max: Int): List<JudicialCase> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.bailii.org/cgi-bin/lucy_search_1.cgi?method=boolean&query=$encoded&results=max&mask_world="
        val request = Request.Builder().url(url).header("User-Agent", "VerumOmnis/5.3.1c").build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()
        return parseBailiiResults(body, max)
    }

    private fun parseBailiiResults(html: String, max: Int): List<JudicialCase> {
        val cases = mutableListOf<JudicialCase>()
        val regex = Regex("""<a href="(/[^"]*cases/[^"]*)">\s*([^<]+)</a>""")
        regex.findAll(html).take(max).forEach { match ->
            val href = match.groupValues[1]
            val title = match.groupValues[2].trim()
            cases += JudicialCase(
                citation = extractCitation(title),
                title = title,
                court = extractCourtFromTitle(title),
                judgmentDate = "",
                summary = "Retrieved from BAILII (UK/Ireland).",
                relevanceToCase = "Common law precedent from UK/Irish jurisdiction.",
                sourceUrl = "https://www.bailii.org$href",
                database = JudicialDatabase.BAILII
            )
        }
        return cases
    }

    private fun searchBailiiStatutes(query: String): List<ResearchStatute> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.bailii.org/cgi-bin/lucy_search_1.cgi?method=boolean&query=$encoded&results=max&mask_uk="
        val request = Request.Builder().url(url).header("User-Agent", "VerumOmnis/5.3.1c").build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()

        val statutes = mutableListOf<ResearchStatute>()
        val regex = Regex("""<a href="(/[^"]*legis/[^"]*)">\s*([^<]+)</a>""")
        regex.findAll(body).take(3).forEach { match ->
            statutes += ResearchStatute(
                citation = match.groupValues[2].trim(),
                title = match.groupValues[2].trim(),
                sections = emptyList(),
                jurisdiction = "United Kingdom",
                application = "Retrieved from BAILII.",
                sourceUrl = "https://www.bailii.org${match.groupValues[1]}"
            )
        }
        return statutes
    }

    // ------------------------------------------------------------------
    // CanLII (Canada)
    // ------------------------------------------------------------------

    private fun searchCanlii(query: String, max: Int): List<JudicialCase> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.canlii.org/en/#search/type=decision&text=$encoded"
        val request = Request.Builder().url(url).header("User-Agent", "VerumOmnis/5.3.1c").build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()
        return parseCanliiResults(body, max)
    }

    private fun parseCanliiResults(html: String, max: Int): List<JudicialCase> {
        val cases = mutableListOf<JudicialCase>()
        val regex = Regex("""<a[^>]*href="(/en/[^"]*\d{4}[^"]*)"[^>]*>\s*([^<]+)</a>""")
        regex.findAll(html).take(max).forEach { match ->
            cases += JudicialCase(
                citation = match.groupValues[2].trim(),
                title = match.groupValues[2].trim(),
                court = "Canadian Court",
                judgmentDate = "",
                summary = "Retrieved from CanLII.",
                relevanceToCase = "Canadian precedent for comparative analysis.",
                sourceUrl = "https://www.canlii.org${match.groupValues[1]}",
                database = JudicialDatabase.CANLII
            )
        }
        return cases
    }

    private fun searchCanliiStatutes(query: String): List<ResearchStatute> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.canlii.org/en/#search/type=legislation&text=$encoded"
        val request = Request.Builder().url(url).header("User-Agent", "VerumOmnis/5.3.1c").build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()

        val statutes = mutableListOf<ResearchStatute>()
        val regex = Regex("""<a[^>]*href="(/en/[^"]*legis/[^"]*)"[^>]*>\s*([^<]+)</a>""")
        regex.findAll(body).take(3).forEach { match ->
            statutes += ResearchStatute(
                citation = match.groupValues[2].trim(),
                title = match.groupValues[2].trim(),
                sections = emptyList(),
                jurisdiction = "Canada",
                application = "Retrieved from CanLII.",
                sourceUrl = "https://www.canlii.org${match.groupValues[1]}"
            )
        }
        return statutes
    }

    // ------------------------------------------------------------------
    // AustLII (Australia)
    // ------------------------------------------------------------------

    private fun searchAustlii(query: String, max: Int): List<JudicialCase> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "http://www.austlii.edu.au/cgi-bin/sinosrch.cgi?method=boolean&query=$encoded&meta=%2Fau&mask_path="
        val request = Request.Builder().url(url).header("User-Agent", "VerumOmnis/5.3.1c").build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()
        return parseAustliiResults(body, max)
    }

    private fun parseAustliiResults(html: String, max: Int): List<JudicialCase> {
        val cases = mutableListOf<JudicialCase>()
        val regex = Regex("""<a href="(/[^"]*cases/[^"]*)">\s*([^<]+)</a>""")
        regex.findAll(html).take(max).forEach { match ->
            cases += JudicialCase(
                citation = match.groupValues[2].trim(),
                title = match.groupValues[2].trim(),
                court = "Australian Court",
                judgmentDate = "",
                summary = "Retrieved from AustLII.",
                relevanceToCase = "Australian precedent for comparative analysis.",
                sourceUrl = "http://www.austlii.edu.au${match.groupValues[1]}",
                database = JudicialDatabase.AUSTLII
            )
        }
        return cases
    }

    private fun searchAustliiStatutes(query: String): List<ResearchStatute> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "http://www.austlii.edu.au/cgi-bin/sinosrch.cgi?method=boolean&query=$encoded&meta=%2Fau&mask_path=legis"
        val request = Request.Builder().url(url).header("User-Agent", "VerumOmnis/5.3.1c").build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()

        val statutes = mutableListOf<ResearchStatute>()
        val regex = Regex("""<a href="(/[^"]*legis/[^"]*)">\s*([^<]+)</a>""")
        regex.findAll(body).take(3).forEach { match ->
            statutes += ResearchStatute(
                citation = match.groupValues[2].trim(),
                title = match.groupValues[2].trim(),
                sections = emptyList(),
                jurisdiction = "Australia",
                application = "Retrieved from AustLII.",
                sourceUrl = "http://www.austlii.edu.au${match.groupValues[1]}"
            )
        }
        return statutes
    }

    // ------------------------------------------------------------------
    // EUR-Lex (European Union)
    // ------------------------------------------------------------------

    private fun searchEurLex(query: String, max: Int): List<JudicialCase> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://eur-lex.europa.eu/search.html?textScope0=ti te to&page=1&scope=EURLEX&type=quick&sortBy=RELEVANCE&DD_YEAR=&DD_LANGUAGE=en&locale=en&qid=1620000000000&name=browse-by%3Adefault&text0=$encoded"
        val request = Request.Builder().url(url).header("User-Agent", "VerumOmnis/5.3.1c").build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()

        val cases = mutableListOf<JudicialCase>()
        val regex = Regex("""<a[^>]*href="(/legal-content/EN/TXT/\?uri=[^"]*)"[^>]*>\s*([^<]+)</a>""")
        regex.findAll(body).take(max).forEach { match ->
            cases += JudicialCase(
                citation = match.groupValues[2].trim(),
                title = match.groupValues[2].trim(),
                court = "European Union Court",
                judgmentDate = "",
                summary = "Retrieved from EUR-Lex.",
                relevanceToCase = "EU legal precedent for comparative analysis.",
                sourceUrl = "https://eur-lex.europa.eu${match.groupValues[1]}",
                database = JudicialDatabase.EUR_LEX
            )
        }
        return cases
    }

    private fun searchEurLexStatutes(query: String): List<ResearchStatute> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://eur-lex.europa.eu/search.html?textScope0=ti&page=1&scope=EURLEX&type=quick&sortBy=RELEVANCE&DD_YEAR=&DD_LANGUAGE=en&locale=en&qid=1620000000000&name=browse-by%3Adefault&text0=$encoded"
        val request = Request.Builder().url(url).header("User-Agent", "VerumOmnis/5.3.1c").build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()

        val statutes = mutableListOf<ResearchStatute>()
        val regex = Regex("""<a[^>]*href="(/legal-content/EN/TXT/\?uri=[^"]*)"[^>]*>\s*([^<]+)</a>""")
        regex.findAll(body).take(3).forEach { match ->
            statutes += ResearchStatute(
                citation = match.groupValues[2].trim(),
                title = match.groupValues[2].trim(),
                sections = emptyList(),
                jurisdiction = "European Union",
                application = "Retrieved from EUR-Lex.",
                sourceUrl = "https://eur-lex.europa.eu${match.groupValues[1]}"
            )
        }
        return statutes
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    internal fun selectDatabases(jurisdiction: String?): List<JudicialDatabase> {
        if (jurisdiction == null) {
            // Search all databases when no jurisdiction specified
            return listOf(
                JudicialDatabase.SAFLII,
                JudicialDatabase.COURTLISTENER,
                JudicialDatabase.BAILII,
                JudicialDatabase.CANLII,
                JudicialDatabase.AUSTLII,
                JudicialDatabase.EUR_LEX
            )
        }
        return when (jurisdiction.uppercase()) {
            "ZA", "ZA-KZN", "ZA-GP" -> listOf(JudicialDatabase.SAFLII, JudicialDatabase.COURTLISTENER, JudicialDatabase.BAILII)
            "US" -> listOf(JudicialDatabase.COURTLISTENER, JudicialDatabase.BAILII)
            "UK" -> listOf(JudicialDatabase.BAILII, JudicialDatabase.COURTLISTENER)
            "CA" -> listOf(JudicialDatabase.CANLII, JudicialDatabase.COURTLISTENER)
            "AU" -> listOf(JudicialDatabase.AUSTLII, JudicialDatabase.COURTLISTENER)
            "EU" -> listOf(JudicialDatabase.EUR_LEX, JudicialDatabase.COURTLISTENER)
            "IN" -> listOf(JudicialDatabase.INDIAN_KANOON, JudicialDatabase.COURTLISTENER)
            else -> listOf(JudicialDatabase.SAFLII, JudicialDatabase.COURTLISTENER, JudicialDatabase.BAILII)
        }
    }

    internal fun extractCitation(title: String): String {
        // Try to extract a citation pattern like [2024] ZACC 12
        val citationRegex = Regex("""\[\d{4}\]\s*\w+\s*\d+""")
        return citationRegex.find(title)?.value ?: title.take(60)
    }

    internal fun extractCourtFromTitle(title: String): String {
        return when {
            title.contains("Supreme Court", ignoreCase = true) -> "Supreme Court"
            title.contains("High Court", ignoreCase = true) -> "High Court"
            title.contains("Constitutional Court", ignoreCase = true) -> "Constitutional Court"
            title.contains("Court of Appeal", ignoreCase = true) -> "Court of Appeal"
            else -> "Unknown Court"
        }
    }
}
