package com.verumomnis.forensic

import com.verumomnis.forensic.ui.VaultArtifact
import com.verumomnis.forensic.ui.VaultFolder
import com.verumomnis.forensic.ui.VaultGrouping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Scan-set grouping (spec §4.1).
 *
 * A scan produces a sealed original, a report and a findings JSON. Listing those
 * as three loose files makes ten scans look like thirty unrelated documents and
 * hides which report belongs to which evidence, so they must collapse to one row.
 */
class VaultGroupingTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(name: String, bytes: Int = 10): File =
        temp.newFile(name).apply { writeBytes(ByteArray(bytes)) }

    @Test
    fun `the three artifacts of one scan collapse into a single set`() {
        val sets = VaultGrouping.group(
            raw = listOf(file("allfuels.pdf")),
            processed = emptyList(),
            sealed = listOf(file("allfuels_report.pdf"), file("allfuels_findings.json"))
        )
        assertEquals(1, sets.size)
        assertEquals(3, sets[0].artifacts.size)
        assertEquals(
            setOf(
                VaultArtifact.Kind.ORIGINAL,
                VaultArtifact.Kind.REPORT,
                VaultArtifact.Kind.FINDINGS
            ),
            sets[0].artifacts.map { it.kind }.toSet()
        )
    }

    @Test
    fun `a set is filed under the most advanced state it has reached`() {
        // Raw + sealed for the same case must file as Sealed, not Raw.
        val sets = VaultGrouping.group(
            raw = listOf(file("matter.pdf")),
            processed = emptyList(),
            sealed = listOf(file("matter_report.pdf"))
        )
        assertEquals(VaultFolder.SEALED, sets.single().folder)
    }

    @Test
    fun `an unrecognised file still appears rather than being dropped`() {
        // Anything in the vault must be visible in the vault. Silently hiding an
        // artifact the grouper cannot classify would lose evidence from view.
        val sets = VaultGrouping.group(
            raw = listOf(file("odd-artifact.xyz")),
            processed = emptyList(),
            sealed = emptyList()
        )
        assertEquals(1, sets.size)
        assertEquals("odd-artifact", sets.single().name)
    }

    @Test
    fun `separate cases do not merge`() {
        val sets = VaultGrouping.group(
            raw = listOf(file("caseA.pdf"), file("caseB.pdf")),
            processed = emptyList(),
            sealed = listOf(file("caseA_report.pdf"))
        )
        assertEquals(2, sets.size)
        assertEquals(setOf("caseA", "caseB"), sets.map { it.name }.toSet())
    }

    @Test
    fun `a set reports its combined size and flagged state`() {
        val sets = VaultGrouping.group(
            raw = listOf(file("case.pdf", 100)),
            processed = emptyList(),
            sealed = listOf(file("case_report.pdf", 50)),
            contradictionsByRef = mapOf("case" to 4)
        )
        val set = sets.single()
        assertEquals(150L, set.sizeBytes)
        assertTrue(set.flagged)
        assertEquals(4, set.contradictionCount)
    }

    @Test
    fun `base name strips the suffixes a scan appends`() {
        assertEquals("matter", VaultGrouping.baseName("matter_report.pdf"))
        assertEquals("matter", VaultGrouping.baseName("matter_findings.json"))
        assertEquals("matter", VaultGrouping.baseName("matter-sealed.pdf"))
        assertEquals("matter", VaultGrouping.baseName("matter.pdf"))
    }
}
