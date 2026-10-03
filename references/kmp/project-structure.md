# KMP Project Structure

**Scope:** Target declaration, source-set hierarchy, `expect`/`actual`, platform abstraction design, Gradle layout and version lockstep.
**Applies to:** every Kotlin Multiplatform module.
**Official sources:**
- <https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html>
- <https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html>
- <https://kotlinlang.org/docs/native-target-support.html>
- <https://developer.android.com/kotlin/multiplatform>

**Rule levels:** see `../README.md`.

> **Documentation location changed.** The KMP documentation moved from `jetbrains.com/help/kotlin-multiplatform-dev/*` to `kotlinlang.org/docs/multiplatform/*`. Old links 301-redirect. **MUST** cite the `kotlinlang.org` URLs.

---

## Google's position [OFFICIAL]

> "Kotlin Multiplatform (KMP) is officially supported by Google for sharing business logic between Android and iOS. Kotlin Multiplatform is stable and production-ready."

Sharing is a spectrum, not a binary: "you can choose what to share across platforms, from just core business logic to the entire application." Shared UI is JetBrains' Compose Multiplatform, layered on top — see `compose-multiplatform.md`.

AndroidX **Tier 1** KMP support means full CI coverage including on-device tests, with source and binary compatibility tracked. Currently Android, JVM and iOS.

---

## What to share — decide explicitly

**SHOULD** share, in this order of confidence:

1. Domain models, validation, business rules — pure Kotlin, no platform surface.
2. Use cases.
3. Repositories and their mapping logic.
4. Networking (Ktor), persistence (Room / DataStore).
5. ViewModels / presentation state.
6. UI (Compose Multiplatform).

**MUST** treat 6 as a separate decision from 1–5. Sharing logic with native UI is a lower-risk posture than sharing UI, and the two decisions are independent.

---

## Target declaration — MUST

```kotlin
// shared/build.gradle.kts
kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    iosArm64()
    iosSimulatorArm64()
}
```

- **MUST** declare `iosArm64` and `iosSimulatorArm64` for an iOS app — the Tier 1 pair (device + Apple-silicon simulator).
- **SHOULD NOT** declare `iosX64`. It is **Tier 3 experimental** as of current Kotlin. Add it only if Intel-Mac simulator support is an explicit requirement.
- **MUST NOT** declare deprecated targets: `macosX64`, `watchosX64`, `tvosX64`, `watchosArm32`, `linuxArm32Hfp`.
- **MUST** declare only the targets the project actually ships. Every extra target adds compile time, constrains `commonMain` to the intersection of available APIs, and adds a CI job.

Tier table and deprecation dates: `../version-matrix.md`.

---

## Source-set hierarchy

The Kotlin Gradle plugin applies a **default hierarchy template** that generates exactly the intermediate source sets your declared targets justify, with type-safe accessors — no `by getting` / `by creating`.

```kotlin
// CORRECT — type-safe accessors from the default template
kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
        }
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies     { implementation(libs.ktor.client.darwin) }
        commonTest.dependencies  { implementation(libs.kotlin.test) }
    }
}
```

Generated hierarchy for those targets:

```
commonMain
├── androidMain
└── appleMain
    └── iosMain
        ├── iosArm64Main
        └── iosSimulatorArm64Main
```

Only relevant source sets are created — declare no watchOS target and `watchosMain` does not exist.

### Custom intermediates

**MUST** call `applyDefaultHierarchyTemplate()` before manual wiring, or the plugin warns and the template is lost.

```kotlin
// CORRECT
kotlin {
    jvm(); macosArm64(); iosArm64(); iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmAndMacos by creating { dependsOn(commonMain.get()) }
        macosArm64Main.get().dependsOn(jvmAndMacos)
        jvmMain.get().dependsOn(jvmAndMacos)
    }
}

// WRONG — manual dependsOn without reapplying the template; produces a warning
// and silently drops iosMain/appleMain
kotlin {
    jvm(); macosArm64(); iosArm64(); iosSimulatorArm64()
    sourceSets {
        val jvmAndMacos by creating { dependsOn(commonMain.get()) }
        macosArm64Main.get().dependsOn(jvmAndMacos)
    }
}
```

Opting out entirely: `kotlin.mpp.applyDefaultHierarchyTemplate=false` in `gradle.properties`. **SHOULD NOT** do this.

### Combinations that cannot share a source set — MUST know

| Combination | Shared source set possible? |
|---|---|
| JVM/Android + Web + Native | yes |
| JVM/Android + Native | yes |
| Web + Native | yes |
| JVM/Android + Web | yes |
| Native targets only | yes |
| **Multiple JVM targets** | **no** |
| **JVM + Android together** | **no** |
| **Multiple JS targets** | **no** |

For the unsupported cases, `expect`/`actual` is the only option. In practice this means a project with both `jvm()` (desktop) and `androidTarget()` **MUST NOT** expect a `jvmAndAndroidMain` to work.

---

## `expect` / `actual`

Supported for: functions, properties, **classes (Beta)**, interfaces, enums, annotations, objects.

Compiler-enforced rules: every `expect` has a matching `actual` in each platform source set; `expect` carries no implementation; `actual` is in the same package; signatures match.

### MUST prefer an interface over an `expect` class

This is the documentation's own recommendation, not a style preference.

```kotlin
// CORRECT — interface in common, factory via expect fun, implementations per platform
// commonMain
interface DeviceInfo {
    val osVersion: String
    val model: String
}
expect fun deviceInfo(): DeviceInfo

// androidMain
actual fun deviceInfo(): DeviceInfo = AndroidDeviceInfo()
internal class AndroidDeviceInfo : DeviceInfo {
    override val osVersion: String = Build.VERSION.RELEASE
    override val model: String = Build.MODEL
}

// iosMain
actual fun deviceInfo(): DeviceInfo = IosDeviceInfo()
internal class IosDeviceInfo : DeviceInfo {
    override val osVersion: String = UIDevice.currentDevice.systemVersion
    override val model: String = UIDevice.currentDevice.model
}
```

```kotlin
// WRONG — expect class
// commonMain
expect class DeviceInfo() {
    val osVersion: String
    val model: String
}
```

Why the wrong form is a problem, per the official reasoning:

1. It allows **exactly one** implementation per target, so a second implementation (a fake, a variant, a remote-config-driven alternative) is impossible.
2. There is nothing to substitute in a test — the test gets whatever the target's `actual` is.
3. It is Beta and emits a warning. Suppressing it is an explicit opt-in:

```kotlin
kotlin { compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") } }
```

**MUST NOT** add `-Xexpect-actual-classes` to silence the warning without a recorded reason.

### Better still: interface + dependency injection, no `expect` at all

**SHOULD** prefer this when a DI container is already present. It removes `expect`/`actual` from the common module entirely.

```kotlin
// commonMain — no expect declaration
interface DeviceInfo { val osVersion: String; val model: String }

// commonMain — Koin module declares the dependency, platform module provides it
val domainModule = module { /* consumers resolve DeviceInfo */ }
expect fun platformModule(): Module
```

See `../libraries/koin-di.md` for the platform-module pattern.

### Other `expect`/`actual` forms

```kotlin
// Properties
expect val identity: Identity                       // commonMain
actual val identity: Identity = JvmIdentity()       // jvmMain

// Objects
expect object IdentityBuilder { fun build(): Identity }

// typealias as the actual — satisfies an expect with an existing platform type
expect class MyDate { fun getYear(): Int }
actual typealias MyDate = java.time.LocalDate       // jvmMain

// Optional annotation — no actual required on platforms that do not need it
@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
expect annotation class XmlSerializable()
```

**Enums:** a platform `actual enum class` **MAY** add constants beyond the common ones. Consequently a `when` over an `expect enum` in common code **MUST** have an `else` branch.

```kotlin
// commonMain
expect enum class Department { IT, HR, SALES }
// jvmMain
actual enum class Department { IT, HR, SALES, LEGAL }   // extra constant is legal

// commonMain — MUST have else
fun describe(d: Department) = when (d) {
    Department.IT -> "IT"
    Department.HR -> "HR"
    Department.SALES -> "Sales"
    else -> "Other"            // required: platforms may add constants
}
```

**Version-sensitive warning:** the official `expect`/`actual` page contains a statement that an `actual` visibility-widening limitation "will be removed in Kotlin 2.0". Kotlin is well past 2.0. **MUST NOT** repeat that sentence; verify current behaviour against the Kotlin version in use.

---

## File naming — MUST

A file in a platform source set that declares top-level members **MUST** carry a platform suffix. The common file has none.

```
commonMain/kotlin/com/example/DeviceInfo.kt
androidMain/kotlin/com/example/DeviceInfo.android.kt
iosMain/kotlin/com/example/DeviceInfo.ios.kt
jvmMain/kotlin/com/example/DeviceInfo.jvm.kt
```

Source sets are flattened at compile time. Identical names compile, but IDE navigation, stack traces and `git log --follow` all become ambiguous.

---

## Module layout

Recommended shape for a shared-logic project. [DEFAULT — Google's modularization guidance (`../architecture/modularization.md`) is Android-shaped; this is its KMP adaptation]

```
/gradle/libs.versions.toml           single source of versions
/build-logic/                        included build: convention plugins
/androidApp/                         Android application module
/iosApp/                             Xcode project
/shared/                             or /core/* + /feature/* when it grows
  src/commonMain/kotlin
  src/commonTest/kotlin
  src/androidMain/kotlin
  src/androidHostTest/kotlin         host JVM tests
  src/androidDeviceTest/kotlin       instrumented tests
  src/iosMain/kotlin
  src/iosTest/kotlin
```

**MUST NOT** put `androidApp`-only code in `shared`. **MUST NOT** put UI in a module named for logic.

**SHOULD** start with one `shared` module and split when the build or the team demands it. Over-modularization is an explicitly named Android anti-pattern and the cost is higher in KMP, because every module multiplies by the number of targets.

---

## Gradle requirements — MUST

| Requirement | Rule |
|---|---|
| Version catalog | **MUST** declare all versions in `gradle/libs.versions.toml`. |
| Compose Compiler plugin version | **MUST** equal the Kotlin version, in every module with `@Composable` code. |
| KSP version | KSP 2.x has its own version line (not Kotlin-prefixed). **MUST** check its release notes for the supported Kotlin range; 2.3.12 verified under Kotlin 2.4.20. |
| CMP version | **MUST** be checked against the Kotlin version in the CMP release notes. |
| KSP for native targets | **MUST** be added per target: `add("kspIosArm64", ...)`, `add("kspIosSimulatorArm64", ...)`, `add("kspAndroid", ...)`. A single `ksp(...)` does not cover native. |
| Convention plugins | **SHOULD** live in a `build-logic` **included build**, not `buildSrc`. |

```kotlin
// CORRECT — KSP per target (Room, Koin annotations, etc.)
dependencies {
    add("kspAndroid",           libs.room.compiler)
    add("kspIosArm64",          libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

// WRONG — only configures the JVM/Android processor; native targets fail to generate
dependencies {
    ksp(libs.room.compiler)
}
```

---

## Android / iOS differences

| Concern | Android | iOS |
|---|---|---|
| Consumption | Gradle project dependency; `androidMain` sees the full Android SDK | a generated framework; see `ios-interop.md` for the five integration options |
| API visibility | everything `public` in `commonMain` is visible | only what survives Objective-C/Swift export — generics, default arguments and `Flow` do not |
| Test execution | `androidHostTest` (JVM) + `androidDeviceTest` (device) | `iosSimulatorArm64Test`; **`iosArm64` does not run tests** |
| OS floor | `minSdk` in the Gradle config | Kotlin/Native default iOS 15.0; override with `-Xoverride-konan-properties=minVersion.ios=14.0` |
| Dependency availability | any Maven artifact | only KMP-published artifacts with an iOS target |

**MUST** check KMP availability before adding any dependency to `commonMain`. A JVM-only artifact in `commonMain` fails at configuration time for native targets, which produces a confusing error far from the cause.

---

## Testing recommendations [OFFICIAL]

```kotlin
kotlin {
    sourceSets {
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
```

```kotlin
// commonTest — MUST use kotlin.test only
import kotlin.test.Test
import kotlin.test.assertEquals

class PriceFormatterTest {
    @Test
    fun formatsZero() {
        assertEquals("$0.00", format(0))
    }
}
```

- **MUST** use `kotlin-test` (`@Test`, `@BeforeTest`, `@AfterTest`, `assertEquals`, `assertContains`, `assertIs`) in `commonTest`. **MUST NOT** use JUnit annotations, Robolectric, Mockito or any JVM-only API there — it will not compile for native.
- **MUST** put platform-specific tests in the matching source set: `androidHostTest`, `androidDeviceTest`, `iosTest`, `jvmTest`.
- `./gradlew allTests` runs every target's suite and writes a combined report to `build/reports/tests/allTests/index.html`.
- **SHOULD** put the bulk of test coverage in `commonTest`, because it runs on every target on the host JVM and is the fastest loop available.

Full strategy: `../quality/testing-strategy.md`.

---

## Cross-references

- Version lockstep and target tiers: `../version-matrix.md`
- CMP, resources, lifecycle, ViewModel, navigation: `compose-multiplatform.md`
- Framework integration, Objective-C/Swift export constraints: `ios-interop.md`
- Module taxonomy and convention plugins: `../architecture/modularization.md`
- DI platform modules: `../libraries/koin-di.md`
- Domain-layer design that keeps `commonMain` platform-free: `../architecture/clean-architecture.md`
