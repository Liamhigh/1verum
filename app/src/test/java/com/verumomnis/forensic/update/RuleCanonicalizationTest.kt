package com.verumomnis.forensic.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Canonical-JSON tests (rule-format.md v1). The canonical bytes are exactly
 * what the rules service signs, so they must match the server's JavaScript
 * canonicalJson byte-for-byte or signature verification fails.
 */
class RuleCanonicalizationTest {

    private fun parse(json: String) = Json.parseToJsonElement(json)

    @Test
    fun sortsObjectKeysRecursivelyAtEveryDepth() {
        val el = parse("""{"b":1,"a":{"z":1,"y":{"b":2,"a":3}},"c":2}""")
        assertEquals(
            """{"a":{"y":{"a":3,"b":2},"z":1},"b":1,"c":2}""",
            RuleUpdateClient.canonicalJson(el)
        )
    }

    @Test
    fun preservesArrayOrder() {
        val el = parse("""{"a":[3,1,2]}""")
        assertEquals("""{"a":[3,1,2]}""", RuleUpdateClient.canonicalJson(el))
    }

    @Test
    fun doesNotEscapeNonAsciiCharacters() {
        // Em-dash, curly quote, CJK — JSON.stringify emits these raw, and the
        // signed bytes must too. (org.json.quote would escape U+2000-U+20FF
        // and silently corrupt the signature input.)
        assertEquals(
            "\"a\u2014b\u2019c\u4e2d\"",
            RuleUpdateClient.quoteJsonString("a\u2014b\u2019c\u4e2d")
        )
    }

    @Test
    fun escapesOnlyWhatJsonStringifyEscapes() {
        assertEquals(
            "\"a\\nb\\tc\\u0001d\\\"e\\\\f\\rg\"",
            RuleUpdateClient.quoteJsonString("a\nb\tc\u0001d\"e\\f\rg")
        )
    }

    @Test
    fun emitsIntegersWithoutDecimalPointOrExponent() {
        val el = parse("""{"a":5,"b":5.0,"c":1e3}""")
        assertEquals("""{"a":5,"b":5,"c":1000}""", RuleUpdateClient.canonicalJson(el))
    }

    @Test
    fun canonicalizesBooleansAndNull() {
        val el = parse("""{"t":true,"f":false,"n":null}""")
        assertEquals("""{"f":false,"n":null,"t":true}""", RuleUpdateClient.canonicalJson(el))
    }

    @Test
    fun nestedPackageShapedDocumentIsCanonical() {
        val el = parse(
            """{"version":"1.0.0","published_at":"2026-07-19T00:12:07.741Z","rules":{"fraud_keywords":[{"id":"FK01","pairs":[["paid","not paid"]]}],"contradiction_patterns":[]}}"""
        )
        assertEquals(
            """{"published_at":"2026-07-19T00:12:07.741Z","rules":{"contradiction_patterns":[],"fraud_keywords":[{"id":"FK01","pairs":[["paid","not paid"]]}]},"version":"1.0.0"}""",
            RuleUpdateClient.canonicalJson(el)
        )
    }
}
