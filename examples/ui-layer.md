# UI Layer Fragments

Compose code around and beyond the compiled screen.

> ## Verification status: PARTIALLY COMPILED — read this first
>
> **The screen itself is now compiled and UI-tested** on JVM and iOS:
> `user-profile/src/commonMain/kotlin/com/example/userprofile/ui/UserProfileScreen.kt`
> (route/content split, every state, snackbar acknowledgement, semantics) with
> `UiTextResolver.kt` (message key → `composeResources` string) and
> `user-profile/src/commonTest/.../ui/UserProfileScreenTest.kt` (6 tests, v2 API).
> Prefer those files to the fragments below wherever they overlap.
>
> **Still NOT COMPILED** in this file: the lazy-list and deferred-read fragments, the Android
> `R.string` resolver, and the Android DI wiring. They were not built because the example has
> no Activity, no Android test source set, and no list screen. Treat them as structurally
> correct patterns, not copy-ready code, and compile them in your own project.

---

## Route / content split — COMPILED (see `ui/UserProfileScreen.kt`)

The pattern that makes a screen testable and previewable.

```kotlin
// Route: reads state, resolves the ViewModel. Not previewable, not unit-testable — and
// that is fine, because it contains no logic.
@Composable
fun UserProfileRoute(
    viewModel: UserViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    UserProfileScreen(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onMessageShown = viewModel::onMessageShown,
    )
}

// Content: values and lambdas only. Previewable, screenshot-testable, drivable from a
// Compose test with no DI graph and no ViewModelStoreOwner.
@Composable
fun UserProfileScreen(
    uiState: UserUiState,
    onRefresh: () -> Unit,
    onMessageShown: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // ...
}
```

```kotlin
// WRONG — the content composable owns its data source
@Composable
fun UserProfileScreen(viewModel: UserViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // ...
}
```

Why the wrong form is a problem: it cannot be previewed, cannot be screenshot-tested, cannot be reused on a second screen, and cannot be instantiated in a UI test without a working DI graph. On iOS with native SwiftUI there is no `ViewModelStoreOwner` at all, so there is nothing to resolve it from.

**`collectAsStateWithLifecycle()`, not `collectAsState()`.** The latter is not lifecycle-aware and keeps collecting while the UI is stopped — which, with `WhileSubscribed`, also prevents the upstream from ever stopping. [OFFICIAL — *Strongly recommended*]

---

## Rendering every state — COMPILED (see `ui/UserProfileScreen.kt`)

```kotlin
@Composable
fun UserProfileScreen(
    uiState: UserUiState,
    onRefresh: () -> Unit,
    onMessageShown: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .fillMaxSize(),
        ) {
            when {
                uiState.isInitialLoad ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center).testTag("loading"))

                uiState.profile == null ->
                    EmptyProfile(onRefresh = onRefresh, modifier = Modifier.testTag("empty"))

                else ->
                    ProfileContent(
                        profile = uiState.profile,
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.testTag("content"),
                    )
            }
        }
    }

    // Consume and acknowledge. The acknowledgement is the half that is usually forgotten,
    // and it is why the message would otherwise re-show on every recomposition.
    val message = uiState.messages.firstOrNull()
    if (message != null) {
        val text = message.text.resolve()
        LaunchedEffect(message.id) {
            snackbarHostState.showSnackbar(text)
            onMessageShown(message.id)
        }
    }
}
```

**`LaunchedEffect(message.id)`** — keyed on the id, not on the message object or `Unit`. Keyed on `Unit` the effect never re-runs for a second message; keyed on the object it re-runs whenever the object is recreated.

**Insets consumed explicitly.** Edge-to-edge is mandatory at API 36 and the opt-out attribute is deprecated and disabled. See `../references/android/platform-requirements.md`.

**`testTag` on every node a test will address** — not rendered text, which breaks in every locale but one.

---

## Resolving `UiText` — the CMP half is COMPILED (`ui/UiTextResolver.kt`); this Android-`R.string` half is NOT

`UiText` crosses from shared code; the platform resolves it. This is the Android-resources half, for an Android-only module that does not use `composeResources`.

```kotlin
// androidMain
@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Key -> stringResource(id.toStringRes())
}

private fun MessageKey.toStringRes(): Int = when (this) {
    MessageKey.USER_LOAD_FAILED -> R.string.user_load_failed
    MessageKey.USER_NOT_FOUND -> R.string.user_not_found
    MessageKey.SESSION_EXPIRED -> R.string.session_expired
    MessageKey.OFFLINE -> R.string.offline
    MessageKey.PROFILE_REFRESHED -> R.string.profile_refreshed
}
```

The `when` is exhaustive over a closed enum, so adding a `MessageKey` is a **compile error** here rather than a missing string at runtime. That is the payoff for making `MessageKey` an enum instead of passing `Int` resource ids through shared code — an `R.string` reference cannot exist in `commonMain`.

For Compose Multiplatform, the same function uses `Res.string.*` from `composeResources` instead of `R.string`. See `../references/kmp/compose-multiplatform.md`.

---

## Lists: the two rules that matter most — NOT COMPILED

```kotlin
// CORRECT
LazyColumn {
    items(
        items = uiState.activity,
        key = { it.id },                      // stable key
    ) { entry ->
        ActivityRow(entry)
    }
}
```

```kotlin
// WRONG — no key: an insertion at the top invalidates the whole list and discards
// every remembered state inside the items
LazyColumn {
    items(uiState.activity) { ActivityRow(it) }
}
```

```kotlin
// WRONG — sorts on every recomposition, which may be every frame
LazyColumn {
    items(uiState.activity.sortedByDescending { it.timestamp }) { ActivityRow(it) }
}

// CORRECT — cache with all inputs as keys...
val sorted = remember(uiState.activity) { uiState.activity.sortedByDescending { it.timestamp } }

// ...but better: sort in the ViewModel or the use case. Composition is not the place
// for data shaping.
```

A missing `remember` key is worse than a missing `remember`: it produces a stale value, which is a correctness bug rather than a performance one.

---

## Deferring reads — NOT COMPILED

```kotlin
// WRONG — recomposes on every animation frame
val color by animateColorAsState(targetColor)
Box(Modifier.fillMaxSize().background(color))

// CORRECT — the read happens in the draw phase; composition is skipped
val color by animateColorAsState(targetColor)
Box(Modifier.fillMaxSize().drawBehind { drawRect(color) })
```

```kotlin
// WRONG — reads scroll state high in the tree
val scroll = rememberScrollState()
Header(profile, scroll.value)

// CORRECT — pass a lambda; the read lands in the layout phase
val scroll = rememberScrollState()
Header(profile) { scroll.value }

@Composable
private fun Header(profile: UserProfile, scrollProvider: () -> Int) {
    Column(Modifier.offset { IntOffset(x = 0, y = scrollProvider()) }) { /* ... */ }
}
```

General principle: **composition → layout → draw**. Push every read to the latest phase that can satisfy it. See `../references/android/compose-ui.md`.

---

## Compose test for the content composable — COMPILED (see `ui/UserProfileScreenTest.kt`)

Possible only because the content composable takes values and lambdas. The compiled test adds loading, refreshing, and acknowledgement cases to the two below.

```kotlin
// commonTest — runs on Android, iOS, desktop and web
class UserProfileScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsEmptyStateWhenNoProfile() = runComposeUiTest {   // androidx.compose.ui.test.v2
        setContent {
            UserProfileScreen(
                uiState = UserUiState(),
                onRefresh = {},
                onMessageShown = {},
            )
        }
        onNodeWithTag("empty").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun refreshInvokesCallback() = runComposeUiTest {
        var refreshed = false
        setContent {
            UserProfileScreen(
                uiState = UserUiState(profile = sampleProfile),
                onRefresh = { refreshed = true },
                onMessageShown = {},
            )
        }
        onNodeWithTag("refresh").performClick()
        assertTrue(refreshed)
    }
}
```

**Import from `androidx.compose.ui.test.v2`.** The non-`v2` entry points are deprecated as of CMP 1.11. **No JUnit `TestRule` in `commonTest`** — the JUnit-based Compose API is desktop-only.

---

## Android DI wiring — NOT COMPILED (the Android target here has no Application)

The Android-specific edge. Shared modules are in `commonMain`; only the Android bindings are here.

```kotlin
// androidMain
actual fun platformModule(): Module = module {
    single<UserLocalDataSource> { RoomUserLocalDataSource(get<AppDatabase>().userDao()) }
    single { getDatabaseBuilder(androidContext()) }
    single<CoroutineDispatcher>(named("io")) { Dispatchers.IO }
}

// commonMain
val presentationModule = module {
    viewModelOf(::UserViewModel)              // not single — see below
    factoryOf(::ObserveUserProfileUseCase)    // stateless
}

val dataModule = module {
    single<UserRepository> {
        DefaultUserRepository(get(), get(), get(named("io")))
    }
}
```

```kotlin
// WRONG — one ViewModel shared across every screen instance, never cleared
single { UserViewModel(get(), get()) }
```

**`single` for the repository** (holds a cache), **`factory` for the use case** (stateless), **`viewModelOf` for the ViewModel**. `single` on a stateless object is retained memory with no benefit, and it hides accidental state the day someone adds a `var`.

**`androidContext()` is only reachable from `androidMain`.** A `Context` must never reach `commonMain` — wrap the capability behind an interface declared in common code. See `../references/libraries/koin-di.md`.

---

## What is missing from these fragments

Deliberately out of scope, each covered elsewhere:

| Not shown | Where |
|---|---|
| Navigation wiring (Navigation 3 back stack, entry decorators) | `../references/kmp/compose-multiplatform.md` |
| The Ktor `HttpClient` configuration this screen's data source needs | `../references/libraries/ktor-networking.md` |
| The Room entity, DAO and migration | `../references/libraries/room-datastore.md` |
| Accessibility beyond `testTag` and `contentDescription` | not covered by this skill — a known gap |
| Theming and the design system | project-specific by definition |
