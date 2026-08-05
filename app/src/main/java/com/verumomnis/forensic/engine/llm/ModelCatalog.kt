package com.verumomnis.forensic.engine.llm

import com.verumomnis.forensic.core.Constitution

/** The three on-device models, tying [Constitution]'s hard-coded URLs/hashes to a downloadable spec. */
object ModelCatalog {
    val GEMMA_3 = ModelDownloadManager.ModelSpec(
        id = "gemma3",
        displayName = "Gemma 3",
        url = Constitution.MODEL_GEMMA3_URL,
        sha256 = Constitution.MODEL_GEMMA3_SHA256,
        sizeBytes = Constitution.MODEL_GEMMA3_SIZE_BYTES
    )

    /**
     * Gemma 3 1B — the report writer used where the 4B will not fit.
     *
     * Same id as [GEMMA_3] deliberately: both land at `models/gemma3.gguf` and
     * fill the REPORT_WRITER role, so the rest of the pipeline neither knows nor
     * cares which one is installed. Only one may be present at a time, which is
     * enforced by that shared path.
     */
    val GEMMA_3_1B = ModelDownloadManager.ModelSpec(
        id = "gemma3",
        displayName = "Gemma 3",
        url = Constitution.MODEL_GEMMA3_1B_URL,
        sha256 = Constitution.MODEL_GEMMA3_1B_SHA256,
        sizeBytes = Constitution.MODEL_GEMMA3_1B_SIZE_BYTES
    )

    val PHI_3 = ModelDownloadManager.ModelSpec(
        id = "phi3",
        displayName = "Phi-3",
        url = Constitution.MODEL_PHI3_URL,
        sha256 = Constitution.MODEL_PHI3_SHA256,
        sizeBytes = Constitution.MODEL_PHI3_SIZE_BYTES
    )

    val GEMMA_4 = ModelDownloadManager.ModelSpec(
        id = "gemma4",
        displayName = "Gemma 4",
        url = Constitution.MODEL_GEMMA4_URL,
        sha256 = Constitution.MODEL_GEMMA4_SHA256,
        sizeBytes = Constitution.MODEL_GEMMA4_SIZE_BYTES
    )

    fun forName(name: String): ModelDownloadManager.ModelSpec? = when (name) {
        "Gemma 3" -> GEMMA_3
        "Phi-3" -> PHI_3
        "Gemma 4" -> GEMMA_4
        else -> null
    }

    /**
     * The report writer sized for this device.
     *
     * Below [SMALL_WRITER_RAM_GB] the 4B is not a slow option, it is an absent
     * one: a 2.49 GB file cannot be held by a handset with under two gigabytes
     * free, so choosing it there means no narrative at all. The 1B fits, and a
     * narrative writer that runs beats a better one that does not.
     */
    fun forName(name: String, deviceRamGb: Int): ModelDownloadManager.ModelSpec? =
        if (name == "Gemma 3" && deviceRamGb < SMALL_WRITER_RAM_GB) GEMMA_3_1B else forName(name)

    /**
     * Every spec that could occupy this model's slot on disk.
     *
     * [GEMMA_3] and [GEMMA_3_1B] share a filename, so "is a model installed?"
     * cannot be answered by one spec alone — a device holding the 1B would look
     * empty when asked about the 4B, and the app would re-download 2.49 GB over
     * a working model.
     */
    fun variantsForName(name: String): List<ModelDownloadManager.ModelSpec> = when (name) {
        "Gemma 3" -> listOf(GEMMA_3, GEMMA_3_1B)
        else -> listOfNotNull(forName(name))
    }

    /** Below this much RAM the report writer drops to the 1B. */
    const val SMALL_WRITER_RAM_GB = 6
}
