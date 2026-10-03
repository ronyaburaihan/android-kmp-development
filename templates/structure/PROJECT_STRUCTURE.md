# Project Structure Convention

**Status:** house convention. When this file (or a copy named `PROJECT_STRUCTURE.md` in a repo) is present, **file placement follows it** and overrides the module-based layout in `../../references/architecture/modularization.md`. The layer *rules* (dependency direction, repository boundary, error handling, cancellation) still come from the references — this file decides *where* things go, not *how* they behave.

**Shape:** package-by-layer inside one Gradle module (or one `commonMain` in KMP). Four roots: `core` → `domain` → `data` → `presentation`. Features are packages, not modules.

**Verified:** the MVI base types, navigation keys, and a scaffolded screen set compile for **JVM and iOS simulator** against `../../examples/user-profile/` (Kotlin 2.4.20, Compose Multiplatform 1.12.1, Koin 4.2.2) — see § Verification. The rest of this file is placement rules.

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
| `event/<feature>` | sealed domain events (`SubscriptionEvent.Expired`, `ConversationEvent.Ended`) and their bus interface (`SharedFlow`, replay 0) | ViewModel→UI effects (those are `presentation.*Effect`) |

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

## `presentation/` — Compose + MVI

### The MVI contract

Base types in `presentation/state/`:

```kotlin
interface UiState                       // immutable snapshot; data class, all val
interface UiIntent                      // user/system intention; sealed per screen
interface UiEffect                      // one-off, consumed exactly once; sealed per screen
```

Every screen owns exactly five files in `presentation/screen/<name>/`:

| File | Contains |
|---|---|
| `<Name>Screen.kt` | `<Name>Route` (resolves the ViewModel, collects state, collects effects) + `<Name>Screen` (stateless content: `state`, `onIntent`, `modifier`) |
| `<Name>ViewModel.kt` | `class <Name>ViewModel(...) : MviViewModel<<Name>UiState, <Name>Intent, <Name>Effect>` — `handle(intent)` is the only entry point |
| `<Name>UiState.kt` | `data class <Name>UiState(...) : UiState` — one object, Compose-stable |
| `<Name>Intent.kt` | `sealed interface <Name>Intent : UiIntent` |
| `<Name>Effect.kt` | `sealed interface <Name>Effect : UiEffect` |

`presentation/app/` is the same five-file set for app-level state (`App.kt` hosts theme + navigation; `AppViewModel` owns session/entitlement/connectivity state and `AppEffect` carries app-level one-offs such as `NavigateToPaywall`).

**`MviViewModel`** (`presentation/state/MviViewModel.kt` — an addition to the listed tree, provided by the scaffold) holds the pattern once:

- `uiState: StateFlow<S>` exposed; `MutableStateFlow` private; `reduce { copy(...) }` is atomic (`update`).
- `effects: Flow<E>` backed by a **`Channel(BUFFERED)`**: each effect is delivered **at most once**, buffered while no collector is active, and lost only on process death.
- `handle(intent: I)` dispatches; subclasses implement `onIntent`.
- `CancellationException` rethrown in the provided `launch` helper.

### The `Effect` trade-off — MUST be understood

Google's guidance is **"do not send events from the ViewModel to the UI"** (*Strongly recommended*; `../../references/architecture/mvvm-udf.md`). This structure chooses `Effect` anyway, which is a legitimate, widespread MVI convention. The cost, and how this template contains it:

| Risk | Mitigation in `MviViewModel` + `Route` |
|---|---|
| Effect emitted while the UI is stopped is **dropped** (`SharedFlow(replay=0)`) | `Channel(BUFFERED)` buffers until a collector resumes |
| Effect **re-delivered** after rotation (`replay=1`) | `Channel` delivers each element once |
| Effect lost on **process death** | accepted — effects are transient by definition. Anything that must survive (a pending navigation after a purchase, an unread error) **MUST be modelled in `UiState`** instead |
| Collector tied to the wrong lifecycle | `Route` collects effects in `LaunchedEffect` keyed on the ViewModel, inside the composition, so collection stops with the screen |
| Navigation as an effect | allowed for forward navigation; **back-stack-state decisions** (deep-link restore, auth redirect) go in `AppUiState` |

**Rule:** use `Effect` for snackbars, toasts, haptics, focus, one-shot navigation, system share sheets. Use `UiState` for anything the user could miss and need to see again.

### Other presentation packages

| Package | Contains | Rules |
|---|---|---|
| `component/<kind>` | reusable stateless composables (`button`, `dialog`, `language`, `audio`, `animation`, `loading`, `error`, `toolbar`, `bottomsheet`, `common`) — values + lambdas, `modifier` param | ViewModel parameters; feature-specific logic |
| `navigation/` | `AppRoute.kt` (sealed, `@Serializable` keys), `AppNavigation.kt` (Navigation 3 `NavDisplay` + entry decorators, one owner of the back stack), `NavigationEffect.kt` (navigation intents from screens → app) | navigation calls from inside feature screens |
| `state/` | `UiState`, `UiIntent`, `UiEffect`, `MviViewModel` | screen-specific types |
| `theme/` | `Theme.kt`, `Color.kt`, `Typography.kt`, `Shape.kt`, `Dimension.kt` | hardcoded colours/dimensions in screens |

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
| Screen set | `<Name>Screen`, `<Name>ViewModel`, `<Name>UiState`, `<Name>Intent`, `<Name>Effect` | `TranslationScreen` … |
| Route / content | `<Name>Route` / `<Name>Screen` | — |
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
2. `Intent`/`Effect` MVI instead of method calls + state-held messages → see the trade-off table above.
3. A use case per feature package is expected; still **do not** add pass-through use cases.

---

## Scaffolding

`scaffold.sh` (same directory) creates the tree and generates the MVI base types and screen sets:

```bash
# create the package tree + presentation/state base types
templates/structure/scaffold.sh init  --root src/commonMain/kotlin --package com.live.voice.translator.instant.speakandtranslate
# add a screen set (5 files) + domain/data feature packages
templates/structure/scaffold.sh screen Translation --root src/commonMain/kotlin --package com.live.voice.translator.instant.speakandtranslate
templates/structure/scaffold.sh feature translation --root src/commonMain/kotlin --package com.live.voice.translator.instant.speakandtranslate
```

For an Android-only project use `--root app/src/main/java` (or `kotlin`).

## Verification

The generated `presentation/state/*`, `presentation/navigation/*` files and one scaffolded screen set (`Sample`) were generated into a copy of `../../examples/user-profile/` (`scaffold.sh init` + `screen Sample` + `feature translation`) and compiled with `compileKotlinJvm compileKotlinIosSimulatorArm64` on 2026-10-03 (Kotlin 2.4.20, Compose Multiplatform 1.12.1, Koin 4.2.2, lifecycle 2.11.0). Compiled, not tested: the scaffold emits placeholders for intent handling. Everything else in this file is a placement rule, not code.
