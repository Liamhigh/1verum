package com.verumomnis.forensic

import com.verumomnis.forensic.crypto.Sha512
import com.verumomnis.forensic.model.LinkedDocumentRecord
import com.verumomnis.forensic.ui.VaultArtifact
import com.verumomnis.forensic.ui.VaultGrouping
import com.verumomnis.forensic.vault.EvidenceVault
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.spec.SecretKeySpec

class LinkedDocumentVaultTest {
    @get:Rule val folder = TemporaryFolder()
    private fun vault() = EvidenceVault(folder.root, masterKeyProvider = {
        SecretKeySpec(ByteArray(32) { 9 }, "AES")
    })

    private fun link(vault: EvidenceVault, id: String, bytes: ByteArray): LinkedDocumentRecord {
        val hash = vault.preservePdfSource(bytes)
        return LinkedDocumentRecord(
            id, "same-name.pdf", hash, "2026-11-23T00:00:00Z",
            vault.documentPath(File(vault.evidenceRaw, "pdf_source_$hash.pdf")), true
        )
    }

    @Test fun explicitPairingSurvivesRestartWithoutChangingSealedBytes() {
        val vault = vault()
        val source = "%PDF synthetic original".toByteArray()
        val sealed = File(vault.reportsSealed, "unrelated-seal-name.pdf").apply { parentFile.mkdirs(); writeText("sealed bytes") }
        val report = File(vault.reportsSealed, "other-name.pdf").apply { writeText("report bytes") }
        val proof = File(vault.reportsSealed, "proof.ots").apply { writeText("pending proof bytes") }
        val record = link(vault, "first-attempt", source).copy(
            sealedPdfPath = vault.documentPath(sealed), reportPdfPath = vault.documentPath(report),
            timestampProofPath = vault.documentPath(proof), reportReference = "synthetic-report"
        )
        vault.saveDocumentLink(record)
        val sealedHash = Sha512.hash(sealed.readBytes())
        val reloaded = vault()
        assertEquals(listOf(record), reloaded.listDocumentLinks())
        val sets = VaultGrouping.group(
            reloaded.evidenceRaw.listFiles().orEmpty().toList(), emptyList(),
            reloaded.reportsSealed.listFiles().orEmpty().toList(),
            links = reloaded.listDocumentLinks(), resolvePath = reloaded::resolveDocumentPath
        )
        assertEquals(1, sets.size)
        assertEquals("same-name.pdf", sets.single().name)
        assertEquals(4, sets.single().artifacts.size)
        assertEquals(setOf(VaultArtifact.Kind.ORIGINAL, VaultArtifact.Kind.SEALED_DOCUMENT,
            VaultArtifact.Kind.REPORT, VaultArtifact.Kind.TIMESTAMP_PROOF), sets.single().artifacts.map { it.kind }.toSet())
        assertTrue(sets.single().artifacts.all { it.available })
        assertEquals(sealedHash, Sha512.hash(sealed.readBytes()))
        assertArrayEquals(source, reloaded.readEvidence(File(record.sourcePath).name))
    }

    @Test fun duplicateSourceNamesStaySeparateAndMissingOutputsRemainVisible() {
        val vault = vault()
        val first = link(vault, "first", "first synthetic PDF".toByteArray())
        val second = link(vault, "second", "second synthetic PDF".toByteArray()).copy(
            sealedPdfPath = "reports/sealed/missing.pdf"
        )
        vault.saveDocumentLink(first)
        vault.saveDocumentLink(second)
        val sets = VaultGrouping.group(emptyList(), emptyList(), emptyList(),
            links = vault().listDocumentLinks(), resolvePath = vault::resolveDocumentPath)
        assertEquals(2, sets.size)
        assertEquals(2, sets.map { it.id }.distinct().size)
        assertEquals(2, sets.map { it.sourceSha512 }.distinct().size)
        val missing = sets.single { it.id == "linked:second" }
        assertFalse(missing.artifacts.single { it.kind == VaultArtifact.Kind.SEALED_DOCUMENT }.available)
        assertTrue(missing.linkNotes.contains("Forensic report PDF not saved"))
        assertTrue(missing.linkNotes.contains("Timestamp proof not saved"))
    }

    @Test fun sameSourceCanHaveTwoDistinctSealingAttempts() {
        val vault = vault()
        val bytes = "identical synthetic PDF".toByteArray()
        vault.saveDocumentLink(link(vault, "one", bytes))
        vault.saveDocumentLink(link(vault, "two", bytes).copy(forensicRequested = false))
        assertEquals(2, vault().listDocumentLinks().size)
        assertEquals(1, vault().listDocumentLinks().map { it.sourcePath }.distinct().size)
    }

    @Test fun linksRejectEscapingPathsAndChangingTheParentIdentity() {
        val vault = vault()
        val record = link(vault, "immutable-parent", "synthetic".toByteArray())
        vault.saveDocumentLink(record)
        assertThrows(IllegalArgumentException::class.java) {
            vault.saveDocumentLink(record.copy(sealedPdfPath = "../outside.pdf"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            vault.saveDocumentLink(record.copy(sourceName = "swapped.pdf"))
        }
        assertEquals(record, vault().listDocumentLinks().single())
    }
}
