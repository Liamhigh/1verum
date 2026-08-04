package com.verumomnis.forensic.ui

import java.io.File

/**
 * Vault organisation for the redesigned Evidence Vault (spec §3.2, §4.1).
 *
 * Kept out of the screen so the grouping rules are testable without Compose.
 */
enum class VaultFolder(val label: String) {
    ALL("All"),
    RAW("Raw"),
    PROCESSED("Processed"),
    SEALED("Sealed")
}

/**
 * One scan set: the artifacts a single scan produced, shown as one row.
 *
 * A scan yields a sealed original, a sealed report and a findings JSON. Listing
 * those as three loose files makes a vault of ten scans look like thirty
 * unrelated documents, and hides which report belongs to which evidence. They
 * are grouped here by seal reference so the vault reads as a case file.
 */
data class ScanSet(
    val id: String,
    val name: String,
    val folder: VaultFolder,
    val artifacts: List<VaultArtifact>,
    /** Contradictions found, when this set includes a report. */
    val contradictionCount: Int = 0,
    val timestamp: String = ""
) {
    val flagged: Boolean get() = contradictionCount > 0
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"
    val sizeBytes: Long get() = artifacts.sumOf { it.sizeBytes }
}

data class VaultArtifact(
    val fileName: String,
    val file: File,
    val kind: Kind,
    val sizeBytes: Long
) {
    enum class Kind(val label: String) {
        ORIGINAL("Sealed original"),
        REPORT("Forensic report"),
        FINDINGS("Findings JSON"),
        OTHER("Document")
    }
}

object VaultGrouping {

    /**
     * Groups loose vault files into scan sets.
     *
     * Artifacts are matched on their base name — a scan writes
     * `matter.pdf`, `matter_report.pdf` and `matter_findings.json`, so stripping
     * the known suffixes recovers the set. Anything unrecognised becomes a set of
     * one rather than being dropped: an artifact that is in the vault must be
     * visible in the vault, even if this code cannot classify it.
     */
    fun group(
        raw: List<File>,
        processed: List<File>,
        sealed: List<File>,
        contradictionsByRef: Map<String, Int> = emptyMap()
    ): List<ScanSet> {
        val sets = linkedMapOf<String, MutableList<VaultArtifact>>()
        val folderOf = mutableMapOf<String, VaultFolder>()

        fun add(file: File, kind: VaultArtifact.Kind, folder: VaultFolder) {
            val key = baseName(file.name)
            sets.getOrPut(key) { mutableListOf() } +=
                VaultArtifact(file.name, file, kind, file.length())
            // Sealed wins over processed wins over raw: a set is filed under the
            // most-advanced state any of its artifacts has reached.
            val current = folderOf[key]
            if (current == null || folder.ordinal > current.ordinal) folderOf[key] = folder
        }

        raw.forEach { add(it, kindOf(it.name), VaultFolder.RAW) }
        processed.forEach { add(it, kindOf(it.name), VaultFolder.PROCESSED) }
        sealed.forEach { add(it, kindOf(it.name), VaultFolder.SEALED) }

        return sets.map { (key, artifacts) ->
            ScanSet(
                id = key,
                name = key,
                folder = folderOf[key] ?: VaultFolder.RAW,
                artifacts = artifacts.sortedBy { it.kind.ordinal },
                contradictionCount = contradictionsByRef[key] ?: 0
            )
        }
    }

    /** Strips the suffixes a scan appends, so all three artifacts share a key. */
    fun baseName(fileName: String): String {
        val stem = fileName.substringBeforeLast('.', fileName)
        return stem
            .removeSuffix("_findings")
            .removeSuffix("_report")
            .removeSuffix("-sealed")
            .removeSuffix("_sealed")
            .trim()
    }

    private fun kindOf(fileName: String): VaultArtifact.Kind {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".json") && lower.contains("finding") -> VaultArtifact.Kind.FINDINGS
            lower.contains("report") -> VaultArtifact.Kind.REPORT
            lower.endsWith(".pdf") || lower.endsWith(".txt") -> VaultArtifact.Kind.ORIGINAL
            else -> VaultArtifact.Kind.OTHER
        }
    }
}
