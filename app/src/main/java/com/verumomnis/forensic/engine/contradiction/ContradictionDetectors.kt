package com.verumomnis.forensic.engine.contradiction

import com.verumomnis.forensic.update.DownloadedRules
import java.util.concurrent.atomic.AtomicInteger

/**
 * 28 Contradiction Detectors — v5.3.1c.
 * 10 base detectors (v5.2.9) + 6 DIGSIM detectors (v5.3.1c) + 12 ported high-value detectors.
 * Results are deduplicated and sorted by severity.
 *
 * Additive hook: [downloadedRulesProvider] feeds an optional extra detector
 * ([detectDownloadedFraudPairs]) driven by signature-verified downloaded rules.
 * It can only ADD findings; the built-in detectors are never modified by it.
 */
object ContradictionDetectors {

    /**
     * Upper bound on how many contradictions a single detector may emit for one
     * evidence set.
     *
     * The pairwise detectors are O(n²) in claim count, and each contradiction
     * embeds both claims' full text four times over (factText, propositionA/B,
     * conflictDescription and the logical pattern's facts). A large scanned
     * bundle therefore used to exhaust the heap inside the detector loop —
     * `OutOfMemoryError` in createContradiction, taking the whole seal down.
     *
     * `detectAll` collapses each detector's output to one entry per
     * (actorA, actorB, type, pattern) tuple, so a bound this high cannot change
     * a real report: it only stops a pathological bundle from allocating
     * millions of throwaway objects before the dedup ever runs.
     */
    const val MAX_CONTRADICTIONS_PER_DETECTOR: Int = 2000

    private val counter = AtomicInteger(0)
    fun resetCounter() { counter.set(0) }
    private fun nextId(): String = "C-${counter.incrementAndNext().toString().padStart(4, '0')}"
    private fun AtomicInteger.incrementAndNext(): Int {
        incrementAndGet()
        return get()
    }

    // ==================== SIGNED RULE UPDATES (additive only) ====================

    /**
     * Signature-verified rules downloaded from the Verum rules service, wired
     * once at app start (see update/RuleRegistry + update/RuleUpdateClient).
     *
     * What downloaded rules DO affect: [detectDownloadedFraudPairs] appends
     * BEHAVIORAL contradictions when two claims (same actor or same subject)
     * contain opposite sides of a downloaded fraud-keyword phrase pair
     * (patternType "DOWNLOADED_RULE_<id>"). These can only ADD findings.
     *
     * What they DO NOT affect: all built-in keyword lists below stay hardcoded
     * and unchanged — downloaded rules never remove, replace, or reweight
     * built-in detection. Downloaded single keywords, behavioral markers,
     * serial patterns and case configs are NOT executed by this engine.
     *
     * When this returns null (fresh install / no verified package yet),
     * [detectAll] behaves exactly as before this hook existed.
     */
    @Volatile
    var downloadedRulesProvider: () -> DownloadedRules? = { null }

    // ==================== HELPERS ====================

    private fun severityScore(claimA: EngineClaim, claimB: EngineClaim, base: Int = 0): EngineSeverity {
        var score = base
        if (claimA.sourceType == EngineStatementType.SWORN_STATEMENT || claimB.sourceType == EngineStatementType.SWORN_STATEMENT) score += 40
        if (claimA.sourceType == EngineStatementType.CONTEMPORANEOUS || claimB.sourceType == EngineStatementType.CONTEMPORANEOUS) score += 30
        if (claimA.sourceType == EngineStatementType.ADMISSION || claimB.sourceType == EngineStatementType.ADMISSION) score += 20
        if (claimA.subject == "GOODWILL_VALUE" || claimB.subject == "GOODWILL_VALUE") score += 15
        return when {
            score >= 70 -> EngineSeverity.VERY_HIGH
            score >= 50 -> EngineSeverity.HIGH
            score >= 30 -> EngineSeverity.MODERATE
            score >= 10 -> EngineSeverity.LOW
            else -> EngineSeverity.INSUFFICIENT
        }
    }

    private val NEGATIONS = listOf(
        "no " to "", "not " to "", "false" to "true", "deny" to "admit",
        "never" to "always", "did not" to "did", "does not" to "does"
    )
    private val OPPOSITE_WORDS = setOf("no", "not", "never", "false", "deny", "refuse", "none")

    private fun isOpposing(a: EngineClaim, b: EngineClaim): Boolean {
        val textA = a.value.lowercase()
        val textB = b.value.lowercase()
        for ((neg, _) in NEGATIONS) {
            if (neg in textA && neg !in textB && a.subject == b.subject) return true
            if (neg in textB && neg !in textA && a.subject == b.subject) return true
        }
        val wordsA = textA.split(Regex("\\s+")).toSet()
        val wordsB = textB.split(Regex("\\s+")).toSet()
        if (wordsA.isNotEmpty() && wordsB.isNotEmpty()) {
            val overlap = wordsA.intersect(wordsB).size
            val union = wordsA.union(wordsB).size
            if (union > 0 && overlap.toDouble() / union < 0.2) {
                if (wordsA.any { it in OPPOSITE_WORDS } || wordsB.any { it in OPPOSITE_WORDS }) return true
            }
        }
        return a.subject == b.subject && a.predicate == b.predicate && a.value != b.value
    }

    private fun createContradiction(
        claimA: EngineClaim, claimB: EngineClaim,
        cType: EngineContradictionType, severity: EngineSeverity,
        baseConfidence: EngineConfidence,
        patternType: String, description: String,
        facts: List<String>, score: Double
    ): EngineContradiction {
        val fact = DetectedFact(
            factText = "Actor \"${claimA.actor}\" stated: \"${claimA.value}\" | Actor \"${claimB.actor}\" stated: \"${claimB.value}\"",
            sourceDocument = "${claimA.documentId} + ${claimB.documentId}",
            sourcePage = claimA.pageNumber,
            sourceLine = 0,
            sha512Hash = claimA.sha512Hash,
            extractionMethod = patternType,
            confidence = baseConfidence
        )
        // No version literal here: LogicalPattern defaults to EngineVersion.TAGGED,
        // so the stamp cannot drift from the engine that produced it.
        val pattern = LogicalPattern(patternType, description, facts, score)
        val hypothesis = when (cType) {
            EngineContradictionType.JUDICIAL_VS_DOCUMENTARY,
            EngineContradictionType.PERJURY_BY_TIMELINE,
            EngineContradictionType.FALSE_ALLEGATION_IN_AFFIDAVIT -> LegalHypothesis(
                suggestedOffence = "Perjury / Fraud on the Court",
                legalBasis = "Contradiction between sworn statement and documentary evidence",
                jurisdictionalNote = "Varies by jurisdiction — requires legal review",
                requiredAdditionalEvidence = listOf(
                    "Sworn statement transcript", "Original documentary evidence", "Authentication of documents"
                )
            )
            EngineContradictionType.DEFECTIVE_JURAT -> LegalHypothesis(
                suggestedOffence = "Fraudulent Affidavit / Defective Jurat",
                legalBasis = "Affidavit filed without mandatory jurat elements — no oath, no commissioner",
                jurisdictionalNote = "Perjury Act, Justices of the Peace Act — varies by jurisdiction",
                requiredAdditionalEvidence = listOf(
                    "Original affidavit with jurat section", "Commissioner appointment records", "Oath administration log"
                )
            )
            EngineContradictionType.PROTECTION_ORDER_AS_LEVERAGE -> LegalHypothesis(
                suggestedOffence = "Abuse of Process / Protection from Harassment Act Misuse",
                legalBasis = "Protection order used as leverage in commercial dispute",
                jurisdictionalNote = "Protection from Harassment Act 17 of 2011 (ZA) or equivalent",
                requiredAdditionalEvidence = listOf(
                    "Protection order application", "Commercial dispute documentation", "Timeline of threats vs applications"
                )
            )
            EngineContradictionType.PROCESS_REMEDY_CONFLICT -> LegalHypothesis(
                suggestedOffence = "Denial of Effective Remedy / ICCPR Article 2(3) Violation",
                legalBasis = "Institution with mandatory duty to respond remains silent or denies remedy",
                jurisdictionalNote = "ICCPR Art 2(3), UDHR Art 8 — international human rights law",
                requiredAdditionalEvidence = listOf(
                    "Statutory duty to respond", "Submission records", "Bounce/denial documentation"
                )
            )
            EngineContradictionType.ACKNOWLEDGE_THEN_DENY -> LegalHypothesis(
                suggestedOffence = "Fraud / Consciousness of Guilt",
                legalBasis = "Actor admits or acknowledges a fact and later denies it",
                jurisdictionalNote = "Varies by jurisdiction — requires legal review",
                requiredAdditionalEvidence = listOf(
                    "Original admission/acknowledgment", "Subsequent denial in same actor's words", "Timeline of both statements"
                )
            )
            EngineContradictionType.NO_COUNTERSIGNATURE_TRAP -> LegalHypothesis(
                suggestedOffence = "Fraudulent Contract Enforcement / Unilateral Document Trap",
                legalBasis = "Enforcing or relying on an agreement that was never countersigned by the enforcing party",
                jurisdictionalNote = "Contract law — signature requirements vary by jurisdiction",
                requiredAdditionalEvidence = listOf(
                    "Unsigned/countersignature page", "Correspondence requesting countersignature", "Enforcement demands"
                )
            )
            EngineContradictionType.GOODWILL_FORFEITURE_SWINDLE -> LegalHypothesis(
                suggestedOffence = "Fraud / Unlawful Expropriation of Goodwill",
                legalBasis = "Demanding forfeiture of goodwill while simultaneously extracting value from it",
                jurisdictionalNote = "Franchise and commercial law — varies by jurisdiction",
                requiredAdditionalEvidence = listOf(
                    "Valuation of goodwill", "Forfeiture clause", "Payments collected on basis of goodwill value"
                )
            )
            EngineContradictionType.MANUFACTURED_CONSENT -> LegalHypothesis(
                suggestedOffence = "Fraud / Fabricated Consent",
                legalBasis = "Claim of consent contradicted by contemporaneous evidence of reluctance or refusal",
                jurisdictionalNote = "Contract and consumer protection law — varies by jurisdiction",
                requiredAdditionalEvidence = listOf(
                    "Contemporaneous communications showing true position", "Signed document", "Witness accounts"
                )
            )
            EngineContradictionType.FABRICATED_DECOY_EVIDENCE -> LegalHypothesis(
                suggestedOffence = "Forgery / Fabrication of Evidence / Fraud on the Court",
                legalBasis = "Decoy or forged evidence introduced to mislead proceedings or counterparties",
                jurisdictionalNote = "Criminal and procedural law — varies by jurisdiction",
                requiredAdditionalEvidence = listOf(
                    "Original authentic record", "Forensic comparison of disputed exhibit", "Metadata and chain of custody"
                )
            )
            EngineContradictionType.DATA_BREACH_ENABLED_FRAUD -> LegalHypothesis(
                suggestedOffence = "Cybercrime / Unauthorized Access / Data-Breach Facilitated Fraud",
                legalBasis = "Fraudulent transaction or intrusion enabled by prior unauthorized data access",
                jurisdictionalNote = "Cybercrime statutes — varies by jurisdiction",
                requiredAdditionalEvidence = listOf(
                    "Access logs", "Device/location attribution", "Unauthorized transaction records"
                )
            )
            EngineContradictionType.SPOLIATION_OF_EVIDENCE -> LegalHypothesis(
                suggestedOffence = "Spoliation of Evidence / Obstruction",
                legalBasis = "Intentional destruction, deletion, or concealment of potentially relevant evidence",
                jurisdictionalNote = "Procedural and criminal law — varies by jurisdiction",
                requiredAdditionalEvidence = listOf(
                    "Original evidence before destruction", "Deletion logs", "Recovery attempts"
                )
            )
            EngineContradictionType.ATTORNEY_OBSTRUCTION -> LegalHypothesis(
                suggestedOffence = "Attorney Misconduct / Obstruction of Justice",
                legalBasis = "Attorney continues to act after dismissal or withholds client file/mandate",
                jurisdictionalNote = "Legal profession conduct rules — varies by jurisdiction",
                requiredAdditionalEvidence = listOf(
                    "Dismissal instruction", "Continued acting documentation", "Mandate/file transfer requests"
                )
            )
            EngineContradictionType.DEFAMATION_THREAT -> LegalHypothesis(
                suggestedOffence = "Intimidation / Abuse of Process / SLAPP",
                legalBasis = "Threat of defamation proceedings used to silence or pressure a party",
                jurisdictionalNote = "Defamation and civil procedure law — varies by jurisdiction",
                requiredAdditionalEvidence = listOf(
                    "Threat communications", "Legal basis asserted", "Context of dispute"
                )
            )
            EngineContradictionType.TECHNOLOGY_REFUSAL_LIABILITY -> LegalHypothesis(
                suggestedOffence = "Negligent Failure to Prevent Foreseeable Fraud",
                legalBasis = "Institution refuses to evaluate or implement offered fraud-prevention technology",
                jurisdictionalNote = "Tort and regulatory duty — varies by jurisdiction",
                requiredAdditionalEvidence = listOf(
                    "Technology offer and specifications", "Rejection/non-response documentation", "Resulting fraud loss"
                )
            )
            EngineContradictionType.CONFLICT_OF_INTEREST -> LegalHypothesis(
                suggestedOffence = "Conflict of Interest / Breach of Fiduciary Duty",
                legalBasis = "Undisclosed or improper interest that may compromise duty of loyalty",
                jurisdictionalNote = "Fiduciary and professional conduct rules — varies by jurisdiction",
                requiredAdditionalEvidence = listOf(
                    "Relationship disclosures", "Financial or familial ties", "Adverse party documentation"
                )
            )
            EngineContradictionType.INSTITUTIONAL_SILENCE -> LegalHypothesis(
                suggestedOffence = "Denial of Effective Remedy / Administrative Silence",
                legalBasis = "Institution with duty to respond fails to do so, creating a cascade of non-response",
                jurisdictionalNote = "Administrative and human rights law — varies by jurisdiction",
                requiredAdditionalEvidence = listOf(
                    "Submission records", "Bounce/undelivered evidence", "Follow-up correspondence"
                )
            )
            EngineContradictionType.CONDITIONAL_CLAUSE_MISINVOKED -> LegalHypothesis(
                suggestedOffence = "Unlawful Termination / Misrepresentation of a Contractual Right",
                legalBasis = "A termination or expiry was invoked under a clause whose precondition (party is the lessee under a head lease, not the owner) was not met, because the record shows the party had become the owner of the premises — the triggering event never occurred",
                jurisdictionalNote = "Contract law; SA common law on effluxion, repudiation and misrepresentation — requires legal review",
                requiredAdditionalEvidence = listOf(
                    "Title deed / property transfer records establishing ownership and its date",
                    "Any head lease agreement (or proof none existed)",
                    "The exact clause relied upon for termination",
                    "Cession / assignment records tracing the invoking party"
                )
            )
            else -> null
        }
        return EngineContradiction(
            contradictionId = nextId(), type = cType, severity = severity,
            confidence = ConfidenceCalibrator.calibrate(baseConfidence, cType.name, true),
            detectedFact = fact, logicalPattern = pattern, legalHypothesis = hypothesis,
            propositionAText = claimA.value, propositionBText = claimB.value,
            propositionAActor = claimA.actor, propositionBActor = claimB.actor,
            conflictDescription = "${claimA.actor} claims: \"${claimA.value}\" but ${claimB.actor} claims: \"${claimB.value}\""
        )
    }

    // ==================== 10 BASE DETECTORS (v5.2.9) ====================

    fun detectStatementVsStatement(claims: List<EngineClaim>): List<EngineContradiction> {
        val results = mutableListOf<EngineContradiction>()
        for (i in claims.indices) {
            if (results.size >= MAX_CONTRADICTIONS_PER_DETECTOR) break
            for (j in i + 1 until claims.size) {
                if (results.size >= MAX_CONTRADICTIONS_PER_DETECTOR) break
                val a = claims[i]; val b = claims[j]
                if (a.actor == b.actor && isOpposing(a, b)) {
                    val (isSem, semScore) = SemanticAnalyzer.detectSemanticContradiction(a, b)
                    if (isSem || (a.subject == b.subject && a.predicate == b.predicate && a.value != b.value)) {
                        results += createContradiction(a, b,
                            EngineContradictionType.STATEMENT_VS_STATEMENT,
                            severityScore(a, b, if (isSem) (semScore * 30).toInt() else 20),
                            if (isSem) EngineConfidence.HIGH else EngineConfidence.MODERATE,
                            "SAME_ACTOR_OPPOSING_CLAIMS",
                            "Same actor \"${a.actor}\" made contradictory statements on the same subject",
                            listOf(a.value, b.value), if (isSem) semScore else 0.5
                        )
                    }
                }
            }
        }
        return results
    }

    fun detectStatementVsEvidence(claims: List<EngineClaim>): List<EngineContradiction> {
        val results = mutableListOf<EngineContradiction>()
        val sworn = claims.filter { it.sourceType == EngineStatementType.SWORN_STATEMENT }
        val docs = claims.filter {
            it.sourceType == EngineStatementType.CONTEMPORANEOUS ||
            it.sourceType == EngineStatementType.CONTRACT_CLAUSE
        }
        for (s in sworn) {
            for (d in docs) {
                if (s.subject == d.subject && isOpposing(s, d)) {
                    results += createContradiction(s, d,
                        EngineContradictionType.STATEMENT_VS_EVIDENCE,
                        EngineSeverity.VERY_HIGH, EngineConfidence.VERY_HIGH,
                        "SWORN_STATEMENT_VS_DOCUMENTARY_EVIDENCE",
                        "Sworn statement contradicted by contemporaneous documentary evidence",
                        listOf(s.value, d.value), 0.9
                    )
                }
            }
        }
        return results
    }

    fun detectFinancialIrregularity(claims: List<EngineClaim>): List<EngineContradiction> {
        val keywords = listOf("payment", "amount", "balance", "deposit", "withdrawal", "transfer", "fee", "rent", "commission")
        val financial = claims.filter { c -> keywords.any { c.value.lowercase().contains(it) } }
        val results = mutableListOf<EngineContradiction>()
        for (i in financial.indices) {
            if (results.size >= MAX_CONTRADICTIONS_PER_DETECTOR) break
            for (j in i + 1 until financial.size) {
                if (results.size >= MAX_CONTRADICTIONS_PER_DETECTOR) break
                val a = financial[i]; val b = financial[j]
                if (a.actor == b.actor && a.subject == b.subject && a.value != b.value) {
                    val (isSem, semScore) = SemanticAnalyzer.detectSemanticContradiction(a, b)
                    if (isSem) {
                        results += createContradiction(a, b,
                            EngineContradictionType.FINANCIAL_IRREGULARITY,
                            EngineSeverity.HIGH, EngineConfidence.HIGH,
                            "FINANCIAL_AMOUNT_DISCREPANCY",
                            "Same actor reported inconsistent financial figures for the same subject",
                            listOf(a.value, b.value), semScore
                        )
                    }
                }
            }
        }
        return results
    }

    fun detectJudicialVsDocumentary(claims: List<EngineClaim>): List<EngineContradiction> {
        val judicial = claims.filter { it.sourceType == EngineStatementType.JUDICIAL_RECORD }
        val docs = claims.filter {
            it.sourceType == EngineStatementType.CONTRACT_CLAUSE ||
            it.sourceType == EngineStatementType.CONTEMPORANEOUS
        }
        val results = mutableListOf<EngineContradiction>()
        for (j in judicial) {
            for (d in docs) {
                if (j.subject == d.subject && isOpposing(j, d)) {
                    results += createContradiction(j, d,
                        EngineContradictionType.JUDICIAL_VS_DOCUMENTARY,
                        EngineSeverity.VERY_HIGH, EngineConfidence.VERY_HIGH,
                        "COURT_STATEMENT_VS_SEALED_DOCUMENT",
                        "Statement made to judicial body contradicted by sealed documentary evidence",
                        listOf(j.value, d.value), 0.95
                    )
                }
            }
        }
        return results
    }

    fun detectTemporalContradiction(claims: List<EngineClaim>): List<EngineContradiction> {
        val results = mutableListOf<EngineContradiction>()
        val dayMs = 1000L * 60 * 60 * 24
        for (i in claims.indices) {
            if (results.size >= MAX_CONTRADICTIONS_PER_DETECTOR) break
            for (j in i + 1 until claims.size) {
                if (results.size >= MAX_CONTRADICTIONS_PER_DETECTOR) break
                val a = claims[i]; val b = claims[j]
                if (a.actor == b.actor && a.subject == b.subject && a.date != null && b.date != null && a.date != b.date) {
                    if (isOpposing(a, b)) {
                        val gapDays = kotlin.math.abs(a.date - b.date) / dayMs
                        val sev = when {
                            gapDays > 730 -> EngineSeverity.VERY_HIGH
                            gapDays > 365 -> EngineSeverity.HIGH
                            else -> EngineSeverity.MODERATE
                        }
                        results += createContradiction(a, b,
                            EngineContradictionType.TEMPORAL_CONTRADICTION, sev, EngineConfidence.HIGH,
                            "TEMPORALLY_SEPARATED_CONTRADICTORY_STATEMENTS",
                            "Same actor made contradictory statements ${gapDays} days apart",
                            listOf(a.value, b.value, "Gap: $gapDays days"), kotlin.math.min(0.9, gapDays / 730.0)
                        )
                    }
                }
            }
        }
        return results
    }

    fun detectConsciousnessOfGuilt(claims: List<EngineClaim>): List<EngineContradiction> {
        val results = mutableListOf<EngineContradiction>()
        val dayMs = 1000L * 60 * 60 * 24
        for (i in claims.indices) {
            if (results.size >= MAX_CONTRADICTIONS_PER_DETECTOR) break
            for (j in i + 1 until claims.size) {
                if (results.size >= MAX_CONTRADICTIONS_PER_DETECTOR) break
                val a = claims[i]; val b = claims[j]
                if (a.actor == b.actor && a.date != null && b.date != null) {
                    val gapDays = kotlin.math.abs(a.date - b.date) / dayMs
                    if (gapDays > 730 && isOpposing(a, b)) {
                        results += createContradiction(a, b,
                            EngineContradictionType.CONSCIOUSNESS_OF_GUILT,
                            EngineSeverity.VERY_HIGH, EngineConfidence.VERY_HIGH,
                            "CONSCIOUSNESS_OF_GUILT_730DAY_GAP",
                            "Actor made contradictory statements ${gapDays} days apart (>2yr gap proves consciousness of guilt)",
                            listOf(a.value, b.value, "Gap: $gapDays days"), 0.95
                        )
                    }
                }
            }
        }
        return results
    }

    fun detectBehavioral(claims: List<EngineClaim>): List<EngineContradiction> {
        val behavioral = listOf("agreed", "promised", "committed", "guaranteed", "assured")
        val denial = listOf("denied", "refused", "rejected", "declined", "never")
        return claims.filter { c ->
            val v = c.value.lowercase()
            behavioral.any { v.contains(it) } && denial.any { v.contains(it) }
        }.map { c ->
            createContradiction(c, c, EngineContradictionType.BEHAVIORAL,
                EngineSeverity.MODERATE, EngineConfidence.MODERATE,
                "BEHAVIORAL_INCONSISTENCY",
                "Actor's statement contains both commitment language and denial language",
                listOf(c.value), 0.5
            )
        }
    }

    fun detectShamTransaction(claims: List<EngineClaim>): List<EngineContradiction> {
        val sham = listOf("arm's length", "independent", "unrelated party", "third party", "at market value")
        val control = listOf("same director", "common ownership", "related party", "subsidiary", "parent company", "controlled by")
        val shamClaims = claims.filter { c -> sham.any { c.value.lowercase().contains(it) } }
        val ctrlClaims = claims.filter { c -> control.any { c.value.lowercase().contains(it) } }
        val results = mutableListOf<EngineContradiction>()
        for (s in shamClaims) {
            for (ctrl in ctrlClaims) {
                if (s.actor == ctrl.actor || s.subject == ctrl.subject) {
                    results += createContradiction(s, ctrl,
                        EngineContradictionType.SHAM_TRANSACTION, EngineSeverity.HIGH, EngineConfidence.HIGH,
                        "SHAM_TRANSACTION_DUAL_CONTROL",
                        "Entity claims arm's-length transaction but evidence shows common control",
                        listOf(s.value, ctrl.value), 0.85
                    )
                }
            }
        }
        return results
    }

    fun detectTacitLeaseViolation(claims: List<EngineClaim>): List<EngineContradiction> {
        val rent = listOf("rent", "lease", "monthly payment", "occupation", "possession")
        val deny = listOf("no contract", "no lease", "expired", "not valid", "no agreement")
        val rentClaims = claims.filter { c -> rent.any { c.value.lowercase().contains(it) } }
        val denyClaims = claims.filter { c -> deny.any { c.value.lowercase().contains(it) } }
        val results = mutableListOf<EngineContradiction>()
        for (r in rentClaims) {
            for (d in denyClaims) {
                if (r.actor == d.actor) {
                    results += createContradiction(r, d,
                        EngineContradictionType.TACIT_LEASE_VIOLATION, EngineSeverity.HIGH, EngineConfidence.HIGH,
                        "RENT_ACCEPTANCE_WHILE_DENYING_CONTRACT",
                        "Actor collected rent/payments while simultaneously denying contract existence",
                        listOf(r.value, d.value), 0.9
                    )
                }
            }
        }
        return results
    }

    fun detectPostExpiryEnforcement(claims: List<EngineClaim>): List<EngineContradiction> {
        val expiry = listOf("expired", "terminated", "ended", "lapsed", "no longer valid")
        val enforce = listOf("enforce", "demand", "require", "compel", "pursuant to")
        val expClaims = claims.filter { c -> expiry.any { c.value.lowercase().contains(it) } }
        val enfClaims = claims.filter { c -> enforce.any { c.value.lowercase().contains(it) } }
        val results = mutableListOf<EngineContradiction>()
        for (e in expClaims) {
            for (enf in enfClaims) {
                if (e.actor == enf.actor && e.subject == enf.subject) {
                    results += createContradiction(e, enf,
                        EngineContradictionType.POST_EXPIRY_ENFORCEMENT, EngineSeverity.VERY_HIGH, EngineConfidence.VERY_HIGH,
                        "ENFORCING_CLAUSE_AFTER_ITS_OWN_EXPIRY",
                        "Actor enforced a clause after claiming the underlying agreement had expired",
                        listOf(e.value, enf.value), 0.95
                    )
                }
            }
        }
        return results
    }

    // ==================== 2 v6.0 FRANCHISE/LEASE DETECTORS ====================

    /**
     * Conditional-clause trap (Caltex Franchise Agreement cl. 3.2.3). A
     * termination/expiry rests on a clause whose precondition is that the party
     * is the LESSEE (not the owner) under a head lease that ended — but the
     * record shows that party had become the OWNER of the premises, so the
     * clause's trigger never occurred and the termination may be void. This is
     * the evidence the engine missed: the lease clause must be read against the
     * ownership record.
     */
    fun detectConditionalClauseMisinvoked(claims: List<EngineClaim>): List<EngineContradiction> {
        val clauseCondition = listOf("lessee", "head lease", "not the owner", "effluxion")
        val ownership = listOf(
            "is the owner", "became the owner", "purchased the property", "owner of the premises",
            "transfer of the property", "registered owner", "acquired the property", "bought the site",
            "took transfer", "ownership of the premises", "owns the premises", "owns the property"
        )
        val clauseClaims = claims.filter { c -> clauseCondition.any { c.value.contains(it, ignoreCase = true) } }
        val ownershipFacts = claims.filter { c -> ownership.any { c.value.contains(it, ignoreCase = true) } }
        val results = mutableListOf<EngineContradiction>()
        for (clause in clauseClaims) {
            if (results.size >= MAX_CONTRADICTIONS_PER_DETECTOR) break
            for (own in ownershipFacts) {
                if (results.size >= MAX_CONTRADICTIONS_PER_DETECTOR) break
                if (clause.sha512Hash.isNotEmpty() && clause.sha512Hash == own.sha512Hash) continue
                results += createContradiction(clause, own,
                    EngineContradictionType.CONDITIONAL_CLAUSE_MISINVOKED,
                    EngineSeverity.VERY_HIGH, EngineConfidence.VERY_HIGH,
                    "TERMINATION_UNDER_LESSEE_CLAUSE_WHILE_OWNER",
                    "A termination/expiry rests on a clause conditioned on the party being a lessee (not the owner), but contemporaneous evidence shows that party was the owner of the premises — the clause's precondition never occurred, so the invoked termination is void",
                    listOf(clause.value, own.value), 0.95
                )
            }
        }
        return results
    }

    /**
     * Goodwill / value of the business recognised or quantified in one document
     * (e.g. the clawback table) but denied or said to have no compensable value
     * elsewhere — "you only take away what exists". Mapped to ACKNOWLEDGE_THEN_DENY.
     */
    fun detectAssetValueDenial(claims: List<EngineClaim>): List<EngineContradiction> {
        val asset = listOf("goodwill", "value of the business")
        val recognitionMarkers = listOf("means", "value", "clawback", "percentage", "inure", "entitled", "recognis", "quantif", "compensat")
        val denialMarkers = listOf("no goodwill", "no compensable", "has no value", "not entitled to any compensation", "without compensation", "no value", "not compensable")
        val recognition = claims.filter { c ->
            val t = c.value.lowercase()
            asset.any { t.contains(it) } && recognitionMarkers.any { t.contains(it) } && denialMarkers.none { t.contains(it) }
        }
        val denial = claims.filter { c ->
            val t = c.value.lowercase()
            denialMarkers.any { t.contains(it) } && (asset.any { t.contains(it) } || t.contains("compensat") || t.contains("value"))
        }
        val results = mutableListOf<EngineContradiction>()
        for (r in recognition) {
            if (results.size >= MAX_CONTRADICTIONS_PER_DETECTOR) break
            for (d in denial) {
                if (results.size >= MAX_CONTRADICTIONS_PER_DETECTOR) break
                if (r.sha512Hash.isNotEmpty() && r.sha512Hash == d.sha512Hash) continue
                results += createContradiction(r, d,
                    EngineContradictionType.ACKNOWLEDGE_THEN_DENY,
                    EngineSeverity.VERY_HIGH, EngineConfidence.VERY_HIGH,
                    "ASSET_VALUE_RECOGNISED_THEN_DENIED",
                    "An asset (goodwill / value of the business) is recognised or quantified in one document but its existence or compensable value is denied elsewhere — a forfeiture or clawback of the asset is itself an admission that it exists",
                    listOf(r.value, d.value), 0.9
                )
            }
        }
        return results
    }

    // ==================== 6 v5.3.1c DIGSIM DETECTORS ====================

    /** Detector 11: DEFECTIVE_JURAT — Affidavit missing mandatory jurat elements. */
    fun detectDefectiveJurat(claims: List<EngineClaim>): List<EngineContradiction> {
        val juratMarkers = listOf("jurat", "oath", "commissioner", " sworn ", "affidavit", "before me")
        val missingMarkers = listOf("no jurat", "missing jurat", "no oath", "no commissioner", "unsigned jurat", "blank jurat")
        val juratClaims = claims.filter { c -> juratMarkers.any { c.value.lowercase().contains(it) } }
        val missingClaims = claims.filter { c -> missingMarkers.any { c.value.lowercase().contains(it) } }
        val results = mutableListOf<EngineContradiction>()
        for (j in juratClaims) {
            for (m in missingClaims) {
                if (j.actor == m.actor || j.documentId == m.documentId) {
                    results += createContradiction(j, m,
                        EngineContradictionType.DEFECTIVE_JURAT, EngineSeverity.VERY_HIGH, EngineConfidence.VERY_HIGH,
                        "DEFECTIVE_JURAT_MISSING_ELEMENTS",
                        "Affidavit filed without mandatory jurat elements — no oath, no commissioner = no perjury liability",
                        listOf(j.value, m.value), 0.95
                    )
                }
            }
        }
        return results
    }

    /** Detector 12: PROTECTION_ORDER_AS_LEVERAGE — Protection from Harassment Act misuse. */
    fun detectProtectionOrderLeverage(claims: List<EngineClaim>): List<EngineContradiction> {
        val protectionMarkers = listOf("protection order", "harassment act", "restrain", "interdict", "protection from harassment")
        val leverageMarkers = listOf("settlement", "bargain", "leverage", "pressure", "threaten", "force agreement", "silence")
        val protectionClaims = claims.filter { c -> protectionMarkers.any { c.value.lowercase().contains(it) } }
        val leverageClaims = claims.filter { c -> leverageMarkers.any { c.value.lowercase().contains(it) } }
        val results = mutableListOf<EngineContradiction>()
        for (p in protectionClaims) {
            for (l in leverageClaims) {
                if (p.actor == l.actor || p.subject == l.subject) {
                    results += createContradiction(p, l,
                        EngineContradictionType.PROTECTION_ORDER_AS_LEVERAGE, EngineSeverity.HIGH, EngineConfidence.HIGH,
                        "PROTECTION_ORDER_USED_AS_LEVERAGE",
                        "Protection from Harassment Act application used as bargaining tool in commercial dispute",
                        listOf(p.value, l.value), 0.85
                    )
                }
            }
        }
        return results
    }

    /** Detector 13: FALSE_ALLEGATION_IN_AFFIDAVIT — Sworn allegation contradicted by evidence. */
    fun detectFalseAllegationInAffidavit(claims: List<EngineClaim>): List<EngineContradiction> {
        val swornAllegations = claims.filter {
            it.sourceType == EngineStatementType.SWORN_STATEMENT ||
            it.sourceType == EngineStatementType.JUDICIAL_RECORD
        }
        val contemporaneous = claims.filter {
            it.sourceType == EngineStatementType.CONTEMPORANEOUS ||
            it.sourceType == EngineStatementType.CLAIM
        }
        val results = mutableListOf<EngineContradiction>()
        for (s in swornAllegations) {
            for (e in contemporaneous) {
                if (s.actor == e.actor && s.subject == e.subject && isOpposing(s, e)) {
                    results += createContradiction(s, e,
                        EngineContradictionType.FALSE_ALLEGATION_IN_AFFIDAVIT, EngineSeverity.VERY_HIGH, EngineConfidence.VERY_HIGH,
                        "SWORN_ALLEGATION_CONTRADICTED_BY_EVIDENCE",
                        "Specific factual allegation in sworn document contradicted by contemporaneous evidence",
                        listOf(s.value, e.value), 0.95
                    )
                }
            }
        }
        return results
    }

    /** Detector 14: TEMPORAL_PRECEDENCE_CONFLICT — Event order reversed between documents. */
    fun detectTemporalPrecedenceConflict(claims: List<EngineClaim>): List<EngineContradiction> {
        val beforeMarkers = listOf("before", "prior to", "preceded by", "earlier than", "first")
        val afterMarkers = listOf("after", "subsequent to", "followed by", "later than", "then")
        val results = mutableListOf<EngineContradiction>()
        for (i in claims.indices) {
            if (results.size >= MAX_CONTRADICTIONS_PER_DETECTOR) break
            for (j in i + 1 until claims.size) {
                if (results.size >= MAX_CONTRADICTIONS_PER_DETECTOR) break
                val a = claims[i]; val b = claims[j]
                if (a.actor == b.actor && a.date != null && b.date != null) {
                    val lowerA = a.value.lowercase(); val lowerB = b.value.lowercase()
                    val aClaimsBefore = beforeMarkers.any { lowerA.contains(it) }
                    val bClaimsAfter = afterMarkers.any { lowerB.contains(it) }
                    val aClaimsAfter = afterMarkers.any { lowerA.contains(it) }
                    val bClaimsBefore = beforeMarkers.any { lowerB.contains(it) }
                    if ((aClaimsBefore && bClaimsBefore && a.date > b.date) || (aClaimsAfter && bClaimsAfter && a.date < b.date)) {
                        results += createContradiction(a, b,
                            EngineContradictionType.TEMPORAL_PRECEDENCE_CONFLICT, EngineSeverity.HIGH, EngineConfidence.HIGH,
                            "TEMPORAL_PRECEDENCE_CONFLICT",
                            "Event A documented before Event B, but later document claims B before A",
                            listOf(a.value, b.value, "Date A: ${a.date}, Date B: ${b.date}"), 0.85
                        )
                    }
                }
            }
        }
        return results
    }

    /** Detector 15: PROCESS_REMEDY_CONFLICT — Institution denies effective remedy. */
    fun detectProcessRemedyConflict(claims: List<EngineClaim>): List<EngineContradiction> {
        val dutyMarkers = listOf("duty to respond", "mandatory", "obligation to", "must respond", "required to")
        val denialMarkers = listOf("no response", "remains silent", "bounced", "denied remedy", "no effective remedy", "ignored")
        val dutyClaims = claims.filter { c -> dutyMarkers.any { c.value.lowercase().contains(it) } }
        val denialClaims = claims.filter { c -> denialMarkers.any { c.value.lowercase().contains(it) } }
        val results = mutableListOf<EngineContradiction>()
        for (d in dutyClaims) {
            for (n in denialClaims) {
                if (d.subject == n.subject || d.actor == n.actor) {
                    results += createContradiction(d, n,
                        EngineContradictionType.PROCESS_REMEDY_CONFLICT, EngineSeverity.VERY_HIGH, EngineConfidence.VERY_HIGH,
                        "PROCESS_REMEDY_CONFLICT",
                        "Institution with mandatory duty to respond remains silent, bounces submissions, or denies effective remedy",
                        listOf(d.value, n.value), 0.95
                    )
                }
            }
        }
        return results
    }

    /** Detector 16: CHARACTER_ASSASSINATION — Personal attacks in sworn testimony. */
    fun detectCharacterAssassination(claims: List<EngineClaim>): List<EngineContradiction> {
        val swornClaims = claims.filter {
            it.sourceType == EngineStatementType.SWORN_STATEMENT ||
            it.sourceType == EngineStatementType.JUDICIAL_RECORD
        }
        val personalMarkers = listOf("character", "reputation", "dishonest", "untrustworthy", "unreliable", "mental health", "emotional", "drinking", "personal life", "family")
        return swornClaims.filter { c ->
            val lower = c.value.lowercase()
            personalMarkers.any { lower.contains(it) } && !lower.contains("relevant") && !lower.contains("material")
        }.map { c ->
            createContradiction(c, c, EngineContradictionType.CHARACTER_ASSASSINATION, EngineSeverity.HIGH, EngineConfidence.HIGH,
                "CHARACTER_ASSASSINATION_IN_SWORN_TESTIMONY",
                "Personal matters included in sworn testimony to attack credibility without relevance to legal issue",
                listOf(c.value), 0.8
            )
        }
    }

    // ==================== v5.3.1c PORTED HIGH-VALUE DETECTORS ====================

    /** Detector 17: ACKNOWLEDGE_THEN_DENY — same actor first admits/acknowledges then denies/rejects. */
    fun detectAcknowledgeThenDeny(claims: List<EngineClaim>): List<EngineContradiction> {
        val triggers = listOf(
            "acknowledge" to "deny",
            "admit" to "deny",
            "agree" to "reject",
            "accept" to "reject",
            "confirm" to "deny"
        )
        val results = mutableListOf<EngineContradiction>()
        for (a in claims) {
            for ((first, second) in triggers) {
                if (a.value.contains(first, ignoreCase = true)) {
                    claims.filter { it != a && it.actor == a.actor && it.value.contains(second, ignoreCase = true) }
                        .forEach { b ->
                            results += createContradiction(a, b,
                                EngineContradictionType.ACKNOWLEDGE_THEN_DENY,
                                EngineSeverity.HIGH, EngineConfidence.HIGH,
                                "ACKNOWLEDGE_THEN_DENY",
                                "Actor ${a.actor} acknowledged/admitted a fact and later denied/rejected it",
                                listOf(a.value, b.value), 0.85
                            )
                        }
                }
            }
        }
        return results
    }

    /** Detector 18: NO_COUNTERSIGNATURE_TRAP — enforcing unsigned or never-countersigned documents. */
    fun detectNoCountersignatureTrap(claims: List<EngineClaim>): List<EngineContradiction> {
        val trapMarkers = listOf("no countersignature", "unsigned agreement", "never signed", "signature missing", "not countersigned")
        return claims.filter { c -> trapMarkers.any { c.value.contains(it, ignoreCase = true) } }
            .map { c ->
                createContradiction(c, c,
                    EngineContradictionType.NO_COUNTERSIGNATURE_TRAP,
                    EngineSeverity.HIGH, EngineConfidence.HIGH,
                    "NO_COUNTERSIGNATURE_TRAP",
                    "Document enforced or relied upon despite missing countersignature",
                    listOf(c.value), 0.8
                )
            }
    }

    /** Detector 19: GOODWILL_FORFEITURE_SWINDLE — taking value from goodwill while forcing its forfeiture. */
    fun detectGoodwillForfeiture(claims: List<EngineClaim>): List<EngineContradiction> {
        val forfeit = listOf("goodwill forfeiture", "forfeit goodwill", "no goodwill", "goodwill cancelled", "forfeit all goodwill")
        val valueExtract = listOf("buy-out", "take over", "extension fee", "goodwill value", "compensable")
        val forfeitClaims = claims.filter { c -> forfeit.any { c.value.contains(it, ignoreCase = true) } }
        val valueClaims = claims.filter { c -> valueExtract.any { c.value.contains(it, ignoreCase = true) } }
        val results = mutableListOf<EngineContradiction>()
        for (f in forfeitClaims) {
            for (v in valueClaims) {
                if (f.actor == v.actor || f.subject == v.subject) {
                    results += createContradiction(f, v,
                        EngineContradictionType.GOODWILL_FORFEITURE_SWINDLE,
                        EngineSeverity.VERY_HIGH, EngineConfidence.HIGH,
                        "GOODWILL_FORFEITURE_SWINDLE",
                        "Actor demands forfeiture of goodwill while simultaneously extracting value from it",
                        listOf(f.value, v.value), 0.9
                    )
                }
            }
        }
        return results
    }

    /** Detector 20: MANUFACTURED_CONSENT — consent claimed but contradicted by contemporaneous reluctance. */
    fun detectManufacturedConsent(claims: List<EngineClaim>): List<EngineContradiction> {
        val consentClaimed = listOf("grateful", "signed willingly", "happy to accept", "consented", "agreed voluntarily")
        val reluctance = listOf("pressured", "no alternative", "distressed", "non-committal", "negotiated for more time", "reluctant")
        val claimed = claims.filter { c -> consentClaimed.any { c.value.contains(it, ignoreCase = true) } }
        val reluctant = claims.filter { c -> reluctance.any { c.value.contains(it, ignoreCase = true) } }
        val results = mutableListOf<EngineContradiction>()
        for (c in claimed) {
            for (r in reluctant) {
                if (c.actor == r.actor || c.subject == r.subject) {
                    results += createContradiction(c, r,
                        EngineContradictionType.MANUFACTURED_CONSENT,
                        EngineSeverity.HIGH, EngineConfidence.HIGH,
                        "MANUFACTURED_CONSENT",
                        "Claim of willing consent contradicted by contemporaneous evidence of pressure or reluctance",
                        listOf(c.value, r.value), 0.85
                    )
                }
            }
        }
        return results
    }

    /** Detector 21: FABRICATED_DECOY_EVIDENCE — forged or decoy evidence deployed. */
    fun detectFabricatedDecoy(claims: List<EngineClaim>): List<EngineContradiction> {
        val decoyMarkers = listOf("fabricated decoy", "decoy evidence", "fake transaction", "sms decoy", "forged whatsapp", "fabricated screenshot", "doctored")
        return claims.filter { c -> decoyMarkers.any { c.value.contains(it, ignoreCase = true) } }
            .map { c ->
                createContradiction(c, c,
                    EngineContradictionType.FABRICATED_DECOY_EVIDENCE,
                    EngineSeverity.VERY_HIGH, EngineConfidence.HIGH,
                    "FABRICATED_DECOY_EVIDENCE",
                    "Forged, doctored, or decoy evidence introduced to mislead",
                    listOf(c.value), 0.9
                )
            }
    }

    /** Detector 22: DATA_BREACH_ENABLED_FRAUD — fraud facilitated by unauthorized data access. */
    fun detectDataBreachFraud(claims: List<EngineClaim>): List<EngineContradiction> {
        val breach = listOf("data breach", "unauthorized access", "compromised", "gmail access", "unauthorised gmail")
        val fraud = listOf("card number", "fraudulent transaction", "unauthorized transaction", "unauthorised data access", "hack")
        val breachClaims = claims.filter { c -> breach.any { c.value.contains(it, ignoreCase = true) } }
        val fraudClaims = claims.filter { c -> fraud.any { c.value.contains(it, ignoreCase = true) } }
        val results = mutableListOf<EngineContradiction>()
        for (b in breachClaims) {
            for (f in fraudClaims) {
                if (b.actor == f.actor || b.subject == f.subject) {
                    results += createContradiction(b, f,
                        EngineContradictionType.DATA_BREACH_ENABLED_FRAUD,
                        EngineSeverity.VERY_HIGH, EngineConfidence.HIGH,
                        "DATA_BREACH_ENABLED_FRAUD",
                        "Unauthorized data access or breach enabled subsequent fraudulent conduct",
                        listOf(b.value, f.value), 0.9
                    )
                }
            }
        }
        return results
    }

    /** Detector 23: SPOLIATION_OF_EVIDENCE — intentional destruction/deletion of evidence. */
    fun detectSpoliation(claims: List<EngineClaim>): List<EngineContradiction> {
        val spoliationMarkers = listOf("destroyed evidence", "deleted message", "spoliation", "wiped", "concealed document", "evidence destruction")
        return claims.filter { c -> spoliationMarkers.any { c.value.contains(it, ignoreCase = true) } }
            .map { c ->
                createContradiction(c, c,
                    EngineContradictionType.SPOLIATION_OF_EVIDENCE,
                    EngineSeverity.VERY_HIGH, EngineConfidence.HIGH,
                    "SPOLIATION_OF_EVIDENCE",
                    "Evidence intentionally destroyed, deleted, or concealed",
                    listOf(c.value), 0.9
                )
            }
    }

    /** Detector 24: ATTORNEY_OBSTRUCTION — attorney continues acting after dismissal or withholds file. */
    fun detectAttorneyObstruction(claims: List<EngineClaim>): List<EngineContradiction> {
        val obstructionMarkers = listOf("attorney obstruction", "obstructed", "withheld mandate", "refused to hand over file", "continued to act", "false statements on record")
        return claims.filter { c -> obstructionMarkers.any { c.value.contains(it, ignoreCase = true) } }
            .map { c ->
                createContradiction(c, c,
                    EngineContradictionType.ATTORNEY_OBSTRUCTION,
                    EngineSeverity.VERY_HIGH, EngineConfidence.HIGH,
                    "ATTORNEY_OBSTRUCTION",
                    "Attorney obstructed process by continuing to act after dismissal or making false statements",
                    listOf(c.value), 0.9
                )
            }
    }

    /** Detector 25: INSTITUTIONAL_SILENCE — institution fails to respond despite duty/cascade. */
    fun detectInstitutionalSilence(claims: List<EngineClaim>): List<EngineContradiction> {
        val silenceMarkers = listOf("no response", "remained silent", "bounced", "failed to respond", "institutional silence", "resolution feedback provided")
        return claims.filter { c -> silenceMarkers.any { c.value.contains(it, ignoreCase = true) } }
            .map { c ->
                createContradiction(c, c,
                    EngineContradictionType.INSTITUTIONAL_SILENCE,
                    EngineSeverity.HIGH, EngineConfidence.HIGH,
                    "INSTITUTIONAL_SILENCE_CASCADE",
                    "Institution with duty to respond remained silent or bounced submissions",
                    listOf(c.value), 0.8
                )
            }
    }

    /** Detector 26: DEFAMATION_THREAT — threat of defamation suit to silence/pressure. */
    fun detectDefamationThreat(claims: List<EngineClaim>): List<EngineContradiction> {
        val threatMarkers = listOf("defamation", "cease and desist", "will sue for defamation", "reputational harm", "govern yourself accordingly")
        return claims.filter { c -> threatMarkers.any { c.value.contains(it, ignoreCase = true) } }
            .map { c ->
                createContradiction(c, c,
                    EngineContradictionType.DEFAMATION_THREAT,
                    EngineSeverity.MODERATE, EngineConfidence.HIGH,
                    "DEFAMATION_THREAT",
                    "Defamation or cease-and-desist threat used to pressure or silence a party",
                    listOf(c.value), 0.7
                )
            }
    }

    /** Detector 27: TECHNOLOGY_REFUSAL_LIABILITY — offered fraud-prevention technology never evaluated. */
    fun detectTechnologyRefusal(claims: List<EngineClaim>): List<EngineContradiction> {
        val offer = listOf("offered", "protocol offered", "certified test document", "fraud-verification protocol")
        val refusal = listOf("no evaluation", "no reply", "failed to evaluate", "refused to implement", "never evaluated")
        val offerClaims = claims.filter { c -> offer.any { c.value.contains(it, ignoreCase = true) } }
        val refusalClaims = claims.filter { c -> refusal.any { c.value.contains(it, ignoreCase = true) } }
        val results = mutableListOf<EngineContradiction>()
        for (o in offerClaims) {
            for (r in refusalClaims) {
                if (o.actor == r.actor || o.subject == r.subject) {
                    results += createContradiction(o, r,
                        EngineContradictionType.TECHNOLOGY_REFUSAL_LIABILITY,
                        EngineSeverity.MODERATE, EngineConfidence.MODERATE,
                        "TECHNOLOGY_REFUSAL_LIABILITY",
                        "Fraud-prevention technology was offered but never evaluated or implemented",
                        listOf(o.value, r.value), 0.65
                    )
                }
            }
        }
        return results
    }

    /** Detector 28: CONFLICT_OF_INTEREST — undisclosed related-party or dual interest. */
    fun detectConflictOfInterest(claims: List<EngineClaim>): List<EngineContradiction> {
        val conflictMarkers = listOf("conflict of interest", "same law firm", "related party", "undisclosed interest", "preferred panel attorney", "dual interest")
        return claims.filter { c -> conflictMarkers.any { c.value.contains(it, ignoreCase = true) } }
            .map { c ->
                createContradiction(c, c,
                    EngineContradictionType.CONFLICT_OF_INTEREST,
                    EngineSeverity.HIGH, EngineConfidence.HIGH,
                    "CONFLICT_OF_INTEREST",
                    "Undisclosed or improper interest that may compromise duty of loyalty",
                    listOf(c.value), 0.8
                )
            }
    }

    // ==================== SIGNED RULE-UPDATE DETECTOR ====================

    /**
     * Additive detector driven by downloaded, signature-verified rules.
     * Flags claim pairs that contain opposite sides of a downloaded
     * fraud-keyword phrase pair (e.g. "paid" vs "not paid"). A single claim
     * containing both sides is flagged as a self-contradiction, mirroring
     * [detectBehavioral]. Conservative severity/confidence (MODERATE) —
     * substring co-occurrence is a candidate signal, not proof.
     */
    fun detectDownloadedFraudPairs(claims: List<EngineClaim>): List<EngineContradiction> {
        val rules = runCatching { downloadedRulesProvider() }.getOrNull() ?: return emptyList()
        if (rules.fraudPairs.isEmpty()) return emptyList()
        val results = mutableListOf<EngineContradiction>()
        for (pair in rules.fraudPairs) {
            val first = pair.first.lowercase()
            val second = pair.second.lowercase()
            if (first.isEmpty() || second.isEmpty()) continue
            val firstClaims = claims.filter { c -> c.value.lowercase().contains(first) }
            val secondClaims = claims.filter { c -> c.value.lowercase().contains(second) }
            for (a in firstClaims) {
                for (b in secondClaims) {
                    if (a.actor == b.actor || a.subject == b.subject) {
                        results += createContradiction(
                            a, b,
                            EngineContradictionType.BEHAVIORAL,
                            EngineSeverity.MODERATE,
                            EngineConfidence.MODERATE,
                            "DOWNLOADED_RULE_${pair.ruleId}",
                            "Downloaded rule ${pair.ruleId} (rules v${rules.version}): " +
                                "co-occurring opposing phrases \"${pair.first}\" / \"${pair.second}\"",
                            listOf(a.value, b.value), 0.5
                        )
                    }
                }
            }
        }
        return results
    }

    // ==================== MASTER DETECT ALL (28 built-in + 1 additive) ====================

    private val ALL_DETECTORS: List<(List<EngineClaim>) -> List<EngineContradiction>> = listOf(
        // v5.2.9 base detectors
        ::detectStatementVsStatement,
        ::detectStatementVsEvidence,
        ::detectFinancialIrregularity,
        ::detectJudicialVsDocumentary,
        ::detectTemporalContradiction,
        ::detectConsciousnessOfGuilt,
        ::detectBehavioral,
        ::detectShamTransaction,
        ::detectTacitLeaseViolation,
        ::detectPostExpiryEnforcement,
        // v5.3.1c DIGSIM detectors
        ::detectDefectiveJurat,
        ::detectProtectionOrderLeverage,
        ::detectFalseAllegationInAffidavit,
        ::detectTemporalPrecedenceConflict,
        ::detectProcessRemedyConflict,
        ::detectCharacterAssassination,
        // v5.3.1c ported high-value detectors
        ::detectAcknowledgeThenDeny,
        ::detectNoCountersignatureTrap,
        ::detectGoodwillForfeiture,
        ::detectManufacturedConsent,
        ::detectFabricatedDecoy,
        ::detectDataBreachFraud,
        ::detectSpoliation,
        ::detectAttorneyObstruction,
        ::detectInstitutionalSilence,
        ::detectDefamationThreat,
        ::detectTechnologyRefusal,
        ::detectConflictOfInterest,
        // v6.0 franchise/lease detectors (AllFuels case)
        ::detectConditionalClauseMisinvoked,
        ::detectAssetValueDenial,
        // Additive signed rule-update detector (no-op until a verified package is downloaded)
        ::detectDownloadedFraudPairs
    )

    /**
     * Run all 28 built-in detectors plus the additive downloaded-rule detector,
     * deduplicate, and sort by severity (highest first). With no downloaded
     * rules the output is identical to running the built-in detectors alone.
     */
    fun detectAll(claims: List<EngineClaim>): List<EngineContradiction> {
        val seen = mutableSetOf<String>()
        val unique = mutableListOf<EngineContradiction>()
        // Deduplicate as each detector returns rather than concatenating all 29
        // result lists first. Detectors run in the same order and contradictions
        // are still created in the same order, so the output (and every
        // contradictionId) is identical to the previous flatMap — but only one
        // detector's results are held at a time, and the duplicates it sheds
        // become collectable immediately instead of pinning the whole run in
        // memory until the end.
        for (detector in ALL_DETECTORS) {
            for (c in detector(claims)) {
                val key = "${c.propositionAActor}:${c.propositionBActor}:${c.type}:${c.logicalPattern.patternType}"
                if (key !in seen) {
                    seen += key
                    unique += c
                }
            }
        }
        return unique.sortedByDescending { EngineScores.severityScore(it.severity) }
    }
}
