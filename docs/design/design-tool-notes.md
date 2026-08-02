repo: Liamhigh/webdocsol
branch: main
path: (whole repo referenced; android app is new, spec'd from seal-module/android and SPEC.md)

## Last sync
date: 2026-08-01T04:02:32Z
note: read seal-document.html payment-gate logic (STRIPE_LINKS/showPaymentGate) and added a matching commercial payment gate in the app: choosing Commercial + confirming shows a licence modal with real pricing tiers (SA R750, SADC R500, International $50) before the seal pipeline runs.

### Updated in this project
- Rebuilt the Android app design to match the locked website theme (navy `#040D1B`, gold `#D4A843`, blue `#4A7EC7`, Cormorant Garamond + Courier New) instead of its previous mismatched styling.
- Added the missing "New" action (FAB → Seal Document / Forensic Scan / Verify), a compose screen with file/camera attach, and a Verify tab for parity with the website.
- Sealing renders as a live chat thread of the real VO-DSS pipeline steps; Forensic Scan now correctly runs the 9-Brain engine (B1–B8) read from index.html's Engine section and the build-instructions PDF — brains are the scan engine itself, not a separate feature.
- Landing/home screen copy now matches the live site's hero exactly ("AI Forensics for Truth" / "Truth for All" / hero subhead, Verify Document + Seal Document CTAs).
- Vault expanded to show the real evidence folder structure (raw/processed/sealed) with per-document delete and an Empty Vault action.
- Added a legal AI Q&A chat per document (evidence citations, ordinal confidence, statute references), sourced from the PDF's AI Chat Interface spec.

## Sources read
- webdocsol repo: README.md, DESIGN_LOCK.md, seal-module/SPEC.md, seal-module/android/README.md, dashboard.html, index.html (hero, Engine/9-Brain grid, nav)
- uploads/fullbuild android ocr.PDF (user-provided): 9-Brain architecture, Evidence Vault file layout, AI Chat Interface, logo asset specs

## Screen map
| Screen (this project) | Repo source |
|---|---|
| Vault / document list | dashboard.html (card/list patterns), README.md brand colours |
| Seal pipeline (8 steps, lights) | README.md Architecture Overview, seal-module/SPEC.md §1 |
| Forensic scan pipeline | README.md Fraud Detection section |
| Verify tab + chain of custody | verify.html concept, SPEC.md §8 Seal Chain of Custody |
| Visual tokens (colors/type/spacing) | DESIGN_LOCK.md |
