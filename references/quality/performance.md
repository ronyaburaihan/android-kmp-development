# Performance

**Scope:** R8 shrinking and optimization, Baseline Profiles, Macrobenchmark, startup and frame metrics, KMP-specific performance constraints.
**Applies to:** Android release builds and benchmark variants; the KMP section applies to the shared module.
**Official sources:**
- <https://developer.android.com/build/shrink-code>
- <https://developer.android.com/topic/performance/baselineprofiles/overview>
- <https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview>

**Rule levels:** see `../README.md`. All rules are [OFFICIAL] unless tagged.

> Compose recomposition performance is in `../android/compose-ui.md` and is not repeated here.

---

## R8 — MUST enable on release

R8 performs three operations: code shrinking (tree shaking), logical optimization (inlining, class merging) and obfuscation.

### AGP ≥ 9.3 — the `optimization {}` DSL

```kotlin
android {
    buildTypes {
        release {
            optimization {
                enable = true   // code + resource optimization, default keep rules included
            }
        }
    }
}
```

Custom keep rules go in `src/main/keepRules/custom-rules.keep`.

### AGP < 9.3 — the legacy DSL

```kotlin
android {
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}
```

### Rules

- **MUST** enable minification and resource shrinking on release. `isShrinkResources` requires `isMinifyEnabled`.
- **MUST** use `proguard-android-optimize.txt`, not `proguard-android.txt` — the latter carries `-dontoptimize`.
- **MUST NOT** disable R8 full mode. It is the default since AGP 8.0; `android.enableR8.fullMode=false` is explicitly not recommended.
- **MUST NOT** enable minification for debug or test builds.
- **MUST NOT** post-process R8's output with other tools — it breaks R8's optimizations and Baseline Profiles.

Optimized resource shrinking: `android.r8.optimizedResourceShrinking=true` on AGP 8.12–8.13; automatic from AGP 9.0 when `isShrinkResources = true`.

### Keep rules — MUST be narrow

```proguard
# CORRECT — targeted
-keep class * extends androidx.room3.RoomDatabase { <init>(); }
-keepclasseswithmembernames class * { native <methods>; }
-keep,allowobfuscation @interface com.example.KeepForReflection
-keep @com.example.KeepForReflection class * { *; }
```

```proguard
# WRONG — disables optimization for the entire app
-keep class * { *; }
-keep class com.example.** { *; }
```

Why the wrong form is a problem: a wildcard keep rule prevents shrinking, inlining and obfuscation across everything it matches, so APK size and startup time regress with no error. It also masks the real reflection dependency, so nobody can later tell which class actually needed keeping.

**SHOULD** use the **R8 Configuration Analyzer** to tighten keep rules.

**MUST** add keep rules for: reflection-based code, JNI entry points, serialization classes not handled by a plugin, and library-required rules (Room, and anything the library's own consumer rules do not cover).

### Mapping files — MUST retain

Mapping file location: `build/outputs/mapping/<variant>/mapping.txt`.

- **MUST** archive the mapping file for every release build and upload it to the crash reporter. Without it, release stack traces are unreadable.
- Android Studio Logcat auto-retraces from AGP 8.2+ for local builds.

### Verifying R8 did not break anything — MUST

**MUST** run the full instrumented suite against a **minified** variant before release. Most R8 breakage (reflection, serialization, DI) appears only there and is invisible in a debug build.

---

## Baseline Profiles — SHOULD for any user-facing app

Baseline Profiles enable AOT compilation of hot paths via Profile Guided Optimization.

Documented gains:

| Source | Improvement |
|---|---|
| Baseline Profile | **~30%** code-execution improvement from first launch |
| R8 rule rewriting (AGP 8.2+) | **~15%** additional |
| Startup Profile | **~15%** additional startup improvement |

**SHOULD** use **both** Baseline Profiles and Startup Profiles.

### Setup

```kotlin
plugins { id("androidx.baselineprofile") }

dependencies {
    androidTestImplementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}
```

Minimum recommended: AGP **8.0** (7.4 supported), macrobenchmark **1.5.0+**, profileinstaller **1.4.1+**.

### Generator

```kotlin
class BaselineProfileGenerator {
    @get:Rule val baselineProfileRule = BaselineProfileRule()

    @Test
    fun appStartupAndUserJourneys() {
        baselineProfileRule.collect(packageName = PACKAGE_NAME) {
            uiAutomator {
                startApp(PACKAGE_NAME)
                onElement { textAsString() == "COMPOSE LAZYLIST" }.click()
                onElement { viewIdResourceName == "myLazyColumn" }.also {
                    it.fling(Direction.DOWN)
                    it.fling(Direction.UP)
                }
                pressBack()
            }
        }
    }
}
```

```
./gradlew app:generateBaselineProfile
```

### Critical build configuration — MUST

| Build | Configuration |
|---|---|
| **Profile generation** variant | `isMinifyEnabled = false`, `-dontobfuscate`, `-dontoptimize`. No R8 optimization. |
| **Release** build | `isMinifyEnabled = true`. R8 rewrites the profile rules to match obfuscated names. |

```kotlin
// CORRECT
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    }
    create("benchmarkDexopt") {
        initWith(getByName("release"))
        signingConfig = signingConfigs.getByName("debug")
        isMinifyEnabled = false          // MUST be false for accurate profile matching
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    }
}
```

```kotlin
// WRONG — generating a profile from an obfuscated build
create("benchmarkDexopt") {
    initWith(getByName("release"))      // inherits isMinifyEnabled = true
    signingConfig = signingConfigs.getByName("debug")
}
```

Why the wrong form is a problem: the profile records obfuscated method names from the generation build, which do not match the obfuscated names in the release build (obfuscation is not stable across builds). The profile silently fails to match and the ~30% gain disappears with no error.

### What to profile

**SHOULD** cover app startup, critical navigation paths, scrolling and list operations, and the registration / login / payment flows — anywhere latency is user-visible.

`includeInStartupProfile = true` only for genuinely critical startup scenarios.

### Known issues — MUST account for

- **OnePlus devices:** disable "Disable permission monitoring" in Developer Options.
- **Huawei and similar:** battery optimizations block profile installation.
- **Play internal app sharing is unsupported** for Baseline Profiles — use the internal testing **track** instead.
- Library Baseline Profiles need AGP 8.3+ or Baseline Profile Gradle plugin 1.2.3+.

File placement (AGP 8.0+): `src/main/baselineProfiles/baseline-prof.txt`, variant-aware.

---

## Macrobenchmark

Measures app-level behaviour: startup, frame timing, custom trace sections.

### Setup — MUST

```kotlin
// app/build.gradle.kts
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    }
    create("benchmark") {
        initWith(getByName("release"))
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += listOf("release")   // required in multi-module projects
    }
}
```

```xml
<!-- app manifest, benchmark variant -->
<profileable android:shell="true" />
```

```kotlin
dependencies { implementation("androidx.profileinstaller:profileinstaller:1.3.0+") }
```

Create the benchmark module from the Android Studio **Benchmark** template (a `com.android.test` module), and select the `benchmark` build type for both the app and the macrobenchmark module.

### Startup benchmark

```kotlin
@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
    ) {
        uiAutomator { startApp(PACKAGE_NAME) }
    }
}
```

### Frame-timing (scroll) benchmark

```kotlin
@LargeTest
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollFeed() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        val list = device.findObject(By.res("feed_list"))
        list.setGestureMargin(device.displayWidth / 5)
        list.fling(Direction.DOWN)
    }
}
```

**MUST** set `testTagAsResourceId = true` in the app so UI Automator can find Compose nodes by `testTag`. Without it `By.res(...)` finds nothing.

### Metrics, modes and iterations

| Metric | Measures |
|---|---|
| `StartupTimingMetric` | cold/warm/hot startup time |
| `FrameTimingMetric` | frame drops and jank |
| `TraceSectionMetric` | custom trace sections |

| `CompilationMode` | Behaviour |
|---|---|
| `DEFAULT` | installs the Baseline Profile if present (Android 7+) |
| `FULL` | complete AOT compilation |
| `PARTIAL` | partial pre-compilation |
| `NONE` | JIT only |
| `IGNORE` | skip compilation changes between runs |

| `StartupMode` | Behaviour |
|---|---|
| `COLD` | kills the app between setup and measurement — **the process does not survive `setupBlock`** |
| `WARM` | restarts activities, keeps the process alive |
| `HOT` | app already foregrounded |

`iterations` is typically 3–10; results are aggregated.

### Rules — MUST

- **MUST** benchmark on **physical devices**. Emulator numbers are not meaningful.
- **MUST** benchmark a **non-debuggable, minified** build.
- **MUST** make the app profileable.
- **MUST NOT** benchmark a device on low battery.
- **SHOULD** use `WARM` rather than `COLD` when the process must survive `setupBlock`.
- **SHOULD** run benchmarks in CI on a dedicated device, on a schedule rather than per-PR — they are slow and device-sensitive. See `testing-strategy.md`.

Outputs: JSON metrics in `build/outputs/connected_android_test_additional_output/`, plus Perfetto traces linked from the test results pane.

---

## KMP performance constraints

### Objective-C interop conversion cost — MUST know

Kotlin collections and strings cross two boundaries on their way to Swift: Kotlin → Objective-C → Swift. [OFFICIAL]

```swift
// CORRECT — one conversion
let nsMap: NSDictionary = kotlinMap as NSDictionary

// IMPLICITLY DOUBLE-CONVERTS, per element
let swiftMap = kotlinMap
```

**MUST NOT** return a large collection from Kotlin to Swift on a hot path (a list-cell binding, a scroll callback) without measuring. The same applies to `String`.

**SHOULD** design the iOS facade to return few, small values rather than large graphs. See `../kmp/ios-interop.md`.

### R8 does not apply to the iOS framework — MUST know

R8 is Android-only. The Kotlin/Native framework is **not** shrunk or obfuscated by it. Implications:

- Dead code in `commonMain` ships to iOS at full size.
- Strings in `commonMain` are readable in the iOS binary. See `security.md`.

### Baseline Profiles cover Android only

There is no iOS equivalent. iOS startup performance is addressed by reducing work in `application(_:didFinishLaunchingWithOptions:)` and by deferring framework initialization — not by a profile.

### CMP on iOS

- **Concurrent rendering is enabled by default** since CMP 1.11.
- `Modifier.preferredFrameRate()` trades smoothness against battery.
- Compose Multiplatform resources load **synchronously on the calling thread** for most types. **MUST NOT** load a large resource during composition; use `Res.getUri(...)`. See `../kmp/compose-multiplatform.md`.

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| Shipping release without R8 | larger APK, slower startup, no obfuscation |
| `proguard-android.txt` | `-dontoptimize`; optimization silently off |
| `-keep class * { *; }` | optimization disabled app-wide, silently |
| Disabling R8 full mode | leaves optimization on the table |
| Not archiving `mapping.txt` | unreadable release crash reports |
| Generating a Baseline Profile from a minified build | profile silently fails to match; gain lost |
| Benchmarking an emulator or a debug build | numbers that justify the wrong change |
| Missing `<profileable>` | no trace data; benchmark produces nothing useful |
| Missing `testTagAsResourceId` | UI Automator cannot find Compose nodes |
| Play internal app sharing for Baseline Profile validation | unsupported; use the internal testing track |
| Returning large Kotlin collections to Swift per frame | per-element double conversion on a hot path |
| Loading a large CMP resource during composition | blocks the calling thread |
| Only running the instrumented suite against a debug build | R8 breakage reaches production |

---

## Testing recommendations

- **MUST** run the instrumented suite against the **minified** release-like variant as a release gate.
- **MUST** regenerate Baseline Profiles when a critical flow changes. A stale profile optimizes code paths the app no longer takes.
- **SHOULD** track `StartupTimingMetric` and `FrameTimingMetric` over time on a fixed device and treat a regression beyond a defined threshold as a build failure in the scheduled job.
- **SHOULD** verify APK/AAB size in CI and alert on a jump — a size regression is usually a new transitive dependency or a newly broad keep rule.
- **SHOULD** read the Compose compiler stability report alongside frame metrics when investigating jank, rather than guessing. See `../android/compose-ui.md`.
- **MUST NOT** treat per-PR benchmark numbers from shared CI runners as signal. Device and runner variance exceeds most real regressions.

---

## Cross-references

- Compose recomposition, stability, strong skipping: `../android/compose-ui.md`
- Keep rule for Room: `../libraries/room-datastore.md`
- Benchmark job placement in CI: `testing-strategy.md`
- R8 and the iOS framework's lack of obfuscation, secret exposure: `security.md`
- Release build configuration and mapping upload: `../release/android-release.md`
- Interop conversion costs in full: `../kmp/ios-interop.md`
- AGP version behaviour: `../version-matrix.md`
