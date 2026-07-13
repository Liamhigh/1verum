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

- [ ] **B1 — Contradiction Brain**: Integrate the 962-line engine from `B1_SOURCE_CODE.md` into the app. Must detect all 11 contradiction types across 7 constitutional categories.
- [ ] **B2 — Document Brain**: Implement PDF metadata extraction, tamper detection, EXIF analysis, font anomaly detection.
- [ ] **B3 — Communication Brain**: Parse chat exports (WhatsApp, SMS, email), detect deletions, timing gaps, redactions.
- [ ] **B4 — Behavioral Brain**: Integrate LIWC++ patterns for evasion/gaslighting detection. Output per-person liability scorecard (0-10).
- [ ] **B5 — Timeline Brain**: Reconstruct event sequences from all evidence. Identify missing/deleted entries. Flag >730-day gaps as consciousness of guilt.
- [ ] **B6 — Financial Brain**: Transaction reconciliation, hidden payment detection, duplicate invoice detection, rent tracing.
- [ ] **B7 — Legal Brain**: Map facts to legal categories. Load jurisdiction-specific statute database. Implement OJRS search framework.
- [ ] **B8 — Audio/Media Brain**: Whisper.cpp JNI integration for transcription. Video frame hashing via FFmpeg. Basic deepfake detection.
- [ ] **B9 — Guardian Brain**: Constitutional rule enforcement. Weaponization keyword detection. Weaponization hard stop UI. Red-team testing framework.
- [ ] **Nine-Brain Orchestrator**: Implement voting system. B1 flags + 2+ other brains confirm = accepted. B9 never votes. Quorum not met = INSUFFICIENT.

### Forensic Pipeline

- [ ] **ForensicService.scan()**: Complete end-to-end pipeline. Ingest → 9-Brain analysis → Triple Verification → Seal → Report.
- [ ] **Triple Verification**: Thesis/Antithesis/Synthesis implementation. Every finding must pass all three stages.
- [ ] **Evidence Ingestion**: Support PDF, PNG/JPEG, MP3/WAV, MP4/MOV, TXT, CSV, ZIP of chat exports, email dumps.
- [ ] **Evidence Vault**: AES-256-GCM encrypted storage with Android hardware-backed keystore (Tee/StrongBox).
- [ ] **Evidence Artifact Model**: Content-hash-based IDs only. No filename/size/timestamp in IDs. Canonical JSON for all metadata.

### LLM Integration

- [ ] **llama.cpp JNI Bridge**: Build JNI wrapper for llama.cpp. Load GGUF models. Support GPU offloading where available.
- [ ] **Model Download System**: Download Gemma 3, PHI-3/Command R, Gemma 4 from verified sources. SHA-256 + signature verification.
- [ ] **Device Tier Detection**: At startup, detect RAM (<4GB / 4-8GB / 8GB+). Load appropriate models and quantizations.
- [ ] **G3 (Gemma 3) Integration**: Report writer model. Receives sealed ScanResult. Generates structured forensic report text.
- [ ] **PHR3 (PHI-3/Command R) Integration**: Chat model for entry/mid-tier. Conversational interface, anchored responses.
- [ ] **G4 (Gemma 4) Integration**: Chat model for flagship. 128K context, deeper reasoning, full bundle analysis.
- [ ] **Model Integrity Check**: B9 validates model hashes on every app launch. Refuse to load if verification fails.

### Report Generation

- [ ] **Sealed PDF Generator**: Deterministic PDF generation. Bundled fonts. No system font dependencies.
- [ ] **Per-Page SHA-512 Footer**: Every page includes: seal ID, SHA-512 hash, timestamp, page number.
- [ ] **QR Code Generation**: QR in footer linking to blockchain anchor. Contains: caseId, finalSealHash, constitutionHash, engineVersion.
- [ ] **7-Category Contradiction Table**: Report section for each constitutional category with severity ratings.
- [ ] **Actor Profile Section**: Per-person dishonesty scores, flags, associated contradictions.

---

## P1 — CORE FUNCTIONALITY (Must Complete After P0)

### Cryptographic Sealing

- [ ] **SHA-512 Evidence Hashing**: Hash every ingested file. Store hash in evidence manifest.
- [ ] **OpenTimestamps Integration**: Submit evidence manifest hash to OpenTimestamps. Handle Bitcoin confirmation.
- [ ] **Blockchain Confirmation Tracking**: Poll for Bitcoin confirmation. Show "pending" → "confirmed" status.
- [ ] **Seal Verification**: In-app ability to verify a sealed report's integrity by re-computing hash.

### Chat Interface

- [ ] **Chat UI Wiring**: Connect Jetpack Compose chat screen to PHR3 (entry/mid) or G4 (flagship).
- [ ] **Anchored Responses**: Every chat response must cite person, page, line from sealed ScanResult.
- [ ] **Hypothesis Flagging**: Legal interpretations automatically flagged as HYPOTHESIS.
- [ ] **Ordinal Confidence**: Chat responses use VERY_HIGH/HIGH/MODERATE/LOW/INSUFFICIENT only.

### Background Processing

- [ ] **WorkManager Integration**: Long-running forensic scans execute in background with progress notifications.
- [ ] **Scan Progress Notifications**: Show which brain is currently active. Percentage complete. ETA.
- [ ] **Foreground Service**: Scan continues even if user switches apps.

### Document Forensics

- [ ] **Tesseract OCR Integration**: Bundled fonts. Deterministic preprocessing (grayscale, threshold, deskew).
- [ ] **PDF Parsing (MuPDF)**: Deterministic rendering. Extract text, images, metadata. Bundled fonts only.
- [ ] **PDF Metadata Analysis**: Detect backdating, font inconsistencies, editing software traces.
- [ ] **EXIF Analysis**: Extract and analyze image metadata. Detect GPS inconsistencies, editing software.

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
