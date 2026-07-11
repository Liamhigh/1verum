package com.verumomnis.forensic

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
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

    private fun renderScreen(screen: String, name: String) {
        val vm = populatedViewModel()
        composeRule.setContent {
            VerumOmnisTheme { VerumApp(vm, initialScreen = screen) }
        }
        composeRule.onRoot().captureRoboImage("$outDir/$name")
    }

    @Test fun story() = renderScreen("STORY", "00_story.png")
    @Test fun chat() = renderScreen("CHAT", "03_chat.png")
    @Test fun report() = renderScreen("REPORT", "02_report.png")
    @Test fun email() = renderScreen("EMAIL", "04_email.png")
    @Test fun vault() = renderScreen("VAULT", "05_vault.png")
    @Test fun tax() = renderScreen("TAX", "11_tax.png")

    @Test
    fun plusActionsMenu() {
        val vm = populatedViewModel()
        composeRule.setContent {
            VerumOmnisTheme { VerumApp(vm, initialScreen = "CHAT", initialMenuOpen = true) }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("$outDir/12_plus_actions.png")
    }

    @Test
    fun emailAntiHarassmentEscalation() {
        val vm = populatedViewModel()
        // Repeated sends to the same recipient escalate ALLOW → WARN → BLOCK.
        repeat(6) { vm.draftAndSendEmail("investigator@saps.gov.za", "Sealed forensic report") }
        composeRule.setContent {
            VerumOmnisTheme { VerumApp(vm, initialScreen = "EMAIL") }
        }
        composeRule.onRoot().captureRoboImage("$outDir/06_email_anti_harassment.png")
    }
}
