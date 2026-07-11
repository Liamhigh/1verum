package com.verumomnis.forensic

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
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

/** Tall render of the Report tab so the B8 audio + Bitcoin-anchor cards are visible. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h4200dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReportAudioOtsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun grantLocation() {
        shadowOf(RuntimeEnvironment.getApplication() as Application)
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @Test
    fun reportWithAudioAndAnchor() {
        val vm = VerumViewModel().apply {
            runScan()
            generateReport()
        }
        composeRule.setContent { VerumOmnisTheme { VerumApp(vm, initialTab = 1) } }
        composeRule.onRoot().captureRoboImage("build/screenshots/09_report_audio_ots.png")
    }
}
