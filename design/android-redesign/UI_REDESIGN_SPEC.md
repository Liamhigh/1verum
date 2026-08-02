# Verum Vault — Android UI Redesign Implementation Spec

**Design source of truth:** `Verum Vault Android.dc.html` (in this folder) — a Claude Design
handoff prototype. `landing-mockup.png` shows the finished landing screen. Recreate the
design's visual output in Jetpack Compose; do not copy the prototype's internal structure.
`android-frame.jsx` is only the phone-frame wrapper used by the prototype — ignore it.

**Functional source of truth:** the website (`Liamhigh/webdocsol`, `seal-document.html`).
Where the app and website differ in behaviour, the website wins. The app must reach feature
parity with the website's Document Sealing Service (modes, seal types, identity, password
protection, protected-PDF handling, certificates, plain-language forensic reports) with one
addition the website will never have: the per-document legal chat.

**Design tokens:** everything is already in `ui/theme/Theme.kt` and `DESIGN_LOCK.md`
(navy `#040D1B`, gold `#D4A843→#b8942a` gradients, steel blue `#4A7EC7`, blue border
`#1A2E52`, Cormorant Garamond display, mono labels, green `#22c55e`, red `#ef4444`).
The prototype uses exactly these values — no new palette entries are needed.

---

## App structure (target)

Three-tab bottom navigation (mono, uppercase, gold = active, steel blue = inactive):

| Tab | Screen | Replaces / reuses |
|-----|--------|-------------------|
| HOME | Landing | new (replaces `StoryScreen` as default entry; keep Story as first-run intro if desired) |
| VAULT | Evidence Vault (thread list) | rework of `VaultScreen` |
| VERIFY | Check a Seal | new thin screen — **verification itself stays on the website** (see below) |

Full-screen routes above tabs: Compose (new seal/scan), Pipeline/Thread (chat-style),
Constitution, Documents & Resources. Existing screens that stay reachable from menus:
Report, Timeline, Actor Profiles, Contradictions, Comparison, Email, Tax, Settings.

## Screen-by-screen

### 1. Landing (HOME tab) — `landing-mockup.png`
- Banner logo (`R.drawable.vo_banner`), "Truth for All" in Cormorant 300, one-line mission.
- Three stat cards computed from real vault state: **Sealed / Verified / Flagged** counts.
- Buttons: outlined "Verify Document" (→ VERIFY tab) and gold-gradient "Seal Document"
  (→ Compose in seal mode).
- "Recent Activity": last 2 vault threads (see Vault), "View All" → VAULT tab.
- Footer links: CONSTITUTION, DOCUMENTS (mono, steel blue).

### 2. Evidence Vault (VAULT tab)
- Header: small banner logo + "Vault" mono label; "Evidence Vault" Cormorant title.
- Folder chips: All / Raw / Processed / Sealed with live counts
  (map to vault storage areas: `/evidence/raw/`, `/evidence/processed/`, `/reports/sealed/`).
- Thread rows (chat-list style): 42dp rounded square avatar with document initial in
  Cormorant gold + status dot (green sealed-clean, red flagged), name, status line
  ("Sealed · SHA-512 verified" / "Fraud indicators found" / "Awaiting processing"),
  mono time + storage path caption, trash icon per row.
- "Empty Vault" (red, mono) with confirm dialog: "Sealed proofs already anchored
  on-chain are unaffected."
- Gold FAB "+" → New Document sheet.

### 3. New Document sheet (bottom sheet)
Three actions: **Seal Document** (gold accent, "Watermark, QR, SHA-512 fingerprint"),
**Forensic Scan** ("Runs the 9-Brain forensic engine (B1–B8)"), **Verify Document**
(→ VERIFY tab). Maps to existing pickers/flows in `VerumApp.kt`.

### 4. Compose screen ("New Seal" / "New Forensic Scan")
Website-parity form, exactly as in the prototype:
- Attach PDF (files) / Scan Document (camera) — dashed drop-style cards.
- Filename + optional note.
- Seal Type: Private (Free) / Commercial chips + Organisation Name + licence notice.
- "+ Add Sender Identity (optional)" collapsible: name, ID/passport, address, email,
  and the **identity-in-QR opt-in** with the exact warning ("Anyone holding the
  document can read this."). Identity default: private Seal Certificate only.
- Password protect (delivery receipt mode): min 8 chars + confirm, same copy as website.
  Output must be a standard password-protected PDF compatible with the website's
  `pdf-encrypt.js` (VO-DSS) so files open cross-platform.
- **Protected-input handling (website parity):** detect `/Encrypt` at attach time, show
  the same notice the website shows (sealed as-is, record on the certificate, print-to-PDF
  for a stamped copy), never a raw error.
- Gold CTA: "Seal Document" / "Run Forensic Scan".

### 5. Pipeline screen — the signature interaction
The seal/scan runs as a **conversation**: each step arrives as an engine chat bubble
(app-icon avatar) with a status dot (gold pulse = processing, green = complete,
red = flagged), mono step label, right-aligned state text, and description.
- Seal steps: GPS + Device → SHA-256 → OpenTimestamps → A4 Watermark → Clean QR →
  Seal Footer → Finalize → SHA-512. (Forensic mode inserts the Forensic Scan step
  as on the website.)
- Scan steps: B1–B8 bubbles with each brain's one-line description (from the prototype).
  A flagged run turns the responsible brain's bubble red with "FLAGGED".
- Summary bubble on completion: title (gold "Document Sealed" / red "Fraud Indicators
  Found"), SEAL ID, SHA-512 (green mono), fraud score box when flagged, QR thumbnail →
  QR detail modal (seal ID, hash, timestamp, GPS·device, verify URL), and
  **Share / Export sheet**: Share Sealed PDF, Share Verification Link
  (`verify.html?h=…`), Export .OTS Proof, Copy Seal ID.
- Completion fires a local notification (in-app banner in the prototype): title/body per
  outcome, tap → reopen the pipeline thread. Map `SealStage` / scan progress to bubbles.

### 6. Thread screen (reopened document) — includes the LEGAL CHAT
Same pipeline history as bubbles, then a divider — "Ask Verum AI about this document" —
then the chat: user bubbles right (blue tint), AI bubbles left with the app avatar.
**Every AI reply carries three chips** (this is constitutional, not decorative):
1. **Citation** — evidence anchor: page + SHA-512 prefix (`Page 1 · SHA-512 a1c825e8…`)
2. **Confidence** — ordinal only (VERY_HIGH/HIGH/MODERATE/LOW/INSUFFICIENT), colour-coded
3. **Statute** — the mapped legal reference (B7 output) or custody/constitution article
Wire to the existing on-device chat pipeline (`ChatScreen`/`VerumViewModel`); the chat may
only read evidence **after sealing** (existing rule). Rounded input + gold circular send.

### 7. Verify (VERIFY tab) — website is the hub
The design's own caption is binding: *"Verification runs against the sealed record at
verumglobal.foundation/verify.html"*. Implement the tab as a **front door, not a verifier**:
- "Tap to scan QR" → camera scan (existing `ScanSealScreen` machinery) → the QR already
  encodes the verify URL → open it in a Custom Tab.
- Paste SHA-512/Seal ID + gold VERIFY button → open
  `https://www.verumglobal.foundation/verify.html?h=<hash>` in a Custom Tab.
- No local verdicts are rendered in-app; the website's record is the single source of
  truth. (The prototype's inline result card is what the *website* will show.)

### 8. Constitution screen
Prototype layout: v6.0 FINAL badge, "Truth for All", motto quote with gold left border,
9/15/3x stat cards, Triple Verification doctrine, selected Prime Directives (III, IV, X),
access model rows, constitution seal card (VO-4FFEA8A806C1 + SHA-512 prefix). Reuse
content from `ConstitutionScreen`/`core/Constitution.kt`.

### 9. Documents & Resources screen
Four cards: Constitution v6.0 Final, VO-DSS-1.2 standard, Document Sealing Service
(→ Compose), Seal Verification Service (→ VERIFY tab). Foundation footer lines.

## Non-negotiables (Constitution)
- Findings are indicators, never determinations of guilt — every surface that shows a
  score or flag repeats this.
- No machine-readable text ⇒ NOT a clean result; failed scans disclosed, never hidden.
- Identity/GPS stay out of the public QR unless explicitly opted in.
- The seal proves integrity and time only.
- Forensic report output follows the website's plain-language format (IN PLAIN LANGUAGE
  box, aggregated structural notes, clean quotes, "How to read this report").

## Engineering notes
- Keep `VerumViewModel` as the single state holder; this is a UI restructure, not a
  pipeline rewrite. `SealDocumentScreen`'s website-parity work (modes, gold CTA,
  LE banner) migrates into Compose/Pipeline screens.
- Bottom navigation: plain Compose Row per the prototype (54dp), not Material NavigationBar
  defaults — match the design's colours/typography exactly.
- Build and test on-device (`./gradlew assembleDebug` + the manual checklist in the PR
  description). This spec was written in an environment without an Android SDK; nothing
  here has been compiled.
