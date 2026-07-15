package com.verumomnis.forensic

import com.verumomnis.forensic.model.Contradiction
import com.verumomnis.forensic.model.ContradictionCategory
import com.verumomnis.forensic.model.ContradictionClaim
import com.verumomnis.forensic.model.ContradictionType
import com.verumomnis.forensic.model.Confidence
import com.verumomnis.forensic.model.OjrsResult
import com.verumomnis.forensic.model.OjrsSearchRequest
import com.verumomnis.forensic.model.Severity
import com.verumomnis.forensic.ojrs.OjrsClient
import com.verumomnis.forensic.ojrs.OjrsService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OjrsServiceTest {

    private val fakeClient = object : OjrsClient {
        override val source = "FAKE"
        override suspend fun search(request: OjrsSearchRequest): List<OjrsResult> = listOf(
            OjrsResult(source = source, title = "Test case for ${request.query}")
        )
    }

    private fun contradiction(respondent: String = "Acme Corp") = Contradiction(
        contradictionId = "C-1",
        brainSource = "B1",
        category = ContradictionCategory.CONTRACT_VALIDITY,
        type = ContradictionType.DIRECT_NEGATION,
        respondent = respondent,
        claimA = ContradictionClaim("A", "doc1", "doc1", sha512 = "a"),
        claimB = ContradictionClaim("B", "doc1", "doc1", sha512 = "b"),
        severity = Severity.HIGH,
        legalSignificance = "fraud",
        confidence = Confidence.HIGH,
        timestamp = "2024-01-01T00:00:00Z"
    )

    @Test
    fun `disabled service returns empty results`() = runBlocking {
        val service = OjrsService(enabled = false, client = fakeClient)
        val results = service.searchForFindings(listOf(contradiction()))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `enabled service queries client`() = runBlocking {
        val service = OjrsService(enabled = true, client = fakeClient)
        val results = service.searchForFindings(listOf(contradiction()))
        assertEquals(1, results.size)
        assertEquals("FAKE", results[0].source)
    }

    @Test
    fun `request includes respondent and category`() {
        val service = OjrsService(enabled = true, client = fakeClient)
        val requests = service.requestsForContradiction(contradiction())
        assertEquals(1, requests.size)
        val req = requests[0]
        assertTrue(req.query.contains("Acme Corp"))
        assertTrue(req.query.contains("CONTRACT VALIDITY"))
        assertEquals(Confidence.INSUFFICIENT, OjrsResult("x", "").confidence)
    }
}
