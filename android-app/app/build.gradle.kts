// ScottsTechX — :app module
// Single-module Compose app. Splitting into :feature-buyer, :feature-driver,
// :core-data, :core-ui can come later once the modular-monolith is green.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.scottstechx.commerceos"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.scottstechx.commerceos"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // API base URL is wired in via BuildConfig so the same APK can point
        // at staging or local dev. Override per-buildType below.
        //   - debug: points at the dev machine (10.0.2.2 from the emulator)
        //   - release: must be set to the hosted API URL at build time.
        //     Override in CI with: -PapiBaseUrl=https://api.scottstechx.example/
        val apiBaseUrl: String =
            (project.findProperty("apiBaseUrl") as String?) ?: "http://10.0.2.2:3001/"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "API_BASE_URL_EMULATOR", "\"http://10.0.2.2:3001/\"")
        // Marketing share-link base. Same env-override mechanism.
        val marketingBaseUrl: String =
            (project.findProperty("marketingBaseUrl") as String?) ?: "https://scottstechx.example/"
        buildConfigField("String", "MARKETING_BASE_URL", "\"$marketingBaseUrl\"")

        // Firebase Auth config. Populate from the Firebase console web-app
        // config (Project settings -> General -> Your apps -> Web). The
        // google-services.json approach is not used, so the build works
        // without a generated file; provide these via -P flags or gradle.properties.
        val firebaseApiKey: String = (project.findProperty("firebaseApiKey") as String?) ?: ""
        val firebaseAppId: String = (project.findProperty("firebaseAppId") as String?) ?: ""
        val firebaseProjectId: String = (project.findProperty("firebaseProjectId") as String?) ?: "scottstechx-52bab"
        val firebaseSenderId: String = (project.findProperty("firebaseSenderId") as String?) ?: ""
        buildConfigField("String", "FIREBASE_API_KEY", "\"$firebaseApiKey\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"$firebaseAppId\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$firebaseProjectId\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"$firebaseSenderId\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // 10.0.2.2 = the host machine as seen from the Android emulator.
            // 3001 matches 12_Backend/src/server.ts default PORT.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3001/\"")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // STUB: real release should resolve API_BASE_URL from BuildConfigField
            // injected by the CI environment. For now, also points at the dev host.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3001/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // Treat warnings about experimental APIs as errors only in CI; local dev
        // is more lenient. Flip freely.
        freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose (BOM-aligned)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Permissions (runtime permission flow in Compose)
    implementation(libs.accompanist.permissions)

    // Google Play Services
    // - play-services-location: real GPS via FusedLocationProvider (Driver + Buyer nearby).
    // - play-services-integrity: REMOVED — no published artifact at any recent version on
    //   Google Maven. PlayIntegrityClient.kt falls back to a no-op (returns null token) so the
    //   rest of the app works fine; the tamper signal is weaker until this is re-added with a
    //   verified coordinate.
    implementation(libs.play.services.location)

    // Credential Manager + Google Identity (One-Tap Sign-In)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // Storage
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Image loading
    implementation(libs.coil.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)

    // Logging
    implementation(libs.timber)

    // Firebase Auth (email/password signup + Google Sign-In)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.android)

    // Instrumentation tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
