import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
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
        versionCode = 52
        versionName = "0.5.0"
        // Baked at build time; lets a shared diagnostics report prove exactly
        // which APK is installed (used by the Diagnostics "build time" row).
        buildConfigField(
            "String",
            "BUILD_TIME",
            "\"${Date()}\""
        )
    }

    signingConfigs {
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
}
