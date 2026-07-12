# Verum Omnis — How This App Actually Works

## What This Is

Verum Omnis is a **forensic document scanner and sealer**. That's it. Users upload documents. The app scans them with a deterministic engine. It finds contradictions, tampering, timeline gaps, financial anomalies. Everything gets SHA-512 hashed, GPS-anchored, sealed into a PDF, and stored in an encrypted vault. The AI chat assistant only ever reads the SEALED case file — never raw uploads.

This is NOT a "fight fraud" app. It's NOT a justice platform. It's NOT a weapon. It is a tool: documents in, forensic analysis out, cryptographic seal on everything.

## The App Is a Chat Room

The whole app is built around a chat interface. The front "Story" screen leads into the **Chat** home screen. The chat input has a **+** button that opens a "Sealed actions" bottom sheet. From there the user can:

- Seal a document (upload + scan)
- Add photo/video evidence
- Verify a document (check hash against vault)
- Deep research (AI reads sealed case, helps with narrative)
- Draft sealed email (sent as PDF, tracked)
- Tax return calculation
- View report
- Open vault

Report / Email / Tax / Vault are sub-screens reached from the sheet or top bar. Back arrow always returns to chat.

## The Data Flow (This Is The Important Part)

```
User uploads document (via + menu)
    |
    v
ForensicService.ingest() — SHA-512 hash computed, GPS recorded
    |
    v
Document stored in EvidenceDocument list (in memory, in ViewModel)
    |
    v
User hits "Seal Case" (or auto-sealed on deep research)
    |
    v
ForensicService.scan() — runs the Nine-Brain Engine
    |
    v
NineBrainEngine.analyze() — deterministic analysis of ALL evidence
    |
    v
ScanResult produced: ForensicFindings + SealRecord
    |
    v
ReportGenerator.generate() — builds the ForensicReport
    |
    v
Everything stored in EvidenceVault (encrypted local storage)
    |
    v
AI chat (VerumViewModel.respond()) reads ONLY the sealed ScanResult
```

**The constitutional boundary**: Anything the user adds goes to the forensic engine FIRST. It gets sealed. Only THEN can the AI chat read it. Never feed un-sealed evidence into the chat context. This is enforced at the ViewModel level.

## The Nine-Brain Engine

Located in `engine/NineBrainEngine.kt`. Pure Kotlin, deterministic (same input = same output), fully unit-testable without a device.

| Brain | File | What It Does |
|-------|------|-------------|
| B1 | `ContradictionExtractor.kt` | Finds contradictions between statements. Uses explicit rules (fell-through vs proceeded, payment denial vs evidence of payment) plus subject-based pairing via `SubjectClassifier` polarity system. Every contradiction anchored to: person, page/line, applicable statute. |
| B2 | `EntityExtractor.kt` | Extracts people, companies, dates, Rand amounts from documents. |
| B3 | `BehavioralBrain.kt` | Detects gaslighting indicators, stress signals, isolation tactics, dismissive language. |
| B4 | B1+B2+B3 output | Cross-references contradictions with entities and behavioral patterns. |
| B5 | Timeline reconstruction | Rebuilds chronological sequence from extracted dates. |
| B6 | `TaxModule.kt` | SA company/individual tax calculations, accountant fee benchmarking, 20% recovered-fraud commission. |
| B7 | Legal framework mapping | Maps extracted facts to applicable statutes across jurisdictions. |
| B8 | `AudioBrain.kt` + `Transcriber.kt` | Audio tamper detection (sample rate inconsistencies, metadata modification, unnatural silence). Voice stress analysis. Speaker diarization. Transcription is pluggable: imported transcript, on-device Whisper, or no model. Transcript feeds back into B1 for contradiction cross-reference. |
| B9 | Triple verification | B9 is the third verifier in Triple-AI consensus. It cross-validates all other brains' findings. |

## Sealing

Located in `crypto/EvidenceSealer.kt` and `crypto/Sha512.kt`.

1. Every document gets SHA-512 hashed on ingest (`Sha512.hash(bytes)`)
2. The scan produces a `SealRecord` containing: seal ID, SHA-512 of the evidence corpus, timestamp, constitutional ruleset fingerprint, status
3. `EvidenceSealer.sealFromHash()` creates the seal with the Constitution ruleset embedded
4. `EvidenceSealer.verify()` checks: VERIFIED / TAMPERED / NO-CHAIN
5. Per-page seal footer format: `VERUM SEAL · {sealId} · SHA-512 {hash} · {timestamp} · Page {n} of {total}`

## PDF Generation

Located in `pdf/SealedPdfGenerator.kt` and `pdf/SealedPageRenderer.kt`.

- Draws directly to Android Canvas (not an external library)
- Watermark (`res/drawable-nodpi/watermark_portrait.png`) drawn at ~18% opacity as page-centred underlay BEFORE text
- Blue branded cover page with gold frame, logo badge, metadata block
- Exhibit pages: gold-framed image, SHA-512 anchor, GPS source (EXIF vs device), jurisdiction
- `SealedPdfContent.fromReport()` + `fromEmail()` handles pagination at 46 lines/page
- Footer on every page shows the seal

## Bitcoin Anchoring

Located in `blockchain/OpenTimestampsService.kt`.

- Separate step from sealing (keeps sealing deterministic for tests)
- POSTs SHA-256 of the evidence SHA-512 bytes to OpenTimestamps calendars
- Assembles standards-compliant detached `.ots` proof: `MAGIC\|0x01\|0x08\|digest\|calendar-timestamp`
- Verifiable by the `ots` client
- Offline = returns OFFLINE result (doesn't block)
- Called via `VerumViewModel.anchorSealToBitcoin()` (runs on `Dispatchers.IO`)
- Never call inside `EvidenceSealer`

## Photo/Video Evidence

Located in `engine/MediaEvidence.kt` and `ui/MediaIngestor.kt`.

- Images/videos picked via `ActivityResultContracts.OpenMultipleDocuments`
- ORIGINAL bytes preserved unaltered in vault (`evidence/raw`)
- SHA-512 fingerprinted
- GPS-anchored: EXIF `latLong` if present, else device GPS at upload
- Sealed, watermarked EXHIBIT is a derivative rendered onto its own PDF page
- Originals stay pristine for chain of custody
- EXIF/GPS/video metadata extraction is device-only; unit tests drive deterministic parts via `ForensicService.ingestMedia()`

## The Vault

Located in `vault/EvidenceVault.kt`.

- AES-256-GCM encrypted local storage
- Structure: `evidence/raw/` (original files), `evidence/meta/` (JSON manifests), `evidence/reports/` (generated reports), `evidence/seals/` (seal records)
- SHA-512 integrity manifest for everything

## Anti-Harassment Monitor

Located in `engine/AntiHarassmentMonitor.kt`.

- Monitors email draft frequency, bulk sending, repeat recipients, cooldown periods
- Escalation: ALLOW → WARN → BLOCK
- Even BLOCKED drafts are still sealed and logged (evidence preserved)
- 10 emails/hour max before escalation

## Tax Module

Located in `engine/TaxModule.kt`.

- SA company tax (full brackets)
- SA individual tax (full brackets with age-based rebates)
- Accountant fee benchmarking by jurisdiction
- Verum fee = 50% of local accountant benchmark

## UI Architecture

- Jetpack Compose with MVVM (`VerumViewModel` + `UiState`)
- `MainActivity.kt` hosts `VerumApp.kt` which is the single navigation host
- `ModalBottomSheet` for the + actions menu
- Theme: gold `#D1A547` on dark navy `#050C15`, Cormorant Garamond / Source Sans 3 / JetBrains Mono
- Brand fonts are variable TTFs in `res/font`; `Type.kt` opts in to `ExperimentalTextApi` for `FontVariation`

## Constitution

Located in `core/Constitution.kt`.

- All constants are COMPILE-TIME. Not config files. Not databases. Changing them requires recompiling from source, which invalidates every existing seal (hash mismatch).
- Key rules: 99% profit to Foundation, free for citizens, 9 brains exactly, 72h dead-man switch, anti-weaponization (Article X) is hierarchically supreme, ordinal confidence only (no percentages), evidence before narrative (no anchor = no sentence)

## Testing

- 75 unit tests: SHA-512 determinism, seal verification/tampering, contradiction extraction against AllFuels-style evidence, B8 audio tamper signals, media GPS anchoring, tax calculations, anti-harassment escalation, OTS proof structure, AES-GCM round-trip, dead-man switch 72h trigger
- Roborazzi screenshot tests for all 7 screens + + menu + anti-harassment states
- Run: `./gradlew testDebugUnitTest`
- Lint: `./gradlew lintDebug` (zero errors)
- No emulator needed — all forensic logic is pure Kotlin/JVM

## Package Structure

```
com.verumomnis.forensic/
  blockchain/     OpenTimestampsService.kt
  core/           Constitution.kt, DeviceTier.kt, ModelLoader.kt, Llm.kt
  crypto/         EvidenceSealer.kt, Sha512.kt, VaultEncryption.kt
  engine/         NineBrainEngine.kt, ContradictionExtractor.kt, EntityExtractor.kt,
                  SubjectClassifier.kt, BehavioralBrain.kt, AudioBrain.kt,
                  Transcriber.kt, TaxModule.kt, ReportGenerator.kt,
                  ForensicService.kt, EmailModule.kt, AntiHarassmentMonitor.kt,
                  EvidenceDocument.kt, AudioEvidence.kt, MediaEvidence.kt
  model/          Models.kt, Report.kt, Finance.kt, Email.kt, Audio.kt, Media.kt, Ots.kt
  pdf/            SealedPdfGenerator.kt, SealedPageRenderer.kt
  ui/             VerumApp.kt, VerumViewModel.kt, Theme.kt, Type.kt,
                  StoryScreen.kt, ChatScreen.kt, ReportScreen.kt,
                  EmailScreen.kt, VaultScreen.kt, TaxScreen.kt,
                  MediaIngestor.kt, Components.kt
  vault/          EvidenceVault.kt
  MainActivity.kt
```

## What To Do When Asked To Modify Code

1. **Respect the constitutional boundary**: Never route raw user uploads directly to the AI chat. Always go through `ForensicService.scan()` first.
2. **Keep it deterministic**: Same evidence = same findings = same seal. Pass a fixed `Instant` to `ForensicService.scan()` and `ReportGenerator.generate()` in tests.
3. **Don't break the seal format**: The per-page footer format and seal structure are part of the protocol. Changing them invalidates existing seals.
4. **Pure Kotlin for business logic**: The engine has no Android dependencies. Keep it that way.
5. **10-word system prompts max**: Per Constitution, no AI system prompt exceeds 10 words.
6. **No randomness, no Date.now()**: Use injected `Instant` parameters everywhere.
