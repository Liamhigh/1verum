# Verum Omnis — How This App Actually Works

## What This Is

Verum Omnis is an app that turns your phone into a forensic evidence scanner. It reads documents, finds contradictions and fraud, and produces court-ready reports — all on the device, with nothing sent to the cloud.

It was built because the justice system is too slow, too expensive, and too easy to manipulate. A petrol station operator in rural South Africa should have the same forensic power as a multi-billion dollar law firm. Now they do.

This is NOT a "fight fraud" app. It's NOT a justice platform. It's NOT a weapon. It is a tool: documents in, forensic analysis out, cryptographic seal on everything.

**Free for everyone.** Private citizens and police use Verum Omnis free of charge, permanently. Institutions — banks, insurance companies, corporations — pay a subscription because Verum saves them more money than it costs. This is written into the Constitution (v6.0 Final) as a hard-coded, compile-time constant. Changing it requires recompiling from source, which invalidates every existing seal.

**It works in every jurisdiction on Earth** because the core engine is contradiction-based. It does not require any single country's legal code to function. It extracts contradictions from the evidence itself — false statements, conflicting claims, actions that contradict words — anchors each one to a person, a page/line reference, and the local statutes that apply wherever the evidence was captured. A contract dispute in Dubai, a tax case in London, a labour matter in São Paulo — same engine, same process, different statutes.

## Real-World Proof: The AllFuels Case

In the AllFuels case, Verum found **111 contradictions** across 528 pages of evidence — including contradictions between what AllFuels swore to the Constitutional Court in 2021 and what their own documents showed from 2018. A human lawyer might spot 10. Verum found 111.

The sealed evidence was accepted by the **Port Shepstone Magistrate's Court** (Case H208/25, October 2025). SAPS has opened criminal cases based on Verum findings. The system meets the **Daubert Standard**, the **ECT Act**, and **ISO 27037**.

## Real-World Proof: The GreenSky Case (Cross-Border)

The GreenSky matter involved **three jurisdictions simultaneously**: UAE (RAKEZ Case #1295911), South Africa (SAPS CAS 126/4/2025), and a Hong Kong client.

- **Parties**: Liam Highcock (50% shareholder, UAE), Marius Nortjé (50% shareholder, UAE), Kevin Lappeman (SA)
- **Evidence**: Memorandum of Association, shareholder agreement, email chains, WhatsApp screenshots, invoices, witness statements, Gmail archive attempt logs

**What the engine extracted:**

| # | Contradiction | Type | Anchored To |
|---|--------------|------|-------------|
| 1 | Kevin dictated the email on 7 March, then called it "rude" on 9 March | Action vs Words | Kevin, WhatsApp log + email chain, UAE CCL Art 84 |
| 2 | Marius said the Hong Kong deal "fell through" but later admitted Kevin completed it | Direct Negation | Marius, email 6 Apr, UAE CCL Art 110(2) |
| 3 | Client confirmed "Thanks for the invoice" on 8 Mar — contradicts "no order existed" | Document Internal | Invoice SL001 + client reply, UAE CCL Art 93 |
| 4 | Termination dated 1 Apr, but order completed 13 Mar — agreement was still active | Temporal Shift | Termination notice + MOA, UAE CCL Art 22 |
| 5 | Kevin refused to help with client communication ("Must I do the marketing?") despite agreement assigning pricing to him | Role Inconsistency | WhatsApp log, Shareholders Agreement |
| 6 | Marius claimed Liam "refused meetings since March 9" — but email chain shows 4+ private meeting requests from Liam were ignored | Direct Negation | Email chain Section 14, UAE CCL Art 22 |
| 7 | Kevin blind-copied on client emails without Liam's knowledge — covert monitoring | Implied Contradiction | Gmail headers, UAE Cybercrime Law |
| 8 | Device "SCAQUACULTURE" attempted Gmail archive — unauthorized data access | Document Internal | Google security log, UAE Cybercrime Law |
| 9 | Marius changed reasons for exclusion: first "rude emails", then "no purchase order", then "deal fell through" | Temporal Shift | Email chain, UAE CCL Art 84 |
| 10 | Kevin told client he was "still negotiating" after invoice was already accepted and paid | Direct Negation | Client email + invoice, Commercial Fraud |

**Legal mapping output:**
- Shareholder Oppression — UAE CCL Art 110(2)
- Breach of Fiduciary Duty — UAE CCL Art 84
- Fraudulent Concealment — Federal Decree Law No. 4/2020
- Cybercrime — Federal Decree Law No. 34/2021
- Discriminatory Conduct — RAKEZ regulations

All anchored to person, page, and statute — then sealed. The sealed report was submitted to RAKEZ and SAPS.

## How It Works — Step by Step

### Step 1: You Upload Your Documents

Take a photo, upload a PDF, import a voice recording, or share a chat export. Verum accepts almost everything — photos, PDFs, emails, WhatsApp chats, audio files, video, even ZIP files containing hundreds of documents. Every document is stored in an encrypted vault on the phone. Nobody else can access it. Not Verum. Not anyone.

In code: `ForensicService.ingest()` via `MediaIngestor.kt` (`ui/MediaIngestor.kt`).

### Step 2: GPS and Timestamp Are Captured

The exact moment you upload, Verum records where you are (GPS coordinates) and when it happened. This creates an unbreakable chain of custody. The court can see: this document was uploaded at this place, at this time, by this person. It hasn't been tampered with since.

In code: GPS from EXIF (`latLong`) if present, else device GPS at upload. Stored in `EvidenceDocument` (`engine/EvidenceDocument.kt`).

### Step 3: Nine Brains Analyse Everything

Verum doesn't have one AI. It has nine — each one a specialist. They all read the documents at the same time, looking for different things:

| # | Brain | What It Finds | Example |
|---|-------|--------------|---------|
| B1 | **Contradiction Brain** (`ContradictionExtractor.kt`) | When someone says two opposite things. Uses subject polarity matching (positive vs negative assertions on the same topic). Every contradiction anchored to person + page/line + applicable statute. | "We never had a contract" vs collecting 87 months of rent. "I never signed" vs here's your signature. |
| B2 | **Document Brain** (`EntityExtractor.kt`) | Forgeries, metadata tampering, creation-date anomalies. | A bank statement created with Photoshop instead of a bank's system. A PDF "created" 14 days BEFORE the events it claims to record. |
| B3 | **Communications Brain** | Deleted messages, suspicious gaps in chat threads, sequence breaks. | A 3-week gap in WhatsApp right after the incriminating email. Messages numbered 12, 13, 15 — where's 14? |
| B4 | **Behavioral Brain** (`BehavioralBrain.kt`) | Gaslighting, manipulation, emotional abuse patterns in text and voice. | Speaker A (Desmond): "I'm mentally broken." Speaker B (Marius): "Calm down, it's just business." |
| B5 | **Timeline Brain** | Reconstructs what happened when. | A document created 14 days BEFORE the events it claims to record. Three "final" deadlines for the same thing. |
| B6 | **Financial Brain** (`TaxModule.kt`) | Hidden payments, duplicate invoices, fraud amount calculation. | An invoice for R2.3M with no supporting delivery note. VAT charged but not declared. |
| B7 | **Legal Brain** | Maps everything to actual laws. Auto-detects jurisdiction from GPS + content. | South African Common Law, UAE CCL, US Federal codes, EU GDPR, UN UNCAC. Searches online court records to find contradictions between sworn testimony and documents. |
| B8 | **Audio Brain** (`AudioBrain.kt` + `Transcriber.kt`) | Transcribes voice recordings, identifies who said what, detects audio tampering. | Speaker diarization: "Speaker A (Desmond): I'm mentally broken. Speaker B (Marius): Calm down." Sample rate inconsistencies indicating splicing. |
| B9 | **R&D Brain** | Checks all the other brains' work. Makes sure nothing was missed. **Does not issue verdicts.** | Cross-validates B1-B8 findings. Flags coverage gaps. Suggests additional checks. |

All brains run in parallel via `NineBrainEngine.analyze()` (`engine/NineBrainEngine.kt`). Pure Kotlin. Deterministic (same input = same output). No Android dependencies. Fully unit-testable without a device.

### Step 4: Triple Verification

Every finding must pass three independent checks before it appears in the report:

- **Thesis**: What does the evidence actually say?
- **Antithesis**: What could contradict it? What are the alternative explanations?
- **Synthesis**: What survives both checks?

If two out of three agree, the finding is accepted. If they don't agree, it gets flagged for human review. The system would rather say "I don't know" than be wrong.

Confidence is **ordinal only**: VERY_HIGH, HIGH, MODERATE, LOW, or INSUFFICIENT. No percentages. No probability scores.

In code: `TripleVerification` object, called within `NineBrainEngine.analyze()`.

### Step 5: The Report Is Generated

Verum produces a professional forensic report that any court in the world can accept:

- Every contradiction found — with exact page number and document reference
- Every forgery or tamper signal — with visual screenshots showing the anomaly
- Timeline reconstruction — what happened, when, in what order
- Financial analysis — how much fraud, with full calculation audit trail
- Legal mapping — which laws were broken, with statute citations
- Behavioral analysis — gaslighting patterns, stress signals
- Audio transcripts — who said what, with timestamps
- Confidence ratings for every finding — ordinal only

In code: `ReportGenerator.generate()` (`engine/ReportGenerator.kt`), rendered via `SealedPdfGenerator.kt` + `SealedPageRenderer.kt`.

### Step 6: The Document Is Sealed

Before the report leaves the phone, it is cryptographically sealed:

- **SHA-512 hash** computed — a unique fingerprint of the entire document (`Sha512.hash()`, `crypto/Sha512.kt`)
- **Hash anchored to the Bitcoin blockchain** via OpenTimestamps — proving the document existed at a specific point in time (`OpenTimestampsService.kt`, `blockchain/`)
- **Watermark applied to every page** — the Verum Omnis watermark at ~20% opacity, behind the text (`res/drawable-nodpi/watermark_portrait.png`)
- **Seal footer on every page** — `VERUM SEAL · {sealId} · SHA-512 {hash} · {timestamp} · Page {n} of {total}`

Once sealed, the document cannot be altered without breaking the seal. Anyone can verify its authenticity by uploading it to verumglobal.foundation.

In code: `EvidenceSealer.sealFromHash()` (`crypto/EvidenceSealer.kt`).

### Step 7: You Share It

The sealed report can be emailed directly to lawyers, police, courts, or regulators. The email is monitored by an anti-harassment system — it won't let you send too many emails to the same person, preventing misuse (10 emails/hour max, escalation: ALLOW → WARN → BLOCK). The recipient receives a tamper-proof, court-ready forensic document. They can verify it on the blockchain. They can check every citation against the original evidence. They can take it straight to court.

In code: `EmailModule.kt` + `AntiHarassmentMonitor.kt` (`engine/`).

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

**The constitutional boundary**: The AI chat assistant (`VerumViewModel.respond()`) reads ONLY the sealed `ScanResult`. Never raw uploads. Anything the user adds goes through `ForensicService.scan()` FIRST. It gets sealed. Only THEN can the AI chat read it. This is enforced at the ViewModel level. Never route raw user uploads directly to the AI chat.

## Jurisdiction Detection

The engine auto-detects jurisdiction from:
- **GPS coordinates** of the evidence capture
- **Content keywords** (statute names, court references, legal terms)
- **Currency symbols** (R, AED, $, €, £, etc.)

Currently mapped jurisdictions:
- **South Africa** — Common Law, Companies Act, POCA, PPA, Cybercrimes Act
- **UAE** — CCL, Cybercrime Law, Commercial Fraud, RAKEZ regulations
- **United States** — Federal codes (USC), state law references
- **European Union** — GDPR, EU Directives
- **United Nations** — UNODC Convention, UNCAC
- **International / Unknown** — Falls back to general contradiction extraction without statute mapping

**New jurisdictions are added by extending the legal mapping rules in B7.** The contradiction engine itself requires no changes — it works everywhere automatically.

## What Makes Verum Different

- **It's on your phone.** Everything happens on the device. Your documents never leave your phone unless you choose to share them. No cloud processing. No data harvesting. No surveillance.
- **It's court-validated.** The Port Shepstone Magistrate's Court accepted Verum sealed evidence (Case H208/25, October 2025). SAPS opened criminal cases based on Verum findings. Meets Daubert Standard, ECT Act, and ISO 27037.
- **It finds what humans miss.** 111 contradictions across 528 pages in the AllFuels case — including contradictions between what was sworn to the Constitutional Court in 2021 and what documents showed from 2018.
- **It's free for citizens.** Private individuals and police officers pay nothing. Never. Institutions pay because Verum saves them money.
- **It can't be weaponized.** Article X of the Constitution — the Anti-War Doctrine — prohibits any use of Verum for lethal targeting, battlefield intelligence, or weapons systems. Truth systems exist to expose harm, not execute it. This is hierarchically supreme and cannot be overridden.

## The Constitution Behind It

Every Verum output carries the Constitution embedded in its seal. The Constitution governs how the AI behaves — not through long system prompts, but through hard-coded rules that cannot be changed:

- 99% of revenue goes to the Verum Foundation, which repairs broken infrastructure worldwide
- 1% covers operations
- The Constitution is immutable — no amendments, no overrides, no exceptions
- Anyone can read it at verumglobal.foundation

All constants in `core/Constitution.kt` are **COMPILE-TIME**. Not config files. Not databases. Changing them requires recompiling from source, which invalidates every existing seal (hash mismatch).

Key rules: 9 brains exactly, 72h dead-man switch, anti-weaponization (Article X) is hierarchically supreme, ordinal confidence only (no percentages), evidence before narrative (no anchor = no sentence), 10-word max system prompts.

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
7. **Jurisdiction-agnostic**: The contradiction engine works everywhere. New jurisdictions are added by extending B7's legal mapping, not by changing B1.
8. **Free for citizens, paid for institutions**: This is a constitutional rule. Don't add paywalls for private individuals or police.
9. **Anti-weaponization is supreme**: Article X cannot be overridden. Don't add features that could be used for lethal targeting or battlefield intelligence.
