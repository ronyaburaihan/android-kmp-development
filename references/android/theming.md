# Theming: Colour, Typography, Shape, Dimensions

**Scope:** reading the app's design tokens — Material colour scheme, typography, shapes, and an app dimensions object — and turning a design's values into those tokens.
**Applies to:** Jetpack Compose and Compose Multiplatform. The Material 3 APIs are the same on both; CMP-only concerns are in § Android / iOS differences.
**Official sources:**
- <https://developer.android.com/develop/ui/compose/designsystems/material3>
- <https://developer.android.com/develop/ui/compose/designsystems/custom>

**Verified:** 2026-10-16.
**Rule levels:** see `../README.md`. The theme mechanism (`MaterialTheme`, extending it through a `CompositionLocal`) is [OFFICIAL]. The token-resolution policy in § Dimensions is [DEFAULT] — a house policy, not a Google rule.

---

## Inspect the theme first — MUST

Before writing UI, read the project's theme package: the `MaterialTheme(...)` call, the colour scheme, the `Typography`, the `Shapes`, and the dimensions object if one exists. Record the accessor names and the token groups (`../../workflows/inspect-project.md`).

The project's theme is the source of every colour, text style, corner and spacing value in new UI. Where the project has no dimensions object, dp values are literals; **MUST NOT** introduce a dimensions object, a second theme, or a parallel token set unasked — that is a migration.

---

## Colour — MUST

**MUST** read colours from `MaterialTheme.colorScheme.<role>`. [OFFICIAL] **MUST** pair `on<Role>` with `<role>` — text on `primaryContainer` is `onPrimaryContainer` — for accessible contrast. [OFFICIAL]

**MUST** map a design colour to a role by **meaning**, not by nearest hex: the design's secondary-text grey is `onSurfaceVariant`, its card fill a `surfaceContainer*` role. [DEFAULT]

A colour no role means (success, warning, a brand accent) goes into the theme as an extended colour with a light and a dark value, using the official extended-theme pattern (`@Immutable data class` + `staticCompositionLocalOf`, provided around `MaterialTheme`). [OFFICIAL mechanism]

**MUST NOT** write a colour literal (`Color(0xFF…)`, `Color.Red`) in a screen or component. A literal has no dark-theme value, so it is wrong in one of the two themes. Colour literals live in theme files only; `Color.Transparent` and `Color.Unspecified` are not design colours and are exempt. [DEFAULT]

---

## Typography — MUST

**MUST** set text style with `style = MaterialTheme.typography.<role>`. [OFFICIAL] The scale has 15 roles: `display`, `headline`, `title`, `body`, `label`, each `Large` / `Medium` / `Small`.

**MUST** pick the role by the text's purpose first (screen title → `headline*`, card title → `title*`, paragraph → `body*`, button and metadata → `label*`), then the nearest size within that group. [DEFAULT]

**MUST NOT** set `fontSize`, `fontFamily`, `lineHeight`, or `letterSpacing` in a screen or component. A different size is a different role. [DEFAULT]

**MAY** adjust a role in place for one call site with `.copy(fontWeight = …)`, or pass `color`, `textAlign`, `maxLines` as `Text` parameters.

A style no role fits **and** used in more than one place goes into the theme's type file, derived from a role: an extension property `val Typography.<name>: TextStyle` [OFFICIAL] or a named style function, matching what the project already has.

---

## Shape — SHOULD

**SHOULD** take component corners from `MaterialTheme.shapes.<size>` (`extraSmall` … `extraLarge`). [OFFICIAL] A corner built in code (`RoundedCornerShape(…)`) takes its radius from the dimensions object, resolved like any other dp value below.

---

## Dimensions — the house policy [DEFAULT]

### The dimensions object

One immutable object holds the app's reusable dp values, provided next to `MaterialTheme` and read through one accessor:

```kotlin
// presentation/theme/AppDimensions.kt — NOT COMPILED; the shape, not a drop-in file
@Immutable
data class AppDimensions(
    // Spacing — padding, gaps, Arrangement.spacedBy
    val paddingNone: Dp = 0.dp,
    val paddingMicro: Dp = 2.dp,
    val paddingExtraSmall: Dp = 4.dp,
    val paddingSmall: Dp = 8.dp,
    val paddingMedium: Dp = 12.dp,
    val paddingLarge: Dp = 16.dp,
    val paddingExtraLarge: Dp = 24.dp,
    val padding2ExtraLarge: Dp = 32.dp,
    val padding3ExtraLarge: Dp = 48.dp,
    val padding4ExtraLarge: Dp = 64.dp,

    // Screen edges
    val screenHorizontalPadding: Dp = 16.dp,
    val screenVerticalPadding: Dp = 16.dp,

    // Icons and avatars
    val iconSizeSmall: Dp = 20.dp,
    val iconSizeMedium: Dp = 24.dp,
    val iconSizeLarge: Dp = 32.dp,
    val avatarSizeSmall: Dp = 40.dp,
    val avatarSizeMedium: Dp = 48.dp,

    // Component sizes
    val buttonHeight: Dp = 56.dp,
    val textFieldHeight: Dp = 56.dp,
    val listItemHeight: Dp = 72.dp,

    // Layout
    val contentMaxWidth: Dp = 640.dp,

    // Corners, elevation, borders
    val cornerRadiusSmall: Dp = 8.dp,
    val cornerRadiusMedium: Dp = 12.dp,
    val cornerRadiusLarge: Dp = 16.dp,
    val elevationCard: Dp = 3.dp,
    val elevationDialog: Dp = 12.dp,
    val borderWidth: Dp = 1.dp,
)

val LocalDimensions = staticCompositionLocalOf<AppDimensions> {
    error("AppDimensions not provided — wrap the content in AppTheme")
}

/** Read as `dimens.paddingLarge`. */
val dimens: AppDimensions
    @Composable
    @ReadOnlyComposable
    get() = LocalDimensions.current
```

```kotlin
// presentation/theme/AppTheme.kt
@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val dimensions = remember { AppDimensions() }
    CompositionLocalProvider(LocalDimensions provides dimensions) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkScheme else lightScheme,
            typography = appTypography(),
            shapes = AppShapes,
            content = content,
        )
    }
}
```

The `error(...)` default makes a missing provider fail at once instead of rendering zeros; previews and UI tests therefore wrap content in `AppTheme`. The official sample uses placeholder defaults instead — **MAY** either; match the project.

Rules for the object:

- **MUST** keep tokens in purpose groups (spacing, screen, icon, avatar, component size, layout, corner, elevation, border). A token belongs to one group.
- **MUST** name a new token in its group's existing scheme. **MUST NOT** start a parallel scheme in the same group (`spacingXs` beside `paddingExtraSmall`): two names for one scale split usage and drift apart.
- **MUST NOT** add a token named after one screen's element (an OTP cell, a hero image). That is a single-use value; it stays a literal at its call site.
- **MUST NOT** change an existing token's value to fit one design. Every screen that reads it changes — an unrequested behaviour change.

### Resolving a design value — MUST, in this order

For every dp value a design gives (padding, gap, size, radius, elevation, border):

1. **Exact token in the matching group** → use it.
2. **Near token in the matching group** → use the nearest one. *Near* means within **2 dp**. On a tie, take the larger token. A design's 23.5 dp or 23 dp padding, with 22 dp and 24 dp tokens, is the 24 dp token.
3. **No near token:**
   - used **once** → write the literal at the call site: `Modifier.size(132.dp)`;
   - repeated **within one file only** → one `private val` at the top of that file;
   - used by **two or more** screens or components → add a token to the matching group, named in its scheme, and use it at the new call sites. Literals elsewhere that the token could replace go in the report's Observations — do not edit them unasked.

**MUST** resolve within the matching group only. A 24 dp padding is `dimens.paddingExtraLarge`, never `dimens.iconSizeMedium` — equal value, different meaning, and they diverge when either scale changes.

**MUST NOT** resolve a touch target below 48 dp. Snap up instead (`accessibility.md`).

Why snap rather than copy the design: design tools export values produced by layout (23.5 dp), not intent. The nearest token keeps the spacing rhythm consistent and stops near-duplicate tokens (22, 23, 24) from accumulating. Why a literal for a single use: a token used once adds a name every reader must learn and buys no consistency.

```kotlin
// CORRECT — design: padding 23.5 dp, gap 8 dp, a 132 dp hero image used only here
Column(
    modifier = Modifier.padding(dimens.paddingExtraLarge),            // 23.5 → 24 dp token
    verticalArrangement = Arrangement.spacedBy(dimens.paddingSmall),  // exact token
) {
    Image(painter = hero, contentDescription = null, modifier = Modifier.size(132.dp)) // single use
    Text(text = title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
}

// WRONG
Modifier.padding(23.5.dp)                       // a 24 dp token exists
Modifier.padding(dimens.iconSizeMedium)         // right value, wrong group
Text(title, fontSize = 22.sp, color = Color(0xFF1C1B1F))  // size and colour bypass the theme
val heroImageSize: Dp = 132.dp                  // added to AppDimensions for one screen
```

---

## Android / iOS differences

| Concern | Android | CMP / iOS |
|---|---|---|
| `MaterialTheme`, `colorScheme`, `typography`, `shapes` | `androidx.compose.material3` | same API, JetBrains Material 3 artifact |
| Dynamic colour | Android 12+ only | not available; return `null` from an `expect`/platform function and fall back to the static scheme |
| Custom fonts | `res/font` or `Res.font.*` | `Res.font.*`; `org.jetbrains.compose.resources.Font` is `@Composable`, so the `Typography` is built in a `@Composable` function |
| `isSystemInDarkTheme()` | available | available in `commonMain` |

---

## Review checklist

Run on the changed UI files, excluding the theme package:

```bash
# colour literals and type attributes outside the theme
grep -nE 'Color\(0x|fontSize *=|fontFamily *=|letterSpacing *=|lineHeight *=' <changed files>
# dp literals — each one must be single-use with no token within 2 dp
grep -nE '[0-9.]+\.dp' <changed files>
```

| Finding | Fix |
|---|---|
| Colour literal in a screen | the colour role it means; else an extended colour |
| `fontSize` / `fontFamily` in a screen | the role with that purpose and nearest size |
| dp literal with a token within 2 dp | the token |
| Token added for a single call site | the literal at the call site |
| Token from the wrong group with the right value | the matching group's token |

---

## Cross-references

- State hoisting, stability, `@Immutable`: `compose-ui.md`
- `CompositionLocal` rules for app-wide state (house structure): `../../templates/structure/PROJECT_STRUCTURE.md` § App-wide state and CompositionLocals
- Touch-target size: `accessibility.md`
- Strings: `localization.md`
- CMP resources and fonts: `../kmp/compose-multiplatform.md`
