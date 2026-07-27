package com.verumomnis.forensic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verumomnis.forensic.engine.llm.LlamaModel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device proof that the native llama.cpp JNI bridge actually loads a real model and
 * generates text — not just that it compiles. Requires a GGUF file pushed to this app's
 * external files dir first:
 *
 *   adb push phi3.gguf /sdcard/Android/data/com.verumomnis.forensic/files/models/phi3.gguf
 *
 * Run with: ./gradlew connectedAndroidTest --tests "*LlamaModelSmokeTest*"
 */
@RunWith(AndroidJUnit4::class)
class LlamaModelSmokeTest {

    @Test
    fun loadsModelAndGeneratesNonEmptyCompletion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val modelFile = File(context.getExternalFilesDir("models"), "phi3.gguf")
        assertTrue(
            "Test fixture not found at ${modelFile.absolutePath} — push a GGUF file there first.",
            modelFile.exists()
        )

        val model = LlamaModel.load(modelFile, name = "Phi-3", nCtx = 512, nGpuLayers = 0)
        assertNotNull("Model failed to load — check logcat tag VoInference for the reason.", model)

        val output = model!!.complete("Reply with a single word: hello", maxTokens = 8)
        assertTrue("Expected non-empty generated text, got: '$output'", output.isNotBlank())

        model.close()
    }
}
