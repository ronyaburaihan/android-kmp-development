# Testing Strategy

**Scope:** Test scopes and locations, KMP test source sets, fakes vs mocks, Compose UI tests, screenshot testing, what to test where.
**Applies to:** all modules.
**Official sources:**
- <https://developer.android.com/training/testing/fundamentals>
- <https://developer.android.com/develop/ui/compose/testing>
- <https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html>
- <https://kotlinlang.org/docs/multiplatform/compose-test.html>
- <https://developer.android.com/studio/preview/compose-screenshot-testing>

**Rule levels:** see `../README.md`.

> Coroutine-specific testing (`runTest`, `TestDispatcher`, Turbine) is in `../kotlin/coroutines-and-flow.md` and is not repeated here. Performance measurement (Macrobenchmark, Baseline Profiles) is in `performance.md`.

---

## Scopes and locations — two independent axes [OFFICIAL]

**Scope:**

| Scope | Subject |
|---|---|
| Small / unit | one method or class |
| Medium | integration of two or more units |
| Big / end-to-end | a screen or a user flow |

**Location:**

| Location | Where it runs | Speed | Fidelity |
|---|---|---|---|
| Local | host machine | fast | lower |
| Instrumented | device or emulator | slow | higher |

**MUST** treat these as independent. The official guidance is explicit: "not all unit tests are local, and not all end-to-end tests run on devices" — Robolectric runs big tests locally.

The trade-off being managed is **scalability versus fidelity**. Small local tests scale; big instrumented tests are faithful.

**Testable architecture is the precondition**, and the official list is the same as the architecture guidance: decouple into layers, keep logic out of Activities and Fragments, keep framework dependencies (notably `Context`) out of business logic, use interfaces and dependency injection.

---

## KMP test source sets — MUST use the right one

| Source set | Runs on | Put here |
|---|---|---|
| `commonTest` | **every declared target** | domain logic, use cases, repositories with fakes, mappers, Flow logic, shared Compose UI tests |
| `androidHostTest` | host JVM | Android-specific logic that needs no device |
| `androidDeviceTest` | emulator / device | instrumented tests, Room migration tests, Android Compose tests needing an Activity |
| `iosTest` | iOS simulator | iOS `actual` implementations |
| `jvmTest` | host JVM | desktop-specific code |

```kotlin
kotlin {
    sourceSets {
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
```

```
./gradlew allTests                          # every target; combined HTML report
./gradlew :shared:iosSimulatorArm64Test     # iOS
./gradlew :shared:connectedAndroidTest      # instrumented
./gradlew :shared:jvmTest                   # desktop
```

Combined report: `build/reports/tests/allTests/index.html`.

### `commonTest` constraints — MUST

**MUST** use `kotlin-test` only: `@Test`, `@BeforeTest`, `@AfterTest`, `assertEquals`, `assertTrue`, `assertFailsWith`, `assertContains`, `assertIs`.

**MUST NOT** use in `commonTest`: JUnit annotations (`org.junit.Test`, `@Before`, `@Rule`), Robolectric, Mockito/MockK, Truth/AssertJ, or any JVM-only library. They do not compile for native targets.

```kotlin
// CORRECT — commonTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PriceFormatterTest {
    @Test
    fun formatsZero() = assertEquals("$0.00", format(0))
}

// WRONG — commonTest; does not compile for iOS
import org.junit.Test
import org.mockito.kotlin.mock

class PriceFormatterTest {
    @Test fun formatsZero() { /* ... */ }
}
```

**MUST** run `iosSimulatorArm64Test` in CI. A JVM-only test dependency that leaked into `commonTest` compiles on the Android path and fails only on the native path, so an Android-only CI job hides it.

> `iosArm64` is Tier 1 but **does not run tests**. The simulator target is the one that executes.

---

## What to test — the official minimum

**MUST** cover, at minimum: [OFFICIAL]

1. **ViewModels**, including Flow emissions.
2. **Data-layer entities** — repositories and data sources.
3. **UI navigation**, as a regression suite.

**SHOULD** weight coverage toward `commonTest`. It runs on every target, needs no emulator or simulator, and is the fastest loop available in a KMP project.

### Suggested distribution [DEFAULT]

| Tier | Location | Proportion | Subject |
|---|---|---|---|
| Domain / use cases / mappers | `commonTest` | largest | pure logic, no platform |
| Repositories with fakes | `commonTest` | large | caching policy, error translation, offline-first behaviour |
| Data sources with `MockEngine` / in-memory DB | `commonTest` / `jvmTest` | medium | parsing, SQL, error mapping |
| ViewModels | `commonTest` | medium | state production, event handling |
| Compose UI | `commonTest` (CMP v2) / `androidDeviceTest` | small | rendering, interaction, accessibility semantics |
| End-to-end flows | `androidDeviceTest` / Xcode UI tests | smallest | navigation, critical journeys |

---

## Fakes, not mocks — MUST [OFFICIAL]

**MUST** prefer test doubles that are real implementations with simplified behaviour.

```kotlin
// CORRECT — a fake: exercises the contract, survives refactors
class FakeNewsRepository : NewsRepository {
    private val feed = MutableStateFlow<List<Article>>(emptyList())
    var refreshCount = 0
        private set

    override fun observeFeed(): Flow<List<Article>> = feed
    override suspend fun refresh() { refreshCount++ }

    fun emit(value: List<Article>) { feed.value = value }
}
```

```kotlin
// WRONG — a mock that encodes implementation details
val repository = mock<NewsRepository>()
whenever(repository.observeFeed()).thenReturn(flowOf(listOf(article)))
verify(repository, times(1)).refresh()
```

Why the wrong form is a problem: `verify(times(1))` asserts *how* the subject works, not *what* it produces. Adding a legitimate cache check that calls `refresh()` twice breaks a passing test without any behaviour regression. Most mocking frameworks are also JVM-only, so the test cannot live in `commonTest` at all.

**MUST** place shared fakes in `commonMain` of a `:core:testing` module, not in `commonTest`. One module's `commonTest` is not visible to another module. See `../architecture/modularization.md`.

**MAY** use a mocking framework in `androidHostTest` or `jvmTest` where it is the only option (final platform classes, framework types). **SHOULD NOT** make it the default.

---

## Compose UI testing

### Compose Multiplatform — `commonTest`

**MUST** use the **v2** API. The non-`v2` entry points are deprecated as of CMP 1.11.

```kotlin
commonTest.dependencies { implementation("org.jetbrains.compose.ui:ui-test:<cmp-version>") }
jvmTest.dependencies    { implementation(compose.desktop.currentOs) }
```

```kotlin
import androidx.compose.ui.test.v2.runComposeUiTest   // v2 — MUST

class FeedScreenTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsEmptyStateWhenNoArticles() = runComposeUiTest {
        setContent {
            FeedScreen(
                uiState = FeedUiState.Success(emptyList()),
                onRefresh = {},
                onArticleClick = {},
            )
        }
        onNodeWithTag("empty_state").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun refreshInvokesCallback() = runComposeUiTest {
        var refreshed = false
        setContent {
            FeedScreen(FeedUiState.Success(emptyList()), onRefresh = { refreshed = true }, onArticleClick = {})
        }
        onNodeWithTag("refresh_button").performClick()
        assertTrue(refreshed)
    }
}
```

**MUST NOT** use a JUnit `TestRule` in `commonTest` — the JUnit-based Compose API is **desktop-only**.
The CMP UI test API is **Experimental**; `@OptIn(ExperimentalTestApi::class)` is required.
v2 defaults to `StandardTestDispatcher` and accepts `effectContext` for overriding the motion-duration scale or supplying a dispatcher.

### Android instrumented Compose tests

```kotlin
androidTestImplementation("androidx.compose.ui:ui-test-junit4:<version>")
debugImplementation("androidx.compose.ui:ui-test-manifest:<version>")
```

```kotlin
class FeedScreenAndroidTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun showsArticles() {
        composeTestRule.setContent {
            AppTheme { FeedScreen(FeedUiState.Success(listOf(article)), {}, {}) }
        }
        composeTestRule.onNodeWithTag("article_${article.id.value}").assertIsDisplayed()
    }
}
```

**MUST** use `createComposeRule()` by default and `createAndroidComposeRule<T>()` **only** when the test genuinely needs the Activity.

Running CMP tests from a shared module on an Android device additionally needs `withDeviceTestBuilder { sourceSetTreeName = "test" }`, the `ui-test-junit4-android` and `ui-test-manifest` dependencies, and an `androidDeviceTest/AndroidManifest.xml` declaring `androidx.activity.ComponentActivity` as the launcher activity.

### Addressing nodes — MUST

Compose tests address the **semantics tree**, which is the same tree assistive technologies read. Synchronization is automatic: the harness waits for recomposition, animations and side effects.

**MUST** address nodes by `Modifier.testTag(...)` in a localised app.

```kotlin
// CORRECT
Button(onClick = onRefresh, modifier = Modifier.testTag("refresh_button")) { Text(stringResource(Res.string.refresh)) }
// test
onNodeWithTag("refresh_button").performClick()

// WRONG — breaks in every locale but one
onNodeWithText("Refresh").performClick()
```

**SHOULD** assert on real accessibility semantics (`contentDescription`, `stateDescription`, `onNodeWithContentDescription`) for at least the primary actions on each screen, so the test also covers screen-reader usability.

**MUST NOT** add `Thread.sleep` or arbitrary waits. If a test needs one, the synchronization contract is being bypassed — find the unsynchronized work instead.

### Testable composables — MUST

A content composable **MUST** take state and lambdas, not a ViewModel. See `../android/compose-ui.md`. A composable with a `ViewModel` parameter cannot be driven from a test without a DI graph and a store owner, and on iOS with native SwiftUI there is no store owner at all.

---

## Screenshot testing — optional, with caveats

The first-party tool is **Compose Preview Screenshot Testing**. Its current state **MUST** be disclosed before recommending it:

- **Alpha** (0.0.1-alpha16), with substantial API changes expected.
- The **standalone-plugin path is deprecated** as of AGP 9.5.0-alpha03, in favour of AGP test suites.
- **Not supported for KMP non-Android targets.**

Setup, for reference:

```properties
# gradle.properties
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
// src/screenshotTest/kotlin/.../FeedScreenScreenshotTest.kt
@PreviewTest
@Preview(showBackground = true)
@Composable
fun FeedScreenEmptyPreview() {
    AppTheme { FeedScreen(FeedUiState.Success(emptyList()), {}, {}) }
}
```

```
./gradlew updateDebugScreenshotTest     # record reference images
./gradlew validateDebugScreenshotTest   # verify
```

Report: `{module}/build/reports/screenshotTest/preview/{variant}/index.html`. Memory-heavy runs: `android.compose.screenshot.maxHeapSize=4g`.

**MUST** know: **renaming a preview function breaks its reference-image association** and the reference must be regenerated.

**SHOULD NOT** make an alpha, deprecated-path tool a merge gate. **MAY** adopt it for local visual review.

> **[UNVERIFIED]** Paparazzi and Roborazzi are the common third-party alternatives. Their current KMP support status was not established. **MUST** verify before recommending either.

---

## iOS testing

**MUST** run native tests on the simulator target:

```
./gradlew :shared:iosSimulatorArm64Test
```

**SHOULD** add a Swift-side XCTest smoke test over the exported facade. Kotlin tests cannot detect export regressions — a new generic parameter, a renamed class, or a dropped `@Throws` compiles in Kotlin and fails only in Xcode.

```swift
final class SharedApiSmokeTests: XCTestCase {
    func testFeedIsCallable() async throws {
        let api = SharedDependencies().sharedApi
        let feed = try await api.feed()
        XCTAssertNotNil(feed)
    }
}
```

**SHOULD** build the iOS framework in CI on every change to the shared module's public API, even when no iOS tests run. The framework build is the only validation of export. See `../kmp/ios-interop.md`.

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| JUnit, Robolectric, Mockito or MockK in `commonTest` | does not compile for native; fails only in the iOS CI job |
| Mocks where a fake would work | tests assert implementation, break on refactor |
| `verify(times(n))` on a repository | encodes call counts as contract |
| Hardcoded dispatchers in the subject | non-deterministic tests, then disabled tests |
| `Thread.sleep` or arbitrary delays | flaky; bypasses Compose synchronization |
| Addressing Compose nodes by rendered text in a localised app | breaks in every other locale |
| A `ViewModel` parameter on a content composable | untestable, unpreviewable |
| `runBlocking` in place of `runTest` | real time, no virtual-time control |
| Reading `.value` of a `stateIn(WhileSubscribed())` flow with no collector | sees the initial value forever; a passing test that proves nothing |
| Shared mutable fixture files between tests | order-dependent suites |
| Leaked in-memory database or `startKoin` without teardown | cross-test interference; no parallelism |
| Only running the Android CI job | hides native compilation and behaviour failures |
| An alpha screenshot tool as a merge gate | blocks merges on tooling instability |

---

## Android / iOS differences

| Concern | Android | iOS |
|---|---|---|
| Test runner | JUnit4 / AndroidJUnitRunner | `kotlin-test` on the native runner; XCTest for Swift |
| `TestRule` support | yes | **no** — not available in `commonTest` |
| Robolectric | available (`androidHostTest`) | not applicable |
| Mocking frameworks | available on JVM source sets | generally unavailable |
| Compose test entry | `createComposeRule()` | `runComposeUiTest` (v2) |
| Screenshot testing | first-party alpha tool | **unsupported** |
| Device farm | emulators, Firebase Test Lab | simulators, physical devices |
| Export validation | not applicable | **framework build + Swift smoke test required** |
| Migration test helper | `MigrationTestHelper` | **[UNVERIFIED]** — see `../libraries/room-datastore.md` |

---

## CI recommendations [DEFAULT]

**SHOULD** structure the pipeline so native failures are visible:

| Job | Runner | Tasks |
|---|---|---|
| Static analysis | Linux | `ktlintCheck`, `detekt`, `lintRelease` |
| Common + JVM + Android unit | Linux | `:shared:jvmTest`, `testDebugUnitTest` |
| iOS | **macOS** | `:shared:iosSimulatorArm64Test`, plus the framework build |
| Instrumented | Linux with emulator, or a device farm | `connectedDebugAndroidTest` |
| Performance (scheduled, not per-PR) | physical device | Macrobenchmark — `performance.md` |
| Contract / integration (non-blocking) | any | staging-host tests |

**MUST** gate merges on the static-analysis, unit and iOS jobs. **MUST NOT** gate merges on contract tests against a live host or on screenshot validation while the tooling is alpha.

**SHOULD** verify in CI that `commonTest` actually executes for every declared target. A platform source set with zero tests is a common blind spot.

---

## Cross-references

- `runTest`, `TestDispatcher`, Turbine, `WhileSubscribed` in tests: `../kotlin/coroutines-and-flow.md`
- What to test in a ViewModel and the acknowledgement pattern: `../architecture/mvvm-udf.md`
- Domain tests as the highest-value tier: `../architecture/clean-architecture.md`
- `MockEngine` HTTP tests: `../libraries/ktor-networking.md`
- DAO, DataStore and migration tests: `../libraries/room-datastore.md`
- Koin graph verification and direct construction: `../libraries/koin-di.md`
- Testable composable API shape: `../android/compose-ui.md`
- CMP test setup detail and run targets: `../kmp/compose-multiplatform.md`
- Macrobenchmark and Baseline Profiles: `performance.md`
- `:core:testing` module placement: `../architecture/modularization.md`
