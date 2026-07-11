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
