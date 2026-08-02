package com.verumomnis.forensic

import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.core.util.Consumer
import androidx.lifecycle.ViewModelProvider
import com.verumomnis.forensic.core.DeadManSwitch
import com.verumomnis.forensic.engine.contradiction.ContradictionDetectors
import com.verumomnis.forensic.llm.Gemma3RuntimeProvider
import com.verumomnis.forensic.llm.LlamaCppGemma3Runtime
import com.verumomnis.forensic.model.GpsRecord
import com.verumomnis.forensic.pdf.SealedPdfExporter
import com.verumomnis.forensic.ui.VerumApp
import com.verumomnis.forensic.ui.VerumViewModel
import com.verumomnis.forensic.ui.theme.VerumOmnisTheme
import com.verumomnis.forensic.update.DownloadedRules
import com.verumomnis.forensic.update.LocalRuleStore
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
        // scheduled. Locally promoted G3 candidate rules are merged in through
        // the same additive path, closing the Gemma-to-engine feedback loop.
        val ruleRegistry = RuleRegistry.getInstance(applicationContext)
        val localRuleStore = LocalRuleStore.getInstance(applicationContext)
        ContradictionDetectors.downloadedRulesProvider = {
            DownloadedRules.merged(ruleRegistry.currentRules(), localRuleStore.promotedRules())
        }
        RuleUpdateWorker.schedule(applicationContext)

        // Hybrid pipeline: install the llama.cpp-backed Gemma 3 runtime when a
        // GGUF model is provisioned under files/models/. Without it the
        // provider stays on the unavailable default and every consumer falls
        // back to the deterministic pipeline.
        LlamaCppGemma3Runtime.discover(filesDir)?.let { Gemma3RuntimeProvider.runtime = it }

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

    /**
     * Capture a real location fix for the seal (spec §4.4).
     *
     * A cached `getLastKnownLocation` can be hours old, and stamping stale
     * coordinates onto sealed evidence is worse than recording none — so a
     * cached fix is only accepted if it is younger than [MAX_FIX_AGE_MS].
     * Otherwise a fresh single-shot fix is requested, and if that also fails the
     * state is set explicitly to "unavailable" rather than left showing whatever
     * was there before.
     */
    fun captureLocation() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            viewModel.setGpsUnavailable()
            return
        }
        try {
            // 1. A recent cached fix is good enough and costs nothing.
            val fresh = providers
                .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < MAX_FIX_AGE_NANOS }
                .maxByOrNull { it.time }
            if (fresh != null) {
                viewModel.setGps(fresh.toGpsRecord())
                return
            }
            // 2. Nothing recent — ask for a live fix.
            // LocationManagerCompat, not LocationManager.getCurrentLocation —
            // the platform method is API 30 and minSdk here is 29.
            // The signal is typed explicitly: LocationManagerCompat overloads it on
            // both androidx and platform CancellationSignal, so a bare null is ambiguous.
            LocationManagerCompat.getCurrentLocation(
                lm,
                providers.first(),
                null as CancellationSignal?,
                ContextCompat.getMainExecutor(this),
                Consumer<Location?> { loc ->
                    if (loc != null) viewModel.setGps(loc.toGpsRecord()) else viewModel.setGpsUnavailable()
                }
            )
        } catch (_: SecurityException) {
            // Permission revoked mid-flight — record the gap rather than guessing.
            viewModel.setGpsUnavailable()
        }
    }

    private fun Location.toGpsRecord() = GpsRecord(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy.toDouble(),
        altitude = altitude,
        timestamp = Instant.now().toString()
    )

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

    private companion object {
        /**
         * Oldest cached fix accepted for a seal: 2 minutes. Anything older is
         * discarded in favour of a live fix, so evidence is never stamped with a
         * location the device happened to remember from somewhere else.
         */
        const val MAX_FIX_AGE_NANOS = 2L * 60 * 1_000_000_000
    }
}
