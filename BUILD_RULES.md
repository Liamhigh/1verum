# Build Rules — Verum Omnis

**Document Purpose:** Non-negotiable rules for any coding assistant working on this codebase. Breaking these rules is grounds for immediate rejection of the work.

**Last Updated:** 2026-07-13  
**Version:** v5.2.8  
**Status:** BINDING — Hard-coded into the project's CI gates

---

## The Golden Rule

> **"If it ships, it works. If it doesn't work, it doesn't ship."**

No exceptions. No excuses. No "I'll fix it later." Every line of code in this repository must be production-ready.

---

## Rule 1: No Placeholders

### What This Means

- No `// TODO: implement this later`
- No `throw new NotImplementedException()`
- No empty function bodies: `fun doSomething() { }`
- No `return null` where a real implementation belongs
- No `// FIXME` comments
- No `// HACK` or `// WORKAROUND` without a constitutional justification
- No `Coming soon` UI text
- No disabled buttons that should be enabled
- No grayed-out menu items that are planned for future versions

### What Is Allowed

- Platform-specific stubs (Android-only code with iOS stub in shared module) — ONLY if the stub throws a clear error: `throw UnsupportedOperationException("iOS not yet supported")`
- Experimental features behind a feature flag that is OFF by default — ONLY if the flag is documented in FUNCTIONAL_REQUIREMENTS.md
- Code that is intentionally incomplete because the prerequisite feature is also incomplete — ONLY if tracked in KNOWN_BUGS.md with a blocker note

### Enforcement

```kotlin
// CI gate: Build fails if any of these patterns are found
grep -r "TODO\|FIXME\|HACK\|NotImplementedException\|Coming soon" \
  --include="*.kt" --include="*.java" src/ && exit 1
```

---

## Rule 2: No TODOs in Code

### What This Means

- No `TODO()` calls in Kotlin
- No `// TODO:` comments
- No `XXX` markers
- No `HACK:` comments
- No `TEMP:` or `TMP:` prefixes

### What Is Allowed

- `// NOTE:` for architectural explanations
- `// CONSTITUTION:` for constitutional constraint references
- `// PERFORMANCE:` for performance optimization notes
- `// SAFETY:` for security consideration notes

### Where Tasks Go Instead

If you identify something that needs to be done later:
1. Add it to MASTER_TASK_LIST.md with the appropriate priority
2. Add it to KNOWN_BUGS.md if it's a bug
3. Do NOT leave a TODO in the code

---

## Rule 3: Fix Compile Errors Before Adding Features

### What This Means

- The codebase MUST compile cleanly before any new feature is added
- `./gradlew build` MUST succeed with zero errors
- All warnings MUST be addressed or explicitly suppressed with justification
- If a merge introduces compile errors, fix them BEFORE continuing

### Build Gate

```bash
# This must pass before any feature work begins
./gradlew clean build

# Zero errors, zero warnings (or documented suppressions)
```

---

## Rule 4: Every Button Must Be Connected

### What This Means

- Every button in the UI MUST have a click handler
- Every button MUST do what its label says it does
- No decorative buttons that don't function
- No buttons that show "Coming soon" toasts
- Navigation buttons MUST navigate to the correct screen
- Action buttons MUST perform the described action

### Verification

For every screen in the app:
1. Tap every button
2. Verify it performs the expected action
3. If it doesn't work, it's a bug — fix it or file it in KNOWN_BUGS.md

---

## Rule 5: Every Screen Must Be Reachable

### What This Means

- Every screen MUST be accessible through the app's navigation
- No orphaned screens that can't be reached
- No screens that are only accessible through hidden gestures
- Deep links MUST work for all primary screens
- Back navigation MUST work correctly from every screen

### Navigation Map

```
Main → Upload → Scan Progress → Report Viewer → Chat
  ↓       ↓           ↓              ↓
Vault   Settings    Cancel       Share Report
  ↓
Case Browser → Case Detail → Evidence Detail
```

Every arrow must work. Every screen must be reachable.

---

## Rule 6: Every Database Query Must Work

### What This Means

- Every Room DAO query MUST be tested
- No queries that return incorrect results
- No queries that crash on edge cases (empty tables, null values)
- Migration scripts MUST work for all schema versions
- Database operations MUST use coroutines (no blocking main thread)

### Testing Requirement

- Unit test for every DAO method
- Integration test for every complex query
- Migration test for every schema version change

---

## Rule 7: Every AI Module Must Be Callable

### What This Means

- Every brain (B1-B9) MUST be callable through the orchestrator
- Every LLM model (G3, PHR3, G4) MUST load and respond
- Model loading failures MUST be handled gracefully
- Inference MUST not crash the app
- Every model MUST produce deterministic output (temperature=0)

### Verification

```kotlin
// This must not crash for any brain
val result = brain.analyze(artifacts)
assertNotNull(result.findings)
assertNotNull(result.confidence)
assertNotNull(result.anchors)
```

---

## Rule 8: Every Report Must Be Generated

### What This Means

- The report generation pipeline MUST work end-to-end
- A scan with contradictions MUST produce a report containing those contradictions
- A scan with no contradictions MUST produce a report saying "No contradictions found"
- The report MUST have the seal footer on every page
- The report MUST be deterministic (same input = same PDF)
- The report MUST be viewable in the in-app viewer
- The report MUST be shareable without corruption

### Verification Steps

1. Upload evidence with known contradictions
2. Run scan
3. Verify report contains all contradictions
4. Verify seal footer on every page
5. Share report, verify it opens correctly
6. Re-scan same evidence, verify reports are identical

---

## Rule 9: Evidence Sealing and Verification Must Work

### What This Means

- SHA-512 hash MUST be computed at ingestion
- Report hash MUST be anchored to blockchain
- Seal verification MUST detect tampering
- A modified report MUST fail verification
- An unmodified report MUST pass verification
- The QR code MUST encode the correct hash

### Verification Steps

1. Upload evidence → note hash
2. Generate report → note seal hash
3. Verify seal → must pass
4. Modify report byte → verify seal → must fail
5. Restore original → verify seal → must pass

---

## Rule 10: The App Must Build Without Errors

### What This Means

- `./gradlew clean build` MUST succeed
- Zero compilation errors
- Zero lint errors (Detekt, KtLint)
- All tests MUST pass
- R8/ProGuard MUST not break the build
- Release build MUST succeed

### Build Checklist

```bash
# Run before every commit
./gradlew clean build                    # Must pass
./gradlew test                           # All unit tests pass
./gradlew connectedDebugAndroidTest      # All instrumented tests pass
./gradlew detekt                         # Zero Detekt issues
./gradlew ktlintCheck                    # Zero formatting issues
./gradlew assembleRelease                # Release build succeeds
```

---

## Rule 11: Tests Must Pass

### What This Means

- All unit tests MUST pass
- All integration tests MUST pass
- All UI tests MUST pass
- All instrumented tests MUST pass
- New code MUST include tests
- No test shall be disabled without documentation in KNOWN_BUGS.md

### Coverage Requirement

| Package | Minimum Coverage |
|---------|-----------------|
| `engine` | 75% |
| `crypto` | 70% |
| `model` | 60% |
| `core` | 80% |
| `vault` | 70% |
| `ui` | 50% |
| **Overall** | **65%** |

---

## Rule 12: Continue Until Done

### What This Means

- Do not stop when the code "mostly works"
- Do not stop when "the hard parts are done"
- Stop ONLY when:
  - `./gradlew clean build` passes with zero errors
  - All tests pass
  - All P0 tasks in MASTER_TASK_LIST.md are complete
  - All P1 tasks in MASTER_TASK_LIST.md are complete
  - BUILD_STATUS.md shows all critical features as COMPLETE
  - No TODOs, placeholders, or stubs remain in code
  - The app runs without crashing on a real device

### Done Criteria

See DONE_CRITERIA.md for the complete definition of "done."

---

## Rule 13: Constitutional Compliance

### What This Means

- All code MUST comply with the Verum Omnis Constitution
- No code that violates Prime Directives
- No code that could enable weaponization (Article X)
- No code that introduces non-determinism
- No code that uses percentage-based confidence
- No code that routes raw evidence to AI chat

### Verification

B9 (Guardian Brain) validates constitutional compliance at runtime. The ConstitutionalValidator performs static analysis at build time.

---

## Rule 14: Documentation

### What This Means

- Every public function MUST have KDoc
- Every complex algorithm MUST have comments explaining the logic
- Every architectural decision MUST be documented in ARCHITECTURE.md
- Every API change MUST update FUNCTIONAL_REQUIREMENTS.md
- Every bug fix MUST update KNOWN_BUGS.md

### KDoc Template

```kotlin
/**
 * Brief description of what this function does.
 *
 * @param paramName Description of parameter
 * @return Description of return value
 * @throws ExceptionName When and why this exception is thrown
 * @constitutionalReference CONSTITUTION.md Prime Directive #4
 */
```

---

## Rule 15: Security

### What This Means

- No hard-coded API keys (use BuildConfig fields or keystore)
- No logging of sensitive data (evidence content, passwords)
- No network calls for evidence processing (offline-first)
- No storage of evidence in shared/external storage
- No export of evidence without encryption
- All evidence encrypted with AES-256-GCM
- All keys stored in Android Keystore

---

## Summary

| Rule | Summary | CI Gate |
|------|---------|---------|
| 1 | No placeholders | `grep -r "TODO\|FIXME\|HACK\|NotImplemented"` |
| 2 | No TODOs in code | `grep -r "TODO()\|// TODO:"` |
| 3 | Fix compile errors first | `./gradlew build` |
| 4 | Every button connected | Manual verification + UI tests |
| 5 | Every screen reachable | Navigation tests |
| 6 | Every DB query works | DAO unit tests |
| 7 | Every AI module callable | Brain integration tests |
| 8 | Every report generated | End-to-end scan test |
| 9 | Sealing/verification works | Seal integrity tests |
| 10 | App builds without errors | `./gradlew clean build` |
| 11 | All tests pass | `./gradlew test` |
| 12 | Continue until done | MASTER_TASK_LIST.md check |
| 13 | Constitutional compliance | B9 + ConstitutionalValidator |
| 14 | Documentation | KDoc coverage check |
| 15 | Security | Security scan |

**Break any of these rules, and the code does not ship.**
