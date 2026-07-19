package com.verumomnis.forensic.update

import android.content.Context

/**
 * Persists the last signature-VERIFIED rule package (raw JSON + version) in
 * SharedPreferences and exposes the current rules to the app.
 *
 * Only packages that passed RSA signature verification in [RuleUpdateClient]
 * may be persisted here — the registry itself performs no verification.
 * The stored rules are public data (the signing public key ships in the app);
 * integrity comes from signature verification at download time, not from
 * storage encryption.
 *
 * Thread-safe: the singleton, the parsed-rules cache and [persist] are all
 * safe to call from any thread (engine scans run on background dispatchers).
 */
class RuleRegistry private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cacheLoaded = false

    @Volatile
    private var cachedRules: DownloadedRules? = null

    /** Version of the currently installed verified package, or null if none. */
    val currentVersion: String?
        get() = prefs.getString(KEY_VERSION, null)

    /** ISO-8601 publication timestamp of the installed package, or null. */
    val currentPublishedAt: String?
        get() = prefs.getString(KEY_PUBLISHED_AT, null)

    /** Raw JSON of the installed verified package, or null if none. */
    fun currentPackageJson(): String? = prefs.getString(KEY_PACKAGE_JSON, null)

    /**
     * The current downloaded rules, parsed from the persisted verified
     * package, or null when no package has been applied (fresh install).
     * A null/empty result must leave all consumers behaving exactly as if
     * the update feature did not exist.
     */
    fun currentRules(): DownloadedRules? {
        if (!cacheLoaded) {
            synchronized(this) {
                if (!cacheLoaded) {
                    cachedRules = currentPackageJson()?.let { json ->
                        runCatching { DownloadedRules.fromPackageJson(json) }.getOrNull()
                    }
                    cacheLoaded = true
                }
            }
        }
        return cachedRules
    }

    /**
     * Atomically persists a verified package and invalidates the parsed cache.
     * Called by [RuleUpdateClient] only AFTER signature verification and the
     * newer-version check have both passed.
     */
    @Synchronized
    fun persist(packageJson: String, version: String, publishedAt: String) {
        prefs.edit()
            .putString(KEY_PACKAGE_JSON, packageJson)
            .putString(KEY_VERSION, version)
            .putString(KEY_PUBLISHED_AT, publishedAt)
            .apply()
        cachedRules = runCatching { DownloadedRules.fromPackageJson(packageJson) }.getOrNull()
        cacheLoaded = true
    }

    companion object {
        private const val PREFS_NAME = "verum_rule_updates"
        private const val KEY_PACKAGE_JSON = "package_json"
        private const val KEY_VERSION = "version"
        private const val KEY_PUBLISHED_AT = "published_at"

        @Volatile
        private var instance: RuleRegistry? = null

        /** App-wide singleton (double-checked locking). */
        fun getInstance(context: Context): RuleRegistry =
            instance ?: synchronized(this) {
                instance ?: RuleRegistry(context.applicationContext).also { instance = it }
            }
    }
}
