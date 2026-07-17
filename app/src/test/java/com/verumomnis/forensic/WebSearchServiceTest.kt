package com.verumomnis.forensic

import com.verumomnis.forensic.model.WebResultCategory
import com.verumomnis.forensic.ojrs.WebSearchService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the NewsAPI leg of [WebSearchService].
 *
 * Honesty contract under test: the parser never invents articles.
 * API errors, malformed payloads, missing fields and "[Removed]" entries
 * all yield an honest empty (or reduced) list — "news unavailable" —
 * instead of fabricated sources.
 */
class WebSearchServiceTest {

    private val okResponse = """
        {
          "status": "ok",
          "totalResults": 2,
          "articles": [
            {
              "source": { "id": "news24", "name": "News24" },
              "author": "Reporter",
              "title": "Fuel retailer faces franchise fraud lawsuit",
              "description": "A major fuel retailer is accused of stripping operators of goodwill.",
              "url": "https://www.news24.com/fin24/companies/fuel-retailer-franchise-fraud-20260716",
              "urlToImage": "https://www.news24.com/img.jpg",
              "publishedAt": "2026-07-16T10:00:00Z",
              "content": "A major fuel retailer is accused..."
            },
            {
              "source": { "id": null, "name": "Reuters" },
              "author": null,
              "title": "Court rules on petroleum dispute arbitration",
              "description": "The court held that section 12B referrals are mandatory.",
              "url": "https://www.reuters.com/legal/petroleum-dispute-2026-07-15/",
              "urlToImage": null,
              "publishedAt": "2026-07-15T08:30:00Z",
              "content": "The court held..."
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses real articles as NEWS_REPORT results`() {
        val results = WebSearchService.parseNewsApiArticles(okResponse, max = 5)

        assertEquals(2, results.size)

        val first = results[0]
        assertEquals("Fuel retailer faces franchise fraud lawsuit", first.title)
        assertEquals(
            "https://www.news24.com/fin24/companies/fuel-retailer-franchise-fraud-20260716",
            first.url
        )
        assertEquals("news24.com", first.domain)
        assertEquals(WebResultCategory.NEWS_REPORT, first.category)
        assertTrue(first.snippet.contains("goodwill"))

        val second = results[1]
        assertEquals("reuters.com", second.domain)
        assertEquals(WebResultCategory.NEWS_REPORT, second.category)
    }

    @Test
    fun `api error status yields empty list, never fabricated articles`() {
        val errorResponse =
            """{"status":"error","code":"apiKeyInvalid","message":"Your API key is invalid."}"""
        val results = WebSearchService.parseNewsApiArticles(errorResponse, max = 5)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `malformed or empty json yields empty list`() {
        assertTrue(WebSearchService.parseNewsApiArticles("not json at all", 5).isEmpty())
        assertTrue(WebSearchService.parseNewsApiArticles("", 5).isEmpty())
    }

    @Test
    fun `removed or incomplete articles are dropped, not embellished`() {
        val response = """
            {
              "status": "ok",
              "totalResults": 3,
              "articles": [
                { "source": {"name": "X"}, "title": "[Removed]", "url": "https://example.org/gone" },
                { "source": {"name": "Y"}, "title": "", "url": "https://example.org/no-title" },
                { "source": {"name": "Z"}, "title": "Real story", "description": "fraud case", "url": "https://example.org/real" }
              ]
            }
        """.trimIndent()
        val results = WebSearchService.parseNewsApiArticles(response, max = 5)

        assertEquals(1, results.size)
        assertEquals("Real story", results[0].title)
        assertEquals("example.org", results[0].domain)
        assertEquals(WebResultCategory.NEWS_REPORT, results[0].category)
    }
}
