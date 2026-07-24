package com.verumomnis.forensic.update

import android.content.Context
import com.verumomnis.forensic.crypto.Sha512
import com.verumomnis.forensic.engine.contradiction.FindingsJsonEmitter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * Closes the GHRP feedback loop on-device: when a G3-raised candidate is
 * PROMOTED (engine re-run or human sign-off), its opposing proposition pair is
 * persisted here as a local fraud-pair rule. The pair is served to the engine
 * through the same additive path as signature-verified downloaded rules
 * (`ContradictionDetectors.detectDownloadedFraudPairs`), so on the next scan
 * the deterministic engine itself detects the contradiction Gemma 3 caught —
 * promotion by engine re-run, exactly as G3_HYBRID_REPORT_PIPELINE.md
 * section 4 specifies.
 *
 * Local promoted rules are additive-only, never modify built-in detectors,
 * and are kept separate from the remote signed package (which remains the
 * only path for externally authored rules).
 */
class LocalRuleStore private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Record a promoted candidate's proposition pair as an engine rule.
     * The rule ID is derived from the pair content (not the per-case
     * candidate counter, which restarts at G3-CAND-0001 for every scan), so
     * promotions from different cases never collide and the same pair is
     * naturally deduplicated.
     */
    @Synchronized
    fun addPromotedCandidate(record: FindingsJsonEmitter.FindingsContradictionRecord) {
        val first = record.propositionAText.trim()
        val second = record.propositionBText.trim()
        if (first.isEmpty() || second.isEmpty()) return
        val ruleId = "G3_" + Sha512.hash("${first.lowercase()}|${second.lowercase()}").take(16)
        val existing = loadPairs()
        if (existing.any { it.ruleId == ruleId }) return
        val updated = existing + DownloadedRules.FraudPair(
            ruleId = ruleId,
            first = first,
            second = second
        )
        prefs.edit().putString(KEY_PAIRS, encodePairs(updated)).apply()
    }

    /**
     * Promoted rules in [DownloadedRules] form, or null when none exist so
     * consumers behave exactly as if the feature did not exist.
     */
    fun promotedRules(): DownloadedRules? {
        val pairs = loadPairs()
        if (pairs.isEmpty()) return null
        return DownloadedRules(
            version = "local-promoted",
            publishedAt = "",
            fraudPairs = pairs,
            fraudTerms = emptyList(),
            behavioralKeywords = emptyList(),
            contradictionPatternCount = 0,
            serialPatternCount = 0
        )
    }

    private fun loadPairs(): List<DownloadedRules.FraudPair> {
        val raw = prefs.getString(KEY_PAIRS, null) ?: return emptyList()
        return runCatching {
            (Json.parseToJsonElement(raw) as JsonArray).mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val first = (obj["first"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val second = (obj["second"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                DownloadedRules.FraudPair(id, first, second)
            }
        }.getOrDefault(emptyList())
    }

    private fun encodePairs(pairs: List<DownloadedRules.FraudPair>): String =
        buildJsonArray {
            pairs.forEach { pair ->
                add(buildJsonObject {
                    put("id", JsonPrimitive(pair.ruleId))
                    put("first", JsonPrimitive(pair.first))
                    put("second", JsonPrimitive(pair.second))
                })
            }
        }.toString()

    companion object {
        private const val PREFS_NAME = "verum_local_promoted_rules"
        private const val KEY_PAIRS = "promoted_fraud_pairs"

        @Volatile
        private var instance: LocalRuleStore? = null

        fun getInstance(context: Context): LocalRuleStore =
            instance ?: synchronized(this) {
                instance ?: LocalRuleStore(context.applicationContext).also { instance = it }
            }
    }
}
