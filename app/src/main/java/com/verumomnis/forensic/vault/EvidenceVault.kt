package com.verumomnis.forensic.vault

import android.content.Context
import com.verumomnis.forensic.crypto.Sha512
import com.verumomnis.forensic.model.ForensicReport
import java.io.File
import java.time.Instant
import javax.crypto.SecretKey
import kotlinx.serialization.json.Json

/**
 * Evidence Vault (Part VII). Local storage following the specified directory
 * structure. Files are written under the app's private storage and every write is
 * accompanied by a SHA-512 integrity entry.
 *
 * Note: AES-256-GCM at-rest encryption and SQLCipher are specified for production;
 * this implementation lays out the vault structure and integrity manifest that
 * those layers wrap.
 */
class EvidenceVault(private val root: File) {

    constructor(context: Context) : this(File(context.filesDir, "vault"))

    val evidenceRaw = File(root, "evidence/raw")
    val evidenceProcessed = File(root, "evidence/processed")
    val findings = File(root, "findings")
    val reportsSealed = File(root, "reports/sealed")
    val reportsDraft = File(root, "reports/draft")
    val seals = File(root, "seals")
    val chatSessions = File(root, "chat_sessions")
    val research = File(root, "research")
    val config = File(root, "config")

    private val manifest = File(findings, "integrity_manifest.json")

    fun initialize() {
        listOf(
            evidenceRaw, evidenceProcessed, findings, reportsSealed, reportsDraft,
            seals, chatSessions, research, config
        ).forEach { it.mkdirs() }
    }

    fun storeEvidence(fileName: String, bytes: ByteArray): String {
        initialize()
        val target = File(evidenceRaw, fileName)
        target.writeBytes(bytes)
        val hash = Sha512.hash(bytes)
        appendManifest(fileName, hash)
        return hash
    }

    /**
     * Streams [input] into the vault, fingerprinting it in the same pass.
     *
     * The ByteArray overload above is fine for small artifacts but costs a full
     * in-memory copy, which is untenable for the multi-hundred-megabyte case
     * bundles this app is built for. Here the peak cost is one 64 KB buffer no
     * matter how large the evidence is.
     *
     * The original bytes are preserved unaltered, as chain of custody requires —
     * streaming changes only how they get to disk, never what is stored.
     *
     * @return the stored file and its SHA-512.
     */
    fun storeEvidenceStreaming(fileName: String, input: java.io.InputStream): Pair<File, String> {
        initialize()
        val target = File(evidenceRaw, fileName)
        val (hash, _) = input.use { source ->
            target.outputStream().use { sink -> Sha512.copyAndHash(source, sink) }
        }
        appendManifest(fileName, hash)
        return target to hash
    }

    fun storeFinding(name: String, json: String) {
        initialize()
        File(findings, name).writeText(json)
    }

    fun storeSeal(name: String, json: String) {
        initialize()
        File(seals, name).writeText(json)
    }

    fun storeOtsProof(shortcode: String, proofBase64: String) {
        initialize()
        File(seals, "seal_$shortcode.ots").writeText(proofBase64)
    }

    fun loadOtsProof(shortcode: String): String? {
        val file = File(seals, "seal_$shortcode.ots")
        return if (file.exists()) file.readText() else null
    }

    /**
     * Shortcodes of every stored `.ots` proof.
     *
     * Lets the anchor upgrader find seals still awaiting Bitcoin confirmation
     * without the caller having to remember what it sealed and when.
     */
    fun listOtsShortcodes(): List<String> =
        seals.listFiles { f -> f.isFile && f.name.startsWith("seal_") && f.extension == "ots" }
            ?.map { it.nameWithoutExtension.removePrefix("seal_") }
            ?.sorted()
            .orEmpty()

    /** Persists a generated report's JSON snapshot so it can later be loaded for comparison. */
    fun storeReport(report: ForensicReport) {
        initialize()
        val safeName = report.reference.replace(Regex("[^A-Za-z0-9._-]"), "_")
        File(reportsSealed, "$safeName.json").writeText(Json.encodeToString(ForensicReport.serializer(), report))
    }

    /** All previously generated reports, most recent first, for the Report Comparison screen. */
    fun listReports(): List<ForensicReport> {
        val files = reportsSealed.listFiles { f -> f.extension == "json" } ?: return emptyList()
        return files.mapNotNull { file ->
            try {
                Json.decodeFromString(ForensicReport.serializer(), file.readText())
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.createdAt }
    }

    fun storeConfig(name: String, json: String) {
        initialize()
        File(config, name).writeText(json)
    }

    fun loadConfig(name: String): String? {
        val file = File(config, name)
        return if (file.exists()) file.readText() else null
    }

    data class IntegrityEntry(val fileName: String, val sha512: String, val timestamp: String)

    /** Default hardware-backed (or test fallback) master key for vault encryption. */
    fun defaultMasterKey(): SecretKey = VaultKeyStore.getOrCreateMasterKey()

    /** Encrypted chat session at rest (AES-256-GCM), stored under chat_sessions as .json.enc. */
    fun storeChatSession(name: String, json: String, key: SecretKey) {
        initialize()
        val fileName = if (name.endsWith(".enc")) name else "$name.enc"
        File(chatSessions, fileName).writeBytes(VaultEncryption.encrypt(json.toByteArray(), key))
    }

    fun readChatSession(name: String, key: SecretKey): String {
        val fileName = if (name.endsWith(".enc")) name else "$name.enc"
        return String(VaultEncryption.decrypt(File(chatSessions, fileName).readBytes(), key))
    }

    /**
     * Deletes one artifact from the device by file name.
     *
     * Deliberately removes the on-device copy only. Any OpenTimestamps anchor
     * already submitted for it stays on the Bitcoin blockchain and any sealed
     * copy the user has shared elsewhere is untouched — deleting here reclaims
     * privacy and space, it does not and cannot retract a seal.
     *
     * The integrity manifest entry is left in place on purpose: it records that
     * an artifact with that hash was once vaulted, which is chain-of-custody
     * history. Rewriting history to hide a deletion is precisely what a forensic
     * tool must not do.
     *
     * @return true if a file was found and removed.
     */
    fun deleteEvidence(fileName: String): Boolean {
        val candidates = listOf(evidenceRaw, evidenceProcessed, reportsSealed, reportsDraft, findings, seals)
        var removed = false
        for (dir in candidates) {
            val f = File(dir, fileName)
            if (f.exists() && f.isFile) removed = f.delete() || removed
        }
        return removed
    }

    /**
     * Removes every stored artifact from this device.
     *
     * Clears evidence, reports, findings, seals and research. The integrity
     * manifest and the encrypted case memory are preserved for the same reason
     * as above: the record that evidence existed, and the conversation about it,
     * are themselves part of the account of what happened.
     *
     * @return the number of files removed.
     */
    fun emptyVault(): Int {
        var count = 0
        listOf(evidenceRaw, evidenceProcessed, reportsSealed, reportsDraft, seals, research).forEach { dir ->
            dir.listFiles()?.forEach { f -> if (f.isFile && f.delete()) count++ }
        }
        // findings holds the manifest alongside per-scan output; keep the manifest.
        findings.listFiles()?.forEach { f ->
            if (f.isFile && f.name != manifest.name && f.delete()) count++
        }
        return count
    }

    fun documentCount(): Int =
        (evidenceRaw.listFiles()?.size ?: 0) + (reportsSealed.listFiles()?.size ?: 0)

    fun integrityManifest(): List<IntegrityEntry> {
        if (!manifest.exists()) return emptyList()
        return manifest.readLines().filter { it.isNotBlank() }.map { line ->
            val file = line.substringAfter("\"file\":\"").substringBefore("\"")
            val hash = line.substringAfter("\"sha512\":\"").substringBefore("\"")
            val ts = line.substringAfter("\"timestamp\":\"", "").substringBefore("\"", "")
            IntegrityEntry(file, hash, ts)
        }
    }

    /** Re-compute SHA-512 for every entry in the manifest and report mismatches. */
    fun verifyIntegrity(): List<String> {
        val mismatches = mutableListOf<String>()
        integrityManifest().forEach { entry ->
            val file = File(evidenceRaw, entry.fileName)
            if (!file.exists()) {
                mismatches += "MISSING: ${entry.fileName}"
            } else {
                val current = Sha512.hash(file.readBytes())
                if (current != entry.sha512) mismatches += "TAMPERED: ${entry.fileName}"
            }
        }
        return mismatches
    }

    private fun appendManifest(fileName: String, hash: String) {
        val line = "{\"file\":\"$fileName\",\"sha512\":\"$hash\",\"timestamp\":\"${Instant.now()}\"}"
        if (manifest.exists()) manifest.appendText("\n$line") else manifest.writeText(line)
    }
}
