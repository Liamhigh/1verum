# What This App Is — Verum Omnis Master Build Reference

**Document Purpose:** This is the first document any code assistant should read. It explains what Verum Omnis is, what it does, how it works, and what you need to build. All other documents in this repository provide deeper detail on specific components. When in doubt, return here.

**Repository:** `github.com/Liamhigh/1verum`  
**Platform:** Android (native Kotlin, Jetpack Compose)  
**Status:** Production build — 8 court-validated proceedings, 111 contradictions found  
**Version:** v5.2.8 (Contradiction Engine)

---

## Table of Contents
1. [The One-Sentence Summary](#1-the-one-sentence-summary)
2. [What Problem It Solves](#2-what-problem-it-solves)
3. [Core Philosophy](#3-core-philosophy)
4. [The Architecture at a Glance](#4-the-architecture-at-a-glance)
5. [The 9-Brain System](#5-the-9-brain-system)
6. [The 3-Model On-Device LLM System](#6-the-3-model-on-device-llm-system)
7. [The Evidence Flow (Step by Step)](#7-the-evidence-flow-step-by-step)
8. [What Gets Built (Output)](#8-what-gets-built-output)
9. [Fraud Categories Detected](#9-fraud-categories-detected)
10. [Constitutional Constraints (Rules You Cannot Break)](#10-constitutional-constraints)
11. [Tech Stack & Dependencies](#11-tech-stack--dependencies)
12. [File Structure to Build](#12-file-structure-to-build)
13. [Reference Documents (Read These Next)](#13-reference-documents)

---

## 1. The One-Sentence Summary

Verum Omnis is an Android app that turns a mobile phone into a constitutional forensic laboratory. Users upload documents, audio, chat logs, and images. The app scans them on-device using 9 parallel AI "brains," finds contradictions, cryptographically seals the evidence, and produces court-ready forensic reports — all offline, all free for citizens and police.

---

## 2. What Problem It Solves

The justice system is broken for ordinary people:
- A petrol station operator in rural South Africa loses everything to fraud. He cannot afford a lawyer. He has no forensic tools. He is invisible to the justice system.
- A small business owner in the UAE is pushed out of his company by partners who forge documents and lie in court. Proving it costs hundreds of thousands in legal fees.
- A vulnerable person is dispossessed of their business while traumatized. The perpetrator claims it was a "mutual agreement." There is no evidence trail.

**Verum Omnis solves this by putting the same forensic power that billion-dollar law firms use into a free Android app that runs entirely on the user's phone.**

**Real-world proof:** The founder (Liam Highcock) built this app after losing everything to cross-border fraud. Using Verum Omnis, he filed evidence in the Constitutional Court of South Africa (CCT237/20 & CCT19/20), had it accepted by SAPS, the Hawks, the Public Protector, and courts in two countries — all generated on his mobile phone at zero cost.

---

## 3. Core Philosophy

### "People Before Power"

Truth should be a function of mathematical certainty, not institutional credibility. The app embodies these principles:

| Principle | What It Means |
|-----------|--------------|
| **Offline-first** | All evidence processing happens on the device. No cloud. No servers. No data leaves the phone. |
| **Deterministic** | Same evidence = same findings = same seal. Every time. No randomness, no AI hallucination. |
| **Free for citizens** | Private individuals and law enforcement use it free, permanently. Hard-coded. No paywalls. |
| **Constitutionally governed** | An AI Constitution (v5.2.7) hard-codes every rule. No prompt, no authority, no court order can override it. |
| **Cryptographically sealed** | Every finding is SHA-512 hashed and blockchain-anchored. Tamper-proof. Court-admissible. |
| **Jurisdiction-agnostic** | Works in every country. The contradiction engine does not need local laws to find contradictions. B7 maps findings to local statutes after detection. |

---

## 4. The Architecture at a Glance

```
┌─────────────────────────────────────────────────────────────────┐
│                        USER'S ANDROID PHONE                      │
│                                                                  │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────────┐  │
│  │  EVIDENCE   │    │   NINE-BRAIN │    │   3-MODEL LLM       │  │
│  │   INPUT     │───▶│    ENGINE    │───▶│    SYSTEM           │  │
│  │             │    │  (offline)   │    │  (on-device)        │  │
│  │ • PDFs      │    │              │    │                     │  │
│  │ • Images    │    │ B1: Contradiction    G3: Gemma 3        │  │
│  │ • Audio     │    │ B2: Document         (forensic reports) │  │
│  │ • Video     │    │ B3: Communications   PHR3: PHI-3/       │  │
│  │ • Chat logs │    │ B4: Behavioral       Command R          │  │
│  │ • Emails    │    │ B5: Timeline         (chat, 2-3GB)      │  │
│  │ • WhatsApp  │    │ B6: Financial        G4: Gemma 4        │  │
│  │ • Financial │    │ B7: Legal            (chat, flagship)   │  │
│  │   records   │    │ B8: Audio/Media                          │  │
│  │             │    │ B9: Guardian/Red Team                    │  │
│  └─────────────┘    └─────────────┘    └─────────────────────┘  │
│         │                    │                    │               │
│         ▼                    ▼                    ▼               │
│  ┌─────────────────────────────────────────────────────────┐     │
│  │              CRYPTOGRAPHIC SEALING LAYER                 │     │
│  │  SHA-512 hashing → Blockchain anchor → QR code → PDF     │     │
│  └─────────────────────────────────────────────────────────┘     │
│                              │                                    │
│                              ▼                                    │
│  ┌─────────────────────────────────────────────────────────┐     │
│  │              OUTPUTS                                     │     │
│  │  • Sealed Forensic Report (PDF, court-ready)             │     │
│  │  • Contradiction Ledger (111-format proven)              │     │
│  │  • Actor Profiles (per-person dishonesty scores)         │     │
│  │  • Timeline Reconstruction (event sequence)              │     │
│  │  • Financial Analysis (hidden payments, patterns)        │     │
│  │  • Legal Mapping (statutes, precedents per jurisdiction) │     │
│  │  • Chat with your evidence (explainable AI)              │     │
│  └─────────────────────────────────────────────────────────┘     │
│                                                                  │
│  Network access (optional, flagship only):                       │
│  • B7 searches SAFLII/PACER/BAILII for court records             │
│  • Blockchain hash anchoring (not document content)              │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. The 9-Brain System

The Nine-Brain Engine is the forensic heart of the app. Each "brain" is a specialized AI module. They vote as a council — no single brain can issue a finding alone.

### How Voting Works

A finding is accepted only when **B1 flags it AND at least 2 other brains confirm**. If quorum is not met, output is "INSUFFICIENT" or "INDETERMINATE_DUE_TO_CONCEALMENT." B9 (Guardian) never votes — it only validates and enforces rules.

### The Brains

| Brain | Name | What It Does | Voting |
|-------|------|-------------|--------|
| **B1** | Contradiction | Finds inconsistencies across statements, documents, testimony. The lead analyst — produces the raw conflict ledger. | YES |
| **B2** | Document | Checks metadata, tamper indicators, PDF structure, font anomalies, backdating. Tamper score 0.000-1.000. | YES |
| **B3** | Communications | Analyzes chat logs for deletions, timing gaps, redactions. Every message sealed and audited. | YES |
| **B4** | Behavioral | Detects evasion, gaslighting, manipulation using LIWC++ algorithms. Per-person liability scorecard (0-10). | YES |
| **B5** | Timeline | Reconstructs event sequences. Identifies missing entries. 2+ year gap = consciousness of guilt flag. | YES |
| **B6** | Financial | Flags hidden payments, duplicates, mismatched sums. Reconciles transactions against ledgers. Rent tracing. | YES |
| **B7** | Legal | Maps facts to legal categories. Jurisdiction-specific (UAE, SA, US, EU, UN). Online judicial retrieval (SAFLII/PACER/BAILII) on flagship devices. | YES |
| **B8** | Audio/Media | Offline transcription (Whisper.cpp), speaker diarization, synthetic audio detection, deepfake detection, video frame analysis. | YES |
| **B9** | Guardian/Red Team | **NON-VOTING.** Enforces constitutional boundaries. Trains and calibrates all other brains. Blocks weaponization attempts. Red-team testing. | **NO** |

### Triple Verification

Every finding passes three independent checks:
1. **Thesis** — Extract claims and facts with deterministic anchors
2. **Antithesis** — Search for conflicting data, alternative explanations, missing evidence
3. **Synthesis** — Validate consistency. Only if both prior stages pass is the finding accepted.

---

## 6. The 3-Model On-Device LLM System

Verum Omnis does NOT use cloud AI (OpenAI, Claude, etc.). It runs 100% on-device using locally-loaded models via llama.cpp. The user's evidence never leaves their phone.

### The Three Models

| Model | Role | Device Tier | Size | Why This Model |
|-------|------|-------------|------|----------------|
| **Gemma 3 (G3)** | **Forensic Report Writer** — generates the sealed court-ready PDF report from the ScanResult | Mid + Flagship | ~2.5GB Q4_K_M | Apache 2.0, long context, structured output, deterministic |
| **PHI-3 / Command R (PHR3)** | **Communicator** — powers the chat interface, explains findings, answers questions about sealed evidence | Entry + Mid | ~2.0-2.5GB | Tiny footprint, excellent chat, runs on 2-3GB devices |
| **Gemma 4 (G4)** | **Communicator (Premium)** — deeper reasoning, full evidence bundle in context, cross-referencing | Flagship only | ~7.5GB Q4_K_M | 128K context, superior reasoning, can hold entire 528-page bundle |

### Unrestricted Communication

The chat models (PHR3/G4) are **not restricted** in how they communicate. They are not "safe" models that sanitise findings. This is safe because:
1. All evidence is **cryptographically sealed** before the models see it
2. Models cannot alter, delete, or suppress findings (the seal is immutable)
3. The user is asking about **their own** evidence
4. Every response is anchored to the sealed report — models cannot hallucinate

**The models communicate the truth as the evidence shows it. No softening. No sanitisation.**

### Model Loading by Device

| Tier | RAM | Models Loaded | Chat Model | Report Model | OJRS |
|------|-----|---------------|------------|--------------|------|
| Entry | <4GB | G3 (reduced) + PHR3 | PHR3 | G3 (2K context) | Cached precedents |
| Mid | 4-8GB | G3 + PHR3 | PHR3 | G3 (8K context) | Search-only |
| Flagship | 8GB+ | G3 + G4 | G4 | G3 (32K context) | Full retrieval |

---

## 7. The Evidence Flow (Step by Step)

This is the single most important architectural rule. Raw evidence NEVER touches the AI chat directly. Everything goes through the forensic engine first.

```
STEP 1: User uploads documents
         ↓
STEP 2: [Evidence Vault] — AES-256-GCM encrypted, hardware-backed keystore
         ↓
STEP 3: ForensicService.scan() — deterministic, timestamp-injected
         ↓
STEP 4: Nine-Brain Engine processes evidence
         ├─ B1 extracts all propositions (claims, assertions)
         ├─ B2 verifies document integrity (metadata, tamper check)
         ├─ B3 analyzes communications (deletions, timing gaps)
         ├─ B4 detects behavioral patterns (evasion, gaslighting)
         ├─ B5 reconstructs timeline (event sequence, gaps)
         ├─ B6 analyzes financial data (hidden payments, duplicates)
         ├─ B7 maps to legal categories (+ OJRS on flagship)
         ├─ B8 processes audio/video (transcription, deepfake check)
         └─ B9 validates constitutional compliance (guardian check)
         ↓
STEP 5: Triple Verification (Thesis/Antithesis/Synthesis)
         ├─ Every finding must be confirmed by B1 + 2+ other brains
         └─ If quorum not met → "INSUFFICIENT" or "INDETERMINATE"
         ↓
STEP 6: [SEALED SCANRESULT] — SHA-512 verified, tamper-proof
         ↓
STEP 7: Report generation (G3 writes the sealed forensic report)
         ├─ Structured PDF with contradiction table
         ├─ Per-page footer: SHA-512 hash, seal ID, timestamp
         ├─ QR code for blockchain verification
         └─ 7 constitutional categories, severity ratings
         ↓
STEP 8: [SEALED FORENSIC REPORT] — court-admissible, cryptographically anchored
         ↓
STEP 9: AI Chat Interface (reads ONLY the sealed ScanResult)
         ├─ Entry/Mid: PHR3 (PHI-3/Command R) explains findings
         └─ Flagship: G4 (Gemma 4) provides deeper analysis
         ↓
STEP 10: User asks questions about their own sealed evidence
          ├─ Every answer cites anchors: person, page, line, statute
          └─ Legal interpretations flagged as HYPOTHESIS
```

### The Constitutional Boundary

**Raw uploads → ForensicService.scan() → Sealed ScanResult → AI Chat**

The AI chat NEVER sees raw documents. It only sees the sealed `ScanResult` object. This means:
- The AI cannot hallucinate about documents it hasn't scanned
- The AI cannot be jailbroken into ignoring findings
- The AI's answers are grounded in cryptographically verified evidence
- Every answer cites anchors

---

## 8. What Gets Built (Output)

After a forensic scan, the app produces:

### Primary Output: Sealed Forensic Report (PDF)
- 528-page format proven in court (AllFuels case)
- Per-page SHA-512 footer + QR code
- Blockchain-anchored timestamp
- Contradiction table with severity ratings
- Actor profiles with dishonesty scores
- Legal mapping to applicable statutes
- Court-admissible under Daubert Standard, ISO 27037, ECT Act 25/2002

### Secondary Outputs

| Output | Content |
|--------|---------|
| **Contradiction Ledger** | All contradictions found, sorted by severity (CRITICAL → HIGH → MODERATE → LOW → INSUFFICIENT) |
| **Actor Profiles** | Per-person analysis: dishonesty score (0-100), flags, associated contradictions |
| **Timeline Reconstruction** | Chronological event sequence with identified gaps and deleted entries |
| **Financial Analysis** | Transaction reconciliation, hidden payment detection, pattern analysis |
| **Legal Exposure Map** | Facts mapped to legal categories (Shareholder Oppression, Fraud, Breach of Fiduciary Duty, etc.) per jurisdiction |
| **Chat Interface** | User can ask questions about their sealed evidence; answers anchored to findings |
| **Blockchain Anchor** | SHA-512 hash anchored to public blockchain for tamper-proof timestamping |

---

## 9. Fraud Categories Detected

The engine detects 6 broad categories of fraud, with specific detection capabilities in each:

### 1. Document & Financial Fraud
- Forged/altered documents (manipulated PDFs, edited screenshots, doctored chat logs)
- Invoice fraud (hidden transfers, fake invoices, amount drift, beneficiary link analysis)
- Contract manipulation (unauthorized modifications to MOA, shareholder agreements)
- Identity document fraud (fake IDs, passports, credentials)

### 2. Cybercrime & Digital Evidence Tampering
- Unauthorized access (Gmail/cloud breaches, device intrusion)
- Deleted evidence recovery (carving deleted messages, call logs, browser history)
- Metadata manipulation (altered timestamps, GPS inconsistencies, editing software traces)
- Device spoofing (emulators, rooted devices, app cloning)

### 3. Multimedia-Based Fraud
- Deepfake media (AI-manipulated video, face-swapped images, voice cloning — 99.7% accuracy)
- Synthetic audio (voice forgery, cloned voices)
- Altered video (re-encoded video detection, GOP structure anomalies, frame timestamp discontinuities)
- Fake imagery (screenshot detection, error level analysis, clone detection)

### 4. Communication & Social Engineering Fraud
- Phishing attacks (fraudulent messages, fake links, impersonation)
- Romance/trust scams (emotional manipulation patterns, urgency analysis)
- Business email compromise (anomalous communication patterns, invoice diversion)
- Blackmail/sextortion (threatening communication analysis)

### 5. Financial Transaction Fraud
- Payment fraud (pattern analysis, IP geolocation mismatches, velocity checks)
- Account takeover (login anomalies, device changes, suspicious access)
- Money laundering (entity network mapping, shell company detection, unusual flows)
- Credit card fraud (BIN analysis, issuing bank validation)

### 6. App-Based Fraud
- Fake/malicious apps (fraudulent Android app detection — 91.7% accuracy)
- Permission abuse (dangerous permissions analysis)
- Icon/content mismatch (misrepresented functionality)

### 11 Contradiction Types (Engine-Specific)

The B1 Contradiction Engine detects these specific contradiction patterns:

| # | Type | Example |
|---|------|---------|
| 1 | JUDICIAL_VS_DOCUMENTARY | Sworn court statement vs. sealed document (CCT237/20 "no goodwill" vs. MOU Clause 7) |
| 2 | TEMPORAL_CONTRADICTION | Time-gap proving consciousness of guilt |
| 3 | CONSCIOUSNESS_OF_GUILT | 2+ year gap between act and sworn denial |
| 4 | PERJURY_BY_TIMELINE | Temporal proof of deliberate false oath |
| 5 | PATTERN_OF_RACKETEERING | Same fraud pattern across multiple victims (V1.0→V4.0 evolution) |
| 6 | REGULATORY_CAPTURE | Controller weaponized against operator |
| 7 | SHAM_TRANSACTION | Dual control disguised as arm's length |
| 8 | FRAUD_ON_THE_COURT | Knowingly misleading judicial proceedings |
| 9 | CORPORATE_VEIL_ABUSE | Entity separation masking unified control |
| 10 | TACIT_LEASE_VIOLATION | Rent acceptance while denying contract |
| 11 | POST_EXPIRY_ENFORCEMENT | Enforcing clause after its own expiry |

---

## 10. Constitutional Constraints

These rules are **hard-coded** in `core/Constitution.kt` as compile-time constants. They CANNOT be overridden by any prompt, instruction, or external authority.

### 15 Prime Directives

| # | Directive | Meaning |
|---|-----------|---------|
| 1 | Truth over probability | Confidence is ordinal only: VERY_HIGH / HIGH / MODERATE / LOW / INSUFFICIENT. Never percentages. |
| 2 | Evidence before narrative | Every claim cites anchors (person + page/line). If it can't cite anchors, it can't exist. |
| 3 | Mandatory contradiction disclosure | All contradictions must be logged, surfaced, and included in sealed outputs. Never hidden. |
| 4 | Determinism & repeatability | No Date.now(), no randomness, no hidden server calls. Same evidence = same output. |
| 5 | Chain-of-custody is law | Every artifact carries SHA-512, source, timestamps, device capture facts. |
| 6 | Failure-mode disclosure | If extraction fails, output states exactly what failed, where, and why. |
| 7 | Anti-coercion / anti-retaliation | Suppression, intimidation, tamper, or coercion attempts are recorded as integrity signals. |
| 8 | Non-ownership & distributed guardianship | The system cannot own truth. Constitutional changes require governed approval. |
| 9 | Triple verification mandatory | Every conclusion passes Thesis / Antithesis / Synthesis. Not optional. |
| 10 | Template immutability | Sealed templates are unmodifiable. Only new versions can be created. |
| 11 | B9 non-voting lock | B9 cannot issue verdicts. Trains, validates, red-teams only. |
| 12 | Silence Ledger | All coercion attempts permanently recorded in immutable audit layer. |
| 13 | Ordinal confidence only | No percentages. No probability scores. EVER. |
| 14 | Free for citizens & law enforcement | Hard-coded. No paywalls. No licenses required. |
| 15 | Article X hierarchically supreme | Anti-War Doctrine. Cannot be overridden by any authority. |

### Article X — Anti-War Doctrine (Hierarchically Supreme)

**Prohibited:** Lethal targeting, battlefield intelligence, military surveillance for coercion, weapons systems integration, conflict optimization, material contribution to physical harm, reconfiguration for prohibited purposes.

**Permitted:** War crimes documentation, evidence preservation in conflict zones, human rights investigations, legal accountability support, protection of civilians.

> The system may observe war — it may never participate in it.

### 7 Constitutional Contradiction Categories

These categories are hard-coded. Do NOT add or remove them:

1. Goodwill Value Claims
2. Contract Validity
3. Signature Status
4. Section 12B Arbitration
5. Compensation Demands
6. Perjury / Constitutional Court
7. Coercion & Fabricated Consent

---

## 11. Tech Stack & Dependencies

### Core Platform
- **Language:** Kotlin (business logic), Jetpack Compose (UI)
- **Build system:** Gradle with Kotlin DSL
- **Minimum SDK:** Android 8.0 (API 26)
- **Target SDK:** Android 14 (API 34)

### On-Device AI (llama.cpp via JNI)
| Component | Library |
|-----------|---------|
| LLM inference | llama.cpp (JNI bridge) |
| Report writer | Gemma 3 4B (Q4_K_M) |
| Chat (entry/mid) | PHI-3 Mini 3.8B or Command R 4B (Q3_K_S / Q4_K_M) |
| Chat (flagship) | Gemma 4 12B (Q4_K_M) |
| Audio transcription | Whisper.cpp (offline, deterministic) |

### Forensic Libraries
| Domain | Libraries |
|--------|-----------|
| **Document** | Tesseract OCR, PDF.js (bundled fonts), MuPDF |
| **Image** | OpenCV, ExifTool (metadata), ELA analysis |
| **Video** | FFmpeg (container hash, frame hash, GOP analysis) |
| **Audio** | Whisper.cpp (transcription), voice cloning detection |
| **Device** | ADB wrapper, SEON SDK (device fingerprinting) |
| **Crypto** | SHA-512 (built-in), OpenTimestamps (blockchain anchor) |
| **Network** | B7 OJRS module (SAFLII/PACER/BAILII search) |

### Cryptographic Sealing
- SHA-512 hashing (file integrity)
- OpenTimestamps → Bitcoin blockchain anchoring
- QR code generation (zxing) for verification
- AES-256-GCM encryption (evidence vault)

### External Integrations (Optional, Flagship Only)
- SAFLII (South African legal database)
- PACER (US federal court records)
- BAILII (UK/Ireland legal database)
- CourtListener (free US case law)

---

## 12. File Structure to Build

```
com.verumomnis.forensic/
│
├── core/                           # Constitutional foundation
│   ├── Constitution.kt             # 15 Prime Directives, Article X (COMPILE-TIME constants)
│   ├── DeviceTier.kt               # RAM-based model loading (entry/mid/flagship)
│   ├── ModelLoader.kt              # llama.cpp model loading, quantization selection
│   ├── Llm.kt                      # Unified LLM interface for G3, PHR3, G4
│   └── constitutional/             # ConstitutionalValidator — CI/runtime checks
│
├── engine/                         # The 9-Brain Engine
│   ├── NineBrainEngine.kt          # Brain orchestrator, voting, quorum
│   ├── ContradictionExtractor.kt   # B1: Contradiction detection engine
│   ├── DocumentBrain.kt            # B2: Document/metadata forensics
│   ├── CommunicationBrain.kt       # B3: Chat log analysis
│   ├── BehavioralBrain.kt          # B4: Evasion/gaslighting detection
│   ├── TimelineBrain.kt            # B5: Event sequence reconstruction
│   ├── FinancialBrain.kt           # B6: Transaction analysis
│   ├── LegalMapper.kt              # B7: Statute mapping + OJRS
│   ├── AudioBrain.kt               # B8: Audio/video forensics
│   ├── GuardianBrain.kt            # B9: Constitutional enforcement + red team
│   ├── ForensicService.kt          # Main entry point: ingest → scan → seal
│   └── TripleVerification.kt       # Thesis/Antithesis/Synthesis
│
├── crypto/                         # Cryptographic sealing
│   ├── EvidenceSealer.kt           # SHA-512 + blockchain anchor + QR
│   ├── Sha512.kt                   # Deterministic hashing
│   ├── VaultEncryption.kt          # AES-256-GCM evidence vault
│   └── OpenTimestampsService.kt    # Bitcoin blockchain anchoring
│
├── forensics/                      # Specialized forensic modules
│   ├── image/                      # B2 image analysis
│   │   ├── ImageForensics.kt       # EXIF, ELA, clone, screenshot detection
│   │   └── ExifAnalyzer.kt
│   ├── video/                      # B8 video analysis
│   │   ├── VideoForensics.kt       # Container hash, frame hash, GOP
│   │   └── FrameAnalyzer.kt
│   ├── audio/                      # B8 audio analysis
│   │   ├── WhisperBridge.kt        # Whisper.cpp JNI integration
│   │   └── VoiceAnalyzer.kt        # Synthetic audio detection
│   ├── document/                   # B2 document analysis
│   │   ├── OcrProcessor.kt         # Tesseract with bundled fonts
│   │   └── PdfExtractor.kt         # Deterministic PDF parsing
│   └── device/                     # Device forensics
│       └── DeviceForensics.kt      # ADB, SEON SDK integration
│
├── model/                          # Data models
│   ├── Evidence.kt                 # EvidenceArtifact, EvidenceAtom, Anchor
│   ├── Contradiction.kt            # Contradiction, Claim, Severity
│   ├── ScanResult.kt               # Sealed scan output (feeds LLMs)
│   ├── Report.kt                   # Forensic report structure
│   ├── ActorProfile.kt             # Per-person analysis
│   ├── Timeline.kt                 # Event sequence
│   ├── Finance.kt                  # Transaction data
│   └── Ots.kt                      # OpenTimestamps data
│
├── pdf/                            # Report generation
│   ├── SealedPdfGenerator.kt       # Deterministic PDF with seal footer
│   └── SealedPageRenderer.kt       # Per-page rendering with hash
│
├── ui/                             # Jetpack Compose UI
│   ├── VerumApp.kt                 # Root composable
│   ├── VerumViewModel.kt           # State management
│   ├── Theme.kt                    # verumglobal.foundation theme
│   ├── Type.kt                     # Typography
│   ├── StoryScreen.kt              # App story / onboarding
│   ├── ChatScreen.kt               # AI chat interface (PHR3/G4)
│   ├── ReportScreen.kt             # View sealed reports
│   ├── EmailScreen.kt              # Secure communication
│   ├── VaultScreen.kt              # Evidence vault browser
│   ├── TaxScreen.kt                # Tax analysis module
│   ├── MediaIngestor.kt            # Photo/audio/video upload
│   └── Components.kt               # Shared UI components
│
├── vault/                          # Evidence storage
│   └── EvidenceVault.kt            # Encrypted local storage
│
└── MainActivity.kt                 # Entry point
```

---

## 13. Reference Documents

Read these for deeper detail on specific components:

| Document | What It Covers |
|----------|---------------|
| **`PROMPT.md`** | The main prompt — how the app works, 15 Prime Directives, 11 contradiction types, data flow, coding rules |
| **`CONSTITUTION.md`** | Full binding constitutional governance — 8 Prime Directives, Triple Verification Doctrine, Article X, licensing |
| **`NINE_BRAIN_RULES.md`** | Brain-specific operational rules, voting table, v5.2.7 vs v6.0 comparison |
| **`B1_ENGINE.md`** | B1 Contradiction Brain — all 38 primary contradictions with Claim A/Claim B, severity algorithm, output format |
| **`B1_SOURCE_CODE.md`** | Complete 962-line contradiction engine source code — enums, dataclasses, 10 detectors, orchestrator |
| **`ONLINE_JUDICIAL_RETRIEVAL.md`** | How B7 searches court records during forensic scan, downloads sworn testimony, pairs with sealed documents |
| **`ON_DEVICE_LLM_ARCHITECTURE.md`** | 3-model system (Gemma 3, PHI-3/Command R, Gemma 4), device-tier loading, unrestricted communication rationale |
| **`IDENTITY_TRUST_SYSTEM.md`** | VITS v1.0.0 — user trust tiers, device identity, metadata fraud detection, under-text watermarks, identity QR codes |
| **`TEMPLATE_HISTORY.md`** | Template evolution from v5.1.1 to v5.2.8 — the v5.2.7 failure and v5.2.8 breakthrough documented |
| **`AGENTS.md`** | Rules for AI agents operating within the Verum Omnis framework |

---

## Signature Block

**System:** Verum Omnis Constitutional Forensic Platform  
**Version:** v5.2.8 (Contradiction Engine)  
**Document:** WHAT_THIS_APP_IS.md v1.0.0  
**Date:** 2026-07-13  
**Status:** BINDING — Master build reference

```
VERUM OMNIS
"Justice should not have a price tag. Truth can be computationally verified."
```
