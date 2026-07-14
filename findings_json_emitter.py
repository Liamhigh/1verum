#!/usr/bin/env python3
"""
Verum Omnis - Findings JSON Emitter v1.0.0
===========================================
Engine-to-G3 contract serializer for the G3 Hybrid Report Pipeline (GHRP).

Status: PROPOSED - pending founder ratification (see G3_HYBRID_REPORT_PIPELINE.md)

What this does:
- Serializes engine Contradiction objects (verum_contradiction_engine_v531c.py)
  into the Findings JSON contract that Gemma 3 narrates from.
- Provides raise_g3_candidate() so contradictions G3 spots in the sealed vault
  are recorded in the identical format, labelled as candidates pending verification.

What this does NOT do:
- It does not modify detection logic. B1-B8 remain the deterministic spine.
- It does not hash artefacts. Hashing stays in the engine/vault (determinism);
  anchors are passed in as pre-computed SHA-512 strings.
- It never touches raw documents. Input is the engine's sealed output only.
"""

from __future__ import annotations

import json
from dataclasses import asdict, is_dataclass
from datetime import datetime, timezone
from enum import Enum
from typing import Any, Dict, Iterable, List, Optional

FINDINGS_JSON_VERSION = "1.0.0"

STATUS_ENGINE_VERIFIED = "ENGINE-VERIFIED"
STATUS_G3_CANDIDATE = "G3-RAISED CANDIDATE - PENDING VERIFICATION"
STATUS_CANDIDATE_PROMOTED = "CANDIDATE PROMOTED - ENGINE-VERIFIED"
STATUS_CANDIDATE_REJECTED = "CANDIDATE REJECTED - REASON LOGGED"


def _enum_value(v: Any) -> Any:
    """Unwrap Enum members to their .value for clean JSON."""
    if isinstance(v, Enum):
        return v.value
    return v


def _obj_to_dict(obj: Any) -> Any:
    """Dataclass / dict / Enum-safe conversion."""
    if obj is None:
        return None
    if isinstance(obj, Enum):
        return obj.value
    if is_dataclass(obj) and not isinstance(obj, type):
        return {k: _obj_to_dict(v) for k, v in asdict(obj).items()}
    if isinstance(obj, dict):
        return {k: _obj_to_dict(v) for k, v in obj.items()}
    if isinstance(obj, (list, tuple)):
        return [_obj_to_dict(v) for v in obj]
    return obj


def contradiction_to_record(c: Any) -> Dict[str, Any]:
    """
    Convert one engine Contradiction (dataclass instance or plain dict) into a
    Findings JSON record. Tolerant of both shapes so sealed/legacy dicts also emit.
    """
    d = _obj_to_dict(c) if not isinstance(c, dict) else dict(c)

    df = d.get("detected_fact") or {}
    lp = d.get("logical_pattern") or {}

    record = {
        "contradiction_id": d.get("contradiction_id", ""),
        "type": _enum_value(d.get("type", "")),
        "severity": _enum_value(d.get("severity", "")),
        "confidence": _enum_value(d.get("confidence", "")),
        "proposition_a_text": d.get("proposition_a_text", ""),
        "proposition_a_actor": d.get("proposition_a_actor", ""),
        "proposition_b_text": d.get("proposition_b_text", ""),
        "proposition_b_actor": d.get("proposition_b_actor", ""),
        "conflict_description": d.get("conflict_description", ""),
        "source_document": df.get("source_document", ""),
        "source_page": df.get("source_page", 0),
        "source_line": df.get("source_line", 0),
        "sha512_anchor": df.get("sha512_hash", ""),
        "extraction_method": df.get("extraction_method", ""),
        "temporal_analysis": d.get("temporal_analysis"),
        "detected_fact": df or None,
        "logical_pattern": lp or None,
        "legal_hypothesis": d.get("legal_hypothesis"),
        "verification_status": d.get("verification_status") or STATUS_ENGINE_VERIFIED,
    }
    return record


def raise_g3_candidate(
    *,
    candidate_id: str,
    contradiction_type: str,
    proposition_a_text: str,
    proposition_b_text: str,
    proposition_a_actor: str,
    proposition_b_actor: str,
    conflict_description: str,
    source_document: str,
    source_page: int,
    sha512_anchor: str,
    severity: str = "MODERATE",
    confidence: str = "MODERATE",
    source_line: int = 0,
    g3_model: str = "gemma-3-4b-it",
) -> Dict[str, Any]:
    """
    Record a contradiction G3 spotted in the sealed vault that the engine missed.

    Two-tier rule (GHRP section 4): candidates are anchored and hashed like any
    other artefact, but labelled pending verification. They never masquerade as
    engine-verified. Promotion happens via engine re-run or human sign-off, and
    is recorded by flipping verification_status to STATUS_CANDIDATE_PROMOTED.
    """
    return {
        "contradiction_id": candidate_id,
        "type": contradiction_type,
        "severity": severity,
        "confidence": confidence,
        "proposition_a_text": proposition_a_text,
        "proposition_a_actor": proposition_a_actor,
        "proposition_b_text": proposition_b_text,
        "proposition_b_actor": proposition_b_actor,
        "conflict_description": conflict_description,
        "source_document": source_document,
        "source_page": source_page,
        "source_line": source_line,
        "sha512_anchor": sha512_anchor,
        "extraction_method": f"G3 vault review ({g3_model}) over sealed bundle",
        "temporal_analysis": None,
        "detected_fact": {
            "fact_text": conflict_description,
            "source_document": source_document,
            "source_page": source_page,
            "source_line": source_line,
            "sha512_hash": sha512_anchor,
            "extraction_method": f"G3 vault review ({g3_model}) over sealed bundle",
            "confidence": confidence,
        },
        "logical_pattern": {
            "pattern_type": contradiction_type,
            "pattern_description": conflict_description,
            "supporting_facts": [proposition_a_text, proposition_b_text],
            "contradiction_score": None,
            "detector_version": f"G3-CANDIDATE ({g3_model})",
        },
        "legal_hypothesis": None,
        "verification_status": STATUS_G3_CANDIDATE,
    }


def emit_findings_json(
    contradictions: Iterable[Any],
    *,
    engine_version: str,
    source_bundle: str,
    case_ids: Optional[List[str]] = None,
    supplement_date: Optional[str] = None,
    integrity_findings: Optional[List[str]] = None,
    extra_records: Optional[Iterable[Dict[str, Any]]] = None,
) -> Dict[str, Any]:
    """
    Build the complete Findings JSON document for one scan.

    `extra_records` accepts G3 candidates produced by raise_g3_candidate().
    Counts split engine-verified vs candidate tiers for the report header.
    """
    records: List[Dict[str, Any]] = [contradiction_to_record(c) for c in contradictions]
    if extra_records:
        records.extend(extra_records)

    candidate_count = sum(
        1 for r in records if r.get("verification_status") == STATUS_G3_CANDIDATE
    )

    return {
        "engine_version": engine_version,
        "findings_json_version": FINDINGS_JSON_VERSION,
        "generated_utc": datetime.now(timezone.utc).isoformat(),
        "source_bundle": source_bundle,
        "case_ids": case_ids or [],
        "supplement_date": supplement_date,
        "engine_verified_count": len(records) - candidate_count,
        "g3_candidate_count": candidate_count,
        "integrity_findings": integrity_findings or [],
        "contradictions": records,
    }


def write_findings_json(findings: Dict[str, Any], output_path: str) -> str:
    """Write Findings JSON to disk (UTF-8, stable formatting). Returns the path."""
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(findings, f, indent=2, ensure_ascii=False)
    return output_path


if __name__ == "__main__":
    # Smoke test: one engine-style record + one G3 candidate.
    demo_engine_record = {
        "contradiction_id": "VO-DEMO-001",
        "type": "STATEMENT_VS_STATEMENT",
        "severity": "HIGH",
        "confidence": "HIGH",
        "proposition_a_text": "Statement A",
        "proposition_a_actor": "Actor A",
        "proposition_b_text": "Statement B",
        "proposition_b_actor": "Actor B",
        "conflict_description": "A and B cannot both be true.",
        "detected_fact": {
            "source_document": "demo.pdf",
            "source_page": 1,
            "source_line": 3,
            "sha512_hash": "ab" * 64,
            "extraction_method": "engine",
            "confidence": "HIGH",
        },
    }
    demo_candidate = raise_g3_candidate(
        candidate_id="VO-DEMO-G3-001",
        contradiction_type="OMISSION",
        proposition_a_text="Reply promised by next week",
        proposition_b_text="No reply exists in sealed record",
        proposition_a_actor="Institution",
        proposition_b_actor="Sealed record",
        conflict_description="Undertaking never honoured anywhere in the vault.",
        source_document="demo.pdf",
        source_page=7,
        sha512_anchor="cd" * 64,
    )
    out = emit_findings_json(
        [demo_engine_record],
        engine_version="5.3.1c",
        source_bundle="demo.pdf",
        case_ids=["demo"],
        extra_records=[demo_candidate],
    )
    print(json.dumps(out, indent=2))
