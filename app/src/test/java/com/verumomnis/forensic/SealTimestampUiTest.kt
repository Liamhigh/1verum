package com.verumomnis.forensic

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.verumomnis.forensic.model.OtsStatus
import com.verumomnis.forensic.model.OtsVerifyResult
import com.verumomnis.forensic.ui.SealResult
import com.verumomnis.forensic.ui.SealTimestampProofPanel
import com.verumomnis.forensic.ui.theme.VerumOmnisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SealTimestampUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun statusCopyAndDownloadActionsFollowOnlyTheCurrentDocumentRecord() {
        val source = SealResult(
            sealedPdf = byteArrayOf(1), otsProof = null, sealId = "synthetic",
            sha256 = "b".repeat(64), sha512 = "a".repeat(128), verifyUrl = "",
            priorChain = emptyList(), pageCount = 1, sealedAtMs = 1L
        )
        val current = mutableStateOf(source)
        var downloads = 0
        compose.setContent { VerumOmnisTheme {
            SealTimestampProofPanel(current.value) { downloads++ }
        } }
        compose.onNodeWithText("Timestamp offline / not submitted").assertExists()
        compose.onNodeWithText("Download .OTS Proof").assertDoesNotExist()
        compose.runOnIdle {
            current.value = source.copy(otsProof = byteArrayOf(2), timestampSubmission = OtsStatus.PENDING)
        }
        compose.onNodeWithText("Timestamp proof pending").assertExists()
        compose.onNodeWithText("Download .OTS Proof").assertIsEnabled().performClick()
        compose.runOnIdle {
            current.value = current.value.copy(timestampVerification =
                OtsVerifyResult(OtsStatus.CONFIRMED, false, true))
        }
        compose.onNodeWithText("Bitcoin confirmation recorded").assertExists()
        compose.onNodeWithText("Download .OTS Proof").assertIsEnabled()
        compose.onRoot().captureRoboImage("build/screenshots/seal-timestamp-confirmed.png")
        compose.runOnIdle {
            current.value = source.copy(timestampSubmission = OtsStatus.FAILED)
        }
        compose.onNodeWithText("Timestamp proof failed").assertExists()
        compose.onNodeWithText("Download .OTS Proof").assertDoesNotExist()
        compose.runOnIdle {
            current.value = source.copy(otsProof = byteArrayOf(), timestampSubmission = OtsStatus.OFFLINE)
        }
        compose.onNodeWithText("Download .OTS Proof").assertDoesNotExist()
        assertEquals(1, downloads)
    }
}
