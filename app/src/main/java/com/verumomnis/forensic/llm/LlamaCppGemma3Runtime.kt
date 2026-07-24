package com.verumomnis.forensic.llm

import java.io.File

/**
 * llama.cpp-backed Gemma 3 runtime (ARCHITECTURE.md: "LLM Runtime | llama.cpp
 * via JNI"). Loads a quantised Gemma 3 GGUF from the app's private storage
 * (`files/models/gemma3-*.gguf`) through the `verum_llama` JNI bridge.
 *
 * Degrades gracefully: when the native library is not bundled or no model
 * file is installed, [isAvailable] is false and every consumer falls back to
 * the deterministic pipeline. This keeps the app fully functional on devices
 * without the model while allowing the hybrid pipeline to light up the moment
 * a model is provisioned.
 */
class LlamaCppGemma3Runtime private constructor(
    private val modelFile: File,
    override val modelName: String
) : Gemma3Runtime {

    @Volatile
    private var modelLoaded: Boolean? = null

    private fun ensureLoaded(): Boolean {
        val loaded = modelLoaded
        if (loaded != null) return loaded
        synchronized(this) {
            val again = modelLoaded
            if (again != null) return again
            val result = nativeLibraryPresent && modelFile.exists() &&
                runCatching { nativeLoadModel(modelFile.absolutePath) }.getOrDefault(false)
            modelLoaded = result
            return result
        }
    }

    override fun isAvailable(): Boolean = ensureLoaded()

    override fun generate(prompt: String, maxTokens: Int): String? {
        if (!ensureLoaded()) return null
        return runCatching { nativeGenerate(prompt, maxTokens) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private external fun nativeLoadModel(path: String): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String?

    companion object {

        /** True when the packaged JNI bridge could be loaded on this device. */
        val nativeLibraryPresent: Boolean by lazy {
            runCatching { System.loadLibrary("verum_llama") }.isSuccess
        }

        /**
         * Find an installed Gemma 3 model under `files/models/` and build a
         * runtime for it. Returns null when the JNI bridge or model is absent,
         * in which case [UnavailableGemma3Runtime] stays in effect.
         */
        fun discover(filesDir: File): LlamaCppGemma3Runtime? {
            if (!nativeLibraryPresent) return null
            val modelsDir = File(filesDir, "models")
            val model = modelsDir.listFiles { f ->
                f.isFile && f.name.startsWith("gemma3") && f.name.endsWith(".gguf")
            }?.minByOrNull { it.name } ?: return null
            val name = "gemma-3-4b-it (${model.name})"
            return LlamaCppGemma3Runtime(model, name)
        }
    }
}
