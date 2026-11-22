package com.verumomnis.forensic

import android.app.Application
import com.verumomnis.forensic.engine.EvidenceDocument
import com.verumomnis.forensic.identity.IdentityService
import com.verumomnis.forensic.identity.InMemoryIdentityKeyStore
import com.verumomnis.forensic.ui.VerumViewModel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ReportPdfStatusTest {

    @Test
    fun pdfWriteFailureKeepsTheReportAndExistingVaultArtifact() {
        val app = RuntimeEnvironment.getApplication() as Application
        val existingBytes = "prior sealed artifact".toByteArray()
        val existing = File(
            app.filesDir,
            "vault/reports/sealed/preserved-pdf-status-${System.nanoTime()}.bin"
        ).apply {
            parentFile?.mkdirs()
            writeBytes(existingBytes)
        }
        val vm = VerumViewModel(
            application = app,
            identityService = IdentityService(app, InMemoryIdentityKeyStore()),
            reportPdfWriter = { _, _ -> throw IllegalStateException("simulated PDF write failure") }
        )
        val now = Instant.parse("2026-11-21T16:00:00Z")
        vm.addDocument(
            EvidenceDocument(
                evidenceId = "DOC001",
                fileName = "plain-record.txt",
                type = "document",
                text = "A neutral record for export testing.",
                sha512 = "a".repeat(128)
            )
        )

        vm.runScan(now = now, caseName = "PdfFailure")
        vm.generateReport(caseName = "PdfFailure", now = now)
        val deadline = System.nanoTime() + 5_000_000_000L
        while (vm.state.value.reportPdfGenerating && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }

        assertNotNull(vm.state.value.report)
        assertFalse(vm.state.value.reportPdfGenerating)
        assertTrue(vm.state.value.reportPdfError.contains("sealed PDF could not be created"))
        assertArrayEquals(existingBytes, existing.readBytes())
    }
}
