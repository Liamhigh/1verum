package com.verumomnis.forensic.vault

import java.io.ByteArrayInputStream
import javax.crypto.SecretKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EvidenceVaultEncryptionTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val key: SecretKey = VaultEncryption.generateKey()

    private fun vault(root: java.io.File = temp.newFolder()) =
        EvidenceVault(root, masterKeyProvider = { key }).also { it.initialize() }

    @Test
    fun `new evidence is encrypted on disk and reads back unchanged`() {
        val vault = vault()
        val bytes = "confidential original evidence".toByteArray()

        val hash = vault.storeEvidence("original.txt", bytes)
        val stored = java.io.File(vault.evidenceRaw, "original.txt")

        assertTrue(VaultEncryption.isEncryptedEvidence(stored))
        assertFalse(stored.readBytes().toString(Charsets.UTF_8).contains("confidential"))
        assertEquals(com.verumomnis.forensic.crypto.Sha512.hash(bytes), hash)
        assertArrayEquals(bytes, vault.readEvidence("original.txt"))
        assertTrue(vault.verifyIntegrity().isEmpty())
    }

    @Test
    fun `streamed evidence is encrypted and remains readable`() {
        val vault = vault()
        val bytes = ByteArray(180_000) { (it % 251).toByte() }

        val (stored, hash) = vault.storeEvidenceStreaming("bundle.bin", ByteArrayInputStream(bytes))

        assertTrue(VaultEncryption.isEncryptedEvidence(stored))
        assertEquals(bytes.size.toLong(), vault.evidenceSize("bundle.bin"))
        assertEquals(com.verumomnis.forensic.crypto.Sha512.hash(bytes), hash)
        assertArrayEquals(bytes, vault.readEvidence("bundle.bin"))
    }

    @Test
    fun `tampered encrypted evidence fails authentication and integrity verification`() {
        val vault = vault()
        vault.storeEvidence("photo.jpg", "original image bytes".toByteArray())
        val stored = java.io.File(vault.evidenceRaw, "photo.jpg")
        val changed = stored.readBytes().also { it[it.lastIndex - 2] = (it[it.lastIndex - 2].toInt() xor 1).toByte() }
        stored.writeBytes(changed)

        assertTrue(vault.verifyIntegrity().contains("TAMPERED: photo.jpg"))
        runCatching { vault.readEvidence("photo.jpg") }.onSuccess {
            throw AssertionError("tampered evidence was returned")
        }
    }

    @Test
    fun `key unavailable fails closed without creating plaintext evidence`() {
        val root = temp.newFolder()
        val vault = EvidenceVault(root, masterKeyProvider = { throw IllegalStateException("key unavailable") })
        vault.initialize()

        val result = runCatching { vault.storeEvidence("private.txt", "must not be stored".toByteArray()) }

        assertTrue(result.isFailure)
        assertFalse(java.io.File(vault.evidenceRaw, "private.txt").exists())
    }

    @Test
    fun `key unavailable does not report valid encrypted evidence as tampered`() {
        val root = temp.newFolder()
        vault(root).storeEvidence("private.txt", "protected bytes".toByteArray())
        val lockedVault = EvidenceVault(
            root,
            masterKeyProvider = { throw IllegalStateException("key unavailable") }
        )

        val read = runCatching { lockedVault.readEvidence("private.txt") }

        assertTrue(read.isFailure)
        assertEquals(listOf("KEY_UNAVAILABLE: private.txt"), lockedVault.verifyIntegrity())
    }

    @Test
    fun `legacy plaintext verifies reads and migrates only after hash validation`() {
        val vault = vault()
        val original = "legacy evidence".toByteArray()
        val file = java.io.File(vault.evidenceRaw, "legacy.txt")
        file.writeBytes(original)
        java.io.File(vault.findings, "integrity_manifest.json").writeText(
            """{"file":"legacy.txt","sha512":"${com.verumomnis.forensic.crypto.Sha512.hash(original)}","timestamp":"2026-01-01T00:00:00Z"}"""
        )

        assertArrayEquals(original, vault.readEvidence("legacy.txt"))
        assertTrue(vault.verifyIntegrity().isEmpty())
        assertEquals(listOf("legacy.txt"), vault.migrateLegacyEvidence())
        assertTrue(VaultEncryption.isEncryptedEvidence(file))
        assertArrayEquals(original, vault.readEvidence("legacy.txt"))
        assertTrue(vault.verifyIntegrity().isEmpty())
    }

    @Test
    fun `migration leaves legacy files untouched when manifest hash does not match`() {
        val vault = vault()
        val original = "unindexed or changed".toByteArray()
        val file = java.io.File(vault.evidenceRaw, "legacy.txt").apply { writeBytes(original) }
        java.io.File(vault.findings, "integrity_manifest.json").writeText(
            """{"file":"legacy.txt","sha512":"wrong","timestamp":"2026-01-01T00:00:00Z"}"""
        )

        assertTrue(vault.migrateLegacyEvidence().isEmpty())
        assertArrayEquals(original, file.readBytes())
    }
}
