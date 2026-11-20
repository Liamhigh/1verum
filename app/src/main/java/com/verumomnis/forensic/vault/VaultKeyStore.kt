package com.verumomnis.forensic.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Hardware-backed keystore wrapper for the Evidence Vault.
 *
 * The non-exportable master key is created once under a stable alias and remains
 * there for the lifetime of the app installation. It is not backed up with app
 * files. If Android Keystore is unavailable or the key is lost/invalidated while
 * encrypted evidence remains, reads fail closed and a replacement key is not
 * created. Removing app data/uninstalling removes both the files and key.
 */
object VaultKeyStore {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "verum_omnis_vault_master"
    fun getOrCreateMasterKey(hasEncryptedEvidence: Boolean = false): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existing != null) return existing.secretKey
            check(!hasEncryptedEvidence) {
                "Vault key is missing while encrypted evidence still exists"
            }

            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setIsStrongBoxBacked(false) // TEE-backed where available; StrongBox is optional.
                .build()
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                .apply { init(spec) }
                .generateKey()
        } catch (error: Exception) {
            throw IllegalStateException(
                "Vault encryption key is unavailable; evidence was not read or replaced",
                error
            )
        }
    }
}
