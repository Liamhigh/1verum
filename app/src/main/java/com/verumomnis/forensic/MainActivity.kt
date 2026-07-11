package com.verumomnis.forensic

import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.verumomnis.forensic.core.DeadManSwitch
import com.verumomnis.forensic.model.GpsRecord
import com.verumomnis.forensic.pdf.SealedPdfExporter
import com.verumomnis.forensic.ui.VerumApp
import com.verumomnis.forensic.ui.VerumViewModel
import com.verumomnis.forensic.ui.theme.VerumOmnisTheme
import java.time.Instant

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: VerumViewModel
    private val pdfExporter by lazy { SealedPdfExporter(this) }
    private val deadManSwitch = DeadManSwitch()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel = ViewModelProvider(this)[VerumViewModel::class.java]

        setContent {
            VerumOmnisTheme {
                VerumApp(
                    viewModel,
                    onCaptureLocation = ::captureLocation,
                    onExportReport = { report -> runCatching { pdfExporter.share(pdfExporter.exportReport(report)) } },
                    onExportEmail = { email -> runCatching { pdfExporter.share(pdfExporter.exportEmail(email)) } }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Any user activity resets the 72h Dead-Man Switch (Constitution safeguard).
        deadManSwitch.recordActivity()
    }

    fun captureLocation() {
        try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            val last = providers.firstNotNullOfOrNull { p ->
                runCatching { lm.getLastKnownLocation(p) }.getOrNull()
            }
            if (last != null) {
                viewModel.setGps(
                    GpsRecord(
                        latitude = last.latitude,
                        longitude = last.longitude,
                        accuracy = last.accuracy.toDouble(),
                        altitude = last.altitude,
                        timestamp = Instant.now().toString()
                    )
                )
            }
        } catch (_: SecurityException) {
            // Permission revoked mid-flight; keep seeded coordinates.
        }
    }
}
