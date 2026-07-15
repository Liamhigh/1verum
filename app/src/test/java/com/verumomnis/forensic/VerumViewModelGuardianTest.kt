package com.verumomnis.forensic

import android.app.Application
import com.verumomnis.forensic.identity.IdentityService
import com.verumomnis.forensic.identity.InMemoryIdentityKeyStore
import com.verumomnis.forensic.model.GuardianViolationType
import com.verumomnis.forensic.ui.VerumViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

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
}
