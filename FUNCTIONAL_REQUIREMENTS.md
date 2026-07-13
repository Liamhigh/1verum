# Functional Requirements — Verum Omnis

**Document Purpose:** Defines exactly how every screen and function should behave. No ambiguity. No "should." Only "must."

**Last Updated:** 2026-07-13  
**Version:** v5.2.8

---

## FR1 — Evidence Upload Flow

### FR1.1 — File Selection
- The app MUST support selecting multiple files simultaneously (minimum 50 files).
- Supported formats: PDF, PNG, JPEG, HEIC, TIFF, MP3, WAV, M4A, MP4, MOV, AVI, TXT, CSV, ZIP (chat exports), EML, MSG (email).
- Maximum individual file size: 500MB.
- Total upload size per case: 2GB (flagship), 500MB (mid), 100MB (entry).
- Files MUST be copied into the Evidence Vault immediately upon selection.
- Original files MUST NOT be modified. All processing works on copies.

### FR1.2 — Evidence Ingestion
- Upon ingestion, each file MUST be SHA-512 hashed immediately.
- The hash MUST be stored in the evidence manifest before any processing begins.
- The Evidence Artifact ID MUST be derived solely from the content hash (first 16 bytes of SHA-512).
- Filename, size, and timestamp MUST NOT be part of the artifact ID.
- Metadata (filename, size, timestamp, MIME type) is stored separately from the content hash.

### FR1.3 — Evidence Vault
- All evidence MUST be stored encrypted with AES-256-GCM.
- The encryption key MUST be stored in Android Keystore (TEE or StrongBox if available).
- Evidence persists across app restarts until explicitly deleted by the user.
- Auto-delete: Evidence older than 90 days MAY be deleted if the user enables auto-cleanup.
- Law enforcement users: Evidence MUST NOT auto-delete. Must be manually purged.

---

## FR2 — Forensic Scan Flow

### FR2.1 — Scan Initiation
- User taps "Begin Forensic Scan" to start.
- The app MUST show a warning: "This scan will analyze all uploaded evidence. Large cases may take 30+ minutes. The app can run in background."
- Upon confirmation, the scan MUST begin immediately.

### FR2.2 — Scan Progress
- The app MUST display real-time progress showing:
  - Current active brain (B1-B9)
  - Percentage complete
  - Evidence artifacts processed / total
  - Estimated time remaining
  - Contradictions found so far (live counter)
- Progress MUST update at least every 2 seconds.
- User MUST be able to background the app during scan.
- A persistent notification MUST show scan progress when backgrounded.
- User MUST NOT be able to start a second scan while one is in progress.

### FR2.3 — Scan Cancellation
- User MUST be able to cancel a scan at any time.
- Cancellation MUST preserve all findings up to the point of cancellation.
- A partial ScanResult MUST be generated with a flag: "SCAN_CANCELLED_PARTIAL."
- No evidence is deleted on cancellation.

### FR2.4 — Scan Completion
- Upon completion, the app MUST:
  1. Generate the sealed ScanResult object
  2. Run Triple Verification on all findings
  3. Generate the Sealed Forensic Report (PDF)
  4. Anchor the report hash to blockchain via OpenTimestamps
  5. Store the sealed report in the Evidence Vault
  6. Show a completion summary screen
- The completion screen MUST show:
  - Total contradictions found (by severity)
  - Scan duration
  - Report seal ID
  - "View Report" and "Chat About Findings" buttons

---

## FR3 — Nine-Brain Engine Behavior

### FR3.1 — Brain Activation Order
Brains MUST activate in this order:
1. B2 (Document) — Verify integrity of all evidence files first
2. B8 (Audio/Media) — Transcribe audio/video, analyze images
3. B3 (Communication) — Parse chat logs, emails
4. B1 (Contradiction) — Extract all propositions, find contradictions
5. B5 (Timeline) — Reconstruct event sequence
6. B6 (Financial) — Analyze transactions
7. B4 (Behavioral) — Detect evasion patterns
8. B7 (Legal) — Map to statutes, OJRS if enabled
9. B9 (Guardian) — Validate constitutional compliance of all findings

### FR3.2 — Voting Rules
- A contradiction is ACCEPTED only if: B1 flagged it AND at least 2 other brains confirm.
- If B1 flags but only 1 other brain confirms: status = "INDETERMINATE_DUE_TO_CONCEALMENT."
- If B1 flags but 0 other brains confirm: status = "INSUFFICIENT."
- B9 NEVER votes. B9 only validates after the fact.
- B9 can VETO any finding that violates the Constitution.

### FR3.3 — Confidence Levels
All findings MUST use ordinal confidence:
- **CRITICAL** (8-10): Direct sworn contradiction or sealed vs judicial
- **HIGH** (6-7): Contradiction between sworn and contemporaneous evidence
- **MODERATE** (4-5): Informal communication contradiction
- **LOW** (2-3): Minor inconsistency, included only if pattern emerges
- **INSUFFICIENT** (0-1): Cannot determine — missing context

NO PERCENTAGES. NO PROBABILITY SCORES. EVER.

### FR3.4 — Consciousness of Guilt
- If temporal gap > 730 days between signed document and sworn contradictory statement:
  - Override severity to MINIMUM CRITICAL
  - Set consciousnessOfGuilt = true
  - Add to report Section 8: "Pattern of Racketeering Indicators"
  - Cite precedent: S v Saoli 2015 (2) SACR 49 (SCA)

---

## FR4 — Sealed Report Behavior

### FR4.1 — Report Format
- Output: PDF, A4 page size
- Font: Bundled deterministic font (no system font dependency)
- Every page MUST include a footer containing:
  - Seal ID (e.g., `seal-044d106d2ad1e01f04f8734d`)
  - Page SHA-512 hash (truncated: first 8 chars + ... + last 8 chars)
  - Constitution version (e.g., `v5.2.8`)
  - Timestamp in UTC
  - Page X of Y
- The first page MUST include a QR code containing the full report hash.

### FR4.2 — Report Sections
The report MUST contain these sections in this order:
1. Cover Page (case name, date, seal ID, QR code)
2. Executive Summary (total contradictions, top 5 critical)
3. Triple Verification Block (Thesis/Antithesis/Synthesis status)
4. Contradiction Ledger (all findings sorted by severity)
5. Actor Profiles (per-person analysis)
6. Timeline Reconstruction (chronological events)
7. Financial Analysis (if applicable)
8. Legal Exposure Map (statute-mapped findings)
9. Pattern Analysis (racketeering indicators if applicable)
10. Evidence Manifest (all artifacts with hashes)
11. Seal Verification Page (full SHA-512, blockchain anchor info)

### FR4.3 — Report Viewer
- User MUST be able to view generated reports in-app.
- Viewer MUST support pinch-to-zoom, scroll, and page navigation.
- The seal footer MUST be visible on every page.
- Tapping the QR code MUST open seal verification.

### FR4.4 — Report Sharing
- User MUST be able to share the sealed PDF via Android share sheet.
- Sharing MUST preserve the PDF exactly — no re-rendering.
- Shared PDF MUST maintain full seal integrity.
- Recipient can verify seal using any Verum Omnis app or web verifier.

---

## FR5 — AI Chat Interface

### FR5.1 — Model Selection
- Entry-tier (<4GB RAM): PHR3 (PHI-3 or Command R)
- Mid-tier (4-8GB RAM): PHR3 (full quality)
- Flagship (8GB+): G4 (Gemma 4)
- User MUST be informed which model is active (shown in chat header).

### FR5.2 — Chat Behavior
- The chat MUST only read the sealed ScanResult object.
- The chat MUST NEVER see raw evidence files.
- Every response MUST cite anchors when making factual claims.
- Anchor format: `{Person}, {Document} p.{Page}, l.{Line}`
- Legal interpretations MUST be flagged: "[HYPOTHESIS]"
- If the model is uncertain, it MUST say "INSUFFICIENT" rather than guess.
- Response generation MUST be deterministic (temperature=0, fixed seed).

### FR5.3 — Chat Input
- User can type natural language questions about their evidence.
- Supported: "What contradictions did you find?", "Explain the timeline gap", "What does Clause 7 mean?"
- The app MUST suggest common questions based on the scan results.
- Voice input MUST be supported (Android SpeechRecognizer).

### FR5.4 — Chat Restrictions
- The model MUST refuse to discuss evidence that hasn't been scanned.
- The model MUST refuse to alter, delete, or suppress findings.
- The model MUST refuse to provide legal advice ("I can explain the findings, but you should consult a lawyer for legal advice").
- The model MUST refuse requests to ignore the Constitution.
- B9 monitors all chat responses. Constitutional violations trigger a warning.

---

## FR6 — Cryptographic Sealing Behavior

### FR6.1 — Hash Generation
- Algorithm: SHA-512
- Input: Canonical JSON of the entire report (sorted keys, deterministic arrays)
- Output: 128-character hexadecimal string
- A single character change MUST produce a completely different hash.

### FR6.2 — Blockchain Anchoring
- Submit report hash to OpenTimestamps.
- Initial status: "PENDING" (awaiting Bitcoin confirmation).
- Confirmation can take minutes to hours.
- Once confirmed: status "CONFIRMED" with block number and timestamp.
- The app MUST check confirmation status periodically and update.

### FR6.3 — Seal Verification
- In-app: Re-compute SHA-512 of report, compare to stored seal.
- QR scan: Scan QR code on any sealed document, verify against blockchain.
- Web: `verumglobal.foundation/verify?hash={hash}` for non-app users.
- Verification MUST complete in <3 seconds.
- Result: "VERIFIED" (hash matches), "TAMPERED" (hash mismatch), or "PENDING" (not yet anchored).

---

## FR7 — Settings & Configuration

### FR7.1 — OJRS Toggle
- Setting: "Online Judicial Retrieval"
- Default: OFF (privacy-first)
- When ON: B7 searches SAFLII/PACER/BAILII during scan
- When OFF: B7 uses only cached precedents (~2,500 cases)
- User MUST be warned that OJRS sends search keywords (not document content) to court databases.

### FR7.2 — Model Management
- Show all downloaded models with version, size, and integrity status.
- Allow re-download if integrity check fails.
- Allow deletion of unused models to free space.
- Show which model tier the device supports.

### FR7.3 — Constitution Viewer
- Display the full Verum Omnis Constitution in-app.
- Searchable text.
- Show current constitution version and hash.
- Highlight the 15 Prime Directives.

### FR7.4 — Evidence Management
- List all stored cases with name, date, evidence count, status.
- Allow deletion of individual cases (with confirmation warning).
- Export all evidence for a case as encrypted ZIP.
- Import evidence from encrypted ZIP.

---

## FR8 — Error Handling

### FR8.1 — Scan Failures
- If a brain fails during scan: log the failure, continue with other brains.
- Failed brain findings MUST be marked: "B{N}_FAILED — results incomplete."
- User MUST be notified which brain failed and why.
- Partial results MUST still be saved.

### FR8.2 — Model Loading Failures
- If a model fails to load: show error, offer re-download.
- If device has insufficient RAM for selected model: auto-downgrade to lower quantization.
- If all models fail: app operates in "constitution-only" mode (rule checking without AI analysis).

### FR8.3 — Out of Memory
- If scan runs out of memory: pause scan, notify user, offer to reduce parallelism.
- Evidence processed so far MUST be preserved.
- App MUST NOT crash on OOM.

### FR8.4 — Corrupted Evidence
- If an evidence file is corrupted or unreadable: skip the file, log the error.
- Corrupted files MUST NOT crash the scan.
- User MUST be notified which files were skipped.

---

## FR9 — Anti-Weaponization

### FR9.1 — Keyword Detection
- B9 MUST scan all user inputs and document content for weaponization keywords.
- Trigger words include: "kill chain", "target acquisition", "artillery correction", "lethal force", "battlefield", "military targeting", "weapons system", "combat optimization".
- Detection is pattern-based, not AI-generated. No false positives from legitimate legal terms.

### FR9.2 — Weaponization Response
- If weaponization keywords detected:
  1. Immediately halt all processing
  2. Display full-screen hard stop: "CONSTITUTIONAL VIOLATION: WEAPONIZATION ATTEMPT"
  3. Disable all export and sharing functionality
  4. Log the violation in the Silence Ledger
  5. Anchor the violation log to blockchain
  6. Prevent the session from producing any report
  7. Show Article X and offer to contact the Verum Omnis team

### FR9.3 — Coercion Detection
- If evidence contains indicators of coercion against the user:
  - Flag as "COERCION_DETECTED" in the report
  - Add to Silence Ledger
  - Elevate severity of related contradictions by one level
  - Suggest reporting to authorities

---

## FR10 — Accessibility

- All screens MUST support Android TalkBack.
- Minimum touch target: 48dp x 48dp.
- Color contrast ratio minimum 4.5:1 for all text.
- Fonts MUST be scalable (respect system font size settings).
- No information conveyed by color alone.
- All images MUST have content descriptions.

---

## Requirement Summary

| Category | Requirements |
|----------|-------------|
| Evidence Upload | 4 |
| Forensic Scan | 4 |
| 9-Brain Engine | 4 |
| Sealed Report | 4 |
| AI Chat | 4 |
| Cryptographic Sealing | 3 |
| Settings | 4 |
| Error Handling | 4 |
| Anti-Weaponization | 3 |
| Accessibility | 6 |
| **TOTAL** | **40** |
