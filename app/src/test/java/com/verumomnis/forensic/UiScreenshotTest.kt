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

/**
 * Renders the real Compose UI to PNG on the JVM (no emulator/KVM) using Roborazzi,
 * which draws the view directly and therefore avoids the forceRedraw stall of the
 * stock captureToImage(). Doubles as a smoke test that every screen composes with
 * populated scan / report / email state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h1100dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outDir = "build/screenshots"

    @Before
    fun grantLocation() {
        val app = RuntimeEnvironment.getApplication() as Application
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun populatedViewModel(): VerumViewModel = VerumViewModel().apply {
        runScan()
        generateReport()
        draftAndSendEmail("investigator@saps.gov.za", "Sealed forensic report")
    }

    private fun renderTab(tab: Int, name: String) {
        val vm = populatedViewModel()
        composeRule.setContent {
            VerumOmnisTheme { VerumApp(vm, initialTab = tab) }
        }
        composeRule.onRoot().captureRoboImage("$outDir/$name")
    }

    @Test fun dashboard() = renderTab(0, "01_dashboard.png")
    @Test fun report() = renderTab(1, "02_report.png")
    @Test fun chat() = renderTab(2, "03_chat.png")
    @Test fun email() = renderTab(3, "04_email.png")
    @Test fun vault() = renderTab(4, "05_vault.png")

    @Test
    fun emailAntiHarassmentEscalation() {
        val vm = populatedViewModel()
        // Repeated sends to the same recipient escalate ALLOW → WARN → BLOCK.
        repeat(6) { vm.draftAndSendEmail("investigator@saps.gov.za", "Sealed forensic report") }
        composeRule.setContent {
            VerumOmnisTheme { VerumApp(vm, initialTab = 3) }
        }
        composeRule.onRoot().captureRoboImage("$outDir/06_email_anti_harassment.png")
    }
}
