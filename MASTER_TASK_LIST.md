# Master Task List — Verum Omnis Build Checklist

**Document Purpose:** The complete checklist of tasks a coding assistant must complete. Do not stop working until every P0 and P1 task is done. Continue through P2 and P3 as time permits.

**Last Updated:** 2026-07-13  
**Version:** v5.2.8  
**Status:** This is a living document — mark tasks as they are completed.

---

## How to Use This Document

1. Start at P0 — Critical Path. Do not move to P1 until all P0 tasks are complete.
2. For each task, check BUILD_STATUS.md to see current status.
3. Mark tasks `[x]` as they are completed in the codebase.
4. If a task is blocked, document the blocker in KNOWN_BUGS.md.
5. Do not leave any P0 task unfinished. Ever.

---

## P0 — CRITICAL PATH (Must Complete First)

These features form the backbone of the app. Without them, nothing else works.

### 9-Brain Engine Core

- [x] **B1 — Contradiction Brain**: `ContradictionExtractor` integrated; detects contradiction types and categories across evidence.
- [x] **B2 — Document Brain**: `DocumentForensicsBrain` implemented with creator-tool mismatch, PDF metadata tamper and image EXIF/GPS consistency checks.
- [~] **B3 — Communication Brain**: Timeline-gap analysis implemented; full chat-export parsing (WhatsApp/SMS/email) pending.
- [x] **B4 — Behavioral Brain**: `BehavioralBrain` detects gaslighting, stress, isolation and dismissive language; scorecard integration pending.
- [x] **B5 — Timeline Brain**: `reconstructTimeline()` extracts and orders dated events; >730-day gap flag pending explicit report/UI surfacing.
- [~] **B6 — Financial Brain**: Duplicate-amount detection and `TaxModule` integration implemented; hidden-payment / rent-tracing pending.
- [~] **B7 — Legal Brain**: Offline jurisdiction/statute mapping (ZA, UAE, US, EU) implemented; OJRS search framework in progress.
- [~] **B8 — Audio/Media Brain**: `AudioBrain` metadata/tamper/transcript analysis implemented; native Whisper.cpp/FFmpeg/deepfake deferred behind interfaces.
- [~] **B9 — Guardian Brain**: `Safeguards` weaponization/coercion checks present; dedicated `GuardianBrain`, Silence Ledger and hard-stop UI in progress.
- [x] **Nine-Brain Orchestrator**: `BrainCouncil` voting implemented; B1 + ≥2 brain confirmation required, B9 never votes.

### Forensic Pipeline

- [x] **ForensicService.scan()**: End-to-end ingest → 9-Brain analysis → seal pipeline implemented; report generation wired.
- [~] **Triple Verification**: `TripleConsensus` model and report thesis/antithesis/synthesis fields present; full narrative pass pending.
- [x] **Evidence Ingestion**: `ForensicService.ingest/ingestAudio/ingestMedia` support documents, audio and media; file-type detection in UI.
- [x] **Evidence Vault**: `EvidenceVault` uses Keystore-backed AES-256-GCM with StrongBox → TEE → software fallback.
- [~] **Evidence Artifact Model**: SHA-512 content hashing used throughout; canonical ID and JSON serialization partially formalized.

### LLM Integration

- [~] **llama.cpp JNI Bridge**: Interfaces (`ReportWriter`, `Transcriber`) isolate native code; JNI wrapper not built in this environment.
- [~] **Model Download System**: Stub download flow present; SHA-256/signature verification pending native model integration.
- [~] **Device Tier Detection**: `DeviceTier` utility present; startup model selection pending.
- [~] **G3 (Gemma 3) Integration**: `DeterministicReportWriter` produces structured appendix text; native Gemma 3 deferred.
- [~] **PHR3 (PHI-3/Command R) Integration**: Chat UI exists; native PHI-3/Command R deferred.
- [~] **G4 (Gemma 4) Integration**: Chat UI exists; native Gemma 4 deferred.
- [~] **Model Integrity Check**: `B9` validation hook reserved; pending model download system.

### Report Generation

- [x] **Sealed PDF Generator**: `SealedPdfGenerator` renders deterministic PDF via Android native API with bundled colors/fonts.
- [x] **Per-Page SHA-512 Footer**: `SealedPdfContent.paginate()` generates deterministic per-page footer.
- [x] **QR Code Generation**: `QrCodeGenerator` produces ZXing QR on cover encoding shortcode, seal hash and Constitution version.
- [~] **7-Category Contradiction Table**: Contradictions rendered by category in matrix; dedicated summary table pending.
- [~] **Actor Profile Section**: Persons extracted; per-person scorecard pending.

---

## P1 — CORE FUNCTIONALITY (Must Complete After P0)

### Cryptographic Sealing

- [x] **SHA-512 Evidence Hashing**: `Sha512.hash()` used for every evidence item; manifest stores hash.
- [~] **OpenTimestamps Integration**: `OpenTimestampsService` submits hash; Bitcoin confirmation handling pending.
- [~] **Blockchain Confirmation Tracking**: `SealRecord.status` supports PENDING; polling pending.
- [~] **Seal Verification**: `EvidenceSealer.verify()` implemented; in-app scanner/workflow pending.

### Chat Interface

- [x] **Chat UI Wiring**: `ChatScreen` and `VerumViewModel` chat flow implemented; deterministic responses while native models deferred.
- [~] **Anchored Responses**: Chat responses cite report reference; person/page/line anchoring pending.
- [~] **Hypothesis Flagging**: Legal mappings flagged in model; chat hypothesis label pending.
- [~] **Ordinal Confidence**: `Confidence` enum used in engine; chat confidence display pending.

### Background Processing

- [x] **WorkManager Integration**: `ForensicScanWorker` + `ScanWorkScheduler` implement background scans with progress Data.
- [~] **Scan Progress Notifications**: Notification channel exists; detailed progress updates pending.
- [~] **Foreground Service**: WorkManager handles background execution; explicit foreground service pending.

### Document Forensics

- [~] **Tesseract OCR Integration**: Deferred behind text-extraction interface; not built in JVM-only environment.
- [~] **PDF Parsing (MuPDF)**: Deferred behind PDF-analysis interface; not built in JVM-only environment.
- [x] **PDF Metadata Analysis**: `DocumentForensicsBrain` detects creator-tool mismatch and metadata tamper signals.
- [~] **EXIF Analysis**: GPS consistency checks implemented via `DocumentForensicsBrain`; full EXIF parser pending.

---

## P2 — FEATURE COMPLETENESS

### Audio/Video Forensics

- [ ] **Whisper.cpp Integration**: Offline transcription with word-level timestamps. Greedy decoding, temperature=0.
- [ ] **Speaker Diarization**: Identify who spoke when in audio recordings.
- [ ] **Video Container Hashing**: FFmpeg-based container integrity verification.
- [ ] **Video Frame Hashing**: Per-frame hash for tamper detection. Detect dropped frames, re-encoding.
- [ ] **GOP Analysis**: Group of Pictures structure analysis for video tampering detection.

### Legal Mapping

- [ ] **Jurisdiction Database**: Pre-loaded legal frameworks for SA, UAE, US, UK, EU, AU, CA, IN.
- [ ] **Statute Auto-Mapping**: B7 automatically maps contradictions to applicable statutes.
- [ ] **Precedent Database**: ~2,500 built-in case summaries for offline operation.
- [ ] **Legal Hypothesis Layer**: All legal conclusions flagged as hypothesis, not fact.

### Report Features

- [ ] **Timeline Visualization**: Interactive timeline in report showing event sequence and gaps.
- [ ] **Financial Analysis Section**: Transaction charts, hidden payment highlights, pattern analysis.
- [ ] **Legal Exposure Map**: Visual mapping of contradictions to legal categories and statutes.
- [ ] **Report Sharing**: Export PDF via Android share sheet. Maintain seal integrity during share.

### Settings

- [ ] **OJRS Toggle**: User can enable/disable online judicial retrieval.
- [ ] **Model Management**: View downloaded models, check integrity, re-download if needed.
- [ ] **Constitution Viewer**: In-app viewer for the full Verum Omnis Constitution.
- [ ] **Privacy Settings**: Control evidence retention, auto-delete policies.

---

## P3 — ADVANCED FEATURES

### Online Judicial Retrieval (OJRS)

- [ ] **SAFLII Search**: Query South African legal database. Parse results. Download judgments.
- [ ] **PACER Search**: Query US federal court records. Handle API authentication.
- [ ] **BAILII Search**: Query UK/Ireland legal database.
- [ ] **Keyword Extraction**: B7 extracts party names, case numbers, legal keywords from documents.
- [ ] **Court Record Download**: Download sworn testimony and judgments. Verify authenticity.
- [ ] **Proposition Pairing**: B1 pairs sealed document propositions with judicial record propositions.
- [ ] **Consciousness of Guilt Detection**: >730-day gap between signed document and sworn testimony = CRITICAL.

### Identity & Trust System (VITS)

- [ ] **UserProfile**: 6 trust tiers (UNVERIFIED → GUARDIAN). Tier-based feature access.
- [ ] **DeviceIdentity**: Hardware-backed device attestation. Unique device fingerprint.
- [ ] **MetadataFraudDetector**: Analyze uploaded file metadata for forgery indicators.
- [ ] **UnderTextWatermark**: Apply invisible watermarks to sensitive documents.
- [ ] **IdentityQRCode**: Generate scannable QR containing user's verified identity.

### Security & Anti-Weaponization

- [ ] **Article X Keyword Detection**: Scan for "kill chain", "target acquisition", "artillery correction" etc.
- [ ] **Weaponization Hard Stop**: Full-screen block. Export disabled. Violation logged. SHA-512 anchored.
- [ ] **Silence Ledger**: Immutable log of coercion attempts. Append-only. Blockchain-anchored.
- [ ] **ConstitutionalValidator**: CI pre-commit hook. Runtime validation. Blocks non-compliant code.

### UI Polish

- [ ] **Biometric Authentication**: Fingerprint/face unlock for Evidence Vault access.
- [ ] **Camera Integration**: Capture evidence directly in-app. Auto-hash on capture.
- [ ] **QR Scanner**: In-app scanner to verify sealed documents from other users.
- [ ] **Report Comparison**: Side-by-side comparison of multiple forensic reports.
- [ ] **Accessibility**: Full TalkBack support. High contrast mode. Font size adjustment.

---

## P4 — OPTIMIZATION & DEPLOYMENT

### Performance

- [ ] **Model Loading Optimization**: <6s load time on flagship. <12s on entry.
- [ ] **Memory Management**: Dynamic model unloading when backgrounded. Aggressive GC.
- [ ] **Battery Optimization**: Background scan battery usage <5% per hour.
- [ ] **Cache Management**: LRU cache for evidence artifacts. Auto-cleanup old scans.

### Testing

- [ ] **Unit Tests**: Minimum 75% code coverage for engine package.
- [ ] **Integration Tests**: End-to-end scan → seal → report flow.
- [ ] **Determinism Tests**: Same input produces identical output hash across 100 runs.
- [ ] **Constitutional Tests**: Verify all 15 Prime Directives are enforced.
- [ ] **UI Tests**: Jetpack Compose screenshot tests for all screens.
- [ ] **Instrumented Tests**: On-device tests for LLM integration, encryption, camera.

### Deployment

- [ ] **Play Store Listing**: Screenshots, description, privacy policy.
- [ ] **App Signing**: Release keystore configured. Play App Signing enabled.
- [ ] **ProGuard/R8**: Minification and obfuscation rules for release builds.
- [ ] **Firebase Crashlytics**: Crash reporting integration (analytics disabled per Constitution).
- [ ] **OTA Updates**: In-app update check (optional, user-controlled).

---

## Task Summary

| Priority | Tasks | Must Complete |
|----------|-------|---------------|
| P0 — Critical | 27 | **YES — ALL OF THEM** |
| P1 — Core | 14 | **YES — ALL OF THEM** |
| P2 — Feature | 13 | Recommended |
| P3 — Advanced | 22 | As time permits |
| P4 — Polish | 15 | As time permits |
| **TOTAL** | **91** | **41 mandatory** |
