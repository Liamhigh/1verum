#!/usr/bin/env python3
"""
Verum Omnis - GHRP Integration Tests v1.0.0
============================================
Tests the G3 Hybrid Report Pipeline companion module (verum_ghrp.py).

Runs the REAL engine end-to-end: scan -> findings JSON -> candidate lifecycle.
No mocks for the engine path; the deterministic spine is exercised as-is.
"""

import json
import os
import sys
import unittest

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, REPO_ROOT)

from verum_ghrp import (  # noqa: E402
    FindingsJsonEmitter,
    G3CandidateRegistry,
    VerumContradictionEngineGHRP,
    GHRP_STATUS_G3_CANDIDATE,
    GHRP_STATUS_CANDIDATE_PROMOTED,
    GHRP_STATUS_CANDIDATE_REJECTED,
    GHRP_STATUS_ENGINE_VERIFIED,
)

SCHEMA_PATH = os.path.join(REPO_ROOT, "FINDINGS_JSON_SCHEMA.json")
REQUIRED_RECORD_FIELDS = [
    "contradiction_id", "type", "severity", "confidence",
    "proposition_a_text", "proposition_b_text",
    "proposition_a_actor", "proposition_b_actor",
    "conflict_description", "verification_status",
]

DEMO_TEXTS = [
    "On 10 March 2025 Marius stated the deal fell through and Greensky never invoiced the client.",
    "On 6 April 2025 Marius admitted that Kevin completed the deal on 13 March 2025.",
    "Standard Bank promised on 19 June 2025 that the client would be contacted by next week.",
    "Standard Bank never contacted the client. No one called. No letter arrived.",
    "The cease and desist letter was served on 23 April 2025 according to the attorney.",
    "The cease and desist letter is dated 30 April 2025 on its face.",
]


class TestEngineToFindingsJson(unittest.TestCase):
    """The engine emits the findings JSON contract from a real scan."""

    @classmethod
    def setUpClass(cls):
        cls.engine = VerumContradictionEngineGHRP(case_id="VO-GHRP-TEST-001")
        cls.report, cls.findings = cls.engine.process_and_emit_from_texts(
            DEMO_TEXTS, source_bundle="ghrp_demo.txt"
        )

    def test_scan_detects_contradictions(self):
        self.assertGreaterEqual(len(self.report.contradictions), 1)

    def test_findings_header_contract(self):
        for key in ("engine_version", "findings_json_version", "source_bundle",
                    "corpus_sha512", "engine_verified_count", "g3_candidate_count",
                    "contradictions"):
            self.assertIn(key, self.findings)
        self.assertEqual(self.findings["findings_json_version"], "1.0.0")

    def test_every_record_anchored_and_complete(self):
        for record in self.findings["contradictions"]:
            for field in REQUIRED_RECORD_FIELDS:
                self.assertIn(field, record, f"{record.get('contradiction_id')} missing {field}")
            self.assertTrue(record["sha512_anchor"], "record must carry SHA-512 anchor")
            self.assertEqual(record["verification_status"], GHRP_STATUS_ENGINE_VERIFIED)

    def test_corpus_hash_matches_report(self):
        self.assertEqual(self.findings["corpus_sha512"], self.report.corpus_hash)

    def test_counts_split_tiers(self):
        total = len(self.findings["contradictions"])
        self.assertEqual(
            self.findings["engine_verified_count"] + self.findings["g3_candidate_count"], total
        )


class TestG3CandidateRegistry(unittest.TestCase):
    """Two-tier rule: candidates anchored, labelled, and never silently promoted."""

    def setUp(self):
        self.registry = G3CandidateRegistry()

    def _raise(self, cid=None):
        return self.registry.raise_candidate(
            contradiction_type="OMISSION",
            proposition_a_text="Reply promised by next week",
            proposition_b_text="No reply exists anywhere in the sealed vault",
            proposition_a_actor="Standard Bank",
            proposition_b_actor="Sealed record",
            conflict_description="Undertaking never honoured in the record.",
            source_document="ghrp_demo.txt",
            source_page=3,
            sha512_anchor="ab" * 64,
            candidate_id=cid,
        )

    def test_candidate_labelled_pending_verification(self):
        record = self._raise()
        self.assertEqual(record["verification_status"], GHRP_STATUS_G3_CANDIDATE)

    def test_unanchored_candidate_refused(self):
        with self.assertRaises(ValueError):
            self.registry.raise_candidate(
                contradiction_type="OMISSION",
                proposition_a_text="x", proposition_b_text="y",
                proposition_a_actor="a", proposition_b_actor="b",
                conflict_description="unanchored",
                source_document="", source_page=0, sha512_anchor="",
            )

    def test_promote_flips_status_with_audit(self):
        record = self._raise()
        promoted = self.registry.promote(record["contradiction_id"], method="engine_rerun")
        self.assertEqual(promoted["verification_status"], GHRP_STATUS_CANDIDATE_PROMOTED)
        self.assertEqual(promoted["promotion_method"], "engine_rerun")
        actions = [a["action"] for a in self.registry.audit_trail()]
        self.assertEqual(actions, ["RAISED", "PROMOTED"])

    def test_reject_requires_reason_and_never_deletes(self):
        record = self._raise()
        with self.assertRaises(ValueError):
            self.registry.reject(record["contradiction_id"], reason="")
        rejected = self.registry.reject(record["contradiction_id"], reason="Duplicate of engine finding")
        self.assertEqual(rejected["verification_status"], GHRP_STATUS_CANDIDATE_REJECTED)
        self.assertEqual(len(self.registry.all_records()), 1, "rejected candidates are retained")

    def test_merge_recounts_tiers_and_excludes_rejected(self):
        engine = VerumContradictionEngineGHRP(case_id="VO-GHRP-TEST-002")
        _, findings = engine.process_and_emit_from_texts(DEMO_TEXTS[:2])
        promoted = self._raise()
        self.registry.promote(promoted["contradiction_id"])
        rejected = self._raise(cid="G3-CAND-REJECT")
        self.registry.reject(rejected["contradiction_id"], reason="Not supported by the sealed record")
        merged = self.registry.merge_into(findings)
        ids = [r["contradiction_id"] for r in merged["contradictions"]]
        self.assertIn(promoted["contradiction_id"], ids)
        self.assertNotIn("G3-CAND-REJECT", ids)
        self.assertEqual(
            merged["engine_verified_count"] + merged["g3_candidate_count"],
            len(merged["contradictions"]),
        )


class TestSchemaFileIntegrity(unittest.TestCase):
    """The shipped schema parses and names the required record fields."""

    def test_schema_parses(self):
        with open(SCHEMA_PATH, encoding="utf-8") as f:
            schema = json.load(f)
        required = schema["$defs"]["contradictionRecord"]["required"]
        for field in REQUIRED_RECORD_FIELDS:
            self.assertIn(field, required)


if __name__ == "__main__":
    unittest.main(verbosity=2)
