# Verum Omnis on Replit

This repository is a native Android app (Kotlin, Jetpack Compose, and a C++
llama.cpp inference bridge), not a web app. Keep the existing Gradle structure.
Read `AGENTS.md` before changing application code.

## Build

Press **Run** or use the Shell:

```bash
bash scripts/android-build.sh
```

The script uses Java 17 from the Replit Nix configuration and installs the
official Android command-line tools into `.local/android-sdk` on first use.
It accepts Android SDK licenses and installs API 34, Build Tools 34.0.0,
NDK 27.2.12479018, and CMake 3.22.1. The first build requires network access
and several GB of disk space. Gradle downloads dependencies, and CMake fetches
the pinned llama.cpp revision as a checksum-verified public archive (no GitHub
credentials required). Later builds reuse these local downloads.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Download it from
the Files panel and install it on an Android 10+ ARM64 device. It is debug-signed,
not a store-ready release. Building an APK does not run the Android UI here.
There is no web server or browser preview; do not add one just for setup.
Use the Run command rather than a persistent server workflow for this one-off build.

## Direct APK sharing

Create a release-signed APK with:

```bash
bash scripts/android-build.sh assembleRelease
```

Release builds require the following Replit Secrets:
`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
and `ANDROID_KEY_PASSWORD`. Base64-encode your locally backed-up `.jks` file
and add the encoded value and credentials through the Secrets tool. Do not put
the keystore or its passwords in Git or chat. The build script materializes the
keystore under the ignored `.local/release-signing/` directory only while
Gradle runs, then removes the temporary copy.

Download the signed APK from `app/build/outputs/apk/release/app-release.apk`.
Keep the original keystore and password backup: every update must use the same
signing identity. The release APK deliberately contains no NewsAPI credential.

## Checks

```bash
bash scripts/android-build.sh testDebugUnitTest
bash scripts/android-build.sh lintDebug
bash scripts/android-build.sh testDebugUnitTest --tests 'com.verumomnis.forensic.UiScreenshotTest'
```

Unit-test HTML reports: `app/build/reports/tests/testDebugUnitTest/index.html`.
JVM screenshot output: `app/build/screenshots/`.
These checks do not replace testing device-only camera, GPS, biometric, and native
inference behavior on a physical Android device.

## Local files and credentials

`local.properties` is generated with the local SDK path and must not be committed.
The downloaded SDK, `.cxx`, build output, and generated diagnostics are ignored.
No credentials are required to compile. NewsAPI has no shared fallback: debug
builds work without a key and report news as unavailable. See
[`docs/NEWS_API_CONFIGURATION.md`](docs/NEWS_API_CONFIGURATION.md) for local
debug configuration and the security limits of credentials in Android APKs.
