# Verum Omnis — Android Forensic AI

**AI Forensics for Truth.** A native Android application (Kotlin + Jetpack Compose)
implementing the Verum Omnis constitutional forensic platform described in the
technical specification (Constitution v5.2.7 · Nine-Brain v1.0).

The UI theme mirrors [verumglobal.foundation](https://www.verumglobal.foundation)
— gold on dark navy with Cormorant Garamond, Source Sans 3 and JetBrains Mono.

## Features
- **Evidence upload with GPS capture** — every ingested file records its GPS coordinates and a SHA-512 fingerprint.
- **Nine-Brain forensic engine** — deterministic contradiction detection, timeline reconstruction, legal-framework mapping and financial analysis; always the third verifier in Triple-AI consensus.
- **Court-ready reports** — every contradiction is anchored to a **person**, a **page/line**, and an **applicable statute**; the report is cryptographically sealed.
- **Cryptographic sealing** — SHA-512 hashing with the Constitution ruleset embedded, per-page seal footers, and seal verification (VERIFIED / TAMPERED / NO-CHAIN).
- **Sealed-PDF email distribution** — every AI-drafted email is delivered as a sealed PDF and written to a distribution audit trail.
- **Anti-harassment monitoring** — frequency, bulk, repeat and cooldown rules escalate ALLOW → WARN → BLOCK while still sealing every draft for the record.
- **Evidence vault** — structured local storage with SHA-512 integrity manifest.
- **Tax module** — SA company/individual tax, accountant fee benchmarking, 20% recovered-fraud commission.

## Build & test
```bash
./gradlew testDebugUnitTest   # unit + JVM UI (Roborazzi) tests
./gradlew lintDebug           # Android lint
./gradlew assembleDebug       # debug APK
```

Requires JDK 17+ and an Android SDK (`sdk.dir` in `local.properties`, `compileSdk 34`, `minSdk 29`).

See `AGENTS.md` for development notes and Cursor Cloud specifics.
