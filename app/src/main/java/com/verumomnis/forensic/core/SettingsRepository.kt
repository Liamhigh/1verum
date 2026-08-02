package com.verumomnis.forensic.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Persisted user-facing app settings (Settings screen toggles). Plain SharedPreferences —
 * these are non-sensitive local UI preferences, not evidence or credentials, so they don't
 * need the Keystore-backed encryption used by [com.verumomnis.forensic.vault.EvidenceVault].
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var ojrsEnabled: Boolean
        get() = prefs.getBoolean(KEY_OJRS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_OJRS_ENABLED, value).apply()

    var autoDeleteDays: Int
        get() = prefs.getInt(KEY_AUTO_DELETE_DAYS, 30)
        set(value) = prefs.edit().putInt(KEY_AUTO_DELETE_DAYS, value).apply()

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var cameraEnabled: Boolean
        get() = prefs.getBoolean(KEY_CAMERA_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CAMERA_ENABLED, value).apply()

    private companion object {
        const val PREFS_NAME = "verum_settings"
        const val KEY_OJRS_ENABLED = "ojrs_enabled"
        const val KEY_AUTO_DELETE_DAYS = "auto_delete_days"
        const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        const val KEY_CAMERA_ENABLED = "camera_enabled"
    }
}
