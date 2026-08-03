package com.verumomnis.forensic

import com.verumomnis.forensic.vault.CaseMemory
import com.verumomnis.forensic.vault.EvidenceVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The case record has to survive a restart.
 *
 * This is the difference between a chat window and a legal record: months into a
 * matter, the user drops in opposing counsel's latest letter and expects an
 * answer from something that still knows the case. The encrypted storage existed
 * in EvidenceVault for a long time with no caller, so the transcript was silently
 * reset on every launch. These tests fail if that regresses.
 */
class CaseMemoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun vault() = EvidenceVault(temp.newFolder()).also { it.initialize() }

    @Test
    fun `a saved session is restored verbatim`() {
        val v = vault()
        val turns = listOf(
            CaseMemory.Turn("You", "Here is the termination letter.", fromUser = true),
            CaseMemory.Turn("Verum Omnis", "Clause 3.2.3 conflicts with the title deed.", fromUser = false)
        )
        assertTrue(CaseMemory.save(v, turns))
        assertEquals(turns, CaseMemory.load(v))
    }

    @Test
    fun `an empty vault yields no history rather than failing`() {
        // A first launch must not error — it simply has nothing to restore.
        assertEquals(emptyList<CaseMemory.Turn>(), CaseMemory.load(vault()))
    }

    @Test
    fun `history is bounded so the record cannot grow without limit`() {
        val v = vault()
        val many = (1..CaseMemory.MAX_TURNS + 120).map {
            CaseMemory.Turn("You", "turn $it", fromUser = true)
        }
        CaseMemory.save(v, many)
        val loaded = CaseMemory.load(v)
        assertEquals(CaseMemory.MAX_TURNS, loaded.size)
        // The oldest are dropped, so the most recent context always survives.
        assertEquals("turn ${CaseMemory.MAX_TURNS + 120}", loaded.last().text)
    }

    @Test
    fun `a corrupt transcript costs history, never access`() {
        val v = vault()
        CaseMemory.save(v, listOf(CaseMemory.Turn("You", "hello", fromUser = true)))
        // Simulate corruption / a rotated key: every .enc in the session dir is clobbered.
        temp.root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".enc") }
            .forEach { it.writeBytes(byteArrayOf(0, 1, 2, 3)) }
        assertEquals(emptyList<CaseMemory.Turn>(), CaseMemory.load(v))
    }

    @Test
    fun `round trip preserves speaker attribution`() {
        // Who said what is evidentially relevant — the user's own words must never
        // come back attributed to the AI.
        val v = vault()
        val turns = listOf(
            CaseMemory.Turn("You", "I never signed it.", fromUser = true),
            CaseMemory.Turn("Verum Omnis", "The MOU shows no countersignature.", fromUser = false)
        )
        CaseMemory.save(v, turns)
        val loaded = CaseMemory.load(v)
        assertTrue(loaded[0].fromUser)
        assertTrue(!loaded[1].fromUser)
        assertEquals("You", loaded[0].author)
    }
}
