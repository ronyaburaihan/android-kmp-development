# Implementation Decisions

The questions a reference implementation has to answer before its code can be copied. Each answer states the trade-off and what to do when the codebase has already decided differently.

**Verified:** 2026-10-03 against `user-profile/`, which compiles for Android, JVM and both iOS targets, whose test suite runs on JVM and the iOS simulator (including Compose UI tests and a seeded Room migration test), and whose exported framework a Swift smoke file compiles against. Exact counts and the commands run are in `README.md`.

> **The codebase outranks every default here.** These are defaults for new code. A production codebase that is internally consistent wins — match it, and say in your report that you did. See `../references/README.md`.

---

## Where does `User` live?

**`:domain`, in `commonMain`, with no annotations.**

```kotlin
// domain/User.kt
public data class User(
    val id: UserId,
    val displayName: String,
    val email: String,
    val avatarUrl: String?,
)
```

The example keeps **three** separate types for one concept:

| Type | Layer | Owns |
|---|---|---|
| `UserDto` | `:data`, `internal` | the wire format — `@Serializable`, `@SerialName` |
| `UserEntity` | `:data`, `internal` | the storage format — Room annotations in a real module |
| `User` | `:domain`, public | the business meaning |

**Why not one class.** The three roles have independent change drivers. The API renames a field, so the database needs a migration. A local-only flag is added, so it leaks into the JSON contract. `@Transient` and `@Ignore` accumulate until no reader can tell which fields are real. Google labels "a model per layer" **Recommended** for complex apps.

**When one class is right.** A small app with a stable API, where the wire format *is* the domain model, pays three times the boilerplate for nothing. Collapse them, and split when the first divergence appears — not before. `../references/architecture/clean-architecture.md` has the test.

**`UserId` as a value class** costs nothing at runtime and makes `repository.user(userId)` impossible to call with an email. Two compile facts, both found by building the example:

- `@JvmInline` is mandatory — the JVM target rejects a `value class` without it.
- `import kotlin.jvm.JvmInline` is mandatory — the native targets fail with `Unresolved reference 'JvmInline'` without it.

Neither is caught by `compileKotlinMetadata`.

---

## Which module owns `UserRepository`?

**`:domain` owns the interface. `:data` owns the implementation.**

```
:domain   interface UserRepository          ◄── declares what it needs
   ▲
   │ implements
:data     internal class DefaultUserRepository
```

This direction *is* the dependency rule. Declaring the interface in `:data` and importing it from `:domain` inverts it: the domain then cannot compile without Ktor and Room, so it cannot be tested on the host, and in KMP it loses the ability to compile for targets those libraries do not support.

The practical KMP payoff: a `:domain` with no platform dependencies compiles for **every** target and runs in `commonTest` on the host JVM. That is the fastest test loop available. Every platform dependency admitted into `:domain` removes a target or a test environment.

**Acceptance test for correct layering:** can this type be tested in `commonTest` with no platform dependency? If a domain type needs an emulator, it is in the wrong module.

---

## Where should the repository implementation live?

**`:data`, `internal`, named for its strategy.**

```kotlin
internal class DefaultUserRepository(
    private val remote: UserRemoteDataSource,
    private val local: UserLocalDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) : UserRepository
```

Three decisions in that signature:

**`internal`.** Callers depend on the domain interface; the DI module is the only public entry point. A `public` implementation lets a consumer construct it directly, bypassing the interface and the DI graph — and makes the DTO type part of the module's effective public API.

**Named `Default…`, not `…Impl`.** `OfflineFirstUserRepository`, `InMemoryUserRepository` and `FakeUserRepository` then read as peers. `Default` is the fallback when no better strategy name exists. Google's naming guidance, labelled *Optional*.

**Offline-first.** `observeUser()` reads from the local store, so the UI renders cached data immediately and works without a network; `refresh()` writes through. The alternative — read from the network, fall back to cache — makes every screen depend on connectivity.

**The injected dispatcher is not stylistic.** `withContext(ioDispatcher)` lives *inside* `refresh()`, which makes the function main-safe: no caller needs to know where it runs. Hardcoding `Dispatchers.IO` means tests race a real thread pool, and the usual outcome is a flaky suite that gets disabled.

---

## How should errors be represented?

**Typed domain errors, carried by an exception.** The example uses a closed `UserError` plus `UserException`.

```kotlin
public sealed interface UserError {
    public data object NotFound : UserError
    public data object Unauthorized : UserError
    public data object Offline : UserError
    public data class Unexpected(val cause: Throwable) : UserError
}
```

**Closed, not open.** The UI must be able to decide exhaustively what to show. An open hierarchy or a bare `Throwable` pushes that into an `else` branch — which is where "Something went wrong" comes from.

**Translated once, at the data-layer boundary.** `DefaultUserRepository.refresh()` is the only place transport failures become domain errors. The repository returns domain types only — never `HttpResponse`, a DTO, or a Ktor exception.

**Never a sentinel value.** Returning an empty list on failure makes "no data" and "the network is down" indistinguishable, so the UI shows an empty state instead of a retry, and the failure never reaches crash reporting.

### Why exceptions rather than `Result<User>`

| | Typed exception | `Result<T>` / sealed outcome |
|---|---|---|
| Cancellation | correct by construction | **must be handled by hand at every boundary** |
| Swift export | maps to `throws` with `@Throws` | generic — **not usable from Swift** |
| Failure visible in signature | no (KDoc only) | yes |
| Happy path | direct | unwrap or `map`/`flatMap` at each layer |

The cancellation column is the decisive one. `kotlin.Result` has no notion of cancellation, and `runCatching` catches `Throwable` — including `CancellationException`. A cancelled job then reports *success* to its parent, and structured concurrency is silently broken.

**If your codebase already uses `Result`, match it** — and supply the helper that makes it safe. `user-profile/src/commonMain/kotlin/com/example/userprofile/alternatives/ResultStyle.kt` is compiled and verified:

```kotlin
public inline fun <T> domainRunCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e                       // never wrap cancellation as a failure
    } catch (e: Exception) {
        Result.failure(e)
    }
```

Using the stdlib `runCatching` inside a coroutine is a defect, not a style choice.

### Where the error becomes words

The domain decides **what failed**; the presentation decides **what to say**.

```kotlin
private fun UserError.toMessageKey(): MessageKey = when (this) {
    UserError.NotFound -> MessageKey.USER_NOT_FOUND
    UserError.Unauthorized -> MessageKey.SESSION_EXPIRED
    UserError.Offline -> MessageKey.OFFLINE
    is UserError.Unexpected -> MessageKey.USER_LOAD_FAILED
}
```

**`UiState` must not hold a `Throwable`**, and the ViewModel must not hold a hardcoded string. The ViewModel has no `Context`, so it cannot call `getString`; it puts a `UiText` in the state and the UI resolves it. An `R.string` id will not compile in `commonMain`, which is why `UiText.Key` wraps an enum rather than an `Int`.

---

## How should cancellation be handled?

Four rules, each with a test in the example.

**1. Rethrow `CancellationException` before any other catch.**

```kotlin
override suspend fun refresh(): Unit = withContext(ioDispatcher) {
    val dto = try {
        remote.fetchUser()
    } catch (e: CancellationException) {
        throw e                                  // first clause, always
    } catch (e: Exception) {
        throw UserException(e.toUserError())
    }
    local.put(dto.toEntity())
}
```

Catching it tells the parent the child completed normally, so `join()`/`await()` returns instead of propagating cancellation and resources tied to the cancelled scope are never released. `DefaultUserRepositoryTest.cancellationPropagatesAndIsNotTranslated` pins the clause ordering.

**2. Never catch `Exception` or `Throwable` without that first clause.** `Throwable` additionally captures `Error` on the JVM.

**3. The ViewModel owns the scope, not the UI.** `refresh()` returns `Unit` and launches in `viewModelScope`. Exposing `suspend fun refresh()` would make the UI own cancellation — and on iOS it exports as a completion handler with no real cancellation at all.

**4. `finally` for state that must settle.** `isRefreshing` is cleared in `finally`, so a cancelled refresh does not leave a spinner on screen forever.

**Not applicable here:** `ensureActive()` in CPU-bound loops. There is no such loop in this example; all suspension points are already cancellable.

---

## Is this ViewModel Android-specific or shared?

**Shared. The build proves it** — `UserViewModel` compiles for Android, JVM, `iosArm64` and `iosSimulatorArm64`, and its tests run on JVM and the iOS simulator. On Android the `androidx.lifecycle.ViewModel` import resolves to Google's artifact, on the other targets to JetBrains'.

```kotlin
import androidx.lifecycle.ViewModel          // commonMain
import androidx.lifecycle.viewModelScope
```

The dependency is `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.11.0` — JetBrains' multiplatform build, which keeps the `androidx.lifecycle` package name. Nothing in `UserViewModel` is Android-specific.

**Four consequences that are not obvious:**

**`google()` is required in your repositories even with no Android target.** The JetBrains artifact depends on `androidx.lifecycle:lifecycle-viewmodel`, which is published to Google's Maven repository, not Maven Central. Found by building this example; the failure is `Could not find androidx.lifecycle:lifecycle-viewmodel:2.11.0`.

**`viewModel()` needs an explicit initializer off-JVM.** There is no type reflection on native or web.

```kotlin
// CORRECT on every target
viewModel { UserViewModel(observeUserProfile, userRepository) }
// or, preferably, the DI accessor
koinViewModel()

// Compiles on Android, fails at runtime on iOS and web
viewModel()
```

**With a native SwiftUI UI there is no `ViewModelStoreOwner` on iOS at all.** `ComposeUIViewController` provides one; SwiftUI does not. A third-party observability bridge is needed, which the official CMP documentation points to but which remains a third-party dependency.

**JVM desktop needs `kotlinx-coroutines-swing`.** `viewModelScope` uses `Dispatchers.Main.immediate`, which has no JVM implementation without it. This module's `jvm()` target runs no UI, so it is not declared — a desktop app must add it.

### Should the ViewModel be shared?

| Share it when | Keep it Android-only when |
|---|---|
| iOS uses Compose Multiplatform | iOS UI is native SwiftUI and the team does not want the bridge |
| State logic is substantial and duplicated | The screen is thin and platform-shaped |
| One team owns both platforms | Separate teams own each UI |

Sharing the ViewModel is the **last** stage in a KMP adoption plan for a reason: it is where the iOS team's workflow changes most. `../workflows/assess-kmp-adoption.md` stages it after models, domain logic, networking and persistence.

---

## Why no `GetUserUseCase`

The example has `ObserveUserProfileUseCase`, which combines two streams and applies a display rule neither repository owns. It deliberately has **no** `GetUserUseCase`.

```kotlin
// NOT in the example — a layer with no policy in it
class GetUserUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(): User = repository.getUser()
}
```

A use case that forwards one repository call adds a file, a DI binding, a constructor parameter and a test that can only assert delegation. It also buries the real use cases among forwarders. Google labels the domain layer **optional** — "recommended in big apps" — so a mandatory use case per repository method is not an official requirement.

**Add a use case when it** contains policy more than one ViewModel needs, orchestrates two or more repositories, or holds a rule that deserves its own test. `ObserveUserProfileUseCase` does all three.

**If your codebase already has a use case per method, match it.** Consistency inside a codebase that made that choice outranks this guidance. Say in your report that you matched it.

---

## Why one `uiState` instead of three flows

```kotlin
// The example
public val uiState: StateFlow<UserUiState>

// NOT the example
val profile: StateFlow<UserProfile?>
val isLoading: StateFlow<Boolean>
val error: StateFlow<String?>
```

Three flows emit independently, so the UI can render "loading" *and* a populated profile *and* an error simultaneously. One state object makes every emission a consistent snapshot.

**Data class, not sealed interface, here.** The states are not mutually exclusive: the screen keeps showing a cached profile while refreshing. A sealed hierarchy whose branches all have to carry `profile` is the signal to use a data class. Use a sealed interface when the states genuinely exclude each other.

**`stateIn(..., WhileSubscribed(5_000), ...)`** keeps the upstream alive across a configuration change without keeping it alive while the app is backgrounded. The 5-second figure is from Google's own recommendation.

**Testing consequence:** a `StateFlow` built this way has no active upstream until something collects. Reading `.value` without a collector sees the initial value forever — a test that passes while asserting nothing. Every ViewModel test in the example collects.

---

## Why one-off messages live in state

`UserUiState.messages` plus `onMessageShown(id)`, not a `Channel` of events.

```kotlin
public fun onMessageShown(messageId: Long) {
    transient.update { state ->
        state.copy(messages = state.messages.filterNot { it.id == messageId })
    }
}
```

A `Channel`/`SharedFlow(replay = 0)` from ViewModel to UI depends on a collector being active at emit time. If the UI is stopped, the event is dropped or delivered late. Configuration change, process death and back-stack pops produce duplicated snackbars and double navigation, and there is no state to inspect when debugging because the event is already consumed.

Google marks **"do not send events from the ViewModel to the UI"** as *Strongly recommended*.

The characteristic bug of the state-held approach is a message that is added but never cleared, so `UserViewModelTest.messageIsClearedAfterAcknowledgement` covers the acknowledgement explicitly.

**If your codebase uses an event channel, match it** and record the deviation. Do not introduce a second pattern in one codebase.

---

## Why `explicitApi()`

`build.gradle.kts` enables `explicitApi()`, so the compiler rejects a public declaration without an explicit visibility modifier or return type.

An inferred return type on a public declaration means an unrelated implementation change can silently alter the module's binary API — and, in a KMP module, the generated Objective-C header, which breaks the Swift build. Enforcing it at the compiler rather than in review is the difference between a rule and an aspiration.

This is why the example says `public val uiState: StateFlow<UserUiState> = ...` rather than `val uiState = _uiState.asStateFlow()`.

---

## What the compiler taught us — findings from building this example

Each of these looked correct and failed on exactly one target, at configuration time, or only in Swift. They are the argument for compiling every target — and the Swift side — before calling an example verified.

| Looked correct | What actually happened | Rule it produced |
|---|---|---|
| `value class UserId(...)` | JVM: `Value classes without '@JvmInline' annotation are not yet supported` | `@JvmInline` is mandatory |
| `@JvmInline value class` with no import | Native: `Unresolved reference 'JvmInline'` | `import kotlin.jvm.JvmInline` is mandatory in `commonMain` |
| `mavenCentral()` only, no Android target | `Could not find androidx.lifecycle:lifecycle-viewmodel:2.11.0` | the JetBrains KMP lifecycle artifact depends on the AndroidX one → `google()` in `dependencyResolutionManagement` |
| `alias(libs.plugins.androidx.room3)` | `Plugin [id: 'androidx.room3', version: '3.0.3'] was not found` | `google()` in `pluginManagement` too |
| `room { schemaDirectory(...) }` | `Unresolved reference: room` | the Room 3 extension is `room3 {}` |
| `public fun environmentModule(local: UserLocalDataSource)` | `'public' function exposes its 'internal' parameter type` | `explicitApi()` catches API leaks the reviewer would miss; the function became `internal` |
| `MockEngine { request -> handler(request) }` with a `MockEngine.` receiver | receiver type mismatch | the handler type is `MockRequestHandler` (receiver `MockRequestHandleScope`) |
| presenter test: `onEvent(); advanceUntilIdle(); expectMostRecentItem()` | `No item was found` | with `backgroundScope` on a `StandardTestDispatcher`, a **suspension point** (`awaitItem()`) is what lets the scheduler run the launched work; the test now waits with `awaitItem()` |
| two DataStore tests sharing one path over separate `FakeFileSystem`s | JVM passed; iOS: `There are multiple DataStores active for the same file` — and a per-instance counter did not fix it, because the Kotlin/Native runner creates a fresh test-class instance per method | DataStore's duplicate-instance guard is process-global by **path string**, released only when the store's scope is cancelled; tests need a *globally* unique path; production needs one `single` per file |
| Room's `expect object AppDatabaseConstructor` | Beta warning on every build | the one recorded reason to add `-Xexpect-actual-classes`; never to silence a class you wrote yourself |
| `override fun migrate(connection: SQLiteConnection)` copied from Room 2 habits | `Non-suspend function 'migrate' cannot override suspend function` | Room 3's `Migration.migrate` is **`suspend`** |
| `MigrationTestHelper(schemaDirectoryPath = schemas/<DatabaseClass>)` | `NoSuchFileException: …/AppDatabase/AppDatabase/1.json` | the helper appends the database class name; pass the `schemas/` **root** |
| `import androidx.sqlite.use` | `Unresolved reference 'use'` | `SQLiteStatement`/`SQLiteConnection` are `AutoCloseable`; the stdlib `use` applies, no import |
| Gradle 8.14.3 with the Android Studio JDK | configuration fails; the whole error is `25.0.3` | Gradle 8.14 does not support JDK 25; AGP 9 needs Gradle 9 anyway — Gradle 9.8.0 + JDK 25 works |
| Android target with no `androidMain` actuals | `NO_ACTUAL_FOR_EXPECT … in module '<commonMain> for JVM'` on `compileAndroidMain` | every `expect` needs an actual **per declared target**; adding a target adds a source set to fill |
| `@ObjCName("SharedApi")` on the facade | `This declaration needs opt-in … ExperimentalObjCName` | `@ObjCName` is opt-in; the framework prefix is the stable default |
| `scope.cancel()` with no import | `Unresolved reference 'cancel'` | `CoroutineScope.cancel` is an extension: `import kotlinx.coroutines.cancel` |
| Swift smoke using `UserProfileSharedApi` | `'UserProfileSharedApi' has been renamed to 'SharedApi'` | the header emits `swift_name("SharedApi")`: Objective-C sees the prefix, **Swift does not** |

Per-example notes — what each file demonstrates, the mistakes it avoids, how it is tested — are in `CATALOG.md`.

## Where to look next

| Topic | Document |
|---|---|
| Layering, dependency rule, boundary mapping | `../references/architecture/clean-architecture.md` |
| UI state, events, state holders | `../references/architecture/mvvm-udf.md` |
| Dispatchers, scopes, cancellation, `stateIn` | `../references/kotlin/coroutines-and-flow.md` |
| The Ktor data source this example stubs | `../references/libraries/ktor-networking.md` |
| The Room entity and DAO this example stubs | `../references/libraries/room-datastore.md` |
| DI wiring, platform modules, `koinViewModel()` | `../references/libraries/koin-di.md` |
| Export constraints on the shared API | `../references/kmp/ios-interop.md` |
| Fakes, test source sets, mutation checking | `../references/quality/testing-strategy.md` |
