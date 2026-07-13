# Known Bugs — Verum Omnis

**Document Purpose:** Tracks all existing bugs, unfinished features, and technical debt. The coding assistant checks this file before starting work and updates it as issues are resolved.

**Last Updated:** 2026-07-13  
**Version:** v5.2.8  
**Status:** 0 confirmed bugs (initial project state — all features need to be built)

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

No confirmed critical bugs at this time. All features need to be implemented from scratch.

---

## HIGH (Major Feature Gaps)

### BUG-001: Nine-Brain Engine Not Implemented
- **Severity:** HIGH
- **Status:** OPEN
- **Area:** Engine
- **Description:** The Nine-Brain Engine (B1-B9) and orchestrator are not yet implemented. The forensic scan pipeline cannot run without them.
- **Reproduction:** Try to run `ForensicService.scan()` — it fails because brains are missing.
- **Expected:** Full 9-brain analysis pipeline with voting and triple verification.
- **Actual:** Placeholder implementation only.
- **Blocked by:** Individual brain implementations (B1 partially exists in `B1_SOURCE_CODE.md` but needs integration).
- **Notes:** This is the highest priority item. See `MASTER_TASK_LIST.md` P0 tasks.

### BUG-002: llama.cpp JNI Bridge Not Built
- **Severity:** HIGH
- **Status:** OPEN
- **Area:** LLM / Native
- **Description:** The JNI bridge between Kotlin and llama.cpp is not built. On-device LLMs cannot load or run.
- **Reproduction:** Try to load any GGUF model — `LlamaModel.load()` throws `UnsatisfiedLinkError`.
- **Expected:** Models load successfully and respond to inference requests.
- **Actual:** Native library not found.
- **Blocked by:** CMake build configuration, NDK setup, JNI wrapper code.
- **Notes:** Requires Android NDK 25.2+, CMake 3.22+. See `DEPENDENCIES.md`.

### BUG-003: Sealed PDF Generation Incomplete
- **Severity:** HIGH
- **Status:** OPEN
- **Area:** Report Generation
- **Description:** The SealedPdfGenerator exists but does not produce the full sealed report format. Missing: per-page SHA-512 footer, QR code, 7-category table, actor profiles.
- **Reproduction:** Generate a report — footer shows placeholder text, no QR code.
- **Expected:** Full 528-page format as described in `FUNCTIONAL_REQUIREMENTS.md`.
- **Actual:** Basic PDF with text only.
- **Blocked by:** B1 integration (needs contradiction data to populate report).

### BUG-004: Evidence Vault Missing Hardware Keystore
- **Severity:** HIGH
- **Status:** OPEN
- **Area:** Crypto / Storage
- **Description:** Evidence Vault encryption exists but does not use Android hardware-backed keystore (TEE/StrongBox). Keys are stored in software-only keystore.
- **Reproduction:** Check keystore type on a device with StrongBox — key is not in StrongBox.
- **Expected:** Keys stored in StrongBox if available, TEE as fallback, software as last resort.
- **Actual:** All keys in software keystore.
- **Blocked by:** None. Can be implemented independently.

---

## MEDIUM (Feature Incomplete)

### BUG-005: OJRS Not Implemented
- **Severity:** MEDIUM
- **Status:** OPEN
- **Area:** B7 / Legal
- **Description:** Online Judicial Retrieval System (SAFLII/PACER/BAILII search) is not implemented. B7 only works offline.
- **Reproduction:** Enable OJRS in settings, run scan — no network queries made.
- **Expected:** B7 searches court databases and pairs judicial records with sealed documents.
- **Actual:** B7 uses only cached precedents.
- **Blocked by:** B7 Legal Mapper implementation, network permissions, API integrations.
- **Notes:** See `ONLINE_JUDICIAL_RETRIEVAL.md` for full specification.

### BUG-006: Audio/Video Forensics Not Implemented
- **Severity:** MEDIUM
- **Status:** OPEN
- **Area:** B8 / Multimedia
- **Description:** B8 Audio/Media Brain is not implemented. No transcription, no deepfake detection, no video analysis.
- **Reproduction:** Upload audio/video file — B8 is skipped during scan.
- **Expected:** Whisper.cpp transcription, speaker diarization, deepfake detection, video frame hashing.
- **Actual:** B8 returns empty findings.
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

### BUG-008: WorkManager Background Scan Not Implemented
- **Severity:** MEDIUM
- **Status:** OPEN
- **Area:** Background Processing
- **Description:** Forensic scans do not run in the background. User must keep app open.
- **Reproduction:** Start scan, background the app — scan pauses.
- **Expected:** Scan continues in background with progress notification.
- **Actual:** Scan pauses when app is backgrounded.
- **Blocked by:** WorkManager integration, foreground service setup.

---

## LOW (Polish / Nice-to-Have)

### BUG-009: Settings Screen Incomplete
- **Severity:** LOW
- **Status:** OPEN
- **Area:** UI
- **Description:** Settings screen exists but is missing: OJRS toggle, model management, constitution viewer, privacy settings.
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
- **Description:** Cannot verify sealed documents from other users via QR scan.
- **Reproduction:** No QR scanner in app.
- **Expected:** In-app QR scanner to verify any Verum Omnis seal.
- **Actual:** Not implemented.
- **Blocked by:** QR code generation in PDF (also not implemented).

---

## RESOLVED

No resolved bugs yet. This section will be populated as bugs are fixed.

### Template for Resolved Bugs

```
### BUG-{number}: Title
- **Severity:** {level}
- **Status:** FIXED
- **Fixed by:** {commit hash or PR}
- **Fix description:** {brief description of the fix}
- **Date resolved:** {date}
```

---

## Technical Debt

| Item | Description | Impact | Priority |
|------|-------------|--------|----------|
| TD-001 | B1 source code exists as documentation only — needs integration into Android project | Cannot run contradiction engine | P0 |
| TD-002 | llama.cpp needs Android NDK build setup | Cannot run on-device LLMs | P0 |
| TD-003 | No database migration strategy yet | Future schema changes will break | P1 |
| TD-004 | No dependency checksum verification | Supply chain risk | P1 |
| TD-005 | Model download uses HTTP not HTTPS | Security risk | P1 |
| TD-006 | No crash reporting integration | Cannot detect production crashes | P2 |
| TD-007 | No analytics (by design per Constitution) | Cannot measure usage — intentional | N/A |

---

## Feature Gaps Summary

| Feature | Status | Priority |
|---------|--------|----------|
| Nine-Brain Engine (B1-B9) | NOT IMPLEMENTED | P0 |
| llama.cpp JNI Bridge | NOT IMPLEMENTED | P0 |
| Sealed PDF (full format) | PARTIAL | P0 |
| Evidence Vault (hardware keystore) | PARTIAL | P0 |
| OJRS (court database search) | NOT IMPLEMENTED | P1 |
| Audio/Video Forensics (B8) | NOT IMPLEMENTED | P1 |
| VITS (Identity & Trust) | NOT IMPLEMENTED | P1 |
| Background Scan (WorkManager) | NOT IMPLEMENTED | P1 |
| Settings Screen (complete) | PARTIAL | P2 |
| Biometric Auth | NOT IMPLEMENTED | P2 |
| Camera Integration | NOT IMPLEMENTED | P2 |
| QR Scanner | NOT IMPLEMENTED | P2 |
