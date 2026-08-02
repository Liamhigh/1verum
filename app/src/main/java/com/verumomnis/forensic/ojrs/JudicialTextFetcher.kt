package com.verumomnis.forensic.ojrs

import com.verumomnis.forensic.model.JudicialCase
import com.verumomnis.forensic.model.JudicialDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches the full judgment/testimony text behind a [JudicialCase] search result, so the
 * contradiction engine (see VerumContradictionEngine.processFromTaggedInputs) has real
 * propositions to pair against sealed evidence instead of [OjrsService]'s placeholder
 * summary strings ("Retrieved from SAFLII. Full judgment available at source URL.").
 *
 * User-triggered only — never called automatically per scan — and low-volume, in keeping
 * with each source's terms: CourtListener has an official opinion-text REST endpoint;
 * SAFLII/BAILII/AustLII/EUR-Lex are fetched as a single follow-up GET on the public
 * judgment page. CanLII is intentionally skipped: their terms prohibit scraping full
 * judgment text outside their official (key-gated) API, which this app does not integrate.
 */
object JudicialTextFetcher {

    private const val MAX_CHARS = 40_000

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch full text for a judicial case. Returns null when the source database isn't
     * supported for full-text retrieval (CanLII, Indian Kanoon, unknown) or the fetch
     * fails — callers must treat null as "skip this case", never as an error to surface.
     */
    suspend fun fetchOpinionText(case: JudicialCase): String? = withContext(Dispatchers.IO) {
        try {
            when (case.database) {
                JudicialDatabase.COURTLISTENER -> fetchCourtListenerOpinion(case.sourceUrl)
                JudicialDatabase.SAFLII, JudicialDatabase.BAILII,
                JudicialDatabase.AUSTLII, JudicialDatabase.EUR_LEX -> fetchAndStripHtml(case.sourceUrl)
                else -> null
            }
        } catch (e: Exception) {
            android.util.Log.w("JudicialTextFetcher", "Failed to fetch text for ${case.citation}: ${e.message}")
            null
        }
    }

    private fun fetchCourtListenerOpinion(sourceUrl: String): String? {
        val id = Regex("""/opinion/(\d+)/""").find(sourceUrl)?.groupValues?.get(1) ?: return null
        val url = "https://www.courtlistener.com/api/rest/v3/opinions/$id/"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "VerumOmnis/5.3.1c (research@verumglobal.foundation)")
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return null
        val text = Regex(""""plain_text"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(body)?.groupValues?.get(1)
            ?: return null
        return unescapeJsonString(text).take(MAX_CHARS)
    }

    private fun fetchAndStripHtml(url: String): String? {
        if (url.isBlank()) return null
        val request = Request.Builder().url(url).header("User-Agent", "VerumOmnis/5.3.1c").build()
        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: return null
        return stripHtml(html).take(MAX_CHARS)
    }

    /** Strip scripts/styles/tags and collapse whitespace. Package-visible for unit tests. */
    internal fun stripHtml(html: String): String {
        val noScripts = html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        val noTags = noScripts.replace(Regex("(?s)<[^>]+>"), " ")
        val unescaped = noTags
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return unescaped.replace(Regex("\\s+"), " ").trim()
    }

    private fun unescapeJsonString(s: String): String =
        s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
}
