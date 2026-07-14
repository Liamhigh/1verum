package com.verumomnis.forensic.engine.contradiction

import kotlinx.serialization.Serializable

/**
 * Verum Omnis — Contradiction Detectors v6 (Companion Layer, Kotlin).
 * Status: RATIFIED — BINDING (founder directive, 2026-07-14).
 * Spec: CANDIDATE_CONTRADICTION_TYPES_v6.md
 *
 * Builds ONTO the sealed engine.  The engine is untouched; these detectors
 * operate on anchored text chunks and emit findings records that merge into a
 * findings document.
 *
 * Three detectors:
 *   1. SWORN_VS_SWORN              — cross-deponent perjury conflict
 *   2. DEVICE_ATTRIBUTION_CHAIN    — declaration-linked digital attribution
 *   3. CRIMINAL_CHARGE_AS_LEVERAGE — retaliatory prosecution pattern
 *
 * Plus the word-boundary precision fix ("lease" must not fire inside "please").
 * Every record is anchored: source document, page, SHA-512.  If it is not
 * anchored, it is not emitted.  Each record's legal hypothesis is enriched with
 * the local-statute anchor (person -> page -> local law).
 */
object V6Detectors {

    const val DETECTOR_VERSION = "v6-ratified-1.0.0"
    const val STATUS_ENGINE_VERIFIED = "ENGINE-VERIFIED"

    // -- Models --------------------------------------------------------------

    @Serializable
    data class V6TextChunk(
        val text: String,
        val source: String,
        val page: Int,
        val sha512: String,
    )

    @Serializable
    data class ActorLexiconEntry(
        val name: String,
        val aliases: List<String> = emptyList(),
        val role: String = "",
    )

    @Serializable
    data class V6LegalHypothesis(
        val suggestedOffence: String? = null,
        val legalBasis: String = "",
        val requiredAdditionalEvidence: List<String> = emptyList(),
        val isHypothesis: Boolean = true,
        val requiresHumanReview: Boolean = true,
        val jurisdiction: String? = null,
        val jurisdictionalNote: String? = null,
        val localStatutes: List<StatuteMap.StatuteCitation> = emptyList(),
    )

    @Serializable
    data class V6Finding(
        val contradictionId: String,
        val type: String,
        val severity: String,
        val confidence: String,
        val propositionAText: String,
        val propositionAActor: String,
        val propositionBText: String,
        val propositionBActor: String,
        val conflictDescription: String,
        val sourceDocument: String,
        val sourcePage: Int,
        val sha512Anchor: String,
        val extractionMethod: String,
        val patternType: String,
        val supportingFacts: List<String>,
        val legalHypothesis: V6LegalHypothesis? = null,
        val verificationStatus: String = STATUS_ENGINE_VERIFIED,
        val jurisdiction: String? = null,
    )

    // -- Word-boundary term matching (the precision fix) ---------------------

    private fun escapeRegex(s: String): String = Regex.escape(s)

    /** Whole-word / whole-phrase match.  "lease" does NOT match inside "please". */
    fun containsTerm(text: String, term: String): Boolean {
        val words = term.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        val pattern = words.joinToString("\\s+") { escapeRegex(it) }
        return Regex("\\b$pattern\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    fun findTerms(text: String, terms: List<String>): List<String> =
        terms.filter { containsTerm(text, it) }

    // -- Sentence + actor helpers --------------------------------------------

    private val SENTENCE_SPLIT_RE = Regex("(?<=[.!?])\\s+(?=[A-Z(\"'])")

    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "but", "that", "this", "with", "from",
        "into", "onto", "over", "under", "your", "their", "his", "her", "its",
        "our", "my", "we", "you", "they", "them", "then", "than", "when",
        "what", "which", "who", "whom", "will", "would", "could", "should",
        "have", "has", "had", "been", "being", "were", "was", "are", "is",
        "for", "not", "all", "any", "each", "such", "these", "those", "about",
    )

    private val NEGATORS = listOf(
        "no", "not", "never", "denied", "denies", "deny", "refused", "refuses",
        "false", "incorrect", "without", "failed to", "did not", "does not",
        "was not", "were not", "cannot", "didn't", "doesn't", "wasn't",
    )

    fun sentences(text: String): List<String> {
        val out = mutableListOf<String>()
        for (rawLine in text.split(Regex("\\r?\\n"))) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            out += SENTENCE_SPLIT_RE.split(line).filter { it.isNotBlank() }
        }
        return out
    }

    fun contentWords(text: String): List<String> {
        val words = Regex("[a-z]{4,}").findAll(text.lowercase()).map { it.value }
        return words.filter { it !in STOP_WORDS }.toList()
    }

    fun jaccard(a: List<String>, b: List<String>): Double {
        val sa = a.toSet(); val sb = b.toSet()
        if (sa.isEmpty() || sb.isEmpty()) return 0.0
        val inter = sa.intersect(sb).size
        return inter.toDouble() / (sa.size + sb.size - inter).toDouble()
    }

    fun negationAsymmetry(a: String, b: String): Boolean {
        val aNeg = findTerms(a, NEGATORS).isNotEmpty()
        val bNeg = findTerms(b, NEGATORS).isNotEmpty()
        return aNeg != bNeg
    }

    /** First lexicon entry whose name or alias appears as whole word(s). */
    fun attributeActor(text: String, lexicon: List<ActorLexiconEntry>): String? {
        for (entry in lexicon) {
            for (cand in listOf(entry.name) + entry.aliases) {
                if (cand.isNotEmpty() && containsTerm(text, cand)) return entry.name
            }
        }
        return null
    }

    // -- Record factory -------------------------------------------------------

    private var counter = 0
    fun resetCounter() { counter = 0 }
    private fun nextId(prefix: String = "V6"): String {
        counter += 1
        return "$prefix-C-${counter.toString().padStart(4, '0')}"
    }

    private fun makeRecord(
        type: String,
        severity: String,
        confidence: String,
        aText: String,
        aActor: String,
        bText: String,
        bActor: String,
        description: String,
        chunk: V6TextChunk,
        patternType: String,
        supporting: List<String>,
        hypothesis: V6LegalHypothesis?,
        secondChunk: V6TextChunk? = null,
    ): V6Finding {
        val sourceDocument =
            if (secondChunk != null && secondChunk.source != chunk.source)
                "${chunk.source} + ${secondChunk.source}"
            else chunk.source
        return V6Finding(
            contradictionId = nextId(),
            type = type,
            severity = severity,
            confidence = confidence,
            propositionAText = aText.trim(),
            propositionAActor = aActor,
            propositionBText = bText.trim(),
            propositionBActor = bActor,
            conflictDescription = description,
            sourceDocument = sourceDocument,
            sourcePage = chunk.page,
            sha512Anchor = chunk.sha512,
            extractionMethod = "deterministic detector ($DETECTOR_VERSION)",
            patternType = patternType,
            supportingFacts = supporting,
            legalHypothesis = hypothesis,
        )
    }

    // -- Detector 1: SWORN_VS_SWORN ------------------------------------------

    private val SWORN_MARKERS = listOf(
        "affidavit", "sworn", "under oath", "solemnly declare", "solemn declaration",
        "deponent", "sworn statement", "oath", "commissioner of oaths",
    )

    fun detectSwornVsSworn(
        chunks: List<V6TextChunk>,
        actors: List<ActorLexiconEntry>,
        minSubjectOverlap: Double = 0.25,
    ): List<V6Finding> {
        data class Claim(val text: String, val actor: String, val chunk: V6TextChunk, val words: List<String>)
        val sworn = mutableListOf<Claim>()
        for (chunk in chunks) for (sent in sentences(chunk.text)) {
            if (findTerms(sent, SWORN_MARKERS).isEmpty()) continue
            val actor = attributeActor(sent, actors) ?: continue
            sworn += Claim(sent, actor, chunk, contentWords(sent))
        }
        val out = mutableListOf<V6Finding>()
        val seen = mutableSetOf<String>()
        for (i in sworn.indices) for (j in i + 1 until sworn.size) {
            val a = sworn[i]; val b = sworn[j]
            if (a.actor == b.actor) continue
            if (jaccard(a.words, b.words) < minSubjectOverlap) continue
            if (!negationAsymmetry(a.text, b.text)) continue
            val key = "${a.actor}|${b.actor}|${a.text.take(40)}|${b.text.take(40)}"
            if (!seen.add(key)) continue
            out += makeRecord(
                type = StatuteMap.TYPE_SWORN_VS_SWORN,
                severity = "VERY_HIGH", confidence = "HIGH",
                aText = a.text, aActor = a.actor, bText = b.text, bActor = b.actor,
                description = "Sworn statements conflict: ${a.actor} and ${b.actor} made opposing " +
                    "sworn assertions on the same material fact. One statement is necessarily false under oath.",
                chunk = a.chunk, secondChunk = b.chunk,
                patternType = "CROSS_DEPONENT_SWORN_CONFLICT",
                supporting = listOf(a.text.trim(), b.text.trim()),
                hypothesis = V6LegalHypothesis(
                    suggestedOffence = "Perjury",
                    legalBasis = "Two mutually exclusive sworn statements on one material fact; one deponent has sworn falsely.",
                    requiredAdditionalEvidence = listOf(
                        "Both sworn documents in original form",
                        "Proof of oath administration for each",
                        "Contemporaneous documentary evidence resolving the fact",
                    ),
                ),
            )
        }
        return out
    }

    // -- Detector 2: DEVICE_ATTRIBUTION_CHAIN --------------------------------

    private const val ENTITY_TERMINATOR = "(?=[\"'‘’“”.,;:!?)…]|$)"

    private val DECLARATION_RES = listOf(
        Regex(
            "\\b(?:i|we)\\s+(?:own|have|run|operate|control)\\s+(?:a\\s+)?" +
                "(?:pty|company|business|firm|entity|ltd|llc|fz-llc)?\\s*" +
                "(?:called|named)?\\s*[\"'“]?([A-Z][A-Za-z0-9 &\\-]{2,60}?)" + ENTITY_TERMINATOR,
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            "\\bmy\\s+(?:company|business|pty|firm)\\s+(?:called|named|is)?\\s*" +
                "[\"'“]?([A-Z][A-Za-z0-9 &\\-]{2,60}?)" + ENTITY_TERMINATOR,
            RegexOption.IGNORE_CASE,
        ),
    )

    private val ADVERSE_ACT_MARKERS = listOf(
        "hack", "hacked", "hacking", "interception", "intercepted", "unauthorised access",
        "unauthorized access", "archive attempt", "breach", "intrusion", "traced to",
        "remote access", "login attempt", "malware", "spyware", "compromised",
    )

    private val DEVICE_REF_RE = Regex(
        "\\b(?:device|account|hostname|profile|handset|computer|machine)\\b" +
            "[^\"'‘’“”.,;:]{0,40}?[\"'‘’“”]([A-Z0-9_\\-]{4,40})[\"'‘’“”]",
        RegexOption.IGNORE_CASE,
    )

    private fun normaliseIdentifier(value: String): String =
        value.uppercase().replace(Regex("[^A-Z0-9]"), "")

    private fun entityMatchesDevice(entity: String, device: String): Boolean {
        val ent = normaliseIdentifier(entity); val dev = normaliseIdentifier(device)
        if (ent.isEmpty() || dev.isEmpty()) return false
        if (dev.contains(ent) || ent.contains(dev)) return true
        return Regex("[A-Za-z]{6,}").findAll(entity).any { dev.contains(it.value.uppercase()) }
    }

    fun detectDeviceAttributionChain(
        chunks: List<V6TextChunk>,
        actors: List<ActorLexiconEntry>,
    ): List<V6Finding> {
        data class Decl(val entity: String, val text: String, val actor: String?, val chunk: V6TextChunk)
        data class Act(val device: String, val text: String, val chunk: V6TextChunk)
        val declarations = mutableListOf<Decl>()
        val adverseActs = mutableListOf<Act>()
        for (chunk in chunks) for (sent in sentences(chunk.text)) {
            for (rx in DECLARATION_RES) {
                rx.find(sent)?.let { m ->
                    declarations += Decl(
                        m.groupValues[1].trim(), sent, attributeActor(sent, actors), chunk,
                    )
                }
            }
            if (findTerms(sent, ADVERSE_ACT_MARKERS).isNotEmpty()) {
                DEVICE_REF_RE.find(sent)?.let { dm ->
                    adverseActs += Act(dm.groupValues[1], sent, chunk)
                }
            }
        }
        val out = mutableListOf<V6Finding>()
        val seen = mutableSetOf<String>()
        for (decl in declarations) for (act in adverseActs) {
            if (!entityMatchesDevice(decl.entity, act.device)) continue
            if (!seen.add("${decl.entity}|${act.device}")) continue
            val actorName = decl.actor ?: "declarant"
            out += makeRecord(
                type = StatuteMap.TYPE_DEVICE_ATTRIBUTION_CHAIN,
                severity = "HIGH", confidence = "MODERATE",
                aText = decl.text, aActor = actorName,
                bText = act.text, bActor = "device '${act.device}'",
                description = "Attribution chain: $actorName declared ownership of '${decl.entity}'; " +
                    "device/account '${act.device}' - named for that entity - performed an adverse " +
                    "digital act. Attribution is corroborating, not conclusive.",
                chunk = decl.chunk, secondChunk = act.chunk,
                patternType = "DECLARATION_LINKED_DEVICE_ATTRIBUTION",
                supporting = listOf(decl.text.trim(), act.text.trim()),
                hypothesis = V6LegalHypothesis(
                    suggestedOffence = "Unauthorised access / interception of data",
                    legalBasis = "Declaration links the named actor to the device that performed the adverse act.",
                    requiredAdditionalEvidence = listOf(
                        "The declaration in original form (message/export)",
                        "Device or account records tying the identifier to the actor",
                        "Logs of the adverse act with timestamps and source addresses",
                    ),
                ),
            )
        }
        return out
    }

    // -- Detector 3: CRIMINAL_CHARGE_AS_LEVERAGE -----------------------------

    private val CHARGE_MARKERS = listOf(
        "opened a case", "laid a charge", "laid charges", "criminal charge",
        "charged with", "case number", "theft case", "criminal case",
        "police case", "opened a criminal case",
    )

    private val CASE_NUMBER_RE = Regex("\\bCAS\\s*\\d{1,5}/\\d{1,2}/\\d{4}\\b", RegexOption.IGNORE_CASE)

    private val CIVIL_DISPUTE_MARKERS = listOf(
        "civil", "applicant", "respondent", "high court", "litigation",
        "application", "counterclaim", "commercial dispute", "fraud claim",
    )

    private val LEVERAGE_MARKERS = listOf(
        "silence", "silenced", "pressure", "leverage", "retaliation", "retaliatory",
        "intimidate", "intimidation", "withdraw", "drop the case", "back down",
    )

    private val DATE_RE = Regex(
        "\\b(\\d{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{4}|\\d{4}-\\d{2}-\\d{2})\\b",
        RegexOption.IGNORE_CASE,
    )

    fun detectCriminalChargeAsLeverage(
        chunks: List<V6TextChunk>,
        actors: List<ActorLexiconEntry>,
        windowSentences: Int = 3,
    ): List<V6Finding> {
        data class S(val text: String, val chunk: V6TextChunk)
        val flat = mutableListOf<S>()
        for (chunk in chunks) for (sent in sentences(chunk.text)) flat += S(sent, chunk)

        val out = mutableListOf<V6Finding>()
        val seen = mutableSetOf<String>()
        val corpusHasCivil = flat.any { findTerms(it.text, CIVIL_DISPUTE_MARKERS).isNotEmpty() }

        for (idx in flat.indices) {
            val s = flat[idx]
            val chargeTerms = findTerms(s.text, CHARGE_MARKERS)
            val caseNo = CASE_NUMBER_RE.find(s.text)
            if (chargeTerms.isEmpty() && caseNo == null) continue

            val lo = maxOf(0, idx - windowSentences)
            val hi = minOf(flat.size, idx + windowSentences + 1)
            val window = flat.subList(lo, hi)
            val leverageHit = window.firstOrNull { findTerms(it.text, LEVERAGE_MARKERS).isNotEmpty() }
            val civilHit = window.firstOrNull { findTerms(it.text, CIVIL_DISPUTE_MARKERS).isNotEmpty() }
            if (leverageHit == null && !(corpusHasCivil && civilHit != null)) continue

            if (!seen.add(s.text.take(60))) continue
            val chargeActor = attributeActor(s.text, actors) ?: "unattributed"
            val dates = DATE_RE.find(s.text)
            val anchor = leverageHit ?: civilHit ?: s
            val basis = if (leverageHit != null)
                "silencing language accompanies the charge"
            else "charge sits inside an ongoing civil dispute between the same parties"

            out += makeRecord(
                type = StatuteMap.TYPE_CRIMINAL_CHARGE_AS_LEVERAGE,
                severity = "VERY_HIGH", confidence = "MODERATE",
                aText = s.text, aActor = chargeActor,
                bText = anchor.text, bActor = "case context",
                description = "Criminal charge used as leverage in a civil dispute: " + basis +
                    (caseNo?.let { ". Charge reference: ${it.value}" } ?: "") +
                    (dates?.let { ". Dated: ${it.groupValues[1]}" } ?: ""),
                chunk = s.chunk, secondChunk = anchor.chunk,
                patternType = "CRIMINAL_CHARGE_AS_CIVIL_LEVERAGE",
                supporting = listOf(s.text.trim(), anchor.text.trim()),
                hypothesis = V6LegalHypothesis(
                    suggestedOffence = "Defeating or obstructing the course of justice / abuse of process",
                    legalBasis = "Criminal process invoked by an opposing civil litigant for leverage rather than legitimate prosecution.",
                    requiredAdditionalEvidence = listOf(
                        "The criminal docket / charge sheet",
                        "Timeline of civil-case milestones vs charge date",
                        "Outcome of the criminal charge (withdrawal, nolle prosequi)",
                    ),
                ),
            )
        }
        return out
    }

    // -- Runner + enrichment --------------------------------------------------

    /**
     * Run all three v6 detectors; return deduplicated findings records.
     * If [jurisdiction] is non-null, each record's legal hypothesis is enriched
     * with the local-statute anchor so the forensic chain closes on local law.
     */
    fun runV6Detectors(
        chunks: List<V6TextChunk>,
        actors: List<ActorLexiconEntry>,
        jurisdiction: String? = StatuteMap.DEFAULT_JURISDICTION,
    ): List<V6Finding> {
        val results = buildList {
            addAll(detectSwornVsSworn(chunks, actors))
            addAll(detectDeviceAttributionChain(chunks, actors))
            addAll(detectCriminalChargeAsLeverage(chunks, actors))
        }
        val seen = mutableSetOf<String>()
        val unique = mutableListOf<V6Finding>()
        for (r in results) {
            val key = "${r.type}|${r.propositionAText.take(50)}|${r.propositionBText.take(50)}"
            if (seen.add(key)) unique += r
        }
        if (jurisdiction == null) return unique
        val j = StatuteMap.normaliseJurisdiction(jurisdiction)
        return unique.map { r -> enrich(r, j) }
    }

    /** Attach jurisdiction + local statutes to one finding's legal hypothesis. */
    fun enrich(finding: V6Finding, jurisdiction: String): V6Finding {
        val j = StatuteMap.normaliseJurisdiction(jurisdiction)
        val base = finding.legalHypothesis ?: V6LegalHypothesis(legalBasis = finding.conflictDescription)
        val enriched = base.copy(
            jurisdiction = j,
            jurisdictionalNote = StatuteMap.jurisdictionalNote(j),
            localStatutes = StatuteMap.statutesForType(finding.type, j),
            isHypothesis = true,
            requiresHumanReview = true,
        )
        return finding.copy(legalHypothesis = enriched, jurisdiction = j)
    }
}
