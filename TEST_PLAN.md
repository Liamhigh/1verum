# Test Plan — Verum Omnis

**Document Purpose:** Every test that must pass before the project is considered complete. Organized by category with pass/fail criteria.

**Last Updated:** 2026-07-13  
**Version:** v5.2.8  
**Coverage Target:** 75% minimum (engine package), 50% (UI package)

---

## T1 — Unit Tests: Core Engine

### T1.1 — Evidence Hashing

| # | Test | Input | Expected | Status |
|---|------|-------|----------|--------|
| 1.1.1 | SHA-512 produces 128-char hex | "hello" | 128-char hex string | PENDING |
| 1.1.2 | Same input = same hash | "test" (run twice) | Identical hashes | PENDING |
| 1.1.3 | Single character change = different hash | "test" vs "tesu" | Completely different hash | PENDING |
| 1.1.4 | Empty string hash | "" | Valid hash (not empty, not null) | PENDING |
| 1.1.5 | Large file hash (500MB) | 500MB random file | Completes <5s, valid hash | PENDING |
| 1.1.6 | Binary file hash | Random bytes | Valid hash | PENDING |

### T1.2 — Evidence Artifact

| # | Test | Input | Expected | Status |
|---|------|-------|----------|--------|
| 1.2.1 | ID derived from content hash | File: "MOU.pdf" | ID = first 16 bytes of SHA-512(content) | PENDING |
| 1.2.2 | Filename not in ID | Two files with same content, different names | Same ID | PENDING |
| 1.2.3 | Timestamp not in ID | Same file ingested at different times | Same ID | PENDING |
| 1.2.4 | Size not in ID | Same content, different padding | Same ID | PENDING |
| 1.2.5 | Canonical JSON serialization | EvidenceArtifact with unsorted fields | Deterministic JSON (sorted keys) | PENDING |

### T1.3 — Constitution Enforcement

| # | Test | Input | Expected | Status |
|---|------|-------|----------|--------|
| 1.3.1 | Confidence ordinal enforcement | Set confidence to "80%" | Rejected — must be VERY_HIGH/HIGH/MODERATE/LOW/INSUFFICIENT | PENDING |
| 1.3.2 | Date.now() detection | Code with `Date.now()` in hash input | ConstitutionalValidator flags ERROR | PENDING |
| 1.3.3 | Math.random() detection | Code with `Math.random()` | ConstitutionalValidator flags ERROR | PENDING |
| 1.3.4 | Article X keyword detection | Input: "target acquisition" | B9 flags WEAPONIZATION_ATTEMPT | PENDING |
| 1.3.5 | Article X legitimate use | Input: "war crimes documentation" | Allowed — in permitted list | PENDING |
| 1.3.6 | B9 non-voting | B9 tries to vote on finding | Rejected — B9 has no vote | PENDING |

### T1.4 — Nine-Brain Voting

| # | Test | Input | Expected | Status |
|---|------|-------|----------|--------|
| 1.4.1 | 3-brain quorum (B1 + B2 + B3) | Finding with 3 confirmations | Status = ACCEPTED | PENDING |
| 1.4.2 | 2-brain quorum (B1 + B2) | Finding with 2 confirmations | Status = ACCEPTED | PENDING |
| 1.4.3 | 1-brain (B1 only) | Finding with 1 confirmation | Status = INDETERMINATE_DUE_TO_CONCEALMENT | PENDING |
| 1.4.4 | 0-brain (none) | Finding with 0 confirmations | Status = INSUFFICIENT | PENDING |
| 1.4.5 | B9 cannot vote | Finding where only B9 confirms | Does not count toward quorum | PENDING |
| 1.4.6 | B9 veto power | Finding that violates Constitution | B9 vetoes regardless of quorum | PENDING |

### T1.5 — Triple Verification

| # | Test | Input | Expected | Status |
|---|------|-------|----------|--------|
| 1.5.1 | All three pass | Thesis=PASS, Antithesis=PASS, Synthesis=PASS | Overall = PASS | PENDING |
| 1.5.2 | Thesis fails | Thesis=FAIL | Overall = FAIL | PENDING |
| 1.5.3 | Antithesis fails | Antithesis=FAIL | Overall = FAIL | PENDING |
| 1.5.4 | Synthesis fails | Synthesis=FAIL | Overall = FAIL | PENDING |
| 1.5.5 | Deterministic output | Same evidence, run 3 times | Identical verification results | PENDING |

---

## T2 — Unit Tests: B1 Contradiction Engine

### T2.1 — Core Engine Tests

| # | Test | Input | Expected | Status |
|---|------|-------|----------|--------|
| 2.1.1 | Empty evidence | Empty message list | No contradictions, INSUFFICIENT | PENDING |
| 2.1.2 | Single message | 1 message | No contradictions (needs 2+ claims) | PENDING |
| 2.1.3 | Intra-actor conflict | Actor A says "yes" then "no" | INTRA_ACTOR_CONFLICT, VERY_HIGH | PENDING |
| 2.1.4 | Inter-actor conflict | Actor A says "yes", Actor B says "no" | INTER_ACTOR_CONFLICT, VERY_HIGH | PENDING |
| 2.1.5 | Timeline conflict | Event timestamp impossible order | TIMELINE_CONFLICT, HIGH | PENDING |
| 2.1.6 | Evidence concealment | Message referencing hidden info | EVIDENCE_CONCEALMENT, MODERATE | PENDING |
| 2.1.7 | Fabrication | Backdated document metadata | FABRICATION, HIGH | PENDING |
| 2.1.8 | Source conflict | Statement contradicts document | SOURCE_CONFLICT, HIGH | PENDING |
| 2.1.9 | Silence/delay | Notice without follow-up action | SILENCE_DELAY, LOW | PENDING |
| 2.1.10 | AllFuels Paradox | MOU Clause 7 + sworn denial | CRITICAL, consciousnessOfGuilt=true | PENDING |

### T2.2 — Severity Scoring

| # | Test | Input | Expected | Status |
|---|------|-------|----------|--------|
| 2.2.1 | Sworn vs sworn (5x vs 5x) | Two sworn statements contradict | CRITICAL (8-10) | PENDING |
| 2.2.2 | Sworn vs email (5x vs 3x) | Sworn statement vs internal email | HIGH (6-7) | PENDING |
| 2.2.3 | Chat vs chat (2x vs 2x) | Two WhatsApp messages contradict | MODERATE (4-5) | PENDING |
| 2.2.4 | >730 day gap | Signed doc 2018, sworn denial 2021 | CRITICAL, consciousnessOfGuilt=true | PENDING |
| 2.2.5 | 365-730 day gap | Gap = 500 days | HIGH, consciousness candidate | PENDING |
| 2.2.6 | <365 day gap | Gap = 100 days | MODERATE, possible negligence | PENDING |

---

## T3 — Integration Tests

### T3.1 — End-to-End Scan Flow

| # | Test | Input | Expected | Status |
|---|------|-------|----------|--------|
| 3.1.1 | Full scan — single PDF | 1 PDF, 5 pages | Scan completes <30s, report generated | PENDING |
| 3.1.2 | Full scan — multiple files | 10 PDFs, 3 images, 2 audio files | Scan completes <5min, all brains activated | PENDING |
| 3.1.3 | Full scan — large case | 100 files, 500MB total | Scan completes <30min, progress tracked | PENDING |
| 3.1.4 | Scan cancellation | Cancel at 50% | Partial results preserved, SCAN_CANCELLED status | PENDING |
| 3.1.5 | Scan with corrupted file | 5 valid files + 1 corrupted | Skip corrupted, complete with 5 files | PENDING |
| 3.1.6 | Determinism test | Same 10 files, scan 3 times | Identical ScanResult hash all 3 times | PENDING |

### T3.2 — Evidence Flow

| # | Test | Input | Expected | Status |
|---|------|-------|----------|--------|
| 3.2.1 | Upload → Vault → Hash | Select PDF | File in vault, hash computed, manifest updated | PENDING |
| 3.2.2 | Evidence persistence | Close app, reopen | Evidence still in vault, hash unchanged | PENDING |
| 3.2.3 | Evidence encryption | Upload file | File encrypted with AES-256-GCM | PENDING |
| 3.2.4 | Evidence decryption | Access encrypted file | File decrypted, content matches original | PENDING |
| 3.2.5 | Evidence deletion | Delete from vault | File removed, manifest updated | PENDING |

### T3.3 — Report Generation

| # | Test | Input | Expected | Status |
|---|------|-------|----------|--------|
| 3.3.1 | Report with no contradictions | Clean evidence | Report generated, "No contradictions found" | PENDING |
| 3.3.2 | Report with contradictions | Evidence with 5 contradictions | All 5 in report, correctly categorized | PENDING |
| 3.3.3 | Report seal integrity | Generated report | Hash footer on every page matches content | PENDING |
| 3.3.4 | Report QR code | Generated report | QR code scans to correct blockchain hash | PENDING |
| 3.3.5 | Deterministic report | Same evidence, generate 3 times | Identical PDFs (bit-for-bit) | PENDING |

---

## T4 — Determinism Tests

| # | Test | Input | Expected | Status |
|---|------|-------|----------|--------|
| 4.1 | Same evidence = same findings | AllFuels evidence, 10 runs | Identical contradiction list all 10 runs | PENDING |
| 4.2 | Same evidence = same seal | AllFuels evidence, 10 runs | Identical SHA-512 seal all 10 runs | PENDING |
| 4.3 | Same evidence = same report | AllFuels evidence, 10 runs | Identical PDF (bit-for-bit) all 10 runs | PENDING |
| 4.4 | Time independence | Run at 9AM vs 9PM | Identical results | PENDING |
| 4.5 | Device independence | Same evidence, different phones | Identical results (same model tier) | PENDING |
| 4.6 | Model temperature = 0 | Chat with same question | Identical response | PENDING |

---

## T5 — Constitutional Tests

| # | Test | Input | Expected | Status |
|---|------|-------|----------|--------|
| 5.1 | No percentages in output | Request percentage confidence | Refused — ordinal only | PENDING |
| 5.2 | Evidence before narrative | Finding without anchor | Rejected — cannot exist | PENDING |
| 5.3 | Mandatory contradiction disclosure | Find contradiction, try to hide | Cannot hide — must include | PENDING |
| 5.4 | No Date.now() in core | Code review | No Date.now() in hash inputs | PENDING |
| 5.5 | Chain of custody | Every artifact | Has SHA-512, source, timestamp | PENDING |
| 5.6 | Failure disclosure | Deliberately break parser | Output states what failed, where, why | PENDING |
| 5.7 | B9 non-voting | Attempt to make B9 vote | B9 cannot vote — enforced | PENDING |
| 5.8 | Free for citizens | App behavior for non-institutional user | No paywall, no license check | PENDING |
| 5.9 | Article X enforcement | Submit weaponization keywords | Hard stop triggered, violation logged | PENDING |
| 5.10 | 7 categories enforced | Try to add 8th category | Rejected — 7 categories are constitutional | PENDING |
| 5.11 | Pattern detection | Same contradiction across 3 victims | Racketeering flag raised | PENDING |
| 5.12 | 10-word prompt limit | System prompt with 11 words | ConstitutionalValidator flags WARNING | PENDING |
| 5.13 | Ordinal confidence only | Set confidence to "85%" | Rejected — must be ordinal | PENDING |
| 5.14 | Template immutability | Modify sealed template | Cannot modify — immutable | PENDING |
| 5.15 | Anti-coercion | Detect coercion in evidence | COERCION_DETECTED flag raised | PENDING |

---

## T6 — UI Tests

### T6.1 — Screen Navigation

| # | Test | Action | Expected | Status |
|---|------|--------|----------|--------|
| 6.1.1 | Upload screen reachable | Tap "Upload Evidence" | Upload screen displayed | PENDING |
| 6.1.2 | Scan screen reachable | Upload files → tap "Scan" | Scan progress screen displayed | PENDING |
| 6.1.3 | Report viewer reachable | Scan completes → tap "View Report" | Report viewer displayed | PENDING |
| 6.1.4 | Chat screen reachable | Report viewer → tap "Chat" | Chat screen displayed | PENDING |
| 6.1.5 | Vault screen reachable | Navigate to vault | Vault browser displayed | PENDING |
| 6.1.6 | Settings reachable | Navigate to settings | Settings screen displayed | PENDING |
| 6.1.7 | Back navigation | Press back from any screen | Returns to previous screen | PENDING |
| 6.1.8 | All screens reachable | Systematic navigation | Every screen accessible | PENDING |

### T6.2 — Upload Screen

| # | Test | Action | Expected | Status |
|---|------|--------|----------|--------|
| 6.2.1 | Multi-file selection | Select 10 files | All 10 shown in upload list | PENDING |
| 6.2.2 | Unsupported file rejection | Select .exe file | Rejected with error message | PENDING |
| 6.2.3 | Large file handling | Select 600MB file | Warning shown, proceed at user risk | PENDING |
| 6.2.4 | File removal | Remove file from list | File removed, others remain | PENDING |
| 6.2.5 | Upload button state | No files selected | "Begin Scan" button disabled | PENDING |

### T6.3 — Scan Progress Screen

| # | Test | Action | Expected | Status |
|---|------|--------|----------|--------|
| 6.3.1 | Progress updates | During scan | Progress bar updates, brain name shown | PENDING |
| 6.3.2 | Contradiction counter | During scan | Live counter increments as found | PENDING |
| 6.3.3 | Background operation | Background app during scan | Notification shows progress | PENDING |
| 6.3.4 | Cancel scan | Tap "Cancel" | Scan stops, partial results saved | PENDING |
| 6.3.5 | Completion screen | Scan finishes | Shows summary, "View Report" button | PENDING |

### T6.4 — Chat Screen

| # | Test | Action | Expected | Status |
|---|------|--------|----------|--------|
| 6.4.1 | Send message | Type question → send | Response appears with anchor citations | PENDING |
| 6.4.2 | Hypothesis flagging | Ask legal interpretation | Response flagged [HYPOTHESIS] | PENDING |
| 6.4.3 | Evidence restriction | Ask about unscanned file | "I can only discuss scanned evidence" | PENDING |
| 6.4.4 | Deterministic response | Ask same question twice | Identical response both times | PENDING |
| 6.4.5 | Voice input | Tap microphone, speak | Text appears in input, can send | PENDING |
| 6.4.6 | Scroll history | Scroll through chat | All messages visible, no crashes | PENDING |

### T6.5 — Accessibility

| # | Test | Action | Expected | Status |
|---|------|--------|----------|--------|
| 6.5.1 | TalkBack — upload screen | Enable TalkBack, navigate | All elements announced correctly | PENDING |
| 6.5.2 | TalkBack — scan progress | During scan | Progress announced periodically | PENDING |
| 6.5.3 | Touch target size | Measure all buttons | Minimum 48dp x 48dp | PENDING |
| 6.5.4 | Color contrast | Check all text | Minimum 4.5:1 ratio | PENDING |
| 6.5.5 | Font scaling | Set font to largest | All text readable, no clipping | PENDING |

---

## T7 — Instrumented Tests (On-Device)

| # | Test | Device | Expected | Status |
|---|------|--------|----------|--------|
| 7.1 | Model loading — flagship | 8GB+ RAM | Gemma 4 + Gemma 3 load <6s | PENDING |
| 7.2 | Model loading — mid | 4-8GB RAM | PHR3 + G3 load <8s | PENDING |
| 7.3 | Model loading — entry | <4GB RAM | PHR3(Q3) + G3(Q3) load <12s | PENDING |
| 7.4 | Hardware keystore | Device with StrongBox | Key stored in StrongBox | PENDING |
| 7.5 | Software keystore fallback | Device without StrongBox | Key stored in TEE | PENDING |
| 7.6 | Encryption performance | Encrypt 100MB file | Completes <10s | PENDING |
| 7.7 | LLM inference — report | Gemma 3, 528-page evidence | Report generated <60s | PENDING |
| 7.8 | LLM inference — chat | PHR3, simple question | Response <5s | PENDING |
| 7.9 | LLM inference — chat flagship | G4, complex question | Response <10s | PENDING |
| 7.10 | Background scan | Start scan → background | Scan continues, notification shows | PENDING |
| 7.11 | Scan after app restart | Start scan → kill app → reopen | Scan resumes from checkpoint | PENDING |
| 7.12 | Camera capture | Capture photo in-app | Photo hashed, stored in vault | PENDING |

---

## T8 — Performance Tests

| # | Test | Input | Expected | Status |
|---|------|-------|----------|--------|
| 8.1 | APK size | Build release APK | <60MB (without models) | PENDING |
| 8.2 | Cold start time | Launch from killed state | <3s on flagship, <5s on mid | PENDING |
| 8.3 | Memory usage — scan | During active scan | <80% of available RAM | PENDING |
| 8.4 | Memory usage — idle | App open, no scan | <200MB | PENDING |
| 8.5 | Battery — background scan | 1-hour scan | <5% battery per hour | PENDING |
| 8.6 | PDF generation | 100-page report | <30s | PENDING |
| 8.7 | Hash computation | 1GB file | <10s | PENDING |
| 8.8 | OCR — single page | 1 page PDF | <5s | PENDING |

---

## Test Summary

| Category | Tests | Target | Status |
|----------|-------|--------|--------|
| T1 — Core Engine Unit | 23 | 100% pass | NOT STARTED |
| T2 — B1 Contradiction | 16 | 100% pass | NOT STARTED |
| T3 — Integration | 12 | 100% pass | NOT STARTED |
| T4 — Determinism | 6 | 100% pass | NOT STARTED |
| T5 — Constitutional | 15 | 100% pass | NOT STARTED |
| T6 — UI | 22 | 100% pass | NOT STARTED |
| T7 — Instrumented | 12 | 100% pass | NOT STARTED |
| T8 — Performance | 8 | All meet targets | NOT STARTED |
| **TOTAL** | **114** | **100% pass** | **0 PASSED** |

---

## Running the Tests

```bash
# Unit tests
./gradlew test

# Integration tests
./gradlew testDebugUnitTest

# UI tests
./gradlew connectedDebugAndroidTest

# All tests with coverage
./gradlew jacocoTestReport

# Specific test class
./gradlew test --tests "com.verumomnis.forensic.engine.ContradictionEngineTest"

# Determinism test (run 10 times)
for i in {1..10}; do ./gradlew test --tests "com.verumomnis.forensic.DeterminismTest"; done
```

## Coverage Requirements

| Package | Minimum Coverage |
|---------|-----------------|
| `com.verumomnis.forensic.engine` | 75% |
| `com.verumomnis.forensic.crypto` | 70% |
| `com.verumomnis.forensic.model` | 60% |
| `com.verumomnis.forensic.core` | 80% |
| `com.verumomnis.forensic.vault` | 70% |
| `com.verumomnis.forensic.ui` | 50% |
| **Overall** | **65%** |
