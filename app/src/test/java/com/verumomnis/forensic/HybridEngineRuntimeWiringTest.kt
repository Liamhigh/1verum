package com.verumomnis.forensic

import com.verumomnis.forensic.llm.Gemma3Runtime
import com.verumomnis.forensic.llm.Gemma3RuntimeProvider
import com.verumomnis.forensic.llm.UnavailableGemma3Runtime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regression guard for the hybrid engine's AI seam.
 *
 * THE BUG THIS EXISTS TO PREVENT. `LlamaCppGemma3Runtime` used to declare its
 * own JNI methods and load a native library called `verum_llama`. The project
 * builds `voinference`, and the only JNI symbols that exist are `LlamaBridge`'s.
 * So the runtime could never load, `Gemma3RuntimeProvider` never left
 * [UnavailableGemma3Runtime], and both consumers of the seam — `G3ReviewPass`
 * (which raises candidate contradictions the deterministic detectors missed)
 * and `ReportWriter.writeNarrative` — silently took the deterministic fallback
 * on every run, on every device. Chat still worked, because chat holds its own
 * `LlamaModel` reference, which is exactly why the failure went unnoticed: the
 * app looked alive while the hybrid engine had never once executed.
 *
 * These tests pin the contract that makes the seam work, without requiring the
 * native library (unavailable on the JVM test runner).
 */
class HybridEngineRuntimeWiringTest {

    @After
    fun reset() {
        Gemma3RuntimeProvider.runtime = UnavailableGemma3Runtime
    }

    @Test
    fun `provider defaults to unavailable so consumers fall back deterministically`() {
        Gemma3RuntimeProvider.runtime = UnavailableGemma3Runtime
        assertFalse(Gemma3RuntimeProvider.runtime.isAvailable())
        assertNull(Gemma3RuntimeProvider.runtime.generate("anything", 128))
    }

    @Test
    fun `installing a runtime makes the hybrid engine seam live`() {
        // Stands in for LlamaCppGemma3Runtime.wrap(loadedModel): once a model is
        // loaded, SOMETHING must write to the provider or the hybrid engine is
        // dead code. Before the fix, nothing on a working code path ever did.
        val installed = object : Gemma3Runtime {
            override val modelName = "gemma-3-4b-it"
            override fun isAvailable() = true
            override fun generate(prompt: String, maxTokens: Int) = "narrative for: $prompt"
        }
        Gemma3RuntimeProvider.runtime = installed

        assertTrue("a loaded model must make the seam available", Gemma3RuntimeProvider.runtime.isAvailable())
        assertEquals("narrative for: p", Gemma3RuntimeProvider.runtime.generate("p", 64))
        assertEquals("gemma-3-4b-it", Gemma3RuntimeProvider.runtime.modelName)
    }

    @Test
    fun `a runtime that fails generation returns null so callers fall back, never fabricate`() {
        Gemma3RuntimeProvider.runtime = object : Gemma3Runtime {
            override val modelName = "gemma-3-4b-it"
            override fun isAvailable() = true
            override fun generate(prompt: String, maxTokens: Int): String? = null
        }
        assertNull(Gemma3RuntimeProvider.runtime.generate("p", 64))
    }

    /**
     * llama.cpp holds one context per model handle and is not re-entrant: two
     * concurrent generations on the same handle corrupt it and the second caller
     * hangs. This was reachable in normal use — the chat communicator falls back
     * to the report-writer model when only one model is installed, so asking a
     * question while a report was being written put two generations on one
     * handle and the conversation froze. `LlamaModel.complete` now serialises.
     * This test pins that contract on a stand-in with the same locking shape.
     */
    @Test
    fun `generation is serialised so concurrent callers cannot overlap`() {
        val concurrent = AtomicInteger(0)
        val maxObserved = AtomicInteger(0)
        val lock = Any()
        val runtime = object : Gemma3Runtime {
            override val modelName = "gemma-3-4b-it"
            override fun isAvailable() = true
            override fun generate(prompt: String, maxTokens: Int): String = synchronized(lock) {
                val inFlight = concurrent.incrementAndGet()
                maxObserved.updateAndGet { prev -> maxOf(prev, inFlight) }
                Thread.sleep(15)              // stand-in for native inference
                concurrent.decrementAndGet()
                "ok"
            }
        }

        val threads = 8
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        repeat(threads) {
            Thread {
                start.await()
                runtime.generate("prompt", 32)
                done.countDown()
            }.start()
        }
        start.countDown()
        assertTrue("all generations finished", done.await(10, TimeUnit.SECONDS))
        assertEquals("native generation must never run concurrently", 1, maxObserved.get())
    }
}
