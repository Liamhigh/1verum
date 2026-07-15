package com.verumomnis.forensic

import com.verumomnis.forensic.model.GuardianViolation
import com.verumomnis.forensic.model.GuardianViolationType
import com.verumomnis.forensic.model.Severity
import com.verumomnis.forensic.model.SilenceLedgerEntry
import com.verumomnis.forensic.security.SilenceLedger
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class SilenceLedgerTest {

    @get:org.junit.Rule
    val tmp = TemporaryFolder()

    private lateinit var ledger: SilenceLedger

    @Before
    fun setup() {
        ledger = SilenceLedger(tmp.newFile("ledger.jsonl"))
    }

    @Test
    fun `append creates chained entries`() {
        val v1 = GuardianViolation(
            GuardianViolationType.COERCION_ATTEMPT, Severity.VERY_HIGH,
            "Threat to keep quiet", "ev-1", "keep quiet", Instant.now().toString()
        )
        val v2 = GuardianViolation(
            GuardianViolationType.ARTICLE_X_WEAPONIZATION, Severity.CRITICAL,
            "Kill chain detected", "ev-2", "kill chain", Instant.now().toString()
        )
        ledger.append(v1)
        ledger.append(v2)

        val entries = ledger.readEntries()
        assertEquals(2, entries.size)
        assertEquals(1L, entries[0].sequence)
        assertEquals(2L, entries[1].sequence)
        assertEquals(entries[0].entryHash, entries[1].previousHash)
    }

    @Test
    fun `verifyChain detects tampering`() {
        ledger.append(GuardianViolationType.COERCION_ATTEMPT, "Coercion", now = Instant.now())
        val entries = ledger.readEntries().toMutableList()
        val tampered = entries[0].copy(description = "Tampered")
        ledger.clear()
        ledger.logFile.writeText(
            Json.encodeToString(SilenceLedgerEntry.serializer(), tampered) + "\n",
            Charsets.UTF_8
        )
        assertEquals(1L, ledger.verifyChain())
    }

    @Test
    fun `verifyChain passes for intact ledger`() {
        ledger.append(GuardianViolationType.COERCION_ATTEMPT, "Coercion", now = Instant.now())
        ledger.append(GuardianViolationType.ARTICLE_X_WEAPONIZATION, "Weaponization", now = Instant.now())
        assertEquals(-1L, ledger.verifyChain())
    }
}
