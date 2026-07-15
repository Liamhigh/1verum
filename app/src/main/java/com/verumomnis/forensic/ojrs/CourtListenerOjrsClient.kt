package com.verumomnis.forensic.ojrs

import com.verumomnis.forensic.model.Confidence
import com.verumomnis.forensic.model.OjrsResult
import com.verumomnis.forensic.model.OjrsSearchRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Free US case-law client backed by the CourtListener REST API.
 *
 * https://www.courtlistener.com/api/rest/v3/search/?q={query}
 *
 * Results are returned as [OjrsResult] objects. No API key is required for
 * read-only search, but network access and a reachable endpoint are required.
 */
class CourtListenerOjrsClient(
    private val client: OkHttpClient = OkHttpClient()
) : OjrsClient {

    override val source = "CourtListener"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(request: OjrsSearchRequest): List<OjrsResult> {
        val url = "https://www.courtlistener.com/api/rest/v3/search/".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("q", request.query)
            ?.addQueryParameter("type", "o") // opinions
            ?.build()
            ?: return emptyList()

        val httpRequest = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()

        return try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val envelope = json.decodeFromString<SearchEnvelope>(body)
                envelope.results.map {
                    OjrsResult(
                        source = source,
                        title = it.caseName,
                        citation = it.citation?.firstOrNull() ?: "",
                        date = it.dateFiled ?: "",
                        url = it.absoluteUrl ?: "",
                        snippet = it.snippet ?: "",
                        confidence = if (it.citation != null) Confidence.MODERATE else Confidence.LOW
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Serializable
    private data class SearchEnvelope(
        val results: List<OpinionResult> = emptyList()
    )

    @Serializable
    private data class OpinionResult(
        @SerialName("caseName") val caseName: String = "",
        @SerialName("citation") val citation: List<String>? = null,
        @SerialName("dateFiled") val dateFiled: String? = null,
        @SerialName("absolute_url") val absoluteUrl: String? = null,
        @SerialName("snippet") val snippet: String? = null
    )
}
