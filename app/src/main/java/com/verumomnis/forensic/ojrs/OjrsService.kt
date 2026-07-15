package com.verumomnis.forensic.ojrs

import com.verumomnis.forensic.engine.EvidenceDocument
import com.verumomnis.forensic.model.Contradiction
import com.verumomnis.forensic.model.OjrsResult
import com.verumomnis.forensic.model.OjrsSearchRequest

/**
 * Online Judicial Retrieval System service.
 *
 * When enabled, B7 uses this service to search public court databases for cases
 * that may match the parties, legal categories and keywords of the sealed evidence.
 * When disabled (the default) all calls return empty results, keeping scans
 * deterministic and offline-safe.
 */
class OjrsService(
    val enabled: Boolean = false,
    private val client: OjrsClient = DisabledOjrsClient
) {

    /** Search across the configured client for every contradiction. */
    suspend fun searchForFindings(contradictions: List<Contradiction>): List<OjrsResult> {
        if (!enabled || client.source == "DISABLED") return emptyList()
        val requests = contradictions.flatMap { requestsForContradiction(it) }
        return requests.flatMap { client.search(it) }
    }

    /** Build one or more search requests from a single contradiction. */
    fun requestsForContradiction(contradiction: Contradiction): List<OjrsSearchRequest> {
        val parties = listOfNotNull(
            contradiction.respondent,
            contradiction.anchoredPerson?.name
        ).filter { it.isNotBlank() }

        val keywords = buildList {
            add(contradiction.category.name.replace("_", " "))
            add(contradiction.legalSignificance)
            contradiction.applicableLaw.forEach { add(it) }
        }

        val query = (parties + keywords).distinct().joinToString(" ")
        return listOf(
            OjrsSearchRequest(
                query = query,
                jurisdiction = "US",
                contradictionCategory = contradiction.category,
                parties = parties
            )
        )
    }

    /** Extract search-worthy keywords from a document (parties + legal nouns). */
    fun extractKeywords(document: EvidenceDocument): List<String> {
        val text = document.text
        val legalNouns = listOf(
            "fraud", "breach", "contract", "theft", "perjury", "coercion",
            "racketeering", "money laundering", "forgery", "defamation", "trespass"
        )
        return legalNouns.filter { text.contains(it, ignoreCase = true) }
    }
}
