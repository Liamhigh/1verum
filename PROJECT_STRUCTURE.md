# Project Structure — Verum Omnis

**Document Purpose:** Explains the purpose of every folder and package in the project. What files belong where, and why.

**Last Updated:** 2026-07-13  
**Version:** v5.2.8

---

## Repository Root

```
1verum/
├── .github/                    # GitHub configuration
│   └── workflows/              # CI/CD pipelines (build, test, lint)
├── app/                        # Android application module
│   └── src/main/
│       ├── java/com/verumomnis/forensic/
│       │   ├── core/           # Constitutional foundation
│       │   ├── crypto/         # Cryptographic sealing
│       │   ├── engine/         # Nine-Brain forensic engine
│       │   ├── forensics/      # Specialized forensic modules
│       │   ├── model/          # Data models
│       │   ├── pdf/            # Report generation
│       │   ├── ui/             # Jetpack Compose screens
│       │   ├── vault/          # Evidence storage
│       │   └── MainActivity.kt # Entry point
│       ├── cpp/                # Native C++ code (llama.cpp JNI)
│       ├── assets/             # Bundled assets (fonts, models)
│       └── res/                # Android resources
├── docs/                       # Documentation (reference copies)
├── build.gradle.kts            # App-level build config
├── settings.gradle.kts         # Project settings
└── gradle.properties           # Gradle properties
```

---

## `core/` — Constitutional Foundation

**Purpose:** The bedrock of the entire system. Hard-coded rules that cannot be overridden.

| File | Purpose |
|------|---------|
| `Constitution.kt` | 15 Prime Directives, Article X Anti-War Doctrine. All COMPILE-TIME constants. |
| `DeviceTier.kt` | Detects device RAM and selects appropriate models (<4GB, 4-8GB, 8GB+). |
| `ModelLoader.kt` | Loads GGUF models via llama.cpp JNI. Handles quantization selection. |
| `Llm.kt` | Unified interface for all LLMs (G3, PHR3, G4). Abstracts model differences. |
| `constitutional/` | `ConstitutionalValidator.kt` — Static analysis for constitutional compliance. |

**Rule:** Nothing in `core/` depends on anything outside `core/` (except Kotlin stdlib). It is the innermost circle.

---

## `crypto/` — Cryptographic Sealing

**Purpose:** Hashing, encryption, and blockchain anchoring. The trust layer.

| File | Purpose |
|------|---------|
| `EvidenceSealer.kt` | Orchestrates the sealing pipeline: hash → timestamp → anchor → QR. |
| `Sha512.kt` | SHA-512 hashing utility. Deterministic, pure Kotlin. |
| `VaultEncryption.kt` | AES-256-GCM encryption/decryption. Android Keystore integration. |
| `OpenTimestampsService.kt` | Submits hashes to OpenTimestamps, polls for Bitcoin confirmation. |

**Rule:** All crypto operations use `java.security` and `javax.crypto` only. No third-party crypto libraries.

---

## `engine/` — The Nine-Brain System

**Purpose:** The forensic heart of the app. 9 specialized AI brains that analyze evidence.

| File | Purpose | Brain |
|------|---------|-------|
| `NineBrainEngine.kt` | Orchestrator. Manages brain execution order, voting, quorum. | — |
| `ContradictionExtractor.kt` | B1: Extracts propositions, finds contradictions across 7 categories. | B1 |
| `DocumentBrain.kt` | B2: PDF metadata, tamper detection, EXIF analysis, font anomalies. | B2 |
| `CommunicationBrain.kt` | B3: Chat log parsing, deletion detection, timing gap analysis. | B3 |
| `BehavioralBrain.kt` | B4: LIWC++ patterns, evasion/gaslighting detection, liability scores. | B4 |
| `TimelineBrain.kt` | B5: Event sequence reconstruction, gap detection, consciousness of guilt. | B5 |
| `FinancialBrain.kt` | B6: Transaction reconciliation, hidden payment detection, rent tracing. | B6 |
| `LegalMapper.kt` | B7: Statute mapping, jurisdiction detection, OJRS integration. | B7 |
| `AudioBrain.kt` | B8: Whisper.cpp transcription, speaker diarization, deepfake detection. | B8 |
| `GuardianBrain.kt` | B9: Constitutional enforcement, weaponization detection, red-team testing. | B9 |
| `ForensicService.kt` | Main entry point. `scan()` method triggers the full pipeline. | — |
| `TripleVerification.kt` | Thesis/Antithesis/Synthesis verification for every finding. | — |
| `EntityExtractor.kt` | Extracts people, organizations, dates, amounts from evidence. | Shared |
| `SubjectClassifier.kt` | Classifies evidence subjects into the 7 constitutional categories. | Shared |
| `EvidenceDocument.kt` | Wrapper for evidence artifacts with parsing capabilities. | Shared |
| `AudioEvidence.kt` | Audio-specific evidence wrapper with waveform access. | Shared |
| `MediaEvidence.kt` | Image/video evidence wrapper with frame access. | Shared |
| `EmailModule.kt` | Email parsing (EML/MSG format). | B3 |
| `AntiHarassmentMonitor.kt` | Detects harassment patterns in evidence. | B4 |
| `Transcriber.kt` | Text extraction coordinator (OCR + Whisper). | B2/B8 |
| `TaxModule.kt` | Tax analysis and reporting. | B6 |
| `ReportGenerator.kt` | Generates the sealed forensic report from ScanResult. | — |

**Rule:** Each brain ONLY writes to its own output data structure. Brains communicate through the orchestrator, not directly.

---

## `forensics/` — Specialized Forensic Modules

**Purpose:** Low-level forensic analysis libraries. Called by the brains, not directly by UI.

### `forensics/image/`

| File | Purpose |
|------|---------|
| `ImageForensics.kt` | Main image analysis coordinator. |
| `ExifAnalyzer.kt` | EXIF metadata extraction and analysis. |
| `ElaAnalyzer.kt` | Error Level Analysis for tamper detection. |
| `CloneDetector.kt` | Copy-paste detection in images. |
| `ScreenshotDetector.kt` | Distinguishes screenshots from original images. |

### `forensics/video/`

| File | Purpose |
|------|---------|
| `VideoForensics.kt` | Main video analysis coordinator. |
| `FrameAnalyzer.kt` | Per-frame hash extraction via FFmpeg. |
| `GopAnalyzer.kt` | Group of Pictures structure analysis. |

### `forensics/audio/`

| File | Purpose |
|------|---------|
| `WhisperBridge.kt` | JNI bridge to Whisper.cpp for transcription. |
| `VoiceAnalyzer.kt` | Synthetic audio and voice cloning detection. |
| `SpeakerDiarizer.kt` | Identifies who spoke when in audio recordings. |

### `forensics/document/`

| File | Purpose |
|------|---------|
| `OcrProcessor.kt` | Tesseract OCR with bundled fonts. |
| `PdfExtractor.kt` | MuPDF-based PDF text and image extraction. |
| `PdfMetadataAnalyzer.kt` | Detects backdating, font inconsistencies. |

### `forensics/device/`

| File | Purpose |
|------|---------|
| `DeviceForensics.kt` | Device fingerprinting via SEON SDK. |
| `AdbBridge.kt` | Android Debug Bridge integration. |

---

## `model/` — Data Models

**Purpose:** All data classes used across the app. Immutable where possible.

| File | Purpose |
|------|---------|
| `Evidence.kt` | `EvidenceArtifact` (content-hash ID), `EvidenceAtom` (parsed content), `Anchor` (page/line reference). |
| `Contradiction.kt` | `Contradiction`, `Claim`, `Severity` (ordinal enum), `ContradictionType` (11 types). |
| `ScanResult.kt` | The sealed output of the forensic scan. Passed to LLMs. Immutable. |
| `Report.kt` | Forensic report structure. Contains all sections. |
| `ActorProfile.kt` | Per-person analysis: dishonesty score, flags, associated contradictions. |
| `Timeline.kt` | Event sequence with gap detection. |
| `Finance.kt` | Transaction data, hidden payment indicators. |
| `Email.kt` | Parsed email structure (headers, body, attachments). |
| `Audio.kt` | Audio transcription output with timestamps. |
| `Media.kt` | Image/video metadata and analysis results. |
| `Ots.kt` | OpenTimestamps data (anchor hash, confirmation status). |
| `ConstitutionalModels.kt` | Data classes for Constitution enforcement (Confidence enum, VotingStatus, etc.). |

**Rule:** All model classes are `data class` with `val` properties. Mutable state lives in ViewModels only.

---

## `pdf/` — Report Generation

**Purpose:** Deterministic PDF generation for sealed forensic reports.

| File | Purpose |
|------|---------|
| `SealedPdfGenerator.kt` | Main PDF generator. Orchestrates all report sections. |
| `SealedPageRenderer.kt` | Renders individual pages with seal footer. |
| `ReportSectionRenderer.kt` | Renders each report section (contradictions, timeline, etc.). |
| `PdfFontProvider.kt` | Loads bundled fonts. Ensures deterministic rendering. |
| `SealFooterRenderer.kt` | Renders per-page SHA-512 footer + QR code. |
| `QrCodeGenerator.kt` | Generates QR codes containing seal verification data. |

**Rule:** PDF generation is deterministic. Same ScanResult + same constitution version = bit-for-bit identical PDF.

---

## `ui/` — Jetpack Compose Screens

**Purpose:** All user interface code. Screens, components, theme, and ViewModels.

| File | Purpose |
|------|---------|
| `VerumApp.kt` | Root composable. Sets up navigation and theme. |
| `VerumViewModel.kt` | Shared ViewModel for app-level state. |
| `Theme.kt` | Material 3 theme. Colors, shapes, typography from verumglobal.foundation. |
| `Type.kt` | Font definitions. Uses bundled fonts only. |
| `StoryScreen.kt` | Onboarding / app story screen. |
| `UploadScreen.kt` | Evidence upload (multi-file picker). |
| `ScanProgressScreen.kt` | Real-time scan progress with brain activity display. |
| `ReportViewerScreen.kt` | In-app sealed report viewer. |
| `ChatScreen.kt` | AI chat interface for asking about sealed evidence. |
| `VaultScreen.kt` | Evidence vault browser. |
| `EmailScreen.kt` | Secure communication interface. |
| `TaxScreen.kt` | Tax analysis module UI. |
| `SettingsScreen.kt` | App settings (OJRS toggle, model management, constitution viewer). |
| `TimelineScreen.kt` | Interactive timeline visualization. |
| `ActorProfileScreen.kt` | Per-person analysis view. |
| `ContradictionDetailScreen.kt` | Drill into specific contradiction. |
| `MediaIngestor.kt` | Photo/audio/video capture and upload. |
| `Components.kt` | Shared UI components (buttons, cards, dialogs). |
| `viewmodel/` | Screen-specific ViewModels (UploadViewModel, ScanViewModel, etc.). |

**Rule:** UI code NEVER calls forensic engines directly. It goes through ViewModel → Repository → Engine.

---

## `vault/` — Evidence Storage

**Purpose:** Encrypted local storage for all evidence and reports.

| File | Purpose |
|------|---------|
| `EvidenceVault.kt` | Main vault interface. CRUD operations for evidence and reports. |
| `VaultDatabase.kt` | Room database definition. |
| `EvidenceDao.kt` | Room DAO for evidence artifacts. |
| `ScanResultDao.kt` | Room DAO for scan results and contradictions. |
| `LedgerDao.kt` | Room DAO for append-only audit ledger. |
| `VaultMigration.kt` | Database migration scripts. |

**Rule:** All vault operations are async (suspend functions). Never block the main thread.

---

## `cpp/` — Native C++ Code

**Purpose:** JNI bridge between Kotlin and llama.cpp / Whisper.cpp.

| File | Purpose |
|------|---------|
| `CMakeLists.txt` | CMake build configuration for native libraries. |
| `llama_jni.cpp` | JNI wrapper for llama.cpp. Model loading, inference, context management. |
| `whisper_jni.cpp` | JNI wrapper for Whisper.cpp. Audio transcription. |
| `native_bridge.h` | Shared JNI utilities and error handling. |

**Rule:** Native code is minimal. All business logic stays in Kotlin. Native code is only for model inference.

---

## `assets/` — Bundled Assets

| Directory | Contents |
|-----------|----------|
| `fonts/` | Bundled deterministic fonts (no system font dependency) |
| `tessdata/` | Tesseract OCR language models (eng, afr, ara) |
| `constitution/` | Verum Omnis Constitution text (for in-app viewer) |
| `precedents/` | ~2,500 cached legal precedents for offline B7 operation |
| `liwc/` | LIWC++ dictionary files for B4 behavioral analysis |

---

## Documentation Files (Repository Root)

These files live at the repository root and provide the complete knowledge base:

| File | Purpose | Read Order |
|------|---------|------------|
| `WHAT_THIS_APP_IS.md` | Master build reference — what the app is and how it works | **1st** |
| `AI_BUILD_INSTRUCTIONS.md` | Rules for AI coding assistants | **2nd** |
| `BUILD_STATUS.md` | Feature completion matrix | **3rd** |
| `MASTER_TASK_LIST.md` | Complete task checklist | **4th** |
| `PROMPT.md` | Main prompt with coding rules and constraints | **5th** |
| `FUNCTIONAL_REQUIREMENTS.md` | Screen-by-screen behavior specification | Reference |
| `ARCHITECTURE.md` | System architecture and data flow | Reference |
| `DEPENDENCIES.md` | Libraries, SDKs, models, versions | Reference |
| `TEST_PLAN.md` | All tests that must pass | Reference |
| `BUILD_RULES.md` | Non-negotiable build rules | Reference |
| `KNOWN_BUGS.md` | Existing bugs and unfinished areas | Reference |
| `DONE_CRITERIA.md` | Definition of "finished" | Reference |
| `PROJECT_STRUCTURE.md` | This file | Reference |
| `CONSTITUTION.md` | Binding constitutional governance | Reference |
| `NINE_BRAIN_RULES.md` | Brain-specific operational rules | Reference |
| `B1_ENGINE.md` | B1 Contradiction Brain specification | Reference |
| `B1_SOURCE_CODE.md` | Complete 962-line B1 source code | Reference |
| `ONLINE_JUDICIAL_RETRIEVAL.md` | B7 OJRS workflow specification | Reference |
| `ON_DEVICE_LLM_ARCHITECTURE.md` | 3-model LLM system specification | Reference |
| `IDENTITY_TRUST_SYSTEM.md` | VITS specification | Reference |
| `TEMPLATE_HISTORY.md` | Template evolution history | Reference |
| `AGENTS.md` | AI agent rules | Reference |

---

## File Placement Rules

When adding new files to the project, follow these rules:

1. **Business logic** → `engine/` (if it's a brain) or `core/` (if it's constitutional)
2. **Cryptographic code** → `crypto/`
3. **Low-level forensics** → `forensics/{domain}/`
4. **Data models** → `model/`
5. **PDF generation** → `pdf/`
6. **UI code** → `ui/` (screens) or `ui/viewmodel/` (ViewModels)
7. **Storage code** → `vault/`
8. **Native code** → `cpp/`
9. **Static assets** → `assets/`
10. **Documentation** → Repository root (`.md` files)

Never place business logic in `ui/`. Never place UI code in `engine/`. Never place models in `crypto/`.
