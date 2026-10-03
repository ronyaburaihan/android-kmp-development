# Koin Dependency Injection

**Scope:** Koin in KMP, Compose integration, platform modules, the Android `Context` problem, Swift interop, DI alternatives.
**Applies to:** KMP projects using Koin; the alternatives section applies to any KMP DI decision.
**Official sources:**
- <https://insert-koin.io/docs/reference/koin-mp/kmp/>
- <https://insert-koin.io/docs/reference/koin-compose/compose>
- <https://insert-koin.io/docs/setup/koin>
- <https://github.com/InsertKoinIO/koin/releases>

**Rule levels:** see `../README.md`.

---

## Framing — Koin is not Google's recommendation

**MUST** state this accurately when advising:

- Google's official Android recommendation is **Hilt** (labelled *Recommended*), with manual DI for simple apps. [OFFICIAL]
- **Hilt is JVM/Android-only.** It cannot be used in `commonMain`.
- Koin is therefore the pragmatic KMP choice because of a platform constraint, **not** because it is an official recommendation. [DEFAULT]

What *is* official and applies regardless of container: [OFFICIAL, from <https://developer.android.com/topic/architecture/recommendations>]

- **MUST** use dependency injection, **constructor injection where possible**.
- **MUST** scope to a container only when the type holds shared mutable data or is expensive to initialise. Do not scope everything.

---

## The real trade-off — MUST disclose

Koin is a **service locator with runtime resolution**. A missing binding is a runtime failure, not a compile error.

| Container | Verification | KMP | Mechanism |
|---|---|---|---|
| **Koin** | runtime (plus a verification facility) | yes | reflection-free DSL |
| **Hilt** | compile-time | **no** — Android/JVM only | annotation processing |
| **Metro** | compile-time, true graph validation | yes | **Kotlin compiler plugin** (FIR+IR), not KSP. Dagger/Anvil/kotlin-inject mental model. Interops with Dagger and kotlin-inject components via `@Includes`. |
| **kotlin-inject** | compile-time | yes | KSP |

**SHOULD** consider **Metro** when compile-time graph validation matters more than Koin's lower setup cost. Metro is used in RevenueCat's own `cat-paywall-kmp` sample. It is third-party and actively developed: <https://github.com/ZacSweers/metro>.

> **[UNVERIFIED]** Koin 4.2.1 added "compiler safety" via Koin Compiler 1.0.0-RC1, which narrows this gap. What exactly it verifies at compile time was not established. Verify before claiming Koin offers compile-time validation.

**MUST NOT** present Koin as compile-time safe without that verification.

---

## Versions

Koin **4.2.2** (2026-06-15). Notable in **4.2.0**: Kotlin 2.3.20, a Ktor 3.4 DI bridge, AndroidX Navigation 3 support, and **`koin-android` minSdk raised to 23**.

> **[UNVERIFIED] artifact versions.** `koin-core` is at 4.2.2 while Maven showed `koin-androidx-compose` at `4.2.0-RC2`. Whether a Koin BOM exists was not confirmed. **MUST** read <https://insert-koin.io/docs/setup/koin> and Maven Central for exact coordinates before writing a version catalog. Prefer a BOM if one exists.

Artifacts named in the official Compose reference:

| Artifact | Purpose |
|---|---|
| `koin-core` | container, multiplatform |
| `koin-android` | Android integration (minSdk 23 from 4.2.0) |
| `koin-compose` | `KoinApplication`, `KoinContext`, `koinInject` — multiplatform |
| `koin-compose-viewmodel` | `koinViewModel()` |
| `koin-compose-viewmodel-navigation` | navigation-scoped ViewModels |
| `koin-compose-navigation3` | Navigation 3 integration |
| `koin-androidx-compose` | Android convenience package |
| `koin-test` | test utilities, usable from `commonTest` |
| `koin-annotations` + Koin compiler (KSP) | annotation-driven module generation |

---

## Module structure — the platform-module pattern

**MUST** keep platform-specific bindings in platform source sets, exposed through a single `expect` function.

```kotlin
// commonMain — one expect declaration, no expect classes
expect fun platformModule(): Module

val domainModule = module {
    factoryOf(::GetFeedWithAuthorsUseCase)
    factoryOf(::CanAccessPremiumContentUseCase)
}

val dataModule = module {
    single<NewsRepository> { OfflineFirstNewsRepository(get(), get(), get(named("io"))) }
    single<CoroutineDispatcher>(named("io")) { Dispatchers.IO }
}

fun appModules() = listOf(domainModule, dataModule, platformModule())
```

```kotlin
// androidMain
actual fun platformModule(): Module = module {
    single<HttpClientEngine> { OkHttp.create() }
    single<SecureStore> { KeystoreSecureStore(androidContext()) }
    single { getDatabaseBuilder(androidContext()) }
}

// iosMain
actual fun platformModule(): Module = module {
    single<HttpClientEngine> { Darwin.create() }
    single<SecureStore> { KeychainSecureStore() }
    single { getDatabaseBuilder() }
}
```

This follows the official KMP guidance to prefer interfaces and `expect fun` factories over `expect` classes — see `../kmp/project-structure.md`.

---

## The Android `Context` problem — MUST

**MUST NOT** let `android.content.Context` reach `commonMain`. [OFFICIAL]

```kotlin
// WRONG — commonMain cannot reference Context, and this does not compile for iOS
// commonMain
val dataModule = module {
    single { DataStoreFactory.create(context) }
}
```

**MUST** wrap the capability behind an interface declared in common code, with platform implementations, keeping Android-specific work in one place.

```kotlin
// CORRECT — commonMain declares the capability
interface AppPaths {
    fun dataStoreFile(name: String): String
}

// androidMain
internal class AndroidAppPaths(private val context: Context) : AppPaths {
    override fun dataStoreFile(name: String) = context.filesDir.resolve(name).absolutePath
}
actual fun platformModule(): Module = module {
    single<AppPaths> { AndroidAppPaths(androidContext()) }
}

// iosMain
internal class IosAppPaths : AppPaths {
    override fun dataStoreFile(name: String): String = documentDirectory() + "/" + name
}
actual fun platformModule(): Module = module {
    single<AppPaths> { IosAppPaths() }
}
```

`androidContext()` is available only inside `androidMain` modules, after `androidContext(...)` has been supplied at startup.

---

## Startup

Two official options.

### Option 1 — start outside Compose

**MUST** use this when non-Composable code also resolves dependencies (a `WorkManager` worker, a `BroadcastReceiver`, an iOS facade, a background task).

```kotlin
// androidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(appModules())
        }
    }
}
```

```kotlin
// commonMain — shared initializer callable from iOS
fun initKoin(extraModules: List<Module> = emptyList()) {
    startKoin { modules(appModules() + extraModules) }
}
```

```swift
// iosApp
@main
struct IosApp: App {
    init() { KoinKt.doInitKoin(extraModules: []) }
    var body: some Scene { WindowGroup { ContentView() } }
}
```

### Option 2 — `KoinApplication` composable

**MAY** use this when the app is Compose-only. The framework wires `androidContext()` and platform logging automatically.

```kotlin
@Composable
fun App() {
    KoinApplication(application = { modules(appModules()) }) {
        AppNavigation()
    }
}
```

**MUST NOT** do both. `startKoin` called twice throws.

```kotlin
// WRONG — Application.onCreate starts Koin and the root composable starts it again
class App : Application() {
    override fun onCreate() { super.onCreate(); startKoin { modules(appModules()) } }
}

@Composable
fun App() {
    KoinApplication(application = { modules(appModules()) }) { AppNavigation() }  // throws
}
```

Platform entry points: desktop — `startKoin` in `main()` **before** the window launches. Web — start in `jsMain`/`wasmJsMain`, or use `KoinApplication` from the root composable.

---

## Resolution in Compose

```kotlin
// CORRECT
@Composable
fun UserScreen(userId: String) {
    val repository = koinInject<UserRepository>()
    val presenter = koinInject<UserPresenter> { parametersOf(userId) }
}

// CORRECT — ViewModel, which also solves the viewModel() initializer problem
@Composable
fun FeedRoute(viewModel: FeedViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FeedScreen(uiState, viewModel::refresh)
}
```

`koinViewModel()` is the recommended way to obtain a ViewModel in CMP: it removes the mandatory `viewModel { ... }` initializer that non-JVM targets require. See `../kmp/compose-multiplatform.md`.

`getKoin()` and `currentKoinScope()` are available for cases that need the container directly. **SHOULD NOT** use them in ordinary UI code.

---

## Constructor injection — MUST

```kotlin
// CORRECT — dependencies are visible in the signature and substitutable in tests
internal class OfflineFirstNewsRepository(
    private val remote: NewsRemoteDataSource,
    private val dao: ArticleDao,
    private val ioDispatcher: CoroutineDispatcher,
) : NewsRepository

val dataModule = module {
    single<NewsRepository> { OfflineFirstNewsRepository(get(), get(), get(named("io"))) }
}
```

```kotlin
// WRONG — service-locator style
internal class OfflineFirstNewsRepository : NewsRepository, KoinComponent {
    private val remote: NewsRemoteDataSource by inject()
    private val dao: ArticleDao by inject()
}
```

Why the wrong form is a problem: the dependencies are invisible in the signature, so a reader cannot tell what the class needs. The test must boot a Koin container and register bindings instead of passing fakes. A missing binding fails at first use rather than at graph construction. And the class is now coupled to Koin, so it cannot move to a module that uses a different container.

**MUST NOT** implement `KoinComponent` in domain or data classes. It is appropriate only at a framework boundary that cannot receive constructor parameters — notably the iOS interop entry point below.

---

## Scoping — MUST be deliberate

```kotlin
val dataModule = module {
    // single: shared mutable state or expensive to create
    single<NewsRepository> { OfflineFirstNewsRepository(get(), get(), get()) }
    single { HttpClient(get<HttpClientEngine>()) { /* config */ } }   // owns a connection pool
    single { getRoomDatabase(get()) }

    // factory: cheap, stateless
    factoryOf(::GetFeedWithAuthorsUseCase)
}
```

```kotlin
// WRONG — everything single
val module = module {
    single { GetFeedUseCase(get()) }          // stateless; nothing to share
    single { ArticleDtoMapper() }             // a pure function held forever
}
```

Why the wrong form is a problem: `single` keeps the instance alive for the container's lifetime. For stateless objects that is pure retained memory with no benefit, and it hides accidental state — the day someone adds a `var` to a "stateless" mapper, it becomes a cross-screen bug.

**MUST** use `single` for: the `HttpClient`, the database, repositories, DataStore instances, anything holding a cache.
**MUST** use `factory` for: use cases, mappers, stateless helpers.
**MUST** use `viewModelOf` / `koinViewModel` scoping for ViewModels rather than `single`.

```kotlin
// WRONG — a single ViewModel is shared across every screen instance and never cleared
single { FeedViewModel(get()) }

// CORRECT
viewModelOf(::FeedViewModel)
```

---

## Swift interop — MUST provide a non-reified entry point

**Swift cannot call Koin's reified functions** such as `get<T>()`. [OFFICIAL]

**MUST** create a Kotlin helper in `iosMain` that implements `KoinComponent` and exposes concrete types.

```kotlin
// iosMain — the only place KoinComponent is acceptable
class SharedDependencies : KoinComponent {
    val sharedApi: SharedApi get() = get()
    val authFacade: AuthFacade get() = get()
}
```

```swift
let deps = SharedDependencies()
let api = deps.sharedApi
```

To register a Swift instance that implements a Kotlin interface, use `Koin.declare()` at runtime with a helper that converts the Objective-C class to a Kotlin `KClass`. [OFFICIAL]

**MUST NOT** expose generic or reified Koin APIs in the framework surface. See `../kmp/ios-interop.md` for the full export constraint list.

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| `Context` in `commonMain` | does not compile for native |
| `startKoin` more than once | throws at startup |
| `KoinComponent` / `by inject()` in domain or data classes | invisible dependencies, container required in tests, Koin coupling |
| `single { }` for stateless objects | retained memory; hides accidental state |
| `single { SomeViewModel(...) }` | one ViewModel shared across all screens, never cleared |
| Reified Koin APIs at the iOS boundary | not callable from Swift |
| Presenting Koin as Google's recommendation | factually wrong — Hilt is |
| Presenting Koin as compile-time safe | unverified |
| One giant `appModule` | nothing can be loaded or replaced in isolation; a test must build the whole graph |

---

## Android / iOS differences

| Concern | Android | iOS | Desktop | Web |
|---|---|---|---|---|
| Startup | `Application.onCreate` with `androidContext()` | shared `initKoin()` from the Swift `App` initializer | `startKoin` in `main()` before the window | `jsMain`/`wasmJsMain` entry point, or `KoinApplication` |
| Platform logger | `androidLogger()` | default | default | default |
| `Context` | available via `androidContext()` | **no equivalent** — wrap behind an interface | n/a | n/a |
| Resolution from outside Compose | anywhere | **MUST** go through a `KoinComponent` helper | anywhere | anywhere |
| minSdk | **23** from Koin 4.2.0 | n/a | n/a | n/a |

---

## Testing recommendations

### Prefer direct construction — MUST

```kotlin
// CORRECT — no container involved
@Test
fun refreshStoresRemoteArticles() = runTest {
    val repository = OfflineFirstNewsRepository(
        remote = FakeNewsRemoteDataSource(listOf(dto)),
        dao = FakeArticleDao(),
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
    )
    repository.refresh()
    assertEquals(1, repository.observeFeed().first().size)
}
```

```kotlin
// WRONG — boots a container to test one class
@Test
fun refreshStoresRemoteArticles() = runTest {
    startKoin { modules(dataModule, testModule) }
    val repository = get<NewsRepository>()
    // ...
    stopKoin()
}
```

Why the wrong form is a problem: the test now depends on the whole module graph, so an unrelated binding change breaks it; `startKoin`/`stopKoin` is global mutable state, so tests cannot run in parallel and a forgotten `stopKoin` leaks into the next test.

### Verify the graph separately — SHOULD

**SHOULD** add one test that checks every module's bindings resolve, so a missing binding fails in CI rather than at runtime. Koin provides a verification facility for this; **MUST** confirm the current API name against the Koin documentation for the version in use. [UNVERIFIED API name]

**SHOULD** declare test dependencies with `koin-test` in `commonTest` so the verification test runs on every target.

**MUST** call `stopKoin()` in teardown for any test that does start a container.

Full strategy: `../quality/testing-strategy.md`.

---

## Cross-references

- Official DI recommendations in context (including Hilt): `../android/app-architecture.md`
- `expect fun` factories vs `expect` classes: `../kmp/project-structure.md`
- `koinViewModel()` and the `viewModel()` initializer requirement: `../kmp/compose-multiplatform.md`
- Swift-callable facade design: `../kmp/ios-interop.md`
- Dispatcher injection, which DI must supply: `../kotlin/coroutines-and-flow.md`
- `HttpClient` as a `single`: `ktor-networking.md`
- Database and DataStore builders as platform bindings: `room-datastore.md`
- Versions: `../version-matrix.md`
