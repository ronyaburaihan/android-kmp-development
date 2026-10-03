# Localization and RTL

**Scope:** String resources on Android and Compose Multiplatform, plurals, formatting, RTL layout, per-app language, pseudolocales, iOS differences.
**Official sources:**
- <https://developer.android.com/guide/topics/resources/localization>
- <https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html>
- <https://developer.android.com/guide/topics/resources/app-languages>

**Rule levels:** see `../README.md`. Resource mechanics are [OFFICIAL].

---

## Where strings live — MUST

| Project type | Location | Access |
|---|---|---|
| Android-only | `res/values[-<qualifier>]/strings.xml` | `stringResource(R.string.x)`, `getString(R.string.x)` |
| Compose Multiplatform | `composeResources/values[-<qualifier>]/strings.xml` | `stringResource(Res.string.x)`; `getString(Res.string.x)` (suspend) |

- **MUST** keep every string the app uses in the **default** directory (`values/`). A locale directory overrides; a missing default is a **crash** in an unsupported locale on Android.
- **MUST NOT** hardcode user-visible text. The ViewModel/presenter emits a `UiText.Key` (an enum, not an `R.string` int — the latter does not exist in `commonMain`); the UI resolves it. See `../../examples/user-profile/`.
- Qualifiers are BCP-47 style: `values-de`, `values-fr-rCA`, `values-b+sr+Latn`.
- CMP: no need to escape `@` or `?`; `\n`, `\t`, `\uXXXX` supported.

---

## Plurals — MUST use, never branch on `count == 1`

```xml
<plurals name="new_messages">
    <item quantity="one">%1$d new message</item>
    <item quantity="other">%1$d new messages</item>
</plurals>
```

```kotlin
// Android
resources.getQuantityString(R.plurals.new_messages, count, count)
// CMP
pluralStringResource(Res.plurals.new_messages, count, count)
```

Quantity categories: `zero`, `one`, `two`, `few`, `many`, `other` — which ones apply is **language-dependent** (Arabic uses all six; English two). English-only `one/other` is correct for English and wrong for Russian, Polish, Arabic. Translators add the categories their language needs; code passes the count twice (selector + argument).

---

## Formatting — MUST use positional arguments

```xml
<string name="greeting">Hello %1$s, you have %2$d messages</string>
```

```kotlin
stringResource(Res.string.greeting, name, count)
```

- **MUST** use `%1$s`-style positional placeholders; translators reorder them. Unnumbered `%s %d` breaks when a language changes word order.
- **MUST NOT** concatenate translated fragments (`stringResource(a) + name + stringResource(b)`).
- Android: wrap non-translatable segments in `<xliff:g id="..." example="...">` so translators leave them alone.
- Numbers, dates, currencies **MUST** be formatted with locale-aware formatters, not string templates. In `commonMain`, `kotlinx-datetime` / `kotlin.time` give instants; the *formatting* is platform (`java.text.NumberFormat` / `NSNumberFormatter`) behind an interface. [DEFAULT]

---

## RTL — MUST

- Android manifest **MUST** declare `android:supportsRtl="true"`.
- **MUST** use `start`/`end`, never `left`/`right`: `PaddingValues(start = …)`, `Modifier.padding(start = …)`, `Arrangement.Start`, `TextAlign.Start`, `android:layout_marginStart`.
- Directional icons (back arrows, chevrons) **MUST** mirror: `Icons.AutoMirrored.Filled.ArrowBack`, or `Modifier.scale(scaleX = -1f)` under `LocalLayoutDirection.current == Rtl`.
- Compose reads `LocalLayoutDirection`; override for a subtree with `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)` — useful for previews and tests.
- Numbers, phone numbers, and code **SHOULD** be forced LTR with a Unicode isolate (`⁦…⁩`) or `textDirection = TextDirection.Ltr` where they would otherwise flip.
- Horizontal gestures (swipe-to-dismiss, carousels) **MUST** respect direction.

---

## Per-app language — SHOULD

Android 13+ exposes a per-app language setting. **SHOULD** support it:

- Declare supported locales (`android:localeConfig` / `resourceConfigurations` in Gradle) so the system settings page lists them.
- Set programmatically with `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr-CA"))`; the framework persists it and restarts activities.
- **MUST NOT** hand-roll locale overriding via `Configuration` wrappers on 13+.

iOS: per-app language is a system setting (Settings → App → Language) with no code; the app only needs its localisations declared in the bundle.

---

## Android / iOS differences

| Concern | Android | iOS (CMP) | iOS (native) |
|---|---|---|---|
| String files | `res/values-*/strings.xml` | `composeResources/values-*/strings.xml` (shared) | `.lproj/Localizable.strings` / String Catalogs |
| Plurals | `<plurals>` | `<plurals>` (same file format) | `.stringsdict` / String Catalog |
| RTL | `supportsRtl` + `start/end` | Compose layout direction (same code) | automatic with Auto Layout leading/trailing |
| Per-app language | Android 13 API | — | system setting |
| Pseudolocale | `en-XA`, `ar-XB` (`pseudoLocalesEnabled true`) | — | Xcode scheme → "Double-Length Pseudolanguage", "Right-to-Left Pseudolanguage" |
| Locale source | `Locale.getDefault()` / `Configuration.locales` | `Locale.current` in Compose | `Locale.current` / `NSLocale` |
| Shared code | `UiText.Key` enum in `commonMain`; resolution per platform | `stringResource(Res.string.*)` directly in shared UI | resolution in Swift from the key |

**In a KMP module with native UIs**, the shared code **MUST** emit keys, not strings; each UI resolves against its own string table. With CMP, `composeResources` is the one shared table.

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| Missing default string | crash in an unsupported locale (Android) |
| `if (count == 1) "item" else "items"` | wrong in most languages |
| Unnumbered `%s`/`%d` | breaks on reordering |
| Concatenating translated fragments | ungrammatical in every language but the source |
| `left`/`right` layout values | mirrored layouts broken |
| Non-mirrored directional icons | back arrow points forward in RTL |
| `R.string` ids in `commonMain` | does not compile |
| `String.format` without a locale for numbers | wrong decimal separators; Arabic-Indic digits surprise |
| Strings in images | untranslatable |
| Translator-facing strings with no context comment | mistranslation |

---

## Testing recommendations

- **MUST** run the app in a **pseudolocale** (`en-XA` for length/accents, `ar-XB` for RTL) before release; it exposes truncation, hardcoded strings, and unmirrored layouts without a translator.
- **MUST** test at least one RTL locale on every screen touched in a release; combine with 200 % font scale (`accessibility.md`).
- **MUST** verify the default-resource rule: set an unsupported locale; the app must not crash.
- **SHOULD** unit-test plural selection and formatting through the `UiText` resolver with a fixed locale.
- **SHOULD** use `@Preview(locale = "ar")` / CMP `@Preview(locale = …)` to catch RTL issues at authoring time.
- **SHOULD** fail CI on untranslated strings via a lint check (`MissingTranslation`) where the project supports it.

---

## Cross-references

- `UiText` pattern and why keys cross the boundary: `../architecture/mvvm-udf.md`, `../../examples/DECISIONS.md`
- CMP resources and `Res` class: `../kmp/compose-multiplatform.md`
- Accessibility and font scaling: `accessibility.md`
- `elegantTextHeight` change affecting several scripts at API 36: `platform-requirements.md`
