# iOS Interoperability

**Scope:** Framework integration options, Objective-C export constraints, Swift export, Compose↔SwiftUI/UIKit embedding, interop tooling.
**Applies to:** the shared module's iOS-facing surface and the Xcode project.
**Official sources:**
- <https://kotlinlang.org/docs/multiplatform/multiplatform-ios-integration-overview.html>
- <https://kotlinlang.org/docs/native-objc-interop.html>
- <https://kotlinlang.org/docs/native-swift-export.html>
- <https://kotlinlang.org/docs/multiplatform/compose-swiftui-integration.html>

**Rule levels:** see `../README.md`.

> **Read this before designing the shared module's public API.** The export constraints below are not a deployment detail — they determine what the shared API may contain. Retrofitting them is expensive.

---

## Framework integration — choose one

[OFFICIAL] Five documented options, all production-ready.

### Local integration (one repository, instant updates)

| Option | When |
|---|---|
| **Direct integration** | **Default choice.** An Xcode build-phase script invokes the Kotlin build. The default when using the JetBrains KMP IDE plugin. Required by Swift export. |
| CocoaPods (local podspec) | Monorepo already on CocoaPods, or the KMP module itself depends on pods |
| Swift Package Manager (local package) | Monorepo on SPM with no critical CocoaPods dependency |

### Remote integration (versioned artifact, separate teams)

| Option | When |
|---|---|
| SwiftPM + XCFramework | Modern iOS app, clean separation, independent versioning |
| CocoaPods + XCFramework | Legacy CocoaPods ecosystem needing remote distribution |

**SHOULD** use **direct integration** unless CocoaPods or independent versioning forces otherwise. It is the documented default, the simplest, and the only option Swift export supports.

**MUST** account for CI: with direct integration the Kotlin build runs inside the Xcode build, so the macOS runner needs a JDK and a warm Gradle cache.

---

## Objective-C export constraints — MUST design around

These are the current production path. Every item is [OFFICIAL].

### Suspend functions and Flow

- A Kotlin `suspend fun` becomes an Objective-C completion handler, and a Swift `async` function for Swift 5.5+. The documentation calls this path **"highly experimental" with limitations** (KT-47610).
- The completion handler **MUST** carry `NSError*`/`Error` for error propagation.
- Swift `async`/`await` over this bridge gives **no real cancellation** of the Kotlin coroutine.
- **`Flow` has no useful Objective-C mapping.** There is no `AsyncSequence` equivalent on this path.

```kotlin
// WRONG — Swift receives an opaque Kotlin object it cannot iterate
class FeedFacade(private val repository: FeedRepository) {
    fun observeFeed(): Flow<List<Article>> = repository.observeFeed()
}

// CORRECT — explicit subscription with a cancellation handle
class FeedFacade(
    private val repository: FeedRepository,
    private val scope: CoroutineScope,
) {
    fun observeFeed(onEach: (List<Article>) -> Unit): Cancellable {
        val job = scope.launch { repository.observeFeed().collect(onEach) }
        return Cancellable { job.cancel() }
    }
}

class Cancellable(private val onCancel: () -> Unit) {
    fun cancel() = onCancel()
}
```

### Exceptions — MUST annotate

All Kotlin exceptions are unchecked; Swift has only checked errors.

- **MUST** annotate every throwing exported function with `@Throws(...)`, listing the expected types.
- An exception **not** in the `@Throws` list that reaches Swift/Objective-C **terminates the program**.
- The reverse direction is not implemented: Swift/Objective-C error methods are not imported as throwing functions in Kotlin.

```kotlin
// CORRECT
@Throws(AuthException::class, CancellationException::class)
suspend fun login(username: String, token: String): Session = ...

// WRONG — any AuthException crashes the iOS app instead of becoming a Swift error
suspend fun login(username: String, token: String): Session = ...
```

### Generics

- **Not supported on interfaces/protocols.** Only classes may declare generic parameters.
- **No variance.** Covariant/contravariant generics coming from Objective-C require force casting in Swift.
- Type constraints are lost, except a non-nullable upper bound:

```kotlin
class Sample<T : Any>() { fun myVal(): T }   // T is marked non-nullable in Objective-C
```

```kotlin
// WRONG — the generic parameter does not survive export
interface Cache<T> {
    fun get(key: String): T?
}

// CORRECT — concrete types at the boundary
interface ArticleCache {
    fun get(key: String): Article?
}
```

### Naming

- Kotlin classes with the **same name in different packages** inside one framework are **renamed unpredictably**. The documented workaround is to rename them yourself.
- Top-level functions are reached through a generated wrapper class: `MyLibraryUtilsKt.foo()`.
- Exported class names get the framework prefix in **Objective-C** (`UserProfileSharedApi`); the header also emits `swift_name("SharedApi")`, so **Swift uses the unprefixed Kotlin name**. Protocols get a `Protocol` suffix in Objective-C. Verified against a generated header in `../../examples/user-profile/` (the Swift smoke file compiles with unprefixed names; prefixed names fail with "has been renamed to").
- `@ObjCName` overrides both names but requires `@OptIn(ExperimentalObjCName::class)`.

```kotlin
// WRONG — two classes named User in one exported framework
// com.example.data.User
// com.example.domain.User

// CORRECT
// com.example.data.UserEntity
// com.example.domain.User
```

### Collections and strings — a performance trap

Kotlin collections cross two boundaries: Kotlin → Objective-C → Swift. Same for `String` → `NSString` → Swift `String`.

```swift
// CORRECT — one conversion
let nsMap: NSDictionary = kotlinMap as NSDictionary

// IMPLICITLY DOUBLE-CONVERTS
let swiftMap = kotlinMap   // Kotlin Map -> NSDictionary -> Swift Dictionary
```

`NSMutableSet` and `NSMutableDictionary` are **not** auto-converted — **MUST** create `KotlinMutableSet` / `KotlinMutableDictionary` explicitly.

### Unsupported or limited — MUST NOT rely on

| Feature | Status |
|---|---|
| Inline (value) classes | map to the underlying primitive or `id` — the wrapper type is gone |
| Kotlin subclasses of Objective-C classes | not supported |
| Custom `List`/`Map`/`Set` implementations | not supported |
| Sealed classes, data classes | limited support — no Swift `enum` mapping, no synthesized equality guarantees |
| **Default arguments** | **not exposed in framework headers** |

```kotlin
// WRONG at the boundary — Swift cannot omit `radix`
fun format(value: Int, radix: Int = 10): String

// CORRECT at the boundary
fun format(value: Int): String = format(value, 10)
fun format(value: Int, radix: Int): String
```

### Linking

Objective-C classes used from Kotlin are **strongly linked** — a missing class crashes at launch. **MUST** guard availability in a Swift/Objective-C wrapper when the class may be absent (OS version, optional framework).

### Export-control annotations — SHOULD use

| Annotation | Use |
|---|---|
| `@ObjCName("SwiftName", "ObjCName")` | control the exported name |
| `@HiddenFromObjC` | keep an internal declaration out of the header |
| `@ShouldRefineInSwift` | mark a declaration for refinement by a Swift wrapper |
| `@Throws(...)` | mandatory for throwing functions — see above |

`-Xexport-kdoc` exports KDoc into the header. **MAY** enable it; it carries a version-compatibility caveat.

---

## The facade pattern — SHOULD

**SHOULD** export a small, explicit facade from `iosMain` rather than exposing the whole domain surface. [DEFAULT — derived from the constraints above]

Reasons: fewer names to collide, fewer boxed collections, fewer `@Throws` sites to audit, and a stable contract for the iOS team that does not shift every time an internal type changes.

```kotlin
// iosMain — the only surface Swift sees
class SharedApi internal constructor(
    private val getFeed: GetFeedUseCase,
    private val scope: CoroutineScope,
) {
    @Throws(NetworkException::class, CancellationException::class)
    suspend fun feed(): List<ArticleDto> = getFeed().map(::toDto)

    fun observeFeed(onEach: (List<ArticleDto>) -> Unit): Cancellable { /* ... */ }
}

// Factory callable from Swift without reified generics
object SharedApiFactory {
    fun create(): SharedApi = /* resolve from the DI graph */
}
```

**MUST** make the DI entry point non-reified for Swift. Swift cannot call Koin's `get<T>()` — see `../libraries/koin-di.md`.

---

## Swift export — Alpha, MUST NOT ship on

[OFFICIAL] Status: **Alpha, incomplete, breaking changes expected, not production-ready.**

What it fixes relative to the Objective-C path:

- Each Kotlin module becomes its own Swift module; **packages are preserved**, so no collision renaming.
- Type aliases preserved; overloads callable unambiguously.
- **Primitive nullability without boxing** — Swift sees `Int?`, not `KotlinInt`.
- **`suspend fun` → Swift `async`** natively.
- **`Flow<T>` → Swift `AsyncSequence`.**
- Flattened package structure option; module-name customisation.

```kotlin
suspend fun hello(): String { delay(1000); return "Hello Swift!" }
fun flowOfStrings(): Flow<String> = flowOf("hello", "world")
```

```swift
let msg = try await hello()
for try await element in flowOfStrings().asAsyncSequence() { print(element) }
```

Coroutines default to `Dispatchers.Default`; switch with `withContext`.

Gradle DSL as the official page documents it:

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

> **[UNVERIFIED] — conflicting sources.** The official page documents `swiftExport { }`. JetBrains issue **KT-87989** deprecates "the legacy Swift Export DSL" in favour of `export { swift { } }`. One of these is stale. **MUST** read the current `native-swift-export` page for the Kotlin version in use before writing this block.

Limitations [OFFICIAL]:

- Works only with **direct integration**.
- Subclasses of `List`/`Set`/`Map` are not exported (KT-80416) and cannot be instantiated from Swift (KT-80417).
- Generic parameters are type-erased to their upper bounds; generics are generally unsupported.
- Limited operator-overloading support.
- Opt-in declarations require an explicit module-level `optIn` compiler option.

**MUST NOT** design a production API that assumes Swift export semantics. **MAY** evaluate it in a spike.

---

## Interop tooling

### SKIE (Touchlab)

Generates Swift-friendly wrappers: sealed classes as Swift enums, suspend/Flow bridging, enum-case renaming to Swift conventions.

> **[UNVERIFIED]** One source states SKIE supports Kotlin 1.8.0–2.1.0, which would make it **incompatible with current Kotlin**. **MUST** check SKIE's compatibility matrix before adding it. Do not recommend it unverified.

### KMP-NativeCoroutines / KMP-ObservableViewModel (rickclephas)

`@NativeCoroutines` and `@NativeCoroutinesState` generate Swift-callable wrappers for suspend functions and flows; `KMPObservableViewModelSwiftUI` makes a shared `ViewModel` observable in SwiftUI.

The **official CMP ViewModel documentation points to KMP-ObservableViewModel** for iOS lifecycle handling. That is as close to an endorsement as a third-party library gets here, but it remains a third-party dependency and **MUST** be recorded as a decision.

---

## Compose ↔ SwiftUI / UIKit

### Compose inside SwiftUI

```kotlin
// iosMain
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
    func updateUIViewController(_ vc: UIViewController, context: Context) { }
}
```

### MUST add the frame-duration key

```xml
<!-- iosApp/Info.plist -->
<key>CADisableMinimumFrameDurationOnPhone</key>
<true/>
```

[OFFICIAL] This enables high refresh rates. **The documentation states the app will crash at runtime without it.** This is a release-blocking omission, not a performance tweak.

### SwiftUI / UIKit inside Compose

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

```swift
Main_iosKt.ComposeEntryPointWithUIViewController(createUIViewController: {
    let swiftUIView = VStack { Text("SwiftUI in Compose Multiplatform") }
    return UIHostingController(rootView: swiftUIView)
})
```

Use `UIKitView` for a bare `UIView`, `UIKitViewController` for a controller. Wrap SwiftUI in `UIHostingController`.

### Documented interop use cases and their `Info.plist` requirements

| Use case | Mechanism | `Info.plist` |
|---|---|---|
| Map | `MKMapView` / SwiftUI `Map` in `UIHostingController` | location strings if the map uses location |
| Camera | `UIImagePickerController` via `UIViewControllerRepresentable` | **`NSCameraUsageDescription` required** |
| Web view | `WKWebView` via `UIViewRepresentable` | — |

**MUST** add every required usage-description string. On iOS a **missing usage string is a launch-time crash**, not a denied permission.

Official samples: <https://github.com/JetBrains/compose-multiplatform/tree/master/examples/interop/>

---

## Android / iOS differences — what has no iOS equivalent

| Android | iOS |
|---|---|
| Gradle project dependency with full SDK access | a generated framework constrained by Objective-C export |
| `Context` threaded through the app | no equivalent; **MUST** be wrapped behind an interface (`../libraries/koin-di.md`) |
| Runtime permission dialogs | `Info.plist` usage strings; missing string = crash |
| WorkManager background work | `BGTaskScheduler` — **no KMP abstraction exists** |
| `AndroidManifest.xml` | `Info.plist` + entitlements |
| R8 mapping file for crash symbolication | dSYM upload. **[UNVERIFIED]** The exact procedure for symbolicating Kotlin/Native frames was not established — verify before relying on iOS crash reports from shared code. |

---

## Testing recommendations

- **MUST** run native tests on `iosSimulatorArm64Test`. `iosArm64` is Tier 1 but **does not run tests**.

```
./gradlew :shared:iosSimulatorArm64Test
```

- **SHOULD** add a Swift-side smoke check over the exported facade. Kotlin tests cannot see export regressions — a change that breaks the generated header (a new generic, a renamed class, a dropped `@Throws`) compiles fine in Kotlin and fails only in Xcode. The cheapest form needs no Xcode project: compile one Swift file against the framework with `xcrun swiftc -F <frameworkDir> -parse-as-library -emit-object` — verified in `../../examples/user-profile/swift-smoke/`. A full XCTest run on a simulator is the stronger form.

```swift
// iosApp/iosAppTests/SharedApiSmokeTests.swift
final class SharedApiSmokeTests: XCTestCase {
    func testFeedIsCallable() async throws {
        let api = SharedApiFactory().create()
        let feed = try await api.feed()
        XCTAssertNotNil(feed)
    }
}
```

- **SHOULD** build the framework in CI on every change to the shared module's public API, even when no iOS tests run. The framework build is the only thing that validates export.
- **MUST** verify `Info.plist` keys (`CADisableMinimumFrameDurationOnPhone`, usage strings) in CI or a release checklist. Both failure modes are crashes with no compile-time signal.

---

## Cross-references

- Targets, source sets, `expect`/`actual`: `project-structure.md`
- CMP lifecycle, ViewModel ownership on iOS, navigation: `compose-multiplatform.md`
- Coroutine/Flow boundary rules: `../kotlin/coroutines-and-flow.md`
- API-surface conventions that affect export: `../kotlin/coding-conventions.md`
- Swift-callable DI entry points: `../libraries/koin-di.md`
- iOS release and upload: `../release/ios-release.md`
