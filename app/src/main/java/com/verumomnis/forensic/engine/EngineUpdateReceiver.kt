package com.verumomnis.forensic.engine

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.verumomnis.forensic.model.AppliedUpdateRecord
import com.verumomnis.forensic.model.ContradictionPatternPatch
import com.verumomnis.forensic.model.EngineUpdate
import com.verumomnis.forensic.model.FraudKeywordPatch
import com.verumomnis.forensic.model.UpdatePatches
import com.verumomnis.forensic.model.UpdatePriority
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * Engine Update Receiver for the Android app.
 *
 * Receives lightweight JSON update payloads (delivered via email, push
 * notification, or background sync) and applies them to the local
 * contradiction engine without requiring a full app update through the
 * Play Store.
 *
 * The update flow:
 *   1. User receives email with JSON attachment (e.g., verum-update-001.json)
 *   2. User opens attachment in the Verum Omnis app
 *   3. EngineUpdateReceiver validates, applies patches, and stores record
 *   4. Local engine now has the latest fraud patterns and detection rules
 *
 * Updates can also be received via:
 *   - Push notification with embedded JSON payload
 *   - Background sync when app connects to internet
 *   - Manual import from file picker
 */
class EngineUpdateReceiver(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "verum_engine_updates"
        private const val KEY_APPLIED_UPDATES = "applied_updates"
        private const val KEY_ENGINE_PATCH_VERSION = "engine_patch_version"
        private const val UPDATE_DIR = "engine_updates"
        private const val CURRENT_ENGINE_VERSION = "5.3.1c"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * Apply an engine update from a JSON string (received via email, push, etc.).
     *
     * @param updateJson The JSON payload string
     * @return Result indicating success or failure with details
     */
    fun applyUpdate(updateJson: String): UpdateResult {
        return try {
            val update = json.decodeFromString(EngineUpdate.serializer(), updateJson)
            applyUpdate(update)
        } catch (e: Exception) {
            UpdateResult.Failure("Invalid update JSON: ${e.message}")
        }
    }

    /**
     * Apply an engine update from a parsed object.
     */
    fun applyUpdate(update: EngineUpdate): UpdateResult {
        // 1. Check if already applied
        if (isUpdateApplied(update.updateId)) {
            return UpdateResult.AlreadyApplied(update.updateId)
        }

        // 2. Check version compatibility
        if (!isVersionCompatible(update)) {
            return UpdateResult.Incompatible(
                update.updateId,
                "Engine version $CURRENT_ENGINE_VERSION is not compatible with this patch " +
                    "(requires ${update.compatibility.minEngineVersion} - ${update.compatibility.maxEngineVersion})"
            )
        }

        // 3. Verify signature (placeholder - would use real crypto verification)
        if (update.signature.isNotBlank() && !verifySignature(update)) {
            return UpdateResult.InvalidSignature(update.updateId)
        }

        // 4. Apply patches
        val patchesApplied = applyPatches(update.patches)

        // 5. Record the update
        val record = AppliedUpdateRecord(
            updateId = update.updateId,
            appliedAt = Instant.now().toString(),
            versionBefore = getCurrentPatchVersion(),
            versionAfter = update.version,
            patchesApplied = patchesApplied,
            success = patchesApplied > 0
        )
        recordUpdate(record)
        storeUpdateFile(update)

        // 6. Return result
        return UpdateResult.Success(
            updateId = update.updateId,
            patchesApplied = patchesApplied,
            description = update.description,
            priority = update.priority
        )
    }

    /**
     * Check for available updates via background sync.
     * In a production system, this would call an API endpoint.
     * For now, it checks for locally stored update files.
     */
    fun checkForUpdates(): List<EngineUpdate> {
        val pendingUpdates = mutableListOf<EngineUpdate>()
        val updateDir = File(context.filesDir, UPDATE_DIR)
        if (!updateDir.exists()) return pendingUpdates

        updateDir.listFiles { f -> f.extension == "json" }?.forEach { file ->
            try {
                val update = json.decodeFromString(EngineUpdate.serializer(), file.readText())
                if (!isUpdateApplied(update.updateId)) {
                    pendingUpdates += update
                }
            } catch (e: Exception) {
                android.util.Log.w("EngineUpdate", "Failed to parse update file: ${file.name}")
            }
        }
        return pendingUpdates.sortedByDescending { it.priority.ordinal }
    }

    /**
     * Import an update from a file (user manually selects update JSON).
     */
    fun importUpdateFromFile(file: File): UpdateResult {
        return try {
            val updateJson = file.readText()
            applyUpdate(updateJson)
        } catch (e: Exception) {
            UpdateResult.Failure("Failed to read update file: ${e.message}")
        }
    }

    /**
     * Get the list of all applied updates.
     */
    fun getAppliedUpdates(): List<AppliedUpdateRecord> {
        val jsonStr = prefs.getString(KEY_APPLIED_UPDATES, "[]") ?: "[]"
        return try {
            json.decodeFromString(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get the current engine patch version.
     */
    fun getCurrentPatchVersion(): String {
        return prefs.getString(KEY_ENGINE_PATCH_VERSION, CURRENT_ENGINE_VERSION) ?: CURRENT_ENGINE_VERSION
    }

    /**
     * Build a summary of what the update contains for user display.
     */
    fun buildUpdateSummary(update: EngineUpdate): String = buildString {
        appendLine("Engine Update ${update.updateId}")
        appendLine("Priority: ${update.priority.name}")
        appendLine()
        appendLine(update.description)
        appendLine()
        appendLine("Contains:")
        update.patches.contradictionPatterns.takeIf { it.isNotEmpty() }?.let {
            appendLine("  · ${it.size} new contradiction pattern(s)")
        }
        update.patches.fraudKeywords.takeIf { it.isNotEmpty() }?.let {
            appendLine("  · ${it.size} new fraud keyword(s)")
        }
        update.patches.detectionRules.takeIf { it.isNotEmpty() }?.let {
            appendLine("  · ${it.size} new detection rule(s)")
        }
        update.patches.statuteMappings.takeIf { it.isNotEmpty() }?.let {
            appendLine("  · ${it.size} new statute mapping(s)")
        }
        update.patches.entityPatterns.takeIf { it.isNotEmpty() }?.let {
            appendLine("  · ${it.size} new entity pattern(s)")
        }
        update.patches.thresholdAdjustments.takeIf { it.isNotEmpty() }?.let {
            appendLine("  · ${it.size} threshold adjustment(s)")
        }
        appendLine()
        appendLine("Based on ${update.statistics.verificationsAnalyzed} verification(s) worldwide.")
        if (update.statistics.geographicClusters.isNotEmpty()) {
            appendLine("Detected in: ${update.statistics.geographicClusters.joinToString(", ")}")
        }
    }

    // ------------------------------------------------------------------
    // Internal: patch application
    // ------------------------------------------------------------------

    private fun applyPatches(patches: UpdatePatches): Int {
        var count = 0

        // Apply contradiction patterns
        for (pattern in patches.contradictionPatterns) {
            if (applyContradictionPattern(pattern)) count++
        }

        // Apply fraud keywords
        for (keyword in patches.fraudKeywords) {
            if (applyFraudKeyword(keyword)) count++
        }

        // Apply detection rules
        for (rule in patches.detectionRules) {
            if (applyDetectionRule(rule)) count++
        }

        // Apply statute mappings
        for (mapping in patches.statuteMappings) {
            if (applyStatuteMapping(mapping)) count++
        }

        // Apply entity patterns
        for (entityPattern in patches.entityPatterns) {
            if (applyEntityPattern(entityPattern)) count++
        }

        // Apply threshold adjustments
        for (adjustment in patches.thresholdAdjustments) {
            if (applyThresholdAdjustment(adjustment)) count++
        }

        return count
    }

    private fun applyContradictionPattern(pattern: ContradictionPatternPatch): Boolean {
        // Store in shared preferences for the contradiction engine to read
        val key = "pattern_${pattern.patternId}"
        val existing = prefs.getString(key, null)
        if (existing != null && pattern.replacesPatternId == null) {
            // Pattern already exists and this isn't a replacement
            return false
        }
        prefs.edit().putString(key, json.encodeToString(ContradictionPatternPatch.serializer(), pattern)).apply()
        android.util.Log.i("EngineUpdate", "Applied contradiction pattern: ${pattern.name} (${pattern.patternId})")
        return true
    }

    private fun applyFraudKeyword(keyword: FraudKeywordPatch): Boolean {
        val key = "fraud_keyword_${keyword.keyword.hashCode()}"
        prefs.edit().putString(key, json.encodeToString(FraudKeywordPatch.serializer(), keyword)).apply()
        android.util.Log.i("EngineUpdate", "Applied fraud keyword: ${keyword.keyword} (weight: ${keyword.weight})")
        return true
    }

    private fun applyDetectionRule(rule: com.verumomnis.forensic.model.DetectionRulePatch): Boolean {
        val key = "detection_rule_${rule.ruleId}"
        prefs.edit().putString(key, json.encodeToString(com.verumomnis.forensic.model.DetectionRulePatch.serializer(), rule)).apply()
        android.util.Log.i("EngineUpdate", "Applied detection rule: ${rule.name} (${rule.ruleId})")
        return true
    }

    private fun applyStatuteMapping(mapping: com.verumomnis.forensic.model.StatuteMappingPatch): Boolean {
        val key = "statute_${mapping.jurisdiction}_${mapping.citation.hashCode()}"
        prefs.edit().putString(key, json.encodeToString(com.verumomnis.forensic.model.StatuteMappingPatch.serializer(), mapping)).apply()
        android.util.Log.i("EngineUpdate", "Applied statute mapping: ${mapping.citation} (${mapping.jurisdiction})")
        return true
    }

    private fun applyEntityPattern(pattern: com.verumomnis.forensic.model.EntityPatternPatch): Boolean {
        val key = "entity_pattern_${pattern.entityType}_${pattern.pattern.hashCode()}"
        prefs.edit().putString(key, json.encodeToString(com.verumomnis.forensic.model.EntityPatternPatch.serializer(), pattern)).apply()
        android.util.Log.i("EngineUpdate", "Applied entity pattern: ${pattern.entityType}")
        return true
    }

    private fun applyThresholdAdjustment(adjustment: com.verumomnis.forensic.model.ThresholdAdjustment): Boolean {
        val key = "threshold_${adjustment.thresholdName}"
        prefs.edit().putFloat(key, adjustment.newValue.toFloat()).apply()
        android.util.Log.i("EngineUpdate", "Applied threshold adjustment: ${adjustment.thresholdName} " +
            "${adjustment.oldValue} -> ${adjustment.newValue}")
        return true
    }

    // ------------------------------------------------------------------
    // Internal: helpers
    // ------------------------------------------------------------------

    private fun isUpdateApplied(updateId: String): Boolean {
        return getAppliedUpdates().any { it.updateId == updateId }
    }

    private fun isVersionCompatible(update: EngineUpdate): Boolean {
        val current = parseVersion(CURRENT_ENGINE_VERSION)
        val min = parseVersion(update.compatibility.minEngineVersion)
        val max = parseVersion(update.compatibility.maxEngineVersion)
        return current >= min && current <= max
    }

    private fun parseVersion(version: String): Int {
        // Simple version parsing: "5.3.1c" -> 50301
        val clean = version.filter { it.isDigit() }
        return clean.padEnd(5, '0').take(5).toIntOrNull() ?: 0
    }

    private fun verifySignature(update: EngineUpdate): Boolean {
        // Placeholder: In production, this would verify a cryptographic signature
        // using the Verum Omnis Foundation's public key
        return update.signature.isNotBlank() && update.signature.length >= 64
    }

    private fun recordUpdate(record: AppliedUpdateRecord) {
        val current = getAppliedUpdates().toMutableList()
        current += record
        prefs.edit().putString(KEY_APPLIED_UPDATES, json.encodeToString(current)).apply()
        prefs.edit().putString(KEY_ENGINE_PATCH_VERSION, record.versionAfter).apply()
    }

    private fun storeUpdateFile(update: EngineUpdate) {
        val dir = File(context.filesDir, UPDATE_DIR).apply { mkdirs() }
        val file = File(dir, "${update.updateId}.json")
        file.writeText(json.encodeToString(EngineUpdate.serializer(), update))
    }

    /**
     * Read all stored contradiction patterns for use by the engine.
     */
    fun getStoredContradictionPatterns(): List<ContradictionPatternPatch> {
        val patterns = mutableListOf<ContradictionPatternPatch>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("pattern_") && value is String) {
                try {
                    patterns += json.decodeFromString(ContradictionPatternPatch.serializer(), value)
                } catch (e: Exception) { /* skip invalid */ }
            }
        }
        return patterns
    }

    /**
     * Read all stored fraud keywords for use by the engine.
     */
    fun getStoredFraudKeywords(): List<FraudKeywordPatch> {
        val keywords = mutableListOf<FraudKeywordPatch>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("fraud_keyword_") && value is String) {
                try {
                    keywords += json.decodeFromString(FraudKeywordPatch.serializer(), value)
                } catch (e: Exception) { /* skip invalid */ }
            }
        }
        return keywords
    }

    // ------------------------------------------------------------------
    // Sealed class results
    // ------------------------------------------------------------------

    sealed class UpdateResult {
        data class Success(
            val updateId: String,
            val patchesApplied: Int,
            val description: String,
            val priority: UpdatePriority
        ) : UpdateResult()

        data class AlreadyApplied(val updateId: String) : UpdateResult()
        data class Incompatible(val updateId: String, val reason: String) : UpdateResult()
        data class InvalidSignature(val updateId: String) : UpdateResult()
        data class Failure(val reason: String) : UpdateResult()
    }
}
