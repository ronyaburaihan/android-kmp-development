# Adaptive Layouts: Phone, Tablet Portrait, Tablet Landscape

**Scope:** designing every screen, dialog and sheet for phone, tablet portrait and tablet landscape; window size classes; what changes per target; previews and tests per target.
**Applies to:** Jetpack Compose and Compose Multiplatform (Android, iPadOS, desktop windows).
**Official sources:**
- <https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes>
- <https://developer.android.com/develop/ui/compose/layouts/adaptive/canonical-layouts>
- <https://developer.android.com/develop/ui/compose/tooling/previews>
- <https://kotlinlang.org/docs/multiplatform/compose-adaptive-layouts.html>

**Verified:** 2026-10-16. API names and breakpoint values were also read from the `androidx.window:window-core` 1.5.0, `ui-test` 1.12.0 and `ui-tooling-preview` 1.8.0 sources.
**Rule levels:** see `../README.md`. Window size classes and the canonical layouts are [OFFICIAL]. Designing for three targets by default, and the per-target choices in § What changes per target, are [DEFAULT] — a house policy.

---

## The default: three targets — MUST [DEFAULT]

Every screen, dialog and sheet is designed for three targets, unless the user explicitly names fewer:

| Target | Window size class | Reference size (previews, tests) |
|---|---|---|
| Phone | width **Compact** (< 600 dp) | 411 × 891 dp |
| Tablet portrait | width **Medium** (600–839 dp), height Expanded | 800 × 1280 dp |
| Tablet landscape | width **Expanded** (≥ 840 dp), height Medium | 1280 × 800 dp |

**Phone landscape** (height **Compact**, < 480 dp; width usually Medium) is not a designed target unless asked, but **MUST** stay usable: content scrolls, nothing is clipped, the primary action is reachable. An app that locks phones to portrait is exempt on phones only — on displays with smallest width ≥ 600 dp, Android 16 ignores orientation locks (`platform-requirements.md` § 3).

**Only an explicit user instruction narrows the targets** ("phone only", "tablet landscape only"). A design file that shows only a phone is **not** such an instruction: derive the other two layouts (§ What changes per target) and list them in the plan for confirmation. When the user does narrow the targets, the screen still runs on a tablet: it **MUST NOT** break there — cap the content width and centre it — and the report says so under Observations.

---

## Branch on the window, never on the device — MUST

> "Window size classes are not intended for *isTablet*-type logic." [OFFICIAL]

The window changes while the app runs — rotation, multi-window, fold and unfold — and a physical device does not guarantee a window size class.

- **MUST** read the window size class from the adaptive library: `currentWindowAdaptiveInfo().windowSizeClass` (Android docs; pass `supportLargeAndXLargeWidth = true` to receive Large and Extra-large) or `currentWindowAdaptiveInfoV2().windowSizeClass` (CMP docs, `org.jetbrains.compose.material3.adaptive:adaptive:1.3.0`). **MUST** check which function the version in the catalog exposes. [OFFICIAL]
- **MUST** compare with `isWidthAtLeastBreakpoint(…)` / `isHeightAtLeastBreakpoint(…)` and the `WindowSizeClass` constants: `WIDTH_DP_MEDIUM_LOWER_BOUND` 600, `WIDTH_DP_EXPANDED_LOWER_BOUND` 840, `WIDTH_DP_LARGE_LOWER_BOUND` 1200, `WIDTH_DP_EXTRA_LARGE_LOWER_BOUND` 1600, `HEIGHT_DP_MEDIUM_LOWER_BOUND` 480, `HEIGHT_DP_EXPANDED_LOWER_BOUND` 900. The `windowWidthSizeClass` / `windowHeightSizeClass` properties are deprecated. [OFFICIAL]
- **MUST NOT** branch on an `isTablet` flag, the physical screen size, `LocalConfiguration.current.orientation`, or `screenWidthDp`.
- **MUST** check height as well as width. Phone landscape has the same Medium width as tablet portrait but a Compact height; a width-only check puts a tablet layout on a 411 dp-tall phone.
- Large and Extra-large widths **SHOULD** use the Expanded layout unless a design exists for them. The 1280 dp reference tablet reports Large when `supportLargeAndXLargeWidth = true`.

```kotlin
// presentation/util/LayoutMode.kt — NOT COMPILED; the shape, not a drop-in file
enum class LayoutMode { Compact, Medium, Expanded }

@Composable
fun currentLayoutMode(): LayoutMode {
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return when {
        // phone landscape: wide but short — single column, scrolls
        !sizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) -> LayoutMode.Compact
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> LayoutMode.Expanded
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> LayoutMode.Medium
        else -> LayoutMode.Compact
    }
}
```

The modes are named for window classes, not devices, so a tablet in split screen gets the Compact layout it actually has room for.

---

## One composable, read once, state above the branch — MUST

- **MUST** read the layout mode **once**, in the screen's content composable (`<Name>ScreenContent` in the house structure), and pass it down as a parameter. Components never call `currentWindowAdaptiveInfo()`.
- **MUST** keep one screen composable with per-target branches; target-specific pieces are private composables in the same file or its `component/` package. Three copies of a screen drift apart.
- **MUST** hold state above the layout branch — in the ViewModel, or in `rememberSaveable` declared before the `when (layoutMode)`. Rotating or unfolding switches the branch; state declared inside a branch is lost with it (typed text, scroll position, the selected list item).

```kotlin
@Composable
internal fun FeedScreenContent(uiState: FeedUiState, actions: FeedActions, modifier: Modifier = Modifier) {
    val layoutMode = currentLayoutMode()
    val listState = rememberLazyListState()          // above the branch: survives rotation
    when (layoutMode) {
        LayoutMode.Compact -> ArticleList(uiState, actions, listState, modifier)
        LayoutMode.Medium -> ArticleList(uiState, actions, listState, modifier.widthIn(max = dimens.contentMaxWidth))
        LayoutMode.Expanded -> Row(modifier) {
            ArticleList(uiState, actions, listState, Modifier.weight(0.4f))
            ArticleDetail(uiState.selected, actions, Modifier.weight(0.6f))
        }
    }
}
```

---

## What changes per target — SHOULD [DEFAULT]

Decide each row for every screen; "same as phone" is a valid answer only when the content still reads well at 1280 dp wide.

| Concern | Phone (Compact) | Tablet portrait (Medium) | Tablet landscape (Expanded) |
|---|---|---|---|
| App navigation | bottom bar | navigation rail | navigation rail or drawer |
| List + detail | one pane; tap navigates | one pane, or two if both fit | two panes — `ListDetailPaneScaffold` |
| Primary + secondary content | stacked | stacked or supporting pane | supporting pane — `SupportingPaneScaffold` |
| Collections | one column | grid | grid, more columns |
| Forms, reading text | full width with screen padding | width capped, centred | width capped, centred |
| Dialogs, sheets | bottom sheet or full-screen dialog | dialog | dialog or side sheet |

Canonical layouts [OFFICIAL]: **list-detail** shows both panes on Expanded width and one at a time on Medium and Compact; **feed** is a `LazyVerticalGrid(GridCells.Adaptive(minSize = …))` that behaves like a list on Compact; **supporting pane** gives the primary area about two-thirds and hides the secondary pane on small windows. The scaffolds are `@ExperimentalMaterial3AdaptiveApi`.

Width caps and pane sizes are dimension tokens, resolved per `theming.md`.

---

## Previews — SHOULD

`@PreviewScreenSizes` renders Phone, Phone - Landscape, Unfolded Foldable (673 × 841), Tablet (1280 × 800) and Desktop. It has **no tablet portrait**; add one:

```kotlin
@PreviewScreenSizes
@Preview(name = "Tablet - Portrait", device = "spec:width=800dp,height=1280dp,dpi=240")
@Composable
private fun FeedScreenContentPreview() {
    AppTheme { FeedScreenContent(uiState = previewState, actions = FeedActions()) }
}
```

Previews show layout; they do not replace a test per target.

---

## Tests — MUST, one per target

Drive each target's window size and assert a node that only that layout has:

```kotlin
@Test
fun tabletLandscapeShowsDetailPane() = runComposeUiTest {
    setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(DpSize(1280.dp, 800.dp))) {
            AppTheme { FeedScreenContent(uiState = stateWithSelection, actions = FeedActions()) }
        }
    }
    onNodeWithTag("detail_pane").assertIsDisplayed()
}
```

- `DeviceConfigurationOverride.WindowSize` overrides `LocalWindowInfo` (and `LocalConfiguration` on Android) as well as the content size. It exists in Compose UI test **1.12.0** and is absent in 1.10.4. `ForcedSize` only resizes the content, so a screen that reads the window size class still sees the test host's window. [OFFICIAL — KDoc in the 1.12.0 sources]
- **MUST** confirm the test is red when the branch is broken. If the layout never switches under the override, the adaptive library is reading metrics the override does not reach: verify per target on an emulator or device instead, and record which.
- One test per target asserts the layout; state and interaction tests stay at the phone size (`../quality/testing-strategy.md`).

**Device check — MUST or `NOT RUN`:** run the screen on a phone and on a ≥ 600 dp tablet or resizable emulator in both orientations. **MUST NOT** claim tablet verification from previews alone.

---

## Android / iOS differences

| Concern | Android | CMP iOS / desktop |
|---|---|---|
| Window size class source | `androidx.compose.material3.adaptive` | `org.jetbrains.compose.material3.adaptive:adaptive`; docs use `currentWindowAdaptiveInfoV2()` |
| Orientation lock | ignored at smallest width ≥ 600 dp on Android 16 | iPad multitasking resizes the window; branch on the window, not the device idiom |
| Desktop | — | the window resizes freely; every class is reachable, so all three layouts are exercised |

---

## Review checklist

| Finding | Fix |
|---|---|
| `isTablet`, `screenWidthDp`, or `orientation` deciding a layout | the window size class |
| Width-only check (phone landscape gets the tablet layout) | add the height check |
| A screen with one layout and no width cap | the per-target table above |
| `currentWindowAdaptiveInfo()` inside a component | read once in the content composable, pass down |
| State declared inside a layout branch | hoist above the branch |
| No tablet-portrait preview or test | add both |

---

## Cross-references

- Orientation and resizability locks ignored on large screens: `platform-requirements.md` § 3
- List-detail with Navigation 3 scenes: `navigation.md` § Adaptive layouts
- State hoisting, `rememberSaveable`: `compose-ui.md`
- Width caps, pane sizes, spacing: `theming.md`
- Placement of `LayoutMode.kt` and the content composable (house structure): `../../templates/structure/PROJECT_STRUCTURE.md`
- Test harness and v2 API: `../quality/testing-strategy.md`
