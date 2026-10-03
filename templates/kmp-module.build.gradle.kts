// Template: a shared KMP library module. Delete what the module does not use.
// Repositories: `google()` is required in BOTH pluginManagement and dependencyResolutionManagement
// for AndroidX artifacts and the androidx.room3 plugin, even with no Android target.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    // alias(libs.plugins.android.library)        // only if this module has an Android target
    // alias(libs.plugins.compose.compiler)       // only if this module has @Composable code; version == Kotlin
    // alias(libs.plugins.ksp)                    // only if Room / annotation processing
    // alias(libs.plugins.room3)
}

kotlin {
    // androidTarget()                           // with android.library plugin
    jvm()
    iosArm64()
    iosSimulatorArm64()                           // Tier 1 pair; do not add iosX64 (Tier 3) by default

    explicitApi()                                 // enforce explicit visibility + return types on the public API

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            // implementation(libs.lifecycle.viewmodel)   // shared ViewModel
            // implementation(libs.room3.runtime); implementation(libs.sqlite.bundled)
            // implementation(libs.datastore.core); implementation(libs.datastore.core.okio); implementation(libs.datastore.preferences.core)
        }
        // androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.koin.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

// Only with Room / KSP. MUST be per target — a bare ksp(...) leaves native targets without generated code.
// dependencies {
//     add("kspAndroid", libs.room3.compiler)
//     add("kspJvm", libs.room3.compiler)
//     add("kspIosArm64", libs.room3.compiler)
//     add("kspIosSimulatorArm64", libs.room3.compiler)
// }
// room { schemaDirectory("$projectDir/schemas") }   // commit the schemas directory
