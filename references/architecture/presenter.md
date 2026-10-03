# Presenter Architecture

**Scope:** The presenter pattern as an alternative to `ViewModel`; when it is appropriate; Circuit as the library form; testing.
**Sources:**
- <https://slackhq.github.io/circuit/> (Circuit — Slack; used in production at Slack)
- <https://github.com/cashapp/molecule> (Molecule — Cash App)
- <https://developer.android.com/topic/architecture/recommendations> (Google's ViewModel recommendation, for contrast)
- Verified reference implementation: `../../examples/user-profile/src/commonMain/kotlin/com/example/userprofile/presentation/UserProfilePresenter.kt`

**Rule levels:** see `../README.md`.

---

## Framing — MUST state accurately

- Google's official recommendation for screen state is the **AAC `ViewModel`** (*Strongly recommended*). [OFFICIAL]
- A **presenter** is a plain Kotlin object with the same contract — one immutable state out, events in — and **no lifecycle framework**. It is an engineering choice, not an official recommendation. [DEFAULT]
- The two are not opposed. The UI contract (`UiState` + events) is identical; what differs is **who owns the scope**.

**MUST NOT** introduce a presenter architecture into a ViewModel codebase, or vice versa, without approval. Consistency outranks either default.

---

## The shape

```kotlin
public class UserProfilePresenter(
    private val scope: CoroutineScope,                // owned by the caller — the whole difference
    observeUserProfile: ObserveUserProfileUseCase,
    private val userRepository: UserRepository,
) {
    public sealed interface Event {
        public data object Refresh : Event
        public data class MessageShown(val id: Long) : Event
    }

    public val state: StateFlow<UserUiState> = /* combine(...).stateIn(scope, WhileSubscribed(5_000), initial) */

    public fun onEvent(event: Event) { /* ... */ }
}
```

Full, compiled, tested source: `../../examples/user-profile/`. The state type `UserUiState` is **shared** with `UserViewModel` — the UI does not know which produced it.

### Rules — MUST

- One immutable `state`; `MutableStateFlow` private. Same rule as `mvvm-udf.md`.
- Events as a sealed type through one `onEvent`, or as methods — match the codebase.
- The presenter **MUST NOT** create its own `CoroutineScope`. The caller supplies and cancels it; that is the contract that makes it work on iOS.
- Rethrow `CancellationException`; fold one-off outcomes into state with acknowledgement — `mvvm-udf.md` § One-off events applies unchanged.
- No Android types. A presenter that imports `android.*` has lost its only advantage.

---

## When a presenter is the right choice

| Situation | Why a presenter |
|---|---|
| **Native SwiftUI over shared code** | iOS has no `ViewModelStoreOwner`. A ViewModel needs a third-party observability bridge; a presenter is an object Swift can create, own in a `@StateObject` wrapper, and cancel in `deinit`. |
| **Circuit-style screen composition** | Each screen part has its own presenter with its own scope; nesting is natural. |
| **Tests without `Dispatchers.setMain`** | `viewModelScope` is bound to `Dispatchers.Main`; a scope parameter is whatever the test passes. |
| **A library module** that must not depend on lifecycle artifacts | the JetBrains ViewModel artifact pulls in AndroidX lifecycle (and needs `google()` in repositories). |

| Situation | Why a ViewModel |
|---|---|
| Android or CMP host | survival across configuration change and process death (`SavedStateHandle`) for free |
| Navigation 3 scoping | `rememberViewModelStoreNavEntryDecorator` scopes it per entry with no code |
| Codebase already on ViewModels | consistency |

The cost of a presenter is that **scope management is now your code**. On Android that means a `rememberCoroutineScope()` or a retained holder per screen — which is what `ViewModel` already is.

---

## Circuit — the library form [DEFAULT]

Circuit (Slack) formalises the pattern as **Presenter + UI**, where the presenter is a `@Composable` function of the Compose *runtime* (not UI) that returns state:

```kotlin
@Serializable data object CounterScreen : Screen
data class CounterState(val count: Int, val eventSink: (CounterEvent) -> Unit) : CircuitUiState
sealed interface CounterEvent : CircuitUiEvent { data object Increment : CounterEvent }

@Composable
fun CounterPresenter(): CounterState {
    var count by rememberSaveable { mutableIntStateOf(0) }
    return CounterState(count) { event ->
        when (event) { CounterEvent.Increment -> count++ }
    }
}
```

Facts verified 2026-10-03: artifacts `com.slack.circuit:circuit-runtime`, `circuit-foundation`, `circuit-test` at **0.39.0** on Maven Central; multiplatform (Android, desktop, iOS demonstrated in Slack's sample); "used in production at Slack and ready for general use". [OFFICIAL to Circuit]

Presenter test API, read from the `circuit-test` **0.39.0 sources jar** (verified 2026-10-03): `suspend fun <UiState : CircuitUiState> Presenter<UiState>.test(...)` (two overloads), `suspend fun <UiState : CircuitUiState> presenterTestOf(...)`, a `CircuitReceiveTurbine<UiState> : ReceiveTurbine<UiState>` with `awaitUnchanged()`, a `TestEventSink` with `assertEvent*` / `assertNoEvents`, and a `FakeNavigator` with `awaitNextScreen()` / `expectNoGoToEvents()`. Parameter lists were not transcribed — **MUST** read the sources for the version in use before writing a test; the names above are confirmed.

**Molecule** (Cash App, `app.cash.molecule:molecule-runtime` 2.2.0) turns a `@Composable` function into a `StateFlow`/`Flow`, which is what makes a composable presenter consumable by non-Compose code and testable with Turbine. Circuit does not require Molecule for the UI path; it is needed to test presenter functions outside a composition. [DEFAULT]

**Adopting Circuit is a structural decision** — it replaces the ViewModel layer and interacts with navigation (Circuit has its own navigator). **MUST** go through `../../workflows/assess-kmp-adoption.md`-style evaluation and approval; **MUST NOT** add it to an existing ViewModel app for one screen.

---

## Plain presenter vs composable presenter

| | Plain class (example) | Composable presenter (Circuit/Molecule) |
|---|---|---|
| Dependencies | coroutines only | Compose runtime + compiler plugin |
| State source | `StateFlow` via `combine`/`stateIn` | `remember`/`rememberSaveable` snapshot state |
| Consumable from Swift | the object, yes; `StateFlow` needs a callback facade (`../kmp/ios-interop.md`) | via Molecule → `StateFlow` → same facade |
| Testing | `runTest` + Turbine, no extra library | Molecule + Turbine (or `circuit-test`) |
| Composition/nesting | manual | first-class |

**SHOULD** start with the plain class. It has no new dependency and is what the compiled example verifies. Move to Circuit when screen composition or its navigator is the actual need.

---

## Android / iOS differences

| Concern | Android / CMP | iOS native SwiftUI |
|---|---|---|
| Who owns the scope | `rememberCoroutineScope()` in the route, or a retained holder | a Swift `ObservableObject` wrapper: creates the scope, cancels in `deinit` |
| Config change / process death | **not handled** by a plain presenter — the caller must retain it, or use a ViewModel | n/a |
| `StateFlow` consumption | `collectAsStateWithLifecycle()` | **not directly** — expose `observe(onEach): Cancellable` on the facade, or use a bridge; `../kmp/ios-interop.md` |
| `Dispatchers.Main` | available | available via `Dispatchers.Main` on Kotlin/Native; the scope the Swift side passes should use it |

**MUST** give the Swift wrapper a cancellation path. A presenter whose scope is never cancelled leaks its upstream flows.

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| Presenter creates its own `CoroutineScope(Dispatchers.Main)` | nobody cancels it; leaks and crashes after the screen is gone |
| `GlobalScope` inside a presenter | same, worse |
| Presenter holds `Context`/`Activity` | all the ViewModel rules broken, none of the benefits |
| Mixing presenters and ViewModels in one feature | two scoping models to reason about |
| Exposing `StateFlow` to Swift directly | opaque object on the Objective-C path |
| Adopting Circuit for one screen | two architectures and two navigators |
| Treating a plain presenter as config-change-safe on Android | state lost on rotation unless retained |

---

## Testing recommendations

```kotlin
@Test
fun refreshFailureSurfacesAsMessage() = runTest {
    val repository = FakeUserRepository().apply { nextRefreshError = UserError.Offline }
    val presenter = UserProfilePresenter(
        scope = backgroundScope,                         // runTest-owned; cancelled at the end
        observeUserProfile = ObserveUserProfileUseCase(repository, FakeSettingsRepository()),
        userRepository = repository,
    )
    presenter.state.test {
        awaitItem()
        presenter.onEvent(UserProfilePresenter.Event.Refresh)
        assertEquals(UiText.Key(MessageKey.USER_LOAD_FAILED), expectMostRecentItem().messages.single().text)
        cancelAndIgnoreRemainingEvents()
    }
}
```

- **MUST** pass `backgroundScope` (or a `TestScope`) — no `Dispatchers.setMain` is needed, which is the point.
- **MUST** collect `state` before asserting — it is `WhileSubscribed`.
- **MUST** test the acknowledgement path for state-held messages.
- **SHOULD** test that cancelling the scope stops the upstream (a fake repository that records collector count).

Full test file: `../../examples/user-profile/src/commonTest/kotlin/com/example/userprofile/presentation/UserProfilePresenterTest.kt`.

---

## Cross-references

- The ViewModel form and the shared `UiState` rules: `mvvm-udf.md`
- `StateFlow` at the iOS boundary: `../kmp/ios-interop.md`
- iOS has no `ViewModelStoreOwner`: `../kmp/compose-multiplatform.md`
- Scope ownership rules: `../kotlin/coroutines-and-flow.md`
- The decision record for the example: `../../examples/DECISIONS.md`
