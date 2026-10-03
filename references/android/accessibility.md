# Accessibility

**Scope:** Compose semantics, TalkBack/VoiceOver behaviour, touch targets, state and live announcements, testing; Compose Multiplatform on iOS.
**Official sources:**
- <https://developer.android.com/develop/ui/compose/accessibility>
- <https://developer.android.com/guide/topics/ui/accessibility>
- <https://kotlinlang.org/docs/multiplatform/compose-accessibility.html>

**Rule levels:** see `../README.md`. Semantics APIs and the 48 dp figure are [OFFICIAL]; process rules are [DEFAULT].

---

## The model

Compose builds a **semantics tree** parallel to the composition. Assistive technology reads it; UI tests address it. The same tree, so every `testTag`-addressable node is already an accessibility node, and every accessibility fix is testable. [OFFICIAL]

Material 3 components carry correct semantics by default. The work is in **icons, images, custom components, composite rows, and dynamic state**.

---

## Rules — MUST

### Content descriptions

```kotlin
// CORRECT — icon-only control describes its purpose
IconButton(onClick = onSave) {
    Icon(Icons.Default.Save, contentDescription = stringResource(Res.string.a11y_save))
}

// CORRECT — decorative image is explicitly silent
Image(painter, contentDescription = null)

// WRONG — TalkBack reads "unlabelled, button"
IconButton(onClick = onSave) { Icon(Icons.Default.Save, contentDescription = "") }

// WRONG — redundant: TalkBack reads "Save. Save button"
Button(onClick = onSave, modifier = Modifier.semantics { contentDescription = "Save" }) { Text("Save") }
```

- Every icon-only control **MUST** have a `contentDescription` naming the **action**, from a string resource.
- Purely decorative images **MUST** pass `contentDescription = null` — not `""`.
- **MUST NOT** describe visible text again; **MUST NOT** include the role ("button") in the description — TalkBack appends it.

### Touch targets

Interactive elements **MUST** be at least **48 × 48 dp**. Material components enforce this via `minimumInteractiveComponentSize`; a custom clickable `Box` does not.

```kotlin
Box(Modifier.size(48.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Icon(...) }
```

### Composite elements

A row that is one logical item **MUST** read as one item, not as five.

```kotlin
// CORRECT — one focusable node; children merged
Row(Modifier.clickable(onClick = onOpen).semantics(mergeDescendants = true) {}) {
    AsyncImage(avatarUrl, contentDescription = null)
    Column { Text(profile.label); Text(profile.email) }
}

// CORRECT — replace children entirely when the merged text would be wrong
Row(Modifier.clearAndSetSemantics { contentDescription = stringResource(Res.string.a11y_profile_row, profile.label) })
```

### State

Dynamic state **MUST** be exposed, not only drawn.

```kotlin
Modifier.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
LinearProgressIndicator(progress = { p }, modifier = Modifier.semantics { stateDescription = "${(p * 100).toInt()}%" })
Text(status, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })   // announce changes
```

- `liveRegion = Polite` for status text that changes (sync state, validation); `Assertive` only for interruptions (errors that block). **MUST NOT** make a frequently-updating counter a live region.
- Loading **MUST** be announced: a progress indicator with a `stateDescription` or a polite live region on the loading text.

### Structure and order

- Section titles **MUST** carry `semantics { heading() }` so screen-reader users can jump between sections.
- Traversal order is top-to-bottom, start-to-end by default. Override with `traversalIndex` only when the visual order misleads (a FAB that should be read last, a toolbar read first).
- `isTraversalGroup = true` on a container that should be read as a unit.

### Custom actions

Gestures with no screen-reader equivalent (swipe to delete, long-press menus) **MUST** also be exposed as `customActions`.

```kotlin
Modifier.semantics {
    customActions = listOf(CustomAccessibilityAction(stringResource(Res.string.a11y_delete)) { onDelete(); true })
}
```

### Text and colour

- **MUST NOT** fix text size in `dp`; use `sp` so font scaling applies. **MUST** verify layouts at 200 % font scale.
- Colour **MUST NOT** be the only carrier of meaning (an error state needs an icon or text, not only red).
- **SHOULD** respect `LocalReduceMotion`/reduced-motion settings for non-essential animation. [UNVERIFIED — API name; verify for the Compose version in use]

---

## Compose Multiplatform on iOS [OFFICIAL]

- CMP maps the semantics tree to **UIAccessibility**; VoiceOver, high-contrast, keyboard and trackpad control are supported.
- The same `contentDescription`, `role`, `stateDescription`, `testTag` properties drive VoiceOver.
- `traversalIndex` is the tool for complex layouts (tables, nested scrolls) where the default order misleads.
- Testing: **XCUITest** can address CMP nodes by `testTag` (exposed as accessibility identifiers).
- On the Objective-C/UIKit side, CMP content is one `UIView` hosting a synthesised accessibility tree — native UIKit accessibility APIs do not apply inside it; semantics do.

**Difference from Android:** iOS has no equivalent of TalkBack's "explore by touch" linear reading of unlabelled groups — an unmerged row reads as scattered fragments under VoiceOver. Merging matters more on iOS.

---

## Android / iOS differences

| Concern | Android (TalkBack) | iOS (VoiceOver) |
|---|---|---|
| Role announcement | appended automatically | appended automatically (traits) |
| Headings | `heading()` → heading navigation | `heading()` → heading rotor |
| Live regions | `liveRegion` | `liveRegion` → `UIAccessibility.post(notification:)` under the hood |
| Custom actions | `customActions` → "Actions" menu | `customActions` → rotor actions |
| Font scaling | system font scale, `sp` | Dynamic Type; `sp` honours it in CMP |
| Testing | Compose test + Accessibility Scanner app | XCUITest + Accessibility Inspector |
| Native interop views | `AndroidView` content uses View accessibility | `UIKitView` content uses UIKit accessibility |

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| `contentDescription = ""` on an icon button | "unlabelled button" |
| Description repeating visible text or the role | double announcements |
| Clickable `Box` under 48 dp | unusable with motor impairments; Accessibility Scanner flags it |
| Unmerged list rows | each row read as 3–5 fragments |
| State only drawn (colour, icon swap) | screen-reader users cannot tell checked/expanded/loading |
| Swipe-only actions | invisible to screen readers |
| Text in `dp` | ignores font scaling |
| Live region on rapidly changing text | continuous announcements |
| Hardcoded descriptions | not localised |

---

## Testing recommendations

- **MUST** assert semantics in Compose tests for primary actions: `onNodeWithContentDescription`, `assertHasClickAction`, `assert(hasStateDescription(...))`. Same tree as `testTag`, so it is cheap.
- **MUST** run **Accessibility Scanner** (Android) on each new screen before release; **SHOULD** run **Accessibility Inspector** on iOS.
- **SHOULD** add a manual TalkBack and VoiceOver pass to the release checklist for screens touched this release — automated checks catch missing labels, not confusing ones.
- **MUST** test at 200 % font scale and in a RTL locale (`localization.md`).
- **SHOULD** fail CI on `Modifier.clickable` nodes under 48 dp where a lint rule exists; `../quality/testing-strategy.md`.

---

## Cross-references

- Compose test addressing by semantics: `../quality/testing-strategy.md`, `../../workflows/test-ui.md`
- Localised descriptions and RTL: `localization.md`
- `UIKitView` / `AndroidView` interop: `xml-views.md`, `../kmp/ios-interop.md`
- Release checklist: `../release/android-release.md`, `../release/ios-release.md`
