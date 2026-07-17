package com.verumomnis.forensic.ojrs

import com.verumomnis.forensic.BuildConfig
import com.verumomnis.forensic.model.WebResultCategory
import com.verumomnis.forensic.model.WebSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Web Search Service for general internet research.
 *
 * Searches the open web for information about:
 *   - Company profiles and corporate records
 *   - Statutes and legal commentary
 *   - News reports about fraud/corporate misconduct
 *   - Academic papers on relevant legal topics
 *   - Government sources (CIPC, Companies House, etc.)
 *
 * Uses a pluggable search backend. By default uses DuckDuckGo's HTML
 * interface (no API key required). Can be swapped for Google Custom Search,
 * Bing API, SerpAPI, or any other search provider.
 */
object WebSearchService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Search provider configuration — can be swapped at runtime. */
    var searchProvider: SearchProvider = SearchProvider.DUCKDUCKGO

    enum class SearchProvider {
        DUCKDUCKGO,   // No API key, HTML scraping
        GOOGLE_CSE,   // Requires API key + CSE ID
        BING,         // Requires API key
        SERPAPI       // Requires API key
    }

    /**
     * Search the web for information relevant to a forensic case.
     *
     * @param query Search terms
     * @param caseContext Additional context from the sealed case (company names, statutes)
     * @param maxResults Maximum results to return
     * @return Categorised web search results
     */
    suspend fun search(
        query: String,
        caseContext: CaseSearchContext = CaseSearchContext(),
        maxResults: Int = 10
    ): List<WebSearchResult> = withContext(Dispatchers.IO) {
        val enrichedQuery = enrichQuery(query, caseContext)
        when (searchProvider) {
            SearchProvider.DUCKDUCKGO -> searchDuckDuckGo(enrichedQuery, maxResults)
            SearchProvider.GOOGLE_CSE -> searchGoogleCse(enrichedQuery, maxResults)
            SearchProvider.BING -> searchBing(enrichedQuery, maxResults)
            SearchProvider.SERPAPI -> searchSerpApi(enrichedQuery, maxResults)
        }
    }

    /**
     * Multi-query search: runs several searches in parallel and
     * combines the results. Useful for comprehensive case research.
     */
    suspend fun searchMulti(
        queries: List<String>,
        caseContext: CaseSearchContext = CaseSearchContext(),
        maxResultsPerQuery: Int = 5
    ): List<WebSearchResult> = withContext(Dispatchers.IO) {
        val allResults = mutableListOf<WebSearchResult>()
        for (query in queries) {
            try {
                allResults += search(query, caseContext, maxResultsPerQuery)
            } catch (e: Exception) {
                android.util.Log.w("WebSearch", "Query '$query' failed: ${e.message}")
            }
        }
        // Deduplicate by URL
        allResults.distinctBy { it.url }.take(queries.size * maxResultsPerQuery)
    }

    /**
     * Build a set of targeted search queries from sealed case findings.
     * Extracts key entities and constructs precise search terms.
     */
    fun buildResearchQueries(
        companyNames: List<String>,
        statutes: List<String>,
        legalIssues: List<String>,
        jurisdiction: String
    ): List<String> {
        val queries = mutableListOf<String>()

        // Company-focused queries
        for (company in companyNames.take(3)) {
            queries += "$company fraud South Africa"
            queries += "$company franchise dispute"
            queries += "$company CIPC"
        }

        // Statute-focused queries
        for (statute in statutes.take(3)) {
            val shortName = statute.substringBefore("Act").trim()
            queries += "$shortName Act cases $jurisdiction"
            queries += "$shortName Act penalties"
        }

        // Legal issue queries
        for (issue in legalIssues.take(3)) {
            queries += "$issue $jurisdiction court case"
            queries += "$issue legal precedent"
        }

        // General pattern queries
        queries += "franchise operator goodwill dispute South Africa"
        queries += "Petroleum Products Act section 12B arbitration"

        return queries.distinct()
    }

    // ------------------------------------------------------------------
    // DuckDuckGo (no API key)
    // ------------------------------------------------------------------

    private fun searchDuckDuckGo(query: String, max: Int): List<WebSearchResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://html.duckduckgo.com/html/?q=$encoded"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()

        return parseDuckDuckGoResults(body, max)
    }

    private fun parseDuckDuckGoResults(html: String, max: Int): List<WebSearchResult> {
        val results = mutableListOf<WebSearchResult>()

        // DuckDuckGo HTML results: <a class="result__a" href="...">title</a>
        // <a class="result__snippet">description</a>
        val resultRegex = Regex(
            """<div class="result[^"]*"[^>]*>.*?<a[^>]*class="result__a"[^>]*href="([^"]*)"[^>]*>(.*?)</a>.*?<a[^>]*class="result__snippet"[^>]*>(.*?)</a>.*?<a[^>]*class="result__url"[^>]*href="[^"]*"[^>]*>([^<]*)</a>.*?""",
            RegexOption.DOT_MATCHES_ALL
        )

        resultRegex.findAll(html).take(max).forEach { match ->
            val redirectUrl = match.groupValues[1]
            val title = stripHtml(match.groupValues[2]).trim()
            val snippet = stripHtml(match.groupValues[3]).trim()
            val domain = match.groupValues[4].trim()

            // DuckDuckGo uses redirect URLs — extract the real URL
            val realUrl = extractRealUrl(redirectUrl)

            results += WebSearchResult(
                title = title,
                snippet = snippet,
                url = realUrl,
                domain = domain,
                relevanceScore = estimateRelevance(title, snippet),
                category = classifyResult(domain, title, snippet)
            )
        }

        // Fallback: simpler parsing if the above fails
        if (results.isEmpty()) {
            val simpleRegex = Regex(
                """<a[^>]*class="result__a"[^>]*href="([^"]*)"[^>]*>(.*?)</a>""",
                RegexOption.DOT_MATCHES_ALL
            )
            simpleRegex.findAll(html).take(max).forEach { match ->
                val redirectUrl = match.groupValues[1]
                val title = stripHtml(match.groupValues[2]).trim()
                results += WebSearchResult(
                    title = title,
                    snippet = "",
                    url = extractRealUrl(redirectUrl),
                    domain = "",
                    relevanceScore = 0.5,
                    category = WebResultCategory.OTHER
                )
            }
        }

        return results
    }

    // ------------------------------------------------------------------
    // Google Custom Search (requires API key)
    // ------------------------------------------------------------------

    private fun searchGoogleCse(query: String, max: Int): List<WebSearchResult> {
        // Placeholder: requires CSE API key + search engine ID
        // Implementation would call: https://www.googleapis.com/customsearch/v1
        android.util.Log.w("WebSearch", "Google CSE not configured — returning empty. Set API key to use.")
        return emptyList()
    }

    // ------------------------------------------------------------------
    // Bing Search (requires API key)
    // ------------------------------------------------------------------

    private fun searchBing(query: String, max: Int): List<WebSearchResult> {
        // Placeholder: requires Bing Search API key
        // Implementation would call: https://api.bing.microsoft.com/v7.0/search
        android.util.Log.w("WebSearch", "Bing Search not configured — returning empty. Set API key to use.")
        return emptyList()
    }

    // ------------------------------------------------------------------
    // SerpAPI (requires API key)
    // ------------------------------------------------------------------

    private fun searchSerpApi(query: String, max: Int): List<WebSearchResult> {
        // Placeholder: requires SerpAPI key
        // Implementation would call: https://serpapi.com/search
        android.util.Log.w("WebSearch", "SerpAPI not configured — returning empty. Set API key to use.")
        return emptyList()
    }

    // ------------------------------------------------------------------
    // NewsAPI (real news articles; key via BuildConfig.NEWS_API_KEY)
    // ------------------------------------------------------------------

    /** NewsAPI.org "everything" endpoint used for case-relevant news search. */
    private const val NEWS_API_URL = "https://newsapi.org/v2/everything"

    /** True when a NewsAPI key is configured (BuildConfig default or override). */
    val newsApiAvailable: Boolean
        get() = BuildConfig.NEWS_API_KEY.isNotBlank()

    /**
     * Search NewsAPI for real, published news articles relevant to the case.
     *
     * Honesty contract: this function NEVER fabricates articles. If the key is
     * missing, the network is down, or the API returns an error, it logs
     * "news unavailable" and returns an empty list — callers report the gap
     * honestly instead of embedding invented sources.
     *
     * @param query Keyword query (NewsAPI supports AND/OR/NOT operators)
     * @param maxResults Maximum articles to return
     * @return Real articles mapped to [WebSearchResult] with NEWS_REPORT category
     */
    suspend fun searchNews(
        query: String,
        maxResults: Int = 5
    ): List<WebSearchResult> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.NEWS_API_KEY.trim()
        if (apiKey.isEmpty()) {
            android.util.Log.w("WebSearch", "NewsAPI key not configured — news unavailable.")
            return@withContext emptyList()
        }
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val pageSize = maxResults.coerceIn(1, 100)
            val url = "$NEWS_API_URL?q=$encoded&pageSize=$pageSize&sortBy=relevancy&language=en&apiKey=$apiKey"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "VerumOmnis/5.3.1c (research@verumglobal.foundation)")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    android.util.Log.w("WebSearch", "NewsAPI HTTP ${response.code} — news unavailable.")
                    return@withContext emptyList()
                }
                parseNewsApiArticles(body, maxResults)
            }
        } catch (e: Exception) {
            android.util.Log.w("WebSearch", "NewsAPI search failed (${e.message}) — news unavailable.")
            emptyList()
        }
    }

    /**
     * Parse a NewsAPI JSON response into categorised results.
     * Internal (not private) so unit tests can exercise it directly.
     * Returns an empty list on API error status or malformed JSON.
     */
    internal fun parseNewsApiArticles(json: String, max: Int): List<WebSearchResult> {
        return try {
            val root = Json.parseToJsonElement(json).jsonObject
            val status = root["status"]?.jsonPrimitive?.contentOrNull
            if (status != "ok") {
                val message = root["message"]?.jsonPrimitive?.contentOrNull ?: "unknown error"
                android.util.Log.w("WebSearch", "NewsAPI status=$status ($message) — news unavailable.")
                return emptyList()
            }
            val articles = root["articles"]?.jsonArray ?: return emptyList()
            articles.take(max).mapNotNull { element ->
                val article = element.jsonObject
                val title = article["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val url = article["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                // NewsAPI marks deleted articles as "[Removed]"; never surface them.
                if (title.isEmpty() || url.isEmpty() || title == "[Removed]") return@mapNotNull null
                val description = article["description"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val domain = runCatching { java.net.URI(url).host?.removePrefix("www.") }
                    .getOrNull().orEmpty()
                WebSearchResult(
                    title = title,
                    snippet = description,
                    url = url,
                    domain = domain,
                    relevanceScore = estimateRelevance(title, description),
                    category = WebResultCategory.NEWS_REPORT
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("WebSearch", "NewsAPI response parse failed (${e.message}) — news unavailable.")
            emptyList()
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun enrichQuery(query: String, context: CaseSearchContext): String {
        return buildString {
            append(query)
            if (context.companyName.isNotBlank()) append(" ${context.companyName}")
            if (context.jurisdiction.isNotBlank()) append(" ${context.jurisdiction}")
        }
    }

    private fun extractRealUrl(duckDuckGoUrl: String): String {
        // DuckDuckGo redirects via /l/?kh=-1&uddg=URL
        val uddgRegex = Regex("""uddg=([^&]+)""")
        val match = uddgRegex.find(duckDuckGoUrl)
        return if (match != null) {
            java.net.URLDecoder.decode(match.groupValues[1], "UTF-8")
        } else {
            duckDuckGoUrl
        }
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("""<[^>]+>"""), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace("&nbsp;", " ")
            .trim()
    }

    private fun estimateRelevance(title: String, snippet: String): Double {
        var score = 0.5
        val text = (title + " " + snippet).lowercase()

        // Boost for legal terms
        if (text.contains("court") || text.contains("judgment") || text.contains("case")) score += 0.15
        if (text.contains("fraud") || text.contains("dispute") || text.contains("arbitration")) score += 0.15
        if (text.contains("statute") || text.contains("act") || text.contains("law")) score += 0.1
        if (text.contains("goodwill") || text.contains("franchise")) score += 0.1
        if (text.contains("section") || text.contains("regulation")) score += 0.05

        // Penalty for irrelevant content
        if (text.contains("advertisement") || text.contains("sponsored")) score -= 0.2
        if (text.contains("wikipedia") && !text.contains("case")) score -= 0.1

        return score.coerceIn(0.0, 1.0)
    }

    private fun classifyResult(domain: String, title: String, snippet: String): WebResultCategory {
        val text = (domain + " " + title + " " + snippet).lowercase()

        return when {
            domain.contains("gov.za") || domain.contains("gov.uk") || domain.contains("gov.au") ||
                domain.contains("gc.ca") || domain.contains("europa.eu") -> WebResultCategory.GOVERNMENT_SOURCE

            domain.contains("saflii.org") || domain.contains("courtlistener.com") ||
                domain.contains("bailii.org") || domain.contains("canlii.org") ||
                domain.contains("austlii.edu.au") || domain.contains("eur-lex.europa.eu") ||
                domain.contains("indiankanoon.org") -> WebResultCategory.COURT_CASE

            domain.contains("cipc.co.za") || domain.contains("companieshouse.gov.uk") ||
                domain.contains("asic.gov.au") || domain.contains("sec.gov") ||
                title.contains("company profile") || title.contains("corporate record") -> WebResultCategory.COMPANY_PROFILE

            domain.contains("lexology.com") || domain.contains("jdsupra.com") ||
                domain.contains("law.com") || domain.contains("mondaq.com") ||
                title.contains("legal analysis") || title.contains("legal commentary") -> WebResultCategory.LEGAL_ARTICLE

            domain.contains("ssrn.com") || domain.contains("jstor.org") ||
                domain.contains("arxiv.org") || domain.contains("researchgate.net") -> WebResultCategory.ACADEMIC_PAPER

            domain.contains("news") || domain.contains("bbc.com") || domain.contains("reuters.com") ||
                domain.contains("bloomberg.com") || domain.contains("fin24.com") ||
                domain.contains("timeslive.co.za") || domain.contains("news24.com") -> WebResultCategory.NEWS_REPORT

            text.contains("act") && (text.contains("section") || text.contains("regulation")) -> WebResultCategory.STATUTE_REGULATION

            else -> WebResultCategory.OTHER
        }
    }

    /**
     * Context from the sealed case used to enrich web searches.
     */
    data class CaseSearchContext(
        val companyName: String = "",
        val jurisdiction: String = "",
        val legalIssue: String = "",
        val respondentName: String = ""
    )
}
