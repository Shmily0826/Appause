import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Tell Room's KSP processor where to write the schema JSON files
// (app/schemas/...) used by the official migration test framework.
ksp {
    arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.absolutePath)
}

// Load signing credentials from local.properties (never committed).
val localProps = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.appause.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.appause.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 91
        versionName = "0.5.39"
        // Baked at build time; lets a shared diagnostics report prove exactly
        // which APK is installed (used by the Diagnostics "build time" row).
        buildConfigField(
            "String",
            "BUILD_TIME",
            "\"${Date()}\""
        )
    }

    signingConfigs {
        // Optional override for the debug keystore location. Normally AGP uses
        // ~/.android/debug.keystore; pass -PappauseDebugKeystore=<path> when
        // that directory isn't writable (e.g. a restricted build sandbox).
        // Point it at a COPY of the usual debug.keystore so the signature stays
        // the same and existing debug installs can still be updated in place.
        providers.gradleProperty("appauseDebugKeystore").orNull?.let { path ->
            getByName("debug") {
                storeFile = file(path)
            }
        }

        create("release") {
            // Values come from local.properties, which is git-ignored.
            storeFile = file(localProps.getProperty("APPause_KEYSTORE_PATH", "release.keystore"))
            storePassword = localProps.getProperty("APPause_KEYSTORE_PASSWORD")
            keyAlias = localProps.getProperty("APPause_KEY_ALIAS")
            keyPassword = localProps.getProperty("APPause_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            // Use a separate applicationId so the debug build can be installed
            // SIDE-BY-SIDE with the release build (it will NOT overwrite it).
            // The accessibility service, DataStore, and app data are all scoped
            // to this id, so the two installs never clash.
            applicationIdSuffix = ".debug"
            // Distinct name so the app's own version string proves which build
            // is installed; the HIGHER versionCode (set in defaultConfig) makes
            // any stale old APK be rejected as a downgrade.
            versionNameSuffix = "-debug"
            isDebuggable = true
            // Debug build keeps AppLogger output (BuildConfig.DEBUG == true),
            // so interception is visible in logcat — this is the test build.
            // (BUILD_TIME is now defined in defaultConfig, so both debug and
            //  release diagnostics reports can prove which APK is installed.)
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign with the locally-stored release key (see signingConfigs above).
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Plain JVM unit tests can't call real android.* framework classes.
        // Return-default-values keeps android.util.Log calls (AppLogger) from
        // crashing in tests instead of throwing "not mocked".
        unitTests.isReturnDefaultValues = true
        // Robolectric needs the real Android resources (manifests, strings,
        // drawables) on the JVM classpath to instantiate a working Context.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Unit tests (app/src/test) — compiled and run only on the dev machine,
    // never included in debug or release APKs.
    testImplementation(libs.junit)
    testImplementation(libs.json)

    // Robolectric: runs Android-framework-dependent code (Context, DataStore,
    // Room) on the JVM so migration + ViewModel + ProState tests need no device.
    testImplementation(libs.robolectric)
    // AndroidX Test core — ApplicationProvider / InstrumentationRegistry for the
    // Robolectric-backed tests (not a transitive dep of Robolectric).
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    // kotlinx-coroutines-test: deterministic coroutine testing for ViewModels
    // and the suspend ProState.redeemCode flow.
    testImplementation(libs.kotlinx.coroutines.test)
}
