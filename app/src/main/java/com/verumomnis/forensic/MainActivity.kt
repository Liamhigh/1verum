package com.verumomnis.forensic

import android.content.Intent
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.verumomnis.forensic.core.DeadManSwitch
import com.verumomnis.forensic.engine.contradiction.ContradictionDetectors
import com.verumomnis.forensic.model.GpsRecord
import com.verumomnis.forensic.pdf.SealedPdfExporter
import com.verumomnis.forensic.ui.VerumApp
import com.verumomnis.forensic.ui.VerumViewModel
import com.verumomnis.forensic.ui.theme.VerumOmnisTheme
import com.verumomnis.forensic.update.RuleRegistry
import com.verumomnis.forensic.update.RuleUpdateWorker
import java.io.File
import java.time.Instant

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: VerumViewModel
    private val pdfExporter by lazy { SealedPdfExporter(this) }
    private val deadManSwitch = DeadManSwitch()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel = ViewModelProvider(this)[VerumViewModel::class.java]

        // Signed rule updates: expose the last signature-VERIFIED rules to the
        // contradiction engine (returns null -> engine unchanged when no package
        // has been downloaded) and schedule the daily update check. The worker
        // uses unique KEEP work, so calling this on every start is a no-op once
        // scheduled.
        val ruleRegistry = RuleRegistry.getInstance(applicationContext)
        ContradictionDetectors.downloadedRulesProvider = { ruleRegistry.currentRules() }
        RuleUpdateWorker.schedule(applicationContext)

        setContent {
            VerumOmnisTheme {
                VerumApp(
                    viewModel,
                    onCaptureLocation = ::captureLocation,
                    onExportReport = { report -> runCatching { pdfExporter.share(pdfExporter.exportReport(report)) } },
                    onExportEmail = { email -> runCatching { pdfExporter.share(pdfExporter.exportEmail(email)) } },
                    onReadConstitution = ::openConstitution
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

    /**
     * Copies the bundled Constitution PDF from assets to cache and opens it with
     * the device's PDF reader (FileProvider-granted uri).
     */
    private fun openConstitution() {
        runCatching {
            val dest = File(cacheDir, "constitution.pdf")
            if (!dest.exists() || dest.length() == 0L) {
                assets.open("constitution.pdf").use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", dest)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, "Read Constitution").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
