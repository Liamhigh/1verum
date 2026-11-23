package com.verumomnis.forensic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.verumomnis.forensic.model.LinkedDocumentRecord
import com.verumomnis.forensic.ui.VaultGrouping
import com.verumomnis.forensic.ui.VaultRedesignScreen
import com.verumomnis.forensic.ui.theme.VerumOmnisTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LinkedDocumentVaultUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun expandedLinkedSetShowsDistinctRolesAndMissingOutputs() {
        val root = File(RuntimeEnvironment.getApplication().cacheDir, "linked-vault-ui").apply { mkdirs() }
        File(root, "original.pdf").writeText("synthetic source")
        File(root, "sealed.pdf").writeText("synthetic sealed bytes")
        val record = LinkedDocumentRecord(
            "synthetic-id", "uploaded-name.pdf", "a".repeat(128), "2026-11-23T00:00:00Z",
            "original.pdf", true, sealedPdfPath = "sealed.pdf", reportPdfPath = "missing-report.pdf"
        )
        val sets = VaultGrouping.group(emptyList(), emptyList(), emptyList(),
            links = listOf(record), resolvePath = { File(root, it) })
        compose.setContent { VerumOmnisTheme {
            VaultRedesignScreen(sets, {}, {}, {}, {})
        } }
        compose.onNodeWithText("uploaded-name.pdf").performClick()
        compose.onNodeWithText("Original evidence").assertExists()
        compose.onNodeWithText("Sealed PDF").assertExists()
        compose.onNodeWithText("Forensic report").assertExists()
        compose.onNodeWithText("Missing or empty file").assertExists()
        compose.onNodeWithText("Timestamp proof not saved").assertExists()
        compose.onRoot().captureRoboImage("build/screenshots/linked-document-vault.png")
    }
}
