# Verum Omnis on Replit

Verum Omnis remains a native Kotlin/Jetpack Compose Android app with a C++
llama.cpp inference bridge. The pnpm workspace includes a separate public download
page; it does not replace or serve the Android UI. Read
`AGENTS.md` before changing application code.

## Workspace layout and boundaries

- `app/`, `gradle/`, `gradlew`, and root Gradle files: original native project.
- `scripts/android-build.sh`: one-shot Android toolchain setup and build entry point.
- `artifacts/`: separate web artifacts and their HTTP services, not Android sources.
- `lib/` and the TypeScript files in `scripts/`: pnpm scaffold utilities.
  Scaffold API/database packages are not part of the Android app.
- `docs/`, `design/`, and root specifications: internal native project references.
- `.migration-backup/`: retained migration metadata; not a build entry point.

Never configure `android-build.sh` or Gradle as a web/deployment server. They
finish after producing files and do not open a port. A future download page must
have its own artifact-owned HTTP workflow. Do not port the native UI, expose the
workspace with a static file server, or auto-publish. No Android emulator is
available here.

## Android build

Run from the workspace root (the script also works from any directory):

```bash
bash scripts/android-build.sh                 # assembleDebug
bash scripts/android-build.sh assembleRelease
```

The script uses Java 17 from `.replit` and installs official Android command-line
tools in `.local/android-sdk` on first use. It accepts SDK licenses and installs
API 34, Build Tools 34.0.0, NDK 27.2.12479018, and CMake 3.22.1. First use needs
network access and several GB of space. Gradle downloads dependencies; CMake
fetches the pinned, checksum-verified llama.cpp archive without Git credentials.
Later builds reuse local downloads. `local.properties` is generated locally.

| Build | Output |
| --- | --- |
| Debug (debug-signed) | `app/build/outputs/apk/debug/app-debug.apk` |
| Release (release-signed) | `app/build/outputs/apk/release/app-release.apk` |

Install on an Android 10+ ARM64 device. Building does not run or preview the app.
For a preconfigured SDK outside Replit, standard `./gradlew assembleDebug`,
`./gradlew testDebugUnitTest`, and `./gradlew lintDebug` remain valid.

## Release identity and secrets

Release builds require existing Replit Secrets `ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`.
Keep the same backed-up keystore for every update; do not generate a replacement.
The script writes a temporary key under ignored `.local/release-signing/` only
while Gradle runs and removes it afterward. It validates the store before
compiling; a mismatched alias is resolved only if the store has one private key.
Multiple-key stores require the correct alias. Never print signing diagnostics
that could contain secret values. Capture release logs privately.

The release APK contains no NewsAPI credential. Debug builds work without a key
and report news as unavailable. See `docs/NEWS_API_CONFIGURATION.md` for optional
local debug configuration and the limits of credentials in APKs.

## Checks

```bash
bash scripts/android-build.sh testDebugUnitTest
bash scripts/android-build.sh lintDebug
bash scripts/android-build.sh testDebugUnitTest --tests 'com.verumomnis.forensic.UiScreenshotTest'
pnpm install --frozen-lockfile
pnpm run typecheck
```

Android test reports: `app/build/reports/tests/testDebugUnitTest/index.html`.
JVM screenshots: `app/build/screenshots/`. Device-only camera, GPS, biometrics,
and native inference still need a physical Android device. No browser screenshot
can verify native Compose UI.

The full Robolectric/native-graphics suite can exceed this host's memory in a
shared test process. For broad verification, use one test worker and fork a new
process per test class; the workspace preservation check passed with a 1 GiB
Gradle heap and 512 MiB test heaps. This is a host-only test constraint, not an
Android app/runtime setting.

pnpm checks cover only the web workspace, not Kotlin. The post-merge script
installs pnpm dependencies only; it must not push a scaffold database or start
Android compilation. Web artifacts own their service run/build commands.

## Private files and public downloads

`.gitignore` excludes SDKs, signing files, local properties, caches, APKs, and
`generated-artifacts/`. `.replitignore` additionally excludes native sources,
private inputs, supporting documents, and the migration backup from web
deployment images. Do not weaken those boundaries to serve an APK.

The download artifact uses an explicit public-assets allowlist: only
the verified release APK and deliberately selected brand assets/metadata. Never
copy signing materials, vault/evidence data, internal reports, or the project
root into a public directory.

`artifacts/verum-download` owns `/` and builds to its own static `dist/public`
directory. Its production build has no Android SDK or signing-secret dependency.
`artifacts/api-server` owns `/api`, with health probe `/api/healthz`, and serves
only the approved APK and checksum at the two exact `/api/releases/` routes.
There is no storage browsing or public upload API. The APK is in App Storage,
not the deployment image or Git. `DEFAULT_OBJECT_STORAGE_BUCKET_ID` must be
available in production; the configured shared storage setting supplies it.

For a future release, first build and verify the signed APK with `apksigner`.
Update `artifacts/api-server/src/releases/android.json`, the fixed API routes,
OpenAPI contract, and download page metadata/links together, then regenerate
the client with `pnpm --filter @workspace/api-spec run codegen`. Stage only the
approved file with:

```bash
node --experimental-strip-types artifacts/api-server/scripts/stage-release.mjs app/build/outputs/apk/release/app-release.apk
```

Staging verifies local and stored SHA-256, uses a checksum-addressed object path,
and refuses overwrites. It is a maintainer operation, never a web startup/build
step. The API pins the validated storage generation while streaming and supports
resumable byte ranges. Run range unit checks with:

```bash
node --experimental-strip-types --test artifacts/api-server/tests/download-range.test.mjs
```
