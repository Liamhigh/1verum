package com.verumomnis.forensic

import com.verumomnis.forensic.engine.FindingsJsonEmitter
import com.verumomnis.forensic.engine.contradiction.ConfidenceCalibrator
import com.verumomnis.forensic.engine.contradiction.EngineVersion
import com.verumomnis.forensic.engine.contradiction.LogicalPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The engine version is bonded to the seal: a sealed report must state the exact
 * ruleset that produced it. Before [EngineVersion] the string was duplicated as
 * five literals and had already drifted — `LogicalPattern.detectorVersion`
 * defaulted to "v5.2.9" long after the v5.3.1c port, so the default disagreed
 * with what the engine actually stamped.
 *
 * These tests fail if any copy is reintroduced.
 */
class EngineVersionTest {

    @Test
    fun `tagged form is the bare version with a v prefix`() {
        assertEquals("v${EngineVersion.VALUE}", EngineVersion.TAGGED)
    }

    @Test
    fun `patterns stamp the engine version by default`() {
        val pattern = LogicalPattern(
            patternType = "TEST",
            patternDescription = "test",
            supportingFacts = emptyList(),
            contradictionScore = 0.5
        )
        assertEquals(EngineVersion.TAGGED, pattern.detectorVersion)
    }

    @Test
    fun `findings JSON reports the same engine version`() {
        assertEquals(EngineVersion.VALUE, FindingsJsonEmitter.ENGINE_VERSION)
    }

    @Test
    fun `calibration audit report states the same engine version`() {
        assertEquals(EngineVersion.TAGGED, ConfidenceCalibrator.reportCalibration()["engineVersion"])
    }

    @Test
    fun `no engine-version literal survives in main sources`() {
        // The regression this guards is duplication, so assert on the source
        // itself: only EngineVersion.kt may contain the version string.
        val srcRoot = java.io.File("src/main/java/com/verumomnis/forensic")
            .takeIf { it.isDirectory } ?: java.io.File("app/src/main/java/com/verumomnis/forensic")
        assertTrue("could not locate main sources at ${srcRoot.absolutePath}", srcRoot.isDirectory)
        val literal = Regex("\"v?${Regex.escape(EngineVersion.VALUE)}\"")
        // Comments legitimately mention the version (e.g. worked examples of the
        // version parser), so scan code only — otherwise prose fails the build.
        val comments = Regex("""//[^\n]*|/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val offenders = srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "EngineVersion.kt" }
            .filter { literal.containsMatchIn(it.readText().replace(comments, "")) }
            .map { it.name }
            .toList()
        assertTrue(
            "engine version hard-coded outside EngineVersion.kt: $offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun `version is not left at a superseded value`() {
        // Guards the specific regression: the default sat at v5.2.9 while the
        // engine had already moved to v5.3.1c.
        assertTrue(
            "engine version must not regress to the superseded v5.2.9",
            EngineVersion.VALUE != "5.2.9" && EngineVersion.TAGGED != "v5.2.9"
        )
    }
}
