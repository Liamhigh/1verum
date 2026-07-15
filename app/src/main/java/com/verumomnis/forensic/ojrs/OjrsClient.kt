package com.verumomnis.forensic.ojrs

import com.verumomnis.forensic.model.OjrsResult
import com.verumomnis.forensic.model.OjrsSearchRequest

/**
 * Client interface for Online Judicial Retrieval Systems (OJRS).
 *
 * Implementations may query SAFLII, PACER, BAILII, CourtListener, etc.
 * The default [DisabledOjrsClient] returns empty results so scans remain
 * deterministic and offline-safe when OJRS is disabled.
 */
interface OjrsClient {
    /** Identifier shown in reports, e.g. "SAFLII", "CourtListener". */
    val source: String

    /** Perform the search. Must be safe to call from a background thread/coroutine. */
    suspend fun search(request: OjrsSearchRequest): List<OjrsResult>
}

/** No-op client used when OJRS is disabled or no network/API key is available. */
object DisabledOjrsClient : OjrsClient {
    override val source = "DISABLED"
    override suspend fun search(request: OjrsSearchRequest): List<OjrsResult> = emptyList()
}
