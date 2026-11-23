package com.verumomnis.forensic.pdf

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Temporary retry attachments get a 24-hour grace period from staging so opening
 * a sharesheet or starting another share does not immediately invalidate them.
 * Expired copies are removed opportunistically on app startup and before a new
 * retry share, not by a timer while the recipient is opening a recent share.
 * Recipients should import their own copies; this is not permanent storage.
 *
 * Only direct, app-named files in cache/retry-share are eligible. Never traverse
 * directories or symbolic links, and never touch saved reports or vault evidence.
 */
internal class RetryShareCache(
    private val cacheRoot: File,
    private val deleteFile: (File) -> Boolean = { it.delete() }
) {
    companion object {
        val RETENTION_MILLIS: Long = TimeUnit.HOURS.toMillis(24)
        private val STAGED_NAME = Regex(
            "VerumOmnis_[A-Za-z0-9._-]+_retry_[A-Za-z0-9._-]+_" +
                "(?:report_[0-9]+\\.pdf|findings_[0-9]+\\.json)"
        )
    }

    val directory: File get() = File(cacheRoot, "retry-share")

    data class CleanupResult(val deleted: Int = 0, val failures: Int = 0)

    /** Cleanup is best effort: one inaccessible file must not block a new share. */
    fun cleanExpired(nowMillis: Long = System.currentTimeMillis()): CleanupResult {
        // Fail closed for invalid clocks; do not let subtraction overflow.
        if (nowMillis < RETENTION_MILLIS) return CleanupResult()
        val cutoff = nowMillis - RETENTION_MILLIS
        var deleted = 0
        var failures = 0
        try {
            val expectedDirectory = File(cacheRoot.canonicalFile, "retry-share")
            if (Files.isSymbolicLink(directory.toPath()) ||
                directory.canonicalFile != expectedDirectory || !directory.isDirectory
            ) return CleanupResult()
            val files = directory.listFiles() ?: return CleanupResult(failures = 1)
            for (file in files) {
                try {
                    if (!STAGED_NAME.matches(file.name) || Files.isSymbolicLink(file.toPath()) ||
                        !file.isFile || file.canonicalFile.parentFile != expectedDirectory
                    ) continue
                    val stagedAt = file.lastModified()
                    // Unknown and future dates are deliberately retained.
                    if (stagedAt <= 0 || stagedAt > cutoff) continue
                    if (deleteFile(file)) deleted++ else failures++
                } catch (_: Exception) {
                    failures++
                }
            }
        } catch (_: Exception) {
            failures++
        }
        return CleanupResult(deleted, failures)
    }
}
