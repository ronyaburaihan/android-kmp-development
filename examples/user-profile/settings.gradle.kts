rootProject.name = "user-profile"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        // The `androidx.room3` Gradle plugin is published to Google's Maven repository only.
        // Without `google()` here, configuration fails with
        // "Plugin [id: 'androidx.room3', version: '3.0.3'] was not found".
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // Required even though this module declares no Android target: the JetBrains
        // multiplatform build of lifecycle-viewmodel depends on `androidx.lifecycle:*`, and
        // Room 3 / DataStore are AndroidX artifacts. All are on Google Maven, not Central.
        google()
    }
}
