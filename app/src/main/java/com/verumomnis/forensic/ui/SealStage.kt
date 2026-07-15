package com.verumomnis.forensic.ui

/**
 * Stages shown to the user during the upload → scan → seal → anchor pipeline.
 *
 * The numeric progress value is a coarse 0.0–1.0 indicator for the progress bar.
 */
enum class SealStage(val label: String, val progress: Float) {
    IDLE("Ready", 0f),
    HASHING("Hashing evidence…", 0.15f),
    SCANNING("Nine-Brain forensic scan…", 0.40f),
    SEALING("Creating SHA-512 seal…", 0.65f),
    ANCHORING("Anchoring to Bitcoin…", 0.80f),
    DONE("Seal complete", 1f),
    ERROR("Seal failed", 0f)
}
