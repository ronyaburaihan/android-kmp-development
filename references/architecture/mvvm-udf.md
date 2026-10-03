# MVVM and Unidirectional Data Flow

**Scope:** UI state modelling, event handling, one-off events, state holders, ViewModel responsibilities.
**Applies to:** the UI layer on Android and CMP.
**Official sources:**
- <https://developer.android.com/topic/architecture>
- <https://developer.android.com/topic/architecture/recommendations>
- <https://developer.android.com/develop/ui/compose/architecture>

**Rule levels:** see `../README.md`.

---

## Framing — what is official and what is not

- **UDF is an official Android principle.** Verbatim: "state flows in only one direction, typically from parent component to child component. The events that modify the data flow in the opposite direction." Named benefits: data consistency, fewer errors, easier debugging. [OFFICIAL]
- **Google's prescription is an AAC `ViewModel` exposing a single `uiState: StateFlow<UiState>` and receiving user actions as method calls.** That is MVVM in all but the name; the official docs say "state holder" and "UDF" rather than "MVVM". [OFFICIAL]
- **MVI is not an official Android pattern.** No `developer.android.com` page prescribes an `Intent`/reducer/`Store` triad. **MUST NOT** present MVI as the Android standard. It is a legitimate team convention. [DEFAULT]

---

## The loop

```
   ┌──────────────── uiState: StateFlow<UiState> ────────────────┐
   │                                                             ▼
ViewModel                                                   Composable
   ▲                                                             │
   └──────────── method call: viewModel.onRefresh() ─────────────┘
```

**MUST:**

- State is exposed as an **immutable** observable. See `../kotlin/coroutines-and-flow.md`.
- Events reach the ViewModel as **method calls**, not as objects pushed into a channel by the UI.
- Nothing flows from the ViewModel to the UI except state.

---

## UI state modelling

Two shapes are both used in official samples. **MAY** choose either; **MUST** be consistent within a project.

### Shape A — a single data class with explicit fields

Use when the screen renders partial data while loading, or when several independent async results coexist.

```kotlin
data class FeedUiState(
    val articles: List<Article> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessages: List<ErrorMessage> = emptyList(),
)
```

### Shape B — a sealed interface of mutually exclusive states

Use when the states genuinely exclude each other and the UI renders one branch at a time.

```kotlin
sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Success(val articles: List<Article>) : FeedUiState
    data class Error(val message: String) : FeedUiState
}
```

```kotlin
// WRONG — a sealed hierarchy that then needs every branch to carry the same fields,
// because the UI must keep showing the list while refreshing
sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Success(val articles: List<Article>, val isRefreshing: Boolean) : FeedUiState
    data class Error(val message: String, val articles: List<Article>) : FeedUiState
}
```

Why the wrong form is a problem: the states are not actually exclusive, so each branch accumulates the others' fields and the `when` at the call site duplicates rendering logic. That is the signal to switch to Shape A.

### Rules for either shape — MUST

- **MUST** expose exactly **one** `uiState` property per screen. [OFFICIAL — labelled *Recommended*]
- **MUST** make `UiState` and everything it contains stable for Compose: `val` properties only, read-only collection types. See `../android/compose-ui.md`.
- **MUST NOT** put a framework type (`Throwable`, `Context`, Room entity, Ktor DTO) in `UiState`. Map errors to a displayable form in the ViewModel.
- **SHOULD** make `UiState` directly renderable — the composable does formatting, not business decisions.

```kotlin
// WRONG — multiple independent flows; the UI can render an inconsistent combination
class FeedViewModel : ViewModel() {
    val articles: StateFlow<List<Article>> = ...
    val isLoading: StateFlow<Boolean> = ...
    val error: StateFlow<String?> = ...
}
```

Why the wrong form is a problem: three flows emit independently, so the UI can momentarily show "loading" and a populated list and an error at the same time. With one `UiState` object, every emission is a consistent snapshot.

---

## Producing state

```kotlin
// CORRECT — state derived from a data-layer flow
class FeedViewModel(
    newsRepository: NewsRepository,
) : ViewModel() {

    val uiState: StateFlow<FeedUiState> =
        newsRepository.observeFeed()
            .map { FeedUiState.Success(it) }
            .catch { emit(FeedUiState.Error(it.toDisplayMessage())) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = FeedUiState.Loading,
            )
}
```

```kotlin
// CORRECT — state mutated by user actions
class OrderViewModel(
    private val repository: OrderRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderUiState())
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    fun setQuantity(quantity: Int) {
        _uiState.update { it.copy(quantity = quantity, price = repository.priceFor(quantity)) }
    }

    fun submit() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            _uiState.value = try {
                repository.submit(_uiState.value.toOrder())
                _uiState.value.copy(isSubmitting = false, isComplete = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _uiState.value.copy(isSubmitting = false, error = e.toDisplayMessage())
            }
        }
    }
}
```

**MUST** use `_uiState.update { it.copy(...) }` rather than `_uiState.value = _uiState.value.copy(...)` when the update depends on the current value. `update` is atomic; the read-then-write form loses concurrent updates.

**MUST** use `stateIn(..., WhileSubscribed(5_000), ...)` for state derived from a data-layer flow. [OFFICIAL — the 5-second figure is Google's own] Rationale and alternatives: `../kotlin/coroutines-and-flow.md`.

---

## One-off events — MUST NOT send events to the UI

This is labelled **Strongly recommended** by Google: *"Do not send events from the ViewModel to the UI. Process events immediately in the ViewModel and cause a state update with the result."*

```kotlin
// WRONG — an event channel from ViewModel to UI
class FeedViewModel : ViewModel() {
    private val _events = Channel<FeedEvent>()
    val events = _events.receiveAsFlow()

    fun onBookmark(id: ArticleId) {
        viewModelScope.launch {
            repository.bookmark(id)
            _events.send(FeedEvent.ShowSnackbar("Bookmarked"))
        }
    }
}
```

Why the wrong form is a problem: the event's delivery depends on a collector being active. If the UI is stopped when the event is sent, the event is either dropped (`SharedFlow` with `replay = 0`) or delivered late to a UI that has moved on. Configuration change, process death and back-stack pops all produce duplicated or lost snackbars and double navigation. There is no state to inspect when debugging, because the event has already been consumed.

```kotlin
// CORRECT — the message is state, and the UI acknowledges consumption
data class FeedUiState(
    val articles: List<Article> = emptyList(),
    val messages: List<UserMessage> = emptyList(),
)

data class UserMessage(val id: Long, val text: String)

class FeedViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    fun onBookmark(id: ArticleId) {
        viewModelScope.launch {
            repository.bookmark(id)
            _uiState.update { it + UserMessage(nextId(), "Bookmarked") }
        }
    }

    fun onMessageShown(messageId: Long) {
        _uiState.update { state -> state.copy(messages = state.messages.filterNot { it.id == messageId }) }
    }
}
```

```kotlin
// CORRECT — the UI consumes and acknowledges
@Composable
fun FeedScreen(uiState: FeedUiState, onMessageShown: (Long) -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = uiState.messages.firstOrNull()

    if (message != null) {
        LaunchedEffect(message.id) {
            snackbarHostState.showSnackbar(message.text)
            onMessageShown(message.id)
        }
    }
    // ...
}
```

The same pattern applies to navigation: model "the user should now be on screen X" as state, let the navigation layer react, and acknowledge.

**MUST NOT** use `Channel`, `SharedFlow(replay = 0)`, or a `LiveData<Event<T>>` wrapper for ViewModel-to-UI signals — **unless the codebase's declared structure is MVI with `Effect`** (`../../templates/structure/PROJECT_STRUCTURE.md`). In that case match it: the template's `MviViewModel` uses a `Channel(BUFFERED)` so effects are buffered while the UI is stopped and delivered once, and durable outcomes still go in `UiState`. Record the deviation in the report.

---

## State holders other than ViewModel

**MUST** use a plain state-holder class, not a `ViewModel`, for a reusable UI component whose state can be hoisted. [OFFICIAL — *Strongly recommended*]

```kotlin
// CORRECT — a plain, remembered state holder for a reusable component
@Stable
class SearchFieldState(initialQuery: String = "") {
    var query by mutableStateOf(initialQuery)
        private set
    val isClearVisible: Boolean get() = query.isNotEmpty()

    fun onQueryChange(value: String) { query = value }
    fun clear() { query = "" }
}

@Composable
fun rememberSearchFieldState(initialQuery: String = ""): SearchFieldState =
    remember { SearchFieldState(initialQuery) }
```

```kotlin
// WRONG — a ViewModel for a reusable component
class SearchFieldViewModel : ViewModel() { /* ... */ }
```

Why the wrong form is a problem: a `ViewModel` is scoped to a `ViewModelStoreOwner`, so two instances of the component on one screen share one state object. It also cannot be used in a preview or a screenshot test without a store owner — and on iOS with native SwiftUI there is no store owner at all (`../kmp/compose-multiplatform.md`).

**MUST** use `ViewModel` at screen level and at Navigation destinations. **MAY** use `rememberViewModelStoreOwner()` for a complex, dynamic, reusable composable that genuinely needs its own ViewModel scope. [OFFICIAL]

---

## ViewModel responsibilities — the boundaries

| ViewModel MUST | ViewModel MUST NOT |
|---|---|
| Expose one immutable `uiState` | Expose `MutableStateFlow` |
| Receive actions as method calls | Push events to the UI |
| Create coroutines in `viewModelScope` | Expose `suspend` functions to the UI |
| Depend on repositories or use cases | Touch a DAO, DataStore, HTTP client, Firebase API, location provider, Bluetooth, or connectivity status |
| Hold no Android types | Hold `Context`, `Activity`, `Resources`, or `Application` |
| Map errors to displayable state | Put `Throwable` in `UiState` |

**MUST NOT** use `AndroidViewModel`. See `../deprecations.md`.

String resources are the common reason people reach for `Context` in a ViewModel. **SHOULD** put a resource identifier or a sealed message type in `UiState` and resolve it in the composable:

```kotlin
// CORRECT
data class UserMessage(val id: Long, val text: UiText)

sealed interface UiText {
    data class Raw(val value: String) : UiText
    data class Resource(val id: StringResource) : UiText   // Res.string.* in CMP
}

// WRONG
class FeedViewModel(private val context: Context) : ViewModel() {
    fun onError() { _uiState.update { it.copy(error = context.getString(R.string.generic_error)) } }
}
```

---

## Android / iOS differences

| Concern | Android | CMP / iOS |
|---|---|---|
| `ViewModel` availability | AAC `ViewModel` | available in `commonMain` via `org.jetbrains.androidx.lifecycle` |
| Obtaining one | `viewModel()` with reflection | **`viewModel { ... }` with an explicit initializer** — parameterless fails off-JVM. Prefer `koinViewModel()` |
| Store owner | framework-provided | `ComposeUIViewController` provides it; **absent** with native SwiftUI |
| State collection | `collectAsStateWithLifecycle()` | same API via the JetBrains artifacts |
| `SavedStateHandle` | restores across process death | **undocumented** in CMP — see `../kmp/compose-multiplatform.md` |
| Native SwiftUI over a shared ViewModel | n/a | needs a third-party observability bridge |
| Snackbar/navigation state acknowledgement | same pattern | same pattern — and more important, because iOS and web lifecycles differ (`ON_DESTROY` never fires on web) |

**MUST** keep the ViewModel's public API free of `Flow` if it is exported to Swift on the Objective-C path. See `../kmp/ios-interop.md`.

---

## Testing recommendations

ViewModels are the first thing Google says to test, including Flow emissions. [OFFICIAL]

```kotlin
class FeedViewModelTest {

    private val repository = FakeNewsRepository()

    @Test
    fun emitsLoadingThenSuccess() = runTest {
        val viewModel = FeedViewModel(repository)

        viewModel.uiState.test {                       // Turbine
            assertEquals(FeedUiState.Loading, awaitItem())
            repository.emit(listOf(article))
            assertEquals(FeedUiState.Success(listOf(article)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun messageIsClearedAfterAcknowledgement() = runTest {
        val viewModel = FeedViewModel(repository)
        viewModel.uiState.test {
            awaitItem()                                 // initial
            viewModel.onBookmark(articleId)
            val withMessage = awaitItem()
            val messageId = withMessage.messages.single().id

            viewModel.onMessageShown(messageId)
            assertTrue(awaitItem().messages.isEmpty())
        }
    }
}
```

**MUST:**

- Subscribe to the state when it was created with `stateIn(..., WhileSubscribed(), ...)`. Reading `.value` without a collector sees only the initial value, forever. [OFFICIAL]
- Use fakes for repositories and use cases. [OFFICIAL]
- Inject a `TestDispatcher` into anything the ViewModel constructs. See `../kotlin/coroutines-and-flow.md`.

**SHOULD** assert on `uiState.value` for simple snapshot assertions and use Turbine for emission sequences. [OFFICIAL for the `.value` preference; Turbine is DEFAULT]

**SHOULD** test the acknowledgement half of every one-off-event-as-state pair. A message that is added but never cleared is the characteristic bug of this pattern, and it only shows up in a test that performs the acknowledgement.

**MUST NOT** test a ViewModel through the UI when the assertion is about state. Drive the ViewModel directly; reserve Compose tests for rendering and interaction.

Full strategy: `../quality/testing-strategy.md`.

---

## Cross-references

- Layer rules, the repository boundary, the full recommendation set: `../android/app-architecture.md`
- Compose state APIs, hoisting, stability of `UiState`: `../android/compose-ui.md`
- `StateFlow`, `stateIn`, `WhileSubscribed`, lifecycle-aware collection: `../kotlin/coroutines-and-flow.md`
- Domain layer and use cases: `clean-architecture.md`
- `viewModel { }` initializer requirement and iOS store ownership: `../kmp/compose-multiplatform.md`
- `koinViewModel()`: `../libraries/koin-di.md`
