package com.verumomnis.forensic

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verumomnis.forensic.engine.llm.LlamaBridge
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device proof that `libvoinference.so` loads on real arm64 hardware.
 *
 * This is the cheap half of the native check and the half that most often
 * breaks: an ABI mismatch, a missing `.so` in the packaged APK, or an
 * unresolved symbol all surface here as `System.loadLibrary` failing. Unlike
 * [LlamaModelSmokeTest] it needs no GGUF fixture, so it runs on any connected
 * device in seconds rather than requiring a 2.5 GB download.
 *
 * Passing here does NOT mean inference works — only that the bridge is
 * reachable. [LlamaModelSmokeTest] covers actual generation, and needs a
 * device with enough RAM to hold the model.
 */
@RunWith(AndroidJUnit4::class)
class NativeBridgeAvailabilityTest {

    @Test
    fun nativeLibraryLoadsOnThisDevice() {
        assertTrue(
            "System.loadLibrary(\"voinference\") failed — the .so is missing from the APK " +
                "for this ABI, or has an unresolved symbol. Check logcat tag VoInference.",
            LlamaBridge.isAvailable
        )
    }
}
