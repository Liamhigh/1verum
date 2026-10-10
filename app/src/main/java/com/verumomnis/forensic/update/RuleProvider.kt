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
 *  - [behavioralMarkers] are consumed by
 *    `ContradictionDetectors.detectDownloadedBehavioralMarkers`: a curated
 *    marker (keyword set) fires when enough of its distinct keywords appear
 *    in one claim (`min_cooccur`, else 2, else 1 for a single-keyword
 *    marker), producing one aggregated BEHAVIORAL finding per marker
 *    (patternType `DOWNLOADED_MARKER_<id>`) whose description carries the
 *    doctrine: indicators for human review, not a determination.
 *
 * What downloaded rules DO NOT affect today (exposed for future wiring only):
 *  - [fraudTerms] (single keyword groups from `fraud_keywords[].terms`) and
 *    [behavioralKeywords] (the raw flat view of `behavioral_markers[].keywords`)
 *    are parsed and exposed but are NOT injected into any detector — single
 *    keywords are not contradictions by themselves in the claim-based engine
 *    model. The executable marker path is [behavioralMarkers].
 *  - `contradiction_patterns`, `serial_patterns` (multi-stage schemes) and
 *    `case_configs` from the package are not executed by the on-device engine;
 *    only their counts are retained for observability.
 *  - `fraud_keywords[].items` (structured role/requirement records) and
 *    `fraud_keywords[].groups` (mutually-exclusive status sets) are not flat
 *    keyword lists; they are intentionally skipped.
 *
 * The seed package's own behavioural markers (BM01-BM10) are the built-in
 * detectors' exported vocabulary (SP01, D07, D08, D28, D29, SP16); they are
 * skipped here exactly as the website engine skips them, so nothing
 * double-fires beside the airtight built-in detectors. Only CURATED markers
 * (BM11+, the learning loop's output) are applied.
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
    val serialPatternCount: Int,
    val behavioralMarkers: List<BehavioralMarker> = emptyList()
) {

    /** One opposing phrase pair from a `fraud_keywords[].pairs` group (e.g. "paid" / "not paid"). */
    data class FraudPair(val ruleId: String, val first: String, val second: String)

    /**
     * One curated behavioural marker: a keyword set that fires when enough
     * distinct keywords co-occur in one claim. [produces] optionally names
     * the behavioural type it labels (e.g. "CT47"); [minCooccur] overrides
     * the default co-occurrence threshold (2, or 1 for single-keyword rules).
     */
    data class BehavioralMarker(
        val ruleId: String,
        val name: String,
        val keywords: List<String>,
        val produces: String? = null,
        val minCooccur: Int? = null
    )

    companion object {

        /**
         * The seed package's own behavioural markers — the built-in
         * detectors' exported vocabulary. Mirrors the website engine's
         * VO_ENGINE_OWN_MARKERS: skipped so curated markers only ever ADD.
         */
        private val ENGINE_OWN_MARKERS = setOf(
            "BM01", "BM02", "BM03", "BM04", "BM05", "BM06", "BM07", "BM08", "BM09", "BM10"
        )

        /**
         * Merge the remote signed package with locally promoted G3 rules.
         * Either side may be null; returns null only when both are, so the
         * engine's "no rules -> unchanged behaviour" invariant holds.
         */
        fun merged(remote: DownloadedRules?, local: DownloadedRules?): DownloadedRules? {
            if (remote == null) return local
            if (local == null) return remote
            return DownloadedRules(
                version = "${remote.version}+${local.version}",
                publishedAt = remote.publishedAt,
                fraudPairs = remote.fraudPairs + local.fraudPairs,
                fraudTerms = remote.fraudTerms + local.fraudTerms,
                behavioralKeywords = remote.behavioralKeywords + local.behavioralKeywords,
                contradictionPatternCount = remote.contradictionPatternCount + local.contradictionPatternCount,
                serialPatternCount = remote.serialPatternCount + local.serialPatternCount,
                behavioralMarkers = remote.behavioralMarkers + local.behavioralMarkers
            )
        }

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
            val curatedMarkers = mutableListOf<BehavioralMarker>()
            val behavioralMarkers = rules?.get("behavioral_markers") as? JsonArray
            behavioralMarkers?.forEach { entry ->
                val obj = entry as? JsonObject ?: return@forEach
                val keywords = mutableListOf<String>()
                (obj["keywords"] as? JsonArray)?.forEach { kwEl ->
                    val kw = (kwEl as? JsonPrimitive)?.contentOrNull
                    if (!kw.isNullOrBlank()) {
                        behavioralKeywords += kw
                        keywords += kw.trim().lowercase()
                    }
                }
                // The seed's own BM01-BM10 are the built-in detectors'
                // vocabulary exported; only curated markers are applied.
                val markerId = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: "BM??"
                if (markerId in ENGINE_OWN_MARKERS) return@forEach
                if (keywords.isEmpty()) return@forEach
                val produces = (obj["produces"] as? JsonPrimitive)?.contentOrNull
                    ?.takeIf { Regex("CT\\d{2}").matches(it) }
                val minCooccur = (obj["min_cooccur"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                curatedMarkers += BehavioralMarker(
                    ruleId = markerId.take(32),
                    name = ((obj["name"] as? JsonPrimitive)?.contentOrNull ?: "").take(80),
                    keywords = keywords.distinct(),
                    produces = produces,
                    minCooccur = minCooccur
                )
            }

            return DownloadedRules(
                version = version,
                publishedAt = publishedAt,
                fraudPairs = fraudPairs,
                fraudTerms = fraudTerms,
                behavioralKeywords = behavioralKeywords,
                contradictionPatternCount = (rules?.get("contradiction_patterns") as? JsonArray)?.size ?: 0,
                serialPatternCount = (rules?.get("serial_patterns") as? JsonArray)?.size ?: 0,
                behavioralMarkers = curatedMarkers
            )
        }
    }
}
