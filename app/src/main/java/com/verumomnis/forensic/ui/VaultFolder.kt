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
    val timestamp: String = "",
    val sourceSha512: String? = null,
    val linkNotes: List<String> = emptyList(),
    /** Absence of a linked result is not a successful clean scan. */
    val analysisKnown: Boolean = contradictionCount > 0
) {
    val flagged: Boolean get() = contradictionCount > 0
    val statusLabel: String get() = when {
        flagged -> "$contradictionCount potential contradiction indicator(s)"
        analysisKnown -> "Analysed · no contradiction indicators flagged"
        folder == VaultFolder.RAW -> "Raw evidence · not assessed here"
        folder == VaultFolder.SEALED -> "Sealed · analysis status unknown"
        else -> "Stored · analysis status unknown"
    }
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"
    val sizeBytes: Long get() = artifacts.sumOf { it.sizeBytes }
}

data class VaultArtifact(
    val fileName: String,
    val file: File,
    val kind: Kind,
    val sizeBytes: Long
) {
    val available: Boolean get() = file.isFile && file.length() > 0
    enum class Kind(val label: String) {
        ORIGINAL("Original evidence"),
        REPORT("Forensic report"),
        FINDINGS("Findings JSON"),
        OTHER("Document"),
        SEALED_DOCUMENT("Sealed PDF"),
        TIMESTAMP_PROOF("Timestamp proof")
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
        contradictionsByRef: Map<String, Int> = emptyMap(),
        links: List<com.verumomnis.forensic.model.LinkedDocumentRecord> = emptyList(),
        resolvePath: (String) -> File = { File(it) }
    ): List<ScanSet> {
        val linkedPaths = mutableSetOf<String>()
        val linkedSets = links.map { link ->
            fun artifact(path: String?, kind: VaultArtifact.Kind): VaultArtifact? = path?.let {
                val file = resolvePath(it)
                linkedPaths += file.canonicalPath
                VaultArtifact(file.name, file, kind, file.length())
            }
            val artifacts = listOfNotNull(
                artifact(link.sourcePath, VaultArtifact.Kind.ORIGINAL),
                artifact(link.sealedPdfPath, VaultArtifact.Kind.SEALED_DOCUMENT),
                artifact(link.reportPdfPath, VaultArtifact.Kind.REPORT),
                artifact(link.timestampProofPath, VaultArtifact.Kind.TIMESTAMP_PROOF)
            )
            ScanSet(
                id = "linked:${link.id}", name = link.sourceName,
                folder = if (link.sealedPdfPath != null || link.reportPdfPath != null) VaultFolder.SEALED else VaultFolder.RAW,
                artifacts = artifacts, timestamp = link.createdAt, sourceSha512 = link.sourceSha512,
                linkNotes = buildList {
                    if (link.sealedPdfPath == null) add("Sealed PDF not created")
                    if (link.reportPdfPath == null) add(
                        if (link.forensicRequested) "Forensic report PDF not saved" else "Forensic report not requested"
                    )
                    if (link.timestampProofPath == null) add("Timestamp proof not saved")
                }
            )
        }
        val sets = linkedMapOf<String, MutableList<VaultArtifact>>()
        val folderOf = mutableMapOf<String, VaultFolder>()

        fun add(file: File, kind: VaultArtifact.Kind, folder: VaultFolder) {
            if (file.canonicalPath in linkedPaths) return
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

        return linkedSets + sets.map { (key, artifacts) ->
            ScanSet(
                id = key,
                name = key,
                folder = folderOf[key] ?: VaultFolder.RAW,
                artifacts = artifacts.sortedBy { it.kind.ordinal },
                linkNotes = listOf("Legacy filename grouping · document relationships were not recorded"),
                contradictionCount = contradictionsByRef[key] ?: 0,
                analysisKnown = contradictionsByRef.containsKey(key)
            )
        }
    }

    /** Strips the suffixes a scan appends, so all three artifacts share a key. */
    fun baseName(fileName: String): String {
        val stem = fileName.substringBeforeLast('.', fileName)
        return stem
            .removeSuffix("_findings")
            .replace(Regex("_report(?:_\\d+)?$"), "")
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
