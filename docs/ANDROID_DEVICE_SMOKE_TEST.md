# Android 10+ ARM64 Device Smoke Test

**Recorded:** 2026-11-20  
**Purpose:** Verify real-device behavior that JVM/Robolectric tests cannot establish.  
**App requirements:** Android API 29+ and `arm64-v8a` (the APK is built for that ABI).

## Current run record

| Area | Result | Evidence |
|---|---|---|
| Build and package | **PASS** | `./gradlew assembleDebug` completed as part of `./gradlew assembleDebug connectedDebugAndroidTest`. The debug APK was produced at `app/build/outputs/apk/debug/app-debug.apk`; it contains `lib/arm64-v8a/libvoinference.so`. |
| JVM tests | **PASS** | `./gradlew testDebugUnitTest`: 256 tests, 0 failures, 0 errors, 1 skipped. `SealedPdfTest` skips the Android `PdfDocument.writeTo` path under Robolectric. |
| Instrumentation test compilation | **PASS** | Both `NativeBridgeAvailabilityTest` and `LlamaModelSmokeTest` compiled into the Android test APK. |
| Connected-device test run | **BLOCKED — NOT RUN** | `./gradlew assembleDebug connectedDebugAndroidTest` reached the connected test task, which failed with `No connected devices!`. `adb devices -l` listed no devices. |
| App launch on Android | **NOT TESTED** | No Android device or running emulator is available. |
| Camera and location permission behavior | **NOT TESTED** | Requires real Android permission dialogs, providers, and camera hardware. |
| Vault persistence/integrity on device | **NOT TESTED** | JVM tests do not establish app-private storage behavior on a phone. |
| Sealed report/PDF on device | **NOT TESTED** | The PDF-bytes path uses Android `PdfDocument`; its JVM test was skipped. |
| JNI model load/inference on device | **NOT TESTED** | The APK contains the ARM64 bridge, but no device was attached and no `phi3.gguf` fixture is present in the project. |

**Do not treat JVM screenshots as evidence of device-only behavior.** The test suite includes JVM UI screenshot tests, but those do not verify runtime permissions, CameraX preview, Android private-file persistence, native library loading on a handset, or real model inference.

## Implementation notes relevant to the test

- `minSdk` is 29, `targetSdk` is 34, and the native ABI filter is `arm64-v8a` in `app/build.gradle.kts`.
- Location permission is requested on initial app composition in `ui/VerumApp.kt`. A granted permission calls `MainActivity.captureLocation`; if no recent fix or provider is available, the app records location as unavailable rather than inventing a fix.
- Camera permission is requested by `ui/ScanSealScreen.kt` for the live CameraX **seal-QR scanner**. The current “Add photo / video” action opens Android's document picker; it does not capture a new photo in-app. A direct camera-capture flow is not present in the inspected UI.
- Picked media is ingested by `ui/MediaIngestor.kt`, and the original bytes are written under the app-private `files/vault/evidence/raw` directory before the forensic scan. The engine receives media metadata/bytes through `ForensicService`; chat receives the sealed scan findings/report, not the original upload bytes/text (`VerumViewModel.sendChat` → `respond` → `buildCommunicatorPrompt`).
- `EvidenceVault` records SHA-512 entries in `files/vault/findings/integrity_manifest.json` and exposes `verifyIntegrity()`. Its source comment explicitly says the AES-256-GCM at-rest encryption layer is specified for production but is not implemented in this storage class. This smoke test checks integrity and persistence only; it does **not** establish encryption at rest.
- Native tests already exist: `NativeBridgeAvailabilityTest` checks library loading without a model; `LlamaModelSmokeTest` loads `phi3.gguf` and checks for non-empty generated text. The latter requires that model fixture in the app's external `models` directory.

## Device prerequisites

1. Use a physical Android 10+ (`API >= 29`) ARM64 phone with a working camera. Turn on location services for the location-granted path.
2. Build the debug APK from this revision:

   ```bash
   ./gradlew assembleDebug
   ```

3. Connect and verify the target:

   ```bash
   adb devices -l
   adb shell getprop ro.build.version.sdk
   adb shell getprop ro.product.cpu.abi
   ```

   The device must be listed as `device`, report API 29 or newer, and use `arm64-v8a`.
4. Install the APK and start from a clean app state. Clearing data removes prior vault contents, so only do this on a designated test install:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell pm clear com.verumomnis.forensic
   adb shell monkey -p com.verumomnis.forensic 1
   ```

## Smoke procedure

Record the phone model, Android version/API, ABI, app build/revision, and result for every step. Use only synthetic test files; do not use personal or case evidence.

### 1. Launch and navigation

1. Launch from the app icon after clearing app data.
2. Confirm the first-run story/landing UI appears without a crash, then enter the app and open Chat, New Forensic Scan, and Evidence Vault.
3. Background and reopen the app once. Confirm it returns without a crash.

**Pass:** screens render and navigation works; no fatal exception or repeated crash appears in `adb logcat`.

### 2. Location permission

1. On first launch, grant precise location while using the app and keep device location services enabled.
2. Select a test photo that has no EXIF GPS through **Add photo / video**. Confirm the preview/scan metadata uses a device location when a fix is available.
3. Repeat from a clean app state and deny location. Confirm the app remains usable and the evidence is explicitly shown as having no recorded GPS; it must not reuse stale coordinates.
4. If Android offers approximate location, test that choice separately and record whether a location is captured or reported unavailable.

**Pass:** granted access records a real available fix; denied/unavailable access does not crash or imply that GPS was recorded.

### 3. Camera permission and QR scanner

1. Open the Chat **+** actions and choose **Scan seal QR**.
2. Deny camera permission. Confirm the permission-required explanation appears, navigation/back still works, and there is no crash.
3. Reset the camera permission (or clear app data), reopen the scanner, grant permission, and confirm a live rear-camera preview appears.
4. Record that this validates the QR scanner camera path only. The current Add photo / video action uses the system picker; it does not launch an in-app photo capture UI.

**Pass:** denial is handled clearly; granting permission opens CameraX preview on the physical device.

### 4. Evidence vault integrity and restart persistence

1. Prepare a synthetic image named `vo-smoke.jpg` on the test workstation and compute its reference digest:

   ```bash
   sha512sum vo-smoke.jpg
   adb push vo-smoke.jpg /sdcard/Download/vo-smoke.jpg
   ```

2. In the app, use **Add photo / video**, choose `vo-smoke.jpg`, review the pending item, and confirm the forensic scan/seal flow.
3. Confirm the item appears in the Evidence Vault. Compare the displayed evidence SHA-512 (where shown) with the workstation digest.
4. Independently inspect the app-private original and manifest from the debug build:

   ```bash
   adb shell run-as com.verumomnis.forensic ls files/vault/evidence/raw
   adb exec-out run-as com.verumomnis.forensic cat files/vault/evidence/raw/vo-smoke.jpg > vo-smoke-from-device.jpg
   sha512sum vo-smoke-from-device.jpg
   adb shell run-as com.verumomnis.forensic cat files/vault/findings/integrity_manifest.json
   ```

   Confirm the copied original's hash equals the workstation hash and the matching manifest entry has the same SHA-512.
5. Force-stop and relaunch the app; repeat the file and manifest checks. Confirm the bytes and hash are unchanged.

**Pass:** the unaltered original is present in private storage, the manifest digest matches, and the data survives process restart. Do not edit or delete the original during this smoke test.

### 5. Sealed forensic report and raw-evidence boundary

1. Prepare a synthetic text file named `vo-report-smoke.txt` with harmless test-only text. In **New Forensic Scan**, select it, review the preview, and start analysis.
2. Wait for the pipeline to complete. Open **View sealed report** and confirm the report is present, has a seal/reference and SHA-512 footer, and remains listed after app restart.
3. Export the report PDF through the report screen's export/share action, save it to a test location, open it in a PDF viewer, and check that the report content and seal footer render. Record the generated file's size and whether it opens.
4. Ask Chat about the completed scan. Confirm its response is grounded in the sealed findings/report. Do not use a screenshot alone to claim that raw upload text was kept out of chat: that boundary is supported here by source inspection, where the communicator prompt is constructed from `ForensicFindings`, not the original file bytes/text.

**Pass:** the sealed report is generated, its identifying seal is visible, the PDF opens on-device, and chat discusses sealed findings rather than receiving the raw upload directly.

### 6. Native bridge and model loading

1. Run the bridge-availability instrumentation test on the connected device:

   ```bash
   ./gradlew connectedDebugAndroidTest --tests "*NativeBridgeAvailabilityTest*"
   ```

   **Pass:** `LlamaBridge.isAvailable` is true on the ARM64 device.
2. Obtain an authorized, compatible `phi3.gguf` test model separately; it is not bundled in the app or repository. Push it to the test app's external model directory:

   ```bash
   adb push phi3.gguf /sdcard/Android/data/com.verumomnis.forensic/files/models/phi3.gguf
   ./gradlew connectedDebugAndroidTest --tests "*LlamaModelSmokeTest*"
   ```

   **Pass:** the model loads through JNI and the test receives non-empty generated text. Record load/inference time, device RAM, and any `VoInference` errors from `adb logcat`. A bridge-availability pass alone is not proof of model loading or inference.

## Final device record

Fill this section only after a real device run; do not replace untested statuses with a build or JVM result.

| Field | Actual result |
|---|---|
| Device / Android version / API / ABI | Not run — no connected device |
| Launch and navigation | Not run |
| Location permission (precise / approximate / denied) | Not run |
| Camera permission (denied / granted / preview) | Not run |
| Evidence SHA-512 and restart persistence | Not run |
| Report and exported PDF | Not run |
| JNI bridge availability | Not run |
| GGUF load and generation | Not run — model fixture also absent |
| Logs, failures, or follow-up notes | Not run |
