# Android + Kotlin Multiplatform Engineering Standards — Research Report

**Purpose:** Evidence base for the `android-kmp-development` AI development skill.
**Verification date:** 2026-10-03 (all version numbers and status claims verified on this date)
**Method:** Official documentation first (kotlinlang.org, developer.android.com, ktor.io, insert-koin.io, developer.apple.com). Vendor/community sources used only where no official source exists, and labelled as such.

## How to read this report

Every claim carries a label:

| Label | Meaning |
|---|---|
| **[OFFICIAL]** | Stated in first-party documentation from the owner of the technology (Google for Android/AndroidX, JetBrains for Kotlin/KMP/Compose Multiplatform/Ktor, Apple for App Store, Koin team for Koin). |
| **[PREFERENCE]** | Engineering convention widely used in production, *not* an official recommendation. Defensible but contestable. |
| **[VERIFY]** | Found in a secondary source or a source that conflicts with another; must be re-checked before being baked into the skill. |
| **[DEPRECATED]** | Officially deprecated, removed, or superseded. |

---

## Version matrix (verified 2026-10-03)

| Component | Version | Status | Source |
|---|---|---|---|
| Kotlin | **2.4.20** (2026-09-07) | Stable. 2.4.0 was the language release (2026-06-03); 2.5.0-Beta1 in EAP | [kotlinlang.org/docs/releases](https://kotlinlang.org/docs/releases.html) |
| Compose Multiplatform | **1.12.1** (2026-09-22, Kotlin 2.5 compat fixes); 1.12.0 (2026-08); 1.13.0-alpha01 open | Stable on Android/iOS/desktop; **Web = Beta** | [JetBrains blog 1.12.0](https://blog.jetbrains.com/kotlin/2026/08/compose-multiplatform-1-12-0/) |
| Compose Compiler Gradle plugin | Must equal the Kotlin version (e.g. `2.4.20`) | Stable; required for Kotlin 2.0+ | [developer.android.com/develop/ui/compose/compiler](https://developer.android.com/develop/ui/compose/compiler) |
| Android Gradle Plugin | **9.4.0** (2026-09); supports up to API 37 | Stable | [AGP 9.4.0 notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes) |
| Play `targetSdk` floor | **API 36** (Android 16) for new apps and updates from **2026-08-31**; extension to 2026-11-01 | Enforced | [VERIFY — secondary sources; confirm on Play Console help] |
| Ktor | **3.6.0** (2026-09-16) | Stable | [ktor.io KMP client guide](https://ktor.io/docs/client-create-multiplatform-application.html) |
| Koin | **4.2.2** (2026-06-15); 4.2.0 added Navigation 3 + Ktor 3.4 DI bridge | Stable | [github.com/InsertKoinIO/koin/releases](https://github.com/InsertKoinIO/koin/releases) |
| Room (Android) | **2.8.5** | Stable; KMP: Android/iOS/JVM, **no web** | [developer.android.com/kotlin/multiplatform](https://developer.android.com/kotlin/multiplatform) |
| Room 3 (`androidx.room3`) | **3.1.0-alpha01** | Alpha — the KMP guide's current sample uses it | [developer.android.com/kotlin/multiplatform/room](https://developer.android.com/kotlin/multiplatform/room) |
| androidx.sqlite | 2.7.1 (2.8.0-alpha01) | Stable, KMP incl. web | same |
| DataStore | **1.2.1** (1.3.0-alpha11) | Stable, KMP incl. web. **Preferences only** on KMP | [developer.android.com/kotlin/multiplatform/datastore](https://developer.android.com/kotlin/multiplatform/datastore) |
| Lifecycle / ViewModel (AndroidX) | 2.11.0 (2.12.0-alpha04) | Stable, KMP | [developer.android.com/kotlin/multiplatform](https://developer.android.com/kotlin/multiplatform) |
| Lifecycle / ViewModel (JetBrains KMP artifacts) | `org.jetbrains.androidx.lifecycle:*` 2.10.0–2.11.0 | Stable | [CMP lifecycle docs](https://kotlinlang.org/docs/multiplatform/compose-lifecycle.html) |
| Navigation 3 | **1.2.0** (2026-09-23) stable; 1.3.0-alpha01 | Stable. Navigation 2.x is in maintenance mode | [VERIFY — androidx release page; maintenance-mode claim is secondary] |
| Navigation 3 on CMP | Supported since **CMP 1.10** on Android, iOS, desktop, web | Stable | [Navigation 3 in CMP](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html) |
| Paging | 3.5.1 | Stable, KMP | [developer.android.com/kotlin/multiplatform](https://developer.android.com/kotlin/multiplatform) |
| kotlinx.coroutines | **1.11.0** (2026-05-08) | Stable | [VERIFY — GitHub releases] |
| kotlinx.serialization | **1.11.0** (2026-04-09) | Stable | [VERIFY — GitHub releases] |
| kotlinx-datetime | **0.8.0** (2026-05-08) | Pre-1.0; `TimeZone` serialization deprecated. Kotlin 2.3 moved core time types (`kotlin.time.Instant`, `Clock`) into stdlib as stable | [VERIFY] |
| Turbine | **1.2.1** | Stable, full KMP | [github.com/cashapp/turbine](https://github.com/cashapp/turbine) |
| Play Billing Library | **9.0.0** (2026-05-19) current major; 8.3.0 last of the v8 line. v8+ required to publish from 2026-08-31 | Enforced | [VERIFY — secondary sources; confirm on Play Billing release notes] |
| RevenueCat `purchases-kmp` | 3.7.0 | Stable, vendor-maintained | [github.com/RevenueCat/purchases-kmp](https://github.com/RevenueCat/purchases-kmp) |
| GitLive `firebase-kotlin-sdk` | 2.7.0 | Community-maintained | [github.com/GitLiveApp/firebase-kotlin-sdk](https://github.com/GitLiveApp/firebase-kotlin-sdk) |
| SKIE (Touchlab) | 0.10.15 | Third-party; **[VERIFY]** — one source claims Kotlin support caps at 2.1.0, which would exclude Kotlin 2.4. Must confirm before recommending | [github.com/touchlab/SKIE/releases](https://github.com/touchlab/SKIE/releases) |
| Compose Preview Screenshot Testing plugin | 0.0.1-alpha16 | **Alpha, and the standalone plugin path is deprecated** as of AGP 9.5.0-alpha03 in favour of AGP test suites | [developer.android.com/studio/preview/compose-screenshot-testing](https://developer.android.com/studio/preview/compose-screenshot-testing) |

> **Version-pinning caution for the skill:** version numbers rot fast. The skill should teach *where to look* (version catalog + the release-notes URLs above) and reserve hard-pinned versions for the handful of places where a wrong version breaks the build (Compose Compiler plugin ↔ Kotlin, KSP ↔ Kotlin, CMP ↔ Kotlin).

---

## 1. Kotlin language and coding conventions

**Official documentation:** <https://kotlinlang.org/docs/coding-conventions.html>

### Recommended practices [OFFICIAL]

- **Naming.** Packages lowercase, no underscores. Classes/objects UpperCamelCase. Functions/properties lowerCamelCase. `const val` and top-level immutable `val` in `SCREAMING_SNAKE_CASE`. Enum constants may be either `SCREAMING_SNAKE_CASE` or UpperCamelCase. Backing properties prefixed with `_`. Acronyms: two letters both capital (`IOStream`), three or more capitalise only the first (`XmlFormatter`, `HttpInputStream`).
- **`@Composable` functions use *class* naming** (UpperCamelCase) — this is in the Kotlin conventions themselves, not just a Compose convention. Factory functions may also use class naming.
- **Multiplatform file naming.** Platform-specific files with top-level declarations take a platform suffix: `Platform.kt` (common), `Platform.android.kt`, `Platform.jvm.kt`, `Platform.ios.kt`.
- **Class member order:** properties and initializer blocks → secondary constructors → methods → companion object. Nested classes go after, if used externally. Interface members keep the interface's order. Overloads are always grouped.
- **Formatting.** 4 spaces, no tabs. Egyptian braces. Spaces around binary operators but not `..`, `?.`, `.`, `::`, or before `?` in a nullable type. Explicit modifier order is specified (see the doc's list; `public/protected/private/internal` → `expect/actual` → `final/open/abstract/sealed/const` → … → `data`).
- **Trailing commas recommended** for declarations, enums, collection literals, `when` entries — cleaner diffs and easier reordering.
- **Idioms.** `val` over `var`. Immutable collection *types* (`List`, `Set`, `Map`) over `ArrayList`/`HashSet`; `listOf()` over `arrayListOf()`. Default parameters over overloads. Expression bodies for single-expression functions. `if` for binary conditions, `when` for three or more. Named arguments when several parameters share a primitive type, and for `Boolean` parameters. String templates over concatenation; no braces for a simple variable (`"$name"`). Open-ended ranges `0..<n` instead of `0..n-1`. Higher-order functions over manual loops — **but `forEach` is the exception: prefer a regular `for` loop** unless the receiver is nullable or it's part of a chain.
- **Property vs function:** prefer a property when the computation doesn't throw, is cheap or cached, and returns the same result for unchanged state.
- **Library/API code (applies to any `commonMain` module consumed by other modules):** always specify visibility explicitly, always specify return and property types explicitly, and KDoc all public members.

### Anti-patterns [OFFICIAL]

- Meaningless type names: `Manager`, `Wrapper`, `Util`; files named `Util.kt`.
- `"${name}"` where `"$name"` suffices; redundant `: Unit`; unnecessary semicolons.
- Horizontal alignment of declarations.
- `when` used for a binary condition.
- Several overloaded constructors where factory functions with distinct names would read better.
- `@param`/`@return` KDoc tags — the convention is to describe parameters inline in the prose.
- Multiple labelled returns inside a lambda; a labelled return as the last statement.

### Platform-specific considerations

- The `.ios.kt` / `.android.kt` suffix convention exists *because* the compiler flattens source sets; identical file names across source sets are legal but make navigation and stack traces ambiguous. [OFFICIAL]
- **Kotlin 2.3 added a return-value checker:** compiled files are treated as if annotated `@MustUseReturnValues` and unused return values are reported. This changes what "idiomatic" looks like for builder-style and `Result`-returning APIs. [OFFICIAL — [kotlin 2.3.0 blog](https://blog.jetbrains.com/kotlin/2025/12/kotlin-2-3-0-released/), [whatsnew23](https://kotlinlang.org/docs/whatsnew23.html)]

### Tooling [PREFERENCE]

No official Google or JetBrains mandate exists for a specific linter. The widely-used production setup is **ktlint** for formatting (it implements the official Kotlin style guide and is zero-config) plus **detekt** for smells and complexity, both in CI; **Spotless** only as the umbrella when the repo is polyglot. Android Lint remains separate and is official for Android-specific checks.

### Testing recommendations

Conventions are enforced by tooling, not tests: `ktlintCheck` / `detekt` as CI gates. Treat formatting failures as non-negotiable and detekt findings as reviewable.

### Sources

- <https://kotlinlang.org/docs/coding-conventions.html>
- <https://kotlinlang.org/docs/whatsnew23.html>
- <https://blog.jetbrains.com/kotlin/2025/12/kotlin-2-3-0-released/>
- <https://www.pistack.xyz/posts/2026-08-20-kotlin-linting-tools-detekt-ktlint-spotless-comparison/> (secondary, tooling preference only)

---

## 2. Android application architecture

**Official documentation:**
- <https://developer.android.com/topic/architecture>
- <https://developer.android.com/topic/architecture/recommendations>
- <https://developer.android.com/topic/modularization>

### The four principles [OFFICIAL]

1. **Separation of concerns.** Don't put logic in Activities — they are ephemeral and unsuitable for holding state.
2. **Drive the UI from data models, preferably persistent ones.** Survives process death and intermittent connectivity.
3. **Single source of truth (SSOT).** Every data type has one owner; only the owner mutates it.
4. **Unidirectional data flow (UDF).** State flows down, events flow up to the SSOT.

### Layers [OFFICIAL]

- **UI layer** — Compose UI plus state holders (`ViewModel` for screens, plain state holder classes for reusable components).
- **Domain layer** (optional; **recommended in big apps**) — use cases / interactors, one responsibility each. Use it to reuse logic across ViewModels or to cut ViewModel complexity.
- **Data layer** — repositories expose data and own business logic; data sources each talk to exactly one source. Repositories centralise changes, resolve multi-source conflicts, and abstract sources away.

### The recommendation list, with Google's own priority labels [OFFICIAL]

**Strongly recommended**
- A clearly defined data layer; a clearly defined UI layer.
- Expose data from the data layer **through a repository** — composables and ViewModels must never touch a database, DataStore, SharedPreferences, Firebase API, location provider, Bluetooth, or connectivity status directly.
- Coroutines and Flow for inter-layer communication.
- UDF: ViewModel exposes state via the observer pattern, receives actions as method calls.
- AAC `ViewModel` for business logic and state.
- Collect state with **`collectAsStateWithLifecycle()`**.
- **Do not send events from ViewModel to UI.** Handle the event in the ViewModel and emit a new state.
- Single-activity app; navigate with **Navigation 3**.
- Jetpack Compose for new UI (phones, tablets, foldables, Wear OS).
- Keep ViewModels free of Android lifecycle types (no `Activity`, `Context`, `Resources`).
- ViewModels at screen level / Navigation destinations; `rememberViewModelStoreOwner()` for complex dynamic reusable composables.
- Plain state-holder classes in reusable UI components.
- Dependency injection, constructor injection by preference; scope to a container when the type holds shared mutable data or is expensive to create.
- Lifecycle-aware effects (`LifecycleStartEffect`, `LifecycleResumeEffect`, `repeatOnLifecycle`) instead of overriding Activity callbacks.
- Testing: know what to test (ViewModels incl. flows, data-layer entities, navigation regression); **prefer fakes to mocks**; test `StateFlow` by asserting `.value` where possible.

**Recommended**
- Domain layer in big apps.
- Expose a single `uiState: StateFlow<…>` per ViewModel; use `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)` for data streams.
- **Do not use `AndroidViewModel`** — use `ViewModel`; keep `Application` out of it.
- Hilt (or manual DI for simple apps).
- A model per layer in complex apps (network DTO → domain → `UiState`).

**Optional (naming)**
- Methods = verb phrases (`makePayment()`). Properties = noun phrases. Streams = `get{Model}Stream()`, plural for lists. Implementations get meaningful names (`OfflineFirstNewsRepository`, `InMemoryNewsRepository`), `Default` prefix as fallback, `Fake` prefix for test doubles.

### Anti-patterns [OFFICIAL]

- Activities/services/broadcast receivers as data sources.
- Android SDK types (`Context`, `Toast`) leaking beyond app components.
- Network/loading logic spread across packages.
- Exposing internal implementation details as a shortcut.
- Exposing mutable types (`MutableStateFlow`, `MutableLiveData`) from a ViewModel.
- Types that don't own their concurrency policy — every type should be **main-safe**.
- `AndroidViewModel`.
- Sending one-off events from ViewModel to UI instead of folding them into state.

### Modularization [OFFICIAL]

Benefits: reusability, visibility control (`internal`), customizable delivery (Play Feature Delivery), scalability, ownership, encapsulation, testability, build performance. Pitfalls named explicitly: **too fine-grained** (build overhead and boilerplate swamp the benefit), **too coarse-grained** (a monolith with extra steps), **too complex** (modularization doesn't suit small projects). Google's position: modularization is **optional** unless you need cross-app reuse, strict visibility control, or Play Feature Delivery. Typical shape: `:app`, `:feature:*`, `:core:*`. Keep the graph acyclic and invert dependencies across boundaries.

**Reference implementation [OFFICIAL sample]:** `android/nowinandroid` — 50+ modules, convention plugins in `build-logic`, API/Impl split in feature modules. `android/architecture-samples` (multimodule branch) is the smaller example.

### Platform-specific considerations

- This guide is **Android-specific**. Hilt, `AndroidViewModel`, `collectAsStateWithLifecycle`'s Android implementation, and Navigation 3's Android artifacts don't all carry to KMP unchanged — see §3.
- Google's architecture advice is *compatible* with KMP, but Hilt is JVM/Android-only, so a KMP project substitutes a multiplatform DI container (§8).

### Testing recommendations [OFFICIAL]

Minimum coverage: ViewModels including Flow emissions; data-layer entities (repositories and data sources); UI navigation regression tests. Fakes over mocks. For `stateIn(WhileSubscribed())` state, tests must subscribe or the flow never starts.

### Code example — canonical ViewModel [OFFICIAL, from the recommendations page]

```kotlin
@HiltViewModel
class BookmarksViewModel @Inject constructor(
    newsRepository: NewsRepository
) : ViewModel() {

    val feedState: StateFlow<NewsFeedUiState> =
        newsRepository
            .getNewsResourcesStream()
            .mapToFeedState(savedNewsResourcesState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = NewsFeedUiState.Loading,
            )
}
```

```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
}
```

### Sources

- <https://developer.android.com/topic/architecture>
- <https://developer.android.com/topic/architecture/recommendations>
- <https://developer.android.com/topic/modularization>
- <https://developer.android.com/topic/modularization/patterns>
- <https://github.com/android/nowinandroid>

---

## 3. Kotlin Multiplatform architecture

**Official documentation:**
- <https://kotlinlang.org/docs/multiplatform/> (note: the KMP docs moved from `jetbrains.com/help/kotlin-multiplatform-dev/*` to `kotlinlang.org/docs/multiplatform/*`; old URLs 301-redirect)
- <https://developer.android.com/kotlin/multiplatform> (Google's position)
- <https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html>
- <https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html>
- <https://kotlinlang.org/docs/native-target-support.html>

### Google's stated position [OFFICIAL]

> "Kotlin Multiplatform (KMP) is officially supported by Google for sharing business logic between Android and iOS. Kotlin Multiplatform is stable and production-ready."

Google frames the choice as a spectrum: "you can choose what to share across platforms, from just core business logic to the entire application." Shared UI is JetBrains' Compose Multiplatform, layered on top. Named production adopters on Google's page: Blinkit, Cash App, Duolingo, Forbes, Google Docs, JioHotstar, Stone, Swiggy, Ultrahuman, Wrike, Zomato.

AndroidX **Tier 1** for KMP means full CI coverage including on-device tests, with source and binary compatibility tracked — currently Android, JVM, iOS.

### Source-set hierarchy [OFFICIAL]

The Kotlin Gradle plugin applies a **default hierarchy template** that generates only the intermediate source sets your declared targets justify (`iosMain`, `appleMain`, `nativeMain`, …) and gives type-safe accessors — no `by getting` / `by creating`:

```kotlin
kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies { /* … */ }
        iosMain.dependencies  { /* … */ }
    }
}
```

Custom intermediates need `applyDefaultHierarchyTemplate()` *before* the manual wiring:

```kotlin
kotlin {
    jvm(); macosArm64(); iosArm64(); iosSimulatorArm64()
    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmAndMacos by creating { dependsOn(commonMain.get()) }
        macosArm64Main.get().dependsOn(jvmAndMacos)
        jvmMain.get().dependsOn(jvmAndMacos)
    }
}
```

**Shared source sets are not possible for every combination.** Supported: JVM/Android + Web + Native; JVM/Android + Native; Web + Native; JVM/Android + Web; native-only. **Not supported:** multiple JVM targets, JVM + Android together, multiple JS targets. Those cases need `expect`/`actual`.

### `expect` / `actual` [OFFICIAL]

Supported for functions, properties, classes (**Beta**), interfaces, enums, annotations, objects. Compiler enforces: every `expect` has a matching `actual` per platform source set; `expect` carries no implementation; same package; matching signatures.

**The documentation's own recommendation is to prefer interfaces + dependency injection over `expect`/`actual` classes**, because expect/actual classes limit you to one implementation per target, are harder to fake in tests, and forgo standard language constructs:

```kotlin
// commonMain
interface Identity {
    val userName: String
    val processID: Long
}
expect fun buildIdentity(): Identity

// jvmMain
actual fun buildIdentity(): Identity = JVMIdentity()
class JVMIdentity(
    override val userName: String = System.getProperty("user.name") ?: "none",
    override val processID: Long = ProcessHandle.current().pid(),
) : Identity

// nativeMain
actual fun buildIdentity(): Identity = NativeIdentity()
```

Expected/actual **classes are Beta** and emit a warning; suppressing it is explicit opt-in:

```kotlin
kotlin { compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") } }
```

Other mechanics: platform `actual enum class` may add extra constants (so common `when` needs `else`); `@OptionalExpectation` makes an `actual annotation` optional per platform; an `actual typealias` can satisfy an `expect` with an existing platform type (`actual typealias MyDate = java.time.LocalDate`).

> **[VERIFY]** The expect/actual page still carries a sentence saying a visibility-widening limitation "will be removed in Kotlin 2.0". Kotlin is at 2.4.20; that line is stale doc text. Confirm current behaviour before repeating it.

### Native target tiers [OFFICIAL]

- **Tier 1** (CI-tested, source+binary compatibility guaranteed): `macosArm64`, `iosSimulatorArm64`, `iosArm64`.
- **Tier 2** (compile-tested, best-effort compatibility): `linuxX64`, `linuxArm64`, `watchosSimulatorArm64`, `watchosArm64`, `tvosSimulatorArm64`, `tvosArm64`.
- **Tier 3** (experimental, no guarantees): `androidNative*`, `mingwX64`, `watchosDeviceArm64`, **`iosX64`**.
- **Deprecated:** `watchosArm32` (since 2.4.20), **`macosX64`** (since 2.3.20), `watchosX64`, `tvosX64` (since 2.3.20), `linuxArm32Hfp`.

Default minimum OS versions: iOS/tvOS 15.0, macOS 12.0, watchOS 8.0. Overridable via `-Xoverride-konan-properties=minVersion.ios=14.0`.

**Consequence for the skill:** declaring `iosX64` (Intel simulator) is now an *experimental-tier* choice; `iosArm64 + iosSimulatorArm64` is the Tier-1 pair. Don't template `iosX64` by default.

### Recommended practices

- Share business logic first (models, repositories, use cases, networking, persistence, validation); decide about UI separately. [OFFICIAL — Google frames it as a spectrum]
- Keep `commonMain` free of platform types; put the platform edge behind an interface defined in `commonMain`. [OFFICIAL]
- Lean on the default hierarchy template; reach for `dependsOn` only for genuinely unsupported groupings. [OFFICIAL]
- Use a Gradle **version catalog** for the Kotlin ↔ Compose-compiler ↔ KSP ↔ CMP version lockstep. [PREFERENCE, but the lockstep requirement itself is OFFICIAL]
- Convention plugins in a `build-logic` included build for multi-module KMP. [PREFERENCE, modelled on the official `nowinandroid` sample]

### Anti-patterns

- `expect`/`actual` **classes** as the default platform-abstraction tool, where an interface + DI would do. [OFFICIAL]
- Trying to share code between a JVM target and an Android target via an intermediate source set — unsupported. [OFFICIAL]
- Treating `commonMain` as a dumping ground for everything, including UI, when only logic needs sharing.  [PREFERENCE]
- Pinning CMP and Kotlin to versions that aren't compatible (CMP 1.12.1 exists specifically to fix Kotlin 2.5 compatibility). [OFFICIAL]

### Testing recommendations [OFFICIAL]

`commonTest` with `kotlin-test` only — no framework-specific API in common tests. `@Test`/`@BeforeTest`/`@AfterTest` and `assertEquals`/`assertContains` map to each platform's runner. Platform-specific tests go in `androidHostTest` (host JVM), `androidDeviceTest` (instrumented), `iosTest`, `jvmTest`. `./gradlew allTests` runs everything and writes a combined HTML report to `build/reports/tests/allTests/index.html`.

### Sources

- <https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html>
- <https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html>
- <https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html>
- <https://kotlinlang.org/docs/native-target-support.html>
- <https://developer.android.com/kotlin/multiplatform>

---

## 4. Compose and Compose Multiplatform

**Official documentation:**
- <https://developer.android.com/develop/ui/compose/architecture>
- <https://developer.android.com/develop/ui/compose/performance/bestpractices>
- <https://developer.android.com/develop/ui/compose/performance/stability>
- <https://developer.android.com/develop/ui/compose/performance/stability/strongskipping>
- <https://developer.android.com/develop/ui/compose/compiler>
- <https://kotlinlang.org/docs/multiplatform/compose-multiplatform-and-jetpack-compose.html>
- <https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources.html>

### CMP ↔ Jetpack Compose relationship [OFFICIAL]

Same compiler and runtime, same API surface (`@Composable`, `remember`, modifiers, animation). JetBrains owns CMP; Google owns Jetpack Compose. On Android, a `compose.material3` dependency **resolves to Google's `androidx.compose.material3:material3`**; on other targets it resolves to `org.jetbrains.compose.material3:material3`. The switch happens through Gradle Module Metadata.

Multiplatform-ready: Compose Animation, Compiler, Foundation, Material, Material 3, Runtime (except the `rxjava2`/`rxjava3` modules), UI, Jetpack Lifecycle, Jetpack ViewModel, Navigation Compose. **Android-only:** Maps Compose, the RxJava runtime modules.

### Stability per target (CMP 1.12.x) [OFFICIAL]

| Target | Status |
|---|---|
| Android | Stable |
| iOS | **Stable** since CMP 1.8.0 (May 2025) |
| Desktop | Stable |
| Web (Wasm) | **Beta** |

### State and architecture guidance [OFFICIAL]

- UDF: events up, state down. Immutable UI driven by observable state.
- `remember { mutableStateOf(…) }` for local UI state; `rememberSaveable` to survive configuration change; `StateFlow`/`ViewModel` for screen state.
- **Hoist state.** Pass immutable values plus event lambdas, which keeps composables reusable and stops them mutating state directly.
- **Pass the minimum.** `Header(title: String, subtitle: String)` recomposes less than `Header(news: News)`.
- Represent every input as an event with a handler.
- The data and domain layers are unaffected by adopting Compose — UDF here is a *UI-layer* pattern.

### Performance practices [OFFICIAL]

1. `remember(keys) { … }` around expensive work — better still, move it out of composition entirely (ViewModel).
2. **Lazy-list `key = { it.id }`** so moved items aren't all recomposed.
3. `derivedStateOf` to collapse high-frequency state into the value you actually render (`listState.firstVisibleItemIndex > 0`).
4. **Defer reads.** Pass `() -> Int` rather than `Int` so the read lands in layout/draw instead of composition.
5. **Lambda modifiers** for frequently changing values: `Modifier.offset { IntOffset(0, scrollProvider()) }`, `Modifier.drawBehind { drawRect(color) }` instead of `Modifier.background(color)`.
6. Never write state during composition — **backwards writes** cause infinite recomposition. Write only from event handlers.

### Stability and strong skipping [OFFICIAL]

- Compiler classifications: Immutable / Stable / Unstable. `var` properties and standard `List`/`Set`/`Map` parameters are unstable.
- `@Immutable` and `@Stable` annotate intent; a stability configuration file handles types you don't own.
- **Strong skipping is enabled by default since Kotlin 2.0.20.** It makes all restartable composables skippable (unstable params compared by `===`, stable by `equals`) and auto-`remember`s lambdas by their captures. Opt out per declaration with `@NonSkippableComposable` / `@DontMemoize`. Measured APK impact in `nowinandroid`: +4 kB.
- Diagnose with compiler reports via the **`composeCompiler {}` DSL** — not the old `composeOptions.kotlinCompilerExtensionArgs`:

```kotlin
plugins {
    alias(libs.plugins.compose.compiler) // id("org.jetbrains.kotlin.plugin.compose"), version == Kotlin version
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_metrics")
    stabilityConfigurationFile = rootProject.layout.projectDirectory.file("stability_config.conf")
}
```

> **[DEPRECATED]** `composeOptions { kotlinCompilerExtensionArgs += ["androidx.compose.compiler.enableStrongSkippingMode=true", …] }` and `enableStrongSkippingMode = true`. The first is pre-Kotlin-2.0 style; the second is only needed below Kotlin 2.0.20. The Compose stability doc page still shows the old `kotlinCompilerExtensionArgs` form in places — **[VERIFY] that page appears internally inconsistent with the compiler-plugin page; trust the compiler-plugin page.**
>
> `kotlinx-collections-immutable` is still a valid way to get stable collection parameters, but with strong skipping on by default it is much less often necessary. Don't present it as mandatory.

### CMP-specific APIs [OFFICIAL]

- **Resources**: `src/commonMain/composeResources/{drawable,font,values,files}`, generated type-safe `Res` class, `stringResource(Res.string.x)`, `painterResource(Res.drawable.y)`, `Font(Res.font.z)`, `Res.getUri(path)` for handing a file to a platform API. Resources may live in any module/source set since 1.6.10 (needs Kotlin 2.0+, Gradle 7.6+). Limitations: most resources load **synchronously on the calling thread**; raw files and web resources are async; streaming large files (video) isn't supported — use `getUri()`.
- **Common `@Preview`** with `name`, `group`, `widthDp`, `heightDp`, `locale`, `showBackground`, `backgroundColor` (unified in 1.10, extended in 1.9.x).
- **iOS**: `Modifier.preferredFrameRate()` for frame-rate control; `PlatformImeOptions` for native UIKit text-input traits; native `UIView`-backed text input and concurrent rendering by default as of 1.11.
- Shadows: `dropShadow` / `innerShadow` modifiers, `DropShadowPainter()`, `InnerShadowPainter()`.
- `MaterialExpressiveTheme` — experimental.

### Deprecations in CMP [OFFICIAL]

- `Window.bindToNavigation()` → `NavController.bindToBrowserNavigation()` (web).
- `CanvasBasedWindow` → `ComposeViewport` (web).
- Legacy UI-test entry points `runComposeUiTest`, `runSkikoComposeUiTest`, `runDesktopComposeUiTest` → the **v2** API (`androidx.compose.ui.test.v2.runComposeUiTest`) as of 1.11.
- `ExperimentalMaterial3ExpressiveApi` / `ExperimentalMaterial3ComponentOverrideApi` public APIs removed in 1.9.x.

### Anti-patterns

- Reading rapidly-changing state high in the tree. [OFFICIAL]
- `Modifier.background(animatedColor)` / `Modifier.offset(dp)` where the lambda overload exists. [OFFICIAL]
- Lazy lists without stable keys. [OFFICIAL]
- Writing state during composition. [OFFICIAL]
- Passing a whole domain object where two fields are used. [OFFICIAL]
- Mutating state inside a composable instead of hoisting. [OFFICIAL]
- Assuming CMP web is production-ready — it is Beta. [OFFICIAL]

### Testing recommendations

See §12.

### Sources

- all URLs listed under "Official documentation" above
- <https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html>
- <https://blog.jetbrains.com/kotlin/2026/05/compose-multiplatform-1-11-0/>
- <https://blog.jetbrains.com/kotlin/2026/08/compose-multiplatform-1-12-0/>
- <https://blog.jetbrains.com/kotlin/2026/01/compose-multiplatform-1-10-0/>

---

## 5. Coroutines and Flow

**Official documentation:**
- <https://developer.android.com/kotlin/coroutines/coroutines-best-practices>
- <https://developer.android.com/kotlin/flow/stateflow-and-sharedflow>

### Recommended practices [OFFICIAL]

1. **Inject dispatchers.** Never hardcode `Dispatchers.X` inside a class — take a `CoroutineDispatcher` parameter so tests can substitute a `TestDispatcher`.
2. **Suspend functions must be main-safe.** A `suspend fun` is safe to call from the main thread; it moves its own blocking work with `withContext(ioDispatcher)`.
3. **The ViewModel creates coroutines**, in `viewModelScope`; it does not expose `suspend` functions to the UI.
4. **Never expose mutable types.** `private val _uiState = MutableStateFlow(...)`; `val uiState: StateFlow<...> = _uiState`.
5. **Data and domain layers expose `suspend` functions (one-shot) and `Flow` (streams).**
6. **Screen-scoped parallel work uses `coroutineScope` / `supervisorScope`** inside a `suspend fun` so it inherits the caller's lifecycle:

```kotlin
suspend fun getBookAndAuthors(): BookAndAuthors = coroutineScope {
    val books   = async { booksRepository.getAllBooks() }
    val authors = async { authorsRepository.getAllAuthors() }
    BookAndAuthors(books.await(), authors.await())
}
```

7. **Work that must outlive the screen gets an injected external `CoroutineScope`** — not `GlobalScope`:

```kotlin
class ArticlesRepository(
    private val articlesDataSource: ArticlesDataSource,
    private val externalScope: CoroutineScope,
) {
    suspend fun bookmarkArticle(article: Article) {
        externalScope.launch { articlesDataSource.bookmarkArticle(article) }.join()
    }
}
```

8. **Make coroutines cancellable.** Cancellation is cooperative; in CPU loops call `ensureActive()`. All `kotlinx.coroutines` suspend functions are already cancellable.
9. **Catch exceptions** at the launch site; **never swallow `CancellationException`** — rethrow it.

### StateFlow / SharedFlow [OFFICIAL]

- `StateFlow`: hot, requires an initial value, conflates, exposes `.value`. The recommended UI-state holder.
- `SharedFlow`: hot, configurable (`replay`, `onBufferOverflow`, `subscriptionCount`, `resetReplayCache()`), no current value. For broadcasting to multiple consumers.
- `stateIn(scope, started, initialValue)` / `shareIn(scope, replay, started)` turn a cold flow hot. `SharingStarted.WhileSubscribed()` is the efficient default; `Eagerly` starts immediately; `Lazily` starts at the first subscriber and never stops.
- **Critical:** never collect UI-bound flows from a bare `lifecycleScope.launch {}` / `launchIn` — use `repeatOnLifecycle(Lifecycle.State.STARTED)` (Views) or `collectAsStateWithLifecycle()` (Compose), or the flow keeps producing while the UI is invisible.
- `LiveData` is positioned as the legacy option (auto-unsubscribes at `STOPPED`, no initial value required).

### Anti-patterns [OFFICIAL]

- `GlobalScope`.
- Hardcoded dispatchers.
- Blocking work in a `suspend fun` without `withContext`.
- Exposing `MutableStateFlow` / suspend functions from a ViewModel.
- Collecting without lifecycle awareness.
- Catching `CancellationException` and not rethrowing.
- `runBlocking` on the main thread. [PREFERENCE — not stated on these pages, but follows from main-safety]

### Platform-specific considerations

- **iOS/native**: `suspend` functions exported through Objective-C become completion handlers; calling them from Swift **on a non-main thread** is prohibited and crashes. Only `CancellationException` is delivered as a Swift `Error` by default — any other exception crossing the boundary terminates the app unless declared with `@Throws`. Swift's `async`/`await` over these bridges gives **no real cancellation**. [VERIFY — gathered from community sources plus the Obj-C interop page; the "main thread only" claim needs confirmation against the current `kotlinx.coroutines` native docs, since the old `native-mt` era is over]
- **Desktop/JVM (Swing)**: `Dispatchers.Main.immediate` requires `kotlinx-coroutines-swing` in `jvmMain`, otherwise `viewModelScope` and `Lifecycle.coroutineScope` fail. [OFFICIAL]
- **Swift export** (Alpha) maps `suspend fun` → Swift `async` and `Flow<T>` → Swift `AsyncSequence` natively, and uses `Dispatchers.Default` by default. This is the direction of travel but is not production-ready. [OFFICIAL]

### Testing recommendations [OFFICIAL]

`runTest` plus an injected `TestDispatcher` (`StandardTestDispatcher` or `UnconfinedTestDispatcher(testScheduler)`):

```kotlin
@Test
fun testBookmarkArticle() = runTest {
    val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    val repository = ArticlesRepository(FakeArticlesDataSource(), defaultDispatcher = testDispatcher)
    repository.bookmarkArticle(article)
    assertThat(dataSource.isBookmarked(article)).isTrue()
}
```

For Flow assertions, **Turbine** 1.2.1 is the de-facto standard and is fully multiplatform. [PREFERENCE — Google's own recommendation is to assert on `StateFlow.value` where possible]

### Sources

- <https://developer.android.com/kotlin/coroutines/coroutines-best-practices>
- <https://developer.android.com/kotlin/flow/stateflow-and-sharedflow>
- <https://kotlinlang.org/docs/native-objc-interop.html>
- <https://kotlinlang.org/docs/native-swift-export.html>
- <https://github.com/cashapp/turbine>

---

## 6. Clean Architecture

**Official documentation:** There is **no Google or JetBrains specification of "Clean Architecture."** The canonical source is Robert C. Martin's article: <https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html>. Google's official equivalent is the layered architecture in §2, which is *compatible with* but not identical to Clean Architecture.

**This distinction matters for the skill.** Telling a user that "Clean Architecture is the Android-recommended architecture" would be false. The accurate statement: Google recommends UI / domain(optional) / data layers with the dependency rule pointing inward; Clean Architecture is a broader, older formulation of the same dependency discipline, and many teams adopt its vocabulary (entities, use cases, interface adapters).

### The original formulation [OFFICIAL to its author]

- **Concentric circles**, outer = low-level mechanism, inner = high-level policy. Integrates Hexagonal, Onion, and DCI.
- **The Dependency Rule:** "source code dependencies can only point inwards." Inner circles know nothing of outer circles.
- **Entities** — enterprise-wide business rules, most stable.
- **Use cases** — application-specific rules; orchestrate entities; insulated from DB and UI changes.
- **Interface adapters** — convert between use-case format and external format (MVC, DB queries, API mapping).
- **Frameworks and drivers** — DB, web framework, third-party tools; "details" kept at arm's length.
- **Crossing boundaries** against the flow of control uses polymorphism / the Dependency Inversion Principle: the inner circle calls an interface it owns, the outer circle implements it.
- **"Simple data structures are passed across the boundaries"** — never pass an entity or a framework type across.

### Recommended practices in a KMP context [PREFERENCE, grounded in the OFFICIAL dependency rule]

- `:domain` in `commonMain` with no platform or framework dependencies: entities, use cases, and *repository interfaces*. The interface is owned by the domain; the implementation lives in `:data`. This is the dependency inversion that makes the shared module testable without Android or iOS.
- `:data` implements those interfaces using Ktor, Room, DataStore; it maps DTO ↔ domain model at the boundary.
- `:ui` / `:feature:*` depends on domain, never on data implementations.
- One use case = one responsibility; invoke via `operator fun invoke`. [PREFERENCE]
- Map models at every boundary in complex apps — Google calls this "a model per layer" and labels it **Recommended**. [OFFICIAL]

### Anti-patterns [PREFERENCE]

- A use case per repository method that only forwards the call — adds a layer with no policy in it. Google's own guidance is that the domain layer is *optional* and justified by complexity or reuse. [the optionality is OFFICIAL]
- Leaking Room entities or Ktor DTOs into the UI layer, which couples the UI to a framework detail.
- Repository interfaces defined in the data layer and *imported* by the domain — that inverts the dependency rule the wrong way.
- Four mandatory modules on a three-screen app — Google explicitly warns against over-modularizing. [OFFICIAL]

### Platform-specific considerations

- On KMP the dependency rule has a practical payoff beyond purity: a `commonMain` domain with no platform dependencies compiles for every target and runs in `commonTest` on the host JVM, which is the fastest test loop available.
- Clean Architecture's "entities" and KMP's `expect`/`actual` interact: platform capabilities (keychain, biometrics, file paths) belong behind a domain-owned interface, with the `actual` implementation in the outer ring. This is the same conclusion the KMP docs reach independently (§3).

### Testing recommendations

Use cases are pure functions over injected interfaces — plain `commonTest` with hand-written fakes, no mocking framework, no Android. This is the highest-value test tier in a KMP codebase.

### Sources

- <https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html>
- <https://developer.android.com/topic/architecture> (Google's layered model, for contrast)
- <https://developer.android.com/topic/architecture/recommendations> ("model per layer", domain-layer optionality)

---

## 7. MVVM and UDF

**Official documentation:**
- <https://developer.android.com/topic/architecture> (UDF principle)
- <https://developer.android.com/topic/architecture/ui-layer> / `.../ui-layer/state-production`
- <https://developer.android.com/develop/ui/compose/architecture>

### What is official and what is not

- **UDF is an official Android principle.** "State flows in only one direction… the events that modify the data flow in the opposite direction." Benefits Google names: data consistency, fewer errors, easier debugging. [OFFICIAL]
- **Google's recommendation is an AAC `ViewModel` exposing a single `uiState: StateFlow<UiState>` and receiving user actions as method calls.** That is MVVM in all but the name; Google's docs use "state holder" and "UDF" rather than "MVVM". [OFFICIAL]
- **MVI is not an official Android pattern.** No `developer.android.com` page prescribes a single `Intent`/reducer/`Store` triad. Presenting MVI as "the Android standard" would be inaccurate. [PREFERENCE]

### Recommended practices [OFFICIAL]

- One `UiState` per screen, modelled as a `data class` with explicit loading/error fields, or a `sealed interface` of states. Both appear in official samples.
- `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue)` for state derived from data-layer flows — the 5 s timeout is the number in Google's own recommendation.
- **One-off events belong in state, not in a channel to the UI.** "Do not send events from the ViewModel to the UI" is marked *Strongly recommended*: process the event, update state, and let the UI consume and acknowledge it (e.g. a `messageShown(id)` callback).
- Plain state-holder classes (not ViewModels) for reusable UI components, with state hoisted. [OFFICIAL]
- `rememberViewModelStoreOwner()` for complex dynamic reusable composables that genuinely need their own ViewModel scope. [OFFICIAL]

### Anti-patterns [OFFICIAL unless marked]

- `Channel`/`SharedFlow` of navigation or snackbar events from the ViewModel to the UI — explicitly discouraged.
- `AndroidViewModel`; holding `Context`, `Activity`, `Resources`, or `Application` in a ViewModel.
- Exposing `MutableStateFlow`.
- Multiple independent `StateFlow`s per screen where one `UiState` would keep the UI consistent. [PREFERENCE — Google's "expose a UI state" is labelled *Recommended*, so multiple flows are a weaker violation than the items above]
- Business logic in composables.

### Platform-specific considerations

- **Android**: `collectAsStateWithLifecycle()`. ViewModel is created and scoped by the framework; `SavedStateHandle` restores across process death.
- **KMP/iOS**: `ViewModel` exists in common code via `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose`, **but there is no built-in `ViewModelStoreOwner` on iOS**, and `viewModel()` **cannot be called without an initializer** on non-JVM targets because type reflection is unavailable:

```kotlin
// commonMain — initializer is mandatory off-JVM
@Composable
fun CupcakeApp(viewModel: OrderViewModel = viewModel { OrderViewModel() }) { /* … */ }
```

  With Navigation 3, ViewModel scoping to a back-stack entry requires explicit decorators:

```kotlin
NavDisplay(
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    ),
    backStack = backStack,
    entryProvider = entryProvider { /* … */ },
)
```

  For a **native SwiftUI** UI over a shared ViewModel, the CMP docs point to the third-party **KMP-ObservableViewModel** library to bridge lifecycle and observability. [OFFICIAL that the docs point there; the library itself is third-party]
- `SavedStateHandle` support in common code is **not documented** on the CMP ViewModel page — treat as a gap (§16).

### Testing recommendations [OFFICIAL]

ViewModels are the first thing to test, including flow emissions. Fakes over mocks. With `stateIn(WhileSubscribed())` the test must collect, or the upstream never starts — Google calls this out specifically.

### Sources

- <https://developer.android.com/topic/architecture>
- <https://developer.android.com/topic/architecture/recommendations>
- <https://developer.android.com/develop/ui/compose/architecture>
- <https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html>

---

## 8. Koin dependency injection

**Official documentation:** <https://insert-koin.io/docs/> — specifically `reference/koin-mp/kmp`, `reference/koin-compose/compose`, `setup/koin`.

### Important framing: Koin is not Google's recommendation [OFFICIAL]

Google's architecture recommendations say **"Use Hilt"** (labelled *Recommended*) and Hilt is JVM/Android-only. Koin is the pragmatic choice for KMP because it works in `commonMain`; that is an **engineering preference driven by a platform constraint**, not an official Android recommendation. The skill must say this plainly, because a reader coming from Android-only work will have been told Hilt.

Alternatives worth naming:
- **Metro** (`ZacSweers/metro`) — compile-time DI as a Kotlin *compiler plugin* (FIR+IR, not KSP), multiplatform, Dagger/Anvil/kotlin-inject mental model, true compile-time graph validation, interops with Dagger and kotlin-inject components. Used in RevenueCat's own `cat-paywall-kmp` sample. [third-party; actively developed]
- **kotlin-inject** — compile-time, multiplatform, KSP-based. [third-party]
- Koin is a **service locator with runtime resolution**; Metro/kotlin-inject/Dagger give compile-time verification. That is the real trade-off, and Koin 4.2.1 added "compiler safety" via Koin Compiler 1.0.0-RC1, which narrows it. [VERIFY — confirm what Koin Compiler 1.0.0 verifies at compile time]

### Current version and artifacts [OFFICIAL]

Koin **4.2.2** (2026-06-15), a maintenance release on the 4.2.x line. Notable: **4.2.0** (2026-03-17) brought Kotlin 2.3.20, a Ktor 3.4 DI bridge, and **AndroidX Navigation 3 support**; it also **raised `koin-android` minSdk to 23**.

Artifacts named in the Compose reference:

| Artifact | Purpose |
|---|---|
| `koin-core` | Core container, multiplatform |
| `koin-android` | Android integration (minSdk 23 from 4.2.0) |
| `koin-compose` | `KoinApplication`, `KoinContext`, `koinInject` — multiplatform Compose |
| `koin-compose-viewmodel` | `koinViewModel()` |
| `koin-compose-viewmodel-navigation` | Navigation-scoped ViewModels |
| `koin-compose-navigation3` | Navigation 3 integration |
| `koin-androidx-compose` | Android convenience package |
| `koin-test` | Test utilities, usable from `commonTest` |
| `koin-annotations` + Koin compiler (KSP) | Annotation-driven module generation |

> **[VERIFY]** `insert-koin.io/docs/setup/koin` returned empty content on fetch twice. Exact coordinates, the BOM artifact name, and per-artifact versions (the `koin-androidx-compose` line showed 4.2.0-RC2 on Maven while core is 4.2.2) must be read off the live setup page or Maven Central before being written into the skill. Do **not** hardcode these yet.

### Recommended practices [OFFICIAL]

- **Android `Context` must not reach `commonMain`.** Wrap it: define a `ContextWrapper`-style interface in common code with Android and iOS `actual` implementations, keeping Android-specific work in one place.
- **Swift cannot call Koin's reified `get<T>()`.** Create a Kotlin helper in `iosMain` implementing `KoinComponent` as the interop entry point. To register a Swift instance that implements a Kotlin interface, use `Koin.declare()` at runtime with a helper that converts the Objective-C class to a Kotlin `KClass`.
- **Two startup styles:** start Koin outside Compose (Android `Application`, or shared KMP init) when non-Composable code also resolves dependencies; or use the `KoinApplication` composable when the app is Compose-only — it wires `androidContext()` and logging per platform automatically.
- Desktop: `startKoin` in `main()` before the window launches. Web: start in `jsMain`/`wasmJsMain`, or from the root composable via `KoinApplication`.
- Resolve with qualifiers, scopes and parameters inside composables:

```kotlin
@Composable
fun MyScreen(userId: String) {
    val repo = koinInject<UserRepository>()
    val presenter = koinInject<UserPresenter> { parametersOf(userId) }
}
```

- Split modules by layer/feature and compose them; use a platform module per target supplied by `expect fun platformModule(): Module`. [PREFERENCE, consistent with the docs' KMP sharing-patterns guidance]
- Official samples: `InsertKoinIO/koin-getting-started/tree/main/compose` and `.../compose-annotations`.

### Anti-patterns [PREFERENCE unless marked]

- Passing `android.content.Context` into `commonMain`. [OFFICIAL]
- Calling `get()`/`inject()` deep inside classes instead of constructor injection — turns Koin into a global service locator and defeats testability. Google's DI guidance is "constructor injection by preference". [the preference for constructor injection is OFFICIAL]
- Expecting reified Koin APIs to be callable from Swift. [OFFICIAL]
- `startKoin` called more than once (e.g. in both `Application.onCreate` and `KoinApplication`).
- Everything `single { }` — scope deliberately; Google's rule is to scope when the type holds shared mutable data or is expensive to initialize. [OFFICIAL]

### Testing recommendations [OFFICIAL]

The Koin docs reference testing in `commonTest` with `koin-test`. In practice: verify module graphs resolve (Koin's module-check/verify facility), and for unit tests construct the subject directly with fakes rather than booting the container — consistent with Google's "prefer fakes to mocks".

### Sources

- <https://insert-koin.io/docs/reference/koin-mp/kmp/>
- <https://insert-koin.io/docs/reference/koin-compose/compose>
- <https://github.com/InsertKoinIO/koin/releases>
- <https://developer.android.com/topic/architecture/recommendations> (Hilt recommendation, DI priorities)
- <https://github.com/ZacSweers/metro>, <https://zacsweers.github.io/metro/latest/designdoc.html>

---

## 9. Ktor and OkHttp networking

**Official documentation:**
- <https://ktor.io/docs/client-create-multiplatform-application.html>
- <https://ktor.io/docs/> (client plugins: ContentNegotiation, Logging, Auth, HttpTimeout, HttpRequestRetry)

### The platform decision [OFFICIAL + VERIFY]

- **Ktor Client is the KMP networking answer.** It is JetBrains', it runs in `commonMain`, and it swaps engines per target. [OFFICIAL]
- **Retrofit cannot be used in `commonMain`** — it is JVM-bound. Retrofit 3.0 (May 2025) is Kotlin-first and targets OkHttp 4.12, but that doesn't change its JVM coupling. [VERIFY — the Retrofit-3-on-OkHttp-4.12 detail came from secondary sources; the JVM-only constraint is uncontroversial]
- **OkHttp 5 dropped its Kotlin Multiplatform support** after the implementation trade-offs proved unsatisfactory. [VERIFY — sourced from a Kotlinlang Slack thread, not a Square release note. Confirm against OkHttp's changelog before stating it in the skill.]
- Practical consequence: Android-only projects may legitimately keep Retrofit + OkHttp; a shared-logic KMP project uses Ktor. Mixed estates can keep Retrofit on Android behind a domain-owned interface while `commonMain` uses Ktor — but running two HTTP stacks is a cost, not a feature. [PREFERENCE]

### Engines per target [OFFICIAL]

| Target | Engine artifact | Backing implementation |
|---|---|---|
| Android | `ktor-client-okhttp` | OkHttp |
| iOS / Apple | `ktor-client-darwin` | `NSURLSession` |
| Any Kotlin target | `ktor-client-cio` | Ktor's own coroutine I/O engine |
| JVM desktop | `ktor-client-okhttp` or `ktor-client-cio` | — |
| JS/Wasm | `ktor-client-js` | Fetch |

As of Ktor **3.4.0**, native engines (Curl, Darwin, WinHttp) respect the configured engine dispatcher and default to `Dispatchers.IO`.

### Setup [OFFICIAL]

```toml
# gradle/libs.versions.toml
[versions]
ktor = "3.6.0"

[libraries]
ktor-client-core   = { module = "io.ktor:ktor-client-core",   version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
```

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies { implementation(libs.ktor.client.core) }
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies     { implementation(libs.ktor.client.darwin) }
    }
}
```

### Recommended practices

- **One `HttpClient` per app, injected** — it owns a connection pool and a coroutine scope; creating one per request is a resource leak. [PREFERENCE; the official KMP tutorial's `private val client = HttpClient()` is tutorial-grade, not production guidance]
- Configure centrally with plugins: `ContentNegotiation` + `kotlinx.serialization` `Json`, `Logging` (debug only), `HttpTimeout`, `HttpRequestRetry`, `Auth` with a bearer refresh. [OFFICIAL that these plugins exist and are the configuration mechanism]
- Use `kotlinx.serialization` with `@Serializable` DTOs and `ignoreUnknownKeys = true` for forward compatibility; `explicitNulls = false` where the API omits nulls. [PREFERENCE]
- Map DTO → domain model at the data-source boundary; never return a DTO from a repository. [OFFICIAL — "model per layer" is *Recommended*]
- Expose repository results as `suspend` (one-shot) or `Flow` (stream), never as the engine's own types. [OFFICIAL]
- Keep the engine choice out of `commonMain`: create the configured client in common code, inject the engine from the platform source set, so tests can inject `MockEngine`. [PREFERENCE, mirrors the official "inject dispatchers" rationale]

### Anti-patterns [PREFERENCE unless marked]

- A new `HttpClient` per call.
- `Logging` with `LogLevel.ALL` in release — logs auth headers and response bodies.
- Retrofit interfaces in `commonMain`. [OFFICIAL by construction — it won't compile]
- Catching `Exception` broadly around a request and swallowing `CancellationException`. [the CancellationException rule is OFFICIAL]
- Cleartext HTTP. Android blocks it by default via network security config; do not add `cleartextTrafficPermitted="true"` outside `debug-overrides`. [OFFICIAL — §13]
- Certificate pinning implemented per-platform with no rotation plan.

### Platform-specific considerations

- Darwin/`NSURLSession` and OkHttp differ in redirect handling, timeout semantics, proxy behaviour, and TLS trust configuration. Certificate pinning is configured per engine, so a pinning requirement means two implementations. [VERIFY — needs engine-specific doc reading before the skill prescribes an approach]
- Android needs `<uses-permission android:name="android.permission.INTERNET"/>`; iOS has App Transport Security, which blocks plain HTTP unless exempted in `Info.plist`.
- Background/long-running transfers: Android uses WorkManager; iOS uses `NSURLSession` background configuration. Neither is abstracted by Ktor. [VERIFY]

### Testing recommendations

- `MockEngine` in `commonTest` for data-source tests — no network, runs on every target. [OFFICIAL — Ktor provides `ktor-client-mock`]
- Serialization round-trip tests on DTOs with real captured payloads. [PREFERENCE]
- Contract/integration tests against a staging host in a separate, non-blocking CI job. [PREFERENCE]

### Sources

- <https://ktor.io/docs/client-create-multiplatform-application.html>
- <https://ktor.io/docs/whats-new-340.html>
- <https://github.com/ktorio/ktor/releases>
- <https://github.com/square/retrofit/issues/4682>, <https://github.com/square/retrofit/issues/4020> (OkHttp 5 / Retrofit 3 compatibility — secondary)

---

## 10. Room and DataStore

**Official documentation:**
- <https://developer.android.com/kotlin/multiplatform/room>
- <https://developer.android.com/kotlin/multiplatform/datastore>
- <https://developer.android.com/training/data-storage/room>

### Room on KMP [OFFICIAL]

Supported targets: **Android, iOS, JVM — not web.** The KMP guide's current sample uses the **`androidx.room3` artifacts at `3.1.0-alpha01`** with the `androidx.room3` Gradle plugin; the stable Android line is `androidx.room` **2.8.5**. The skill must state which line it targets and why, because the package names differ (`androidx.room3.*` vs `androidx.room.*`).

```toml
[versions]
room3  = "3.1.0-alpha01"
sqlite = "2.7.1"
ksp    = "<kotlin-compatible-ksp-version>"

[libraries]
androidx-sqlite-bundled      = { module = "androidx.sqlite:sqlite-bundled",      version.ref = "sqlite" }
androidx-room3-runtime       = { module = "androidx.room3:room3-runtime",        version.ref = "room3" }
androidx-room3-compiler      = { module = "androidx.room3:room3-compiler",       version.ref = "room3" }
androidx-room3-sqlite-wrapper= { module = "androidx.room3:room3-sqlite-wrapper", version.ref = "room3" }

[plugins]
ksp           = { id = "com.google.devtools.ksp", version.ref = "ksp" }
androidx-room3= { id = "androidx.room3",          version.ref = "room3" }
```

```kotlin
plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room3)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.room3.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
        androidMain.dependencies { implementation(libs.androidx.room3.sqlite.wrapper) }
    }
}

dependencies {
    add("kspAndroid",             libs.androidx.room3.compiler)
    add("kspIosSimulatorArm64",   libs.androidx.room3.compiler)
    add("kspIosArm64",            libs.androidx.room3.compiler)
}

room { schemaDirectory("$projectDir/schemas") }
```

Common declarations, with the KMP-specific `@ConstructedBy` / `RoomDatabaseConstructor`:

```kotlin
// commonMain
@Entity
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
)

@Dao
interface TodoDao {
    @Insert suspend fun insert(item: TodoEntity)
    @Query("SELECT count(*) FROM TodoEntity") suspend fun count(): Int
    @Query("SELECT * FROM TodoEntity") fun getAllAsFlow(): Flow<List<TodoEntity>>
}

@Database(entities = [TodoEntity::class], version = 1)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getDao(): TodoDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
```

Platform builders:

```kotlin
// androidMain
fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath("my_room.db")
    return Room.databaseBuilder<AppDatabase>(context = appContext, name = dbFile.absolutePath)
}

// iosMain
fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder<AppDatabase>(name = documentDirectory() + "/my_room.db")

private fun documentDirectory(): String {
    val dir = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(dir?.path)
}
```

**KMP differences from Android-only Room, per the official table:**

| Feature | KMP | Android-only |
|---|---|---|
| DAO functions | must be `suspend` | may be blocking |
| Reactive type | `Flow<List<T>>` | `LiveData<List<T>>` also available |
| Transactions | `useWriterConnection { }` | `withTransaction { }` |
| Migration parameter | `SQLiteConnection` | `SupportSQLiteDatabase` |
| Driver | `BundledSQLiteDriver` (recommended) | n/a |

ProGuard/R8: `-keep class * extends androidx.room3.RoomDatabase { <init>(); }`.

### DataStore on KMP [OFFICIAL]

**Only Preferences DataStore is supported on KMP** (1.1.0+). Proto DataStore is not. Dependencies in `commonMain`:

```kotlin
commonMain.dependencies {
    implementation("androidx.datastore:datastore-core:1.2.1")
    implementation("androidx.datastore:datastore-preferences-core:1.2.1")
}
```

```kotlin
// commonMain
fun createDataStore(storage: Storage<Preferences>): DataStore<Preferences> =
    DataStoreFactory.create(storage = storage)

internal const val dataStoreFileName = "dice.preferences_pb"

// androidMain
fun createDataStore(context: Context): DataStore<Preferences> = createDataStore(
    storage = FileStorage(
        serializer = PreferencesSerializer,
        produceFile = { context.filesDir.resolve(dataStoreFileName) },
    )
)

// iosMain
fun createDataStore(): DataStore<Preferences> = createDataStore(
    storage = OkioStorage(
        fileSystem = FileSystem.SYSTEM,
        serializer = PreferencesSerializer,
        producePath = {
            val dir: NSURL? = NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null, create = false, error = null,
            )
            (requireNotNull(dir).path + "/$dataStoreFileName").toPath()
        },
    )
)
```

Web uses `WebLocalStorage`; JVM desktop uses `FileStorage` with a `File`.

### Recommended practices

- Repository owns the DAO and the DataStore; nothing above the data layer sees either. [OFFICIAL — *Strongly recommended*]
- `Flow`-returning queries for observable reads; `suspend` for writes. [OFFICIAL]
- `setQueryCoroutineContext(Dispatchers.IO)` so queries are main-safe. [OFFICIAL]
- Export schemas (`room { schemaDirectory(...) }`) and commit them — they are the input to migration tests. [OFFICIAL for Android Room]
- Write explicit migrations; keep `fallbackToDestructiveMigration` out of release builds. [PREFERENCE]
- A single `DataStore<Preferences>` instance per file per process — multiple instances over one file corrupt state. [VERIFY — true for Android DataStore; confirm the KMP phrasing]
- DataStore is for small key/value preferences; structured or queryable data goes in Room. [OFFICIAL by capability]

### Anti-patterns

- `SharedPreferences` in new code; and specifically **`EncryptedSharedPreferences`** — see §13, it is deprecated. [OFFICIAL]
- DataStore or Room accessed from a ViewModel or composable. [OFFICIAL — *Strongly recommended* against]
- Blocking DAO functions in KMP — they won't compile. [OFFICIAL]
- Secrets or tokens in DataStore Preferences unencrypted. [OFFICIAL, §13]
- Depending on `room3` alpha artifacts in a project that needs a stable dependency surface, without recording that choice.

### Testing recommendations

- Room: in-memory database in `commonTest`/`androidTest` for DAO tests; migration tests from exported schemas (Android has `MigrationTestHelper`; the KMP equivalent needs checking — see §16). [OFFICIAL for Android]
- DataStore: construct the store over a temp directory per test and assert on the emitted `Flow`. [PREFERENCE]
- Repository tests use a fake DAO/fake store, per "prefer fakes to mocks". [OFFICIAL]

### Sources

- <https://developer.android.com/kotlin/multiplatform/room>
- <https://developer.android.com/kotlin/multiplatform/datastore>
- <https://developer.android.com/kotlin/multiplatform>
- <https://github.com/android/kotlin-multiplatform-samples>

---

## 11. Android and iOS interoperability

**Official documentation:**
- <https://kotlinlang.org/docs/multiplatform/multiplatform-ios-integration-overview.html>
- <https://kotlinlang.org/docs/native-objc-interop.html>
- <https://kotlinlang.org/docs/native-swift-export.html>
- <https://kotlinlang.org/docs/multiplatform/compose-swiftui-integration.html>
- <https://kotlinlang.org/docs/multiplatform/compose-lifecycle.html>

### Framework integration options [OFFICIAL]

**Local integration** (one repo, instant updates):
1. **Direct integration** — Xcode build script invokes the Kotlin build. Best when there are no CocoaPods dependencies; **this is the default** when using the JetBrains KMP IDE plugin, and it is the simplest.
2. **CocoaPods (local podspec)** — for monorepos already on CocoaPods, or when the KMP module itself depends on pods.
3. **Swift Package Manager (local package)** — for monorepos on SPM with no critical CocoaPods dependency.

**Remote integration** (versioned artifact, separate teams):
4. **SwiftPM + XCFramework**.
5. **CocoaPods + XCFramework** via the Kotlin CocoaPods Gradle plugin.

### Objective-C export: the constraint list [OFFICIAL]

This is the most important interop section for a skill, because these constraints shape the *shared API surface*:

- **`suspend` functions** → Objective-C completion handlers / Swift `async` (Swift 5.5+). The doc calls the async/await path **"highly experimental" with limitations** (KT-47610). The completion handler must carry `NSError*`/`Error`.
- **`Flow` is not exported usefully.** No `AsyncSequence` mapping in the Objective-C path.
- **Exceptions.** All Kotlin exceptions are unchecked; Swift has only checked errors. A throwing function must be annotated **`@Throws(…)`** with the expected types, or any exception reaching Swift/Objective-C **terminates the program**. The reverse direction (Swift errors → Kotlin exceptions) is not implemented.
- **Generics.** Not supported on interfaces/protocols — only classes can declare them. No variance. Type constraints are lost except non-nullable upper bounds.
- **Name collisions.** Same-named Kotlin classes in different packages inside one framework are **renamed unpredictably**; the documented workaround is to rename them yourself. Top-level functions are reached through a generated wrapper class (`MyLibraryUtilsKt.foo()`). Class names get a framework prefix; protocols get a `Protocol` suffix.
- **Collections and strings cost conversions.** `Map` → `NSDictionary` → Swift `Dictionary` is a double conversion; cast explicitly (`let nsMap: NSDictionary = map as NSDictionary`) to avoid it. Same for `String` → `NSString` → Swift `String`. `NSMutableSet`/`NSMutableDictionary` aren't auto-converted — use `KotlinMutableSet`/`KotlinMutableDictionary`.
- **Unsupported / limited:** inline (value) classes map to the underlying primitive or `id`; Kotlin subclasses of Objective-C classes; custom `List`/`Map`/`Set` implementations; sealed and data classes (limited); **default arguments are not exposed in framework headers**.
- **Strong linking**: Objective-C classes used from Kotlin are strongly linked — a missing class crashes at launch; guard with a Swift/Objective-C availability wrapper.
- **Mitigations the docs name:** `@ObjCName`, `@HiddenFromObjC`, `@ShouldRefineInSwift`, `@Throws`, `-Xexport-kdoc` (with a version-compatibility caveat).

### Swift export [OFFICIAL — Alpha]

Status: **Alpha, incomplete, breaking changes expected, not production-ready.** What it fixes:

- Each Kotlin module becomes its own Swift module; Kotlin packages preserved (no collision renaming); type aliases preserved; overloads callable unambiguously.
- **Primitive nullability without boxing** — `Int?` instead of `KotlinInt`.
- **`suspend fun` → Swift `async`**, and **`Flow<T>` → Swift `AsyncSequence`**:

```kotlin
suspend fun hello(): String { delay(1000); return "Hello Swift!" }
fun flowOfStrings(): Flow<String> = flowOf("hello", "world")
```

```swift
let msg = try await hello()
for try await element in flowOfStrings().asAsyncSequence() { print(element) }
```

- Coroutines default to `Dispatchers.Default`; switch with `withContext`.

Gradle DSL as documented:

```kotlin
kotlin {
    iosArm64(); iosSimulatorArm64()

    swiftExport {
        moduleName = "Shared"
        flattenPackage = "com.example.sandbox"
        export(project(":subproject")) {
            moduleName = "Subproject"
            flattenPackage = "com.subproject.library"
        }
        configure { freeCompilerArgs.add("-Xexpect-actual-classes") }
    }
}
```

> **[VERIFY — conflicting sources]** The official page documents `swiftExport { }`. A JetBrains/Kotlin PR (KT-87989) deprecates "the legacy Swift Export DSL" in favour of `export { swift { } }`. One of the two is stale. Resolve before the skill shows a DSL.

Swift export limitations [OFFICIAL]: works only with **direct integration**; subclasses of `List`/`Set`/`Map` are not exported (KT-80416) and cannot be instantiated from Swift (KT-80417); generics are type-erased to upper bounds and generally unsupported; limited operator support; opt-in declarations need a module-level `optIn` compiler option.

### Third-party interop tooling

**SKIE** (Touchlab) generates Swift-friendly wrappers — sealed classes as Swift enums, suspend/Flow bridging, enum case renaming to Swift conventions. Latest 0.10.15.
> **[VERIFY]** One source states SKIE supports Kotlin 1.8.0–2.1.0. If still true, SKIE is incompatible with Kotlin 2.4.20 and must not be recommended. Check SKIE's compatibility matrix directly.

**KMP-NativeCoroutines / KMP-ObservableViewModel** (rickclephas) — `@NativeCoroutines`, `@NativeCoroutinesState`, and a SwiftUI-observable `ViewModel`. The **official CMP ViewModel page points to KMP-ObservableViewModel** for iOS lifecycle handling, which is as close to an endorsement as a third-party library gets here.

### Compose ↔ native UI [OFFICIAL]

Compose inside SwiftUI:

```kotlin
fun MainViewController(): UIViewController = ComposeUIViewController {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("This is Compose code", fontSize = 20.sp)
    }
}
```

```swift
struct ComposeViewController: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        Main_iosKt.MainViewController()
    }
    func updateUIViewController(_ vc: UIViewController, context: Context) {}
}
```

> **Required:** add `CADisableMinimumFrameDurationOnPhone` to `Info.plist` to enable high refresh rates — **the docs state the app will crash at runtime without it.**

SwiftUI/UIKit inside Compose — `UIKitView` for a `UIView`, `UIKitViewController` for a controller (wrap SwiftUI in `UIHostingController`):

```kotlin
@OptIn(ExperimentalForeignApi::class)
fun ComposeEntryPointWithUIViewController(
    createUIViewController: () -> UIViewController,
): UIViewController = ComposeUIViewController {
    Column(Modifier.fillMaxSize()) {
        Text("SwiftUI inside Compose Multiplatform")
        UIKitViewController(
            factory = createUIViewController,
            modifier = Modifier.size(300.dp).border(2.dp, Color.Blue),
        )
    }
}
```

Documented use cases with their `Info.plist` requirements: `MKMapView` maps, camera via `UIImagePickerController` (needs `NSCameraUsageDescription`), web via `WKWebView`.

### Lifecycle mapping [OFFICIAL]

CMP provides a common `LifecycleOwner`. iOS mapping: `viewWillAppear` → `ON_START`, `didBecomeActive` → `ON_RESUME`, `viewDidDisappear` → `ON_STOP`. Web (Wasm) **skips `CREATED` and never reaches `DESTROYED`**. Desktop maps Swing window events. Desktop additionally requires `kotlinx-coroutines-swing` because `Lifecycle.coroutineScope` uses `Dispatchers.Main.immediate`.

### Recommended practices

- **Design the shared API for Objective-C export, not for Kotlin elegance** — unless and until Swift export is stable. Concretely: avoid generics on interfaces, avoid default arguments at the boundary, avoid exposing `Flow` directly, annotate `@Throws`, and keep class names unique across packages within a framework. [OFFICIAL, derived from the constraint list]
- Prefer a **small, explicit facade** in `iosMain` over exporting the whole domain surface — fewer names to collide, fewer boxed collections, fewer breaking changes for the iOS team. [PREFERENCE]
- Choose **direct integration** unless CocoaPods or remote versioning forces otherwise — it's the documented default and the only option Swift export supports. [OFFICIAL]
- Declare `iosArm64 + iosSimulatorArm64` (Tier 1). [OFFICIAL]

### Anti-patterns

- Exposing `Flow` to Swift via the Objective-C path and expecting it to work. [OFFICIAL]
- Letting any non-`@Throws` exception cross into Swift. [OFFICIAL]
- Relying on Kotlin default arguments from Swift. [OFFICIAL]
- Two same-named classes in different packages in one exported framework. [OFFICIAL]
- Shipping on Swift export today. [OFFICIAL — it is Alpha]
- Omitting `CADisableMinimumFrameDurationOnPhone`. [OFFICIAL]

### Testing recommendations

- `iosSimulatorArm64Test` in CI for native tests (`./gradlew iosSimulatorArm64Test`) — Tier 1 and test-running is supported there; `iosArm64` does not run tests. [OFFICIAL]
- A Swift-side smoke test (XCTest) that calls each exported facade function, which catches export regressions that Kotlin tests cannot see. [PREFERENCE]

### Sources

- all URLs under "Official documentation" above
- <https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html>
- <https://github.com/touchlab/SKIE/releases>
- <https://youtrack.jetbrains.com/issue/KT-47610>, KT-80416, KT-80417
- <https://github.com/JetBrains/kotlin/pull/7921> (KT-87989, Swift export DSL deprecation — secondary)

---

## 12. Testing strategies

**Official documentation:**
- <https://developer.android.com/training/testing/fundamentals>
- <https://developer.android.com/develop/ui/compose/testing>
- <https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html>
- <https://kotlinlang.org/docs/multiplatform/compose-test.html>
- <https://developer.android.com/studio/preview/compose-screenshot-testing>
- <https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview>

### Scopes and the trade-off [OFFICIAL]

Three scopes: **small/unit** (one method or class), **medium** (integration of two or more units), **big/end-to-end** (a screen or flow). Two execution locations: **local** (host JVM — fast, high isolation) and **instrumented** (device/emulator — slow, high fidelity). Google is explicit that these axes are independent: "not all unit tests are local, and not all end-to-end tests run on devices (e.g. Robolectric runs big local tests)." The trade-off is **scalability vs fidelity**.

Testable architecture is a precondition, and Google names the same four moves as §2: layer the code, keep logic out of Activities/Fragments, keep framework dependencies (notably `Context`) out of business logic, use interfaces and DI.

### KMP test layout [OFFICIAL]

| Source set | Runs on | Use |
|---|---|---|
| `commonTest` | every declared target | `kotlin-test` only; domain, data, mapping, Flow logic |
| `androidHostTest` | host JVM | Android-specific logic without a device |
| `androidDeviceTest` | emulator/device | instrumented, Compose UI on Android |
| `iosTest` | simulator (`iosSimulatorArm64Test`) | iOS `actual` implementations |
| `jvmTest` | host JVM | desktop |

`./gradlew allTests` runs everything; combined report at `build/reports/tests/allTests/index.html`. Rule from the docs: **no framework-specific API in `commonTest`.**

### Compose UI testing — Android [OFFICIAL]

```kotlin
androidTestImplementation("androidx.compose.ui:ui-test-junit4:$composeVersion")
debugImplementation("androidx.compose.ui:ui-test-manifest:$composeVersion")
```

`createComposeRule()` when no Activity is needed; `createAndroidComposeRule<MainActivity>()` only when it is. Finders (`onNodeWithText`, `onNodeWithTag`, `onAllNodes`), actions (`performClick`, `performTextInput`, `performScrollTo`), assertions (`assertIsDisplayed`, `assertExists`, `assertTextEquals`). Tests address the **semantics tree**, not the composition tree, which is the same tree assistive technologies use. Synchronization is automatic — the harness waits for recomposition, animations and side effects.

### Compose UI testing — Multiplatform [OFFICIAL]

API is **Experimental**. No JUnit `TestRule` in common code (JUnit-based API is desktop-only); use `runComposeUiTest` with a `ComposeUiTest` receiver. **Use the v2 package**: `androidx.compose.ui.test.v2.runComposeUiTest` — the non-`v2` entry points are deprecated as of CMP 1.11, and v2 defaults to `StandardTestDispatcher` (coroutines run in queue order) and accepts an `effectContext` for overriding the motion-duration scale or supplying a dispatcher.

```kotlin
commonTest.dependencies { implementation("org.jetbrains.compose.ui:ui-test:1.12.1") }
jvmTest.dependencies    { implementation(compose.desktop.currentOs) }
```

```kotlin
class ExampleTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun myTest() = runComposeUiTest {
        setContent {
            var text by remember { mutableStateOf("Hello") }
            Text(text, modifier = Modifier.testTag("text"))
            Button(onClick = { text = "Compose" }, modifier = Modifier.testTag("button")) {
                Text("Click me")
            }
        }
        onNodeWithTag("text").assertTextEquals("Hello")
        onNodeWithTag("button").performClick()
        onNodeWithTag("text").assertTextEquals("Compose")
    }
}
```

Running: `:shared:iosSimulatorArm64Test`, `:shared:connectedAndroidTest`, `:shared:jvmTest`, `:shared:wasmJsTest`.

Android instrumented tests from the shared module need `withDeviceTestBuilder { sourceSetTreeName = "test" }`, `ui-test-junit4-android` + `ui-test-manifest`, and an `androidDeviceTest/AndroidManifest.xml` declaring `androidx.activity.ComponentActivity` as launcher.

### Screenshot testing [OFFICIAL]

The first-party tool is **Compose Preview Screenshot Testing** — and its current state is awkward: **alpha**, and the standalone-plugin path is **deprecated as of AGP 9.5.0-alpha03 / plugin 0.0.1-alpha16** in favour of **AGP test suites**. It also **does not support KMP non-Android targets.**

Setup (standalone path, for reference):

```properties
android.experimental.enableScreenshotTest=true
```

```kotlin
plugins { alias(libs.plugins.screenshot) }
android { experimentalProperties["android.experimental.enableScreenshotTest"] = true }
dependencies {
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.ui.tooling)
}
```

```kotlin
// src/screenshotTest/kotlin/…
@PreviewTest
@Preview(showBackground = true)
@Composable
fun GreetingPreview() { MyApplicationTheme { Greeting("Android!") } }
```

`./gradlew updateDebugScreenshotTest` to record, `./gradlew validateDebugScreenshotTest` to verify; HTML report at `{module}/build/reports/screenshotTest/preview/{variant}/index.html`. Renaming a preview function breaks its reference-image association. `android.compose.screenshot.maxHeapSize=4g` for memory-heavy runs.

> **Recommendation for the skill:** given alpha + deprecation + no KMP support, screenshot testing should be presented as **optional with a clear caveat**, pointing at AGP test suites as the forward path. Paparazzi and Roborazzi are the common third-party alternatives [PREFERENCE] — **[VERIFY]** their current KMP support status was not researched.

### Performance testing [OFFICIAL]

**Macrobenchmark** measures startup and frame timing on a release-like build.

```kotlin
// app/build.gradle.kts
buildTypes {
    release { isMinifyEnabled = true; isShrinkResources = true
              proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")) }
    create("benchmark") {
        initWith(getByName("release"))
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += listOf("release") // multi-module
    }
}
```

```xml
<profileable android:shell="true" />
```

```kotlin
@LargeTest @RunWith(AndroidJUnit4::class)
class SampleStartupBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test fun startup() = benchmarkRule.measureRepeated(
        packageName = "com.example.app",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
    ) { uiAutomator { startApp("com.example.app") } }
}
```

Metrics: `StartupTimingMetric`, `FrameTimingMetric`, `TraceSectionMetric`. `CompilationMode`: `DEFAULT` / `FULL` / `PARTIAL` / `NONE` / `IGNORE`. `StartupMode`: `COLD` / `WARM` / `HOT` — note `COLD` kills the process between `setupBlock` and `measureBlock`. Official do/don't: physical devices only (not emulators), non-debuggable minified builds, `testTagAsResourceId = true` so UI Automator can find Compose nodes, run in CI; don't benchmark on low battery or debug builds.

### Recommended test strategy [OFFICIAL where cited]

1. **`commonTest` carries the weight** — domain, use cases, repositories with fakes, mappers, Flow logic. Fast, runs on every target. [OFFICIAL that common tests should use `kotlin-test` only; the emphasis is PREFERENCE]
2. **ViewModel tests** with `runTest` + injected `TestDispatcher`, asserting on `uiState`. Subscribe when the state uses `WhileSubscribed`. [OFFICIAL]
3. **Fakes, not mocks.** [OFFICIAL — *Strongly recommended*]
4. **Compose UI tests** in `commonTest` via `runComposeUiTest` v2 for shared UI; `createAndroidComposeRule` for Android-specific screens. [OFFICIAL]
5. **Navigation regression tests.** [OFFICIAL — *Strongly recommended*]
6. **Macrobenchmark + Baseline Profiles** on the release-like variant. [OFFICIAL]
7. Swift-side XCTest smoke test over the exported facade. [PREFERENCE]

### Anti-patterns

- Framework-specific test APIs in `commonTest`. [OFFICIAL]
- Mocks where a fake would do. [OFFICIAL]
- Hardcoded dispatchers making tests non-deterministic. [OFFICIAL]
- Benchmarking a debug build or an emulator. [OFFICIAL]
- Non-`v2` CMP test entry points in new code. [OFFICIAL — deprecated]
- Finding nodes by rendered text in a localized app instead of `testTag`. [PREFERENCE]
- Treating alpha screenshot tooling as a release gate.

### Sources

- all URLs under "Official documentation" above
- <https://blog.jetbrains.com/kotlin/2026/05/compose-multiplatform-1-11-0/> (v2 test API, deprecations)
- <https://github.com/cashapp/turbine>

---

## 13. Security and performance

**Official documentation:**
- <https://developer.android.com/privacy-and-security/security-best-practices>
- <https://developer.android.com/privacy-and-security/keystore>
- <https://developer.android.com/build/shrink-code>
- <https://developer.android.com/topic/performance/baselineprofiles/overview>
- <https://developer.android.com/about/versions/16/behavior-changes-16>

### Security — Android [OFFICIAL]

**Storage**
- Private data goes in **internal storage** (`filesDir`) — sandboxed, no permission needed, removed on uninstall.
- App-specific external files via `getExternalFilesDir()`; media via MediaStore; other shared files via the Storage Access Framework. Verify removable-storage availability; verify integrity with a hash (SHA-512 example given).
- Cache ≤1 MB → `cacheDir`; >1 MB → `externalCacheDir`, which has **no enforced security**.
- `getSharedPreferences(..., MODE_PRIVATE)` always; never share preferences between apps. `MODE_WORLD_READABLE` / `MODE_WORLD_WRITABLE` are deprecated.
- **No sensitive data in external storage without encryption.**

**IPC**
- `android:exported="false"` on providers/receivers you don't share (matters for API ≤16 where the default was `true`); mark every component's `android:exported` explicitly.
- Share via `content://` URIs and `FileProvider`, never `file://`.
- Grant access with `FLAG_GRANT_READ_URI_PERMISSION` / `FLAG_GRANT_WRITE_URI_PERMISSION` rather than broad permissions.
- Between your own apps, use `android:protectionLevel="signature"` permissions.
- Show an app chooser (`Intent.createChooser`) when an implicit intent could resolve to several apps.
- Receivers non-exported by default; `PendingIntent` with `FLAG_IMMUTABLE`; avoid sticky broadcasts and implicit-intent hijacking.

**Permissions**
- Least privilege; relinquish when no longer needed; defer to the system via intents (e.g. `ACTION_INSERT` for contacts avoids `READ_CONTACTS`/`WRITE_CONTACTS`); file I/O needs no permission with SAF/MediaStore.
- Gate sensitive screens behind device credential or **biometric** auth.

**Network**
- HTTPS with trusted CAs. Declare a **network security config** and set `cleartextTrafficPermitted="false"` for production domains; user-installed certificates only under `<debug-overrides>`:

```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">secure.example.com</domain>
    </domain-config>
    <debug-overrides>
        <trust-anchors><certificates src="user" /></trust-anchors>
    </debug-overrides>
</network-security-config>
```

- Custom CAs need a custom `TrustManager` (the config file can't express them).
- Keep the security provider current via `ProviderInstaller.installIfNeededAsync(...)`.

**WebView**
- Allowlist the content you load; load only content you control.
- **Never enable a JavaScript interface** unless you fully control the content. Prefer HTML message channels (`createWebMessageChannel`, API 23+).

**Cryptography**
- Use AES (not DES/RC4), RSA (not DSA), SHA-512 (not MD5/SHA-1), HMAC-SHA256. No hardcoded secrets.
- Store keys in the **Android Keystore**, hardware-backed where available; hardware key attestation for sensitive operations.
- **`EncryptedSharedPreferences` / `androidx.security:security-crypto` is DEPRECATED** — deprecated at 1.1.0-alpha07 (April 2025) and no longer recommended or maintained, owing to Keystore inconsistencies across OEMs/versions, main-thread StrictMode violations, and keyset-corruption crashes. The replacement direction is **DataStore + Google Tink** (`StreamingAead`), migrating existing data with `SharedPreferencesMigration`. [DEPRECATED status is OFFICIAL via the reference page; the deprecation date, reasons and the DataStore+Tink replacement path come from secondary sources — **[VERIFY]** before writing migration steps]

**Other**
- `exec()` from the app home directory is blocked on API 29+ (W^X); no in-memory modification of executable code via `dlopen()` text relocations; avoid dynamic code loading from untrusted sources.
- Never ship `android:debuggable="true"`.
- **Play Integrity API** to check device/app integrity before sensitive operations; Safe Browsing for URL checks; Advanced Protection Mode support.
- Sign every app before publishing.

### Security — iOS / KMP considerations

- **Keychain** is the iOS equivalent of Keystore; there is no multiplatform secure-storage API in AndroidX. Secure storage therefore needs an `expect`/`actual` (or interface + DI) pair: Keystore-backed on Android, Keychain on iOS. [PREFERENCE — no official KMP secure-storage guidance exists; see §16]
- **App Transport Security** on iOS blocks plain HTTP by default — the iOS analogue of network security config, configured in `Info.plist`.
- Certificate pinning must be configured per Ktor engine (OkHttp vs Darwin) — two implementations. [VERIFY]
- Secrets do not belong in `commonMain` source or in the exported framework; both are readable in the shipped binary.

### Performance — Android [OFFICIAL]

**R8** (shrink, optimize, obfuscate). AGP ≥9.3 has a simplified DSL:

```kotlin
android {
    buildTypes {
        release {
            optimization { enable = true } // code + resource optimization, default keep rules included
        }
    }
}
```

Custom keep rules go in `src/main/keepRules/custom-rules.keep`. Pre-9.3:

```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
}
```

- Use `proguard-android-optimize.txt`, **not** the legacy `proguard-android.txt` (which carried `-dontoptimize`).
- **R8 full mode is on by default since AGP 8.0.** Disabling it (`android.enableR8.fullMode=false`) is not recommended.
- Optimized resource shrinking: `android.r8.optimizedResourceShrinking=true` on AGP 8.12–8.13; automatic from AGP 9.0 when `isShrinkResources = true`.
- Mapping file at `build/outputs/mapping/<variant>/mapping.txt`; Studio Logcat auto-retraces from AGP 8.2+.
- Don't: enable for debug/test builds; write `-keep class * { *; }`; post-process R8's output with other tools (breaks optimizations and Baseline Profiles). Use the **R8 Configuration Analyzer** to tighten keep rules.

**Baseline Profiles** — AOT-compile hot paths via PGO. Documented gains: **~30%** code-execution improvement from first launch, **~15%** more from R8 rule rewriting (AGP 8.2+), **~15%** more startup improvement from Startup Profiles. Use both profile types.

```kotlin
plugins { id("androidx.baselineprofile") }
dependencies {
    androidTestImplementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}
```

```kotlin
class BaselineProfileGenerator {
    @get:Rule val baselineProfileRule = BaselineProfileRule()

    @Test fun appStartupAndUserJourneys() {
        baselineProfileRule.collect(packageName = PACKAGE_NAME) {
            uiAutomator {
                startApp(PACKAGE_NAME)
                onElement { textAsString() == "COMPOSE LAZYLIST" }.click()
                onElement { viewIdResourceName == "myLazyColumn" }.also {
                    it.fling(Direction.DOWN); it.fling(Direction.UP)
                }
                pressBack()
            }
        }
    }
}
```

`./gradlew app:generateBaselineProfile`. Critical configuration: the **generation** variant must have `minifyEnabled = false` / `-dontobfuscate` / `-dontoptimize`; the **release** build has `minifyEnabled = true` and R8 rewrites the rules to match obfuscated names. Minimum recommended AGP 8.0 (7.4 supported); macrobenchmark 1.5.0+; profileinstaller 1.4.1+. Known issues: OnePlus permission-monitoring setting; Huawei-style battery optimizations block profile installation; Play internal app sharing is unsupported (use internal testing track). Cover startup, navigation, scrolling, and critical flows (registration, login, payment).

**Compose runtime performance:** see §4.

### Android 16 (API 36) behaviour changes that force code changes [OFFICIAL]

Relevant because the Play `targetSdk` floor is API 36:

- **Edge-to-edge is mandatory.** `windowOptOutEdgeToEdgeEnforcement` is deprecated and disabled — handle insets properly.
- **Predictive back animations are on by default.** `onBackPressed()` is no longer called and `KEYCODE_BACK` is not dispatched. Migrate to the supported back APIs, or temporarily set `android:enableOnBackInvokedCallback="false"`.
- **Large screens (sw ≥ 600dp): orientation/resizability/aspect-ratio restrictions are ignored** — `android:screenOrientation`, `android:resizableActivity`, `android:minAspectRatio`, `android:maxAspectRatio`, and `set/getRequestedOrientation()` have no effect. Games can opt out via `android:appCategory`; a temporary `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` property exists but **will not apply when targeting API 37+**.
- `ScheduledExecutorService.scheduleAtFixedRate()` runs at most one missed task.
- `elegantTextHeight` is ignored (affects Arabic, Thai, Indic scripts and others).
- `BODY_SENSORS` / `BODY_SENSORS_BACKGROUND` replaced by granular `android.permissions.health` (`READ_HEART_RATE`, `READ_HEALTH_DATA_IN_BACKGROUND`).
- `MediaStore.getVersion()` is per-app and opaque — don't parse it.
- Opt-in **safer intents** (`android:intentMatchingFlags="enforceIntentFilter"`): explicit intents must match the target's filter; action-less intents match nothing.
- Opt-in-phase **local network permission** (mDNS/SSDP/NsdManager) — test with `adb shell am compat enable RESTRICT_LOCAL_NETWORK <pkg>`; `NEARBY_WIFI_DEVICES` restores access.
- Bluetooth `ACTION_KEY_MISSING` / `ACTION_ENCRYPTION_CHANGE`; `CompanionDeviceManager.removeBond(int)`.

### Testing recommendations

- Security: no test substitutes for review, but assert that debug-only config (cleartext, logging, debuggable) cannot reach release; keep a CI check on the merged release manifest. [PREFERENCE]
- Performance: Macrobenchmark in CI on a physical device, tracking startup and frame timing; regenerate Baseline Profiles when critical flows change. [OFFICIAL]
- R8: run the full instrumented suite against a **minified** variant — most R8 breakage (reflection, serialization) only appears there. [PREFERENCE]

### Sources

- all URLs under "Official documentation" above
- <https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences>
- <https://blog.includesecurity.com/2026/08/encryptedsharedpreferences-is-dead-heres-what-you-should-use-instead/> (secondary)
- <https://developer.android.com/google/play/integrity>

---

## 14. Firebase and subscription integration

### Firebase on KMP — the honest picture

**Official documentation:** Firebase ships **official native SDKs** for Android (`com.google.firebase:firebase-bom`), iOS (CocoaPods/SPM), Web, Flutter and Unity. There is **no official Google/Firebase SDK for Kotlin Multiplatform.** [VERIFY — searches of firebase.google.com surfaced no KMP page; a secondary source states the request "still has not landed on Firebase's public feature board" as of mid-2026. Confirm by checking firebase.google.com/docs directly before the skill asserts this.]

Three viable approaches:

| Approach | What it is | Trade-off |
|---|---|---|
| **A. Native SDKs + `expect`/`actual`** | Call the Firebase Android SDK from `androidMain`, the Firebase iOS SDK from `iosMain`, behind a domain-owned interface in `commonMain` | Most control, zero extra dependency risk, official SDKs on both sides. Most boilerplate. **Recommended default.** [PREFERENCE] |
| **B. GitLive `firebase-kotlin-sdk`** (`dev.gitlive`, 2.7.0) | Community Kotlin-first wrapper over the native SDKs; use Firebase from `commonMain` | Much less boilerplate; **community-maintained**, so it is a supply-chain and lag risk. Not all services or all APIs covered. |
| **C. "KFire"** | Described by a 2026 Medium article as a modern Firebase SDK for KMP covering Auth, Firestore, Storage, Messaging, Analytics, Remote Config, Crashlytics, Core, with automated project setup; "currently in beta" | **[VERIFY] Do not include in the skill yet.** The only sources found are blog posts, one of which calls it "officially-structured" — a phrase that does **not** mean official Google support. Provenance, maintainer and license must be established first. |

**Platform differences that bite regardless of approach:**
- **Crashlytics** needs the Gradle plugin and symbol upload on Android, and dSYM upload on iOS. Kotlin/Native stack traces require uploading the framework's dSYMs or crashes are unsymbolicated. [VERIFY — exact current procedure not researched]
- **FCM** push: Android needs a `FirebaseMessagingService` and the `POST_NOTIFICATIONS` runtime permission (API 33+); iOS needs APNs certificates/keys, `UNUserNotificationCenter` delegation, and the Push Notifications capability. The token lifecycle is platform code either way.
- **Analytics** parameter naming and event limits differ subtly between the two SDKs.
- iOS Firebase SDK integration interacts with the KMP framework-integration choice (§11): CocoaPods vs SPM.

### Subscriptions / in-app purchases

**Official documentation:**
- Android: <https://developer.android.com/google/play/billing>
- iOS: Apple StoreKit 2 / `Product.purchase`, and App Store Connect subscription configuration

**Play Billing status [VERIFY — all from secondary sources; confirm against the Play Billing release notes and Play Console policy pages]:**
- **9.0.0** released 2026-05-19 is the current major; **8.3.0** is the last of the v8 line.
- **From 2026-08-31, Play rejects new apps and updates built against Billing Library 7 or older** (extension available to 2026-11-01). Already-published apps keep working but cannot ship updates.
- Guidance circulating is to go straight to v9 rather than stop at v8.

**No multiplatform billing API exists from either vendor.** Options:

| Approach | Notes |
|---|---|
| **Native per platform behind a common interface** | Play Billing `BillingClient` in `androidMain`, StoreKit 2 in Swift/`iosMain`. Full control; two implementations of entitlement logic, receipt validation and restore. [PREFERENCE] |
| **RevenueCat `purchases-kmp`** (3.7.0) | Vendor SDK wrapping Play Billing and StoreKit plus RevenueCat's backend: receipt validation, entitlement state, cross-platform subscriber identity, and a **Compose Multiplatform paywall component**. `purchases-kmp` 3.0.0 significantly simplified iOS integration. Trade-off: a paid third-party dependency in the purchase path and a vendor-held source of entitlement truth. Official docs: <https://www.revenuecat.com/docs/getting-started/installation/kotlin-multiplatform>, sample: `RevenueCat/cat-paywall-kmp`. |

**Recommended practices [PREFERENCE, except where noted]**
- **Entitlement state is server-authoritative.** Never gate a paid feature on a client-side purchase flag alone; verify receipts server-side (Play Developer API / App Store Server API).
- Model entitlements in the domain layer as a plain `Flow<Entitlements>`; keep `BillingClient` and StoreKit types in the data layer.
- Handle the full lifecycle: pending purchases, **acknowledgement within 3 days or Play auto-refunds** [VERIFY — confirm the current window in Play Billing docs], restore/transfer, grace period, account hold, upgrade/downgrade proration, and refunds via RTDN / App Store Server Notifications.
- Test with Play **license testers** and **closed testing**, and with iOS **sandbox** accounts plus StoreKit configuration files for local testing.
- Keep the Play Billing version current — it is a hard publishing gate, not a preference. [OFFICIAL]

**Anti-patterns**
- Client-only entitlement checks.
- Not acknowledging purchases.
- Ignoring `PENDING` purchase state.
- Leaking the billing SDK type into the UI layer. [OFFICIAL by the repository rule in §2]
- Assuming price, currency, proration or trial semantics are the same on both stores.

### Sources

- <https://developer.android.com/google/play/billing>
- <https://www.revenuecat.com/docs/getting-started/installation/kotlin-multiplatform>
- <https://github.com/RevenueCat/purchases-kmp>
- <https://github.com/RevenueCat/cat-paywall-kmp>
- <https://www.revenuecat.com/blog/engineering/kmp-migration>
- <https://github.com/GitLiveApp/firebase-kotlin-sdk>
- <https://firebaseopensource.com/projects/gitliveapp/firebase-kotlin-sdk/>
- secondary: Medium/foresightmobile posts on the Billing v8/v9 deadline and on "KFire"

---

## 15. Android and iOS release engineering

**Official documentation:**
- <https://developer.android.com/guide/app-bundle>
- <https://developer.android.com/studio/publish/app-signing>
- <https://support.google.com/googleplay/android-developer/answer/9859348> (prepare and roll out a release)
- <https://developers.google.com/android-publisher/tracks>
- <https://developer.android.com/guide/playcore/in-app-updates>
- <https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds>

### Android [OFFICIAL]

**Android App Bundle (`.aab`)** is the publishing format; Play generates and signs per-device APKs.
- Required for **new apps since August 2021**; new and existing **TV apps since June 2023**.
- Apps **over 200 MB** must use Play Feature Delivery or Play Asset Delivery.
- **4 GB** compressed download cap for base + configuration APKs; each on-demand feature download has the same cap; asset packs have separate limits.
- **`.obb` APK expansion files are not supported** with app bundles.
- Build: `./gradlew bundleRelease`, or Studio's *Generate Signed Bundle / APK*.
- Test locally with **bundletool**: `build-apks` → `install-apks`; `get-device-spec` for a device-specific build. Sideloaded builds missing split APKs fail on Android 10+ and Google-certified devices.
- Known pitfalls named in the docs: tools that rewrite the resource table at build time; property conflicts between base and feature modules (feature modules inherit base config).

**Play App Signing:** you sign the bundle; Play signs the delivered APKs.

**Release tracks** [the four tracks are OFFICIAL via the Play publishing API docs; the tester-count and production-access details below are **[VERIFY]** from secondary sources]: **internal** (up to 100 testers, fastest), **closed** (chosen testers/groups), **open** (public beta on Play), **production**. Secondary sources state new developer accounts must run **12 testers on closed testing for 14 days** before requesting production access.

**Staged rollout:** release to a percentage of production users and ramp up; halt, resume, or roll back as confidence dictates. Available for **updates**, not a first production publish. Also usable on test tracks.

**In-app updates** (Play Core, `AppUpdateManager`): **flexible** (background download, app stays usable) or **immediate** (blocking full-screen flow, Play handles install and restart). Requires API 21+, mobile/tablet/ChromeOS, and is **incompatible with `.obb` expansion files**.

**`targetSdk` gate:** API 36 from 2026-08-31 for new apps and updates, with an extension to 2026-11-01. Wear OS and Automotive need ≥ API 35; TV and XR need ≥ API 34. Apps not targeting at least API 35 become invisible to new users on newer devices. [VERIFY — secondary sources; confirm on the Play Console help page]

**Release build checklist [PREFERENCE, assembled from the official pieces]:** R8 enabled with resource shrinking (§13) → Baseline Profile generated from the non-minified benchmark variant and consumed by release → mapping file uploaded for deobfuscated crash reports → no debug logging, no cleartext, not debuggable → signed bundle → bundletool smoke test → internal → closed → staged production.

### iOS [OFFICIAL]

**Upload paths:** Xcode (Organizer / *Distribute App*), `xcrun altool --validate-app` / `--upload-app`, the **Transporter** Mac app, or the **App Store Connect API** (JWT-authenticated, the CI/CD path, used by Xcode Cloud).

- A build is identified by **bundle ID + version + build string**; each triple must be unique.
- Uploads are **processed asynchronously**; you get an email when the build is available.
- **Xcode 14 or later is required for uploads starting in 2026.**
- Required role: Account Holder, Admin, App Manager, or Developer.
- Formats: `.ipa` (iOS), `.app`/`.zip` (macOS).
- Delivery progress, warnings, errors, logs and history are visible in App Store Connect.

**TestFlight:** **internal testers** (account team members) and up to **10,000 external testers**. Builds can go to testers before App Review; App Store release requires **App Review**. **Phased release** is the iOS analogue of a staged rollout.

### KMP-specific release considerations [PREFERENCE unless noted]

- The Kotlin framework build must be part of the iOS archive step. With **direct integration** this is an Xcode build-phase script; CI must therefore have a JDK and the Gradle cache available on the macOS runner.
- **dSYM / symbol upload** for Kotlin/Native frames, or iOS crash reports from shared code are unsymbolicated. [VERIFY — exact procedure not researched]
- Keep one version-of-truth for the shared module and derive both `versionName`/`versionCode` and `CFBundleShortVersionString`/`CFBundleVersion` from it, so a crash in shared code maps to one commit on both stores.
- Two review cadences (Play's staged rollout vs Apple's review + phased release) mean **the two platforms are not in lockstep**; feature flags, not simultaneous releases, are how you keep a shared codebase shippable.
- `CADisableMinimumFrameDurationOnPhone` in `Info.plist` is required for CMP on iOS (§11) — a release-blocking crash if missed. [OFFICIAL]
- Signing: Play App Signing (upload key + Play-held app key) vs Apple certificates/provisioning profiles (or Xcode Cloud / `fastlane match`). Two entirely separate key-management stories.

### Anti-patterns

- Publishing an APK where Play requires a bundle. [OFFICIAL]
- Shipping without uploading the R8 mapping file — unreadable crash reports.
- Benchmarking or profiling the build you ship rather than a release-like variant. [OFFICIAL]
- Straight-to-100% production rollout for a risky release when staged rollout exists. [OFFICIAL capability]
- Letting the `targetSdk` or Billing Library version drift past a Play deadline. [OFFICIAL]
- Using Play internal app sharing to validate Baseline Profiles — explicitly unsupported; use the internal testing track. [OFFICIAL]

### Testing recommendations

- Pre-release: full instrumented suite on the **minified** variant; bundletool install on at least one physical device; Macrobenchmark on the benchmark variant.
- iOS: TestFlight internal build on every main-branch merge; external group before App Review.

### Sources

- all URLs under "Official documentation" above
- <https://developer.android.com/build/shrink-code>
- <https://developer.android.com/topic/performance/baselineprofiles/overview>
- secondary: Play Console help summaries on tracks, staged rollout, and the API 36 deadline

---

## 16. Gaps, conflicts, and open questions

These must be resolved — or explicitly scoped out — before the skill is written. Shipping the skill with any of these unresolved risks stating something false.

### A. Conflicting sources — resolve before writing

| # | Conflict | Action |
|---|---|---|
| A1 | **Swift export DSL.** Official page documents `swiftExport { }`; PR KT-87989 deprecates "the legacy Swift Export DSL" for `export { swift { } }`. | Read the Kotlin 2.4.20 release notes and the current `native-swift-export` page; pin the answer to a Kotlin version. |
| A2 | **Compose stability page vs compiler-plugin page.** The stability page shows `composeOptions.kotlinCompilerExtensionArgs` and presents strong skipping as opt-in; the compiler page says the `composeCompiler {}` DSL is the mechanism and strong skipping is **default since Kotlin 2.0.20**. | Trust the compiler-plugin page and the strong-skipping page. Do not reproduce the `kotlinCompilerExtensionArgs` form. |
| A3 | **Ktor version.** Official KMP guide's catalog shows `3.6.0`; one secondary source says "3.1.3 is current". | 3.6.0 (2026-09-16) per the docs and GitHub releases. |
| A4 | **Koin artifact versions.** Core at 4.2.2, but Maven shows `koin-androidx-compose` at 4.2.0-RC2. `insert-koin.io/docs/setup/koin` would not render. | Read the live setup page and Maven Central; determine whether a Koin BOM exists and prefer it. |
| A5 | **Room: `androidx.room` 2.8.5 vs `androidx.room3` 3.1.0-alpha01.** Google's KMP page lists room 2.8.5 as the KMP-supported library; the Room-KMP guide's sample uses room3 alpha. | Decide which line the skill targets; state the package names and the stability trade-off explicitly. |
| A6 | **SKIE Kotlin compatibility.** One source caps support at Kotlin 2.1.0, which would exclude 2.4.20. | Check SKIE's compatibility matrix. If it lags, drop the recommendation or gate it on a Kotlin version. |
| A7 | **Expect/actual doc staleness.** The page says a visibility limitation "will be removed in Kotlin 2.0"; Kotlin is 2.4.20. | Verify current behaviour; don't repeat the sentence. |
| A8 | **OkHttp 5 dropping KMP.** Sourced from a Slack thread, not a Square changelog. | Confirm in OkHttp's own CHANGELOG before stating it. |

### B. Not verified against an official source — all currently [VERIFY]

- Play **`targetSdk` API 36 deadline** (2026-08-31, extension to 2026-11-01) and the per-form-factor floors — confirm on Play Console help.
- Play **Billing Library** version/deadline facts (9.0.0 current, v8+ to publish from 2026-08-31, 3-day acknowledgement window).
- Play Console **track details**: internal tester cap, the 12-testers-for-14-days production-access rule.
- **Navigation 2.x "maintenance mode"** claim, and Navigation 3 1.2.0's exact release/stability wording.
- **kotlinx.coroutines / serialization / datetime** exact current versions (read from GitHub releases or Maven Central, not search snippets).
- **`EncryptedSharedPreferences` deprecation specifics** — date, stated reasons, and the officially-suggested replacement. Only the deprecated *status* is confirmed from the reference page.
- **Firebase's official position on KMP** — confirm directly on firebase.google.com rather than inferring from absence.
- **"KFire"** — provenance, maintainer, license, maturity. Currently blog-only; treat as unusable.
- **KMP suspend-from-Swift threading rule** ("must be called from the main thread"). Community-sourced and possibly a pre-`native-mt` artifact.
- **Ktor certificate pinning** per engine (OkHttp vs Darwin) — not researched.
- **Crashlytics/dSYM symbolication for Kotlin/Native** — not researched.

### C. Topics in scope that were not covered deeply enough

1. **Room KMP migration testing.** Android has `MigrationTestHelper`; the KMP equivalent (with `SQLiteConnection` migrations) was not found. Needed before the skill prescribes a migration policy.
2. **`SavedStateHandle` / state restoration in KMP.** The CMP ViewModel page doesn't cover it. Android process-death restoration and iOS state restoration are different problems with no documented shared answer.
3. **Secure storage in KMP.** No official Keystore/Keychain abstraction. Need to evaluate the common third-party options and whether to recommend one or hand-roll `expect`/`actual`.
4. **WorkManager-equivalent background work on iOS.** No KMP story; `BGTaskScheduler` vs WorkManager differ fundamentally.
5. **Accessibility.** Not in the original scope list but unavoidable for production: Compose semantics, TalkBack, and the state of CMP's iOS VoiceOver support (the 1.9 notes mention web accessibility work, implying iOS accessibility has its own maturity curve).
6. **Localization and RTL** across Android resources, CMP `composeResources`, and iOS — including plurals, which `composeResources` support needs confirming.
7. **CI/CD concretely.** GitHub Actions matrix for a KMP repo (Linux runner for `commonTest`/JVM/Android, macOS runner for iOS targets), Gradle remote build cache, macOS runner cost. All preference-level, but the skill will be asked for it.
8. **Paparazzi / Roborazzi KMP support** — the practical alternative to the alpha-and-deprecated first-party screenshot tool.
9. **Compose Hot Reload** — CMP 1.10 shipped it as stable; relevant to developer workflow, not researched.
10. **Gradle convention plugins** in concrete form — the `nowinandroid` `build-logic` pattern is the reference but wasn't read in detail.
11. **Kotlin 2.4 language features** specifically. Research covered 2.3's stable features (nested type aliases, data-flow exhaustiveness, the return-value checker) and confirmed 2.4.20 is current, but 2.4.0's own language changes weren't read.
12. **`kotlin.time` vs `kotlinx-datetime` division of labour** post-Kotlin-2.3 — which types now live in the stdlib and what remains in the (pre-1.0, 0.8.0) library.
13. **Modularization patterns page** (`developer.android.com/topic/modularization/patterns`) — referenced but not fetched; it carries the official module-type taxonomy.
14. **MVI** — deliberately left as [PREFERENCE] with no official backing. If the skill is to recommend it, that needs to be framed as a team convention, not an Android standard.

### D. Structural recommendations for the skill itself

1. **Separate "Google says" from "we do".** The sharpest finding in this research is that the official Android recommendation set (Hilt, `collectAsStateWithLifecycle`, Navigation 3, AAC ViewModel) is Android-shaped, and a KMP project must substitute in several places (Koin/Metro for Hilt, CMP lifecycle artifacts, Nav3-on-CMP, `viewModel { }` with a mandatory initializer). A skill that blurs the two will give wrong advice to whichever reader it isn't addressing.
2. **Don't hardcode versions except where the lockstep is real.** Compose Compiler plugin == Kotlin version; KSP ↔ Kotlin; CMP ↔ Kotlin. Everything else belongs in a version catalog with a pointer to release notes.
3. **Carry the deprecation list.** `EncryptedSharedPreferences`, `composeOptions.kotlinCompilerExtensionArgs`, non-`v2` CMP test APIs, `Window.bindToNavigation`, `CanvasBasedWindow`, `AndroidViewModel`, `iosX64`/`macosX64` targets, Billing < 8, `proguard-android.txt`, `file://` URIs, `onBackPressed()`. These are where real codebases are wrong today.
4. **Flag alpha/beta explicitly:** CMP web (Beta), Swift export (Alpha), expect/actual classes (Beta), room3 (alpha), Compose Preview Screenshot Testing (alpha + deprecated path), Material 3 Expressive (experimental), CMP UI test API (experimental).
5. **State the iOS-export API design constraints early**, because they shape the shared module's public surface and are expensive to retrofit.

---

## Appendix — primary sources used

**Kotlin / KMP (JetBrains)**
- <https://kotlinlang.org/docs/coding-conventions.html>
- <https://kotlinlang.org/docs/releases.html>
- <https://kotlinlang.org/docs/whatsnew23.html>
- <https://kotlinlang.org/docs/native-target-support.html>
- <https://kotlinlang.org/docs/native-objc-interop.html>
- <https://kotlinlang.org/docs/native-swift-export.html>
- <https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html>
- <https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html>
- <https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html>
- <https://kotlinlang.org/docs/multiplatform/multiplatform-ios-integration-overview.html>
- <https://kotlinlang.org/docs/multiplatform/compose-multiplatform-and-jetpack-compose.html>
- <https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources.html>
- <https://kotlinlang.org/docs/multiplatform/compose-lifecycle.html>
- <https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html>
- <https://kotlinlang.org/docs/multiplatform/compose-swiftui-integration.html>
- <https://kotlinlang.org/docs/multiplatform/compose-test.html>
- <https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html>
- <https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html>
- <https://blog.jetbrains.com/kotlin/2025/12/kotlin-2-3-0-released/>
- <https://blog.jetbrains.com/kotlin/2026/01/compose-multiplatform-1-10-0/>
- <https://blog.jetbrains.com/kotlin/2026/05/compose-multiplatform-1-11-0/>
- <https://blog.jetbrains.com/kotlin/2026/08/compose-multiplatform-1-12-0/>
- <https://ktor.io/docs/client-create-multiplatform-application.html>
- <https://ktor.io/docs/whats-new-340.html>

**Android (Google)**
- <https://developer.android.com/topic/architecture>
- <https://developer.android.com/topic/architecture/recommendations>
- <https://developer.android.com/topic/modularization>
- <https://developer.android.com/kotlin/multiplatform>
- <https://developer.android.com/kotlin/multiplatform/room>
- <https://developer.android.com/kotlin/multiplatform/datastore>
- <https://developer.android.com/kotlin/coroutines/coroutines-best-practices>
- <https://developer.android.com/kotlin/flow/stateflow-and-sharedflow>
- <https://developer.android.com/develop/ui/compose/architecture>
- <https://developer.android.com/develop/ui/compose/compiler>
- <https://developer.android.com/develop/ui/compose/performance/bestpractices>
- <https://developer.android.com/develop/ui/compose/performance/stability>
- <https://developer.android.com/develop/ui/compose/performance/stability/strongskipping>
- <https://developer.android.com/develop/ui/compose/testing>
- <https://developer.android.com/training/testing/fundamentals>
- <https://developer.android.com/studio/preview/compose-screenshot-testing>
- <https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview>
- <https://developer.android.com/topic/performance/baselineprofiles/overview>
- <https://developer.android.com/build/shrink-code>
- <https://developer.android.com/build/releases/agp-9-4-0-release-notes>
- <https://developer.android.com/privacy-and-security/security-best-practices>
- <https://developer.android.com/about/versions/16/behavior-changes-16>
- <https://developer.android.com/guide/app-bundle>
- <https://developer.android.com/guide/playcore/in-app-updates>
- <https://developers.google.com/android-publisher/tracks>

**Apple**
- <https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds>

**Other first-party (library owners)**
- <https://insert-koin.io/docs/reference/koin-mp/kmp/>
- <https://insert-koin.io/docs/reference/koin-compose/compose>
- <https://github.com/InsertKoinIO/koin/releases>
- <https://github.com/cashapp/turbine>
- <https://github.com/GitLiveApp/firebase-kotlin-sdk>
- <https://github.com/RevenueCat/purchases-kmp>
- <https://www.revenuecat.com/docs/getting-started/installation/kotlin-multiplatform>
- <https://github.com/ZacSweers/metro>
- <https://github.com/touchlab/SKIE/releases>
- <https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html>
- <https://github.com/android/nowinandroid>


---

## Addendum — corrections found after this report (2026-10-03, during example compilation and registry checks)

This report was the input to the skill. Building `examples/user-profile/` against the documented versions and re-checking registries corrected the following; the reference documents carry the corrected facts, and `LIMITATIONS.md` § 5 lists them.

| This report said | Verified reality |
|---|---|
| Room 3 (`androidx.room3`) exists only as 3.1.0-alpha01 | `androidx.room3` **3.0.3 is stable** (2026-09-09), KMP-first, new major with breaking changes; 3.1.0-alpha01 is the alpha line |
| KSP versions are `<kotlin>-<ksp>` and must match Kotlin | KSP 2.x uses its own version line; latest **2.3.12**; generates Room code under Kotlin 2.4.20 on JVM and both iOS targets (verified) |
| Koin BOM existence unconfirmed | `io.insert-koin:koin-bom` 4.2.2 exists |
| Navigation 3 artifacts unspecified | `androidx.navigation3:navigation3-runtime` / `navigation3-ui` 1.2.0; `lifecycle-viewmodel-navigation3` is 2.12.0-alpha04 |
| (not covered) | `google()` is required in `pluginManagement` and `dependencyResolutionManagement` for any module using AndroidX KMP artifacts or the `androidx.room3` plugin, even with no Android target |
| (not covered) | Room 3's Gradle extension is `room3 {}` |
| (not covered) | DataStore's duplicate-instance guard is process-global by path; tests need unique paths |
| (not covered) | `value class` in `commonMain` needs both `@JvmInline` and `import kotlin.jvm.JvmInline`; each omission fails on a different target |
| Play / Billing deadlines from secondary sources | Confirmed on Play Console help (API 36 by 2026-08-31, extension 2026-11-01; Wear/Auto ≥35; TV/XR ≥34; existing apps ≥35) and the Billing deprecation FAQ (v8+ by 2026-08-31; two-year cycle; v9 latest) |
| Circuit test API unreachable | `circuit-test` 0.39.0 sources: `Presenter.test(...)`, `presenterTestOf(...)`, `CircuitReceiveTurbine.awaitUnchanged()`, `TestEventSink`, `FakeNavigator` |
| Room KMP migration testing unverified | `androidx.room3:room3-testing` 3.0.3 `MigrationTestHelper(schemaDirectoryPath, databasePath, driver, databaseClass, databaseFactory)` — takes the `schemas/` root; works from `jvmTest`; `Migration.migrate` is `suspend` |
| AGP 9 unbuildable (JDK 17) | Buildable with JDK 25 (Android Studio JBR) + Gradle 9.8.0; `com.android.kotlin.multiplatform.library` 9.4.1 compiled the Android target with Compose and Room/KSP |
| Swift sees framework-prefixed names | Objective-C does; the header's `swift_name(...)` gives Swift the unprefixed Kotlin names (verified against a generated header) |
