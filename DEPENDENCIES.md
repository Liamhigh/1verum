# Dependencies — Verum Omnis

**Document Purpose:** Every library, SDK, model, and tool required to build and run Verum Omnis. With exact versions, download sources, and verification hashes.

**Last Updated:** 2026-07-13  
**Version:** v5.2.8

---

## Android Platform

| Dependency | Version | Source | Purpose |
|-----------|---------|--------|---------|
| Kotlin | 1.9.22 | Gradle plugin | Primary language |
| Android Gradle Plugin | 8.2.0 | Google Maven | Build system |
| Compile SDK | 34 (Android 14) | Android SDK | Target platform |
| Min SDK | 26 (Android 8.0) | Android SDK | Minimum supported |
| Target SDK | 34 (Android 14) | Android SDK | Target platform |
| JDK | 17 | OpenJDK / Android Studio | Compilation |

## Jetpack Libraries

| Dependency | Version | Gradle Coordinates | Purpose |
|-----------|---------|-------------------|---------|
| Core KTX | 1.12.0 | `androidx.core:core-ktx` | Core Kotlin extensions |
| AppCompat | 1.6.1 | `androidx.appcompat:appcompat` | Backward compatibility |
| Material 3 | 1.11.0 | `com.google.android.material:material` | Material Design components |
| Jetpack Compose BOM | 2024.01.00 | `androidx.compose:compose-bom` | UI framework |
| Compose UI | (BOM) | `androidx.compose.ui:ui` | Compose UI primitives |
| Compose Material3 | (BOM) | `androidx.compose.material3:material3` | Material 3 for Compose |
| Compose Tooling | (BOM) | `androidx.compose.ui:ui-tooling` | Preview support |
| Compose ViewModel | (BOM) | `androidx.lifecycle:lifecycle-viewmodel-compose` | ViewModel integration |
| Navigation Compose | 2.7.6 | `androidx.navigation:navigation-compose` | Screen navigation |
| Hilt | 2.50 | `com.google.dagger:hilt-android` | Dependency injection |
| Hilt Compiler | 2.50 | `com.google.dagger:hilt-compiler` | DI code generation |
| Room | 2.6.1 | `androidx.room:room-ktx` | Local database |
| Room Compiler | 2.6.1 | `androidx.room:room-compiler` | DB code generation |
| DataStore Preferences | 1.0.0 | `androidx.datastore:datastore-preferences` | Key-value storage |
| WorkManager | 2.9.0 | `androidx.work:work-runtime-ktx` | Background tasks |
| Biometric | 1.1.0 | `androidx.biometric:biometric` | Fingerprint/face auth |
| Security Crypto | 1.1.0-alpha06 | `androidx.security:security-crypto` | Encrypted preferences |
| SplashScreen | 1.0.1 | `androidx.core:core-splashscreen` | Splash screen API |

## Forensic Libraries

| Dependency | Version | Source | Purpose | Brain |
|-----------|---------|--------|---------|-------|
| Tesseract OCR | 5.3.3 | GitHub/tesseract-ocr | Text extraction from images | B2 |
| Tesseract Android | 4.1.1 | `com.rmtheis:tess-two` | Android Tesseract wrapper | B2 |
| MuPDF | 1.23.7 | Artifex | PDF parsing and rendering | B2 |
| ExifTool | 12.70 | exiftool.org | Image metadata extraction | B2 |
| OpenCV Android | 4.8.1 | OpenCV official | Image analysis, ELA | B2 |
| FFmpeg | 6.0 | ffmpeg.org | Video container/frame hash | B8 |
| FFmpeg Android | `com.arthenica:ffmpeg-kit-min` 6.0 | Maven Central | Android FFmpeg wrapper | B8 |
| Whisper.cpp | master (commit `a5abfe6`) | GitHub/ggerganov | Offline speech transcription | B8 |
| zxing | 3.5.2 | `com.google.zxing:core` | QR code generation | Seal |
| zxing Android | 3.5.2 | `com.journeyapps:zxing-android-embedded` | QR scanner | Seal |

## AI / LLM

| Dependency | Version | Source | Purpose |
|-----------|---------|--------|---------|
| llama.cpp | master (commit `b4e38b9`) | GitHub/ggerganov | On-device LLM inference |
| llama.cpp JNI | (built from source) | Local build | Android JNI bridge |

## On-Device Models

| Model | Version | Quantization | Size | Download Source | SHA-256 Verification |
|-------|---------|-------------|------|----------------|---------------------|
| Gemma 3 4B Instruct | 3.0.0 | Q4_K_M | 2.5GB | Kaggle/google/gemma-3-4b-it | TBD |
| Gemma 3 4B Instruct | 3.0.0 | Q3_K_S | 1.8GB | Kaggle/google/gemma-3-4b-it | TBD |
| PHI-3 Mini 4K Instruct | 3.5 | Q4_K_M | 2.3GB | HuggingFace/microsoft/Phi-3-mini-4k-instruct | TBD |
| PHI-3 Mini 4K Instruct | 3.5 | Q3_K_S | 1.6GB | HuggingFace/microsoft/Phi-3-mini-4k-instruct | TBD |
| Command R (4B) | v1.0 | Q4_K_M | 2.4GB | HuggingFace/CohereForAI/c4ai-command-r-v01 | TBD |
| Command R (4B) | v1.0 | Q3_K_S | 1.7GB | HuggingFace/CohereForAI/c4ai-command-r-v01 | TBD |
| Gemma 4 12B Instruct | 4.0.0 | Q4_K_M | 7.5GB | Kaggle/google/gemma-4-12b-it | TBD |
| Whisper.cpp (Base) | master | Q5_0 | 150MB | GitHub/ggerganov/whisper.cpp | TBD |
| Whisper.cpp (Medium) | master | Q5_0 | 1.5GB | GitHub/ggerganov/whisper.cpp | TBD |

All model hashes MUST be verified by B9 on every app launch.

## Cryptographic & Blockchain

| Dependency | Version | Source | Purpose |
|-----------|---------|--------|---------|
| BouncyCastle | 1.77 | `org.bouncycastle:bcprov-jdk18on` | SHA-512, encryption utilities |
| OpenTimestamps Client | 0.7.0 | opentimestamps.org | Blockchain anchoring |
| BitcoinJ | 0.16.2 | `org.bitcoinj:bitcoinj-core` | Bitcoin transaction verification |

## Networking (OJRS only — optional)

| Dependency | Version | Source | Purpose |
|-----------|---------|--------|---------|
| OkHttp | 4.12.0 | `com.squareup.okhttp3:okhttp` | HTTP client for court database queries |
| Retrofit | 2.9.0 | `com.squareup.retrofit2:retrofit` | REST API for SAFLII/PACER |
| Kotlin Serialization JSON | 1.6.2 | `org.jetbrains.kotlinx:kotlinx-serialization-json` | JSON parsing |

## Testing

| Dependency | Version | Gradle Coordinates | Purpose |
|-----------|---------|-------------------|---------|
| JUnit 4 | 4.13.2 | `junit:junit` | Unit testing |
| JUnit 5 | 5.10.0 | `org.junit.jupiter:junit-jupiter` | Advanced unit testing |
| MockK | 1.13.8 | `io.mockk:mockk` | Kotlin mocking |
| Turbine | 1.0.0 | `app.cash.turbine:turbine` | Flow testing |
| Espresso | 3.5.1 | `androidx.test.espresso:espresso-core` | UI testing |
| Compose Testing | (BOM) | `androidx.compose.ui:ui-test-junit4` | Compose UI tests |

## Build & Development

| Dependency | Version | Source | Purpose |
|-----------|---------|--------|---------|
| Ktlint | 1.0.1 | ktlint.github.io | Kotlin linting |
| Detekt | 1.23.4 | detekt.dev | Static analysis |
| JaCoCo | 0.8.11 | jacoco | Code coverage |

---

## External Tools (Development)

| Tool | Version | Source | Purpose |
|------|---------|--------|---------|
| Android Studio | Hedgehog (2023.1.1) | developer.android.com | IDE |
| CMake | 3.22.1 | cmake.org | Native code build |
| NDK | 25.2.9519653 | Android SDK | Native development |

---

## Total Dependency Footprint

| Category | Count | Approximate Size |
|----------|-------|-----------------|
| Android/Jetpack | 20 | ~15MB (APK) |
| Forensic Libraries | 8 | ~25MB (APK) |
| Cryptographic | 3 | ~5MB (APK) |
| Networking | 3 | ~3MB (APK, optional) |
| Testing | 6 | Test-only (not in APK) |
| **On-Device Models** | **9** | **~20GB (downloaded at runtime)** |

**APK Size:** ~50MB (without models)  
**Runtime Size:** ~20GB (with all models downloaded)
