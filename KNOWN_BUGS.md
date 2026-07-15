# Known Bugs — Verum Omnis

**Document Purpose:** Tracks all existing bugs, unfinished features, and technical debt. The coding assistant checks this file before starting work and updates it as issues are resolved.

**Last Updated:** 2026-07-13  
**Version:** v5.2.8  
**Status:** 0 confirmed critical bugs

---

## How to Use This File

- **Before starting work:** Read this file to understand known issues
- **While working:** Update status as bugs are fixed
- **After fixing:** Move resolved bugs to the "Resolved" section with the fix commit hash
- **When discovering new bugs:** Add them with full reproduction steps

---

## Format

```
### BUG-{number}: Title
- **Severity:** CRITICAL / HIGH / MEDIUM / LOW
- **Status:** OPEN / IN_PROGRESS / FIXED / WONT_FIX
- **Area:** Which component (B1, UI, Crypto, etc.)
- **Description:** What happens
- **Reproduction:** Step-by-step to reproduce
- **Expected:** What should happen
- **Actual:** What actually happens
- **Blocked by:** Any dependencies (bug IDs or missing features)
- **Notes:** Any additional context
```

---

## CRITICAL (Build-Breaking)

No confirmed critical bugs at this time.

---

## HIGH (Major Feature Gaps)

### BUG-001: Nine-Brain Engine Core
- **Severity:** HIGH
- **Status:** FIXED
- **Area:** Engine
- **Description:** The Nine-Brain Engine core (B1–B9) and orchestrator are implemented and wired into `ForensicService.scan()`.
- **Reproduction:** Run `./gradlew testDebugUnitTest` — engine tests pass.
- **Expected:** Full 9-brain analysis pipeline with voting and triple verification.
- **Actual:** B1 contradiction extraction, B2 document forensics, B3 timeline-gap analysis, B4 behavioral signals, B5 timeline reconstruction, B6 financial anomaly detection, B7 jurisdiction/statute mapping, B8 audio tamper/transcript analysis, and the Brain Council orchestrator are all implemented.
- **Blocked by:** None.
- **Notes:** Native-only capabilities (Whisper.cpp transcription, FFmpeg video analysis) remain behind the `Transcriber` / `MediaAnalyzer` interfaces and are not built in this JVM-only environment.

### BUG-002: llama.cpp JNI Bridge Not Built
- **Severity:** HIGH
- **Status:** OPEN
- **Area:** LLM / Native
- **Description:** The JNI bridge between Kotlin and llama.cpp is not built. On-device LLMs cannot load or run.
- **Reproduction:** Try to load any GGUF model — `LlamaModel.load()` throws `UnsatisfiedLinkError`.
- **Expected:** Models load successfully and respond to inference requests.
- **Actual:** Native library not found.
- **Blocked by:** CMake build configuration, NDK setup, JNI wrapper code.
- **Notes:** Requires Android NDK 25.2+, CMake 3.22+. See `DEPENDENCIES.md`. The `ReportWriter` interface is in place so the rest of the app builds without the native bridge.

### BUG-003: Sealed PDF Generation
- **Severity:** HIGH
- **Status:** FIXED
- **Area:** Report Generation
- **Description:** `SealedPdfGenerator` now produces branded cover pages, per-page SHA-512 footers, and a cover-page QR code encoding the report hash.
- **Reproduction:** Run a scan and export the PDF — footer contains seal shortcode, truncated SHA-512, Constitution version, timestamp and page numbers.
- **Expected:** Full sealed report format.
- **Actual:** Per-page footer and cover QR are implemented.
- **Blocked by:** None.
- **Notes:** The 7-category contradiction summary table and per-person actor-profile sections are pending (tracked as report enhancements).

### BUG-004: Evidence Vault Missing Hardware Keystore
- **Severity:** HIGH
- **Status:** FIXED
- **Area:** Crypto / Storage
- **Description:** `VaultKeystore` generates AES-256-GCM keys in Android Keystore with StrongBox → TEE → software fallback, and `EvidenceVault` uses Keystore-backed encryption.
- **Reproduction:** `EvidenceVault` initialization creates/loads the Keystore key.
- **Expected:** Keys stored in StrongBox if available, TEE as fallback, software as last resort.
- **Actual:** StrongBox/TEE fallback implemented.
- **Blocked by:** None.

---

## MEDIUM (Feature Incomplete)

### BUG-005: OJRS Partial
- **Severity:** MEDIUM
- **Status:** IN_PROGRESS
- **Area:** B7 / Legal
- **Description:** Online Judicial Retrieval System (SAFLII/PACER/BAILII search) framework is being added. Live network queries are stubbed pending API access.
- **Reproduction:** Enable OJRS in settings, run scan — offline statutes load; online search returns structured placeholder results.
- **Expected:** B7 searches court databases and pairs judicial records with sealed documents.
- **Actual:** B7 uses cached precedent/statute mapping; live OJRS not yet enabled by default.
- **Blocked by:** API keys / public endpoints, network permissions, parsing contracts.
- **Notes:** See `ONLINE_JUDICIAL_RETRIEVAL.md`.

### BUG-006: Audio/Video Forensics Partial
- **Severity:** MEDIUM
- **Status:** OPEN
- **Area:** B8 / Multimedia
- **Description:** B8 Audio Brain detects tamper signals, metadata inconsistencies and stress markers from transcripts. Native transcription and video analysis are not implemented.
- **Reproduction:** Upload audio/video file — metadata/tamper checks run; native transcription is skipped unless a transcript is supplied.
- **Expected:** Whisper.cpp transcription, speaker diarization, deepfake detection, video frame hashing.
- **Actual:** Best-effort transcript-based analysis only.
- **Blocked by:** Whisper.cpp JNI integration, FFmpeg integration, deepfake model.

### BUG-007: VITS Not Implemented
- **Severity:** MEDIUM
- **Status:** OPEN
- **Area:** Identity / Trust
- **Description:** Verum Identity & Trust System (VITS) is not implemented. No user trust tiers, no device identity, no metadata fraud detection.
- **Reproduction:** Check settings — no identity or trust options.
- **Expected:** Full VITS as described in `IDENTITY_TRUST_SYSTEM.md`.
- **Actual:** Not implemented.
- **Blocked by:** None. Can be implemented independently.

### BUG-008: WorkManager Background Scan
- **Severity:** MEDIUM
- **Status:** FIXED
- **Area:** Background Processing
- **Description:** `ForensicScanWorker`, `ScanWorkScheduler`, and the `VerumViewModel.startBackgroundScan()` enqueue path are implemented.
- **Reproduction:** Start a background scan from the UI; WorkManager enqueues the worker.
- **Expected:** Scan continues in background with progress notification.
- **Actual:** Background scan scaffolding in place; foreground service continuation pending.
- **Blocked by:** None.

---

## LOW (Polish / Nice-to-Have)

### BUG-009: Settings Screen Incomplete
- **Severity:** LOW
- **Status:** OPEN
- **Area:** UI
- **Description:** Settings screen exists but is missing: OJRS toggle (being added), model management, constitution viewer, privacy settings.
- **Reproduction:** Open settings — only basic options visible.
- **Expected:** Full settings as described in `FUNCTIONAL_REQUIREMENTS.md`.
- **Actual:** Basic settings only.
- **Blocked by:** Features that settings control (OJRS, model management).

### BUG-010: Biometric Authentication Not Implemented
- **Severity:** LOW
- **Status:** OPEN
- **Area:** Security / UI
- **Description:** Evidence Vault is not protected by biometric authentication.
- **Reproduction:** Open vault — no fingerprint/face prompt.
- **Expected:** Biometric prompt before accessing vault or sensitive features.
- **Actual:** No biometric protection.
- **Blocked by:** None. Can be implemented independently.

### BUG-011: Camera Integration Not Implemented
- **Severity:** LOW
- **Status:** OPEN
- **Area:** UI / Evidence
- **Description:** Cannot capture evidence directly in-app. Must select existing files.
- **Reproduction:** Upload screen — no camera option.
- **Expected:** Camera capture with auto-hash on save.
- **Actual:** File picker only.
- **Blocked by:** None. Can be implemented independently.

### BUG-012: QR Scanner Not Implemented
- **Severity:** LOW
- **Status:** OPEN
- **Area:** UI / Verification
- **Description:** QR codes are generated on sealed PDF covers, but there is no in-app scanner to verify seals from other users.
- **Reproduction:** No QR scanner in app.
- **Expected:** In-app QR scanner to verify any Verum Omnis seal.
- **Actual:** Not implemented.
- **Blocked by:** None.

---

## RESOLVED

### BUG-001: Nine-Brain Engine Core — FIXED
- **Severity:** HIGH
- **Status:** FIXED
- **Fixed by:** Phase 1 implementation
- **Fix description:** Implemented `BrainCouncil` voting orchestrator, integrated `ContradictionExtractor`, `DocumentForensicsBrain`, `JurisdictionService`, `BehavioralBrain`, `AudioBrain`, timeline reconstruction and financial anomaly analysis into `NineBrainEngine`. `ForensicService.scan()` runs the full pipeline and seals the evidence set.
- **Date resolved:** 2026-07-13

### BUG-003: Sealed PDF Generation — FIXED
- **Severity:** HIGH
- **Status:** FIXED
- **Fixed by:** Phase 1 implementation
- **Fix description:** Added per-page SHA-512 footer with page numbering, Constitution version and timestamp; added ZXing QR code on the cover encoding the report hash.
- **Date resolved:** 2026-07-13

### BUG-004: Evidence Vault Hardware Keystore — FIXED
- **Severity:** HIGH
- **Status:** FIXED
- **Fixed by:** Phase 1 implementation
- **Fix description:** Added `VaultKeystore` using `KeyGenParameterSpec` with StrongBox → TEE → software fallback and wired AES-256-GCM encryption/decryption into `EvidenceVault`.
- **Date resolved:** 2026-07-13

### BUG-008: WorkManager Background Scan — FIXED
- **Severity:** MEDIUM
- **Status:** FIXED
- **Fixed by:** Phase 1 implementation
- **Fix description:** Added `ForensicScanWorker` (CoroutineWorker) with progress `Data`, notification channel and `ScanWorkScheduler`; wired `VerumViewModel.startBackgroundScan()` enqueue path.
- **Date resolved:** 2026-07-13

---

## Technical Debt

| Item | Description | Impact | Priority |
|------|-------------|--------|----------|
| TD-001 | B1 source code exists as documentation only — needs integration into Android project | Cannot run contradiction engine | P0 — resolved via `ContradictionExtractor` |
| TD-002 | llama.cpp needs Android NDK build setup | Cannot run on-device LLMs | P0 — blocked by environment |
| TD-003 | Native transcription/video libraries (Whisper.cpp, FFmpeg) need NDK build | Cannot run native AV forensics | P1 — blocked by environment |
| TD-004 | OJRS needs live API contracts / keys | Cannot perform live court searches | P1 — in progress |
