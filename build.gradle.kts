plugins {
    id("com.android.application") version "8.12.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
    // Apply in :app only after app/google-services.json is added.
    id("com.google.gms.google-services") version "4.5.0" apply false
}
