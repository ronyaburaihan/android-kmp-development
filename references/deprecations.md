# Deprecated APIs and Practices

**MUST NOT** appear in new code. **SHOULD** be flagged when encountered in existing code, with the replacement named. Do not silently rewrite unrelated code to fix these — report them.

**Verified:** 2026-10-03.

## Security and storage

| Deprecated | Replacement | Notes |
|---|---|---|
| `EncryptedSharedPreferences`, `EncryptedFile`, the whole `androidx.security:security-crypto` (Jetpack Security Crypto) library | DataStore + an explicit crypto layer (Google Tink `StreamingAead`), or Android Keystore directly | [OFFICIAL] Deprecated; no longer recommended or maintained. Reported causes: Keystore behaviour inconsistency across OEMs and OS versions, main-thread StrictMode violations, keyset-corruption crashes. The deprecation date, reasons, and the exact replacement path are [UNVERIFIED] — confirm before writing a migration. See `quality/security.md`. |
| `SharedPreferences` in new code | DataStore Preferences | [DEFAULT] `SharedPreferences` is not formally deprecated but DataStore is the recommended replacement and is the only KMP-capable option. |
| `MODE_WORLD_READABLE`, `MODE_WORLD_WRITABLE` | `MODE_PRIVATE` plus an explicit IPC mechanism | [OFFICIAL] |
| `file://` URIs in `Intent` data | `content://` via `FileProvider` | [OFFICIAL] Throws on modern Android. |
| MD5, SHA-1, DES, RC4 | SHA-512 / HMAC-SHA256 / AES | [OFFICIAL] |

## Build configuration

| Deprecated | Replacement | Notes |
|---|---|---|
| `composeOptions { kotlinCompilerExtensionArgs += [...] }` | The `composeCompiler { }` DSL from the Compose Compiler Gradle plugin | [OFFICIAL] Pre-Kotlin-2.0 mechanism. The Compose stability documentation page still shows this form in places; it is stale — trust <https://developer.android.com/develop/ui/compose/compiler>. |
| `composeCompiler { enableStrongSkippingMode = true }` | Nothing — strong skipping is **on by default since Kotlin 2.0.20** | [OFFICIAL] Only needed below 2.0.20. |
| `getDefaultProguardFile("proguard-android.txt")` | `getDefaultProguardFile("proguard-android-optimize.txt")` | [OFFICIAL] The non-optimize file carries `-dontoptimize`. |
| `android.enableR8.fullMode=false` | Leave R8 full mode on (default since AGP 8.0) | [OFFICIAL] Disabling is explicitly not recommended. |
| `buildSrc` for shared build logic | A `build-logic` included build with convention plugins | [DEFAULT] `buildSrc` invalidates the whole build on change. See `architecture/modularization.md`. |

## Android framework and lifecycle

| Deprecated | Replacement | Notes |
|---|---|---|
| `AndroidViewModel` | `ViewModel` with injected dependencies | [OFFICIAL] "Do not use `AndroidViewModel`" is an explicit Android recommendation. |
| `onBackPressed()` override; handling `KeyEvent.KEYCODE_BACK` | `OnBackPressedDispatcher` / `BackHandler` (Compose) | [OFFICIAL] When targeting API 36, `onBackPressed()` **is not called** and `KEYCODE_BACK` **is not dispatched**. |
| `R.attr#windowOptOutEdgeToEdgeEnforcement` | Proper window-inset handling | [OFFICIAL] Deprecated and disabled at API 36. |
| `elegantTextHeight` | Layouts that do not depend on it | [OFFICIAL] Ignored at API 36. Affects Arabic, Thai, Lao, Myanmar and several Indic scripts. |
| `BODY_SENSORS`, `BODY_SENSORS_BACKGROUND` | Granular `android.permissions.health` permissions (`READ_HEART_RATE`, `READ_HEALTH_DATA_IN_BACKGROUND`) | [OFFICIAL] At API 36. |
| `LiveData` in new code | `StateFlow` | [DEFAULT] Not deprecated, but the official guidance positions `StateFlow` as the recommendation and `LiveData` as the legacy option. |
| Navigation 2.x (`androidx.navigation`) in new apps | Navigation 3 (`androidx.navigation3`) | [UNVERIFIED] that Navigation 2.x is formally in maintenance mode. What is [OFFICIAL]: Navigation 3 is the version named in the current Android architecture recommendations, and Navigation 3 1.2.0 is stable. |
| `GlobalScope` | An injected `CoroutineScope` | [OFFICIAL] |

## Compose Multiplatform

| Deprecated | Replacement | Notes |
|---|---|---|
| `runComposeUiTest`, `runSkikoComposeUiTest`, `runDesktopComposeUiTest` from the non-`v2` package | `androidx.compose.ui.test.v2.runComposeUiTest` | [OFFICIAL] Deprecated as of CMP 1.11. v2 defaults to `StandardTestDispatcher` and accepts `effectContext`. |
| `Window.bindToNavigation()` (web) | `NavController.bindToBrowserNavigation()` | [OFFICIAL] |
| `CanvasBasedWindow` (web) | `ComposeViewport` | [OFFICIAL] |
| Public APIs formerly tagged `ExperimentalMaterial3ExpressiveApi` / `ExperimentalMaterial3ComponentOverrideApi` | Material3 alpha artifact if genuinely needed | [OFFICIAL] Removed in CMP 1.9.x. |

## Kotlin / KMP targets

| Deprecated | Replacement | Notes |
|---|---|---|
| `macosX64` | `macosArm64` | [OFFICIAL] Deprecated since Kotlin 2.3.20. |
| `watchosX64`, `tvosX64` | the `*SimulatorArm64` equivalents | [OFFICIAL] Deprecated since Kotlin 2.3.20. |
| `watchosArm32` | `watchosArm64` | [OFFICIAL] Deprecated since Kotlin 2.4.20. |
| `linuxArm32Hfp` | `linuxArm64` | [OFFICIAL] Deprecated since Kotlin 1.8.20. |
| `iosX64` in new projects | `iosSimulatorArm64` | [OFFICIAL] Not deprecated, but demoted to Tier 3 experimental. |

## Libraries

| Deprecated / unavailable | Replacement | Notes |
|---|---|---|
| Retrofit in `commonMain` | Ktor Client | Retrofit is JVM-bound and will not compile for native targets. Retrofit remains valid for Android-only modules. |
| `androidx.compose.runtime.rxjava2` / `rxjava3` | Flow / `StateFlow` | [OFFICIAL] Not supported in Compose Multiplatform at all. |
| Google Play Billing Library ≤ 7 | v8 minimum, v9 current | [UNVERIFIED] dates — see `version-matrix.md`. A publishing gate, not a style preference. |

## Stale documentation to distrust

These official pages contain statements that are out of date. Do not repeat them.

| Page | Stale statement | Reality |
|---|---|---|
| `multiplatform-expect-actual.html` | "This limitation will be removed in Kotlin 2.0" (about `actual` visibility widening) | Kotlin is at 2.4.20. Verify current behaviour rather than repeating the sentence. |
| `develop/ui/compose/performance/stability` | Shows `composeOptions.kotlinCompilerExtensionArgs` and presents strong skipping as opt-in | Use the `composeCompiler { }` DSL; strong skipping is default since Kotlin 2.0.20. |
