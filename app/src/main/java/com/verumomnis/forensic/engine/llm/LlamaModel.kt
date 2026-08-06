package com.verumomnis.forensic.engine.llm

import com.verumomnis.forensic.engine.ReportWriter
import com.verumomnis.forensic.engine.Gemma3ReportWriter
import com.verumomnis.forensic.model.ForensicFindings
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A loaded on-device GGUF model (llama.cpp), lifecycle-managed on the Kotlin side.
 * Loading and generation are both blocking/synchronous — callers running this off the
 * main thread is their responsibility (see [Gemma3NativeReportWriter], [VerumViewModel]).
 */
class LlamaModel private constructor(private var handle: Long, val name: String) : AutoCloseable {

    private val closed = AtomicBoolean(false)

    /**
     * Serialises native generation. llama.cpp holds ONE context per model handle
     * and is not re-entrant: two concurrent `nativeGenerate` calls on the same
     * handle corrupt that context and the second caller hangs. This was reachable
     * in normal use — `adoptFallbackCommunicator()` deliberately points the chat
     * communicator at the report-writer model when only one model is installed,
     * so asking a question while a report was being written put two generations
     * on one handle and the conversation froze. Callers now queue instead.
     */
    private val generationLock = Any()

    /** Blocking, deterministic (greedy) text completion. Empty string if generation fails. */
    fun complete(prompt: String, maxTokens: Int = 768): String {
        if (closed.get()) return ""
        return synchronized(generationLock) {
            if (closed.get()) return@synchronized ""
            try {
                LlamaBridge.nativeGenerate(handle, prompt, maxTokens)
            } catch (_: Exception) {
                ""
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            // Freeing while a generation is in flight would pull the context out
            // from under the native call, so wait for it to finish first.
            synchronized(generationLock) {
                LlamaBridge.nativeFree(handle)
                handle = 0
            }
        }
    }

    companion object {
        /**
         * Loads [modelFile] with a context window of [nCtx] tokens. [nGpuLayers] offloads
         * that many transformer layers to GPU (0 = CPU-only, safest default across devices;
         * a negative value offloads all layers).
         *
         * Returns null if the native library isn't available (.so not built for this ABI)
         * or the model fails to load (missing/corrupt file, insufficient RAM) — callers must
         * fall back to the existing deterministic (no-model) behaviour, never fabricate.
         */
        fun load(modelFile: File, name: String, nCtx: Int = 4096, nGpuLayers: Int = 0): LlamaModel? {
            if (!LlamaBridge.isAvailable || !modelFile.exists()) return null
            val handle = try {
                LlamaBridge.nativeLoadModel(modelFile.absolutePath, nCtx, nGpuLayers)
            } catch (_: Exception) {
                0L
            }
            if (handle == 0L) return null
            return LlamaModel(handle, name)
        }
    }
}

/**
 * Real Gemma 3 narrative writer: builds the same structured prompt as [Gemma3ReportWriter]
 * (PROMPT.md Section 7) but runs it through a loaded native model instead of returning the
 * prompt text itself. Falls back to [Gemma3ReportWriter]'s prompt-echo behaviour if the
 * model produces no output, so the report body is never silently blank.
 */
class Gemma3NativeReportWriter(private val model: LlamaModel) : ReportWriter {
    override fun writeNarrative(findings: ForensicFindings, caseName: String, findingsJsonPath: String): String {
        val prompt = Gemma3ReportWriter.buildPrompt(findings, caseName, findingsJsonPath)
        val generated = model.complete(prompt).trim()
        return generated.ifEmpty { prompt }
    }
}
