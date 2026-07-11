# Verum Omnis — Android Forensic AI

Native Android app (Kotlin + Jetpack Compose) implementing the Verum Omnis
forensic platform: SHA-512 evidence sealing, the deterministic Nine-Brain engine,
GPS-anchored evidence, court-ready report generation, sealed-PDF email
distribution with anti-harassment monitoring, and an evidence vault. UI theme
matches www.verumglobal.foundation (gold `#D1A547` on dark navy `#050C15`,
Cormorant Garamond / Source Sans 3 / JetBrains Mono).

## Module layout
- `app/src/main/java/com/verumomnis/forensic/core` — Constitution constants, device-tier LLM loading.
- `.../crypto` — `Sha512`, `EvidenceSealer` (seal + verify).
- `.../engine` — `NineBrainEngine`, `TaxModule`, `ReportGenerator`, `EmailModule`, `AntiHarassmentMonitor`, `ForensicService`.
- `.../model` — serializable data models (evidence atoms, contradictions, seals, reports, emails).
- `.../vault` — `EvidenceVault` local storage layout.
- `.../ui` — Compose screens (Dashboard, Report, Chat, Email, Vault) + theme.
- Business logic is pure Kotlin/JVM so it is unit-testable without a device.

## Common commands (standard Gradle; run from repo root)
- Unit tests: `./gradlew testDebugUnitTest`
- Lint: `./gradlew lintDebug`
- Debug APK: `./gradlew assembleDebug` (output `app/build/outputs/apk/debug/app-debug.apk`)

## App UX model (important)
- The app is a chat room. The front **Story** screen leads to the **Chat** home. The chat input has a **+**
  that opens a "Sealed actions" bottom sheet: seal a document, add photo/video, verify a document, deep
  research, draft sealed email, tax return, view report, open vault. Report/Email/Tax/Vault are sub-screens
  reached from the sheet or top bar (back arrow returns to chat).
- Constitutional boundary (enforced by design): anything the user adds goes to the **forensic engine only**
  (`ForensicService`/`NineBrainEngine`) → SHA-512 sealed + GPS-anchored + vaulted with findings JSON + report.
  The chat AI (`VerumViewModel.respond`) reads ONLY the sealed `scanResult`/report — never raw uploads. Keep
  it that way: never feed un-sealed evidence bytes/text into the chat context.

## Cursor Cloud specific instructions
- Java 17+ is required (VM has JDK 21); the Android Gradle Plugin toolchain targets JVM 17.
- The Android SDK is provisioned by the startup update script into `$HOME/android-sdk`, and
  `local.properties` (git-ignored) is (re)written with `sdk.dir=$HOME/android-sdk`. If Gradle
  reports a missing SDK, recreate it: `echo "sdk.dir=$HOME/android-sdk" > local.properties`.
- No KVM is available, so the Android emulator cannot run here. Do NOT try to boot an AVD.
- UI is verified via JVM screenshot tests (Roborazzi + Robolectric native graphics), not an emulator.
  Run `./gradlew testDebugUnitTest --tests "com.verumomnis.forensic.UiScreenshotTest"`; PNGs are
  written to `app/build/screenshots/`. `roborazzi.test.record=true` is set in `app/build.gradle.kts`.
- Stock Compose `captureToImage()` hangs under Robolectric (forceRedraw never completes) — use
  Roborazzi's `captureRoboImage()` for screenshots instead.
- Brand fonts are variable TTFs committed under `app/src/main/res/font`; `Type.kt` opts in to
  `ExperimentalTextApi` for `FontVariation`.
- Forensic logic is deterministic by design (same evidence ⇒ same findings/seal); tests rely on this,
  so pass a fixed `Instant` to `ForensicService.scan` / `ReportGenerator.generate` in tests.
- Sealed PDFs are produced by `pdf/SealedPdfGenerator` (Android `PdfDocument`); each page is painted by
  `pdf/SealedPageRenderer`, which draws the portrait watermark (`res/drawable-nodpi/watermark_portrait.png`)
  as a low-opacity, page-centred background underlay before the text and per-page seal footer.
- `android.graphics.pdf.PdfDocument.writeTo` is a native API that Robolectric cannot run off-device, so the
  PDF-bytes test self-skips there (JUnit `Assume`). Verify the visual output via `SealedPageRenderer` drawn to
  a `Bitmap` instead (see `SealedPdfTest.rendersSealedPageWithWatermarkToArtifact`).
- The watermark asset was extracted from a real sealed report and made transparent; to swap in an exact
  brand file, replace `app/src/main/res/drawable-nodpi/watermark_portrait.png` (transparent background).
- Bitcoin anchoring is real (`blockchain/OpenTimestampsService`): it POSTs the SHA-256 of the evidence
  SHA-512 bytes to the OpenTimestamps calendars and assembles a standards-compliant detached `.ots`
  proof (`MAGIC|0x01|0x08|digest|calendar-timestamp`) verifiable by the `ots` client. It needs network;
  offline it returns an OFFLINE result. Anchoring is a separate step from the deterministic seal — call
  `VerumViewModel.anchorSealToBitcoin()` (runs on `Dispatchers.IO`); never anchor inside `EvidenceSealer`
  (keeps sealing deterministic for tests). Unit tests cover the digest/proof/offline paths; the live
  calendar submission is validated manually (see PR), not in CI.
- Photo/video evidence (`ui/MediaIngestor` + `engine/MediaEvidence`): images/videos are picked via
  `ActivityResultContracts.OpenMultipleDocuments`; the ORIGINAL bytes are preserved unaltered in the vault
  (`evidence/raw`), SHA-512 fingerprinted, and GPS-anchored (EXIF `latLong` if present, else device GPS at
  upload) with the capture timestamp. The sealed, watermarked EXHIBIT is a derivative rendered onto its own
  PDF page (`SealedPageRenderer.drawExhibit`) so originals stay pristine for chain of custody. EXIF/GPS/video
  metadata extraction is device-only; unit tests drive the deterministic parts via `ForensicService.ingestMedia`.
- B8 audio (`engine/AudioBrain` + `Transcriber`): tamper/voice-stress/diarization run on `AudioEvidence`.
  Transcription is pluggable — `ProvidedTranscriptTranscriber` parses an imported `[mm:ss] Speaker: text`
  transcript (which is folded into the doc set so B1 cross-references it); a real on-device Whisper model
  implements `Transcriber` for live STT, and `NoModelTranscriber` returns INSUFFICIENT when absent.
