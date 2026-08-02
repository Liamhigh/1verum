# Android UI Redesign — Build Spec (Verum Omnis)

**Status:** authoritative spec for the Android UI rebuild.
**Design source:** `docs/design/Verum-Vault-Android.dc.html` (Claude Design mock-up, read it top to bottom — it is the pixel reference).
**Branch:** `claude/placeholder-rbqx8w`. Build with `./gradlew assembleDebug`; report `BUILD SUCCESSFUL` before any merge.

This is a **UI redesign + wiring** job, not a new app. The engine, sealing, vault and
chat backends already exist (`NineBrainEngine.kt`, `VerumContradictionEngine.kt`,
`ContradictionEngineUpdater.kt`, `EvidenceVault.kt`, `FindingsJsonEmitter.kt`, `ChatScreen.kt`,
`SealDocumentScreen.kt`, `VaultScreen.kt`, `ConstitutionScreen.kt`, `VerifyDocumentScreen.kt`,
`ScanHomeScreen.kt`). Reskin and re-wire these to match the mock-up; do not rewrite the engine.

---

## 0. Design tokens (from the mock-up, match exactly)

| Token | Value |
|---|---|
| Background (screen) | `#040D1B` |
| Panel / card bg | `rgba(15,52,96,0.08)` on borders `rgba(26,46,82,0.5)` |
| Gold (primary action) | `#D4A843` (gradient `linear-gradient(135deg,#D4A843,#b8942a)`) |
| Blue (links/labels) | `#4A7EC7` |
| Off-white (headings) | `#F8F9FA`; body `#D5D8DD`; muted `#8a93a3` / `#3d4c63` |
| Green / red state | `#22c55e` / `#ef4444` (`#F87171` text) |
| Serif (display) | Cormorant Garamond (300–600) — already in `res/font` |
| Mono (labels/hashes) | Courier New / monospace, uppercase, letter-spacing `0.06–0.15em` |
| Corner radius | cards 12–16px, bubbles `3px 14px 14px 14px` (AI) / `14px 14px 3px 14px` (user), FAB 18px |
| Pulse anim | active pipeline dot: `vo-pulse 1.4s` gold glow |

Keep `Theme.kt` / `colors.xml` as the single source; add any missing tokens there.

---

## 1. Branding rules (locked)

- **The chat/AI is called "Verum Omnis"** everywhere. Remove every user-facing "Phi-3",
  "Forensic Engine", "Guardian", "9-Brain Research Assistant" label from responder identity.
  Header title = **Verum Omnis**; subtitle may read *"On-device forensic AI"* (mono, blue).
- **Chat avatar = the blue circle logo** → existing drawable `vo_badge.png`
  (navy circle + globe), clipped to a circle (`Modifier.clip(CircleShape)`), 26dp in message
  rows, 22dp in the notification. Do **not** use the wordmark asset — it is illegible small.
- Banner/logo on landing & vault header = `vo_banner.png`.
- Never surface the underlying model name to the user. Internally it is still Gemma 3 + the
  deterministic engine; the human sees only "Verum Omnis".

---

## 2. Screen map (mock-up → Kotlin)

| Mock-up screen (`isX`) | Build in / adapt |
|---|---|
| `isLanding` — hero, stats, CTAs, Ask Verum Omnis card, Recent Activity | new `LandingScreen.kt` (or top of `VerumApp.kt` nav) |
| `isHome` — Evidence **Vault**, folder chips, delete, Empty Vault, FAB | `VaultScreen.kt` |
| `isSheet` — New Document (Seal / Forensic Scan / Verify) | bottom sheet in `VaultScreen.kt` |
| `isCompose` — filename, note, seal type, identity, **password**, payment gate | `SealDocumentScreen.kt` |
| `isPipeline` — stepped green-lights + summary card | `ScanHomeScreen.kt` (green-lights already landed in `0cb10a7`) |
| `isThread` — reopened doc: steps + per-document Verum Omnis chat | `ReportScreen.kt` + `ChatScreen.kt` |
| `isAiChat` — **standalone** Verum Omnis chat, attach vault docs, Deep Research/Email/Affidavit | `ChatScreen.kt` |
| `isConstitution` — in-app constitution | `ConstitutionScreen.kt` |
| `isDocuments` — Documents & Resources | new small screen or section |
| `isVerify` — scan QR / paste hash → website, chain of custody | `VerifyDocumentScreen.kt` |
| tab bar Home / Vault / Verify | `VerumApp.kt` scaffold |

---

## 3. Per-screen requirements

### 3.1 Landing (`isLanding`)
- `vo_banner.png`, serif **"Truth for All"**, hero subhead verbatim from the mock-up.
- Three stat tiles: **Sealed / Verified / Flagged** (gold / green / red counts) — bind to real
  vault counts, not literals.
- CTAs: **Verify Document** (outline) + **Seal Document** (gold).
- **"Ask Verum Omnis"** card → opens standalone chat (§3.7). Copy: *"Chat with the on-device
  forensic engine across your sealed vault."*
- Recent Activity list (2 items) + View All → Vault.
- Footer links: **Constitution**, **Documents**.

### 3.2 Vault (`isHome`) — organization + delete
- Header: `vo_banner.png` + serif **"Evidence Vault"**.
- Folder chips **All / Raw / Processed / Sealed** with live counts (mock-up `folders`).
- **Per-item delete** (🗑, `deleteThread`) and **Empty Vault** with the red confirm dialog
  (`confirmEmptyVault`). Wire to `EvidenceVault.kt` delete APIs; deleting removes on-device
  copies only (say so — on-chain anchors are unaffected, per the dialog copy).
- FAB (+) opens the New Document sheet.
- **GAP TO ADD — scan-set grouping (see §4.1).** Each row is a *scan set*, not a loose file.

### 3.3 New Document sheet (`isSheet`)
Three actions: **Seal Document** (watermark/QR/SHA-512), **Forensic Scan** (runs the engine),
**Verify Document**. This is the "seal **with** vs **without** forensic scan" split the user asked for.

### 3.4 Compose / Seal (`isCompose`)
- Filename + **Note** fields.
- **Seal Type**: Private (free) / Commercial. Commercial → Organisation name + licence notice,
  and on confirm shows the **payment gate** (SA R750 / SADC R500 / Intl $50) mirroring the
  website's Stripe tiers. Private citizens & law enforcement seal free.
- **+ Add Sender Identity**: name / ID / address / email, plus **"include identity + GPS in the
  public QR"** opt-in (default off — private by default).
- **Password protect** toggle → password + confirm (min 8). Wire to the existing
  encrypted-PDF path. Never re-encrypt an already-encrypted input (see webdocsol precedent).
- CTA label switches Seal Document / Run Forensic Scan by mode.

### 3.5 Pipeline (`isPipeline`) — green-lights
- Chat-thread layout, avatar = `vo_badge.png`, one bubble per step with a status dot
  (pending → processing gold-pulse → complete green / flagged red).
- **SEAL_STEPS** (8): GPS + Device → SHA-256 → OpenTimestamps → A4 Watermark → Clean QR →
  Seal Footer → Finalize → SHA-512.
- **SCAN_STEPS** = the real engine, surfaced as B1–B8 (Contradiction, Document Forensics,
  Communications, Linguistics & Deception, Timeline & Geolocation, Financial, Legal Mapping,
  Audio & Voice). **These must be driven by real `NineBrainEngine` progress, not a timer** — the
  reported "scan hangs" bug is fixed by wiring real callbacks + a visible failure state, never a
  spinner with no end.
- Summary card: SEAL ID, SHA-512, fraud panel when flagged (real score/keywords), QR tile →
  QR detail (incl. **GPS · Device** field), Share / Export sheet.

### 3.6 Thread (`isThread`)
Reopen a scan set: show the completed steps + the summary, then a **per-document Verum Omnis
chat** grounded in that document. Each AI reply carries citation + ordinal confidence + candidate
statute chips. Keep the "indicator, not a determination" framing (Prime Directive).

### 3.7 Standalone chat (`isAiChat`) — **no scan required**
- Reachable from the landing card with an **empty vault** — the user asked to "talk to the Legal
  AI without having something scanned". Works standalone.
- Attach sealed docs from the vault (📎) as grounding context; only **sealed** docs are selectable
  (their SHA-512 guarantees content). Show attached-context chips with remove (✕).
- Action menu (+): **Deep Research Report**, **Draft Email**, **Draft Affidavit**, Attach Sealed
  Document. Wire to `DeepResearchEngine.kt` / `ChatScreen` logic.
- Header title **Verum Omnis**, avatar `vo_badge.png`.

### 3.8 Constitution (`isConstitution`) & Documents (`isDocuments`)
In-app readable constitution (v6.0 Final) and a Documents & Resources list. Users must be able to
read the constitution offline in-app.

### 3.9 Verify (`isVerify`)
Scan QR or paste SHA-512 / Seal ID → opens `verumglobal.foundation/verify.html?h=…`; render the
chain-of-custody list when available.

---

## 4. Backend gaps the UI must not drop

### 4.1 Vault = grouped scan sets (one entry, 3 files)
Every scan produces **{ sealed original file, sealed forensic report PDF, findings JSON }**. Store
and display these as **one expandable scan set**, not three loose files. Expanding shows the three
artifacts with individual open/share; the set carries one seal ID, date, folder and fraud state.
Update `EvidenceVault.kt` persistence + `VaultScreen.kt` accordingly.

### 4.2 Seal the original into the vault
The original scanned/attached file is sealed and stored in the set alongside the report + JSON —
not discarded after scanning.

### 4.3 Hybrid engine (Gemma 3) — keep all four behaviours
1. Deterministic engine (same detectors as the website) runs first.
2. **Gemma 3 catches contradictions the engine missed** (`G3CandidateRegistry` — candidates are
   marked *G3-RAISED CANDIDATE — PENDING VERIFICATION*, never silently promoted).
3. **Gemma 3 can feed findings back to strengthen the engine** (`ContradictionEngineUpdater.kt`)
   so the miss isn't repeated.
4. **Gemma 3 writes the human narrative**, and the **raw engine findings are printed alongside the
   narrative for auditing** — the narrative never replaces the deterministic findings.

### 4.4 GPS actually captured
Request location permission and record real coordinates at seal time; show them in the seal
metadata / QR detail. The mock-up's GPS field must be backed by a real fix (or an explicit
"location unavailable" state), not a placeholder.

### 4.5 Legal mapping = SA/UAE
B7 / statute chips use the real SA + UAE legal engine (`JurisdictionService.kt`), cross-border
aware. The mock-up's "Fraud Act 2006 (UK)" is placeholder copy only.

### 4.6 Report format = the shared GHRP contract (firebase is canonical)
The forensic report Gemma 3 writes on Android follows the **same** contract as the firewall:
`firebase/REPORT_FORMAT_SPECIFICATION.md` (19 mandated sections), the section validator in
`reportStructure.ts`, the `G3_SYSTEM_PROMPT.md`, and the section-by-section
`firebase/ALLFUELS_GHRP_MAPPING.md`. Android's `ReportGenerator.kt` / `ReportWriter.kt` +
`FindingsJsonEmitter.kt` must emit the same sections (engine = facts, Gemma = prose). Two
functions the founder asked for are part of this contract:

- **External corroboration (internet).** Gemma may gather court cases + news **only when there
  is real, citable signal**; each item is sourced/dated, marked `EXTERNAL — UNSEALED`, placed in
  its own section, and **never overrides** sealed evidence or promotes a G3 candidate. Fetched
  text is data, not instruction. Offline / no signal → "analysis rests entirely on sealed evidence."
- **Deep Research Report** (the standalone-chat action in §3.7): grounds on user-selected **sealed**
  vault files as context, synthesizes across them, cites every anchor, and may add external
  corroboration under the same rules.

**Port target (reference implementation is now merged in firebase):** mirror
`fraud-firewall/src/pipeline/externalCorroboration.ts` and `deepResearch.ts` in Kotlin. The
rules are enforced in code there, not left to the model: the entitlement gate
(`private`/`law_enforcement` free, `commercial` needs a valid licence — reuse the app's seal-type/
licence state), the **signal-or-silence** filter (keep an item only if it has a resolvable URL or
case number), **sanitise** fetched text (strip injection directives before Gemma sees it), and the
**never-mutate / never-override / EXTERNAL-UNSEALED / contextual-only** invariants. The network
fetcher is injected — reuse `WebSearchService.kt` / `OjrsClient.kt`; `DeepResearchEngine.kt` is the
deep-research entry point. Keep the same unit tests (entitlement, signal gate, sanitisation,
never-mutate, sealed-only context).

---

## 5. Definition of done
- Every screen in §2 matches the mock-up's theme, layout and copy.
- Chat is branded **Verum Omnis** with the `vo_badge.png` avatar; no model names surface.
- Vault: delete, Empty Vault, folder chips, and **grouped scan sets** (§4.1) all work.
- Compose: password protect, seal type + payment gate, add-identity + GPS-in-QR opt-in.
- Standalone chat works with an empty vault.
- Forensic scan is driven by real engine progress with a failure state (no hang).
- GPS is really captured; original + report + JSON are sealed into one vault scan set.
- `./gradlew assembleDebug` → `BUILD SUCCESSFUL`.
