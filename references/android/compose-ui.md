# Compose UI: State, Recomposition, Stability

**Scope:** Compose state APIs, state hoisting, composable API design, recomposition performance, stability and strong skipping, the Compose Compiler Gradle plugin.
**Applies to:** Jetpack Compose and Compose Multiplatform — the content here is identical on both, because they share the same compiler and runtime. CMP-only concerns (per-target stability, resources, lifecycle, navigation) are in `../kmp/compose-multiplatform.md`.
**Official sources:**
- <https://developer.android.com/develop/ui/compose/architecture>
- <https://developer.android.com/develop/ui/compose/performance/bestpractices>
- <https://developer.android.com/develop/ui/compose/performance/stability>
- <https://developer.android.com/develop/ui/compose/performance/stability/strongskipping>
- <https://developer.android.com/develop/ui/compose/compiler>

**Rule levels:** see `../README.md`. All rules are [OFFICIAL] unless tagged.

---

## Build setup — MUST

**MUST** apply the Compose Compiler Gradle plugin in **every** module that contains `@Composable` code. Kotlin 2.0+ requires it; there is no alternative mechanism.

```toml
# gradle/libs.versions.toml
[versions]
kotlin = "2.4.20"

[plugins]
kotlin-android   = { id = "org.jetbrains.kotlin.android",        version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

```kotlin
// module build.gradle.kts
plugins {
    alias(libs.plugins.compose.compiler)
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_metrics")
    stabilityConfigurationFile = rootProject.layout.projectDirectory.file("stability_config.conf")
}
```

**MUST** keep the plugin version equal to the Kotlin version. See `../version-matrix.md`.

**MUST NOT** use `composeOptions { kotlinCompilerExtensionArgs += [...] }`. It is the pre-Kotlin-2.0 mechanism. Some official pages still show it; they are stale. See `../deprecations.md`.

`composeCompiler {}` options: `reportsDestination`, `metricsDestination`, `stabilityConfigurationFile`, `includeSourceInformation`, `featureFlags`.

---

## Unidirectional data flow in the UI layer

```
        ┌─────────── state ───────────┐
        ▼                             │
   Composable                    State holder
        │                             ▲
        └─────────── event ───────────┘
```

**MUST:**

- Every input is an event with an event handler. No composable reads mutable global state and no composable writes to state it does not own.
- State flows down as immutable values; events flow up as lambdas.

The data and domain layers are **unaffected** by adopting Compose. UDF here is a UI-layer pattern only.

---

## State APIs — pick by lifetime

| Need | API |
|---|---|
| Transient UI state (expanded/collapsed, text field buffer) | `remember { mutableStateOf(...) }` |
| Same, surviving configuration change / process death | `rememberSaveable { mutableStateOf(...) }` |
| Screen state owned by business logic | `StateFlow` in a `ViewModel`, read with `collectAsStateWithLifecycle()` |
| Derived value from rapidly-changing state | `remember { derivedStateOf { ... } }` |

```kotlin
// CORRECT — local, transient
var query by remember { mutableStateOf("") }
OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search") })

// CORRECT — screen state from the ViewModel
@Composable
fun FeedRoute(viewModel: FeedViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FeedScreen(uiState = uiState, onRefresh = viewModel::refresh)
}
```

**MUST** use `mutableIntStateOf` / `mutableLongStateOf` / `mutableFloatStateOf` / `mutableDoubleStateOf` for primitives rather than `mutableStateOf(0)`, to avoid autoboxing on every write. [DEFAULT — follows from the primitive-specialised APIs existing; not stated as a rule on the cited pages]

---

## State hoisting and composable API design — MUST

**MUST** split a screen into a *route* composable that reads state and a *content* composable that receives it. The content composable takes values and lambdas only.

```kotlin
// CORRECT — content is stateless, previewable and testable
@Composable
fun FeedScreen(
    uiState: FeedUiState,
    onRefresh: () -> Unit,
    onArticleClick: (ArticleId) -> Unit,
    modifier: Modifier = Modifier,
) { /* ... */ }

// WRONG — the content composable owns its data source
@Composable
fun FeedScreen(viewModel: FeedViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    /* ... */
}
```

Why the wrong form is a problem: the composable cannot be previewed, cannot be screenshot-tested, cannot be reused on a second screen, and cannot be instantiated in a UI test without a working DI graph.

**MUST** pass the minimum necessary parameters.

```kotlin
// CORRECT — recomposes only when title or subtitle change
@Composable
fun Header(title: String, subtitle: String)

// WRONG — recomposes when ANY field of News changes
@Composable
fun Header(news: News)
```

**MUST** accept `modifier: Modifier = Modifier` as the first optional parameter of any reusable composable, and apply it to the outermost layout node. [DEFAULT — universal Compose API convention]

**MUST NOT** mutate state from inside a composable body. Mutation happens in event handlers.

---

## Recomposition performance — the six rules

### 1. Cache expensive work with `remember`, or move it out

```kotlin
// WRONG — sorts on every recomposition
@Composable
fun ContactList(contacts: List<Contact>, comparator: Comparator<Contact>) {
    LazyColumn {
        items(contacts.sortedWith(comparator)) { ContactRow(it) }
    }
}

// CORRECT — sorts when inputs change
@Composable
fun ContactList(contacts: List<Contact>, comparator: Comparator<Contact>) {
    val sorted = remember(contacts, comparator) { contacts.sortedWith(comparator) }
    LazyColumn { items(sorted) { ContactRow(it) } }
}

// BEST — the sort belongs in the ViewModel, not the composition
```

A composable body may run on every frame. Anything whose cost is more than a field read **MUST** be either `remember`ed with correct keys or hoisted out of composition entirely.

**MUST** pass every input as a `remember` key. A missing key produces a stale cached value, which is a correctness bug, not a performance one.

### 2. Lazy layouts MUST have stable keys

```kotlin
// WRONG — moving one item recomposes every item
LazyColumn { items(notes) { NoteRow(it) } }

// CORRECT
LazyColumn {
    items(items = notes, key = { it.id }) { NoteRow(it) }
}
```

Without a key, Compose identifies items by index, so an insertion at the top invalidates the whole list and discards all `remember`ed state inside the items.

### 3. `derivedStateOf` to collapse high-frequency state

```kotlin
// WRONG — recomposes on every scroll pixel
val listState = rememberLazyListState()
val showButton = listState.firstVisibleItemIndex > 0

// CORRECT — recomposes only when the boolean flips
val listState = rememberLazyListState()
val showButton by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
```

### 4. Defer state reads as long as possible

```kotlin
// WRONG — reads scroll state high in the tree, recomposing the whole subtree per frame
@Composable
fun SnackDetail() {
    Box(Modifier.fillMaxSize()) {
        val scroll = rememberScrollState(0)
        Title(snack, scroll.value)
    }
}

// CORRECT — passes a lambda; the read happens in the layout phase
@Composable
fun SnackDetail() {
    Box(Modifier.fillMaxSize()) {
        val scroll = rememberScrollState(0)
        Title(snack) { scroll.value }
    }
}

@Composable
private fun Title(snack: Snack, scrollProvider: () -> Int) {
    Column(modifier = Modifier.offset { IntOffset(x = 0, y = scrollProvider()) }) { /* ... */ }
}
```

### 5. Use the lambda overloads of modifiers for frequently changing values

```kotlin
// WRONG — recomposes on every animation frame
val color by animateColorBetween(Color.Cyan, Color.Magenta)
Box(Modifier.fillMaxSize().background(color))

// CORRECT — the read happens in the draw phase; composition is skipped entirely
val color by animateColorBetween(Color.Cyan, Color.Magenta)
Box(Modifier.fillMaxSize().drawBehind { drawRect(color) })
```

Same pattern for layout: `Modifier.offset { IntOffset(...) }` instead of `Modifier.offset(x.dp, y.dp)`.

The general principle: **composition → layout → draw**. Push every read to the latest phase that can satisfy it.

### 6. MUST NOT perform backwards writes

```kotlin
// WRONG — infinite recomposition
@Composable
fun BadComposable() {
    var count by remember { mutableIntStateOf(0) }
    Button(onClick = { count++ }) { Text("Recompose") }
    Text("$count")
    count++            // backwards write: reads then writes the same state in composition
}

// CORRECT — writes happen only in event handlers
@Composable
fun GoodComposable() {
    var count by remember { mutableIntStateOf(0) }
    Button(onClick = { count++ }) { Text("Recompose") }
    Text("$count")
}
```

A write to state that has already been read in the current composition invalidates that composition, which recomposes, which writes again. The frame never settles.

---

## Stability

The Compose compiler classifies every type as **Immutable**, **Stable**, or **Unstable**.

| Classification | Definition |
|---|---|
| Immutable | properties never change; all methods are referentially transparent |
| Stable | properties can change, but Compose is notified through `State` objects |
| Unstable | Compose cannot track changes |

```kotlin
// STABLE — all val, all stable property types
data class Contact(val name: String, val number: String)

// UNSTABLE — var properties
data class Contact(var name: String, var number: String)

// UNSTABLE — List/Set/Map are unstable; the compiler cannot prove immutability
data class Feed(val articles: List<Article>)
```

**MUST** declare data classes used as composable parameters with `val` properties only.

**MAY** annotate:

```kotlin
@Immutable
data class Contact(val name: String, val number: String)

@Stable
interface ContactRepository {
    val contacts: SnapshotStateList<Contact>
}
```

**SHOULD** use `stabilityConfigurationFile` for types you do not own (a third-party model you cannot annotate) rather than wrapping every one of them.

---

## Strong skipping — on by default

**Version-sensitive:** strong skipping is **enabled by default from Kotlin 2.0.20**. [OFFICIAL]

What it does:

1. Makes **all** restartable composables skippable, including those with unstable parameters. Unstable parameters are compared by instance equality (`===`); stable parameters by `equals`.
2. Automatically wraps lambdas inside composables in `remember` keyed on their captures.

```kotlin
// What you write
@Composable
fun MyComposable(unstable: Unstable, stable: Stable) {
    val lambda = { use(unstable); use(stable) }
}

// What the compiler generates
@Composable
fun MyComposable(unstable: Unstable, stable: Stable) {
    val lambda = remember(unstable, stable) { { use(unstable); use(stable) } }
}
```

Opt out per declaration: `@NonSkippableComposable` on the function, `@DontMemoize` on a lambda.

**MUST NOT** add `composeCompiler { enableStrongSkippingMode = true }` on Kotlin 2.0.20+. It is a no-op at best and signals that the author believed it was off.

**Consequence for `kotlinx-collections-immutable`:** with strong skipping on, a composable taking a `List` parameter is still skippable — the list is compared by identity. `ImmutableList` is therefore **optional**, not required. Add it when a *stable* comparison (`equals`) is genuinely needed, not reflexively. APK impact of strong skipping measured on `nowinandroid`: +4 kB.

---

## Diagnosing stability — the workflow

1. Set `reportsDestination` / `metricsDestination` (see build setup above).
2. `./gradlew assembleRelease`
3. Read `<reportsDestination>/<module>-classes.txt` and `-composables.txt`.

Report vocabulary:

```
stable class Contact
unstable class BadContact
restartable skippable scheme("[androidx.compose.ui.UiComposable]") fun ContactRow
restartable fun Header                 // NOT skippable — investigate
```

**SHOULD** investigate any frequently-recomposed composable reported as `restartable` but not `skippable`.

Common causes of unexpected instability:

| Cause | Fix |
|---|---|
| `var` property | make it `val` |
| `List` / `Map` / `Set` parameter where `equals` comparison is needed | `ImmutableList`, or accept identity comparison under strong skipping |
| Type from a module without the Compose compiler applied | apply the plugin to that module, annotate, or add it to the stability configuration file |
| Interface parameter | the compiler cannot see implementations; annotate `@Stable` if the contract guarantees it |

---

## Anti-patterns summary — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| Writing state during composition | infinite recomposition |
| Lazy list without `key` | full-list invalidation on insert/move, loss of item state |
| Reading high-frequency state at the top of the tree | whole-subtree recomposition per frame |
| `Modifier.background(animatedValue)` where `drawBehind` exists | recomposition per frame instead of a redraw |
| Passing a whole domain object where two fields are read | recomposition on unrelated field changes |
| Expensive computation in a composable body with no `remember` | work repeated per frame |
| `remember` with a missing key | stale value — a correctness bug |
| `ViewModel` parameter on a reusable content composable | not previewable, not testable, not reusable |
| Mutating hoisted state from inside a child composable | concurrency hazard and lost SSOT |
| `collectAsState()` for a ViewModel flow on Android | not lifecycle-aware; keeps collecting while stopped |

---

## Android / iOS differences

The APIs in this document are identical on Android and CMP. The differences live elsewhere:

| Concern | Where |
|---|---|
| Per-target stability (web is Beta), resources, lifecycle mapping, `viewModel()` initializer requirement, Navigation 3 on CMP | `../kmp/compose-multiplatform.md` |
| Embedding Compose in SwiftUI/UIKit and the required `Info.plist` key | `../kmp/ios-interop.md` |
| `collectAsStateWithLifecycle` on non-Android targets | `../kmp/compose-multiplatform.md` |

---

## Testing recommendations

**MUST** make content composables stateless so they can be driven directly:

```kotlin
@Test
fun showsEmptyState() = runComposeUiTest {       // CMP v2 API
    setContent { FeedScreen(uiState = FeedUiState.Empty, onRefresh = {}, onArticleClick = {}) }
    onNodeWithTag("empty_state").assertIsDisplayed()
}
```

**MUST** address nodes by `Modifier.testTag(...)`, not by rendered text, in a localised app.

**SHOULD** set `testTagAsResourceId = true` in the app's semantics configuration when Macrobenchmark or UI Automator must find Compose nodes. [OFFICIAL — required for Macrobenchmark; see `../quality/performance.md`]

**SHOULD** verify recomposition counts for list rows and animated surfaces using the Compose compiler report plus a Macrobenchmark `FrameTimingMetric`, not by eye.

Test setup, runners and source sets: `../quality/testing-strategy.md`.

---

## Cross-references

- State ownership, `uiState` modelling, the no-events rule: `../architecture/mvvm-udf.md`
- Layer rules and the repository boundary: `app-architecture.md`
- `StateFlow`, `stateIn`, lifecycle-aware collection: `../kotlin/coroutines-and-flow.md`
- Immutability and collection-type conventions: `../kotlin/coding-conventions.md`
- CMP-specific behaviour: `../kmp/compose-multiplatform.md`
- Frame-timing measurement: `../quality/performance.md`
