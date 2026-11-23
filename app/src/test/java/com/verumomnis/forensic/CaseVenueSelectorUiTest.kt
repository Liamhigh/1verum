package com.verumomnis.forensic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import com.verumomnis.forensic.engine.JurisdictionService
import com.verumomnis.forensic.ui.CaseVenueSelector
import com.verumomnis.forensic.ui.theme.VerumOmnisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h800dp-mdpi")
class CaseVenueSelectorUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun selectingVenueUpdatesCaseAndKeepsGPSAsContext() {
        val selected = mutableStateOf<String?>(null)
        val gpsSuggestion = JurisdictionService.resolve(
            com.verumomnis.forensic.model.GpsRecord(51.5074, -0.1278, timestamp = "synthetic")
        )
        compose.setContent {
            VerumOmnisTheme {
                CaseVenueSelector(selected.value, gpsSuggestion) { selected.value = it }
            }
        }

        compose.onNodeWithText("Use GPS suggestion · United Kingdom (UK)").assertExists().performClick()
        compose.onNodeWithText("South Africa — KwaZulu-Natal · ZA-KZN").assertExists().performClick()
        compose.runOnIdle { assertEquals("ZA-KZN", selected.value) }
        compose.onNodeWithText(
            "Investigator-selected venue. GPS remains supporting context and will not replace this choice."
        ).assertExists()
    }
}
