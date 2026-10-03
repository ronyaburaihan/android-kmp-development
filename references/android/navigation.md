# Navigation

**Scope:** Navigation 3 fundamentals, back-stack ownership, scoping state to destinations, deep links, Navigation 2 in existing projects, KMP/iOS considerations.
**Official sources:**
- <https://developer.android.com/guide/navigation/navigation-3>
- <https://developer.android.com/jetpack/androidx/releases/navigation3>
- <https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html>
- <https://developer.android.com/topic/architecture/recommendations> (single-activity, Navigation 3)

**Rule levels:** see `../README.md`.

---

## Which library — MUST decide from inspection

| Situation | Library | Status |
|---|---|---|
| New Compose app | **Navigation 3** — `androidx.navigation3:navigation3-runtime` / `navigation3-ui` **1.2.0** stable | [OFFICIAL] the version the architecture recommendations name |
| Existing app on Navigation 2 (`androidx.navigation`) | stay on Navigation 2 until a migration is approved | Nav 2 still works; "maintenance mode" is [UNVERIFIED] |
| Existing app on Voyager / Decompose / custom | match it | [DEFAULT] |
| Compose Multiplatform | Navigation 3 on CMP, since CMP 1.10, all targets | [OFFICIAL] |

**MUST NOT** introduce Navigation 3 into a Navigation 2 codebase as a side effect of a feature. **MUST NOT** run two navigation systems indefinitely.

Artifacts verified 2026-10-03: `navigation3-runtime` 1.2.0, `navigation3-ui` 1.2.0. `androidx.lifecycle:lifecycle-viewmodel-navigation3` is at **2.12.0-alpha04** — alpha; its API surface may change.

---

## Navigation 3 — the model

The back stack is **yours**: a `SnapshotStateList<NavKey>` you own, observed by `NavDisplay`. Navigation is `add`; back is `removeLastOrNull`. There is no library-internal stack to query. [OFFICIAL]

```kotlin
// Keys are plain serializable types. In an Android app they are also what survives
// process death, so they MUST be Parcelable/Serializable-compatible with the saver used.
@Serializable sealed interface AppKey : NavKey
@Serializable data object Feed : AppKey
@Serializable data class Article(val id: String) : AppKey
@Serializable data object Settings : AppKey

@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack<AppKey>(Feed)      // saveable; survives config change

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),  // rememberSaveable per entry
            rememberViewModelStoreNavEntryDecorator(),       // ViewModel per entry
        ),
        entryProvider = entryProvider {
            entry<Feed> { FeedRoute(onArticleClick = { backStack.add(Article(it.value)) }) }
            entry<Article> { key -> ArticleRoute(key.id, onBack = { backStack.removeLastOrNull() }) }
            entry<Settings> { SettingsRoute() }
        },
    )
}
```

> **[UNVERIFIED] API names.** `rememberNavBackStack`, `entryProvider { entry<T> { } }`, and the two decorator factories are as documented in the Navigation 3 guide and the CMP Navigation 3 page at verification time. Navigation 3 moved to stable recently; **MUST** confirm names against the artifact version in use before writing them into a project.

### Rules — MUST

- **Add both entry decorators.** Without `rememberViewModelStoreNavEntryDecorator`, a ViewModel is not scoped to its entry: it survives a pop and leaks state into the next visit. Without the saveable decorator, `rememberSaveable` inside a destination does not survive navigation.
- **Keys are data, not screens.** A key carries the minimum needed to render — an id, not a whole model. Whole models in keys bloat saved state and go stale.
- **One owner for the back stack.** `:app` owns it; features expose keys and routes, not navigation calls. See `../architecture/modularization.md`.
- **ViewModels at the destination level**, resolved inside the entry. See `../architecture/mvvm-udf.md`.
- **Model navigation as state when it is an outcome** — "the user is now on screen X after saving" is a state the ViewModel sets and the navigation layer reacts to and acknowledges, not a `Channel` event. See `../architecture/mvvm-udf.md` § One-off events.

### Adaptive layouts

Navigation 3 can display more than one entry at once (list–detail on a large screen) through scene strategies. Large screens ignore orientation locks at API 36, so **MUST** verify every navigation flow at ≥600dp. See `platform-requirements.md`.

---

## Deep links — MUST handle on both platforms

Navigation 3 provides a deep-link matching API in `androidx.navigation3.runtime.deeplink` (`DeepLinkRequest`, `DeepLinkUri`, `DeepLinkMatcher`) with KMP support. [UNVERIFIED — confirm against the release page before use]

The deep-link *parsing* belongs in common code; the *intent handling* is platform code:

```kotlin
// commonMain — pure: URI → key or null
public fun parseDeepLink(uri: String): AppKey? = /* match "…/article/{id}" → Article(id) */

// Android — Activity.onNewIntent / onCreate: parseDeepLink(intent.dataString) → backStack.add(key)
// iOS     — SceneDelegate / SwiftUI onOpenURL: same parse, same add, via the exported facade
```

**MUST** validate every deep-link parameter as untrusted input. **MUST** build a sensible back stack (`Feed` then `Article`), not a bare `Article`, or back exits the app.

---

## Navigation 2 — working in an existing codebase

If the project uses `androidx.navigation` with Compose or Fragments:

- **MUST** match it. Add destinations the way existing ones are added (`NavGraphBuilder` extension per feature is the usual shape).
- **MUST** use type-safe routes (`@Serializable` route objects) if the project already does; **MUST NOT** introduce string routes next to typed ones.
- **MUST** scope ViewModels to the `NavBackStackEntry` (`hiltViewModel()` / `koinViewModel()` inside the composable destination), not to the Activity.
- Nav 2 deep links are declared per destination; Nav 2 on CMP is supported through the JetBrains `navigation-compose` artifact.

Migration to Navigation 3 is a refactor with its own approval — `../../workflows/refactor.md`.

---

## Single activity — MUST

[OFFICIAL — *Strongly recommended*] One Activity hosting the whole app. Multiple Activities multiply back-stack, deep-link, and configuration-change handling, and interact badly with predictive back. See `platform-requirements.md` for the API 36 back-handling changes that assume a single navigation owner.

---

## Android / iOS differences

| Concern | Android | iOS (CMP) | iOS (native SwiftUI) |
|---|---|---|---|
| Library | Navigation 3 | Navigation 3 on CMP | `NavigationStack` |
| Back | system back / predictive back → `onBack` | `onBack` wired to the CMP back dispatcher | interactive pop gesture; no system back button |
| State survival | process death via saveable keys | app relaunch; no process-death equivalent, but the same saveable mechanism | `@SceneStorage` / manual |
| Deep links | `Intent` with `ACTION_VIEW`; app links verification | `onOpenURL` → exported parse | same |
| Web | `NavController.bindToBrowserNavigation()` (CMP) | — | — |
| Shared code | keys and parse logic in `commonMain` | same | keys usable through the facade; the stack is Swift-owned |

With native SwiftUI, the back stack cannot be shared — Swift owns `NavigationStack`. Share the **keys** and the **deep-link parser**; keep the stack per platform.

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| `NavDisplay` without the ViewModel decorator | ViewModels outlive their entries; stale state on revisit |
| Whole domain objects as keys | bloated saved state; stale data |
| Feature modules calling into other features' routes | cross-feature coupling — `../architecture/modularization.md` |
| Navigation events via `Channel` from a ViewModel | dropped/duplicated navigation on config change |
| A deep link that lands on a leaf with no parent in the stack | back exits the app |
| Unvalidated deep-link parameters | injection into queries or UI |
| Two navigation systems in one app | permanent half-migration |
| Multiple Activities as "screens" | predictive back and deep links break |
| Navigation APIs in `commonMain` for a native-SwiftUI app | nothing on iOS consumes them |

---

## Testing recommendations

[OFFICIAL] UI navigation regression tests are on Google's minimum-coverage list.

- **MUST** test the back stack as data: given a stack, an action, assert the resulting list. With Navigation 3 this needs no UI.
- **MUST** test deep-link parsing in `commonTest`: valid, malformed, missing parameter, unexpected host.
- **MUST** test that each deep link produces a stack with a correct parent.
- **SHOULD** test one end-to-end flow per feature with a Compose test (`runComposeUiTest` v2 / `createComposeRule`), asserting on `testTag`s of the destination reached.
- **MUST** verify ViewModel scoping: navigate away and back; assert the destination's state was reset (or restored, if that is the intent).
- **MUST** verify predictive back on an API 36 device for every custom back surface — `platform-requirements.md`.

---

## Cross-references

- ViewModel scoping and state-driven navigation outcomes: `../architecture/mvvm-udf.md`
- Navigation 3 on CMP, entry decorators, web binding: `../kmp/compose-multiplatform.md`
- Feature-module boundaries and who owns the stack: `../architecture/modularization.md`
- Predictive back and large-screen behaviour at API 36: `platform-requirements.md`
- Migration approval: `../../workflows/refactor.md`
- Versions: `../version-matrix.md`
