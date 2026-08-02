package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.ForensicFindings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Pluggable report narrative writer.
 *
 * The deterministic engine produces the sealed body. A [ReportWriter] may
 * additionally produce a Gemma-style narrative appendix from the structured
 * findings. The appendix is NOT part of the cryptographic seal, so the seal
 * remains deterministic and reproducible.
 */
interface ReportWriter {
    /** Generate a narrative prose section from the extracted findings. */
    fun writeNarrative(findings: ForensicFindings, caseName: String, findingsJsonPath: String = ""): String
}

/**
 * Default deterministic writer: returns an empty narrative so the sealed report
 * body is the sole source of truth. Always safe, always reproducible.
 *
 * When a [findingsJsonPath] is supplied, the writer also appends an explicit
 * G3 candidate tier section so any G3-raised candidates are labelled in the
 * report narrative without contaminating the sealed body.
 */
object DeterministicReportWriter : ReportWriter {
    override fun writeNarrative(findings: ForensicFindings, caseName: String, findingsJsonPath: String): String {
        val candidates = readG3Candidates(findingsJsonPath)
        if (candidates.isEmpty()) return ""
        return buildString {
            appendLine()
            appendLine("G3-RAISED CANDIDATE TIER")
            appendLine("The following contradictions were raised by Gemma 3 during vault review.")
            appendLine("They are anchored and hashed but pending engine or human verification.")
            appendLine("They must never be presented as engine-verified findings.")
            appendLine()
            candidates.forEachIndexed { index, record ->
                appendLine("${index + 1}. [${record.severity}] ${record.contradiction_id} — ${record.type}")
                appendLine("   Proposition A (${record.proposition_a_actor}): \"${record.proposition_a_text}\"")
                appendLine("   Proposition B (${record.proposition_b_actor}): \"${record.proposition_b_text}\"")
                appendLine("   Conflict: ${record.conflict_description}")
                appendLine("   Source: ${record.source_document} p${record.source_page}")
                appendLine("   Anchor: SHA-512 ${record.sha512_anchor.take(16)}…")
                appendLine("   Status: ${record.verification_status}")
                appendLine()
            }
        }
    }

    private fun readG3Candidates(path: String): List<FindingsJsonEmitter.FindingsJsonRecord> {
        if (path.isBlank()) return emptyList()
        val file = File(path)
        if (!file.exists()) return emptyList()
        return try {
            val document = Json.decodeFromString(FindingsJsonEmitter.FindingsJsonDocument.serializer(), file.readText())
            document.contradictions.filter { it.verification_status == FindingsJsonEmitter.STATUS_G3_CANDIDATE }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/**
 * Gemma 3 narrative writer.
 *
 * When an on-device Gemma 3 runtime is installed (see
 * [com.verumomnis.forensic.llm.Gemma3RuntimeProvider]), [writeNarrative] runs
 * real inference over the structured prompt from [buildPrompt] and returns the
 * human-readable forensic narrative, with the deterministic G3 candidate tier
 * appended so pending candidates stay visibly labelled. When no runtime is
 * available it falls back to [DeterministicReportWriter] so the report never
 * contains model-shaped placeholder text. The narrative is an appendix and is
 * never part of the cryptographic seal.
 */
object Gemma3ReportWriter : ReportWriter {

    override fun writeNarrative(findings: ForensicFindings, caseName: String, findingsJsonPath: String): String {
        val deterministic = DeterministicReportWriter.writeNarrative(findings, caseName, findingsJsonPath)
        val runtime = com.verumomnis.forensic.llm.Gemma3RuntimeProvider.runtime
        if (!runtime.isAvailable()) return deterministic
        val narrative = runtime.generate(buildPrompt(findings, caseName, findingsJsonPath), maxTokens = 4096)
        if (narrative.isNullOrBlank()) return deterministic
        return buildString {
            appendLine("NARRATIVE ANALYSIS (Gemma 3 — unsealed appendix)")
            appendLine(narrative.trim())
            if (deterministic.isNotBlank()) {
                appendLine()
                append(deterministic)
            }
        }
    }

    /** Construct the structured, bounded prompt described in PROMPT.md Section 7. */
    fun buildPrompt(findings: ForensicFindings, caseName: String, findingsJsonPath: String = ""): String = buildString {
        appendLine("Write a detailed forensic analysis report.")
        appendLine("CASE: $caseName")
        appendLine("JURISDICTION: ${findings.jurisdiction}")
        appendLine("DOCUMENTS ANALYZED: ${findings.documentsAnalyzed}")
        appendLine("CONTRADICTIONS: ${findings.contradictions.size}")
        appendLine("PERSONS EXTRACTED:")
        findings.extractedPersons.take(12).forEach { p ->
            appendLine("  - ${p.name}${p.age?.let { " · age $it" } ?: ""} · ${p.role}${p.idNumber?.let { " · ID $it" } ?: ""}")
        }
        appendLine("LEGAL FRAMEWORK:")
        findings.legalMappings.forEach { appendLine("  - $it") }
        appendLine("TOP CONTRADICTIONS:")
        findings.contradictions.take(8).forEach { c ->
            appendLine("  [${c.severity}] ${c.contradictionId} · ${c.respondent}")
            appendLine("    A: \"${c.claimA.text}\"")
            appendLine("    B: \"${c.claimB.text}\"")
            appendLine("    Law: ${c.applicableLaw.joinToString("; ")}")
        }
        findings.financial?.let { f ->
            appendLine("FINANCIAL:")
            f.companyTax?.let { appendLine("  Tax liability: R%,.2f".format(it.taxLiability)) }
            f.flaggedAnomalies.forEach { appendLine("  Anomaly: $it") }
        }
        appendLine("G3 CANDIDATE TIER RULE:")
        appendLine("- Any contradiction you spot that the engine did not emit must be labelled G3-RAISED CANDIDATE and anchored/hashed.")
        appendLine("- Candidates stay separate from engine-verified findings.")
        appendLine("- Flag extraction gaps. Never write around holes.")
        if (findingsJsonPath.isNotBlank()) {
            appendLine("FINDINGS JSON PATH: $findingsJsonPath")
        }
        appendLine("Write the report now. Professional forensic language. Be specific about findings. Reference exact amounts and dates. Anchor every claim to the persons and statutes above.")
    }
}
