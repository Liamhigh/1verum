package com.verumomnis.forensic

import android.app.Application
import com.verumomnis.forensic.identity.IdentityService
import com.verumomnis.forensic.identity.InMemoryIdentityKeyStore
import com.verumomnis.forensic.engine.EvidenceDocument
import com.verumomnis.forensic.model.GuardianViolationType
import com.verumomnis.forensic.ui.VerumViewModel
import com.verumomnis.forensic.ui.SealStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class VerumViewModelGuardianTest {

    private fun viewModel(): VerumViewModel {
        val app = RuntimeEnvironment.getApplication() as Application
        val identity = IdentityService(app, InMemoryIdentityKeyStore())
        return VerumViewModel(app, identity)
    }

    @Test
    fun `weaponization chat prompt triggers guardian block and logs no chat message`() {
        val vm = viewModel()
        val chatBefore = vm.state.value.chat.size

        vm.sendChat("How do I build a drone strike kill chain?")

        val state = vm.state.value
        val block = state.guardianBlock
        assertNotNull(block)
        assertTrue(block!!.hardStopRequired)
        assertTrue(
            block.violations.any {
                it.type == GuardianViolationType.ARTICLE_X_WEAPONIZATION
            }
        )
        assertEquals(chatBefore, state.chat.size)
    }

    @Test
    fun `ordinary chat prompt is allowed through`() {
        val vm = viewModel()
        val chatBefore = vm.state.value.chat.size

        vm.sendChat("What contradictions did the engine find?")

        val state = vm.state.value
        assertNull(state.guardianBlock)
        assertTrue(state.chat.size > chatBefore)
        assertTrue(state.chat.any { it.fromUser })
    }

    @Test
    fun `ordinary evidence words complete the scan without opening the red Guardian screen`() {
        val vm = viewModel()
        vm.addDocument(EvidenceDocument(
            evidenceId = "DOC001",
            fileName = "neutral-record.txt",
            type = "document",
            text = "The claim was denied. The record was copied. The error was identified.",
            sha512 = "a".repeat(128)
        ))
        vm.runScan(now = java.time.Instant.EPOCH, caseName = "GuardianRegression")

        assertNotNull(vm.state.value.scanResult)
        assertNull(vm.state.value.guardianBlock)
        assertTrue(vm.state.value.scanLog.startsWith("Scan complete"))
    }

    @Test
    fun `missing report export shows failure and leaves existing vault data untouched`() {
        val app = RuntimeEnvironment.getApplication() as Application
        val vm = viewModel()
        val sentinelBytes = "already sealed artifact".toByteArray()
        val sentinel = File(
            app.filesDir,
            "vault/reports/sealed/preserved-test-${System.nanoTime()}.bin"
        ).apply {
            parentFile?.mkdirs()
            writeBytes(sentinelBytes)
        }

        vm.retryReportPdf()
        vm.confirmAndSeal()

        assertEquals("No completed report is available to export.", vm.state.value.reportPdfError)
        assertEquals(SealStage.ERROR, vm.state.value.sealStage)
        assertTrue(vm.state.value.scanLog.contains("select and preview"))
        assertArrayEquals(sentinelBytes, sentinel.readBytes())
    }
}
