package com.verumomnis.forensic

import android.app.Application
import com.verumomnis.forensic.engine.EvidenceDocument
import com.verumomnis.forensic.identity.IdentityService
import com.verumomnis.forensic.identity.InMemoryIdentityKeyStore
import com.verumomnis.forensic.model.GpsRecord
import com.verumomnis.forensic.ui.VerumViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JurisdictionSelectionViewModelTest {
    @Test
    fun scanUsesTheSelectedVenueInsteadOfTheGpsSuggestion() {
        val app = RuntimeEnvironment.getApplication() as Application
        val gps = GpsRecord(
            latitude = 51.5074,
            longitude = -0.1278,
            timestamp = "2026-11-23T10:00:00Z"
        )
        val viewModel = VerumViewModel(
            application = app,
            identityService = IdentityService(app, InMemoryIdentityKeyStore())
        )
        viewModel.addDocument(
            EvidenceDocument(
                evidenceId = "DOC001",
                fileName = "venue-selection.txt",
                type = "document",
                text = "Synthetic case evidence for venue selection.",
                sha512 = "b".repeat(128),
                gps = gps
            )
        )
        viewModel.setSelectedVenue("ZA-KZN")
        viewModel.runScan(
            now = Instant.parse("2026-11-23T10:00:00Z"),
            caseName = "ManualVenue"
        )

        val findings = viewModel.state.value.scanResult?.findings
        assertNotNull(findings)
        assertEquals("ZA-KZN", findings?.jurisdiction)
        assertEquals("INVESTIGATOR_SELECTED", findings?.jurisdictionSource?.selectionBasis)
        assertEquals(gps, findings?.jurisdictionSource?.gps)
    }
}
