package com.verumomnis.forensic.model

import kotlinx.serialization.Serializable

/** A single result from an Online Judicial Retrieval System (OJRS) query. */
@Serializable
data class OjrsResult(
    val source: String,
    val title: String,
    val citation: String = "",
    val date: String = "",
    val url: String = "",
    val snippet: String = "",
    val confidence: Confidence = Confidence.INSUFFICIENT
)

/** Search request built from a contradiction or evidence bundle. */
@Serializable
data class OjrsSearchRequest(
    val query: String,
    val jurisdiction: String,
    val contradictionCategory: ContradictionCategory? = null,
    val parties: List<String> = emptyList()
)
