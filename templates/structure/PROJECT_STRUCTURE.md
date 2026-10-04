# Project Structure Convention

**Status:** house convention. When this file (or a copy named `PROJECT_STRUCTURE.md` in a repo) is present, **file placement follows it** and overrides the module-based layout in `../../references/architecture/modularization.md`. The layer *rules* (dependency direction, repository boundary, error handling, cancellation) still come from the references — this file decides *where* things go, not *how* they behave.

**Shape:** package-by-layer inside one Gradle module (or one `commonMain` in KMP). Four roots: `core` → `domain` → `data` → `presentation`. Features are packages, not modules.

**Verified:** see § Verification for exactly what was compiled. The rest of this file is placement rules.

---

## Dependency direction — MUST

```
presentation ──► domain ◄── data
      │                        │
      └──────► core ◄──────────┘
```

| From | May import | MUST NOT import |
|---|---|---|
| `domain` | `core.common` only | `data`, `presentation`, `core.network`, `core.platform`, any SDK, any `androidx.*` |
| `data` | `domain`, `core.*` | `presentation` |
| `presentation` | `domain`, `core.common`, `core.analytics` (event types), Compose, lifecycle, Koin accessors | `data` (any class), `core.network`, `core.platform` directly, Room, DataStore, SDKs |
| `core` | `core.common`; a `core.*` package may depend on another `core.*` only downward (`network` → `common`) | `domain`, `data`, `presentation` |

**The repository boundary** (`../../references/android/app-architecture.md`): a `presentation` class reaches data only through a `domain.repository.*` or `domain.service.*` interface, via a `domain.usecase.*` or directly — never through `data.*` or `core.network.*`.

**Lint it:** a detekt `ForbiddenImport` rule (or an ArchUnit-style test) per row above. A convention enforced only by review is violated within a month.

---

## `core/` — infrastructure, feature-agnostic

| Package | Contains | MUST NOT contain |
|---|---|---|
| `common/result` | `AppResult<T>` / outcome types if the codebase uses a result wrapper; `domainRunCatching` (never stdlib `runCatching` in a coroutine) | feature types |
| `common/exception` | base `AppException`, `NetworkException`, `StorageException`; the closed error hierarchy's roots | HTTP status codes (those are `core.network.error`) |
| `common/logger` | `Logger` interface + no-op; platform sinks live in `core.platform.system` | vendor SDK calls |
| `common/coroutine` | `CoroutineScope` providers (app scope with `SupervisorJob` + `CoroutineExceptionHandler`), `Cancellable` handle type | `GlobalScope` |
| `common/dispatcher` | `DispatcherProvider` interface (`io`, `default`, `main`, `mainImmediate`) + default impl. **Every** class that switches context injects this. | hardcoded `Dispatchers.*` elsewhere |
| `common/time` | `Clock` abstraction (`now(): Instant`), formatting helpers behind an interface | `System.currentTimeMillis()` call sites elsewhere |
| `common/constants` | app-wide constants (timeouts, page sizes, limits) | secrets, URLs, keys |
| `common/extensions` | small, `internal`, stdlib-level extensions | business logic; public extensions on stdlib types |
| `network/client` | `createHttpClient(engine, …)` — the single Ktor client factory | per-feature clients |
| `network/config` | base URL provider, timeouts, environment (`NetworkConfig`) | hardcoded production URLs |
| `network/interceptor` | Ktor plugins/`defaultRequest` config: auth bearer, headers, logging (debug-gated) | `LogLevel.ALL/BODY` in release |
| `network/connectivity` | `ConnectivityObserver` interface; impl via `expect`/platform | — |
| `network/serialization` | the shared `Json { ignoreUnknownKeys = true; explicitNulls = false }` | DTOs |
| `network/error` | HTTP → `core.common.exception` translation (`toAppException()`), one place | feature-specific errors |
| `security/encryption` | crypto helpers over platform keystore/keychain keys | hand-rolled ciphers |
| `security/securestorage` | `SecureStore` interface + Keystore/Keychain impls (tokens live **here**, never in DataStore) | `EncryptedSharedPreferences` |
| `security/device` | root/jailbreak signals, Play Integrity wrapper | — |
| `platform/*` | `audio`, `camera`, `clipboard`, `file`, `permission`, `share`, `system`, `uri`: **implementations** of `domain.service.*` and platform capability interfaces; `expect`/`actual` lives here when unavoidable | business rules; anything a use case should decide |
| `analytics/event` | `AnalyticsEvent` sealed hierarchy — typed events, one definition for all platforms | string literals at call sites |
| `analytics/parameter` | parameter keys/limits | PII |
| `analytics/tracker` | `AnalyticsTracker` interface; vendor impls in `data/analytics/tracker` | — |
| `ads/*` | `AdPolicy`, `BannerAd`/`InterstitialAds`/`RewardedAds` interfaces + platform impls; `ads/config` ad-unit ids per build type (test ids in debug) | ads shown without consent/entitlement checks |
| `billing/*` | `PurchaseClient` interface + Play Billing/StoreKit impls; `product`/`purchase`/`subscription` models **of the store SDK**, `config` product ids | entitlement decisions (those are `domain`) |
| `notification/*` | permission request, channel setup, scheduler (WorkManager/`BGTaskScheduler` wrappers) | notification *content* decisions (domain/presentation) |
| `review/` | in-app review request wrapper | — |
| `di/` | `CoreModule.kt`, `NetworkModule.kt`, `PlatformModule.kt` (`expect fun platformModule(): Module`) | feature bindings |

**`core` MUST be feature-blind.** A `core` class that mentions "translation" or "conversation" belongs in `domain`, `data`, or `presentation`.

---

## `domain/` — pure Kotlin, no frameworks

Feature packages (`conversation`, `translation`, `speech`, `texttospeech`, `camera`, `history`, `phrase`, `punctuation`, `billing`, `subscription`, `notification`, `discount`, `usage`, `user`) repeat under each sub-root. **Adding a feature = adding the same package name under `model`, `repository`, `usecase` (and `service`/`event` if needed), plus the mirrors in `data` and `presentation`.**

| Package | Contains | Rules |
|---|---|---|
| `model/<feature>` | immutable `data class`es, `value class` ids, sealed error types, enums | no `@Serializable`, no `@Entity`, no `androidx` imports |
| `repository/<feature>` | **interfaces only** — `suspend` for one-shot, `Flow` for streams; domain types in signatures | no implementation, no DTO/entity types |
| `usecase/<feature>` | classes with `operator fun invoke`; real policy or orchestration of ≥2 repositories/services | pass-through forwarders (call the repository directly instead — `../../references/architecture/clean-architecture.md`) |
| `service/<feature>` | **interfaces** for capabilities with platform implementations: `SpeechRecognizer`, `TextToSpeechEngine`, `OcrService`, `DocumentReader`, `Translator`. Implemented in `core/platform/*` or `data/` | SDK types in signatures |
| `event/<feature>` | sealed domain events (`SubscriptionEvent.Expired`, `ConversationEvent.Ended`) and their bus interface (`SharedFlow`, replay 0) | ViewModel→UI signals (those are state in `<Name>UiState`) |

---

## `data/` — implementations, wire and storage shapes

| Package | Contains | Rules |
|---|---|---|
| `repository/<feature>` | `internal class Default<Feature>Repository : domain.repository.<Feature>Repository`; offline-first where a local source exists; **the single error-translation point** (`core.network.error`) | public implementations; `Dispatchers.*` hardcoded |
| `source/local/room/database` | `AppDatabase.kt` (`@Database`, `@ConstructedBy`, version), `configure()` with `BundledSQLiteDriver` + `addMigrations` | `fallbackToDestructiveMigration` in a shipping build |
| `source/local/room/dao/<feature>` | `internal interface <Feature>Dao` — `suspend` / `Flow` only | blocking functions |
| `source/local/room/entity/<feature>` | `internal data class <Thing>Entity` with `@Entity`; new NOT NULL columns carry `defaultValue` | reuse as domain model |
| `source/local/room/migration` | `MIGRATION_n_m` objects (`override suspend fun migrate(connection: SQLiteConnection)` on Room 3) + the committed `schemas/` directory | — |
| `source/local/datastore` | `AppPreferences.kt` / `UserPreferences.kt` wrap one `DataStore<Preferences>` each; `PreferenceKeys.kt` holds **every** key once | keys duplicated elsewhere; secrets |
| `source/remote/<feature>` | `<Feature>Api.kt` — `internal class` over the injected `HttpClient`, relative paths; `dto/` with `@Serializable` DTOs, nullability from the contract | a new `HttpClient`; DTOs leaving `data` |
| `source/remote/auth` | `AuthApi`, `LoginRequest`/`LoginResponse`/`RefreshTokenResponse` DTOs; refresh wiring via `core.network.interceptor` + `core.security.securestorage` | tokens in DataStore |
| `model/<feature>` | data-layer-only models that are neither DTO nor entity: store-SDK results, combined local+remote intermediates, cache envelopes | domain types (those are `domain.model`) |
| `mapper/<feature>` | `internal fun XDto.toEntity()`, `XEntity.toDomain()`, `X.toDto()` — extension functions, pure | public mappers; mappers in `domain` |
| `analytics/mapper`, `analytics/tracker` | domain/UI event → vendor event mapping; vendor tracker impls (`FirebaseAnalyticsTracker`) | vendor types above `data` |
| `di/` | `DataModule.kt` (DB, DataStore, mappers if any), `RepositoryModule.kt` (`single<XRepository> { DefaultXRepository(...) }`), `SourceModule.kt` (APIs, DAOs, stores) | — |

**Three models per concept** (`dto` → `entity` → `domain.model`) is this structure's explicit choice. Collapse only with a recorded decision.

---

## `presentation/` — Compose + MVVM/UDF

State flows down as one immutable `uiState`; actions flow up as plain method calls. This is the official Android pattern (`../../references/architecture/mvvm-udf.md`). There is **no** `UiEvent`/`UiEffect` type and **no** effect channel.

```
presentation/
├── app/           App.kt  AppViewModel.kt  AppUiState.kt  AppState.kt
├── navigation/    AppRoute.kt  MainTab.kt  AppNavHost.kt  <Area>Graph.kt  NavExtensions.kt
├── screen/<name>/ <Name>Screen.kt  <Name>ViewModel.kt  <Name>UiState.kt  component/
├── component/     shared stateless composables
├── permission/    permission controllers and status types
├── theme/         AppTheme.kt  Color.kt  Type.kt  Dimens.kt
├── util/          UiText.kt  CompositionLocals.kt  DeviceLayoutMode.kt
└── di/            PresentationModule.kt
```

### The screen set — MUST

Every screen owns exactly three files in `presentation/screen/<name>/`, plus an optional `component/` package:

| File | Contains | MUST NOT contain |
|---|---|---|
| `<Name>Screen.kt` | `<Name>NavigationActions`, `<Name>Actions`, `<Name>Route` (stateful), `<Name>Screen` (stateless), previews | a nav controller; business logic |
| `<Name>ViewModel.kt` | `class <Name>ViewModel(...) : ViewModel()` — one `uiState: StateFlow<<Name>UiState>`, public methods for actions | Compose imports; navigation calls; `Channel`/`SharedFlow` to the UI |
| `<Name>UiState.kt` | `@Immutable data class <Name>UiState(...)` and its derived `val`s | lambdas; flows; mutable collections |
| `component/*.kt` | stateless pieces used only by this screen — values, lambdas, `modifier` | a ViewModel; reads of app-state CompositionLocals |

Nested screens nest packages: `screen/memory/detail/`, `screen/settings/profile/`.

```kotlin
// <Name>Screen.kt

/** Where the screen can lead. Built by the navigation graph; the screen knows no routes. */
@Immutable
data class HomeNavigationActions(
    val onOpenPaywall: () -> Unit,
    val onOpenMemory: (String) -> Unit,
)

/** What the user can do. Built by HomeRoute; defaults keep previews short. */
@Immutable
internal data class HomeActions(
    val onRefresh: () -> Unit = {},
    val onProClick: () -> Unit = {},
    val onMemoryClick: (String) -> Unit = {},
)

/** Route: ViewModel + navigation. The only place the two meet. No layout. */
@Composable
fun HomeRoute(
    navigationActions: HomeNavigationActions,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        actions = HomeActions(
            onRefresh = viewModel::refresh,
            onProClick = navigationActions.onOpenPaywall,
            onMemoryClick = navigationActions.onOpenMemory,
        ),
    )
}

/** Screen: values in, lambdas out. Previewable and testable without DI or navigation. */
@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
)
```

| Rule | Level |
|---|---|
| `<Name>Route` takes `navigationActions` first and the ViewModel last, defaulted to `koinViewModel()`. | MUST |
| `<Name>Screen` takes `uiState`, `actions`, `modifier` — nothing else. It **MUST NOT** read a nav controller or an app-state CompositionLocal. | MUST |
| `<Name>NavigationActions` has **no** default values, so a forgotten exit is a compile error. `<Name>Actions` has defaults, for previews. | SHOULD |
| `<Name>NavigationActions` is consumed by `<Name>Route` only; it is never passed into `<Name>Screen` or a component. | MUST |
| A click that only navigates goes `navigationActions` → `actions` and skips the ViewModel. | SHOULD |
| A decision that picks between two destinations (premium gate) is made once, in `<Name>Route` or the ViewModel — never inside a component. | MUST |
| Layout mode, window size and similar are read once in `<Name>Screen` and passed down as parameters. | SHOULD |
| Portrait/landscape/tablet variants are one composable with parameters, not copies. | SHOULD |
| Composable parameters: required first, then `modifier`, then optionals. | MUST [OFFICIAL] |
| Move a composable to `component/` once `<Name>Screen.kt` passes ~300 lines. | SHOULD |

### The ViewModel — MUST

- Exposes exactly one `uiState: StateFlow<<Name>UiState>`; the `MutableStateFlow` is private and written with `update { }`. Prefer `combine(...).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)` when state is derived from repository streams.
- Actions are public methods named for what happens (`refresh()`, `delete()`), not a sealed event type.
- Reads navigation arguments from `SavedStateHandle`; the Route receives no ids.
- Rethrows `CancellationException`; puts every other failure in `uiState` as `UiText`.
- **One-off outcomes are state.** A message is an item in `userMessages` that the UI acknowledges (`userMessageShown(id)`); "saved, now leave" is a flag (`isSaved`) that `<Name>Route` reacts to in a `LaunchedEffect` and then calls its navigation action. Mechanics: `../../references/architecture/mvvm-udf.md`.
- **MUST NOT** send events to the UI through a `Channel` or `SharedFlow`. An indefinite snackbar collected from a channel blocks every effect queued behind it, and an effect consumed while the UI is stopped is lost.

### `app/` — app-level state

| File | Contains |
|---|---|
| `App.kt` | theme, the single snackbar host, `CompositionLocalProvider`, `AppNavHost`. Reacts to `AppUiState` (signed out → auth graph; update downloaded → prompt) with `LaunchedEffect`s keyed on state. |
| `AppViewModel.kt` | combines app-wide streams (session, entitlement, appearance, update status) from domain use cases. Startup sequencing belongs in a use case, not in `init`. |
| `AppUiState.kt` | startup and session only: `isDataLoaded`, `initialRoute`, `isAuthenticated`, update status. **MUST NOT** carry per-screen data such as usage counters. |
| `AppState.kt` | the app-wide, read-mostly value screens show (subscription tier) and its extension properties. |

### App-wide state and CompositionLocals — MUST

| State | Delivery |
|---|---|
| Theme, dimensions, language | CompositionLocal |
| Small, immutable, read-mostly, needed across the tree (`AppState`: subscription tier) | `LocalAppState` **MAY** be used — read it in `<Name>Route` only and pass the value down as a parameter |
| Anything a ViewModel decides on (limits, gating, what to load) | the domain use case injected into that ViewModel, mapped into its `UiState` |
| Frequently changing or screen-specific data (usage numbers) | that screen's ViewModel → `UiState`; **MUST NOT** be a CompositionLocal |
| The nav controller | **MUST NOT** be a CompositionLocal; navigation leaves a screen through `<Name>NavigationActions` |

Both delivery paths **MUST** start from the same domain use case, so they cannot disagree. While an app-wide value is still loading, **MUST NOT** treat it as its negative ("not premium"): hide upsell UI and skip gating until it resolves.

### `navigation/` — one owner per back stack

| File | Contains |
|---|---|
| `AppRoute.kt` | `@Serializable sealed interface AppRoute` — full-screen destinations and nested-graph keys. Arguments are ids only; **MUST NOT** carry PII or whole models. |
| `MainTab.kt` | `@Serializable sealed interface MainTab` — bottom-navigation destinations. A separate type, so a tab cannot be sent to the app back stack. |
| `AppNavHost.kt` | the single app-level host; calls the graph builders. |
| `<Area>Graph.kt` | `NavGraphBuilder.<area>Graph(navController)` — a real nested graph. Builds each screen's `<Name>NavigationActions`; **every transition is readable here**. |
| `NavExtensions.kt` | stack-clearing helpers (`navigateToMain()`, `navigateMainTab()`). |

- The navigation library is the project's own (Navigation 3 `NavDisplay` or Navigation 2 `NavHost`); this structure does not choose it — `../../references/android/navigation.md`.
- Each host is declared **once**. A shell that switches between bottom bar and rail by layout mode takes the host as a content slot.
- The selected tab is derived with `destination.hasRoute<T>()`, never by comparing against `T::class.qualifiedName` — that breaks under R8.
- Tabs are an `enum` (`MainTabItem(tab, label, icon)`) iterated by the bar; no index literals.

### Other presentation packages

| Package | Contains | Rules |
|---|---|---|
| `component/` | reusable stateless composables — values + lambdas, `modifier` param | ViewModel parameters; feature-specific logic |
| `permission/` | permission controllers, `PermissionStatus` | requesting on screen entry — ask in context, from a user action |
| `theme/` | `AppTheme.kt`, `Color.kt`, `Type.kt`, `Dimens.kt` | hardcoded colours, sizes, or font attributes in screens |
| `util/` | `UiText` (+ `asString()`), `CompositionLocals.kt`, layout-mode helpers | screen-specific types |
| `di/` | `PresentationModule.kt` — one `viewModelOf(::<Name>ViewModel)` per screen | bindings for `data` or `core` |

---

## Naming — MUST

| Thing | Pattern | Example |
|---|---|---|
| Repository interface / impl | `<Feature>Repository` / `Default<Feature>Repository` (or strategy name) | `TranslationRepository` / `OfflineFirstTranslationRepository` |
| Use case | `<Verb><Noun>UseCase`, `operator fun invoke` | `TranslateTextUseCase` |
| Service interface / impl | `<Capability>` / `<Platform><Capability>` | `SpeechRecognizer` / `AndroidSpeechRecognizer` |
| API | `<Feature>Api` | `TranslationApi` |
| DTO | `<Thing>Request` / `<Thing>Response` / `<Thing>Dto` | `TranslateRequest`, `TranslateResponse` |
| Entity / DAO | `<Thing>Entity` / `<Thing>Dao` | `HistoryEntity` / `HistoryDao` |
| Mapper | `fun <From>.to<To>()` in `<Feature>Mapper.kt` | `fun TranslateResponse.toDomain()` |
| Screen set (files) | `<Name>Screen.kt`, `<Name>ViewModel.kt`, `<Name>UiState.kt` | `HomeScreen.kt` … |
| Route / content | `<Name>Route` / `<Name>Screen` | `HomeRoute` / `HomeScreen` |
| Screen callbacks | `<Name>NavigationActions` / `<Name>Actions` | `HomeNavigationActions` / `HomeActions` |
| ViewModel action | verb, no `on` prefix | `refresh()`, `userMessageShown(id)` |
| Callback property | `on<Thing><Verb>` | `onMemoryClick`, `onOpenPaywall` |
| Routes | `AppRoute.<Name>`, `MainTab.<Name>`, `<Area>Graph` | `AppRoute.MemoryDetail` |
| Analytics event | `AnalyticsEvent.<Verb><Noun>` | `AnalyticsEvent.TranslationCompleted` |
| DI module vals | `coreModule`, `networkModule`, `platformModule()`, `dataModule`, `repositoryModule`, `sourceModule`, `presentationModule` | — |

---

## Where the references still apply

| Concern | Reference (unchanged by this structure) |
|---|---|
| Error representation, cancellation, dispatchers | `../../references/kotlin/coroutines-and-flow.md`, `../../references/kotlin/language-essentials.md` |
| Repository rule, layering | `../../references/android/app-architecture.md`, `../../references/architecture/clean-architecture.md` |
| Room / DataStore mechanics | `../../references/libraries/room-datastore.md` |
| Ktor client, auth, tokens | `../../references/libraries/ktor-networking.md`, `../../references/integrations/auth-and-tokens.md` |
| Koin scoping | `../../references/libraries/koin-di.md` |
| Compose state and performance | `../../references/android/compose-ui.md` |
| Secure storage, logging, secrets | `../../references/quality/security.md` |
| Ads, billing, notifications, review | `../../references/integrations/*.md`, `../../references/android/background-work.md` |

**Deviations this structure makes from the reference defaults** (record them in reports as "codebase convention"):

1. Package-by-layer in one module instead of Gradle modules → visibility is by `internal` within the module and by lint rule across layers, not by module boundary.
2. A use case per feature package is expected; still **do not** add pass-through use cases.

---

## Scaffolding

`scaffold.sh` (same directory) creates the tree and generates the navigation key types and screen sets:

```bash
# create the package tree + navigation key types (AppRoute, MainTab)
templates/structure/scaffold.sh init  --root src/commonMain/kotlin --package com.live.voice.translator.instant.speakandtranslate
# add a screen set (3 files) + domain/data feature packages
templates/structure/scaffold.sh screen Translation --root src/commonMain/kotlin --package com.live.voice.translator.instant.speakandtranslate
templates/structure/scaffold.sh feature translation --root src/commonMain/kotlin --package com.live.voice.translator.instant.speakandtranslate
```

For an Android-only project use `--root app/src/main/java` (or `kotlin`).

## Verification

`scaffold.sh init` + `screen Sample` + `feature translation` were run into a copy of `../../examples/user-profile/`, and the generated files — `presentation/navigation/AppRoute.kt`, `MainTab.kt`, and the three-file `Sample` screen set (`SampleNavigationActions`, `SampleActions`, `SampleRoute`, `SampleScreen`, `SampleViewModel`, `SampleUiState`) — compiled with `compileKotlinJvm compileKotlinIosSimulatorArm64 compileAndroidMain` on 2026-10-04 with no warnings (Kotlin 2.4.20, Compose Multiplatform 1.12.1, Koin 4.2.2, lifecycle 2.11.0). Compiled, not tested: the scaffold emits a placeholder `refresh()`. The `Home*` snippets and the `app/`, `navigation/` file descriptions in this document are **not** compiled; they are placement rules and shapes.
