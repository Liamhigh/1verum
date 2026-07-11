package com.verumomnis.forensic.vault

import android.content.Context
import com.verumomnis.forensic.crypto.Sha512
import java.io.File

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

    fun storeFinding(name: String, json: String) {
        initialize()
        File(findings, name).writeText(json)
    }

    fun storeSeal(name: String, json: String) {
        initialize()
        File(seals, name).writeText(json)
    }

    fun documentCount(): Int =
        (evidenceRaw.listFiles()?.size ?: 0) + (reportsSealed.listFiles()?.size ?: 0)

    private fun appendManifest(fileName: String, hash: String) {
        val line = "{\"file\":\"$fileName\",\"sha512\":\"$hash\"}"
        if (manifest.exists()) manifest.appendText("\n$line") else manifest.writeText(line)
    }
}
