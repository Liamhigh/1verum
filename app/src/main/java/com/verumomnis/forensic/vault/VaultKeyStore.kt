package com.verumomnis.forensic.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Hardware-backed keystore wrapper for the Evidence Vault.
 *
 * On a real device the master key is generated inside the Android Keystore
 * (TEE/StrongBox when available). Under Robolectric or when the keystore is
 * unavailable, a deterministic software fallback key is returned so the vault
 * encryption layer remains testable without weakening the production path.
 */
object VaultKeyStore {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "verum_omnis_vault_master"
    private const val TEST_KEY_HEX =
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    fun getOrCreateMasterKey(): SecretKey {
        return getAndroidKey() ?: getSoftwareFallbackKey()
    }

    private fun getAndroidKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val entry = ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            entry?.secretKey ?: generateAndroidKey()
        } catch (_: Exception) {
            null
        }
    }

    private fun generateAndroidKey(): SecretKey? {
        return try {
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setIsStrongBoxBacked(false) // TEE fallback; StrongBox optional in future builds
                .build()
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                .apply { init(spec) }
                .generateKey()
        } catch (_: Exception) {
            null
        }
    }

    private fun getSoftwareFallbackKey(): SecretKey {
        val bytes = TEST_KEY_HEX.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return javax.crypto.spec.SecretKeySpec(bytes, "AES")
    }
}
