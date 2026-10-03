# Kotlin Coding Conventions

**Scope:** Naming, formatting, source organisation, language idioms, public API surface rules, lint tooling.
**Applies to:** all Kotlin code — `commonMain`, platform source sets, Android modules.
**Official source:** <https://kotlinlang.org/docs/coding-conventions.html>
**Rule levels:** see `../README.md`.

Everything in the "Naming", "Source file organisation", "Formatting" and "Language idioms" sections below is [OFFICIAL] unless tagged otherwise.

---

## Naming

**MUST:**

| Element | Convention | Example |
|---|---|---|
| Package | all lowercase, no underscores | `com.example.feature.detail` |
| Class, object, interface, typealias | UpperCamelCase | `DeclarationProcessor` |
| Function, property, local val/var | lowerCamelCase | `fetchLatestNews` |
| `const val`, top-level immutable `val` | SCREAMING_SNAKE_CASE | `MAX_RETRY_COUNT` |
| Backing property | `_` prefix | `_uiState` |
| `@Composable` function returning `Unit` | **UpperCamelCase (class naming)** | `NewsItemRow` |
| Factory function | MAY use class naming | `fun Flow(...)` |
| Enum constant | SCREAMING_SNAKE_CASE **or** UpperCamelCase (pick one per project) | `IN_PROGRESS` |
| Two-letter acronym | both letters uppercase | `IOStream` |
| Three-or-more-letter acronym | capitalise first letter only | `XmlFormatter`, `HttpInputStream` |

**MUST NOT** use meaningless type names: `Manager`, `Wrapper`, `Util`, `Helper`, `Data`, `Info` as the whole name. A file named `Util.kt` is explicitly called out as an anti-pattern.

### Naming conventions for architecture types [OFFICIAL — labelled *Optional* by Google]

These are **SHOULD**, not MUST. Source: <https://developer.android.com/topic/architecture/recommendations>

- Methods: verb phrases — `makePayment()`, `refreshFeed()`.
- Properties: noun phrases — `inProgressTopicSelection`.
- Flow-returning functions: `get{Model}Stream()`; plural for lists — `getAuthorsStream(): Flow<List<Author>>`.
- Interface implementations: meaningful names describing the strategy — `OfflineFirstNewsRepository`, `InMemoryNewsRepository`. Use a `Default` prefix only when no better name exists.
- Test doubles: `Fake` prefix — `FakeAuthorsRepository`.

---

## Source file organisation

**MUST:**

- A file containing a single class or interface is named after it: `NewsRepository.kt`.
- A file with multiple top-level declarations gets a descriptive UpperCamelCase name: `ProcessDeclarations.kt`.
- Platform-specific files with top-level declarations take a platform suffix. The common file has no suffix.

```
commonMain/kotlin/com/example/Platform.kt          // expect declarations
androidMain/kotlin/com/example/Platform.android.kt // actual
iosMain/kotlin/com/example/Platform.ios.kt         // actual
jvmMain/kotlin/com/example/Platform.jvm.kt         // actual
```

This matters more in KMP than in Android-only code: source sets are flattened at compile time, so identical file names across source sets make navigation and stack traces ambiguous.

**MUST** order class members:

1. Property declarations and initializer blocks
2. Secondary constructors
3. Method declarations
4. Companion object
5. Nested classes (place at the end if used externally)

**MUST** keep interface member order identical to the interface declaration when implementing it. **MUST** group overloads together.

**SHOULD** put several related declarations in one file when the file stays under a few hundred lines — one-class-per-file is not a Kotlin convention.

**SHOULD** place extension functions next to the class they extend, or next to the client code that uses them, and restrict visibility (`private` top-level, member, or local) wherever possible.

---

## Formatting

**MUST:** 4 spaces, never tabs. Opening brace at end of line, closing brace on its own line.

**MUST** follow the whitespace rules:

```kotlin
val sum = a + b                 // spaces around binary operators
val range = 0..i                // NO spaces around `..`
i++                             // NO space around unary operators
if (condition) { }              // space after control-flow keyword
class Person(val id: Int)       // NO space before `(` in declaration
foo(1, 2)                       // NO space before `(` in call
foo.bar()?.baz()                // NO spaces around `.` / `?.`
val m: Map<K, V> = mapOf()      // NO spaces inside type parameter brackets
val ref = Foo::class            // NO spaces around `::`
val name: String? = null        // NO space before `?`
list.filter { it > 10 }         // spaces around `{`, `}` and `->`
// comment                      // space after `//`
```

**MUST** follow the colon rule: space **before** `:` when it separates a type from a supertype or follows `object`; no space before `:` when it separates a declaration from its type; always a space **after** `:`.

```kotlin
abstract class Foo<out T : Any> : IFoo {        // space before `:` (supertype)
    abstract fun foo(a: Int): T                 // no space before `:` (declaration)
}
```

**MUST** use this modifier order:

```
public / protected / private / internal
expect / actual
final / open / abstract / sealed / const
external
override
lateinit
tailrec
vararg
suspend
inner
enum / annotation / fun
companion
inline / value
infix
operator
data
```

**SHOULD** use trailing commas in declarations, parameter lists, enums, collection literals, type arguments and `when` entries. They produce cleaner diffs and make reordering safe.

**MUST NOT** horizontally align declarations. **MUST NOT** use unnecessary semicolons.

---

## Language idioms

### Immutability

**MUST** prefer `val` over `var`.
**MUST** declare collection *types* as the read-only interfaces and build them with the read-only factories.

```kotlin
// CORRECT
val items: List<Item> = listOf(a, b)
val index: Map<Id, Item> = mapOf(id to item)

// WRONG — exposes mutation to every caller and defeats Compose stability inference
val items: ArrayList<Item> = arrayListOf(a, b)
val index: HashMap<Id, Item> = hashMapOf()
```

Why the wrong form is a problem: `ArrayList`/`HashMap` as a declared type lets any holder of the reference mutate shared state (breaking the single-source-of-truth rule in `../android/app-architecture.md`), and a `var` property or mutable collection makes a class **unstable** to the Compose compiler (see `../android/compose-ui.md`).

### Default parameters over overloads

```kotlin
// CORRECT
fun format(value: Int, radix: Int = 10): String

// WRONG — more code, and the overloads are not exposed to Swift anyway (see ../kmp/ios-interop.md)
fun format(value: Int): String
fun format(value: Int, radix: Int): String
```

**Version-sensitive warning:** Kotlin default arguments are **not exposed in generated Objective-C framework headers**. For a function that crosses the KMP→Swift boundary, default parameters silently disappear. Design boundary APIs with explicit parameters or overloads. [OFFICIAL]

### Expression bodies and expression-form control flow

```kotlin
// CORRECT
fun double(x: Int) = x * 2
fun label(x: Boolean) = if (x) "yes" else "no"
fun describe(code: Int) = when (code) {
    0 -> "zero"
    in 1..9 -> "digit"
    else -> "other"
}

// WRONG — block body with a single return, and `: Unit` is redundant
fun double(x: Int): Int { return x * 2 }
fun log(message: String): Unit { println(message) }
```

### `if` versus `when`

**SHOULD** use `if` for a binary condition and `when` for three or more options. Using `when` for a two-branch boolean is listed as an anti-pattern.

### Loops

**SHOULD** prefer `filter`/`map`/`fold` over manual loops — **except `forEach`**, where a plain `for` loop is preferred unless the receiver is nullable or the call is part of a longer chain.

```kotlin
// CORRECT
for (item in items) process(item)
val names = items.filter { it.isActive }.map { it.name }

// DISCOURAGED by the official conventions
items.forEach { process(it) }
```

**SHOULD** use open-ended ranges: `for (i in 0..<n)`, not `0..n - 1`.

### Strings

**MUST** use templates over concatenation. **MUST NOT** add braces for a bare variable.

```kotlin
// CORRECT
"Hello, $name — ${items.size} items"

// WRONG
"Hello, " + name                  // concatenation
"Hello, ${name}"                  // redundant braces
```

**SHOULD** use multiline strings with `trimIndent()` / `trimMargin()` instead of `\n` escapes.

### Named arguments

**SHOULD** use named arguments when a call has several parameters of the same primitive type, and for any `Boolean` parameter where the meaning is not obvious from context.

```kotlin
// CORRECT
drawSquare(x = 10, y = 10, width = 100, height = 100, fill = true)

// WRONG — unreadable, and a transposition is a silent bug
drawSquare(10, 10, 100, 100, true)
```

### Property versus function

**SHOULD** expose a property rather than a function when the computation does not throw, is cheap or cached, and returns the same result for unchanged object state. Otherwise expose a function.

### Lambda parameters

**SHOULD** use `it` only for short, non-nested lambdas. **MUST** name parameters explicitly in nested lambdas.

**SHOULD NOT** use multiple labelled returns in one lambda, and **MUST NOT** use a labelled return as the last statement. If a lambda needs several exit points, convert it to an anonymous function.

### Type aliases

**MAY** define a `typealias` for a functional type or a long generic type used repeatedly.

```kotlin
typealias NewsResult = Result<List<Article>>
```

**Version-sensitive warning:** nested type aliases became **Stable in Kotlin 2.3.0**. Do not use them if the project targets an older Kotlin. [OFFICIAL]

---

## Public API surface rules

These apply to any module consumed by another module — which in a KMP project means **every `commonMain` module** and every `:core:*` / `:domain` module.

**MUST:**

- Specify visibility explicitly on every declaration. Do not rely on the `public` default.
- Specify return types and property types explicitly. Do not rely on inference for a public signature.
- Provide KDoc on every public declaration, except an override that adds no new contract.
- When a public function returns a platform type (a Java/Objective-C interop type), declare the Kotlin type explicitly.

**MUST NOT** use `@param` or `@return` KDoc tags. The Kotlin convention is to describe parameters inline in the prose.

```kotlin
// CORRECT
/**
 * Returns the cached articles for [feedId], or an empty list when the feed has
 * never been synchronised. Never throws; a failed read yields an empty list.
 */
internal fun cachedArticles(feedId: FeedId): List<Article> = ...

// WRONG — inferred public signature, tag-style KDoc, implicit visibility
/**
 * @param feedId the feed
 * @return the articles
 */
fun cachedArticles(feedId: FeedId) = ...
```

Why the wrong form is a problem: an inferred return type on a public declaration means an unrelated implementation change can silently alter the module's binary API, which is a source-incompatible change for every consumer and, in KMP, changes the generated Objective-C header.

---

## Kotlin 2.3+ behaviour that changes what "idiomatic" means

**Version-sensitive warning.** [OFFICIAL] <https://kotlinlang.org/docs/whatsnew23.html>

- **Unused return value checker.** From Kotlin 2.3.0, compiled files are treated as if annotated `@MustUseReturnValues`, and unused return values are reported. Builder-style APIs and `Result`-returning functions that were previously fine now produce warnings. Either consume the value or make the function return `Unit`.
- **Data-flow-based exhaustiveness checks for `when`** are Stable. A `when` that was previously accepted may now be reported as non-exhaustive, or an `else` branch may now be flagged as unreachable.
- Core time types (`kotlin.time.Instant`, `kotlin.time.Clock`) are stable in the **standard library**. **SHOULD** prefer them over `kotlinx-datetime` (still pre-1.0 at 0.8.0) for instants and durations; use `kotlinx-datetime` only for calendar/time-zone work. [OFFICIAL that the stdlib types are stable; the preference is DEFAULT]

---

## Lint and format tooling

No official Google or JetBrains mandate exists for a specific third-party linter. The following is [DEFAULT].

**SHOULD** configure:

| Tool | Role |
|---|---|
| **ktlint** | Formatting and the official style guide. Zero-config, has an auto-formatter. If only one tool is adopted, adopt this one. |
| **detekt** | Code smells, complexity, potential bugs. Parses the Kotlin AST so it understands coroutines, flows and sealed interfaces. |
| **Android Lint** | Android-specific correctness. Already present; **MUST** keep `lintRelease` in CI. |
| **Spotless** | Only when the repository is polyglot and needs one formatting gateway. Wraps ktlint. |

**SHOULD** treat formatting violations as a hard CI failure and detekt findings as reviewable. Formatting has no judgement in it; smells do.

---

## Testing recommendations

Conventions are enforced by tooling, not tests.

- **MUST** run `ktlintCheck` (or `spotlessCheck`) and `detekt` in CI on every pull request.
- **SHOULD** run `./gradlew ktlintFormat` as a pre-commit hook rather than arguing in review.
- **SHOULD** fail CI on new Android Lint errors in release variants; warnings MAY be baselined.
- For KMP, **MUST** ensure the lint tasks cover every source set, not just `commonMain` — platform source sets are a common blind spot.

---

## Android / iOS differences

| Concern | Android | iOS / KMP native |
|---|---|---|
| File naming | no suffix needed | `.ios.kt` / `.android.kt` suffix required for files with top-level declarations in platform source sets |
| Default arguments | fully supported | **not exposed** in generated Objective-C headers |
| Public API inference | a source-compatibility concern | also changes the generated Objective-C header, breaking the Swift build |
| Acronym and name collisions | harmless | same-named classes in different packages in one exported framework are **renamed unpredictably** — see `../kmp/ios-interop.md` |

---

## Cross-references

- Compose stability consequences of `var` and mutable collections: `../android/compose-ui.md`
- `expect`/`actual` file layout and the interface-over-expect-class rule: `../kmp/project-structure.md`
- Objective-C export constraints on API design: `../kmp/ios-interop.md`
- Architecture naming recommendations in context: `../android/app-architecture.md`
