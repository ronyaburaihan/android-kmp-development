# Example Catalog

One entry per example the skill promises. Every entry points at **compiled, tested** source in `user-profile/` unless marked otherwise. For each: where it is, what it demonstrates, the mistakes it is written to avoid, and how it is tested.

Base path for all source references: `user-profile/src/commonMain/kotlin/com/example/userprofile/` (main) and `user-profile/src/commonTest/kotlin/com/example/userprofile/` (test).

The *why* behind the cross-cutting decisions (where `User` lives, who owns the repository interface, exceptions vs `Result`, cancellation, shared vs Android ViewModel) is in `DECISIONS.md` and is not repeated here.

---

## Repository

| | |
|---|---|
| Interface | `domain/UserRepository.kt` — owned by the domain |
| Implementation | `data/DefaultUserRepository.kt` — `internal`, offline-first, injected dispatcher |
| Demonstrates | repository rule (UI never touches a data source); `Flow` for reads, `suspend` for writes; single error-translation point; main-safety via `withContext` inside the function |
| Common mistakes avoided | interface declared in `:data` (inverts the dependency rule); `Dispatchers.IO` hardcoded (untestable); `catch (e: Exception)` without rethrowing `CancellationException`; returning `emptyList()` on failure; exposing the DTO or entity |
| Tested by | `data/DefaultUserRepositoryTest.kt` — write-through, each error translation, `Unexpected` preserves cause, **cancellation propagates untranslated** |

## Use case

| | |
|---|---|
| Source | `domain/ObserveUserProfileUseCase.kt` |
| Demonstrates | a use case that earns its place: combines two repositories and applies a display rule neither owns; `operator fun invoke` |
| Common mistakes avoided | a pass-through `GetUserUseCase` that forwards one repository call (deliberately absent — see `DECISIONS.md`); a use case that creates its own scope |
| Tested by | `domain/ObserveUserProfileUseCaseTest.kt` — defaults, each preference, boundaries (single-word name, blank name pinned as `"?"`) |

## ViewModel (shared)

| | |
|---|---|
| Source | `presentation/UserViewModel.kt` — JetBrains `androidx.lifecycle.ViewModel`, compiles for JVM and iOS |
| Demonstrates | one immutable `uiState: StateFlow<…>` with explicit type; `stateIn(WhileSubscribed(5_000))`; actions as methods; one-off messages held in state with `onMessageShown` acknowledgement; error → `UiText` mapping without a `Context` |
| Common mistakes avoided | exposing `MutableStateFlow`; three independent flows; a `Channel` of events to the UI; a hardcoded string or `Throwable` in state; `AndroidViewModel`; parameterless `viewModel()` off-JVM |
| Tested by | `presentation/UserViewModelTest.kt` — profile emission, refresh delegation, failure → message, **acknowledgement clears**, every error maps to its own key. Needs `Dispatchers.setMain` because `viewModelScope` is Main-bound |

## Presenter

| | |
|---|---|
| Source | `presentation/UserProfilePresenter.kt` — plain Kotlin, caller-owned `CoroutineScope`, sealed `Event` |
| Demonstrates | the same UI contract as the ViewModel with no lifecycle framework; when a presenter beats a ViewModel (native SwiftUI host, Circuit-style composition, tests without `setMain`) |
| Common mistakes avoided | presenter creating its own scope (nobody cancels it); `GlobalScope`; exposing `StateFlow` directly to Swift |
| Tested by | `presentation/UserProfilePresenterTest.kt` — same assertions as the ViewModel test with **no `Dispatchers.setMain`**; the test's `awaitState` helper shows that suspension drives virtual time. Reference: `../references/architecture/presenter.md` |

## StateFlow

| | |
|---|---|
| Source | `presentation/UserViewModel.kt` (`uiState`), `presentation/UserProfilePresenter.kt` (`state`) |
| Demonstrates | `combine(...).stateIn(scope, WhileSubscribed(5_000), initial)`; conflation; `.update { }` for atomic transitions; derived property (`isInitialLoad`) instead of a stored field |
| Common mistakes avoided | `SharingStarted.Eagerly` (upstream alive while backgrounded); `value = value.copy()` (lost updates); reading `.value` in a test with no collector (sees the initial value forever) |
| Tested by | every ViewModel/presenter test **collects** before asserting — the `WhileSubscribed` rule in practice |

## SharedFlow

| | |
|---|---|
| Source | `session/SessionEvents.kt` — `SessionEventBus` |
| Demonstrates | the legitimate `SharedFlow` use: a data-layer **broadcast** to several consumers (sign-out, token refreshed); `replay = 0` so late subscribers do not get a stale sign-out; `extraBufferCapacity` + `DROP_OLDEST` so `tryEmit` never fails from a non-suspending callback |
| Common mistakes avoided | `SharedFlow`/`Channel` for ViewModel → UI one-off events (dropped/duplicated across lifecycle); capacity 0 with `SUSPEND` making `tryEmit` return false |
| Tested by | `session/SessionEventBusTest.kt` — delivered to every active subscriber; late subscriber gets nothing; publish never fails without collectors |

## Koin modules

| | |
|---|---|
| Source | `di/Modules.kt`; actuals `jvmMain/.../di/PlatformModule.jvm.kt`, `iosMain/.../di/PlatformModule.ios.kt` |
| Demonstrates | `single` for the client/repositories/bus, `factory` for use cases, `factory` (standing in for `viewModelOf`) for the ViewModel; `expect fun platformModule(): Module`; an `internal` environment module so tests inject `MockEngine` and in-memory stores; BOM usage |
| Common mistakes avoided | `Context` in `commonMain`; `single` for stateless objects; `single` for a ViewModel; `KoinComponent`/`by inject()` in domain classes; reified `get<T>()` at the Swift boundary; a public function exposing an `internal` type (the compiler rejected the first version — see `DECISIONS.md`) |
| Tested by | `di/KoinGraphTest.kt` — every public binding resolves in an isolated `koinApplication { }` (no global `startKoin`); repository is a `single`. This is what stands in for compile-time validation |

## Ktor client

| | |
|---|---|
| Source | `network/HttpClientFactory.kt` (`createHttpClient`), `network/KtorUserRemoteDataSource.kt` |
| Demonstrates | one client, **engine injected**; `expectSuccess = true`; `ContentNegotiation` with `ignoreUnknownKeys`; `HttpTimeout`; bearer `Auth` with `loadTokens` / `refreshTokens` / host-scoped `sendWithoutRequest`; relative paths under `defaultRequest`; no logging plugin in the production path |
| Common mistakes avoided | `HttpClient()` per call; `LogLevel.ALL`; `sendWithoutRequest { true }` (token sent to every host); DTO returned above the data layer; Retrofit in `commonMain` |
| Tested by | `network/KtorUserRemoteDataSourceTest.kt` — `MockEngine` asserts the **request** (path, bearer header attached up front) and the response; unknown fields tolerated; 4xx throws a typed exception |

## Token management (API authentication)

| | |
|---|---|
| Source | `network/Tokens.kt` — `TokenStore`, `TokenRefresher`, `TokenManager` |
| Demonstrates | tokens behind an interface the platform implements over secure storage; one refresh at a time (`Mutex`); rejected refresh → clear + broadcast `SignedOut`; sign-out propagation via `SessionEventBus` |
| Common mistakes avoided | tokens in DataStore/Room plaintext; concurrent refreshes; retrying a rejected refresh; sign-out that clears the store but not consumers |
| Tested by | `network/TokenManagerTest.kt` — stores and publishes on success; clears and signs out on rejection; concurrent calls serialise; no-op with no stored token. Reference: `../references/integrations/auth-and-tokens.md` |

## DataStore

| | |
|---|---|
| Source | `settings/DataStoreSettingsRepository.kt` — implements the domain's `SettingsRepository` |
| Demonstrates | Preferences DataStore on KMP via `OkioStorage`; keys, defaults and the file name in one place; `.catch { emit(emptyPreferences()) }` on reads; parsing a stored enum defensively; a platform-agnostic `create(fileSystem, producePath)` factory |
| Common mistakes avoided | a key string duplicated in a ViewModel; unhandled `dataStore.data` errors (screen crash on a corrupt file); two instances over one file; Proto DataStore on KMP (unsupported); `SharedPreferences` / `EncryptedSharedPreferences` |
| Tested by | `settings/DataStoreSettingsRepositoryTest.kt` — real DataStore over `FakeFileSystem`: defaults when unset; writes persist and emit |

## Room

| | |
|---|---|
| Source | `database/AppDatabase.kt` — Room 3 (`androidx.room3` 3.0.3) entity, DAO, database, `@ConstructedBy` constructor, `MIGRATION_1_2`, common `configure()` with `BundledSQLiteDriver` + `addMigrations` |
| Demonstrates | KMP-first Room: coroutine-only DAO, `SQLiteDriver`, KSP registered **per target** (Android, JVM, 2× iOS), exported schemas `1.json` and `2.json`, an additive migration with a `defaultValue` column, **`override suspend fun migrate(connection: SQLiteConnection)`**, no destructive fallback, `-Xexpect-actual-classes` for the one documented reason |
| Common mistakes avoided | blocking DAO functions; bare `ksp(...)`; `room {}` instead of `room3 {}`; missing `google()` in `pluginManagement`; mixing `androidx.room` and `androidx.room3`; a new NOT NULL column without a default; non-suspend `migrate` (Room 2 signature); `fallbackToDestructiveMigration` in a shipping build |
| Tested by | `database/UserRowDaoTest.kt` (JVM + iOS, in-memory) and **`jvmTest/.../AppDatabaseMigrationTest.kt`** — `MigrationTestHelper` from `room3-testing` creates the DB at v1 from `schemas/`, seeds a row, runs `MIGRATION_1_2`, validates against `2.json`, asserts the row survived and the new column took its default |

## Compose UI

| | |
|---|---|
| Source | `ui/UserProfileScreen.kt` (route + content), `ui/UiTextResolver.kt`, `composeResources/values/strings.xml` — **compiled on Android, JVM, iOS** |
| Demonstrates | route/content split with `koinViewModel()` + `collectAsStateWithLifecycle()`; every state rendered (loading, empty, content, refreshing); `LaunchedEffect(message.id)` acknowledgement; `UiText.Key` → `composeResources` string via an exhaustive `when`; `consumeWindowInsets` (edge-to-edge); `testTag`s; `heading()` and a described progress indicator |
| Common mistakes avoided | ViewModel in a content composable; `collectAsState()` on a ViewModel flow; hardcoded strings; `R.string` ids in shared code; `LaunchedEffect(Unit)` for messages |
| Tested by | `ui/UserProfileScreenTest.kt` — 6 tests via `androidx.compose.ui.test.v2.runComposeUiTest`, run on **JVM and iOS simulator**; nodes addressed by `testTag`. The remaining fragments (lists, deferred reads, Android `R.string`, Android DI) are in `ui-layer.md`, marked per section |

## Swift-facing facade (iOS interop)

| | |
|---|---|
| Source | `iosMain/.../api/SharedApi.kt`; `swift-smoke/SharedApiSmoke.swift` |
| Demonstrates | the Objective-C-export-safe shape: no `Flow` (callback + `Cancellable` handle), no generics, no default arguments, primitive-field `ProfileSnapshot` DTO, `@Throws` on every throwing function incl. `CancellationException`, a non-reified `SharedApiFactory` for Koin; `swift_name` means Swift uses unprefixed names |
| Common mistakes avoided | exporting `Flow`/`StateFlow`; exporting domain types with value classes; missing `@Throws` (the app terminates); reified `get<T>()` at the boundary; `@ObjCName` without its opt-in; assuming Swift sees prefixed names |
| Tested by | `swift-smoke/SharedApiSmoke.swift` **compiles** against the generated `UserProfile.framework` header with `xcrun swiftc` — proves the surface is callable (`async throws`, callback, handle). Compile-only; not executed on a simulator |

## expect / actual

| | |
|---|---|
| Source | `platform/Platform.kt` (`expect fun deviceInfo()`), `androidMain/.../Platform.android.kt`, `jvmMain/.../Platform.jvm.kt`, `iosMain/.../Platform.ios.kt`; `di/Modules.kt` (`expect fun platformModule()`) with three actuals |
| Demonstrates | `expect` **functions**, not classes; platform file-name suffixes; the one justified `expect object` (Room's constructor) with its reason recorded |
| Common mistakes avoided | `expect class` for a capability (one implementation per target, unfakeable, Beta); `-Xexpect-actual-classes` added to silence a warning on a class you wrote yourself |
| Tested by | `platform/PlatformTest.kt` — runs on JVM and iOS; the Android actual is compiled (no Android test source set in this example) |

## Platform interfaces

| | |
|---|---|
| Source | `platform/Platform.kt` (`DeviceInfo`), `network/Tokens.kt` (`TokenStore`), `data/UserDataSources.kt` (`UserLocalDataSource`, `UserRemoteDataSource`) |
| Demonstrates | capability declared in common code as an interface; implemented per platform; supplied by DI; trivially faked in `commonTest` |
| Common mistakes avoided | platform types in `commonMain`; `Context` threaded through shared code; `expect`/`actual` reaching a platform SDK from the domain |
| Tested by | every `commonTest` file uses a fake implementation of one of these; `di/KoinGraphTest.kt` resolves `DeviceInfo` through the platform module |

## Unit testing

| | |
|---|---|
| Source | `fakes/Fakes.kt` and every `*Test.kt` under `commonTest` |
| Demonstrates | fakes over mocks; `kotlin-test` only in `commonTest`; one behaviour per test; error paths and boundaries, not only happy paths; tests run on JVM **and** iOS simulator |
| Common mistakes avoided | JUnit/MockK/Robolectric in `commonTest` (will not compile for native); `verify(times(n))`; `Thread.sleep`; text matching; shared mutable fixtures |
| Tested by | the suite itself — see `README.md` for the per-target execution record |

## Coroutine testing

| | |
|---|---|
| Source | `data/DefaultUserRepositoryTest.kt` (`UnconfinedTestDispatcher`, cancellation), `presentation/UserViewModelTest.kt` (`Dispatchers.setMain`/`resetMain`), `presentation/UserProfilePresenterTest.kt` (`backgroundScope`, suspension-driven scheduling), all `*Test.kt` (`runTest`, Turbine) |
| Demonstrates | injected `TestDispatcher`; `runTest` virtual time; Turbine for emission sequences; `Dispatchers.setMain` when and only when `viewModelScope` is involved; pinning that cancellation propagates |
| Common mistakes avoided | real dispatchers in tests (flaky); `runBlocking`; `advanceUntilIdle()` where a suspension point is what actually drives the scheduler (the first version of the presenter test made this mistake); reading `.value` of a `WhileSubscribed` flow with no collector |
| Tested by | — these *are* the tests |

---

## Not in the compiled example

| Promised example | Where it is instead | Why not compiled |
|---|---|---|
| Lazy-list keys, deferred reads, Android `R.string` resolver, Android DI wiring | `ui-layer.md` (marked per section) | no list screen, no Activity, no Android test source set in this example |
| Ktor OkHttp / Darwin engine wiring | `../references/libraries/ktor-networking.md` | the example injects `MockEngine`; engine artifacts are platform dependencies the host app declares |
| Keystore / Keychain `TokenStore` implementations | `../references/quality/security.md` | platform code requiring an Android/iOS app target |
| XCTest **execution** on a simulator | `../references/kmp/ios-interop.md` | the Swift smoke is compile-only; running needs an Xcode project |
