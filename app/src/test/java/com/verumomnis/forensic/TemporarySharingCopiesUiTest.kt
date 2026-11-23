package com.verumomnis.forensic

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.verumomnis.forensic.pdf.RetryShareCache
import com.verumomnis.forensic.ui.TemporarySharingCopiesControl
import com.verumomnis.forensic.ui.theme.VerumOmnisTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TemporarySharingCopiesUiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()

    private fun staged(cache: RetryShareCache, suffix: String): File =
        File(cache.directory.apply { mkdirs() }, "VerumOmnis_source_retry_attempt_${suffix}").apply {
            writeText("synthetic")
        }

    @Test fun confirmationCancelAndClearPreserveVaultedOriginals() {
        val cache = RetryShareCache(File(temporary.root, "cache"))
        val pdf = staged(cache, "report_123.pdf")
        val json = staged(cache, "findings_456.json")
        val originals = listOf("evidence/raw/source.pdf", "reports/report.pdf", "findings/findings.json")
            .map { File(temporary.root, "vault/$it").apply { parentFile!!.mkdirs(); writeText("original $it") } }
        val before = originals.associateWith { it.readBytes() }
        compose.setContent { VerumOmnisTheme { TemporarySharingCopiesControl(cache = cache) } }
        compose.waitUntil { compose.onAllNodesWithText("2 temporary sharing copies · 18 bytes total")
            .fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("CLEAR TEMPORARY SHARING COPIES").performClick()
        compose.onNodeWithText("Shares that receiving apps have not yet imported may stop opening.",
            substring = true).assertExists()
        assertTrue(pdf.exists())
        assertTrue(json.exists())
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Clear copies now").assertDoesNotExist()
        assertTrue(pdf.exists())
        assertTrue(json.exists())
        compose.onNodeWithText("CLEAR TEMPORARY SHARING COPIES").performClick()
        compose.onNodeWithText("Clear copies now").performClick()
        compose.waitUntil { !pdf.exists() && !json.exists() }
        compose.waitUntil { compose.onAllNodesWithText("0 temporary sharing copies · 0 bytes total")
            .fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("CLEAR TEMPORARY SHARING COPIES").assertIsNotEnabled()
        compose.onNodeWithText("Removed 2 temporary copies.", substring = true).assertExists()
        before.forEach { (file, bytes) -> assertArrayEquals(bytes, file.readBytes()) }
    }

    @Test fun partialFailureIsVisibleAndOtherActionsStayAvailable() {
        val cache = RetryShareCache(File(temporary.root, "cache")) {
            if (it.extension == "json") false else it.delete()
        }
        staged(cache, "report_123.pdf")
        val json = staged(cache, "findings_456.json")
        var otherActionUsed = false
        compose.setContent {
            VerumOmnisTheme {
                Column {
                    TemporarySharingCopiesControl(cache = cache)
                    TextButton(onClick = { otherActionUsed = true }) { Text("Review saved evidence") }
                }
            }
        }
        compose.waitUntil { compose.onAllNodesWithText("2 temporary sharing copies", substring = true)
            .fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("CLEAR TEMPORARY SHARING COPIES").performClick()
        compose.onNodeWithText("Clear copies now").performClick()
        compose.waitUntil { compose.onAllNodesWithText("Removed 1 temporary copies; 1 cleanup operation(s) failed.",
            substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 temporary sharing copies · 9 bytes total").assertExists()
        compose.onNodeWithText("CLEAR TEMPORARY SHARING COPIES").assertIsEnabled()
        compose.onNodeWithText("Review saved evidence").performClick()
        assertTrue(otherActionUsed)
        assertTrue(json.exists())
        File("build/screenshots").mkdirs()
        compose.onRoot().captureRoboImage("build/screenshots/temporary-sharing-copies.png")
    }
}
