package com.verumomnis.forensic.vault

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The persistent case record — the conversation itself, kept in the vault.
 *
 * Every other assistant forgets between sessions. This one must not: the vault
 * is meant to become a person's whole legal record, so that months into a matter
 * they can drop in the other side's latest letter and get an answer from
 * something that still knows the case. A conversation that resets on every
 * launch cannot do that, however good the evidence store is.
 *
 * Stored encrypted (AES-256-GCM, hardware-backed key) under the vault's
 * chat_sessions directory, alongside the evidence it discusses. It never leaves
 * the device and is never shared.
 */
object CaseMemory {

    private const val TAG = "CaseMemory"

    /** Default session name. One case per device today; the name allows more later. */
    const val DEFAULT_SESSION = "case_memory"

    /**
     * Cap on retained turns. Long enough that a matter running for months keeps
     * its thread, bounded so the file cannot grow without limit on a phone.
     * Oldest turns are dropped first; the vaulted evidence they refer to is
     * never touched.
     */
    const val MAX_TURNS = 500

    @Serializable
    data class Turn(val author: String, val text: String, val fromUser: Boolean)

    @Serializable
    data class Session(
        val version: Int = 1,
        val turns: List<Turn> = emptyList()
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Persists [turns], newest-trimmed to [MAX_TURNS].
     *
     * Failures are swallowed deliberately: losing the ability to save history
     * must never take down a forensic scan or block a seal. The evidence is the
     * thing that matters; the transcript is a convenience on top of it.
     */
    fun save(vault: EvidenceVault, turns: List<Turn>, sessionName: String = DEFAULT_SESSION): Boolean =
        runCatching {
            val trimmed = if (turns.size > MAX_TURNS) turns.takeLast(MAX_TURNS) else turns
            vault.storeChatSession(
                sessionName,
                json.encodeToString(Session(turns = trimmed)),
                vault.defaultMasterKey()
            )
            true
        }.onFailure {
            // Swallowed so a failure here can never take down a scan or block a
            // seal — but logged, because a silently vanishing case record is the
            // one failure the user would never notice and would most regret.
            android.util.Log.w(TAG, "case memory save failed", it)
        }.getOrDefault(false)

    /**
     * Restores a saved session, or an empty list if there is none.
     *
     * A decryption failure also yields empty rather than throwing — a corrupt or
     * key-rotated transcript should cost the user their history, not their access
     * to the app and the evidence inside it.
     */
    fun load(vault: EvidenceVault, sessionName: String = DEFAULT_SESSION): List<Turn> =
        runCatching {
            json.decodeFromString<Session>(
                vault.readChatSession(sessionName, vault.defaultMasterKey())
            ).turns
        }.getOrDefault(emptyList())
}
