package com.verumomnis.forensic.llm

import com.verumomnis.forensic.engine.llm.LlamaModel
import java.io.File

/**
 * [Gemma3Runtime] backed by the app's real native inference bridge.
 *
 * WHY THIS WAS REWRITTEN. The previous version declared its own JNI methods
 * (`Java_com_verumomnis_forensic_llm_LlamaCppGemma3Runtime_native*`) and loaded
 * a library named `verum_llama`. Neither existed: `CMakeLists.txt` builds
 * `voinference`, and the only JNI symbols in `voinference_jni.cpp` are
 * `LlamaBridge`'s. So `nativeLibraryPresent` was permanently false, `discover()`
 * always returned null, [Gemma3RuntimeProvider] never left
 * [UnavailableGemma3Runtime], and every consumer — `G3ReviewPass` (the hybrid
 * engine's candidate-raising review) and `ReportWriter.writeNarrative` — took
 * the deterministic fallback on every run. The hybrid engine had never executed
 * on any device, while chat worked, because chat uses the other stack.
 *
 * It now delegates to [LlamaModel], which uses the bridge that actually ships.
 * The degradation contract is unchanged: with no native library or no model,
 * [Gemma3RuntimeProvider] stays unavailable and callers fall back deterministically.
 */
class LlamaCppGemma3Runtime private constructor(
    private val model: LlamaModel,
    override val modelName: String
) : Gemma3Runtime {

    override fun isAvailable(): Boolean = true

    override fun generate(prompt: String, maxTokens: Int): String? =
        model.complete(prompt, maxTokens).takeIf { it.isNotBlank() }

    companion object {

        /**
         * Wrap an already-loaded model. This is the path that matters in
         * practice: `VerumViewModel` downloads and loads the model through
         * `ModelDownloadManager`/`LlamaModel`, then hands it here so the hybrid
         * engine uses the very same loaded instance instead of opening a second
         * copy of the weights — which would double the memory footprint on
         * exactly the low-RAM devices this app exists to serve.
         */
        fun wrap(model: LlamaModel, modelName: String = model.name): LlamaCppGemma3Runtime =
            LlamaCppGemma3Runtime(model, modelName)

        /**
         * Find a side-loaded GGUF under `files/models/` and load it, for models
         * placed on the device manually rather than downloaded through the
         * catalogue. Returns null when the native library is absent or no model
         * file is present, leaving [UnavailableGemma3Runtime] in effect.
         */
        fun discover(filesDir: File): LlamaCppGemma3Runtime? {
            val modelsDir = File(filesDir, "models")
            val file = modelsDir.listFiles { f ->
                f.isFile && f.name.endsWith(".gguf") &&
                    (f.name.startsWith("gemma3") || f.name.startsWith("gemma-3"))
            }?.minByOrNull { it.name } ?: return null
            val loaded = LlamaModel.load(file, "gemma-3-4b-it (${file.name})") ?: return null
            return LlamaCppGemma3Runtime(loaded, loaded.name)
        }
    }
}
