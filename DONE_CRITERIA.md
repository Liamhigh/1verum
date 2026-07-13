# Done Criteria — Verum Omnis

**Document Purpose:** Defines what "finished" means in measurable, objective terms. The coding assistant stops work only when every criterion in this document is satisfied.

**Last Updated:** 2026-07-13  
**Version:** v5.2.8  
**Status:** BINDING — Hard stop criteria

---

## The Definition of Done

The Verum Omnis project is **COMPLETE** when and only when ALL of the following criteria are met. No exceptions. No "almost done." Every criterion must be objectively verifiable.

---

## Criterion 1: Build Success

**Status:** NOT MET

The project MUST build cleanly with zero errors:

```bash
./gradlew clean build
```

- ✅ Zero compilation errors
- ✅ Zero Detekt violations
- ✅ Zero KtLint formatting violations
- ✅ R8/ProGuard release build succeeds
- ✅ `./gradlew assembleRelease` produces a signed APK

**Verification:** Run the command. If it prints `BUILD SUCCESSFUL`, this criterion is met.

---

## Criterion 2: All P0 Tasks Complete

**Status:** NOT MET

Every P0 (Critical Path) task in `MASTER_TASK_LIST.md` MUST be complete:

- [ ] B1 — Contradiction Brain fully integrated and callable
- [ ] B2 — Document Brain implemented (OCR, PDF parsing, metadata)
- [ ] B3 — Communication Brain implemented (chat parsing, deletion detection)
- [ ] B4 — Behavioral Brain implemented (LIWC++, evasion detection)
- [ ] B5 — Timeline Brain implemented (event sequencing, gap detection)
- [ ] B6 — Financial Brain implemented (transaction analysis, rent tracing)
- [ ] B7 — Legal Brain implemented (statute mapping, OJRS framework)
- [ ] B8 — Audio/Media Brain implemented (Whisper.cpp, video hashing)
- [ ] B9 — Guardian Brain implemented (constitutional enforcement)
- [ ] Nine-Brain Orchestrator with voting and quorum
- [ ] ForensicService.scan() complete end-to-end pipeline
- [ ] Triple Verification (Thesis/Antithesis/Synthesis)
- [ ] llama.cpp JNI bridge loading and running models
- [ ] G3 (Gemma 3) report writer generating reports
- [ ] PHR3 (PHI-3/Command R) chat on entry/mid devices
- [ ] G4 (Gemma 4) chat on flagship devices
- [ ] Sealed PDF generation with per-page SHA-512 footer
- [ ] Evidence Vault with AES-256-GCM + hardware keystore
- [ ] SHA-512 hashing at ingestion
- [ ] OpenTimestamps blockchain anchoring
- [ ] Device tier detection (<4GB / 4-8GB / 8GB+)
- [ ] Model integrity verification (B9 validates hashes)
- [ ] WorkManager background scan with progress notification

**Verification:** Check every box above. All must be `[x]`.

---

## Criterion 3: All P1 Tasks Complete

**Status:** NOT MET

Every P1 task in `MASTER_TASK_LIST.md` MUST be complete:

- [ ] Blockchain confirmation tracking (pending → confirmed)
- [ ] Seal verification (re-compute hash, detect tampering)
- [ ] Chat UI wired to PHR3/G4 with anchored responses
- [ ] Chat hypothesis flagging ([HYPOTHESIS] for legal interpretations)
- [ ] Ordinal confidence in chat (no percentages)
- [ ] Foreground service for background scans
- [ ] Tesseract OCR with bundled fonts
- [ ] MuPDF deterministic PDF parsing
- [ ] PDF metadata analysis (backdating, font anomalies)
- [ ] EXIF metadata extraction and analysis

**Verification:** Check every box above. All must be `[x]`.

---

## Criterion 4: All Tests Pass

**Status:** NOT MET

Every test in `TEST_PLAN.md` MUST pass:

| Category | Tests | Status |
|----------|-------|--------|
| T1 — Core Engine Unit | 23 | ALL PASS |
| T2 — B1 Contradiction | 16 | ALL PASS |
| T3 — Integration | 12 | ALL PASS |
| T4 — Determinism | 6 | ALL PASS |
| T5 — Constitutional | 15 | ALL PASS |
| T6 — UI | 22 | ALL PASS |
| T7 — Instrumented | 12 | ALL PASS |
| T8 — Performance | 8 | ALL PASS |
| **TOTAL** | **114** | **114 PASS** |

**Verification:**
```bash
./gradlew clean test
./gradlew connectedDebugAndroidTest
# Both must report 100% pass rate
```

---

## Criterion 5: Code Coverage

**Status:** NOT MET

Code coverage MUST meet or exceed these minimums:

| Package | Minimum | Actual | Met? |
|---------|---------|--------|------|
| `com.verumomnis.forensic.engine` | 75% | TBD | ❌ |
| `com.verumomnis.forensic.crypto` | 70% | TBD | ❌ |
| `com.verumomnis.forensic.model` | 60% | TBD | ❌ |
| `com.verumomnis.forensic.core` | 80% | TBD | ❌ |
| `com.verumomnis.forensic.vault` | 70% | TBD | ❌ |
| `com.verumomnis.forensic.ui` | 50% | TBD | ❌ |
| **Overall** | **65%** | **TBD** | **❌** |

**Verification:**
```bash
./gradlew jacocoTestReport
# Check coverage report in build/reports/jacoco/
```

---

## Criterion 6: No Placeholders or TODOs

**Status:** NOT MET

The codebase MUST contain zero placeholders:

```bash
# This command MUST return zero results
grep -r "TODO\|FIXME\|HACK\|NotImplementedException\|Coming soon\|placeholder" \
  --include="*.kt" --include="*.java" --include="*.xml" src/ 2>/dev/null | wc -l
# Expected output: 0
```

- No `TODO()` calls
- No `// TODO:` comments
- No `throw NotImplementedException()`
- No empty function bodies where implementation belongs
- No "Coming soon" UI text
- No disabled buttons that should be enabled

**Verification:** Run the grep command. Output must be `0`.

---

## Criterion 7: Constitutional Compliance

**Status:** NOT MET

All code MUST comply with the Verum Omnis Constitution:

- [ ] No percentage-based confidence (ordinal only)
- [ ] No `Date.now()` or `System.currentTimeMillis()` in hash inputs
- [ ] No `Math.random()` or non-deterministic functions in core logic
- [ ] No raw evidence routed to AI chat
- [ ] Article X weaponization keywords trigger hard stop
- [ ] B9 cannot vote (enforced at code level)
- [ ] 7 constitutional categories (no more, no less)
- [ ] Free for citizens and law enforcement (no paywalls)
- [ ] Triple verification on every finding
- [ ] Chain of custody (SHA-512, source, timestamp on every artifact)

**Verification:** ConstitutionalValidator static analysis passes. B9 runtime validation passes.

---

## Criterion 8: BUILD_STATUS.md Shows Acceptable Completion

**Status:** NOT MET

The Feature Completion Matrix in `BUILD_STATUS.md` MUST show:

| Category | Minimum Complete | Status |
|----------|-----------------|--------|
| Core Engine | 80% (14/17) | ❌ |
| LLM System | 60% (7/11) | ❌ |
| Report Generation | 80% (10/12) | ❌ |
| Cryptographic | 80% (7/8) | ❌ |
| UI Screens | 90% (14/15) | ❌ |
| Integration | 90% (14/15) | ❌ |
| **Overall** | **80%** | **❌** |

**Verification:** Count COMPLETE (✅) items in BUILD_STATUS.md. Each category must meet its minimum.

---

## Criterion 9: App Runs Without Crashes

**Status:** NOT MET

The app MUST run for 30 minutes on a real device without crashing:

- [ ] Cold start succeeds (<5s on mid-tier device)
- [ ] Evidence upload works (multiple files)
- [ ] Forensic scan completes (with at least 10 files)
- [ ] Report viewer opens and displays correctly
- [ ] Chat interface responds to questions
- [ ] Evidence vault shows stored artifacts
- [ ] Background scan continues when app is backgrounded
- [ ] App returns to foreground and shows correct state
- [ ] No ANR (Application Not Responding) dialogs
- [ ] No OutOfMemory crashes

**Verification:** Manual testing on physical Android device. All 10 items must pass.

---

## Criterion 10: Documentation Complete

**Status:** NOT MET

All documentation MUST be current and accurate:

- [ ] `WHAT_THIS_APP_IS.md` — reflects current architecture
- [ ] `PROMPT.md` — all rules still apply, no contradictions
- [ ] `BUILD_STATUS.md` — accurately reflects implementation status
- [ ] `MASTER_TASK_LIST.md` — all completed tasks marked `[x]`
- [ ] `FUNCTIONAL_REQUIREMENTS.md` — all screens behave as specified
- [ ] `ARCHITECTURE.md` — matches actual code structure
- [ ] `DEPENDENCIES.md` — all dependencies correct with versions
- [ ] `TEST_PLAN.md` — all tests pass as documented
- [ ] `BUILD_RULES.md` — no violations in codebase
- [ ] `KNOWN_BUGS.md` — all P0/P1 bugs resolved or documented
- [ ] `DONE_CRITERIA.md` — this file: all criteria show MET

**Verification:** Read each document. Confirm it matches the codebase.

---

## Completion Checklist

| Criterion | Weight | Status |
|-----------|--------|--------|
| 1 — Build Success | Required | ❌ |
| 2 — P0 Tasks Complete | Required | ❌ |
| 3 — P1 Tasks Complete | Required | ❌ |
| 4 — All Tests Pass | Required | ❌ |
| 5 — Code Coverage Met | Required | ❌ |
| 6 — No Placeholders | Required | ❌ |
| 7 — Constitutional Compliance | Required | ❌ |
| 8 — BUILD_STATUS.md Targets | Required | ❌ |
| 9 — App Runs Without Crashes | Required | ❌ |
| 10 — Documentation Complete | Required | ❌ |

**Overall Status: 0/10 criteria met**

**The project is NOT complete.** Continue working until all criteria show ✅ MET.

---

## Sign-Off

When all criteria are met, update this section:

```
Signed off by: {assistant name}
Date: {date}
Commit: {git commit hash}
All 10 criteria: MET ✅
```
