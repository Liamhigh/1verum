package com.verumomnis.forensic.security

import android.content.Context
import com.verumomnis.forensic.crypto.Sha512
import com.verumomnis.forensic.model.GuardianAssessment
import com.verumomnis.forensic.model.GuardianViolation
import com.verumomnis.forensic.model.GuardianViolationType
import com.verumomnis.forensic.model.SilenceLedgerEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * Append-only ledger of coercion / weaponization / constitutional-violation attempts.
 *
 * Every entry is chained to the previous entry's hash (SHA-512) so the log cannot
 * be silently modified without detection. The ledger is stored as newline-delimited
 * JSON in the app's private files directory.
 */
class SilenceLedger(internal val logFile: File) {

    constructor(context: Context) : this(File(context.filesDir, "silence_ledger.jsonl"))

    private val json = Json { prettyPrint = false }

    /** Append a raw violation, converting it into a chained ledger entry. */
    fun append(
        violation: GuardianViolation,
        now: Instant = Instant.now()
    ): SilenceLedgerEntry = append(
        type = violation.type,
        description = violation.description,
        evidenceId = violation.evidenceId,
        trigger = violation.trigger,
        now = now
    )

    /** Append all violations from a B9 assessment. */
    fun appendAll(assessment: GuardianAssessment, now: Instant = Instant.now()): List<SilenceLedgerEntry> =
        assessment.violations.map { append(it, now) }

    /** Append a new entry, chaining its hash to the previous entry. */
    fun append(
        type: GuardianViolationType,
        description: String,
        evidenceId: String = "",
        trigger: String = "",
        now: Instant = Instant.now()
    ): SilenceLedgerEntry {
        val previous = readEntries().lastOrNull()
        val sequence = (previous?.sequence ?: 0L) + 1
        val previousHash = previous?.entryHash ?: "0".repeat(128)
        val payload = "$sequence|${now.toEpochMilli()}|$type|$description|$evidenceId|$trigger|$previousHash"
        val entryHash = Sha512.hash(payload.toByteArray(Charsets.UTF_8))
        val entry = SilenceLedgerEntry(
            sequence = sequence,
            timestamp = now.toString(),
            type = type,
            description = description,
            evidenceId = evidenceId,
            trigger = trigger,
            previousHash = previousHash,
            entryHash = entryHash
        )
        logFile.appendText(json.encodeToString(entry) + "\n", Charsets.UTF_8)
        return entry
    }

    /** Read all ledger entries in order. */
    fun readEntries(): List<SilenceLedgerEntry> {
        if (!logFile.exists()) return emptyList()
        return logFile.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { json.decodeFromString<SilenceLedgerEntry>(it) }.getOrNull() }
    }

    /** Verify the hash chain. Returns the first invalid sequence or -1 if valid. */
    fun verifyChain(): Long {
        val entries = readEntries()
        var expectedPrevious = "0".repeat(128)
        entries.forEach { entry ->
            val payload = "${entry.sequence}|${Instant.parse(entry.timestamp).toEpochMilli()}|" +
                "${entry.type}|${entry.description}|${entry.evidenceId}|${entry.trigger}|${entry.previousHash}"
            val recomputed = Sha512.hash(payload.toByteArray(Charsets.UTF_8))
            if (entry.previousHash != expectedPrevious || entry.entryHash != recomputed) {
                return entry.sequence
            }
            expectedPrevious = entry.entryHash
        }
        return -1
    }

    fun clear() {
        logFile.delete()
    }
}
