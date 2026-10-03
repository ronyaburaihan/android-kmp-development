# Version Matrix and Compatibility Lockstep

**Verified:** 2026-10-03. Version numbers go stale fast; this file is the single place they live. No other reference document hardcodes a version unless the version is part of a hard compatibility constraint.

## Rule 0

**MUST** declare every dependency version in `gradle/libs.versions.toml` (a Gradle version catalog). **MUST NOT** hardcode a version string in a module `build.gradle.kts`. [DEFAULT — the catalog itself; the lockstep constraints below are OFFICIAL]

## Hard lockstep constraints — MUST be satisfied or the build fails

These are not preferences. Getting them wrong produces a compile error or a cryptic plugin failure.

| Constraint | Rule | Source |
|---|---|---|
| Compose Compiler Gradle plugin ↔ Kotlin | The plugin version **MUST** equal the Kotlin version exactly. Kotlin `2.4.20` → `org.jetbrains.kotlin.plugin.compose` `2.4.20`. | [OFFICIAL] <https://developer.android.com/develop/ui/compose/compiler> |
| Compose Compiler plugin requirement | Kotlin 2.0+ **MUST** apply `org.jetbrains.kotlin.plugin.compose` in every module that uses Compose. The pre-2.0 `composeOptions` mechanism no longer applies. | [OFFICIAL] same |
| KSP ↔ Kotlin | KSP 2.x uses its **own** version line (latest **2.3.12**), no longer `<kotlin>-<ksp>`. **MUST** check the KSP release notes for the Kotlin range a release supports. Verified empirically: KSP 2.3.12 runs under Kotlin 2.4.20 in `examples/user-profile/` (see `examples/README.md`). | [OFFICIAL] <https://github.com/google/ksp/releases> |
| Compose Multiplatform ↔ Kotlin | CMP releases target specific Kotlin versions. CMP `1.12.1` exists specifically to fix Kotlin 2.5 compatibility. **MUST** check the CMP release note for the Kotlin version in use. | [OFFICIAL] <https://blog.jetbrains.com/kotlin/2026/08/compose-multiplatform-1-12-0/> |

## Repositories — MUST

AndroidX artifacts (`androidx.lifecycle`, `androidx.room3`, `androidx.datastore`, `androidx.sqlite`) and the **`androidx.room3` Gradle plugin** are on **Google Maven only**. **MUST** declare `google()` in both `pluginManagement.repositories` and `dependencyResolutionManagement.repositories`, **even in a KMP module with no Android target** — the JetBrains `org.jetbrains.androidx.lifecycle:*` artifacts depend on the AndroidX ones. Verified by two configuration failures while building `examples/user-profile/`.

## Current versions

| Component | Version | Status / notes |
|---|---|---|
| Kotlin | **2.4.20** (2026-09-07) | Stable. 2.4.0 was the language release (2026-06-03). 2.5.0-Beta1 in EAP. |
| Compose Multiplatform | **1.12.1** (2026-09-22) | Android/iOS/desktop **Stable**; **web Beta**. 1.13.0-alpha01 open. |
| Compose Compiler plugin | = Kotlin version | See lockstep above. |
| Android Gradle Plugin | **9.4.0** (2026-09) | Supports up to API 37. |
| Ktor | **3.6.0** (2026-09-16) | Stable. |
| Koin | **4.2.2** (2026-06-15) — `io.insert-koin:koin-bom` **exists** at 4.2.2 | 4.2.0 raised `koin-android` minSdk to **23** and added Navigation 3 support. Verified: `koin-core`, `koin-test`, `koin-compose-viewmodel` all 4.2.2. |
| Room 2 (`androidx.room`, Android line) | **2.8.5** | Stable. KMP: Android, iOS, JVM. |
| Room 3 (`androidx.room3`, KMP-first line) | **3.0.3** stable (2026-09-09); 3.1.0-alpha01 | **Stable.** New major: `androidx.room3.*` packages, SQLiteDriver only, coroutine-only DAOs, KSP only, targets Android/iOS/JVM/web. Verified compiling + tested in `examples/user-profile/`. See `libraries/room-datastore.md`. |
| androidx.sqlite | **2.7.1** (2.8.0-alpha01) | Stable. KMP including web. |
| DataStore | **1.2.1** (1.3.0-alpha11) | Stable. KMP including web. **Preferences only** on KMP. |
| Lifecycle / ViewModel (AndroidX) | **2.11.0** (2.12.0-alpha04) | Stable, KMP. |
| Lifecycle / ViewModel (JetBrains KMP artifacts) | `org.jetbrains.androidx.lifecycle:*` **2.10.0–2.11.0** | Stable. Used from `commonMain`. |
| Navigation 3 | **1.2.0** (2026-09-23) — `androidx.navigation3:navigation3-runtime` / `navigation3-ui` | Stable. CMP support since CMP 1.10 on Android, iOS, desktop, web. `androidx.lifecycle:lifecycle-viewmodel-navigation3` is **2.12.0-alpha04** (alpha). |
| Paging | **3.5.1** | Stable, KMP. |
| kotlinx.coroutines | **1.11.0** (2026-05-08) | [UNVERIFIED] — read from GitHub releases before pinning. |
| kotlinx.serialization | **1.11.0** (2026-04-09) | [UNVERIFIED] — as above. |
| kotlinx-datetime | **0.8.0** (2026-05-08) | Pre-1.0. Kotlin 2.3 moved `kotlin.time.Instant` / `Clock` into the **stdlib** as stable — prefer stdlib time types where they suffice. [UNVERIFIED version] |
| Turbine | **1.2.1** | Stable, full KMP. |
| Play Billing Library | **9** is the latest line | Two-year deprecation cycle. **Publishing gate — see below.** [OFFICIAL] |
| RevenueCat `purchases-kmp` | **3.7.0** | Vendor-maintained. |
| GitLive `firebase-kotlin-sdk` | **2.7.0** | Community-maintained. |
| Compose Preview Screenshot Testing | **0.0.1-alpha16** | Alpha, and the standalone-plugin path is **deprecated** as of AGP 9.5.0-alpha03. |

## Platform floors — MUST satisfy to publish

| Requirement | Value | Status |
|---|---|---|
| Play `targetSdk` for new apps and updates | **API 36** (Android 16) from **2026-08-31**; extension to **2026-11-01**. Wear OS / Automotive ≥ API 35. TV / XR ≥ API 34. **Existing apps** must target ≥ API 35 by the same date or become available only on devices at or below their target. | [OFFICIAL] <https://support.google.com/googleplay/android-developer/answer/11926878> (verified 2026-10-03) |
| Play Billing Library | **v8 or later** for new apps and updates from **2026-08-31**; extension to **2026-11-01**. v8's own deadline is 2027-08-31; v9's 2028-08-31. Unmaintained APKs keep working. | [OFFICIAL] <https://developer.android.com/google/play/billing/deprecation-faq> (verified 2026-10-03) |
| Kotlin/Native Apple minimums | iOS/tvOS **15.0**, macOS **12.0**, watchOS **8.0** by default. | [OFFICIAL] <https://kotlinlang.org/docs/native-target-support.html> |
| Xcode for App Store uploads | **Xcode 14 or later** required for uploads starting in 2026. | [OFFICIAL] <https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds> |

## Kotlin/Native target tiers — affects which targets you MAY declare

[OFFICIAL] <https://kotlinlang.org/docs/native-target-support.html>

| Tier | Meaning | Apple targets |
|---|---|---|
| **Tier 1** | CI-tested (compile + run), source and binary compatibility guaranteed | `iosArm64`, `iosSimulatorArm64`, `macosArm64` |
| **Tier 2** | Compile-tested, best-effort compatibility | `watchosArm64`, `watchosSimulatorArm64`, `tvosArm64`, `tvosSimulatorArm64` |
| **Tier 3** | Experimental, no compatibility guarantees | **`iosX64`**, `watchosDeviceArm64`, `mingwX64`, `androidNative*` |
| **Deprecated** | — | `macosX64` (since 2.3.20), `watchosX64`, `tvosX64` (since 2.3.20), `watchosArm32` (since 2.4.20) |

- **MUST** declare `iosArm64` and `iosSimulatorArm64` for an iOS app. [OFFICIAL — these are the Tier 1 pair]
- **SHOULD NOT** declare `iosX64` unless Intel-simulator support is an explicit requirement. It is Tier 3 experimental. [OFFICIAL]
- **MUST NOT** declare deprecated targets in a new project. [OFFICIAL]

## Known version conflicts to resolve before relying on them

| Item | Conflict | Resolution path |
|---|---|---|
| Koin artifact versions | **Resolved:** `koin-bom` 4.2.2 exists; use it. `koin-androidx-compose` lagged at 4.2.0-RC2 on Maven at verification time — check before depending on it. | — |
| Room line | **Resolved:** both lines are stable — `androidx.room` 2.8.5 (Android) and `androidx.room3` 3.0.3 (KMP-first). Pick one; never mix packages. | `libraries/room-datastore.md` |
| Swift export DSL | Official page documents `swiftExport { }`; KT-87989 deprecates "the legacy Swift Export DSL" for `export { swift { } }`. | Read the current `native-swift-export` page for the Kotlin version in use. See `kmp/ios-interop.md`. |
| SKIE Kotlin compatibility | One source caps SKIE support at Kotlin 2.1.0, which would exclude 2.4.20. | Check SKIE's compatibility matrix. Do not add SKIE without confirming. |
| OkHttp 5 and KMP | A Slack thread states OkHttp 5 dropped Kotlin Multiplatform support. Not confirmed in Square's changelog. | Irrelevant for most projects (Ktor is the KMP answer), but confirm before stating it. |

## Where to re-verify

| Component | Canonical source |
|---|---|
| Kotlin | <https://kotlinlang.org/docs/releases.html> |
| Compose Multiplatform | <https://github.com/JetBrains/compose-multiplatform/releases> |
| AndroidX (all Jetpack libraries) | <https://developer.android.com/jetpack/androidx/versions/all-channel> |
| AndroidX KMP support status | <https://developer.android.com/kotlin/multiplatform> |
| AGP | <https://developer.android.com/build/releases/gradle-plugin> |
| Ktor | <https://github.com/ktorio/ktor/releases> |
| Koin | <https://github.com/InsertKoinIO/koin/releases> |
| Play Billing | <https://developer.android.com/google/play/billing/release-notes> |
| Play policy deadlines | <https://support.google.com/googleplay/android-developer> |
