#!/usr/bin/env python3
"""
Verum Omnis - G3 Hybrid Report Pipeline (GHRP) Companion Module v1.0.0
=======================================================================
Status: RATIFIED — BINDING (founder directive, 2026-07-14).
Spec: G3_HYBRID_REPORT_PIPELINE.md | Schema: FINDINGS_JSON_SCHEMA.json

Builds on verum_contradiction_engine_v531c.py. The engine file is sealed
(VO-CE-v531c-DIGSIM-20260713) and stays byte-identical — this module extends
it from outside, so there is zero regression surface on the sealed engine.

Capabilities:
- FindingsJsonEmitter: engine ForensicReport -> Findings JSON contract v1.0.0
  that Gemma 3 narrates from the sealed vault.
- G3CandidateRegistry: two-tier rule machinery. Contradictions G3 spots in
  the sealed vault are recorded anchored + hashed, labelled pending
  verification, never masquerading as engine-verified.
- VerumContradictionEngineGHRP: drop-in engine subclass adding
  process_and_emit_from_texts/files().

Usage:
    from verum_ghrp import VerumContradictionEngineGHRP
    engine = VerumContradictionEngineGHRP(case_id="VO-CASE-001")
    report, findings = engine.process_and_emit_from_texts(texts)
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Tuple

from verum_contradiction_engine_v531c import (
    CaseConfig,
    Contradiction,
    ForensicReport,
    VerumContradictionEngine,
)

__all__ = [
    "GHRP_VERSION",
    "GHRP_FINDINGS_JSON_VERSION",
    "GHRP_STATUS_ENGINE_VERIFIED",
    "GHRP_STATUS_G3_CANDIDATE",
    "GHRP_STATUS_CANDIDATE_PROMOTED",
    "GHRP_STATUS_CANDIDATE_REJECTED",
    "FindingsJsonEmitter",
    "G3CandidateRegistry",
    "VerumContradictionEngineGHRP",
]


GHRP_VERSION = "1.0.0"
GHRP_FINDINGS_JSON_VERSION = "1.0.0"

GHRP_STATUS_ENGINE_VERIFIED = "ENGINE-VERIFIED"
GHRP_STATUS_G3_CANDIDATE = "G3-RAISED CANDIDATE - PENDING VERIFICATION"
GHRP_STATUS_CANDIDATE_PROMOTED = "CANDIDATE PROMOTED - ENGINE-VERIFIED"
GHRP_STATUS_CANDIDATE_REJECTED = "CANDIDATE REJECTED - REASON LOGGED"


class FindingsJsonEmitter:
    """
    Engine-native serializer: ForensicReport -> Findings JSON contract v1.0.0.

    One record per contradiction. No prose, no interpretation, no percentages.
    Ordinal severity/confidence only. Every record carries its anchor
    (document, page, line, hash). If it is not anchored, it is not emitted.
    """

    @staticmethod
    def record_from_contradiction(c: Contradiction) -> Dict[str, Any]:
        df = c.detected_fact
        status = c.verification_status or {}
        if isinstance(status, dict):
            status_value = status.get("status") or GHRP_STATUS_ENGINE_VERIFIED
        else:
            status_value = str(status)
        record = {
            "contradiction_id": c.contradiction_id,
            "type": c.type.value,
            "severity": c.severity.value,
            "confidence": c.confidence.value,
            "proposition_a_text": c.proposition_a_text,
            "proposition_a_actor": c.proposition_a_actor,
            "proposition_b_text": c.proposition_b_text,
            "proposition_b_actor": c.proposition_b_actor,
            "conflict_description": c.conflict_description,
            "source_document": df.source_document,
            "source_page": df.source_page,
            "source_line": df.source_line,
            "sha512_anchor": df.sha512_hash,
            "extraction_method": df.extraction_method,
            "temporal_analysis": c.temporal_analysis,
            "detected_fact": {
                "fact_text": df.fact_text,
                "source_document": df.source_document,
                "source_page": df.source_page,
                "source_line": df.source_line,
                "sha512_hash": df.sha512_hash,
                "extraction_method": df.extraction_method,
                "confidence": df.confidence.value,
            },
            "logical_pattern": {
                "pattern_type": c.logical_pattern.pattern_type,
                "pattern_description": c.logical_pattern.pattern_description,
                "supporting_facts": c.logical_pattern.supporting_facts,
                "contradiction_score": c.logical_pattern.contradiction_score,
                "detector_version": c.logical_pattern.detector_version,
            },
            "legal_hypothesis": (
                {
                    "suggested_offence": c.legal_hypothesis.suggested_offence,
                    "legal_basis": c.legal_hypothesis.legal_basis,
                    "jurisdictional_note": c.legal_hypothesis.jurisdictional_note,
                    "required_additional_evidence": c.legal_hypothesis.required_additional_evidence,
                    "is_hypothesis": c.legal_hypothesis.is_hypothesis,
                    "requires_human_review": c.legal_hypothesis.requires_human_review,
                }
                if c.legal_hypothesis
                else None
            ),
            "verification_status": status_value,
        }
        return record

    @classmethod
    def from_report(
        cls,
        report: ForensicReport,
        source_bundle: str = "",
        case_ids: Optional[List[str]] = None,
        integrity_findings: Optional[List[str]] = None,
        extra_records: Optional[List[Dict[str, Any]]] = None,
    ) -> Dict[str, Any]:
        """Build the complete Findings JSON document for one engine scan."""
        records = [cls.record_from_contradiction(c) for c in report.contradictions]
        if extra_records:
            records.extend(extra_records)
        candidate_count = sum(
            1 for r in records if r.get("verification_status") == GHRP_STATUS_G3_CANDIDATE
        )
        return {
            "engine_version": report.version,
            "findings_json_version": GHRP_FINDINGS_JSON_VERSION,
            "generated_utc": datetime.now(timezone.utc).isoformat(),
            "source_bundle": source_bundle or report.case_id,
            "case_id": report.case_id,
            "case_ids": case_ids or [],
            "corpus_sha512": report.corpus_hash,
            "engine_verified_count": len(records) - candidate_count,
            "g3_candidate_count": candidate_count,
            "integrity_findings": integrity_findings or [],
            "triple_verification": report.triple_verification,
            "contradictions": records,
        }

    @classmethod
    def write(cls, findings: Dict[str, Any], output_path: str) -> str:
        """Write Findings JSON to disk (UTF-8, stable formatting). Returns the path."""
        import json as _json
        with open(output_path, "w", encoding="utf-8") as f:
            _json.dump(findings, f, indent=2, ensure_ascii=False)
        return output_path


class G3CandidateRegistry:
    """
    Two-tier rule machinery (GHRP spec section 4).

    When Gemma 3, reading the SEALED vault, spots a contradiction the engine
    did not emit, it is recorded here as a G3-RAISED CANDIDATE — anchored and
    hashed like any other artefact, labelled pending verification. Promotion
    happens by engine re-run or human sign-off. Rejected candidates are never
    deleted; the rejection reason is sealed with them.
    """

    def __init__(self, g3_model: str = "gemma-3-4b-it"):
        self.g3_model = g3_model
        self._candidates: Dict[str, Dict[str, Any]] = {}
        self._counter = 0
        self._audit: List[Dict[str, Any]] = []

    def _next_id(self) -> str:
        self._counter += 1
        return f"G3-CAND-{self._counter:04d}"

    def raise_candidate(
        self,
        *,
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
        candidate_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Record a G3-raised candidate. Anchored input is mandatory."""
        if not source_document or source_page < 0 or not sha512_anchor:
            raise ValueError(
                "GHRP two-tier rule: candidates must be anchored "
                "(source_document, source_page, sha512_anchor). "
                "If it is not anchored, it is not emitted."
            )
        cid = candidate_id or self._next_id()
        method = f"G3 vault review ({self.g3_model}) over sealed bundle"
        record = {
            "contradiction_id": cid,
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
            "extraction_method": method,
            "temporal_analysis": None,
            "detected_fact": {
                "fact_text": conflict_description,
                "source_document": source_document,
                "source_page": source_page,
                "source_line": source_line,
                "sha512_hash": sha512_anchor,
                "extraction_method": method,
                "confidence": confidence,
            },
            "logical_pattern": {
                "pattern_type": contradiction_type,
                "pattern_description": conflict_description,
                "supporting_facts": [proposition_a_text, proposition_b_text],
                "contradiction_score": None,
                "detector_version": f"G3-CANDIDATE ({self.g3_model})",
            },
            "legal_hypothesis": None,
            "verification_status": GHRP_STATUS_G3_CANDIDATE,
        }
        self._candidates[cid] = record
        self._audit.append({
            "action": "RAISED",
            "candidate_id": cid,
            "utc": datetime.now(timezone.utc).isoformat(),
        })
        return record

    def promote(self, candidate_id: str, method: str = "human_signoff") -> Dict[str, Any]:
        """Promote a candidate to engine-verified after re-run or human sign-off."""
        record = self._candidates.get(candidate_id)
        if record is None:
            raise ValueError(f"Unknown candidate {candidate_id}")
        record["verification_status"] = GHRP_STATUS_CANDIDATE_PROMOTED
        record["promotion_method"] = method
        record["promoted_utc"] = datetime.now(timezone.utc).isoformat()
        self._audit.append({
            "action": "PROMOTED",
            "candidate_id": candidate_id,
            "method": method,
            "utc": record["promoted_utc"],
        })
        return record

    def reject(self, candidate_id: str, reason: str) -> Dict[str, Any]:
        """Reject a candidate. Never deleted — reason sealed with the record."""
        if not reason:
            raise ValueError("Rejection requires a reason. The record of why is itself evidence.")
        record = self._candidates.get(candidate_id)
        if record is None:
            raise ValueError(f"Unknown candidate {candidate_id}")
        record["verification_status"] = GHRP_STATUS_CANDIDATE_REJECTED
        record["rejection_reason"] = reason
        record["rejected_utc"] = datetime.now(timezone.utc).isoformat()
        self._audit.append({
            "action": "REJECTED",
            "candidate_id": candidate_id,
            "reason": reason,
            "utc": record["rejected_utc"],
        })
        return record

    def pending(self) -> List[Dict[str, Any]]:
        return [r for r in self._candidates.values()
                if r["verification_status"] == GHRP_STATUS_G3_CANDIDATE]

    def all_records(self) -> List[Dict[str, Any]]:
        return list(self._candidates.values())

    def audit_trail(self) -> List[Dict[str, Any]]:
        return list(self._audit)

    def merge_into(self, findings: Dict[str, Any], include_rejected: bool = False) -> Dict[str, Any]:
        """
        Merge registry candidates into a Findings JSON document and recount tiers.
        Rejected candidates stay out of the report body but remain in the registry.
        """
        to_merge = [
            r for r in self._candidates.values()
            if include_rejected or r["verification_status"] != GHRP_STATUS_CANDIDATE_REJECTED
        ]
        findings["contradictions"].extend(to_merge)
        findings["g3_candidate_count"] = sum(
            1 for r in findings["contradictions"]
            if r.get("verification_status") == GHRP_STATUS_G3_CANDIDATE
        )
        findings["engine_verified_count"] = len(findings["contradictions"]) - findings["g3_candidate_count"]
        findings["g3_candidate_audit"] = self.audit_trail()
        return findings


class VerumContradictionEngineGHRP(VerumContradictionEngine):
    """
    GHRP-enabled engine. Builds on VerumContradictionEngine — the base class
    is untouched. Adds one capability: after a scan, emit the Findings JSON
    contract that Gemma 3 narrates from the sealed vault.

    Usage:
        engine = VerumContradictionEngineGHRP(case_id="VO-CASE-001")
        report, findings = engine.process_and_emit_from_texts(texts)
    """

    def __init__(self, case_id: str = "UNSPECIFIED", case_config: Optional[CaseConfig] = None,
                 g3_model: str = "gemma-3-4b-it"):
        super().__init__(case_id=case_id, case_config=case_config)
        self.candidate_registry = G3CandidateRegistry(g3_model=g3_model)

    def _emit(self, report: ForensicReport, source_bundle: str = "",
              case_ids: Optional[List[str]] = None,
              integrity_findings: Optional[List[str]] = None) -> Dict[str, Any]:
        return FindingsJsonEmitter.from_report(
            report,
            source_bundle=source_bundle,
            case_ids=case_ids or ([self.case_id] if self.case_id else []),
            integrity_findings=integrity_findings,
        )

    def process_and_emit_from_texts(self, texts: List[str],
                                    source_bundle: str = "",
                                    case_ids: Optional[List[str]] = None,
                                    integrity_findings: Optional[List[str]] = None
                                    ) -> Tuple[ForensicReport, Dict[str, Any]]:
        report = self.process_from_texts(texts)
        return report, self._emit(report, source_bundle, case_ids, integrity_findings)

    def process_and_emit_from_files(self, filepaths: List[str],
                                    source_bundle: str = "",
                                    case_ids: Optional[List[str]] = None,
                                    integrity_findings: Optional[List[str]] = None
                                    ) -> Tuple[ForensicReport, Dict[str, Any]]:
        report = self.process_from_files(filepaths)
        return report, self._emit(report, source_bundle or ", ".join(filepaths),
                                  case_ids, integrity_findings)
