# Coroutines and Flow

**Scope:** Dispatcher injection, scope ownership, main-safety, cancellation, exception handling, `StateFlow`/`SharedFlow`, lifecycle-aware collection.
**Applies to:** `commonMain` business logic, Android UI layer, iOS boundary.
**Official sources:**
- <https://developer.android.com/kotlin/coroutines/coroutines-best-practices>
- <https://developer.android.com/kotlin/flow/stateflow-and-sharedflow>

**Rule levels:** see `../README.md`. All numbered rules below are [OFFICIAL].

---

## 1. Inject dispatchers — MUST

**MUST NOT** reference `Dispatchers.X` inside a class body. **MUST** accept a `CoroutineDispatcher` as a constructor parameter.

```kotlin
// CORRECT
class NewsRepository(
    private val remote: NewsRemoteDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun fetch(): List<Article> = withContext(ioDispatcher) {
        remote.load()
    }
}

// WRONG
class NewsRepository(private val remote: NewsRemoteDataSource) {
    suspend fun fetch(): List<Article> = withContext(Dispatchers.IO) {
        remote.load()
    }
}
```

Why the wrong form is a problem: the test cannot substitute a `TestDispatcher`, so the test either races against a real thread pool or needs `Thread.sleep`. The result is a flaky test suite that gets disabled.

**MUST** provide dispatchers through dependency injection as named/qualified bindings, not as default parameter values in production wiring. The default value above is a convenience for construction sites that do not use DI; the DI container **SHOULD** pass them explicitly.

---

## 2. Suspend functions MUST be main-safe

A `suspend fun` **MUST** be safe to call from the main thread. The function itself is responsible for moving blocking work off the main thread with `withContext`.

```kotlin
// CORRECT — caller does not need to know where this runs
suspend fun readConfig(): Config = withContext(ioDispatcher) {
    parse(file.readText())
}

// WRONG — blocks whatever thread the caller is on
suspend fun readConfig(): Config = parse(file.readText())
```

This is the same rule as "types are responsible for their own concurrency policy" in `../android/app-architecture.md`. A caller **MUST NOT** need to wrap a suspend call in `withContext` to make it safe.

**MUST NOT** call `runBlocking` on the main thread.

---

## 3. The ViewModel creates coroutines; it does not expose suspend functions — MUST

```kotlin
// CORRECT
class LatestNewsViewModel(
    private val getLatestNews: GetLatestNewsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LatestNewsUiState>(LatestNewsUiState.Loading)
    val uiState: StateFlow<LatestNewsUiState> = _uiState.asStateFlow()

    fun loadNews() {
        viewModelScope.launch {
            _uiState.value = try {
                LatestNewsUiState.Success(getLatestNews())
            } catch (e: IOException) {
                LatestNewsUiState.Error(e)
            }
        }
    }
}

// WRONG — makes the UI responsible for scoping, and leaks a suspend API to Swift
class LatestNewsViewModel(private val getLatestNews: GetLatestNewsUseCase) : ViewModel() {
    suspend fun loadNews(): List<Article> = getLatestNews()
}
```

Why the wrong form is a problem: the UI must now own a scope and handle cancellation, which puts business concerns in the view. On iOS it is worse — a `suspend` function exported to Swift becomes a completion handler with no real cancellation (see `../kmp/ios-interop.md`).

---

## 4. Never expose mutable types — MUST

```kotlin
// CORRECT
private val _uiState = MutableStateFlow(UiState())
val uiState: StateFlow<UiState> = _uiState.asStateFlow()

// WRONG
val uiState = MutableStateFlow(UiState())
```

Why the wrong form is a problem: any collector can write to the state, so the single-source-of-truth guarantee is gone and a wrong value has no single place to be traced back to.

The same applies to `MutableSharedFlow` (`asSharedFlow()`) and to collections (`toList()` or a read-only declared type).

---

## 5. Data and domain layers expose `suspend` for one-shot, `Flow` for streams — MUST

```kotlin
interface ExampleRepository {
    suspend fun refresh()                       // one-shot
    fun observeExamples(): Flow<List<Example>>  // stream
}
```

**MUST NOT** return a `Deferred`, a `Channel`, a `Job`, or a platform type (`Call`, `Task`, `NSURLSessionTask`) from a repository.

---

## 6. Scope selection — MUST

| Work lifetime | Mechanism |
|---|---|
| Bound to the caller (a screen) | `coroutineScope { }` / `supervisorScope { }` inside a `suspend fun` |
| Bound to a screen's ViewModel | `viewModelScope` |
| MUST outlive the screen (e.g. a bookmark write) | an **injected** `CoroutineScope` |

```kotlin
// CORRECT — parallel work inherits the caller's lifecycle and cancellation
class GetBookAndAuthorsUseCase(
    private val books: BooksRepository,
    private val authors: AuthorsRepository,
) {
    suspend operator fun invoke(): BookAndAuthors = coroutineScope {
        val b = async { books.getAllBooks() }
        val a = async { authors.getAllAuthors() }
        BookAndAuthors(b.await(), a.await())
    }
}
```

```kotlin
// CORRECT — work that must survive navigation away
class ArticlesRepository(
    private val dataSource: ArticlesDataSource,
    private val externalScope: CoroutineScope,
) {
    suspend fun bookmark(article: Article) {
        externalScope.launch { dataSource.bookmark(article) }.join()
    }
}
```

**MUST NOT** use `GlobalScope`.

```kotlin
// WRONG
fun bookmark(article: Article) {
    GlobalScope.launch { dataSource.bookmark(article) }
}
```

Why the wrong form is a problem: `GlobalScope` cannot be cancelled, cannot be replaced in a test, and has no exception handler, so a failure there is an uncaught crash with no owner.

Use `supervisorScope` instead of `coroutineScope` when one child's failure **MUST NOT** cancel its siblings.

---

## 7. Cancellation is cooperative — MUST

**MUST** call `ensureActive()` inside any CPU-bound loop. All `kotlinx.coroutines` suspend functions (`delay`, `withContext`, channel operations) are already cancellable.

```kotlin
// CORRECT
suspend fun indexAll(files: List<File>) = withContext(defaultDispatcher) {
    for (file in files) {
        ensureActive()
        index(file)
    }
}

// WRONG — the loop runs to completion after cancellation
suspend fun indexAll(files: List<File>) = withContext(defaultDispatcher) {
    for (file in files) index(file)
}
```

---

## 8. Exception handling — MUST

**MUST** catch expected exceptions at the launch site. **MUST NOT** swallow `CancellationException`.

```kotlin
// CORRECT
viewModelScope.launch {
    try {
        repository.login(username, token)
        _uiState.value = UiState.LoggedIn
    } catch (e: CancellationException) {
        throw e                     // cancellation is not a failure
    } catch (e: IOException) {
        _uiState.value = UiState.Error(e)
    }
}

// WRONG — swallows cancellation, so the coroutine machinery believes the job completed
viewModelScope.launch {
    try {
        repository.login(username, token)
    } catch (e: Exception) {
        _uiState.value = UiState.Error(e)
    }
}
```

Why the wrong form is a problem: catching `CancellationException` breaks structured concurrency. The parent believes the child finished normally, so `join()`/`await()` returns instead of propagating cancellation, and resources tied to the cancelled scope are never released.

**MUST NOT** catch `Throwable` to "be safe" — that captures `CancellationException` and, on JVM, `Error`.

---

## 9. StateFlow vs SharedFlow — choose by role

| | `StateFlow` | `SharedFlow` |
|---|---|---|
| Role | current state | event broadcast |
| Initial value | **required** | not required |
| Conflation | yes (skips intermediate values) | configurable |
| `.value` | yes | no |
| Use for | screen UI state, any "current value" | multi-consumer signals (a refresh tick) |

**MUST** use `StateFlow` for UI state.

`MutableSharedFlow` configuration: `replay`, `onBufferOverflow` (`SUSPEND` / `DROP_LATEST` / `DROP_OLDEST`), `subscriptionCount`, `resetReplayCache()`.

### Converting cold to hot

```kotlin
// CORRECT — in a ViewModel
val feedState: StateFlow<FeedUiState> =
    newsRepository.observeFeed()
        .map(::toUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FeedUiState.Loading,
        )
```

`SharingStarted` options: `WhileSubscribed()` (most efficient; stops upstream when nothing collects), `Eagerly` (starts immediately, never stops), `Lazily` (starts at the first subscriber, never stops).

**SHOULD** use `WhileSubscribed(5_000)` for ViewModel state derived from a data-layer flow. The 5 second timeout is the value in Google's own recommendation; it keeps the upstream alive across a configuration change without keeping it alive while the app is backgrounded.

```kotlin
// WRONG — the database query and network observer keep running while the app is in the background
.stateIn(viewModelScope, SharingStarted.Eagerly, FeedUiState.Loading)
```

For a data source shared by multiple consumers, use `shareIn` with an injected external scope:

```kotlin
class NewsRemoteDataSource(private val externalScope: CoroutineScope) {
    val latestNews: Flow<List<ArticleHeadline>> = flow { /* ... */ }
        .shareIn(externalScope, replay = 1, started = SharingStarted.WhileSubscribed())
}
```

---

## 10. Lifecycle-aware collection — MUST (Android)

**MUST NOT** collect a UI-bound flow from a bare `lifecycleScope.launch { }` or `launchIn(lifecycleScope)`.

```kotlin
// CORRECT — Compose
@Composable
fun FeedScreen(viewModel: FeedViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
}

// CORRECT — Views
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect(::render)
    }
}

// WRONG — keeps collecting while the UI is stopped
lifecycleScope.launch {
    viewModel.uiState.collect(::render)
}
```

Why the wrong form is a problem: the collector stays active while the Activity is stopped, so the upstream (database query, network poll, location updates) keeps producing and keeps a reference to a view that is no longer visible. With `WhileSubscribed`, it also prevents the upstream from ever stopping.

**`collectAsState()` (without `WithLifecycle`) is not lifecycle-aware.** Use it only for a flow you created inside the composition.

---

## Android / iOS differences

### Desktop/JVM — MUST add the Swing dispatcher

`ViewModel.viewModelScope` and `Lifecycle.coroutineScope` use `Dispatchers.Main.immediate`, which on JVM desktop requires: [OFFICIAL]

```kotlin
jvmMain.dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:<version>")
}
```

Without it, anything touching `viewModelScope` fails at runtime on desktop.

### iOS — the Objective-C export boundary

[OFFICIAL] <https://kotlinlang.org/docs/native-objc-interop.html>

- A Kotlin `suspend fun` becomes an Objective-C completion handler, and a Swift `async` function for Swift 5.5+. The documentation calls this path **"highly experimental" with limitations** (KT-47610).
- The completion handler **MUST** carry `NSError*`/`Error`. Only `CancellationException` is delivered as a Swift `Error` by default; **any other exception reaching Swift terminates the app** unless the function is annotated `@Throws`.
- Swift's `async`/`await` over this bridge gives **no real cancellation** of the Kotlin coroutine.
- **`Flow` is not usefully exported through the Objective-C path.** There is no `AsyncSequence` mapping.

Consequences — **MUST** follow when designing the iOS-facing API:

- **MUST NOT** expose `Flow<T>` directly from a type exported to Swift. Expose a callback-registration function returning a cancellation handle, or use a bridging library.
- **MUST** annotate every exported throwing function with `@Throws(...)` listing the expected types.
- [UNVERIFIED] Community sources state Kotlin `suspend` functions must be invoked from the iOS **main thread** and crash otherwise. This may be a pre-`native-mt` artifact. Verify against the current `kotlinx.coroutines` native documentation before designing around it.

**Swift export (Alpha) changes this**, and is the direction of travel but **MUST NOT** be shipped on: it maps `suspend fun` to Swift `async` natively and `Flow<T>` to Swift `AsyncSequence`, with coroutines defaulting to `Dispatchers.Default`. See `../kmp/ios-interop.md`.

### Dispatchers across platforms

- `Dispatchers.IO` exists on all targets but is backed differently; do not assume a thread-count.
- Ktor native engines (Curl, Darwin, WinHttp) respect the configured engine dispatcher and default to `Dispatchers.IO` as of Ktor 3.4.0. [OFFICIAL]
- **MUST NOT** assume `Dispatchers.Main` exists without a platform dependency (see the Swing note above).

---

## Testing recommendations

**MUST** use `runTest` with an injected `TestDispatcher`. [OFFICIAL]

```kotlin
class ArticlesRepositoryTest {
    @Test
    fun bookmarkWritesThrough() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dataSource = FakeArticlesDataSource()
        val repository = ArticlesRepository(dataSource, ioDispatcher = dispatcher)

        repository.bookmark(article)

        assertTrue(dataSource.isBookmarked(article))
    }
}
```

- `StandardTestDispatcher` — coroutines run in queue order; use when ordering matters. This is also the default in the CMP v2 UI test API.
- `UnconfinedTestDispatcher` — eager execution; use for simple pass-through tests.

**MUST** account for `WhileSubscribed`: a `StateFlow` created with `stateIn(..., WhileSubscribed(), ...)` has no active upstream until something collects, so a test that only reads `.value` sees the initial value forever. Collect it, or assert through the collector.

**SHOULD** assert on `StateFlow.value` where the state is a simple snapshot. [OFFICIAL]
**SHOULD** use **Turbine** (`app.cash.turbine:turbine`, full KMP) for emission sequences and for `SharedFlow`. [DEFAULT]

```kotlin
@Test
fun emitsLoadingThenSuccess() = runTest {
    viewModel.uiState.test {
        assertEquals(UiState.Loading, awaitItem())
        assertIs<UiState.Success>(awaitItem())
        cancelAndIgnoreRemainingEvents()
    }
}
```

**MUST NOT** use `Thread.sleep`, `delay` with real time, or `runBlocking` in tests to wait for a coroutine.

---

## Cross-references

- UI state modelling and the "no events to the UI" rule: `../architecture/mvvm-udf.md`
- Layer responsibilities and the repository rule: `../android/app-architecture.md`
- Compose collection and recomposition: `../android/compose-ui.md`
- Objective-C/Swift export constraints in full: `../kmp/ios-interop.md`
- Test source sets and runners: `../quality/testing-strategy.md`
