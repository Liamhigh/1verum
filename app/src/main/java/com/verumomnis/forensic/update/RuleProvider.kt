package com.verumomnis.forensic.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Parsed view of a signature-verified rule package downloaded from the Verum
 * rules service (`Constitution.RULE_MANIFEST_URL`, format: rule-format.md v1).
 *
 * What downloaded rules DO affect today:
 *  - [fraudPairs] are consumed by
 *    `ContradictionDetectors.detectDownloadedFraudPairs`: when two claims
 *    (same actor or same subject) contain opposite sides of a downloaded
 *    phrase pair, an additional BEHAVIORAL contradiction is reported
 *    (patternType `DOWNLOADED_RULE_<id>`). Downloaded entries only ever ADD
 *    candidate contradictions; built-in detectors and their keyword lists are
 *    never modified or removed.
 *
 * What downloaded rules DO NOT affect today (exposed for future wiring only):
 *  - [fraudTerms] (single keyword groups from `fraud_keywords[].terms`) and
 *    [behavioralKeywords] (from `behavioral_markers[].keywords`) are parsed
 *    and exposed but are NOT injected into any detector — single keywords are
 *    not contradictions by themselves in the claim-based engine model.
 *  - `contradiction_patterns`, `serial_patterns` (multi-stage schemes) and
 *    `case_configs` from the package are not executed by the on-device engine;
 *    only their counts are retained for observability.
 *  - `fraud_keywords[].items` (structured role/requirement records) and
 *    `fraud_keywords[].groups` (mutually-exclusive status sets) are not flat
 *    keyword lists; they are intentionally skipped.
 *
 * When no verified package has been downloaded, [RuleRegistry.currentRules]
 * returns null and engine behaviour is identical to a fresh install.
 */
class DownloadedRules(
    val version: String,
    val publishedAt: String,
    val fraudPairs: List<FraudPair>,
    val fraudTerms: List<String>,
    val behavioralKeywords: List<String>,
    val contradictionPatternCount: Int,
    val serialPatternCount: Int
) {

    /** One opposing phrase pair from a `fraud_keywords[].pairs` group (e.g. "paid" / "not paid"). */
    data class FraudPair(val ruleId: String, val first: String, val second: String)

    companion object {

        /** Parses a persisted (already signature-verified) package JSON document. */
        fun fromPackageJson(packageJson: String): DownloadedRules =
            fromPackage(Json.parseToJsonElement(packageJson) as JsonObject)

        /** Parses a decoded package object. Tolerant: unknown/absent fields are ignored. */
        fun fromPackage(pkg: JsonObject): DownloadedRules {
            val version = (pkg["version"] as? JsonPrimitive)?.contentOrNull ?: "0.0.0"
            val publishedAt = (pkg["published_at"] as? JsonPrimitive)?.contentOrNull ?: ""
            val rules = pkg["rules"] as? JsonObject

            val fraudPairs = mutableListOf<FraudPair>()
            val fraudTerms = mutableListOf<String>()
            val fraudKeywords = rules?.get("fraud_keywords") as? JsonArray
            fraudKeywords?.forEach entries@{ entry ->
                val obj = entry as? JsonObject ?: return@entries
                val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: "FK??"
                // "pairs": [["paid","not paid"], ...] -> opposing phrase pairs
                (obj["pairs"] as? JsonArray)?.forEach pairs@{ pairEl ->
                    val pair = pairEl as? JsonArray ?: return@pairs
                    val a = (pair.getOrNull(0) as? JsonPrimitive)?.contentOrNull
                    val b = (pair.getOrNull(1) as? JsonPrimitive)?.contentOrNull
                    if (!a.isNullOrBlank() && !b.isNullOrBlank()) {
                        fraudPairs += FraudPair(id, a, b)
                    }
                }
                // "terms": flat string entries only. Array entries are co-occurrence
                // sets (e.g. FK03 logically-impossible states), not single keywords.
                (obj["terms"] as? JsonArray)?.forEach { termEl ->
                    val term = (termEl as? JsonPrimitive)?.contentOrNull
                    if (!term.isNullOrBlank()) fraudTerms += term
                }
            }

            val behavioralKeywords = mutableListOf<String>()
            val behavioralMarkers = rules?.get("behavioral_markers") as? JsonArray
            behavioralMarkers?.forEach { entry ->
                val obj = entry as? JsonObject ?: return@forEach
                (obj["keywords"] as? JsonArray)?.forEach { kwEl ->
                    val kw = (kwEl as? JsonPrimitive)?.contentOrNull
                    if (!kw.isNullOrBlank()) behavioralKeywords += kw
                }
            }

            return DownloadedRules(
                version = version,
                publishedAt = publishedAt,
                fraudPairs = fraudPairs,
                fraudTerms = fraudTerms,
                behavioralKeywords = behavioralKeywords,
                contradictionPatternCount = (rules?.get("contradiction_patterns") as? JsonArray)?.size ?: 0,
                serialPatternCount = (rules?.get("serial_patterns") as? JsonArray)?.size ?: 0
            )
        }
    }
}
