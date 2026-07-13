# Architecture — Verum Omnis System Design

**Document Purpose:** Complete architectural description of the Verum Omnis system. How all components connect, communicate, and enforce constitutional constraints.

**Last Updated:** 2026-07-13  
**Version:** v5.2.8  
**Pattern:** Offline-first, deterministic, constitutionally governed

---

## 1. Architectural Principles

These principles are non-negotiable. Every design decision must satisfy all of them.

1. **Offline-first**: Core forensic processing happens without network access. Network is optional augmentation only.
2. **Deterministic**: Same input + same constitution version = identical output. No randomness, no time-dependent behavior in core logic.
3. **Constitutionally bounded**: The Constitution is the supreme authority. No component can override it.
4. **Evidence never touches AI directly**: Raw evidence → Forensic Engine → Sealed ScanResult → AI. The AI only sees processed, sealed output.
5. **Cryptographic chain of custody**: Every evidence artifact is hashed at ingestion. Every report is hashed and blockchain-anchored.
6. **Free for citizens, paid for institutions**: Hard-coded in Constitution. No paywalls for individuals or law enforcement.

---

## 2. System Layers

```
┌──────────────────────────────────────────────────────────────┐
│  LAYER 6: PRESENTATION (Jetpack Compose)                      │
│  • Screens: Upload, ScanProgress, ReportViewer, Chat, Vault   │
│  • ViewModels: State management, UI logic                     │
│  • Theme: verumglobal.foundation visual identity              │
├──────────────────────────────────────────────────────────────┤
│  LAYER 5: AI INTERFACE (On-Device LLMs)                       │
│  • G3 (Gemma 3): Report generation from sealed ScanResult     │
│  • PHR3 (PHI-3/Command R): Chat on entry/mid devices          │
│  • G4 (Gemma 4): Chat on flagship devices                     │
│  • llama.cpp JNI: Model loading, inference, context mgmt      │
├──────────────────────────────────────────────────────────────┤
│  LAYER 4: FORENSIC ENGINE (Nine-Brain System)                 │
│  • 9 specialized brains (B1-B9)                               │
│  • Orchestrator: Voting, quorum, consensus                    │
│  • Triple Verification: Thesis/Antithesis/Synthesis           │
│  • ForensicService: Main entry point                          │
├──────────────────────────────────────────────────────────────┤
│  LAYER 3: FORENSIC MODULES (Specialized Analysis)             │
│  • Image: EXIF, ELA, clone detection, screenshot detection    │
│  • Video: FFmpeg container/frame hash, GOP analysis           │
│  • Audio: Whisper.cpp transcription, speaker diarization      │
│  • Document: Tesseract OCR, PDF parsing, metadata analysis    │
│  • Device: ADB, SEON SDK, device fingerprinting               │
├──────────────────────────────────────────────────────────────┤
│  LAYER 2: CRYPTOGRAPHIC & STORAGE                             │
│  • Evidence Vault: AES-256-GCM encrypted storage              │
│  • SHA-512: Hashing for all artifacts                         │
│  • OpenTimestamps: Blockchain anchoring                       │
│  • Sealed PDF: Deterministic report generation                │
│  • QR Code: Seal verification linking                         │
├──────────────────────────────────────────────────────────────┤
│  LAYER 1: FOUNDATION (Constitution & Core)                    │
│  • Constitution.kt: 15 Prime Directives, Article X            │
│  • DeviceTier: RAM-based capability detection                 │
│  • ModelLoader: llama.cpp initialization                      │
│  • ConstitutionalValidator: CI + runtime compliance checks    │
└──────────────────────────────────────────────────────────────┘
```

---

## 3. Data Flow Architecture

### 3.1 — Evidence Ingestion Flow

```
User selects files
       ↓
File Picker (multi-select, 50 files max)
       ↓
Evidence files copied to Evidence Vault (AES-256-GCM)
       ↓
SHA-512 hash computed for each file
       ↓
Evidence Artifact created (ID = content hash, filename excluded)
       ↓
Hash stored in evidence manifest
       ↓
Ingestion event logged to append-only ledger (JSONL)
       ↓
Files ready for forensic scan
```

### 3.2 — Forensic Scan Flow

```
User initiates scan
       ↓
ForensicService.scan() called with list of EvidenceArtifacts
       ↓
WorkManager starts background task with foreground service
       ↓
B2 (Document) verifies integrity of all files
       ↓
B8 (Audio/Media) transcribes audio/video, analyzes images
       ↓
B3 (Communication) parses chat logs, emails, detects deletions
       ↓
B1 (Contradiction) extracts propositions, finds contradictions
       ↓
B5 (Timeline) reconstructs event sequence from all evidence
       ↓
B6 (Financial) analyzes transactions, detects hidden payments
       ↓
B4 (Behavioral) detects evasion, gaslighting patterns
       ↓
B7 (Legal) maps findings to statutes, OJRS if enabled
       ↓
B9 (Guardian) validates all findings against Constitution
       ↓
Nine-Brain Orchestrator applies voting rules
       ↓
Triple Verification runs (Thesis/Antithesis/Synthesis)
       ↓
ScanResult object sealed (SHA-512 hash computed)
       ↓
G3 (Gemma 3) generates forensic report from sealed ScanResult
       ↓
PDF generated with per-page SHA-512 footer + QR code
       ↓
Report hash anchored to blockchain via OpenTimestamps
       ↓
Sealed report stored in Evidence Vault
       ↓
Scan complete — user notified
```

### 3.3 — Chat Flow

```
User opens chat screen
       ↓
App loads appropriate model (PHR3 for entry/mid, G4 for flagship)
       ↓
Sealed ScanResult passed to model as context
       ↓
User asks question about their evidence
       ↓
Model generates response anchored to sealed findings
       ↓
Response displayed with citations (person, page, line)
       ↓
Legal interpretations flagged as [HYPOTHESIS]
```

**Critical constraint:** The chat model NEVER sees raw evidence files. Only the sealed ScanResult.

### 3.4 — Seal Verification Flow

```
User opens sealed report
       ↓
App re-computes SHA-512 of PDF content
       ↓
Compare with stored seal hash
       ↓
Match → "VERIFIED" + show blockchain confirmation status
       ↓
Mismatch → "TAMPERED" + show diff details
```

---

## 4. Nine-Brain Engine Architecture

### 4.1 — Brain Interface

Every brain implements the same interface:

```kotlin
interface ForensicBrain {
    val brainId: BrainId           // B1, B2, ..., B9
    val votingStatus: VotingStatus // VOTING or NON_VOTING
    
    // Analyze evidence and produce findings
    suspend fun analyze(artifacts: List<EvidenceArtifact>): BrainOutput
    
    // Validate constitutional compliance of this brain's operation
    fun validateConstitution(): ValidationResult
}

interface BrainOutput {
    val findings: List<Finding>
    val confidence: Confidence     // VERY_HIGH, HIGH, MODERATE, LOW, INSUFFICIENT
    val anchors: List<Anchor>      // Evidence references for every finding
    val processingTimeMs: Long
}
```

### 4.2 — Orchestrator

The orchestrator manages brain execution:

```kotlin
class NineBrainOrchestrator(private val brains: List<ForensicBrain>) {
    
    suspend fun runFullAnalysis(artifacts: List<EvidenceArtifact>): ScanResult {
        // Execute brains in order (B2→B8→B3→B1→B5→B6→B4→B7→B9)
        val outputs = executeInOrder(artifacts)
        
        // B9 validates all outputs against Constitution
        val validated = b9.validate(outputs)
        
        // Apply voting rules
        val accepted = applyVoting(validated.findings)
        
        // Run Triple Verification
        val verified = tripleVerify(accepted)
        
        // Seal the result
        return seal(verified)
    }
    
    private fun applyVoting(findings: List<Finding>): List<Finding> {
        return findings.map { finding ->
            val confirmingBrains = finding.confirmations.size
            when {
                confirmingBrains >= 2 -> finding.copy(status = ACCEPTED)
                confirmingBrains == 1 -> finding.copy(status = INDETERMINATE)
                else -> finding.copy(status = INSUFFICIENT)
            }
        }
    }
}
```

### 4.3 — Parallel vs Sequential Execution

- **Sequential (mandatory):** B2 → B8 → B3 → B1 → B5 → B6 → B4 → B7 → B9
  - Each brain may depend on outputs from previous brains
  - B1 needs B2/B3/B8 output for proposition extraction
  - B5 needs B1 output for timeline anchoring
  - B7 needs B1/B4/B6 output for legal mapping
  - B9 needs ALL outputs for validation

- **Parallel (within a brain):** Each brain processes multiple evidence artifacts in parallel using Kotlin coroutines.

---

## 5. LLM Architecture

### 5.1 — Model Loading

```kotlin
class ModelLoader(private val context: Context) {
    
    fun loadModelsForTier(tier: DeviceTier): LoadedModels {
        return when (tier) {
            ENTRY -> LoadedModels(
                reportWriter = loadGemma3(Q3_K_S, contextSize = 2048),
                communicator = loadPHR3(Q3_K_S, contextSize = 4096),
                flagshipComm = null
            )
            MID -> LoadedModels(
                reportWriter = loadGemma3(Q4_K_M, contextSize = 8192),
                communicator = loadPHR3(Q4_K_M, contextSize = 8192),
                flagshipComm = null
            )
            FLAGSHIP -> LoadedModels(
                reportWriter = loadGemma3(Q4_K_M, contextSize = 32768),
                communicator = null,  // G4 replaces PHR3
                flagshipComm = loadGemma4(Q4_K_M, contextSize = 131072)
            )
        }
    }
    
    private fun loadGemma3(quant: Quantization, contextSize: Int): LlamaModel {
        return LlamaModel.load(
            path = "models/gemma3-${quant.name}.gguf",
            contextSize = contextSize,
            gpuLayers = detectGpuLayers(),
            temperature = 0.0f,  // Deterministic
            seed = 42             // Fixed seed
        )
    }
}
```

### 5.2 — Model Lifecycle

```
App Launch
    ↓
Detect device RAM → Determine tier
    ↓
Check if models downloaded
    ↓
No → Download from verified source + verify hash
    ↓
Yes → B9 validates model integrity (SHA-256 + signature)
    ↓
Valid → Load models into memory
    ↓
Invalid → Show error, offer re-download
    ↓
Models ready for use
    ↓
App Backgrounded → Models offloaded to disk (context preserved)
    ↓
App Foregrounded → Models reloaded from cache (<2s)
    ↓
App Terminated → Models unloaded, memory freed
```

### 5.3 — Unrestricted Communication Safety

The chat models are unrestricted because of architectural guarantees:

1. **Input is sealed:** The model receives only the ScanResult, not raw evidence
2. **Output is anchored:** Every response cites specific anchors from the seal
3. **Cannot alter findings:** The seal is immutable — the model has read-only access
4. **Cannot hallucinate contradictions:** The model can only reference what's in the seal
5. **No network access:** Models run entirely offline

---

## 6. Storage Architecture

### 6.1 — Evidence Vault

```
/data/data/com.verumomnis.forensic/vault/
├── manifest.jsonl           # Append-only evidence manifest
├── artifacts/               # Encrypted evidence files
│   ├── {hash1}.enc          # AES-256-GCM encrypted
│   ├── {hash2}.enc
│   └── ...
├── scans/                   # Sealed scan results
│   ├── {scanId}/
│   │   ├── scanresult.json  # Sealed ScanResult
│   │   └── report.pdf       # Sealed forensic report
├── models/                  # Downloaded LLM models
│   ├── gemma3-q4_k_m.gguf
│   ├── phi3-q3_k_s.gguf
│   └── ...
└── ledger.jsonl             # Append-only audit log
```

### 6.2 — Encryption

- Algorithm: AES-256-GCM
- Key: Stored in Android Keystore (hardware-backed if available)
- IV: Random 12 bytes per file, stored alongside ciphertext
- Tag: 128-bit authentication tag
- Key rotation: New key generated on first install, never rotated (to maintain seal validity)

### 6.3 — Room Database Schema

```kotlin
@Entity
data class EvidenceArtifact(
    @PrimaryKey val contentHash: String,  // SHA-512 (first 16 bytes)
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val ingestionTimestamp: Long,         // Metadata only, not part of ID
    val encryptionIv: ByteArray,
    val encryptionTag: ByteArray,
    val storagePath: String
)

@Entity
data class ScanResult(
    @PrimaryKey val sealId: String,       // "seal-" + timestamp + random
    val contentHash: String,              // SHA-512 of entire ScanResult
    val constitutionVersion: String,      // e.g., "v5.2.8"
    val startTimestamp: Long,
    val endTimestamp: Long,
    val totalContradictions: Int,
    val criticalCount: Int,
    val highCount: Int,
    val blockchainAnchor: String?,        // OpenTimestamps response
    val status: ScanStatus                // COMPLETE, CANCELLED, FAILED
)

@Entity
data class ContradictionEntity(
    @PrimaryKey val id: String,           // C-{category}-{number}
    val scanId: String,                   // FK to ScanResult
    val category: String,                 // One of 7 constitutional categories
    val severity: String,                 // CRITICAL/HIGH/MODERATE/LOW
    val claimAText: String,
    val claimBText: String,
    val temporalGapDays: Int,
    val anchorPerson: String,
    val anchorPage: Int,
    val verifiedBy: String                // Comma-separated brain list
)
```

---

## 7. Component Communication

### 7.1 — Dependency Graph

```
Presentation Layer (UI)
    ↓ (uses)
AI Interface (LLMs)
    ↓ (receives sealed ScanResult from)
Forensic Engine (9-Brain)
    ↓ (uses)
Forensic Modules (OCR, FFmpeg, etc.)
    ↓ (stores output in)
Cryptographic Layer (Vault, Hash, Seal)
    ↓ (enforced by)
Foundation (Constitution)
```

### 7.2 — Dependency Injection (Hilt)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object ForensicModule {
    
    @Provides
    @Singleton
    fun provideEvidenceVault(@ApplicationContext context: Context): EvidenceVault {
        return EvidenceVault(context, provideEncryptionKey())
    }
    
    @Provides
    @Singleton
    fun provideNineBrainOrchestrator(
        brains: List<ForensicBrain>,
        constitution: Constitution
    ): NineBrainOrchestrator {
        return NineBrainOrchestrator(brains, constitution)
    }
    
    @Provides
    fun provideModelLoader(@ApplicationContext context: Context): ModelLoader {
        return ModelLoader(context)
    }
}
```

### 7.3 — Cross-Component Rules

- **UI → Engine:** UI calls ForensicService.scan(). UI NEVER calls individual brains directly.
- **Engine → LLM:** Engine produces ScanResult. LLM receives ScanResult. Engine NEVER passes raw evidence to LLM.
- **LLM → UI:** LLM generates text. UI displays text. LLM NEVER writes to storage directly.
- **Engine → Vault:** Engine writes scan results and reports to vault through Crypto layer.
- **All → Constitution:** Every component checks Constitution before acting. B9 enforces globally.

---

## 8. Security Architecture

### 8.1 — Threat Model

| Threat | Mitigation |
|--------|-----------|
| Evidence tampering | SHA-512 at ingestion + blockchain anchor |
| Report tampering | Per-page hash in footer + QR code |
| AI hallucination | Deterministic models (temp=0) + evidence anchoring |
| Jailbreak/prompt injection | AI only sees sealed ScanResult, not raw evidence |
| Cloud data exposure | Offline-first. No evidence content leaves device. |
| Model tampering | B9 validates model hashes on every launch |
| Coercion against user | Silence Ledger + coercion detection in B4 |
| Weaponization | Article X keyword detection + hard stop |
| Device compromise | AES-256-GCM + hardware-backed keystore |
| Memory dump attack | Models offloaded when backgrounded |

### 8.2 — Trust Boundaries

```
[Untrusted: User Input]
         ↓
[Trusted: Evidence Vault] ← AES-256-GCM + Keystore
         ↓
[Trusted: Forensic Engine] ← Constitution-governed
         ↓
[Trusted: Sealed ScanResult] ← SHA-512 verified
         ↓
[Semi-Trusted: LLM] ← On-device, read-only access to seal
         ↓
[Trusted: Sealed Report] ← SHA-512 + Blockchain
```

---

## 9. Scalability Considerations

### 9.1 — Device Tiers

| Tier | RAM | Models | Max Evidence | Max Context |
|------|-----|--------|-------------|-------------|
| Entry | 2-4GB | G3(Q3) + PHR3(Q3) | 100MB / ~50 files | 4K tokens |
| Mid | 4-8GB | G3(Q4) + PHR3(Q4) | 500MB / ~200 files | 8K tokens |
| Flagship | 8GB+ | G3(Q4) + G4(Q4) | 2GB / ~1000 files | 128K tokens |

### 9.2 — Large Case Handling

- Cases >500MB: Process in chunks. Save intermediate results.
- Audio >30min: Transcribe in segments. Merge transcripts.
- Video >1GB: Extract key frames only. Skip full frame hashing.
- >1000 files: Batch processing with progress persistence.

---

## 10. Technology Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Platform | Android native (Kotlin) | Target demographic, offline capability |
| UI Framework | Jetpack Compose | Modern, declarative, MVVM-native |
| Architecture | MVVM + Repository | Testable, lifecycle-aware |
| DI Framework | Hilt | Compile-time safety, Android-integrated |
| Async | Coroutines + Flow | Structured concurrency, reactive streams |
| Storage | Room + Encrypted Files | Structured queries + secure file storage |
| Background | WorkManager + Foreground Service | Reliable background execution |
| LLM Runtime | llama.cpp via JNI | Best performance for on-device inference |
| Model Format | GGUF (llama.cpp native) | Standard format, quantized, fast loading |
| PDF Generation | Custom (bundled fonts) | Deterministic output, no system dependencies |
| Blockchain | OpenTimestamps → Bitcoin | Free, immutable, widely recognized |
| Hashing | SHA-512 | 128-char output, collision-resistant |
| Encryption | AES-256-GCM | Authenticated encryption, hardware-accelerated |
