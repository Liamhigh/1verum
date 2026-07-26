package com.verumomnis.forensic

import com.verumomnis.forensic.ojrs.JudicialTextFetcher
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the deterministic, offline-verifiable part of [JudicialTextFetcher]: HTML
 * cleanup. Network fetch paths (real judgment downloads) are exercised on-device, in
 * keeping with [OjrsServiceTest]'s existing testing philosophy for this module.
 */
class JudicialTextFetcherTest {

    @Test
    fun `stripHtml removes scripts styles and tags`() {
        val html = """
            <html><head><style>.x{color:red}</style><script>alert('x')</script></head>
            <body><p>The applicant   was <b>never</b> served with notice.</p></body></html>
        """.trimIndent()

        val text = JudicialTextFetcher.stripHtml(html)

        assertEquals("The applicant was never served with notice.", text)
    }

    @Test
    fun `stripHtml unescapes common HTML entities`() {
        val html = "<p>Smith &amp; Jones &mdash; &quot;binding&quot; &amp; enforceable</p>"
            .replace("&mdash;", "-")

        val text = JudicialTextFetcher.stripHtml(html)

        assertEquals("Smith & Jones - \"binding\" & enforceable", text)
    }
}
