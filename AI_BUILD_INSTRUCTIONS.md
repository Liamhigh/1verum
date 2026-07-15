# AI Build Instructions — Verum Omnis

**Document Purpose:** The master instruction set for any AI coding assistant (ChatGPT, Codex, Claude Code, Gemini CLI, Cursor, etc.) working on this codebase. These instructions override any conflicting guidance.

**Last Updated:** 2026-07-13  
**Version:** v5.2.8  
**Authority:** BINDING

---

## Before You Write Any Code

1. **Read `WHAT_THIS_APP_IS.md` first.** Understand what this app is, what it does, and why it exists.
2. **Read `BUILD_STATUS.md` second.** Understand what is complete and what is missing.
3. **Read `MASTER_TASK_LIST.md` third.** Understand what tasks you must complete.
4. **Read `BUILD_RULES.md` fourth.** Understand the rules you cannot break.
5. **Read `KNOWN_BUGS.md` fifth.** Understand what is already broken.
6. Only then begin coding.

---

## Absolute Rules (Never Break These)

### Rule 1: Never Leave TODOs

- Do NOT write `TODO()` in Kotlin code
- Do NOT write `// TODO:` comments
- Do NOT write `// FIXME:` comments
- Do NOT write `throw NotImplementedException()`
- Do NOT leave empty function bodies
- Do NOT write "Coming soon" or "Not yet implemented" in UI text

**If you identify work that needs to be done later, add it to `MASTER_TASK_LIST.md` and `KNOWN_BUGS.md`. Do NOT leave a TODO in the code.**

### Rule 2: Never Leave Placeholder Code

- Every function MUST have a real implementation
- Every button MUST have a working click handler
- Every screen MUST be fully functional
- No stubs, no mocks, no fakes in production code
- Test code can use mocks — production code cannot

### Rule 3: Fix Compile Errors Before Adding Features

```bash
# Run this BEFORE starting any feature work
./gradlew clean build
# MUST pass with zero errors
```

If there are compile errors, fix them first. Do not add new code on top of broken code.

### Rule 4: Ensure Every Button Is Connected

For every button you create or modify:
1. It MUST have a click handler
2. The handler MUST do what the button label says
3. It MUST not show "Coming soon" or any placeholder message

### Rule 5: Ensure Every Screen Is Reachable

Every screen in the app MUST be accessible through normal navigation. No hidden screens. No orphaned screens. No screens only accessible through secret gestures.

### Rule 6: Ensure Every Database Query Works

- Every Room DAO query MUST be correct
- Every query MUST be tested (unit test)
- No queries that crash on empty tables
- No queries that return incorrect results
- All DB operations on background threads (coroutines)

### Rule 7: Ensure Every AI Module Is Callable

- Every brain (B1-B9) MUST be callable through the orchestrator
- Every LLM model (G3, PHR3, G4) MUST load and respond
- Model loading failures MUST be handled gracefully (no crashes)
- Every model MUST produce deterministic output (temperature=0, fixed seed)

### Rule 8: Ensure Every Report Is Generated

- The report generation pipeline MUST work end-to-end
- Reports MUST have the seal footer on every page
- Reports MUST be deterministic (same input = identical PDF)
- Reports MUST be viewable and shareable without corruption

### Rule 9: Ensure Evidence Sealing and Verification Work

- SHA-512 hash computed at ingestion
- Report hash anchored to blockchain
- Seal verification detects tampering (modified report = FAILED verification)
- Unmodified report passes verification

### Rule 10: Ensure the App Builds Without Errors

```bash
./gradlew clean build
# This MUST pass before you consider any work complete
```

### Rule 11: Run All Available Tests

```bash
./gradlew test                    # Unit tests
./gradlew connectedAndroidTest    # Instrumented tests (if device connected)
```

All tests MUST pass. If a test fails, fix it or document why it cannot be fixed in `KNOWN_BUGS.md`.

### Rule 12: Continue Until There Are No Unresolved Build Errors

Do not stop when:
- "The main features work"
- "I'll fix the tests later"
- "This is good enough for now"

Stop ONLY when:
- `./gradlew clean build` passes
- All tests pass
- All P0 tasks in `MASTER_TASK_LIST.md` are complete
- `BUILD_STATUS.md` shows all critical features as COMPLETE
- No TODOs, placeholders, or stubs remain in code

---

## Coding Standards

### Kotlin Style

- Follow the official Kotlin Coding Conventions
- Use Ktlint for formatting (`./gradlew ktlintCheck`)
- Maximum line length: 120 characters
- Use meaningful variable names (not `a`, `b`, `x`)
- Use `val` by default, `var` only when necessary
- Prefer immutability (data classes, copy())
- Use coroutines for async operations (no callbacks)
- Use Flow for reactive streams
- Use `Result<T>` for operations that can fail

### Architecture

- **MVVM + Repository**: ViewModel → Repository → Data Source
- **Hilt DI**: Constructor injection for all dependencies
- **Single source of truth**: Room database is the source of truth
- **Unidirectional data flow**: UI → ViewModel → Repository → DB → UI
- **Offline-first**: App works without network. Network is enhancement only.

### Documentation

- Every public function MUST have KDoc
- Every complex algorithm MUST have inline comments
- Use `// CONSTITUTION:` for constitutional constraint references
- Use `// NOTE:` for architectural explanations
- Use `// PERFORMANCE:` for optimization notes
- Update `BUILD_STATUS.md` when you complete a feature
- Update `MASTER_TASK_LIST.md` when you complete a task
- Update `KNOWN_BUGS.md` when you fix a bug

### Testing

- Write tests BEFORE or WITH production code (TDD preferred)
- Minimum 75% coverage for `engine` package
- Every bug fix MUST include a regression test
- Use MockK for mocking in unit tests
- Use Espresso for UI tests
- Use Turbine for Flow testing

---

## Constitutional Constraints

These are hard-coded rules. No code you write can violate them.

1. **Confidence is ordinal only**: VERY_HIGH / HIGH / MODERATE / LOW / INSUFFICIENT. Never percentages. Never 0-1 scores.
2. **Evidence before narrative**: Every finding MUST cite anchors (person, page, line). No anchor = no finding.
3. **Determinism**: Same input + same constitution version = identical output. No randomness. No time-dependent behavior in hash inputs.
4. **Raw evidence never touches AI**: Evidence → ForensicEngine → Sealed ScanResult → AI. Never bypass the forensic engine.
5. **Free for citizens**: No paywalls. No license checks for non-institutional users.
6. **Article X**: No weaponization. Keywords: "kill chain", "target acquisition", "artillery" trigger hard stop.
7. **B9 cannot vote**: B9 validates but never votes on findings.
8. **7 categories**: Goodwill Value, Contract Validity, Signature Status, Section 12B, Compensation, Perjury, Coercion. No more, no less.
9. **Triple verification**: Every finding passes Thesis/Antithesis/Synthesis.
10. **10-word prompt limit**: No AI system prompt exceeds 10 words.

---

## Priority Order

1. **P0 — Critical Path**: Nine-Brain Engine, ForensicService, llama.cpp, Sealed PDF
2. **P1 — Core**: Blockchain anchoring, Vault hardware keystore, Chat wiring, Background scan
3. **P2 — Feature**: 7-category report, B7 legal mapping, B8 audio/video, QR codes
4. **P3 — Advanced**: OJRS, VITS, Deepfake detection, Timeline visualization
5. **P4 — Polish**: Biometric auth, Camera, Accessibility, Performance

**Do NOT work on P2 until all P0 and P1 tasks are complete.**

---

## File Update Checklist

After every significant change, update these files:

- [ ] `BUILD_STATUS.md` — Update feature status (❌ → ⚠️ → ✅)
- [ ] `MASTER_TASK_LIST.md` — Mark completed tasks `[x]`
- [ ] `KNOWN_BUGS.md` — Add new bugs found, mark fixed bugs as FIXED
- [ ] `DONE_CRITERIA.md` — Update criterion status if applicable

---

## Communication Protocol

When you (the AI assistant) are unsure about a decision:

1. Check the Constitution (`CONSTITUTION.md`)
2. Check the Architecture (`ARCHITECTURE.md`)
3. Check the Functional Requirements (`FUNCTIONAL_REQUIREMENTS.md`)
4. If still unsure, make the most conservative choice (safety over features)
5. Document the decision in your commit message

Never guess. Never assume. When in doubt, enforce the Constitution.

---

## Final Instruction

> **This is a forensic tool that produces court-admissible evidence. People's freedom, property, and justice depend on this code working correctly. There is no room for shortcuts, no tolerance for "good enough," and no excuse for shipping broken code. Every line you write either upholds or undermines the rule of law. Write accordingly.**
