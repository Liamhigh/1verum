"""
verum_statutes.py  --  Verum Omnis v6 local-statute enrichment layer.

Forensic chain (ratified by the case owner):
    extraction  ->  contradiction  ->  PERSON  ->  PAGE  ->  LOCAL LAW (statute)

The contradiction engine and the v6 detectors already anchor every finding to a
PERSON (actor lexicon) and a PAGE (TextChunk.page + sha512).  This module adds
the final anchor: the LOCAL STATUTE / LAW that the contradiction engages.

Design rules
------------
1.  The sealed engine (v5.3.1c) is NEVER modified.  Statute enrichment is a
    pure read/transform pass over the findings-JSON contract.
2.  Statute citations are *hypotheses*, not legal advice.  Every enriched
    hypothesis keeps ``is_hypothesis: True`` and ``requires_human_review: True``
    and gains a ``local_statutes`` list plus a ``jurisdiction`` field.
3.  Jurisdiction is selected explicitly (default ZA, because the live matters
    -- SAPS correspondence, the EIB complaint and judgment H208/25 -- are all
    South African).  A GENERIC common-law fallback is provided so the chain
    still closes on unmapped jurisdictions.

Statute accuracy notes (verified against the Acts themselves)
-------------------------------------------------------------
ZA - Perjury / false sworn statement
    * Perjury is a *common-law* offence in South Africa (up to 10 years).
    * s 9, Justices of the Peace and Commissioners of Oaths Act 16 of 1963:
      a false statement "knowing it to be false" in an affidavit, affirmation
      or solemn/attested declaration is an offence liable to the penalties
      prescribed by law for perjury.
    * s 319, Criminal Procedure Act 51 of 1977: proof of previous inconsistent
      statements by a witness.
ZA - Cybercrime / device-attribution
    * Cybercrimes Act 19 of 2020 (in force 1 Dec 2021):
        s 2  unlawful access
        s 3  unlawful interception of data
        s 4  unlawful acts re software/hardware tool
        s 5  unlawful interference with data or computer program
        s 6  unlawful interference with storage medium / system
        s 7  unlawful acquisition/possession of password or access code
        s 8  cyber fraud
        s 9  cyber forgery and uttering
        s 10 cyber extortion
        s 11 aggravated offences (restricted computer system)
        s 12 theft of incorporeal property
        s 54 ECSP 72-hour breach reporting (not yet in force)
    * RICA 70 of 2002, s 2: prohibition on interception of communications.
    * ECTA 25 of 2002, s 86: legacy unauthorised-access provision (largely
      superseded by the Cybercrimes Act but still cited for pre-2021 conduct).
ZA - Extortion / criminal charge as leverage
    * Cyber extortion: s 10, Cybercrimes Act 19 of 2020.
    * Extortion: common-law offence.
    * Intimidation Act 72 of 1982, s 1.
    * Defeating or obstructing the course of justice: common-law offence.
    * PRECCA 12 of 2004 (corruption) where a gratification is present.
    * Contempt of court: common-law offence (relevant to judgment H208/25).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, Iterable, List, Optional, Sequence

# The three ratified v6 contradiction types (kept here so this module is
# self-describing; they mirror the constants in verum_v6_detectors.py).
TYPE_SWORN_VS_SWORN = "SWORN_VS_SWORN"
TYPE_DEVICE_ATTRIBUTION_CHAIN = "DEVICE_ATTRIBUTION_CHAIN"
TYPE_CRIMINAL_CHARGE_AS_LEVERAGE = "CRIMINAL_CHARGE_AS_LEVERAGE"

JURISDICTION_ZA = "ZA"
JURISDICTION_GENERIC = "GENERIC"
DEFAULT_JURISDICTION = JURISDICTION_ZA


@dataclass
class StatuteCitation:
    """A single local-law anchor attached to a legal hypothesis."""

    instrument: str            # short title of the Act / source of law
    citation: str              # pinpoint: section / common-law label
    note: str = ""             # one-line applicability note
    statutory: bool = True     # False => common-law / case-law anchor

    def as_dict(self) -> Dict[str, Any]:
        return {
            "instrument": self.instrument,
            "citation": self.citation,
            "note": self.note,
            "statutory": self.statutory,
        }


# ---------------------------------------------------------------------------
# ZA (South Africa) statute map
# ---------------------------------------------------------------------------

_ZA: Dict[str, List[StatuteCitation]] = {
    # -- v6 type 1: cross-deponent sworn conflict ---------------------------
    TYPE_SWORN_VS_SWORN: [
        StatuteCitation(
            "Justices of the Peace and Commissioners of Oaths Act 16 of 1963",
            "s 9",
            "False statement in an affidavit/affirmation/solemn declaration, "
            "made knowing it to be false; liable to perjury penalties.",
        ),
        StatuteCitation(
            "Perjury (common law)",
            "common-law offence",
            "Wilfully making a false statement under oath; up to 10 years "
            "imprisonment.",
            statutory=False,
        ),
        StatuteCitation(
            "Criminal Procedure Act 51 of 1977",
            "s 319",
            "Admissibility/proof of a witness's previous inconsistent "
            "statement.",
        ),
    ],
    # -- v6 type 2: declaration-linked device attribution -------------------
    TYPE_DEVICE_ATTRIBUTION_CHAIN: [
        StatuteCitation(
            "Cybercrimes Act 19 of 2020",
            "s 2",
            "Unlawful and intentional access to a computer system or data.",
        ),
        StatuteCitation(
            "Cybercrimes Act 19 of 2020",
            "s 3",
            "Unlawful and intentional interception of data.",
        ),
        StatuteCitation(
            "Cybercrimes Act 19 of 2020",
            "s 5",
            "Unlawful interference with data or a computer program.",
        ),
        StatuteCitation(
            "Cybercrimes Act 19 of 2020",
            "s 11",
            "Aggravated offence where the target is a restricted computer "
            "system (financial institution / organ of state).",
        ),
        StatuteCitation(
            "Regulation of Interception of Communications Act 70 of 2002 (RICA)",
            "s 2",
            "Prohibition on interception of communications without authority.",
        ),
        StatuteCitation(
            "Electronic Communications and Transactions Act 25 of 2002 (ECTA)",
            "s 86",
            "Legacy unauthorised access/interception provision (pre-Dec-2021 "
            "conduct).",
        ),
    ],
    # -- v6 type 3: criminal charge used as leverage ------------------------
    TYPE_CRIMINAL_CHARGE_AS_LEVERAGE: [
        StatuteCitation(
            "Cybercrimes Act 19 of 2020",
            "s 10",
            "Cyber extortion where the threat/pressure is applied via data "
            "message.",
        ),
        StatuteCitation(
            "Extortion (common law)",
            "common-law offence",
            "Unlawfully and intentionally applying pressure to induce a person "
            "to submit or hand over an advantage.",
            statutory=False,
        ),
        StatuteCitation(
            "Intimidation Act 72 of 1982",
            "s 1",
            "Intimidating conduct intended to compel a course of action.",
        ),
        StatuteCitation(
            "Defeating or obstructing the course of justice (common law)",
            "common-law offence",
            "Abusing a criminal charge/report to derail a lawful process.",
            statutory=False,
        ),
        StatuteCitation(
            "Prevention and Combating of Corrupt Activities Act 12 of 2004 (PRECCA)",
            "ss 3-7",
            "Applies where the leverage involves an offer/demand of a "
            "gratification.",
        ),
    ],

    # -----------------------------------------------------------------------
    # Existing engine types -- mapped so the WHOLE frenzy report, not just the
    # three v6 types, closes the local-law anchor where a confident mapping
    # exists.  Unmapped types fall through to the GENERIC/common-law note.
    # -----------------------------------------------------------------------
    "CONTRADICTORY_STATEMENTS": [
        StatuteCitation(
            "Criminal Procedure Act 51 of 1977",
            "s 319",
            "Previous inconsistent statement by a witness.",
        ),
    ],
    "SWORN_VS_UNSIGNED": [
        StatuteCitation(
            "Justices of the Peace and Commissioners of Oaths Act 16 of 1963",
            "s 9",
            "False statement in a sworn affidavit.",
        ),
    ],
    "DOCUMENT_ALTERATION": [
        StatuteCitation(
            "Cybercrimes Act 19 of 2020",
            "s 9",
            "Cyber forgery and uttering.",
        ),
        StatuteCitation(
            "Forgery and uttering (common law)",
            "common-law offence",
            "Unlawful falsification of a document with intent to defraud.",
            statutory=False,
        ),
    ],
    "TIMELINE_CONFLICT": [
        StatuteCitation(
            "Defeating or obstructing the course of justice (common law)",
            "common-law offence",
            "False account of chronology intended to mislead a process.",
            statutory=False,
        ),
    ],
    "CONTEMPT_OF_COURT_ORDER": [
        StatuteCitation(
            "Contempt of court (common law)",
            "common-law offence",
            "Wilful disobedience of a court order (e.g. judgment H208/25).",
            statutory=False,
        ),
    ],
    "BREACH_OF_COURT_ORDER": [
        StatuteCitation(
            "Contempt of court (common law)",
            "common-law offence",
            "Wilful disobedience of a court order (e.g. judgment H208/25).",
            statutory=False,
        ),
    ],
}


# ---------------------------------------------------------------------------
# GENERIC (common-law) fallback -- used for any jurisdiction without a bespoke
# map, and for any type not present in the ZA map.
# ---------------------------------------------------------------------------

_GENERIC: Dict[str, List[StatuteCitation]] = {
    TYPE_SWORN_VS_SWORN: [
        StatuteCitation(
            "Perjury (common law)",
            "common-law offence",
            "Two mutually exclusive sworn statements on one material fact; one "
            "deponent has sworn falsely. Confirm the local perjury statute.",
            statutory=False,
        ),
    ],
    TYPE_DEVICE_ATTRIBUTION_CHAIN: [
        StatuteCitation(
            "Computer-misuse / unlawful-access legislation",
            "jurisdiction-specific",
            "Unauthorised access to or interception of data is an offence in "
            "virtually all jurisdictions; cite the local computer-misuse act.",
        ),
    ],
    TYPE_CRIMINAL_CHARGE_AS_LEVERAGE: [
        StatuteCitation(
            "Extortion / blackmail (common law)",
            "common-law offence",
            "Applying pressure (including a threatened criminal charge) to "
            "extract an advantage. Confirm the local extortion/blackmail law.",
            statutory=False,
        ),
        StatuteCitation(
            "Perverting / defeating the course of justice (common law)",
            "common-law offence",
            "Abusing a criminal process to obstruct a lawful one.",
            statutory=False,
        ),
    ],
}


JURISDICTIONS: Dict[str, Dict[str, List[StatuteCitation]]] = {
    JURISDICTION_ZA: _ZA,
    JURISDICTION_GENERIC: _GENERIC,
}


def normalise_jurisdiction(code: Optional[str]) -> str:
    """Resolve a user-supplied jurisdiction code to a known map key."""
    if not code:
        return DEFAULT_JURISDICTION
    c = code.strip().upper()
    if c in JURISDICTIONS:
        return c
    # Common aliases for South Africa.
    if c in {"RSA", "SOUTH AFRICA", "ZAF", "SA"}:
        return JURISDICTION_ZA
    return JURISDICTION_GENERIC


def statutes_for_type(
    contradiction_type: str,
    jurisdiction: Optional[str] = None,
) -> List[StatuteCitation]:
    """Return the ordered statute citations for a contradiction type.

    Falls back to the GENERIC map when the jurisdiction has no bespoke entry
    for the type.  Returns an empty list if nothing maps anywhere.
    """
    j = normalise_jurisdiction(jurisdiction)
    primary = JURISDICTIONS.get(j, {})
    cites = primary.get(contradiction_type)
    if cites:
        return list(cites)
    if j != JURISDICTION_GENERIC:
        fallback = _GENERIC.get(contradiction_type)
        if fallback:
            return list(fallback)
    return []


# ---------------------------------------------------------------------------
# Enrichment of the findings-JSON contract
# ---------------------------------------------------------------------------

_JURISDICTION_NOTE = {
    JURISDICTION_ZA: (
        "Local law: Republic of South Africa. Statute citations are "
        "hypotheses for legal review, not legal advice."
    ),
    JURISDICTION_GENERIC: (
        "Local law: common-law baseline. Confirm the equivalent statute in "
        "the governing jurisdiction."
    ),
}


def enrich_hypothesis(
    hypothesis: Optional[Dict[str, Any]],
    contradiction_type: str,
    jurisdiction: Optional[str] = None,
) -> Optional[Dict[str, Any]]:
    """Attach ``local_statutes`` + ``jurisdiction`` to one legal hypothesis.

    Returns a NEW dict (does not mutate the caller's).  If the hypothesis is
    None, returns None (record stays as-is).
    """
    if hypothesis is None:
        return None
    j = normalise_jurisdiction(jurisdiction)
    cites = statutes_for_type(contradiction_type, j)
    enriched = dict(hypothesis)
    enriched["jurisdiction"] = j
    enriched["local_statutes"] = [c.as_dict() for c in cites]
    # Keep the hypothesis flagging explicit even after enrichment.
    enriched.setdefault("is_hypothesis", True)
    enriched.setdefault("requires_human_review", True)
    # Replace the generic "confirm local statute" note with the concrete one.
    enriched["jurisdictional_note"] = _JURISDICTION_NOTE.get(
        j, _JURISDICTION_NOTE[JURISDICTION_GENERIC]
    )
    return enriched


def _iter_contradiction_records(findings: Dict[str, Any]) -> Iterable[Dict[str, Any]]:
    """Yield every record dict that carries a 'type' and a 'legal_hypothesis'.

    The findings contract stores contradictions under several possible keys
    depending on tier (engine-verified vs G3-raised candidate).  We walk all
    list-valued members and pick up anything shaped like a contradiction
    record.
    """
    for value in findings.values():
        if isinstance(value, list):
            for item in value:
                if isinstance(item, dict) and "type" in item:
                    yield item


def enrich_legal_hypotheses(
    findings: Dict[str, Any],
    jurisdiction: Optional[str] = None,
) -> Dict[str, Any]:
    """Enrich every contradiction record in a findings document, in place.

    Adds, to each record's ``legal_hypothesis``:
        * ``jurisdiction``    -- resolved jurisdiction code
        * ``local_statutes``  -- ordered list of statute citations
        * a concrete ``jurisdictional_note``

    Returns the same dict for convenience.  Records without a legal_hypothesis
    get one synthesised so the local-law anchor is never dropped.
    """
    j = normalise_jurisdiction(jurisdiction)
    count = 0
    for rec in _iter_contradiction_records(findings):
        ctype = rec.get("type")
        if not ctype:
            continue
        hyp = rec.get("legal_hypothesis")
        if hyp is None:
            # Synthesise a minimal hypothesis so the local-law anchor closes.
            hyp = {
                "suggested_offence": None,
                "legal_basis": rec.get("conflict_description", ""),
                "is_hypothesis": True,
                "requires_human_review": True,
            }
        rec["legal_hypothesis"] = enrich_hypothesis(hyp, ctype, j)
        # Mirror the jurisdiction at record level for easy filtering.
        rec["jurisdiction"] = j
        count += 1
    findings.setdefault("statute_enrichment", {})
    findings["statute_enrichment"] = {
        "jurisdiction": j,
        "records_enriched": count,
        "note": _JURISDICTION_NOTE.get(j, _JURISDICTION_NOTE[JURISDICTION_GENERIC]),
    }
    return findings


__all__ = [
    "StatuteCitation",
    "JURISDICTION_ZA",
    "JURISDICTION_GENERIC",
    "DEFAULT_JURISDICTION",
    "TYPE_SWORN_VS_SWORN",
    "TYPE_DEVICE_ATTRIBUTION_CHAIN",
    "TYPE_CRIMINAL_CHARGE_AS_LEVERAGE",
    "normalise_jurisdiction",
    "statutes_for_type",
    "enrich_hypothesis",
    "enrich_legal_hypotheses",
]
