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
}
