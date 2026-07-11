package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.BehavioralAnalysis
import com.verumomnis.forensic.model.BehavioralFinding
import com.verumomnis.forensic.model.Severity
import java.util.Locale

/**
 * B4 Behavioral Brain (build spec Section 12). Deterministic, text-based detection
 * of gaslighting, stress signals and manipulation patterns.
 */
object BehavioralBrain {

    private val GASLIGHTING = listOf(
        "that never happened", "i never said that", "you're imagining", "you are imagining",
        "you're misremembering", "you are misremembering", "that was never agreed", "you're overreacting"
    )
    private val STRESS_MARKERS = listOf(
        "!!!", "???", "please", "help", "urgent", "desperate", "broken", "destroyed", "devastated",
        "mentally broken", "can't take", "cannot take"
    )
    private val ISOLATION = listOf(
        "just the two of us", "don't bring", "alone", "private meeting", "no lawyers", "no witnesses", "removed", "witness"
    )
    private val DISMISSIVE = listOf(
        "you're overreacting", "calm down", "it's just business", "don't take it personally", "you're too emotional"
    )

    fun analyze(documents: List<EvidenceDocument>): BehavioralAnalysis {
        val gaslighting = mutableListOf<BehavioralFinding>()
        val stress = mutableListOf<BehavioralFinding>()
        val manipulation = mutableListOf<BehavioralFinding>()

        documents.forEach { doc ->
            val lower = doc.text.lowercase(Locale.ROOT)
            GASLIGHTING.forEach { p ->
                if (lower.contains(p)) gaslighting += finding("FACT_DENIAL", p, doc, Severity.HIGH)
            }
            STRESS_MARKERS.forEach { p ->
                if (lower.contains(p)) stress += finding("STRESS_SIGNAL", p, doc, Severity.MODERATE)
            }
            ISOLATION.forEach { p ->
                if (lower.contains(p)) manipulation += finding("ISOLATION_TACTIC", p, doc, Severity.CRITICAL)
            }
            DISMISSIVE.forEach { p ->
                if (lower.contains(p)) manipulation += finding("DISMISSIVE_LANGUAGE", p, doc, Severity.HIGH)
            }
        }

        val score = minOf(
            1.0,
            gaslighting.size * 0.15 + stress.size * 0.05 + manipulation.size * 0.20
        )
        return BehavioralAnalysis(gaslighting, stress, manipulation, score)
    }

    private fun finding(type: String, trigger: String, doc: EvidenceDocument, severity: Severity): BehavioralFinding {
        val idx = doc.text.lowercase(Locale.ROOT).indexOf(trigger)
        val context = if (idx >= 0) {
            val start = (idx - 30).coerceAtLeast(0)
            val end = (idx + trigger.length + 30).coerceAtMost(doc.text.length)
            doc.text.substring(start, end).replace("\n", " ").trim()
        } else trigger
        return BehavioralFinding(type, trigger, context, severity, doc.evidenceId, 1)
    }
}
