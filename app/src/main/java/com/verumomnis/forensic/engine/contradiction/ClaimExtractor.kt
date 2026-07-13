package com.verumomnis.forensic.engine.contradiction

import java.security.MessageDigest

/**
 * Claim Extractor — v5.2.9.
 * Extracts evidence atoms from text, then converts to Claims
 * with subject/actor/statement-type detection.
 */
object ClaimExtractor {

    private val SUBJECT_KEYWORDS = mapOf(
        "GOODWILL_VALUE" to listOf("goodwill", "brand", "franchise", "entrenched"),
        "CONTRACT_VALIDITY" to listOf("contract", "agreement", "binding", "countersign", "lease"),
        "SIGNATURE_STATUS" to listOf("signature", "signed", "blank", "unsigned"),
        "SECTION_12B" to listOf("section 12B", "arbitration", "referral", "Business Zone"),
        "COMPENSATION" to listOf("fee", "payment", "rent", "compensation", "deposit"),
        "PERJURY" to listOf("perjury", "Constitutional Court", "sworn", "CCT", "affidavit"),
        "COERCION" to listOf("coerce", "threat", "intimidate", "suppress", "silence"),
        "RACKETEERING" to listOf("pattern", "systematic", "multiple victims", "scheme")
    )

    /** Extract evidence atoms from text content — no file I/O. */
    fun extractFromText(text: String, sourceName: String, injectedTs: Long? = null): List<EngineEvidenceAtom> {
        val md = MessageDigest.getInstance("SHA-512")
        return text.lineSequence()
            .mapIndexed { i, line -> i to line.trim() }
            .filter { (_, line) -> line.isNotEmpty() }
            .map { (i, line) ->
                EngineEvidenceAtom(
                    artifactHash = md.digest(line.toByteArray()).joinToString("") { "%02x".format(it) },
                    pageNumber = 0,
                    lineNumber = i + 1,
                    timestamp = injectedTs,
                    sourcePath = sourceName,
                    content = line,
                    fileType = EngineFileType.TXT
                )
            }
            .toList()
    }

    /** Hash a corpus of evidence texts for blockchain anchoring. */
    fun hashCorpus(texts: List<String>): String {
        val md = MessageDigest.getInstance("SHA-512")
        for (t in texts) md.update(t.toByteArray())
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Convert EvidenceAtoms to Claims with subject/actor/statement-type detection. */
    fun extractClaims(atoms: List<EngineEvidenceAtom>, caseConfig: CaseConfig? = null): List<EngineClaim> {
        return atoms.mapIndexed { i, atom ->
            val subject = detectSubject(atom.content, caseConfig)
            val actor = detectActor(atom.content, caseConfig)
            EngineClaim(
                id = "claim-${i + 1}",
                subject = subject,
                predicate = "states",
                value = atom.content,
                actor = actor,
                date = atom.timestamp,
                sourceType = detectStatementType(atom.content),
                sourceLocation = "${atom.sourcePath} p.${atom.pageNumber} line ${atom.lineNumber}",
                documentId = atom.sourcePath,
                sha512Hash = atom.artifactHash,
                pageNumber = atom.pageNumber
            )
        }
    }

    private fun detectSubject(text: String, caseConfig: CaseConfig?): String {
        val lower = text.lowercase()
        // Case config overrides
        caseConfig?.legalSubjects?.forEach { (subj, keywords) ->
            if (keywords.any { lower.contains(it.lowercase()) }) return subj.uppercase().replace(" ", "_")
        }
        // Default keyword detection
        SUBJECT_KEYWORDS.forEach { (subj, keywords) ->
            if (keywords.any { lower.contains(it.lowercase()) }) return subj
        }
        return "OTHER"
    }

    private fun detectActor(text: String, caseConfig: CaseConfig?): String {
        // Full name pattern
        Regex("""\\b([A-Z][a-z]+ [A-Z][a-z]+)\\b""").find(text)?.let { return it.value }
        // Case config entity keywords
        caseConfig?.entityKeywords?.forEach { entity ->
            if (text.lowercase().contains(entity.lowercase())) return entity
        }
        // First person
        Regex("""\\b(I|we|our company)\\b""", RegexOption.IGNORE_CASE).find(text)?.let {
            return it.value.replaceFirstChar { ch -> ch.uppercase() }
        }
        return "UNKNOWN"
    }

    private fun detectStatementType(text: String): EngineStatementType {
        val lower = text.lowercase()
        return when {
            lower.contains("sworn") || lower.contains("affidavit") || lower.contains("under oath") ->
                EngineStatementType.SWORN_STATEMENT
            lower.contains("admit") || lower.contains("confess") -> EngineStatementType.ADMISSION
            lower.contains("deny") || lower.contains("reject") || lower.contains("false") ->
                EngineStatementType.DENIAL
            lower.contains("demand") || lower.contains("require") -> EngineStatementType.DEMAND
            lower.contains("promise") || lower.contains("guarantee") -> EngineStatementType.PROMISE
            lower.contains("threat") || lower.contains("intimidate") -> EngineStatementType.THREAT
            lower.contains("clause") || lower.contains("section") || lower.contains("agreement") ->
                EngineStatementType.CONTRACT_CLAUSE
            lower.contains("court") || lower.contains("cct") || lower.contains("judgment") ->
                EngineStatementType.JUDICIAL_RECORD
            else -> EngineStatementType.CLAIM
        }
    }
}
