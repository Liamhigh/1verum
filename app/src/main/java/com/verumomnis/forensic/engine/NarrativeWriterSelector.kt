package com.verumomnis.forensic.engine

import com.verumomnis.forensic.engine.llm.Gemma3NativeReportWriter
import com.verumomnis.forensic.engine.llm.LlamaModel

/**
 * Chooses which [ReportWriter] narrates a report.
 *
 * Extracted from `VerumViewModel.generateReportSync` so the choice can be tested
 * without a device: the selection is the safety-critical part (a report must
 * never come back blank because a model failed to load), while actually running
 * Gemma 3 needs an 8 GB+ phone and a 2.5 GB download.
 *
 * The order is deliberate:
 *  1. A loaded on-device model narrates for real.
 *  2. Otherwise [Gemma3ReportWriter], which degrades internally.
 *  3. Otherwise [DeterministicReportWriter], which is always reproducible.
 *
 * Under the Constitution's failure-mode-disclosure rule a missing model is
 * disclosed, never hidden — see [unavailableNote].
 */
object NarrativeWriterSelector {

    /**
     * @param reportWriterModel the loaded on-device writer, or null if the model
     *   was never downloaded, failed SHA-256 verification, or would not fit in RAM.
     * @param hasResearch whether external research findings exist.
     * @param gemma3RuntimeAvailable whether the G3 runtime seam
     *   (`Gemma3RuntimeProvider.runtime`) has a backend installed. This is a
     *   second, independent narration path from [reportWriterModel]: the model is
     *   loaded directly through `ModelDownloadManager`, whereas the runtime is
     *   provisioned separately. A directly loaded model is preferred because it
     *   is already verified and resident.
     */
    fun select(
        reportWriterModel: LlamaModel?,
        hasResearch: Boolean,
        gemma3RuntimeAvailable: Boolean = false
    ): ReportWriter = when {
        reportWriterModel != null -> Gemma3NativeReportWriter(reportWriterModel)
        // Gemma3ReportWriter drives the runtime seam and degrades to the
        // deterministic writer internally if generation returns nothing.
        gemma3RuntimeAvailable -> Gemma3ReportWriter
        hasResearch -> Gemma3ReportWriter
        else -> DeterministicReportWriter
    }

    /** True when the narrative came from a real on-device model rather than a fallback. */
    fun isNativeNarrative(writer: ReportWriter): Boolean = writer is Gemma3NativeReportWriter

    /**
     * Honest disclosure appended when no on-device model narrated the report.
     *
     * The deterministic body is complete and sealed either way; this states that
     * the prose appendix is absent and why, rather than leaving the reader to
     * assume the AI reviewed the evidence and found nothing worth saying.
     */
    const val UNAVAILABLE_NOTE: String =
        "AI narrative unavailable on this device — the on-device model is not " +
            "loaded (not downloaded, failed verification, or insufficient memory). " +
            "The sealed findings below are complete and unaffected; only the " +
            "explanatory prose is absent."
}
