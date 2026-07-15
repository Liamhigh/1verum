package com.verumomnis.forensic.engine.contradiction

/**
 * Verum Contradiction Engine v5.3.1c — Master Orchestrator.
 *
 * Seal: VO-CE-v531c-DIGSIM-20260713
 * Constitution: v6.0 Final
 * 52 types | 28 detectors | 26 serial patterns | 9 cases | B1-B11
 *
 * Dual interface:
 *  - processFromFiles(): reads files from disk (CLI/background)
 *  - processFromTexts(): creates atoms from text strings (Android UI)
 *
 * Full pipeline: atoms -> claims -> 16 detectors -> dedup ->
 * triple verification -> actor profiles -> corpus hash -> report.
 *
 * This is the Kotlin port of the Python v5.3.1c engine, adapted
 * to integrate with the existing NineBrainEngine as B1's
 * enhanced contradiction detection system.
 */
class VerumContradictionEngine(
    private val caseId: String = "VO-CASE-001",
    caseName: String = "",
    private val injectedTimestamp: Long? = null
) {
    private var caseConfig: CaseConfig = getCaseConfig(caseName)

    init {
        ContradictionDetectors.resetCounter()
    }

    /** Set case configuration by name ("allfuels", "greensky", "southbridge", "digsim", etc.). */
    fun setCase(caseName: String) {
        caseConfig = getCaseConfig(caseName)
    }

    /**
     * Process evidence from FILE PATHS.
     * For CLI and background processing.
     */
    fun processFromFiles(filePaths: List<String>): EngineForensicReport {
        // NOTE: On Android, file reading should use Storage Access Framework
        // This is a stub — production code should read via ContentResolver
        val atoms = mutableListOf<EngineEvidenceAtom>()
        for (fp in filePaths) {
            try {
                val content = java.io.File(fp).readText()
                atoms += ClaimExtractor.extractFromText(content, fp, injectedTimestamp)
            } catch (e: Exception) {
                // File read failed — log but continue
                android.util.Log.w("VerumEngine", "Failed to read $fp: ${e.message}")
            }
        }
        return runPipeline(atoms)
    }

    /**
     * Process evidence from TEXT CONTENT.
     * PRIMARY interface for Android UI integration.
     * No file I/O — creates atoms directly from text strings.
     */
    fun processFromTexts(texts: List<String>, sourceNames: List<String>? = null): EngineForensicReport {
        val names = sourceNames ?: texts.mapIndexed { i, _ -> "input_$i" }
        val atoms = mutableListOf<EngineEvidenceAtom>()
        for (i in texts.indices) {
            atoms += ClaimExtractor.extractFromText(texts[i], names[i], injectedTimestamp)
        }
        return runPipeline(atoms)
    }

    /** Shared pipeline — all entry points converge here. */
    private fun runPipeline(atoms: List<EngineEvidenceAtom>): EngineForensicReport {
        // Step 1: Extract claims
        val claims = ClaimExtractor.extractClaims(atoms, caseConfig)

        // Step 2: Detect all contradictions (28 detectors: 10 base + 6 DIGSIM + 12 ported)
        val contradictions = ContradictionDetectors.detectAll(claims)

        // Step 3: Triple verification
        val tv = TripleVerifier.verifyTriple(contradictions)

        // Step 4: Actor profiles
        val profiles = TripleVerifier.buildProfiles(claims, contradictions)

        // Step 5: Corpus hash
        val corpusHash = ClaimExtractor.hashCorpus(atoms.map { it.content })

        // Step 6: Assemble report
        return EngineForensicReport(
            caseId = caseId,
            contradictions = contradictions,
            actorProfiles = profiles,
            tripleVerification = tv,
            corpusHash = corpusHash,
            confidenceCalibration = ConfidenceCalibrator.reportCalibration(),
            generatedAt = injectedTimestamp ?: System.currentTimeMillis()
        )
    }
}

// ==================== CONVENIENCE FUNCTIONS ====================

/** One-shot contradiction detection from text strings. */
fun detectContradictions(
    texts: List<String>,
    caseId: String = "VO-CASE-001",
    caseName: String = "",
    injectedTimestamp: Long? = null
): EngineForensicReport {
    return VerumContradictionEngine(caseId, caseName, injectedTimestamp)
        .processFromTexts(texts)
}

/** One-shot contradiction detection from file paths. */
fun detectContradictionsFromFiles(
    filePaths: List<String>,
    caseId: String = "VO-CASE-001",
    caseName: String = "",
    injectedTimestamp: Long? = null
): EngineForensicReport {
    return VerumContradictionEngine(caseId, caseName, injectedTimestamp)
        .processFromFiles(filePaths)
}

/** Format a report to string. */
fun formatReport(report: EngineForensicReport, format: String = "txt"): String {
    return TripleVerifier.generateReport(report, format)
}
