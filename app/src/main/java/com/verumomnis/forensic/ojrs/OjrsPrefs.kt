package com.verumomnis.forensic.ojrs

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent on/off switch for Online Judicial Retrieval.
 *
 * Default is **disabled** so scans are deterministic and no network traffic is
 * generated without explicit user consent.
 */
class OjrsPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    fun clear() = prefs.edit().remove(KEY_ENABLED).apply()

    companion object {
        private const val PREFS_NAME = "verum_ojrs_prefs"
        private const val KEY_ENABLED = "ojrs_enabled"
    }
}
