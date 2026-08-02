package com.verumomnis.forensic.llm

/**
 * On-device Gemma 3 inference seam for the G3 Hybrid Report Pipeline (GHRP).
 *
 * The deterministic engine never depends on this being available: every
 * consumer must treat an unavailable runtime (or a null generation) as
 * "fall back to the deterministic output". The runtime is only ever additive —
 * narrative appendices and G3-raised candidates — and never contributes to
 * the sealed report body.
 */
interface Gemma3Runtime {

    /** Model identifier recorded in provenance metadata (e.g. "gemma-3-4b-it"). */
    val modelName: String

    /** True only when a model is loaded and inference can actually run. */
    fun isAvailable(): Boolean

    /**
     * Run one bounded generation. Returns null when the runtime is
     * unavailable or inference fails; callers must fall back deterministically.
     */
    fun generate(prompt: String, maxTokens: Int = 1024): String?
}

/** Default runtime when no model is installed: always unavailable. */
object UnavailableGemma3Runtime : Gemma3Runtime {
    override val modelName: String = "gemma-3-4b-it"
    override fun isAvailable(): Boolean = false
    override fun generate(prompt: String, maxTokens: Int): String? = null
}

/**
 * App-wide runtime holder. [MainActivity] installs a real llama.cpp-backed
 * runtime here when a Gemma 3 GGUF model is present on the device; tests may
 * install a fake. Everything else reads through this provider so the engine
 * stays pure Kotlin and unit-testable off-device.
 */
object Gemma3RuntimeProvider {
    @Volatile
    var runtime: Gemma3Runtime = UnavailableGemma3Runtime
}
