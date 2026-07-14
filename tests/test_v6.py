#!/usr/bin/env python3
"""
Verum Omnis - v6 Validation Suite
=================================
Status: RATIFIED - BINDING (founder directive, 2026-07-14).
Spec: CANDIDATE_CONTRADICTION_TYPES_v6.md + G3_HYBRID_REPORT_PIPELINE.md

Validates the ratified forensic chain:

    extraction -> contradiction -> PERSON -> PAGE -> LOCAL LAW (statute)

Covers:
  1. Word-boundary precision fix ("lease" must not fire inside "please").
  2. The three v6 detectors fire on anchored text and emit the findings shape.
  3. Every emitted finding carries PERSON (actor), PAGE (source_page) and
     LOCAL LAW (local_statutes) once enriched.
  4. Statute map resolves ZA + GENERIC and falls back sanely.
  5. merge_v6_into_findings recounts tiers.
  6. GHRP JUDICIALLY-CONFIRMED tier (the H208/25 promotion path).

Run:  python3 test_v6.py
"""

import hashlib
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))  # repo root holds the modules
sys.path.insert(0, HERE)

import verum_v6_detectors as V  # noqa: E402
import verum_statutes as S  # noqa: E402
import verum_ghrp as G  # noqa: E402
from verum_v6_detectors import ActorLexiconEntry, TextChunk  # noqa: E402


def _sha(label: str) -> str:
    return hashlib.sha512(label.encode()).hexdigest()


# Actor lexicon mirroring the live matters (case126 / greensky / EIB complaint).
ACTORS = [
    ActorLexiconEntry("Liam Highstead", aliases=["Liam", "Highstead"], role="complainant"),
    ActorLexiconEntry("Green Sky", aliases=["GreenSky", "green sky"], role="institution"),
]


# ---------------------------------------------------------------------------
# 1. Word-boundary precision
# ---------------------------------------------------------------------------

class TestWordBoundaryPrecision(unittest.TestCase):
    def test_lease_does_not_fire_inside_please(self):
        self.assertFalse(V.contains_term("Please find attached.", "lease"))

    def test_lease_fires_as_whole_word(self):
        self.assertTrue(V.contains_term("The lease was signed.", "lease"))

    def test_multiword_phrase_boundary(self):
        self.assertTrue(
            V.contains_term("There was unauthorised access to the server.", "unauthorised access")
        )
        self.assertFalse(
            V.contains_term("He was an authorised accessor.", "unauthorised access")
        )

    def test_case_insensitive(self):
        self.assertTrue(V.contains_term("SWORN affidavit", "sworn"))


# ---------------------------------------------------------------------------
# 2. The three detectors fire on anchored text
# ---------------------------------------------------------------------------

class TestV6DetectorsFire(unittest.TestCase):
    def setUp(self):
        V.reset_counter()

    def test_sworn_vs_sworn(self):
        # Two DIFFERENT actors, both sworn, opposing on one material fact.
        a = TextChunk(
            text=("In my sworn affidavit, I Liam Highstead solemnly declare that the "
                  "rental payment deposit funds were never paid into the account."),
            source="case126.pdf", page=3, sha512=_sha("a"),
        )
        b = TextChunk(
            text=("In the sworn affidavit the deponent Green Sky solemnly declares that the "
                  "rental payment deposit funds were paid into the account."),
            source="case126.pdf", page=12, sha512=_sha("b"),
        )
        recs = V.detect_sworn_vs_sworn([a, b], ACTORS)
        self.assertTrue(recs, "SWORN_VS_SWORN did not fire")
        r = recs[0]
        self.assertEqual(r["type"], "SWORN_VS_SWORN")
        self.assertEqual(r["severity"], "VERY_HIGH")
        # PERSON anchor: two different actors.
        self.assertNotEqual(r["proposition_a_actor"], r["proposition_b_actor"])

    def test_device_attribution_chain(self):
        decl = TextChunk(
            text="I own a company called Greensky Holdings Pty Ltd, says Liam Highstead.",
            source="case126.pdf", page=7, sha512=_sha("decl"),
        )
        act = TextChunk(
            text=("The breach and unauthorised access was traced to device "
                  "'GREENSKY-SERVER-01' on the network."),
            source="case126.pdf", page=8, sha512=_sha("act"),
        )
        recs = V.detect_device_attribution_chain([decl, act], ACTORS)
        self.assertTrue(recs, "DEVICE_ATTRIBUTION_CHAIN did not fire")
        r = recs[0]
        self.assertEqual(r["type"], "DEVICE_ATTRIBUTION_CHAIN")
        self.assertIn("GREENSKY", r["proposition_b_actor"].upper())

    def test_device_attribution_multiword_entity_regression(self):
        # Regression: the exact real case126 phrasing. The entity capture must
        # extend to the FULL multi-word name ("South Coast Aquaculture"), not
        # stop at its first word, so it links to device "SCAQUACULTURE".
        decl = TextChunk(
            text="\u201cI have a PTY called South Coast Aquaculture\u2026\u201d",
            source="case126.pdf", page=5, sha512=_sha("decl2"),
        )
        act = TextChunk(
            text="The attempted Gmail intrusion originated from a device labeled "
                 "\u201cSCAQUACULTURE\u201d on the network.",
            source="case126.pdf", page=5, sha512=_sha("act2"),
        )
        recs = V.detect_device_attribution_chain([decl, act], ACTORS)
        self.assertTrue(recs, "multi-word entity declaration was not linked to device")
        self.assertIn("SCAQUACULTURE", recs[0]["proposition_b_actor"])

    def test_criminal_charge_as_leverage(self):
        charge = TextChunk(
            text=("Green Sky opened a criminal case against Liam Highstead, case number "
                  "CAS 142/07/2025, on 12 Jul 2025. This was done to pressure Liam "
                  "Highstead to withdraw the application and back down."),
            source="case126.pdf", page=20, sha512=_sha("charge"),
        )
        recs = V.detect_criminal_charge_as_leverage([charge], ACTORS)
        self.assertTrue(recs, "CRIMINAL_CHARGE_AS_LEVERAGE did not fire")
        r = recs[0]
        self.assertEqual(r["type"], "CRIMINAL_CHARGE_AS_LEVERAGE")
        self.assertEqual(r["severity"], "VERY_HIGH")

    def test_no_fire_without_anchor_opposition(self):
        # Two sworn statements that AGREE must not raise SWORN_VS_SWORN.
        a = TextChunk(
            text="In my sworn affidavit I Liam Highstead declare the funds were paid on time.",
            source="d.pdf", page=1, sha512=_sha("x"),
        )
        b = TextChunk(
            text="In my sworn affidavit Green Sky confirms the funds were paid on time.",
            source="d.pdf", page=2, sha512=_sha("y"),
        )
        recs = V.detect_sworn_vs_sworn([a, b], ACTORS)
        self.assertEqual(recs, [], "Agreement must not be flagged as a contradiction")


# ---------------------------------------------------------------------------
# 3. The full forensic chain: PERSON + PAGE + LOCAL LAW
# ---------------------------------------------------------------------------

class TestForensicChain(unittest.TestCase):
    def setUp(self):
        V.reset_counter()
        self.chunks = [
            TextChunk(
                text=("In my sworn affidavit, I Liam Highstead solemnly declare that the "
                      "rental payment deposit funds were never paid into the account."),
                source="case126.pdf", page=3, sha512=_sha("a")),
            TextChunk(
                text=("In the sworn affidavit the deponent Green Sky solemnly declares that the "
                      "rental payment deposit funds were paid into the account."),
                source="case126.pdf", page=12, sha512=_sha("b")),
        ]

    def test_chain_closes_person_page_law(self):
        recs = V.run_v6_detectors(self.chunks, ACTORS, jurisdiction="ZA")
        self.assertTrue(recs, "no findings produced")
        for r in recs:
            # PERSON
            self.assertTrue(r["proposition_a_actor"] or r["proposition_b_actor"])
            # PAGE
            self.assertIsInstance(r["source_page"], int)
            self.assertGreaterEqual(r["source_page"], 1)
            # LOCAL LAW
            lh = r.get("legal_hypothesis") or {}
            self.assertEqual(lh.get("jurisdiction"), "ZA")
            self.assertTrue(lh.get("local_statutes"), "no local statutes attached")
            first = lh["local_statutes"][0]
            self.assertIn("instrument", first)
            self.assertIn("citation", first)
            # Hypothesis flagging must survive enrichment.
            self.assertTrue(lh.get("is_hypothesis"))
            self.assertTrue(lh.get("requires_human_review"))

    def test_sworn_maps_to_perjury_statute(self):
        recs = [r for r in V.run_v6_detectors(self.chunks, ACTORS, jurisdiction="ZA")
                if r["type"] == "SWORN_VS_SWORN"]
        self.assertTrue(recs)
        instruments = [s["instrument"] for s in recs[0]["legal_hypothesis"]["local_statutes"]]
        self.assertTrue(
            any("Justices of the Peace" in i for i in instruments),
            "perjury statute (Justices of the Peace Act s 9) not cited",
        )


# ---------------------------------------------------------------------------
# 4. Statute map resolution
# ---------------------------------------------------------------------------

class TestStatuteMap(unittest.TestCase):
    def test_za_three_types_have_citations(self):
        for t in (S.TYPE_SWORN_VS_SWORN,
                  S.TYPE_DEVICE_ATTRIBUTION_CHAIN,
                  S.TYPE_CRIMINAL_CHARGE_AS_LEVERAGE):
            cites = S.statutes_for_type(t, "ZA")
            self.assertTrue(cites, f"no ZA statutes for {t}")

    def test_device_chain_cites_cybercrimes_act(self):
        cites = S.statutes_for_type(S.TYPE_DEVICE_ATTRIBUTION_CHAIN, "ZA")
        self.assertTrue(any("Cybercrimes Act" in c.instrument for c in cites))

    def test_leverage_cites_cyber_extortion(self):
        cites = S.statutes_for_type(S.TYPE_CRIMINAL_CHARGE_AS_LEVERAGE, "ZA")
        self.assertTrue(any(c.citation == "s 10" for c in cites))

    def test_generic_fallback_for_unmapped_type(self):
        self.assertEqual(S.statutes_for_type("NON_EXISTENT_TYPE", "ZA"), [])

    def test_generic_jurisdiction_resolves(self):
        cites = S.statutes_for_type(S.TYPE_SWORN_VS_SWORN, "GENERIC")
        self.assertTrue(cites)

    def test_jurisdiction_aliases(self):
        self.assertEqual(S.normalise_jurisdiction("south africa"), "ZA")
        self.assertEqual(S.normalise_jurisdiction("rsa"), "ZA")
        self.assertEqual(S.normalise_jurisdiction("nowhere"), "GENERIC")
        self.assertEqual(S.normalise_jurisdiction(None), "ZA")

    def test_enrich_whole_findings_document(self):
        findings = {
            "contradictions": [
                {"type": "SWORN_VS_SWORN", "conflict_description": "x",
                 "legal_hypothesis": {"is_hypothesis": True}},
                {"type": "DEVICE_ATTRIBUTION_CHAIN", "conflict_description": "y",
                 "legal_hypothesis": None},
            ]
        }
        out = S.enrich_legal_hypotheses(findings, "ZA")
        self.assertEqual(out["statute_enrichment"]["records_enriched"], 2)
        for rec in out["contradictions"]:
            self.assertEqual(rec["jurisdiction"], "ZA")
            self.assertTrue(rec["legal_hypothesis"]["local_statutes"])


# ---------------------------------------------------------------------------
# 5. merge_v6_into_findings
# ---------------------------------------------------------------------------

class TestMerge(unittest.TestCase):
    def setUp(self):
        V.reset_counter()

    def test_merge_recounts_tiers(self):
        a = TextChunk(
            text=("In my sworn affidavit, I Liam Highstead solemnly declare that the "
                  "rental payment deposit funds were never paid into the account."),
            source="case126.pdf", page=3, sha512=_sha("a"))
        b = TextChunk(
            text=("In the sworn affidavit the deponent Green Sky solemnly declares that the "
                  "rental payment deposit funds were paid into the account."),
            source="case126.pdf", page=12, sha512=_sha("b"))
        recs = V.run_v6_detectors([a, b], ACTORS, jurisdiction="ZA")
        findings = {"contradictions": []}
        V.merge_v6_into_findings(findings, recs)
        self.assertEqual(len(findings["contradictions"]), len(recs))
        self.assertEqual(findings["engine_verified_count"], len(recs))
        self.assertEqual(findings["g3_candidate_count"], 0)


# ---------------------------------------------------------------------------
# 6. GHRP JUDICIALLY-CONFIRMED tier (H208/25 path)
# ---------------------------------------------------------------------------

class TestJudiciallyConfirmed(unittest.TestCase):
    def _raise(self):
        reg = G.G3CandidateRegistry()
        rec = reg.raise_candidate(
            contradiction_type="SWORN_VS_SWORN",
            proposition_a_text="A", proposition_b_text="B",
            proposition_a_actor="Liam", proposition_b_actor="Green Sky",
            conflict_description="conflict", source_document="case126.pdf",
            source_page=3, sha512_anchor="abc123")
        return reg, rec["contradiction_id"]

    def test_confirm_judicially_sets_tier_and_anchor(self):
        reg, cid = self._raise()
        r = reg.confirm_judicially(
            cid, judgment_ref="H208/25",
            court="Gauteng High Court", case_number="2025-12345")
        self.assertEqual(r["verification_status"], G.GHRP_STATUS_JUDICIALLY_CONFIRMED)
        self.assertEqual(r["judicial_confirmation"]["judgment_ref"], "H208/25")

    def test_confirm_requires_judgment_ref(self):
        reg, cid = self._raise()
        with self.assertRaises(ValueError):
            reg.confirm_judicially(cid, judgment_ref="  ")

    def test_confirm_unknown_id_raises(self):
        reg, _ = self._raise()
        with self.assertRaises(ValueError):
            reg.confirm_judicially("NOPE", judgment_ref="H208/25")

    def test_judicial_recount_in_merge(self):
        reg, cid = self._raise()
        reg.confirm_judicially(cid, judgment_ref="H208/25")
        findings = {"contradictions": []}
        reg.merge_into(findings)
        self.assertEqual(findings["judicially_confirmed_count"], 1)
        self.assertEqual(findings["g3_candidate_count"], 0)

    def test_judicial_no_longer_pending(self):
        reg, cid = self._raise()
        reg.confirm_judicially(cid, judgment_ref="H208/25")
        self.assertEqual(reg.pending(), [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
