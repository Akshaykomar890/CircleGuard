plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.nebulaiq.assignment"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nebulaiq.assignment"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        buildConfigField(
            "String",
            "CIRCLEGUARD_WORKER_URL",
            "\"https://circleguard-notifications.akshay-circleguard.workers.dev\"",
        )
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("io.insert-koin:koin-androidx-compose:4.0.0")

    implementation("androidx.compose.ui:ui:1.9.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.0")
    implementation("androidx.compose.material3:material3-android:1.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.9.0")

    // Screen-to-screen flow.
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // Background upload work after a geofence event.
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // Fused location and Android geofencing APIs.
    implementation("com.google.android.gms:play-services-location:21.4.0")

    // Small HTTP client for the Cloudflare Worker API.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Firebase versions are managed together by the BoM.
    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")

    // Reducer/ViewModel and Compose test coverage will be added with feature tests.
    testImplementation("junit:junit:4.13.2")
}
