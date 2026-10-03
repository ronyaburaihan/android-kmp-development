# XML Layouts and View Interoperability

**Scope:** Working in an existing View-based codebase; Compose ↔ Views interop; migration posture.
**Official sources:**
- <https://developer.android.com/develop/ui/compose/migrate/interoperability-apis>
- <https://developer.android.com/develop/ui/compose/migrate/strategy>

**Rule levels:** see `../README.md`. API facts are [OFFICIAL].

---

## Posture — MUST

Google's recommendation for **new** UI is Jetpack Compose. [OFFICIAL — *Strongly recommended*] That is not a mandate to rewrite existing XML.

- **MUST** match the existing screen's technology when modifying it. A Compose island inside a View screen for a one-line change is churn, not progress.
- **MUST NOT** migrate a screen from XML to Compose as part of an unrelated task. Migration is its own approved work — see `../../workflows/refactor.md`.
- **SHOULD** build genuinely new screens in Compose, embedded via `ComposeView` where the host is View-based.
- **MAY** keep XML indefinitely for stable screens. Views are not deprecated.

The architecture underneath is unchanged either way: a View-based screen still has a `ViewModel`, one `uiState`, `repeatOnLifecycle` collection. See `app-architecture.md`.

---

## View screens done correctly

```kotlin
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val viewModel: SettingsViewModel by viewModels()
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = checkNotNull(_binding)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentSettingsBinding.bind(view)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
        binding.darkModeSwitch.setOnCheckedChangeListener { _, on -> viewModel.setDarkMode(on) }
    }

    private fun render(state: SettingsUiState) {
        binding.darkModeSwitch.isChecked = state.darkMode
    }

    override fun onDestroyView() {
        _binding = null              // the view outlives nothing; the fragment outlives the view
        super.onDestroyView()
    }
}
```

**MUST:**

- Use **ViewBinding**, not `findViewById` and not Kotlin synthetics (removed).
- Collect with `viewLifecycleOwner` + `repeatOnLifecycle(STARTED)`. A bare `lifecycleScope.launch { collect }` keeps collecting while stopped — see `../kotlin/coroutines-and-flow.md` § 10.
- Null the binding in `onDestroyView`. The fragment survives its view; a retained binding leaks the view hierarchy.
- Render from one `uiState`; the listener-to-ViewModel direction is the same UDF as Compose.

**MUST NOT** use `LiveData` in new View code where the codebase is on `StateFlow` — see `../deprecations.md`. If the codebase is consistently `LiveData`, match it.

---

## Compose inside Views — `ComposeView`

```xml
<androidx.compose.ui.platform.ComposeView
    android:id="@+id/compose_header"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

```kotlin
binding.composeHeader.apply {
    // The strategy decides when the composition is disposed. In a Fragment this one is
    // mandatory; the default disposes on window detach, which happens on every
    // fragment transaction and recreates the composition each time.
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        AppTheme { ProfileHeader(uiState.header, onEdit = viewModel::onEdit) }
    }
}
```

**MUST** set `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed` for a `ComposeView` inside a Fragment or a `RecyclerView` item. The default (`DisposeOnDetachedFromWindow`) is correct only for a `ComposeView` owned directly by an Activity.

**MUST** wrap content in the app theme; a `ComposeView` does not inherit the XML theme.

---

## Views inside Compose — `AndroidView` / `AndroidViewBinding`

```kotlin
// A legacy custom View, a MapView, a WebView, an ad view
AndroidView(
    factory = { context -> ChartView(context) },
    update = { view -> view.setData(points) },       // runs on recomposition; keep it cheap
    modifier = Modifier.fillMaxWidth().height(200.dp),
)

// An existing XML layout with ViewBinding
AndroidViewBinding(ItemLegacyRowBinding::inflate) {
    title.text = row.title
}
```

**MUST:**

- Create the View in `factory` only; mutate in `update`. `factory` runs once per composition slot; `update` runs on every recomposition where the lambda's captures changed.
- Keep `update` idempotent and cheap. It is the equivalent of `render`.
- Handle the View's own lifecycle for Views that need it (`MapView`, `WebView`): `DisposableEffect` with `onDispose` for `onDestroy`-style cleanup, and `LifecycleEventObserver` for pause/resume forwarding.

**MUST NOT** hold a reference to the View outside the composable. It is recreated when the slot is.

---

## Migration strategy — when approved

[OFFICIAL] Bottom-up or screen-by-screen; both are supported.

1. **Theme first.** Create a Compose theme that mirrors the XML theme so mixed screens look consistent (Material Theme Adapter or a hand-written mapping).
2. **New screens in Compose**, hosted in existing Activities/Fragments via `ComposeView`.
3. **Leaf components** (a row, a card) migrate before their containers.
4. **Navigation last.** Keep the existing Fragment navigation until the screens inside it are Compose; then move to Navigation 3. See `navigation.md`.
5. **Delete XML only when nothing inflates it.** A lint check for unused resources confirms.

**MUST NOT** run two navigation systems indefinitely. That is the state in which most half-migrated apps get stuck.

---

## Android / iOS differences

| Concern | Android | iOS |
|---|---|---|
| Legacy UI toolkit | Views / XML | UIKit / Storyboards |
| Modern toolkit | Compose | SwiftUI; or Compose Multiplatform |
| Embedding modern in legacy | `ComposeView` | `UIHostingController` (SwiftUI) / `ComposeUIViewController` (CMP) |
| Embedding legacy in modern | `AndroidView` | `UIViewRepresentable` (SwiftUI) / `UIKitView` (CMP) — see `../kmp/ios-interop.md` |
| Shared code involvement | none — View code is Android-only by definition | none |

A View-based Android screen and a UIKit iOS screen can share the same `commonMain` ViewModel or presenter; only the rendering differs.

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| `findViewById` / Kotlin synthetics | synthetics are removed; `findViewById` is untyped and unchecked |
| Retaining the ViewBinding past `onDestroyView` | leaks the view hierarchy |
| Collecting without `repeatOnLifecycle` | work continues while stopped |
| `ComposeView` in a Fragment without `DisposeOnViewTreeLifecycleDestroyed` | composition recreated on every transaction; state lost |
| Mutating a View inside `AndroidView.factory` on recomposition | `factory` does not re-run; mutations land in the wrong place |
| Heavy work in `AndroidView.update` | runs on every recomposition |
| Migrating a screen as a side effect of another task | unreviewable, unapproved |
| Two navigation systems long-term | permanent half-migration |

---

## Testing recommendations

- **MUST** test the ViewModel exactly as for a Compose screen — the UI toolkit does not change the ViewModel tests. See `../architecture/mvvm-udf.md`.
- **SHOULD** use Espresso with `FragmentScenario` for View screens; `createComposeRule` for Compose islands; both in the same test when a screen mixes them.
- **MUST** test the `onDestroyView` path for Fragments with bindings — a leak test with LeakCanary in debug builds catches the retained-binding class of bug.
- **SHOULD** screenshot-test migrated leaf components against their XML predecessors before deleting the XML.

---

## Cross-references

- Compose state and performance: `compose-ui.md`
- Lifecycle-aware collection: `../kotlin/coroutines-and-flow.md`
- Navigation during and after migration: `navigation.md`
- Approved migration as refactoring work: `../../workflows/refactor.md`
- iOS embedding equivalents: `../kmp/ios-interop.md`
