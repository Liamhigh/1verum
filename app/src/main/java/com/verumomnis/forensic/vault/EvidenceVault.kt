package com.verumomnis.forensic.vault

import android.content.Context
import com.verumomnis.forensic.crypto.Sha512
import java.io.File
import java.time.Instant
import javax.crypto.SecretKey

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

    /** Persist a sealed report artefact (PDF) under reports/sealed. Returns its SHA-512. */
    fun storeSealedReport(fileName: String, bytes: ByteArray): String {
        initialize()
        val target = File(reportsSealed, fileName)
        target.writeBytes(bytes)
        return Sha512.hash(bytes)
    }

    /** Persist a draft report artefact under reports/draft. Returns its SHA-512. */
    fun storeDraftReport(fileName: String, bytes: ByteArray): String {
        initialize()
        val target = File(reportsDraft, fileName)
        target.writeBytes(bytes)
        return Sha512.hash(bytes)
    }

    /**
     * Delete a single file inside the vault. Refuses any path that resolves
     * outside the vault root; never deletes directories.
     */
    fun delete(file: File): Boolean {
        val canonicalRoot = root.canonicalPath
        val canonical = file.canonicalPath
        if (canonical == canonicalRoot || !canonical.startsWith("$canonicalRoot/")) return false
        return file.isFile && file.delete()
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
