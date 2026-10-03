# Compose Multiplatform

**Scope:** CMP's relationship to Jetpack Compose, per-target stability, library availability, resources, lifecycle, ViewModel, Navigation 3 on CMP, CMP-specific APIs and deprecations.
**Applies to:** CMP modules.
**Prerequisite:** `../android/compose-ui.md` — state, hoisting, recomposition and stability are identical on both and are **not repeated here**.
**Official sources:**
- <https://kotlinlang.org/docs/multiplatform/compose-multiplatform-and-jetpack-compose.html>
- <https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources.html>
- <https://kotlinlang.org/docs/multiplatform/compose-lifecycle.html>
- <https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html>
- <https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html>

**Rule levels:** see `../README.md`.

---

## Relationship to Jetpack Compose [OFFICIAL]

| | Compose Multiplatform | Jetpack Compose |
|---|---|---|
| Owner | JetBrains | Google |
| Targets | Android, iOS, desktop, web | Android |

Same compiler, same runtime, same API surface (`@Composable`, `remember`, layouts, modifiers, animation). Jetpack Compose knowledge transfers directly.

**Artifact resolution is automatic.** A declared `compose.material3` dependency resolves to:

- `androidx.compose.material3:material3` on Android (Google's artifact)
- `org.jetbrains.compose.material3:material3` on every other target

This happens via Gradle Module Metadata. **MUST NOT** declare the `androidx.*` and `org.jetbrains.compose.*` artifacts manually in an attempt to control it.

---

## Per-target stability — MUST respect

| Target | Status |
|---|---|
| Android | **Stable** |
| iOS | **Stable** (since CMP 1.8.0, May 2025) |
| Desktop (JVM) | **Stable** |
| **Web (Wasm)** | **Beta** |

- **MUST NOT** describe the web target as production-ready. It is Beta.
- **MAY** ship iOS on CMP. All major APIs are stable with compatibility guarantees.

---

## Library availability — MUST check before using

[OFFICIAL]

**Multiplatform-ready:** Compose Animation, Compiler, Foundation, Material, Material 3, Runtime, UI, Jetpack Lifecycle, Jetpack ViewModel, Navigation Compose, Navigation 3 (since CMP 1.10).

**Android-only — MUST NOT use in `commonMain`:**

- Maps Compose
- `androidx.compose.runtime.rxjava2` and `rxjava3`

AndroidX KMP support status, per library and per target: <https://developer.android.com/kotlin/multiplatform>. **MUST** check there before adding any AndroidX dependency to `commonMain`.

---

## Resources

Directory layout:

```
src/commonMain/composeResources/
├── drawable/      images
├── font/          fonts
├── values/        strings and other values
└── files/         raw files
```

Since CMP 1.6.10 resources **MAY** live in any module and any source set (requires Kotlin 2.0.0+ and Gradle 7.6+). A generated type-safe `Res` class provides the accessors.

```kotlin
// CORRECT
Text(stringResource(Res.string.app_name))
Image(painter = painterResource(Res.drawable.logo), contentDescription = null)
Text("Hello", fontFamily = FontFamily(Font(Res.font.inter_regular)))

// For handing a resource to a platform API that needs a URI/path
val uri = Res.getUri("files/intro.mp4")
```

### Limitations — MUST know [OFFICIAL]

- Most resources load **synchronously on the calling thread**. Raw files and web resources load asynchronously.
- **Streaming large raw files (video) is not supported.** **MUST** use `Res.getUri(...)` and hand the URI to a platform player.

```kotlin
// WRONG — loads an entire video into memory on the composition thread
val bytes = Res.readBytes("files/intro.mp4")

// CORRECT — hand the URI to the platform
val uri = Res.getUri("files/intro.mp4")
PlatformVideoPlayer(uri)
```

---

## Lifecycle

```kotlin
commonMain.dependencies {
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:<version>")
}
```

CMP provides a common `LifecycleOwner`. States and events are the standard Android set: `DESTROYED` → `CREATED` → `STARTED` → `RESUMED`, with `ON_CREATE`/`ON_START`/`ON_RESUME`/`ON_PAUSE`/`ON_STOP`/`ON_DESTROY`.

### Platform mapping — MUST account for [OFFICIAL]

| Platform | Mapping |
|---|---|
| **iOS** | `viewWillAppear` → `ON_START`, `didBecomeActive` → `ON_RESUME`, `viewDidDisappear` → `ON_STOP` |
| **Web (Wasm)** | **Skips `CREATED`** (always attached to the page) and **never reaches `DESTROYED`** (the page only terminates on tab close). Uses `visibilitychange`, `focus`, `blur`. |
| **Desktop (Swing)** | `windowIconified` → `ON_STOP`, `windowGainedFocus` → `ON_RESUME`, `dispose` → `ON_DESTROY` |

**MUST NOT** write cleanup logic that depends on `ON_DESTROY` firing when the app targets web — it will not fire.

```kotlin
// CORRECT — observe lifecycle in common code
val lifecycleOwner = LocalLifecycleOwner.current
val state by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> start()
            Lifecycle.Event.ON_STOP -> stop()
            else -> Unit
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

**SHOULD** prefer `LifecycleStartEffect` / `LifecycleResumeEffect` over a manual `DisposableEffect` + observer when the work is a simple start/stop pair.

### Desktop requirement — MUST

```kotlin
desktopMain.dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:<version>")
}
```

`Lifecycle.coroutineScope` and `ViewModel.viewModelScope` use `Dispatchers.Main.immediate`, which on JVM desktop requires this artifact. Without it, anything touching `viewModelScope` fails at runtime.

---

## ViewModel

```kotlin
commonMain.dependencies {
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:<version>")
}
```

### MUST supply an initializer to `viewModel()`

Type reflection is unavailable on non-JVM targets. A parameterless `viewModel()` call **does not work** off-JVM.

```kotlin
// CORRECT — works on every target
@Composable
fun OrderRoute(viewModel: OrderViewModel = viewModel { OrderViewModel(repository) }) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    OrderScreen(uiState, viewModel::setQuantity)
}

// WRONG — compiles on Android, fails on iOS/web at runtime
@Composable
fun OrderRoute(viewModel: OrderViewModel = viewModel()) { /* ... */ }
```

**SHOULD** resolve the ViewModel through the DI container instead, which removes the initializer boilerplate and keeps construction in one place:

```kotlin
@Composable
fun OrderRoute(viewModel: OrderViewModel = koinViewModel()) { /* ... */ }
```

See `../libraries/koin-di.md`.

The ViewModel body itself is ordinary common Kotlin:

```kotlin
class OrderViewModel(private val repository: OrderRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(OrderUiState())
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    fun setQuantity(n: Int) {
        _uiState.update { it.copy(quantity = n, price = repository.priceFor(n)) }
    }
}
```

### iOS has no built-in `ViewModelStoreOwner` — MUST plan for it [OFFICIAL]

When the UI is **CMP** on iOS, `ComposeUIViewController` provides the owner and `viewModel { }` works.

When the UI is **native SwiftUI** over a shared ViewModel, there is no `ViewModelStoreOwner`. The official CMP documentation points to the third-party **KMP-ObservableViewModel** library for manual lifecycle handling and SwiftUI observability.

```kotlin
// commonMain — using the third-party library the CMP docs reference
import com.rickclephas.kmp.observableviewmodel.ViewModel
import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState

class OrderViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(OrderUiState())
    @NativeCoroutinesState
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()
}
```

```swift
import KMPObservableViewModelSwiftUI

struct OrderView: View {
    @StateViewModel private var viewModel = OrderViewModel()
    var body: some View {
        Text("Quantity: \(viewModel.uiState.quantity)")
    }
}
```

**MUST** record this as a third-party dependency decision, not an official API.

### `SavedStateHandle` in common code — UNVERIFIED

The CMP ViewModel documentation does **not** cover `SavedStateHandle`. **MUST NOT** assume Android's process-death restoration semantics apply on other targets. Verify before designing state restoration that depends on it.

---

## Navigation 3 on CMP

[OFFICIAL] Supported since **CMP 1.10** on Android, iOS, desktop and web. Navigation 3 is the version named in the current Android architecture recommendations; Navigation 3 1.2.0 is stable.

Navigation 3's model: the back stack is **user-owned** — a `SnapshotStateList` of state that the UI observes directly, rather than a library-internal stack.

### MUST add entry decorators for scoped state

```kotlin
// CORRECT — Compose state and ViewModels are scoped per back-stack entry
NavDisplay(
    backStack = backStack,
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    ),
    entryProvider = entryProvider { /* ... */ },
)

// WRONG — no decorators: ViewModels are not scoped to entries, so a ViewModel
// survives a pop and leaks state into the next visit to that destination
NavDisplay(
    backStack = backStack,
    entryProvider = entryProvider { /* ... */ },
)
```

Required dependency for the ViewModel decorator — **alpha** at verification (`2.12.0-alpha04`):

```kotlin
commonMain.dependencies {
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:<version>")   // alpha; API may change
}
```

**KMP deep links:** `androidx.navigation3.runtime.deeplink` provides `DeepLinkRequest`, `DeepLinkUri` and `DeepLinkMatcher`. [UNVERIFIED — confirm on the navigation3 release page before relying on it.]

**Web:** **MUST** use `NavController.bindToBrowserNavigation()`. `Window.bindToNavigation()` is deprecated.

---

## CMP-specific APIs

| API | Notes |
|---|---|
| Common `@Preview` | Unified in CMP 1.10; parameters `name`, `group`, `widthDp`, `heightDp`, `locale`, `showBackground`, `backgroundColor` |
| `Modifier.preferredFrameRate()` | iOS frame-rate control — trade smoothness against battery |
| `PlatformImeOptions` | iOS text-input traits: keyboard type, autocorrection, return-key behaviour |
| `dropShadow` / `innerShadow` modifiers, `DropShadowPainter()`, `InnerShadowPainter()` | Customisable shadows |
| `MaterialExpressiveTheme` | **Experimental** — MUST NOT use in production without accepting breakage |
| Context menu API for `SelectionContainer` / `BasicTextField` | Behind `ComposeFoundationFlags.isNewContextMenuEnabled = true` |
| `WebElementView()` | Embed HTML content (web target) |
| `SwingFrame()` / `SwingDialog()` | Desktop, with an `init` block for early window configuration |
| Compose Hot Reload | Stable and bundled since CMP 1.10 |

### iOS behaviour notes [OFFICIAL]

- Native `UIView`-backed text input since CMP 1.11 — precise caret movement, native gestures, system context menus.
- **Concurrent rendering is enabled by default** since CMP 1.11.

---

## CMP deprecations — MUST NOT use

| Deprecated | Replacement |
|---|---|
| `runComposeUiTest`, `runSkikoComposeUiTest`, `runDesktopComposeUiTest` (non-`v2` package) | `androidx.compose.ui.test.v2.runComposeUiTest` |
| `Window.bindToNavigation()` | `NavController.bindToBrowserNavigation()` |
| `CanvasBasedWindow` | `ComposeViewport` |
| Public `ExperimentalMaterial3ExpressiveApi` / `ExperimentalMaterial3ComponentOverrideApi` APIs | removed in CMP 1.9.x; the Material3 alpha artifact if genuinely required |

Full list: `../deprecations.md`.

---

## Android / iOS differences — summary table

| Concern | Android | iOS | Web |
|---|---|---|---|
| Stability | Stable | Stable | **Beta** |
| `viewModel()` without initializer | works | **fails** | **fails** |
| `ViewModelStoreOwner` | framework-provided | provided by `ComposeUIViewController`; **absent** with native SwiftUI | provided by `ComposeViewport` |
| `ON_DESTROY` | fires | fires | **never fires** |
| `CREATED` state | present | present | **skipped** |
| Required `Info.plist` key | — | `CADisableMinimumFrameDurationOnPhone` (**crash without it**) | — |
| Screenshot testing | first-party tool (alpha) | **not supported** by the first-party tool | not supported |
| Maps | Maps Compose | platform `MKMapView` via interop | — |

---

## Testing recommendations

```kotlin
commonTest.dependencies { implementation("org.jetbrains.compose.ui:ui-test:<cmp-version>") }
jvmTest.dependencies    { implementation(compose.desktop.currentOs) }
```

```kotlin
// CORRECT — v2 API, runs in commonTest on Android, iOS, desktop and web
class ExampleTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun togglesText() = runComposeUiTest {       // androidx.compose.ui.test.v2
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

- **MUST** import from `androidx.compose.ui.test.v2`. The non-`v2` entry points are deprecated.
- **MUST NOT** use a JUnit `TestRule` in `commonTest`. The JUnit-based Compose test API is **desktop-only**.
- The CMP UI test API is **Experimental** — `@OptIn(ExperimentalTestApi::class)` is required.
- v2 defaults to `StandardTestDispatcher` (queue-order execution) and accepts `effectContext` for overriding the motion-duration scale or supplying a dispatcher.

Run targets:

```
./gradlew :shared:iosSimulatorArm64Test
./gradlew :shared:connectedAndroidTest
./gradlew :shared:jvmTest
./gradlew :shared:wasmJsTest
```

Android instrumented tests from a shared module additionally need `withDeviceTestBuilder { sourceSetTreeName = "test" }`, the `ui-test-junit4-android` and `ui-test-manifest` dependencies, and an `androidDeviceTest/AndroidManifest.xml` declaring `androidx.activity.ComponentActivity` as the launcher activity.

Full strategy: `../quality/testing-strategy.md`.

---

## Cross-references

- Compose state, hoisting, recomposition, stability, strong skipping: `../android/compose-ui.md`
- Targets, source sets, `expect`/`actual`: `project-structure.md`
- Embedding in SwiftUI/UIKit, the `Info.plist` requirement: `ios-interop.md`
- `koinViewModel()` and DI wiring: `../libraries/koin-di.md`
- UI state modelling: `../architecture/mvvm-udf.md`
- Versions and CMP↔Kotlin lockstep: `../version-matrix.md`
