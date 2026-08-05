package com.verumomnis.forensic.core

/**
 * Device-tier LLM architecture (Part III of the specification).
 *
 * NO Mistral. The Android app runs Gemma 3 (bundled), Phi-3 (2GB+) and Gemma 4
 * (8GB+) only. Legal + R&D brains load on premium (8GB+) devices.
 *
 * The 8GB Gemma 4 threshold matches ON_DEVICE_LLM_ARCHITECTURE.md's "Flagship
 * (8GB+ RAM)" tier and is confirmed by real-device testing: on a 5.4GB-RAM phone,
 * Gemma 4's 7.7GB weight file loads but generation drops to ~20+ seconds/token as
 * the OS thrashes reclaiming memory from other apps — unusable below this tier.
 *
 * The 9-Brain forensic engine ALWAYS runs regardless of how many LLMs load, so
 * verification is ALWAYS triple, never dual.
 */
enum class LlmRole { REPORT_WRITER, COMMUNICATOR, LEGAL, RND }

/**
 * Communication style for an LLM. UNRESTRICTED means the model communicates
 * directly under the Constitution only, with no additional safety/censorship
 * filters beyond the constitutional safeguards.
 */
enum class LlmCommunicationStyle { RESTRICTED, UNRESTRICTED }

data class Llm(
    val name: String,
    val role: LlmRole,
    val bundled: Boolean,
    val flagship: Boolean = false,
    val communicationStyle: LlmCommunicationStyle = LlmCommunicationStyle.UNRESTRICTED
)

enum class DeviceTier(val label: String) {
    LOW_END("Low-End"),
    STANDARD("Standard"),
    PREMIUM("Premium"),
    ENTERPRISE("Enterprise");

    companion object {
        fun forRam(deviceRamGb: Int): DeviceTier = when {
            deviceRamGb >= 16 -> ENTERPRISE
            deviceRamGb >= 8 -> PREMIUM
            deviceRamGb >= 4 -> STANDARD
            else -> LOW_END
        }
    }
}

object ModelLoader {

    /** A flagship device can run Gemma 4 and the premium legal/R&D brains. */
    fun isFlagship(deviceRamGb: Int): Boolean = deviceRamGb >= 8

    /** Mirrors loadModels(deviceRamGB) from the specification.
     *
     * Roles under the Constitution-absorbed, unrestricted communication model:
     *  - Gemma 3  → forensic report writer (always present, bundled)
     *  - Phi-3    → communicator for standard 2GB+ devices
     *  - Gemma 4  → communicator for flagship 8GB+ devices
     */
    fun loadModels(deviceRamGb: Int): List<Llm> {
        val models = mutableListOf<Llm>()
        // The report writer. NOT bundled: no model ships inside the APK — this one
        // is downloaded and hash-verified like the others, which is why it has a URL
        // and a SHA-256 in Constitution. The old `bundled = true` here claimed the
        // opposite and made the app describe a model it did not have.
        models += Llm("Gemma 3", LlmRole.REPORT_WRITER, bundled = false)
        if (deviceRamGb >= 2) {
            // Standard communicator.
            models += Llm("Phi-3", LlmRole.COMMUNICATOR, bundled = false)
        }
        if (isFlagship(deviceRamGb)) {
            // Flagship communicator — direct, unrestricted, constitution-governed.
            models += Llm("Gemma 4", LlmRole.COMMUNICATOR, bundled = false, flagship = true)
        }
        if (deviceRamGb >= 8) {
            models += Llm("Legal Brain", LlmRole.LEGAL, bundled = false)
            models += Llm("R&D Brain", LlmRole.RND, bundled = false)
        }
        return models
    }

    /** The communicator is the LLM the user talks to (Gemma 4 flagship > Phi-3 > Gemma 3). */
    fun communicator(models: List<Llm>): Llm =
        models.lastOrNull { it.name == "Gemma 4" && it.flagship }
            ?: models.firstOrNull { it.name == "Phi-3" }
            ?: models.first { it.name == "Gemma 3" }

    /** The report writer is ALWAYS Gemma 3. */
    fun reportWriter(models: List<Llm>): Llm = models.first { it.name == "Gemma 3" }
}
