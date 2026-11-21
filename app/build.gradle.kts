import java.time.Instant
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

// Optional local-only NewsAPI key. Never provide a shared/default value: any key
// compiled into an Android APK can be extracted. The release variant below
// deliberately has no key; production access should go through a backend proxy.
val localProperties = Properties().apply {
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) localProps.inputStream().use { load(it) }
}
val debugNewsApiKey = (
    providers.gradleProperty("NEWS_API_KEY").orNull
        ?: providers.environmentVariable("NEWS_API_KEY").orNull
        ?: localProperties.getProperty("NEWS_API_KEY")
        ?: ""
    ).trim()

val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull
    ?.takeIf { it.isNotBlank() }
val releaseStorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull

fun buildConfigString(value: String): String =
    "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r") + "\""

android {
    namespace = "com.verumomnis.forensic"
    compileSdk = 34
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.verumomnis.forensic"
        minSdk = 29
        targetSdk = 34
        versionCode = 4
        versionName = "5.2.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Real build timestamp for the Settings > About screen (replaces a hardcoded date).
        val buildTime = Instant.now().toString()
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")

        // On-device LLM inference bridge (voinference_jni.cpp -> llama.cpp). arm64-v8a only
        // for now — covers essentially all Android 10+ (minSdk 29) devices in real use.
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                // Static libc++: voinference is the only native library we ship, so there is
                // nothing to share a runtime with. This also drops libc++_shared.so from the
                // APK entirely — one fewer library needing 16 KB alignment (see CMakeLists).
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            if (
                releaseStorePassword.isNullOrBlank() ||
                releaseKeyAlias.isNullOrBlank() ||
                releaseKeyPassword.isNullOrBlank()
            ) {
                throw GradleException(
                    "Release signing is incomplete. Configure the Android signing secrets."
                )
            }
            create("releaseUpload") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("releaseUpload")
            }
            // Never embed the optional local debug credential in a distributable APK.
            buildConfigField("String", "NEWS_API_KEY", "\"\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // A locally supplied key still is not secret once compiled into this APK.
            buildConfigField("String", "NEWS_API_KEY", buildConfigString(debugNewsApiKey))

            // Build the native inference library optimised even in debug.
            //
            // AGP passes CMAKE_BUILD_TYPE=Debug for this variant, which compiles
            // llama.cpp/ggml at -O0: no vectorisation, no inlining, scalar matmul.
            // Measured on an SM-A366B, a Gemma-3 1B chat reply took ~4.8 minutes
            // (~2 tok/s) — the app read as frozen. Inference is arithmetic in a
            // tight loop, so the optimiser is not a nicety here, it is the feature.
            //
            // RelWithDebInfo rather than Release: -O2 with symbols retained, so
            // native crashes still produce a usable stack trace. Kotlin/Java debug
            // builds are unaffected — this governs the CMake targets only.
            externalNativeBuild {
                cmake {
                    arguments += "-DCMAKE_BUILD_TYPE=RelWithDebInfo"
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all { it.systemProperty("roborazzi.test.record", "true") }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // The in-app Constitution reads the generated copy of the repo's
    // CONSTITUTION.md — see syncConstitution below.
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/constitution"))
}

val validateReleaseSigning by tasks.registering {
    group = "verification"
    description = "Prevents creating an unsigned APK or app bundle for distribution."
    doLast {
        if (releaseKeystorePath == null) {
            throw GradleException(
                "Release outputs must be signed. Run scripts/android-build.sh with " +
                    "ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, " +
                    "ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD configured as Replit Secrets."
            )
        }
    }
}

tasks.configureEach {
    if (name == "packageRelease" || name == "bundleRelease") {
        dependsOn(validateReleaseSigning)
    }
}

/**
 * Copies the repo's CONSTITUTION.md into the APK's assets on every build, so the
 * governing document has exactly one source of truth.
 *
 * The app previously showed only `assets/constitution.pdf`, a separately sealed
 * artifact that drifted from the repo: the markdown sat at v5.2.7 while the
 * bundled PDF was v6.0. Generating the asset means the text a user reads in the
 * app is the text in the repository, always.
 */
val syncConstitution by tasks.registering(Copy::class) {
    description = "Copies CONSTITUTION.md into assets so the app and repo cannot drift."
    from(rootProject.file("CONSTITUTION.md"))
    into(layout.buildDirectory.dir("generated/constitution"))
    rename { "constitution.md" }
}

tasks.named("preBuild") { dependsOn(syncConstitution) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.work)
    implementation(libs.okhttp)
    implementation(libs.pdfbox.android)
    implementation(libs.zxing.core)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.accompanist.permissions)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit)
    debugImplementation(libs.androidx.ui.test.manifest)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
