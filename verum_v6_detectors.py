#!/usr/bin/env python3
"""
Verum Omnis - Contradiction Detectors v6 (Companion Layer)
===========================================================
Status: RATIFIED - BINDING (founder directive, 2026-07-14).
Spec: CANDIDATE_CONTRADICTION_TYPES_v6.md

Builds ONTO the sealed v5.3.1c engine. The sealed engine file is untouched;
these detectors operate on anchored text chunks and emit findings-JSON
records (FINDINGS_JSON_SCHEMA.json shape) that merge into a findings document.

Three detectors:
  1. SWORN_VS_SWORN - cross-deponent perjury conflict
  2. DEVICE_ATTRIBUTION_CHAIN - declaration-linked digital attribution
  3. CRIMINAL_CHARGE_AS_LEVERAGE - retaliatory prosecution pattern

Plus the precision fix from the same spec: word-boundary keyword matching
("lease" must not fire inside "please").

Every record is anchored: source document, page, SHA-512. If it is not
anchored, it is not emitted.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Any, Dict, Iterable, List, Optional, Sequence

from verum_statutes import DEFAULT_JURISDICTION, enrich_hypothesis

DETECTOR_VERSION = "v6-ratified-1.0.0"

TYPE_SWORN_VS_SWORN = "SWORN_VS_SWORN"
TYPE_DEVICE_ATTRIBUTION_CHAIN = "DEVICE_ATTRIBUTION_CHAIN"
TYPE_CRIMINAL_CHARGE_AS_LEVERAGE = "CRIMINAL_CHARGE_AS_LEVERAGE"

STATUS_ENGINE_VERIFIED = "ENGINE-VERIFIED"


# ---------------------------------------------------------------------------
# Text chunks - the anchored unit every detector works on
# ---------------------------------------------------------------------------

@dataclass
class TextChunk:
    text: str
    source: str          # document name, e.g. "case126_p3" or the file name
    page: int            # 1-based page number
    sha512: str          # SHA-512 of the source artefact


PAGE_MARKER_RE = re.compile(r"===== PAGE (\d+) =====\n")


def chunks_from_marked_text(marked_text: str, source: str, sha512: str) -> List[TextChunk]:
    """Split text carrying '===== PAGE n =====' markers into anchored chunks."""
    parts = PAGE_MARKER_RE.split(marked_text)
    chunks: List[TextChunk] = []
    for i in range(1, len(parts), 2):
        page_no = int(parts[i])
        body = parts[i + 1] if i + 1 < len(parts) else ""
        if body.strip():
            chunks.append(TextChunk(text=body, source=source, page=page_no, sha512=sha512))
    if not chunks and marked_text.strip():
        chunks.append(TextChunk(text=marked_text, source=source, page=1, sha512=sha512))
    return chunks


# ---------------------------------------------------------------------------
# Word-boundary matching (spec section 6 - precision fix)
# ---------------------------------------------------------------------------

def contains_term(text: str, term: str) -> bool:
    """True when `term` appears as whole word(s) - never as a substring of a
    longer word. 'lease' matches 'the lease agreement' but NOT 'please'."""
    pattern = r"\b" + r"\s+".join(re.escape(w) for w in term.split()) + r"\b"
    return re.search(pattern, text, re.IGNORECASE) is not None


def find_terms(text: str, terms: Iterable[str]) -> List[str]:
    return [t for t in terms if contains_term(text, t)]


# ---------------------------------------------------------------------------
# Sentence + actor helpers
# ---------------------------------------------------------------------------

SENTENCE_SPLIT_RE = re.compile(r"(?<=[.!?])\s+(?=[A-Z(\"'])")

STOP_WORDS = {
    "the", "a", "an", "and", "or", "but", "that", "this", "with", "from",
    "into", "onto", "over", "under", "your", "their", "his", "her", "its",
    "our", "my", "we", "you", "they", "them", "then", "than", "when",
    "what", "which", "who", "whom", "will", "would", "could", "should",
    "have", "has", "had", "been", "being", "were", "was", "are", "is",
    "for", "not", "all", "any", "each", "such", "these", "those", "about",
}

NEGATORS = [
    "no", "not", "never", "denied", "denies", "deny", "refused", "refuses",
    "false", "incorrect", "without", "failed to", "did not", "does not",
    "was not", "were not", "cannot", "didn't", "doesn't", "wasn't",
]


def sentences(text: str) -> List[str]:
    out: List[str] = []
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        out.extend(s for s in SENTENCE_SPLIT_RE.split(line) if s.strip())
    return out


def content_words(text: str) -> List[str]:
    words = re.findall(r"[a-zA-Z]{4,}", text.lower())
    return [w for w in words if w not in STOP_WORDS]


def jaccard(a: Sequence[str], b: Sequence[str]) -> float:
    sa, sb = set(a), set(b)
    if not sa or not sb:
        return 0.0
    return len(sa & sb) / len(sa | sb)


def negation_asymmetry(a: str, b: str) -> bool:
    a_neg = bool(find_terms(a, NEGATORS))
    b_neg = bool(find_terms(b, NEGATORS))
    return a_neg != b_neg


@dataclass
class ActorLexiconEntry:
    name: str
    aliases: List[str] = field(default_factory=list)
    role: str = ""  # e.g. "respondent", "complainant", "institution"


def attribute_actor(text: str, lexicon: Sequence[ActorLexiconEntry]) -> Optional[str]:
    """First lexicon entry whose name or alias appears as whole word(s)."""
    for entry in lexicon:
        candidates = [entry.name] + list(entry.aliases)
        for cand in candidates:
            if cand and contains_term(text, cand):
                return entry.name
    return None


# ---------------------------------------------------------------------------
# Record factory - every detector emits the same anchored findings shape
# ---------------------------------------------------------------------------

_counter = {"n": 0}


def _next_id(prefix: str = "V6") -> str:
    _counter["n"] += 1
    return f"{prefix}-C-{_counter['n']:04d}"


def reset_counter() -> None:
    _counter["n"] = 0


def _record(
    *,
    contradiction_type: str,
    severity: str,
    confidence: str,
    a_text: str,
    a_actor: str,
    b_text: str,
    b_actor: str,
    description: str,
    chunk: TextChunk,
    pattern_type: str,
    supporting: List[str],
    legal_hypothesis: Optional[Dict[str, Any]],
    second_chunk: Optional[TextChunk] = None,
) -> Dict[str, Any]:
    source_document = chunk.source
    if second_chunk is not None and second_chunk.source != chunk.source:
        source_document = f"{chunk.source} + {second_chunk.source}"
    return {
        "contradiction_id": _next_id(),
        "type": contradiction_type,
        "severity": severity,
        "confidence": confidence,
        "proposition_a_text": a_text.strip(),
        "proposition_a_actor": a_actor,
        "proposition_b_text": b_text.strip(),
        "proposition_b_actor": b_actor,
        "conflict_description": description,
        "source_document": source_document,
        "source_page": chunk.page,
        "source_line": 0,
        "sha512_anchor": chunk.sha512,
        "extraction_method": f"deterministic detector ({DETECTOR_VERSION})",
        "temporal_analysis": None,
        "detected_fact": {
            "fact_text": description,
            "source_document": source_document,
            "source_page": chunk.page,
            "source_line": 0,
            "sha512_hash": chunk.sha512,
            "extraction_method": pattern_type,
            "confidence": confidence,
        },
        "logical_pattern": {
            "pattern_type": pattern_type,
            "pattern_description": description,
            "supporting_facts": supporting,
            "contradiction_score": None,
            "detector_version": DETECTOR_VERSION,
        },
        "legal_hypothesis": legal_hypothesis,
        "verification_status": STATUS_ENGINE_VERIFIED,
    }


# ---------------------------------------------------------------------------
# Detector 1: SWORN_VS_SWORN - cross-deponent perjury conflict
# ---------------------------------------------------------------------------

SWORN_MARKERS = [
    "affidavit", "sworn", "under oath", "solemnly declare", "solemn declaration",
    "deponent", "sworn statement", "oath", "commissioner of oaths",
]


def detect_sworn_vs_sworn(
    chunks: Sequence[TextChunk],
    actors: Sequence[ActorLexiconEntry],
    *,
    min_subject_overlap: float = 0.25,
) -> List[Dict[str, Any]]:
    """Two DIFFERENT actors, both speaking in a sworn context, asserting
    opposing versions of the same material fact. One of them is perjuring."""
    sworn_claims: List[Dict[str, Any]] = []
    for chunk in chunks:
        for sent in sentences(chunk.text):
            if not find_terms(sent, SWORN_MARKERS):
                continue
            actor = attribute_actor(sent, actors)
            if actor is None:
                continue
            sworn_claims.append({
                "text": sent, "actor": actor, "chunk": chunk,
                "words": content_words(sent), "neg": bool(find_terms(sent, NEGATORS)),
            })

    out: List[Dict[str, Any]] = []
    seen = set()
    for i in range(len(sworn_claims)):
        for j in range(i + 1, len(sworn_claims)):
            a, b = sworn_claims[i], sworn_claims[j]
            if a["actor"] == b["actor"]:
                continue  # same actor = STATEMENT_VS_STATEMENT's job
            if jaccard(a["words"], b["words"]) < min_subject_overlap:
                continue
            if not negation_asymmetry(a["text"], b["text"]):
                continue
            key = (a["actor"], b["actor"], a["text"][:40], b["text"][:40])
            if key in seen:
                continue
            seen.add(key)
            out.append(_record(
                contradiction_type=TYPE_SWORN_VS_SWORN,
                severity="VERY_HIGH",
                confidence="HIGH",
                a_text=a["text"], a_actor=a["actor"],
                b_text=b["text"], b_actor=b["actor"],
                description=(
                    f'Sworn statements conflict: {a["actor"]} and {b["actor"]} made opposing '
                    "sworn assertions on the same material fact. One statement is necessarily false "
                    "under oath."
                ),
                chunk=a["chunk"], second_chunk=b["chunk"],
                pattern_type="CROSS_DEPONENT_SWORN_CONFLICT",
                supporting=[a["text"].strip(), b["text"].strip()],
                legal_hypothesis={
                    "suggested_offence": "Perjury",
                    "legal_basis": "Two mutually exclusive sworn statements on one material fact; one deponent has sworn falsely.",
                    "jurisdictional_note": "Perjury is prosecutable in all common-law jurisdictions; confirm local statute.",
                    "required_additional_evidence": [
                        "Both sworn documents in original form",
                        "Proof of oath administration for each",
                        "Contemporaneous documentary evidence resolving the fact",
                    ],
                    "is_hypothesis": True,
                    "requires_human_review": True,
                },
            ))
    return out


# ---------------------------------------------------------------------------
# Detector 2: DEVICE_ATTRIBUTION_CHAIN - declaration-linked attribution
# ---------------------------------------------------------------------------

# Entity names terminate on a quote (straight or curly), sentence punctuation,
# an ellipsis, or end-of-line.  The non-greedy capture extends to the FULL
# multi-word entity (e.g. "South Coast Aquaculture"), not just its first word.
_ENTITY_TERMINATOR = r"(?=[\"'\u2018\u2019\u201c\u201d.,;:!?)\u2026]|$)"

DECLARATION_RES = [
    re.compile(
        r"\b(?:i|we)\s+(?:own|have|run|operate|control)\s+(?:a\s+)?"
        r"(?:pty|company|business|firm|entity|ltd|llc|fz-llc)?\s*"
        r"(?:called|named)?\s*[\"'\u201c]?"
        r"([A-Z][A-Za-z0-9 &\-]{2,60}?)" + _ENTITY_TERMINATOR,
        re.IGNORECASE),
    re.compile(
        r"\bmy\s+(?:company|business|pty|firm)\s+(?:called|named|is)?\s*"
        r"[\"'\u201c]?([A-Z][A-Za-z0-9 &\-]{2,60}?)" + _ENTITY_TERMINATOR,
        re.IGNORECASE),
]

ADVERSE_ACT_MARKERS = [
    "hack", "hacked", "hacking", "interception", "intercepted", "unauthorised access",
    "unauthorized access", "archive attempt", "breach", "intrusion", "traced to",
    "remote access", "login attempt", "malware", "spyware", "compromised",
]

# Device/account identifiers may be wrapped in straight OR curly quotes (PDF
# text extraction emits both).  Accept either on both sides.
DEVICE_REF_RE = re.compile(
    r"\b(?:device|account|hostname|profile|handset|computer|machine)\b"
    r"[^\"'\u2018\u2019\u201c\u201d.,;:]{0,40}?"
    r"[\"'\u2018\u2019\u201c\u201d]([A-Z0-9_\-]{4,40})[\"'\u2018\u2019\u201c\u201d]",
    re.IGNORECASE,
)


def _normalise_identifier(value: str) -> str:
    return re.sub(r"[^A-Z0-9]", "", value.upper())


def _entity_matches_device(entity: str, device: str) -> bool:
    ent = _normalise_identifier(entity)
    dev = _normalise_identifier(device)
    if not ent or not dev:
        return False
    if dev in ent or ent in dev:
        return True
    for word in re.findall(r"[A-Za-z]{6,}", entity):
        if word.upper() in dev:
            return True
    return False


def detect_device_attribution_chain(
    chunks: Sequence[TextChunk],
    actors: Sequence[ActorLexiconEntry],
) -> List[Dict[str, Any]]:
    """Actor declares ownership of entity E; a device/account named for E then
    performs an adverse digital act. The declaration is the attribution link."""
    declarations: List[Dict[str, Any]] = []
    adverse_acts: List[Dict[str, Any]] = []

    for chunk in chunks:
        for sent in sentences(chunk.text):
            for rx in DECLARATION_RES:
                m = rx.search(sent)
                if m:
                    actor = attribute_actor(sent, actors)
                    declarations.append({
                        "entity": m.group(1).strip(), "text": sent,
                        "actor": actor, "chunk": chunk,
                    })
            if find_terms(sent, ADVERSE_ACT_MARKERS):
                dm = DEVICE_REF_RE.search(sent)
                if dm:
                    adverse_acts.append({
                        "device": dm.group(1), "text": sent, "chunk": chunk,
                    })

    out: List[Dict[str, Any]] = []
    seen = set()
    for decl in declarations:
        for act in adverse_acts:
            if not _entity_matches_device(decl["entity"], act["device"]):
                continue
            key = (decl["entity"], act["device"])
            if key in seen:
                continue
            seen.add(key)
            actor_name = decl["actor"] or "declarant"
            out.append(_record(
                contradiction_type=TYPE_DEVICE_ATTRIBUTION_CHAIN,
                severity="HIGH",
                confidence="MODERATE",
                a_text=decl["text"], a_actor=actor_name,
                b_text=act["text"], b_actor=f"device '{act['device']}'",
                description=(
                    f"Attribution chain: {actor_name} declared ownership of '{decl['entity']}'; "
                    f"device/account '{act['device']}' - named for that entity - performed an adverse "
                    "digital act. Attribution is corroborating, not conclusive."
                ),
                chunk=decl["chunk"], second_chunk=act["chunk"],
                pattern_type="DECLARATION_LINKED_DEVICE_ATTRIBUTION",
                supporting=[decl["text"].strip(), act["text"].strip()],
                legal_hypothesis={
                    "suggested_offence": "Unauthorised access / interception of data",
                    "legal_basis": "Declaration links the named actor to the device that performed the adverse act.",
                    "jurisdictional_note": "Cybercrime statutes vary; attribution evidence typically corroborates rather than proves alone.",
                    "required_additional_evidence": [
                        "The declaration in original form (message/export)",
                        "Device or account records tying the identifier to the actor",
                        "Logs of the adverse act with timestamps and source addresses",
                    ],
                    "is_hypothesis": True,
                    "requires_human_review": True,
                },
            ))
    return out


# ---------------------------------------------------------------------------
# Detector 3: CRIMINAL_CHARGE_AS_LEVERAGE - retaliatory prosecution
# ---------------------------------------------------------------------------

CHARGE_MARKERS = [
    "opened a case", "laid a charge", "laid charges", "criminal charge",
    "charged with", "case number", "theft case", "criminal case",
    "police case", "opened a criminal case",
]

CASE_NUMBER_RE = re.compile(r"\bCAS\s*\d{1,5}/\d{1,2}/\d{4}\b", re.IGNORECASE)

CIVIL_DISPUTE_MARKERS = [
    "civil", "applicant", "respondent", "high court", "litigation",
    "application", "counterclaim", "commercial dispute", "fraud claim",
]

LEVERAGE_MARKERS = [
    "silence", "silenced", "pressure", "leverage", "retaliation", "retaliatory",
    "intimidate", "intimidation", "withdraw", "drop the case", "back down",
]

DATE_RE = re.compile(
    r"\b(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{4}|\d{4}-\d{2}-\d{2})\b",
    re.IGNORECASE,
)


def detect_criminal_charge_as_leverage(
    chunks: Sequence[TextChunk],
    actors: Sequence[ActorLexiconEntry],
    *,
    window_sentences: int = 3,
) -> List[Dict[str, Any]]:
    """Criminal charge laid by the opposing party in a civil dispute, timed to
    case milestones or accompanied by silencing language."""
    flat: List[Dict[str, Any]] = []
    for chunk in chunks:
        for sent in sentences(chunk.text):
            flat.append({"text": sent, "chunk": chunk})

    out: List[Dict[str, Any]] = []
    seen = set()
    corpus_has_civil = any(find_terms(s["text"], CIVIL_DISPUTE_MARKERS) for s in flat)

    for idx, s in enumerate(flat):
        charge_terms = find_terms(s["text"], CHARGE_MARKERS)
        case_no = CASE_NUMBER_RE.search(s["text"])
        if not charge_terms and not case_no:
            continue

        window = flat[max(0, idx - window_sentences): idx + window_sentences + 1]
        leverage_hit: Optional[Dict[str, Any]] = None
        for w in window:
            if find_terms(w["text"], LEVERAGE_MARKERS):
                leverage_hit = w
                break
        civil_hit = None
        for w in window:
            if find_terms(w["text"], CIVIL_DISPUTE_MARKERS):
                civil_hit = w
                break

        if leverage_hit is None and not (corpus_has_civil and civil_hit is not None):
            continue

        key = s["text"][:60]
        if key in seen:
            continue
        seen.add(key)

        charge_actor = attribute_actor(s["text"], actors) or "unattributed"
        dates = DATE_RE.findall(s["text"])
        anchor_text = leverage_hit["text"] if leverage_hit else (civil_hit["text"] if civil_hit else s["text"])
        anchor_chunk = leverage_hit["chunk"] if leverage_hit else (civil_hit["chunk"] if civil_hit else s["chunk"])
        basis = (
            "silencing language accompanies the charge"
            if leverage_hit else "charge sits inside an ongoing civil dispute between the same parties"
        )
        out.append(_record(
            contradiction_type=TYPE_CRIMINAL_CHARGE_AS_LEVERAGE,
            severity="VERY_HIGH",
            confidence="MODERATE",
            a_text=s["text"], a_actor=charge_actor,
            b_text=anchor_text, b_actor="case context",
            description=(
                "Criminal charge used as leverage in a civil dispute: " + basis +
                (f". Charge reference: {case_no.group(0)}" if case_no else "") +
                (f". Dated: {dates[0]}" if dates else "")
            ),
            chunk=s["chunk"], second_chunk=anchor_chunk,
            pattern_type="CRIMINAL_CHARGE_AS_CIVIL_LEVERAGE",
            supporting=[s["text"].strip(), anchor_text.strip()],
            legal_hypothesis={
                "suggested_offence": "Defeating or obstructing the course of justice / abuse of process",
                "legal_basis": "Criminal process invoked by an opposing civil litigant for leverage rather than legitimate prosecution.",
                "jurisdictional_note": "Abuse of criminal process is actionable in most jurisdictions; motive must be evidenced by pattern, not assertion.",
                "required_additional_evidence": [
                    "The criminal docket / charge sheet",
                    "Timeline of civil-case milestones vs charge date",
                    "Outcome of the criminal charge (withdrawal, nolle prosequi)",
                ],
                "is_hypothesis": True,
                "requires_human_review": True,
            },
        ))
    return out


# ---------------------------------------------------------------------------
# Runner + merge
# ---------------------------------------------------------------------------

def run_v6_detectors(
    chunks: Sequence[TextChunk],
    actors: Sequence[ActorLexiconEntry],
    jurisdiction: Optional[str] = DEFAULT_JURISDICTION,
) -> List[Dict[str, Any]]:
    """Run all three v6 detectors; return deduplicated findings records.

    If ``jurisdiction`` is given, every record's ``legal_hypothesis`` is
    enriched with the local-statute anchor (person -> page -> local law), so
    the forensic chain closes on the local statute for each finding.
    """
    results: List[Dict[str, Any]] = []
    results.extend(detect_sworn_vs_sworn(chunks, actors))
    results.extend(detect_device_attribution_chain(chunks, actors))
    results.extend(detect_criminal_charge_as_leverage(chunks, actors))
    seen = set()
    unique: List[Dict[str, Any]] = []
    for r in results:
        key = (r["type"], r["proposition_a_text"][:50], r["proposition_b_text"][:50])
        if key in seen:
            continue
        seen.add(key)
        unique.append(r)
    if jurisdiction:
        for r in unique:
            r["legal_hypothesis"] = enrich_hypothesis(
                r.get("legal_hypothesis"), r["type"], jurisdiction
            )
            r["jurisdiction"] = r["legal_hypothesis"]["jurisdiction"]
    return unique


def merge_v6_into_findings(findings: Dict[str, Any], v6_records: Sequence[Dict[str, Any]]) -> Dict[str, Any]:
    """Merge v6 detector records into a findings document and recount tiers."""
    findings["contradictions"].extend(v6_records)
    cand = sum(
        1 for r in findings["contradictions"]
        if r.get("verification_status", "").startswith("G3-RAISED")
    )
    findings["g3_candidate_count"] = cand
    findings["engine_verified_count"] = len(findings["contradictions"]) - cand
    return findings
