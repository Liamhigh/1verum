package com.verumomnis.forensic

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.verumomnis.forensic.identity.IdentityService
import com.verumomnis.forensic.identity.InMemoryIdentityKeyStore
import com.verumomnis.forensic.ui.VerumApp
import com.verumomnis.forensic.ui.VerumViewModel
import com.verumomnis.forensic.ui.theme.VerumOmnisTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h1000dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SimpleSealingScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Before fun grantLocation() {
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    private fun showHome() {
        val app = RuntimeEnvironment.getApplication()
        val vm = VerumViewModel(app, IdentityService(app, InMemoryIdentityKeyStore()))
        compose.setContent { VerumOmnisTheme { VerumApp(vm, initialScreen = "LANDING") } }
    }
    @Test fun homeHasOnlyTwoPrimaryDocumentActions() {
        showHome()
        compose.onNodeWithText("SEAL DOCUMENT").assertExists()
        compose.onNodeWithText("CHECK DOCUMENT").assertExists()
        compose.onRoot().captureRoboImage("build/screenshots/simple-home.png")
    }
    @Test fun sealGoesDirectlyToTwoModesWithoutTheScanDashboard() {
        showHome()
        compose.onNodeWithText("SEAL DOCUMENT").performClick()
        compose.onNodeWithText("Protect your PDF", substring = true).assertExists()
        compose.onNodeWithText("Seal document with forensic report").assertExists()
        compose.onNodeWithText("New Forensic Scan").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/screenshots/simple-seal.png")
    }
    @Test fun forensicModeChangesTheActionAndOptionalFieldsStayHidden() {
        showHome()
        compose.onNodeWithText("SEAL DOCUMENT").performClick()
        compose.onNodeWithText("Seal document with forensic report").performClick()
        compose.onNodeWithText("Seal with forensic report", ignoreCase = true).performScrollTo().assertExists()
        compose.onNodeWithText("+ Optional details").assertExists()
        compose.onNodeWithText("Organisation Name").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/screenshots/simple-forensic-mode.png")
    }
}
