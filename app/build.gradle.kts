plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.reforge"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.reforge"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }
        // only ship the ABI the target device uses for now (arm64)
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // UniFFI runtime for the Rust native core (skyla_core). The @aar variant
    // ships JNA's own native libs for Android.
    implementation("net.java.dev.jna:jna:5.14.0@aar")

    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")
}

// ---------------------------------------------------------------------------
// Rust native core (skyla_core) via UniFFI.
//
// `rust/build.sh` builds the host cdylib, regenerates the Kotlin bindings into
// app/src/main/java/uniffi/, and cross-compiles the arm64-v8a .so into
// app/src/main/jniLibs/. We hook it ahead of preBuild so a normal Gradle build
// always compiles against an up-to-date core. Requires cargo + cargo-ndk + an
// Android NDK; see rust/README or rust/build.sh for details.
val buildRustCore by tasks.registering(Exec::class) {
    group = "build"
    description = "Build the Rust core and regenerate its UniFFI bindings + .so"
    workingDir = rootProject.projectDir
    commandLine("bash", "rust/build.sh")
    // Surface the SDK/NDK location to cargo-ndk running under Gradle.
    val sdkDir = android.sdkDirectory.absolutePath
    environment("ANDROID_HOME", sdkDir)
    environment("ANDROID_SDK_ROOT", sdkDir)
    // Only rebuild when the Rust sources or build script change.
    inputs.dir(rootProject.file("rust/src"))
    inputs.files(rootProject.file("rust/Cargo.toml"), rootProject.file("rust/build.sh"))
    outputs.dir(rootProject.file("app/src/main/jniLibs/arm64-v8a"))
    outputs.dir(rootProject.file("app/src/main/java/uniffi/skyla_core"))
}

tasks.named("preBuild").configure { dependsOn(buildRustCore) }
