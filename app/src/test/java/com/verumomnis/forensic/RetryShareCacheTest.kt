package com.verumomnis.forensic

import com.verumomnis.forensic.pdf.RetryShareCache
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files

class RetryShareCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val now = 1_795_392_000_000L
    private val cutoff get() = now - RetryShareCache.RETENTION_MILLIS
    private val pdfName = "VerumOmnis_source_retry_attempt-1_report_123.pdf"
    private val jsonName = "VerumOmnis_source_retry_attempt-1_findings_456.json"
    private fun file(directory: File, name: String, modified: Long): File =
        File(directory.apply { mkdirs() }, name).apply {
            writeText("synthetic preserved bytes: $name")
            assertTrue(setLastModified(modified))
        }

    @Test fun removesExpiredPairsButRetainsRecentAndNewAttachments() {
        val cache = RetryShareCache(temporary.root)
        val expiredPdf = file(cache.directory, pdfName, cutoff - 1)
        val expiredJson = file(cache.directory, jsonName, cutoff - 1)
        val recent = file(cache.directory, pdfName.replace("123", "124"), cutoff + 1)
        val current = file(cache.directory, jsonName.replace("456", "457"), now)
        val recentBytes = recent.readBytes()
        val currentBytes = current.readBytes()

        val result = cache.cleanExpired(now)

        assertEquals(RetryShareCache.CleanupResult(deleted = 2), result)
        assertFalse(expiredPdf.exists())
        assertFalse(expiredJson.exists())
        assertArrayEquals(recentBytes, recent.readBytes())
        assertArrayEquals(currentBytes, current.readBytes())
    }

    @Test fun expiryStartsAtExactly24HoursAndUnknownOrFutureDatesAreRetained() {
        val cache = RetryShareCache(temporary.root)
        val boundary = file(cache.directory, pdfName, cutoff)
        val unknown = file(cache.directory, jsonName, 0)
        val future = file(cache.directory, pdfName.replace("123", "125"), now + 60_000)

        assertEquals(1, cache.cleanExpired(now).deleted)
        assertFalse(boundary.exists())
        assertTrue(unknown.exists())
        assertTrue(future.exists())
    }

    @Test fun neverDeletesVaultSavedReportsUnrelatedCacheOrNestedFiles() {
        val root = temporary.root
        val cache = RetryShareCache(File(root, "cache"))
        val protectedFiles = listOf(
            file(File(root, "files/vault/reports/sealed"), pdfName, cutoff - 1),
            file(File(root, "files/vault/findings"), jsonName, cutoff - 1),
            file(File(root, "files/vault/evidence/raw"), "source.pdf", cutoff - 1),
            file(File(root, "cache"), pdfName, cutoff - 1),
            file(cache.directory, "original.pdf", cutoff - 1),
            file(cache.directory, "unrelated.json", cutoff - 1),
            file(File(cache.directory, "nested"), pdfName, cutoff - 1),
            file(File(cache.directory, jsonName), "nested-sensitive-file", cutoff - 1)
        )
        val before = protectedFiles.associateWith { it.readBytes() }
        file(cache.directory, pdfName, cutoff - 1)

        assertEquals(1, cache.cleanExpired(now).deleted)
        before.forEach { (path, bytes) -> assertArrayEquals(path.path, bytes, path.readBytes()) }
    }

    @Test fun skipsSymbolicFilesAndDirectoriesWithoutTouchingTheirTargets() {
        val cache = RetryShareCache(File(temporary.root, "cache"))
        val vaultFile = file(File(temporary.root, "vault"), "original.pdf", cutoff - 1)
        cache.directory.mkdirs()
        val link = File(cache.directory, pdfName)
        Files.createSymbolicLink(link.toPath(), vaultFile.toPath())
        val nestedLink = File(cache.directory, jsonName)
        Files.createSymbolicLink(nestedLink.toPath(), vaultFile.parentFile.toPath())
        val original = vaultFile.readBytes()

        assertEquals(0, cache.cleanExpired(now).deleted)
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertTrue(Files.isSymbolicLink(nestedLink.toPath()))
        assertArrayEquals(original, vaultFile.readBytes())
    }

    @Test fun skipsEntireCleanupWhenShareDirectoryIsASymbolicLink() {
        val cacheRoot = File(temporary.root, "cache").apply { mkdirs() }
        val target = File(temporary.root, "vault")
        val protected = file(target, pdfName, cutoff - 1)
        Files.createSymbolicLink(File(cacheRoot, "retry-share").toPath(), target.toPath())
        val original = protected.readBytes()

        assertEquals(0, RetryShareCache(cacheRoot).cleanExpired(now).deleted)
        assertArrayEquals(original, protected.readBytes())
    }

    @Test fun failedDeletionDoesNotThrowOrPreventOtherExpiredFilesBeingCleaned() {
        val cache = RetryShareCache(temporary.root) { candidate ->
            when {
                candidate.name.endsWith(".json") -> throw IOException("Synthetic deletion failure")
                candidate.name.contains("report_123") -> false
                else -> candidate.delete()
            }
        }
        val failedPdf = file(cache.directory, pdfName, cutoff - 1)
        val failedJson = file(cache.directory, jsonName, cutoff - 1)
        val other = file(cache.directory, pdfName.replace("123", "999"), cutoff - 1)

        assertEquals(RetryShareCache.CleanupResult(deleted = 1, failures = 2), cache.cleanExpired(now))
        assertTrue(failedPdf.exists())
        assertTrue(failedJson.exists())
        assertFalse(other.exists())
    }

    @Test fun missingDirectoryIsNotCreatedAndInvalidClockDoesNotDeleteAnything() {
        val cache = RetryShareCache(temporary.root)
        assertEquals(RetryShareCache.CleanupResult(), cache.cleanExpired(now))
        assertFalse(cache.directory.exists())
        val staged = file(cache.directory, pdfName, cutoff - 1)
        assertEquals(RetryShareCache.CleanupResult(), cache.cleanExpired(Long.MIN_VALUE))
        assertTrue(staged.exists())
    }

    @Test fun inventoryAndManualClearIncludeRecentUnknownAndFutureCopiesOnly() {
        val cache = RetryShareCache(File(temporary.root, "cache"))
        val copies = listOf(
            file(cache.directory, pdfName, now),
            file(cache.directory, jsonName, 0),
            file(cache.directory, pdfName.replace("123", "999"), now + 60_000)
        )
        val protectedFiles = listOf(
            file(File(temporary.root, "vault/evidence/raw"), "source.pdf", now),
            file(File(temporary.root, "vault/reports"), pdfName, now),
            file(File(temporary.root, "vault/findings"), jsonName, now),
            file(cache.directory, "findings.json", now),
            file(cache.directory, "report.pdf", now),
            file(File(cache.directory, "nested"), pdfName, now),
            file(cache.directory.parentFile!!, pdfName, now)
        )
        val before = protectedFiles.associateWith { it.readBytes() }
        val link = File(cache.directory, jsonName.replace("456", "999"))
        Files.createSymbolicLink(link.toPath(), protectedFiles.first().toPath())

        assertEquals(RetryShareCache.Inventory(3, copies.sumOf { it.length() }), cache.inventory())
        assertEquals(RetryShareCache.CleanupResult(deleted = 3), cache.clearAll())
        assertEquals(RetryShareCache.Inventory(), cache.inventory())
        assertTrue(Files.isSymbolicLink(link.toPath()))
        before.forEach { (path, bytes) -> assertArrayEquals(path.path, bytes, path.readBytes()) }
    }

    @Test fun manualClearReportsPartialFailuresAndCanBeRetried() {
        val cache = RetryShareCache(temporary.root) { candidate ->
            if (candidate.name == jsonName) false else candidate.delete()
        }
        val pdf = file(cache.directory, pdfName, now)
        val json = file(cache.directory, jsonName, now)
        assertEquals(RetryShareCache.CleanupResult(1, 1), cache.clearAll())
        assertFalse(pdf.exists())
        assertEquals(RetryShareCache.Inventory(1, json.length()), cache.inventory())
        assertEquals(RetryShareCache.CleanupResult(1), RetryShareCache(temporary.root).clearAll())
    }

    @Test fun replacingShareDirectoryWithVaultLinkAfterInventoryFailsClosed() {
        val cache = RetryShareCache(File(temporary.root, "cache"))
        val staged = file(cache.directory, pdfName, now)
        assertEquals(1, cache.inventory().count)
        assertTrue(staged.delete())
        assertTrue(cache.directory.delete())
        val original = file(File(temporary.root, "vault"), pdfName, now)
        val bytes = original.readBytes()
        Files.createSymbolicLink(cache.directory.toPath(), original.parentFile.toPath())
        assertEquals(RetryShareCache.Inventory(failures = 1), cache.inventory())
        assertEquals(RetryShareCache.CleanupResult(failures = 1), cache.clearAll())
        assertArrayEquals(bytes, original.readBytes())
    }

    @Test fun manualClearContinuesAfterThrownDeletionAndDoesNotCreateMissingDirectory() {
        val cache = RetryShareCache(temporary.root) { candidate ->
            if (candidate.name == jsonName) throw IOException("Synthetic failure")
            candidate.delete()
        }
        assertEquals(RetryShareCache.Inventory(), cache.inventory())
        assertEquals(RetryShareCache.CleanupResult(), cache.clearAll())
        assertFalse(cache.directory.exists())
        file(cache.directory, pdfName, now)
        file(cache.directory, jsonName, now)
        assertEquals(RetryShareCache.CleanupResult(1, 1), cache.clearAll())
    }
}
