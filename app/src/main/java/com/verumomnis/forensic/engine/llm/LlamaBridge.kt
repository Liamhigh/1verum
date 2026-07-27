package com.verumomnis.forensic.engine.llm

/**
 * Raw JNI declarations for the native inference bridge (voinference_jni.cpp -> llama.cpp).
 * See [LlamaModel] for the higher-level, lifecycle-safe wrapper the rest of the app uses.
 */
internal object LlamaBridge {

    /** True once the native library has loaded successfully; false if the .so is missing. */
    val isAvailable: Boolean = try {
        System.loadLibrary("voinference")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    /** Loads a GGUF model file. Returns an opaque native handle, or 0 on failure. */
    external fun nativeLoadModel(modelPath: String, nCtx: Int, nGpuLayers: Int): Long

    /** Blocking, greedy (deterministic) text completion. Returns the generated text only. */
    external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int): String

    /** Frees the model and context associated with [handle]. Safe to call once per handle. */
    external fun nativeFree(handle: Long)
}
