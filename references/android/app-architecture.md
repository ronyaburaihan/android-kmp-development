# Android App Architecture

**Scope:** Layer responsibilities, the official recommendation set with its priority labels, the repository rule, dependency management.
**Applies to:** Android app modules and the shared layers they consume.
**Official sources:**
- <https://developer.android.com/topic/architecture>
- <https://developer.android.com/topic/architecture/recommendations>

**Rule levels:** see `../README.md`.

> **KMP readers:** this document is the Android-shaped official guidance. Several items do not carry to KMP unchanged — Hilt is JVM/Android-only, `collectAsStateWithLifecycle` and `viewModel()` have KMP-specific forms, and Navigation 3 needs CMP support. Those substitutions are in `../kmp/compose-multiplatform.md`. Read this document for *what* the layers do and *why*; read the KMP documents for *how* on non-Android targets.

---

## The four principles — MUST [OFFICIAL]

1. **Separation of concerns.** Do not put logic in Activities or Fragments. They are ephemeral and unsuitable for holding application state.
2. **Drive the UI from data models, preferably persistent ones.** Data models are independent of UI elements, so the app survives process death and intermittent connectivity.
3. **Single source of truth (SSOT).** Every data type has exactly one owner, and **only the owner mutates it**.
4. **Unidirectional data flow (UDF).** State flows down; events flow up to the SSOT.

---

## Layers

```
UI layer  ──────► Domain layer (optional) ──────► Data layer
(Compose +         (use cases)                    (repositories +
 state holders)                                   data sources)

   state flows downward ◄────────────────────────────
   events flow upward   ────────────────────────────►
```

### UI layer

Displays data and is the user-interaction surface. Contains **state holders** that live exactly as long as the UI element they serve:

- `ViewModel` for screen-level state.
- Plain state-holder classes for reusable UI components where state can be hoisted.

### Domain layer — optional, **SHOULD** in large apps

Classes are **use cases** or **interactors**, one functionality each. Introduce it to reuse business logic across several ViewModels, or to cut ViewModel complexity. See `../architecture/clean-architecture.md` for design detail.

### Data layer

- **Repositories** expose data to the rest of the app and contain business logic. **MUST** create a repository even for a single data source.
- **Data sources** each work with exactly one source (a file, a network API, a database).
- Repository responsibilities, verbatim from the official guidance: exposing data to the rest of the app, centralising data changes, resolving conflicts between multiple data sources, abstracting data sources from the rest of the app.

---

## The repository rule — MUST [OFFICIAL, *Strongly recommended*]

UI-layer components — composables **and** ViewModels — **MUST NOT** interact directly with:

- a database or DAO
- DataStore or SharedPreferences
- Firebase APIs
- a location provider
- Bluetooth
- connectivity status
- any HTTP client

Everything on that list goes behind a repository.

```kotlin
// CORRECT
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> =
        settingsRepository.observeSettings()
            .map(::toUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)
}

// WRONG
class SettingsViewModel(
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {
    val uiState = dataStore.data
        .map { SettingsUiState(darkMode = it[DARK_MODE] ?: false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)
}
```

Why the wrong form is a problem: the ViewModel now owns the storage key, the default value, the serialisation format and the error policy. Three screens reading the same preference will each define their own default, so the app disagrees with itself. There is also nothing to fake in a test except DataStore itself, which means the test needs a filesystem.

---

## The official recommendation set

Reproduced with Google's own priority labels. [OFFICIAL]

### Architecture — Strongly recommended → **MUST**

- A clearly defined data layer.
- A clearly defined UI layer.
- Expose application data from the data layer **through a repository**.
- Use coroutines and flows to communicate between layers.

### Architecture — Recommended in big apps → **SHOULD**

- A domain layer with use cases, to reuse business logic across ViewModels or to simplify ViewModel complexity.

### UI layer — Strongly recommended → **MUST**

- Follow UDF: the ViewModel exposes UI state via the observer pattern and receives actions through method calls.
- Use AAC `ViewModel` for business logic and for fetching data to expose UI state.
- Collect UI state with `collectAsStateWithLifecycle()`.
- **Do not send events from the ViewModel to the UI.** Process the event in the ViewModel and cause a state update with the result.
- Single-activity application; navigate with Navigation 3 and handle deep links there.
- Use Jetpack Compose for new UI on phones, tablets, foldables and Wear OS.

### ViewModel — Strongly recommended → **MUST**

- Keep ViewModels independent of the Android lifecycle. **MUST NOT** hold `Activity`, `Context`, `Resources`, or any lifecycle-related type.
- Use flows for receiving data and `suspend` functions with `viewModelScope` for actions.
- Use ViewModels at screen level and at Navigation destinations. Use `rememberViewModelStoreOwner()` for complex, dynamic, reusable composables that genuinely need their own scope.
- Use plain state-holder classes in reusable UI components.

### ViewModel — Recommended → **SHOULD**

- **MUST NOT** use `AndroidViewModel`. (Google labels this *Recommended*; it is treated as MUST here because `AndroidViewModel` exists only to hold `Application`, which violates the Strongly-recommended rule above.)
- Expose a single `uiState` property as a `StateFlow`. For data streams use `stateIn` with `SharingStarted.WhileSubscribed(5_000)`.

### Lifecycle — Strongly recommended → **MUST**

Use lifecycle-aware effects in composables rather than overriding Activity lifecycle callbacks:

| Need | API |
|---|---|
| Synchronous work on start/stop | `LifecycleStartEffect` |
| Synchronous work on resume/pause | `LifecycleResumeEffect` |
| Asynchronous work | `repeatOnLifecycle` |
| Collecting a Flow | `collectAsStateWithLifecycle` |

```kotlin
// CORRECT
@Composable
fun LocationChangedEffect(
    locationManager: LocationManager,
    onLocationChanged: (Location) -> Unit,
) {
    val currentOnLocationChanged by rememberUpdatedState(onLocationChanged)

    LifecycleStartEffect(locationManager) {
        val listener = LocationListener { currentOnLocationChanged(it) }
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 1f, listener,
            )
        } catch (e: SecurityException) {
            // missing permission
        }
        onStopOrDispose { locationManager.removeUpdates(listener) }
    }
}
```

`rememberUpdatedState` is required here: without it the effect captures the first `onLocationChanged` lambda and keeps calling the stale one after recomposition.

### Dependency injection — Strongly recommended → **MUST**

- Use dependency injection, **constructor injection where possible**.
- Scope to a dependency container when the type holds mutable data that must be shared, or is expensive to initialise. Do not scope everything.

### Dependency injection — Recommended → **SHOULD**

- Use **Hilt** on Android, or manual DI in simple apps. Hilt for complex projects with multiple screens, ViewModels, WorkManager, or ViewModels scoped to the navigation back stack.

> **KMP substitution:** Hilt does not work in `commonMain`. See `../libraries/koin-di.md`, which also states plainly that Koin is not Google's recommendation.

### Models — Recommended → **SHOULD**

Create a model per layer in complex apps: a remote-data-source model (DTO), a DAO/entity model, and a `UiState` class, with explicit mapping between them.

---

## Anti-patterns — MUST NOT [OFFICIAL]

| Anti-pattern | Why it is a problem |
|---|---|
| Activities, services or broadcast receivers as data sources | They are destroyed by the OS at unpredictable times; the data goes with them. |
| Android SDK types (`Context`, `Toast`, `Resources`) outside app components | Makes the type untestable off-device and unusable from `commonMain`. |
| Network/loading logic spread across multiple classes or packages | No single place to change the error policy, retry policy or auth refresh. |
| Exposing internal implementation details "to save time" | Google's phrasing: "You might gain a bit of time in the short term, but you are then likely to incur technical debt many times over." |
| Exposing `MutableStateFlow` / `MutableLiveData` from a ViewModel | Destroys the SSOT guarantee. |
| A type that is not main-safe | Forces every caller to know its threading, which the caller will get wrong. |
| `AndroidViewModel` | Exists only to hold `Application`, which is a lifecycle type. |
| One-off events sent from the ViewModel to the UI | See `../architecture/mvvm-udf.md`. |

---

## Supporting best practices [OFFICIAL]

- **MUST** make each part testable in isolation.
- **MUST** make types responsible for their own concurrency policy — "main-safe" means safe to call from the main thread without blocking it.
- **SHOULD** persist offline data so the app functions without a network.
- **SHOULD** preserve UI state across configuration changes.
- **SHOULD** use canonical layouts for multiple form factors, and derive adaptive state from `currentWindowAdaptiveInfo()`.
- **SHOULD** let Jetpack and other recommended libraries handle boilerplate rather than reimplementing it.

---

## Android / iOS differences

| Concern | Android | KMP / iOS |
|---|---|---|
| Layer model | identical | identical — the layering is the portable part |
| `ViewModel` | AAC `ViewModel`, framework-scoped, `SavedStateHandle` restores across process death | available in `commonMain` via `org.jetbrains.androidx.lifecycle`, but **no built-in `ViewModelStoreOwner` on iOS** and `viewModel()` requires an explicit initializer. `SavedStateHandle` support in common code is undocumented. See `../kmp/compose-multiplatform.md` |
| State collection | `collectAsStateWithLifecycle()` | same API via the JetBrains lifecycle artifacts; iOS lifecycle maps from view-controller callbacks |
| DI | Hilt (official recommendation) | Koin / Metro / kotlin-inject — `../libraries/koin-di.md` |
| Navigation | Navigation 3 | Navigation 3 on CMP since CMP 1.10, with explicit entry decorators for ViewModel scoping |
| Connectivity, location, Bluetooth | Android APIs behind a repository | **MUST** be behind an interface declared in `commonMain` with platform implementations |

---

## Testing recommendations [OFFICIAL]

**MUST** test, at minimum:

1. **ViewModels**, including Flow emissions.
2. **Data-layer entities** — repositories and data sources.
3. **UI navigation**, as a regression suite.

**MUST** prefer fakes to mocks. A fake is a real implementation of the interface with simplified behaviour; it exercises the contract, survives refactors, and does not encode call order.

```kotlin
// CORRECT — a fake
class FakeNewsRepository(
    private val articles: MutableStateFlow<List<Article>> = MutableStateFlow(emptyList()),
) : NewsRepository {
    override fun observeFeed(): Flow<List<Article>> = articles
    override suspend fun refresh() { /* no-op */ }
    fun emit(value: List<Article>) { articles.value = value }
}
```

```kotlin
// WRONG — a mock that encodes implementation details
val repository = mock<NewsRepository>()
whenever(repository.observeFeed()).thenReturn(flowOf(listOf(article)))
verify(repository, times(1)).refresh()   // breaks the moment caching is added
```

**MUST** handle `stateIn(WhileSubscribed())` in tests: subscribe to the state, or the upstream never starts and `.value` stays at the initial value forever.

Full test strategy, source sets and runners: `../quality/testing-strategy.md`.

---

## Cross-references

- UI state modelling, events, state holders: `../architecture/mvvm-udf.md`
- Domain layer design and boundary mapping: `../architecture/clean-architecture.md`
- Module taxonomy and granularity: `../architecture/modularization.md`
- Compose state and performance: `compose-ui.md`
- Dispatchers, scopes, `stateIn`: `../kotlin/coroutines-and-flow.md`
- Repository implementation with Ktor/Room: `../libraries/ktor-networking.md`, `../libraries/room-datastore.md`
