package com.verumomnis.forensic.vault

import com.verumomnis.forensic.crypto.Sha512
import java.io.File
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EvidenceVaultIntegrityTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val key: SecretKey = VaultEncryption.generateKey()

    private fun vault(root: File) =
        EvidenceVault(root, masterKeyProvider = { key }).also { it.initialize() }

    @Test
    fun `stored original matches its manifest SHA-512`() {
        val vault = vault(temp.newFolder())
        val original = "unaltered original evidence".toByteArray()

        val storedHash = vault.storeEvidence("original.txt", original)
        val manifestEntry = vault.integrityManifest().single()

        assertEquals(Sha512.hash(original), storedHash)
        assertEquals(storedHash, manifestEntry.sha512)
        assertEquals("original.txt", manifestEntry.fileName)
        assertTrue(vault.verifyIntegrity().isEmpty())
    }

    @Test
    fun `modified and missing evidence produce explicit mismatches`() {
        val vault = vault(temp.newFolder())
        vault.storeEvidence("modified.txt", "evidence to alter".toByteArray())
        vault.storeEvidence("missing.txt", "evidence to remove".toByteArray())

        val modified = File(vault.evidenceRaw, "modified.txt")
        modified.writeBytes(modified.readBytes().also { bytes ->
            bytes[bytes.lastIndex - 1] = (bytes[bytes.lastIndex - 1].toInt() xor 1).toByte()
        })
        assertTrue(File(vault.evidenceRaw, "missing.txt").delete())

        assertEquals(
            listOf("TAMPERED: modified.txt", "MISSING: missing.txt"),
            vault.verifyIntegrity()
        )
    }

    @Test
    fun `manifest and verification result persist after reopening vault`() {
        val root = temp.newFolder()
        val originalVault = vault(root)
        originalVault.storeEvidence("kept.txt", "persistent evidence".toByteArray())
        assertTrue(File(originalVault.evidenceRaw, "kept.txt").delete())
        val expectedManifest = originalVault.integrityManifest()

        val reopenedVault = vault(root)

        assertEquals(expectedManifest, reopenedVault.integrityManifest())
        assertEquals(listOf("MISSING: kept.txt"), reopenedVault.verifyIntegrity())
    }

    @Test
    fun `sealed report writes never replace an existing vault artifact`() {
        val vault = vault(temp.newFolder())
        val fileName = "forensic-report.pdf"
        val originalPdf = "%PDF-original".toByteArray()
        val replacementPdf = "%PDF-replacement".toByteArray()

        vault.storeSealedReport(fileName, originalPdf)
        val secondWrite = runCatching { vault.storeSealedReport(fileName, replacementPdf) }

        assertTrue(secondWrite.isFailure)
        assertArrayEquals(originalPdf, File(vault.reportsSealed, fileName).readBytes())
        assertTrue(secondWrite.exceptionOrNull() is java.nio.file.FileAlreadyExistsException)
    }
}
