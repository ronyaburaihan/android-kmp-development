# Kotlin Language Essentials

**Scope:** Null safety, immutability, collections, extension functions, error handling. The language-level rules that shape every file; style and layout are in `coding-conventions.md`, coroutines in `coroutines-and-flow.md`.
**Official sources:**
- <https://kotlinlang.org/docs/null-safety.html>
- <https://kotlinlang.org/docs/collections-overview.html>
- <https://kotlinlang.org/docs/exceptions.html>
- <https://kotlinlang.org/docs/extensions.html>

**Rule levels:** see `../README.md`. Language facts are [OFFICIAL]; API-design rules are [DEFAULT] unless tagged.

---

## Null safety

The compiler distinguishes `String` from `String?`. The only sources of an NPE in pure Kotlin are: an explicit `throw`, `!!` on a null, an uninitialised `lateinit`, a platform type from Java/Objective-C, and initialisation-order inconsistencies. [OFFICIAL]

**MUST:**

- Prefer `?.`, `?:`, `let`, and smart casts. **MUST NOT** use `!!` outside a test or a line with a comment proving non-null-ness that the compiler cannot see.
- Treat every platform type as nullable at the boundary. A Java or Objective-C return value has unknown nullability (`String!`); **MUST** declare an explicit Kotlin type on any public declaration that would otherwise expose one.
- Use `requireNotNull(x) { "reason" }` / `checkNotNull` at an entry point when null is a contract violation — it fails with a message instead of an anonymous NPE.
- Use `filterNotNull()` / `mapNotNull { }` on collections rather than `!!` inside a loop.

```kotlin
// CORRECT
val name = user?.displayName ?: "Guest"
user?.let { track(it.id) }
val ids: List<UserId> = rows.mapNotNull { it.id?.let(::UserId) }

// WRONG — a crash with no message, at the one call site that happened to get null
val name = user!!.displayName
```

**`lateinit`** is for framework-injected fields that are guaranteed before first use (Android `onCreate`). **SHOULD NOT** use it in a plain class; a constructor parameter is the honest form. Reading an uninitialised `lateinit` throws `UninitializedPropertyAccessException`.

**Nullable receivers:** `fun String?.orDash() = this ?: "—"` is legal and readable. **SHOULD** keep such extensions `private`/`internal` — a public nullable-receiver extension invites `x.orDash()` where the reader expects a safe call.

---

## Immutability

**MUST** prefer `val` over `var` and read-only collection types in every declaration that does not need mutation. [OFFICIAL — coding conventions]

The consequences are concrete, not stylistic:

| A `var` or mutable collection in | Consequence |
|---|---|
| A composable parameter type | the class is **unstable** to the Compose compiler; see `../android/compose-ui.md` |
| A `UiState` | a consumer can mutate state the ViewModel owns; single-source-of-truth is gone |
| A domain model | two layers can disagree about the same object |
| An exported KMP type | Swift sees a mutable `NSMutableArray`-style type with no thread guarantees |

```kotlin
// CORRECT — immutable snapshot; a change produces a new value
public data class UserUiState(val profile: UserProfile?, val messages: List<UserMessage>)
val next = state.copy(messages = state.messages + message)

// WRONG — shared mutable state behind a val
public class UserUiState { val messages: MutableList<UserMessage> = mutableListOf() }
```

`data class` `copy` is the idiom for "modified snapshot". For a `MutableStateFlow`, use `update { it.copy(...) }` — atomic — not `value = value.copy(...)`.

---

## Collections

[OFFICIAL]

| Read-only | Mutable | Factory |
|---|---|---|
| `List<T>` | `MutableList<T>` | `listOf` / `mutableListOf` (ArrayList) |
| `Set<T>` | `MutableSet<T>` | `setOf` / `mutableSetOf` (LinkedHashSet) |
| `Map<K, V>` | `MutableMap<K, V>` | `mapOf` / `mutableMapOf` (LinkedHashMap) |

- **Read-only collections are covariant** (`List<Rectangle>` is a `List<Shape>`); mutable ones are not. **MUST** use the read-only type in a public signature.
- **Read-only is not immutable.** A `List<T>` may be a view over a `MutableList` someone else holds. For a defensive copy, `toList()`; for a genuinely immutable, hash-stable structure, `kotlinx.collections.immutable` — needed far less often than people assume now that Compose strong skipping is default.
- **Sequences for long chains or large inputs.** `asSequence()` makes `map`/`filter` lazy and avoids an intermediate list per step. **SHOULD NOT** use a sequence for a short chain over a small list; it is slower there.
- **`forEach` is the one higher-order function the conventions discourage** in favour of a `for` loop — see `coding-conventions.md`.

```kotlin
// CORRECT — one pass, no intermediate lists
val active = users.asSequence().filter { it.isActive }.map { it.toRow() }.take(20).toList()

// WRONG — a public API exposing a mutable, non-covariant type
public fun activeUsers(): MutableList<User>
```

**Equality:** `listOf(1) == listOf(1)` is structural. `Array` equality is referential — **MUST NOT** put an `Array` in a `data class` that relies on `equals`.

---

## Extension functions

[OFFICIAL] An extension is resolved **statically** on the declared type, not dynamically on the runtime type, and a member always wins over an extension with the same signature.

**SHOULD:**

- Use extensions for mapping and formatting at boundaries (`ArticleDto.toDomain()`), where the receiver's module must not know the target type.
- Keep them `internal` or `private`. A public extension on a type you do not own becomes part of your module's API surface forever.
- Name them for what they return, not what they do to the receiver.

**MUST NOT:**

- Shadow a member or a stdlib function; the call resolves to the member and the extension is silently dead.
- Put mutating logic in an extension on an immutable type — it will return a new value the caller may ignore.
- Rely on an extension being virtual.

```kotlin
// CORRECT — mapper in the data layer, internal, named for its result
internal fun UserDto.toEntity(): UserEntity = UserEntity(id = userId, /* … */)

// WRONG — public, on a stdlib type, name collides with the reader's expectation
public fun String.toUserId(): UserId   // every String in every module now "has" this
```

---

## Error handling

Three mechanisms; pick by what the caller must do.

| Mechanism | Use when |
|---|---|
| **Sealed error type carried by an exception** | the failure set is closed and the caller must branch on it; crosses suspend boundaries and the iOS boundary (with `@Throws`) correctly |
| **`Result<T>` / sealed outcome** | failure must be visible in the signature and the codebase already standardised on it |
| **Plain exception** | a programming error (`IllegalArgumentException`, `IllegalStateException`) — not expected, not caught |

**MUST:**

- Model expected failures as a **closed** set so the UI can branch exhaustively. An open hierarchy pushes the decision into an `else` branch.
- Translate framework exceptions into domain errors **once**, at the layer boundary that knows both.
- Rethrow `CancellationException` before any broader `catch` — see `coroutines-and-flow.md` § 8. **MUST NOT** use `runCatching` inside a coroutine; it swallows cancellation.
- Annotate exported throwing functions with `@Throws` or the iOS app terminates — see `../kmp/ios-interop.md`.

**MUST NOT:**

- `catch (e: Exception)` or `catch (e: Throwable)` to "be safe".
- Return a sentinel (`emptyList()`, `null`, `-1`) to mean failure.
- Use exceptions for control flow a return type can express.

```kotlin
// CORRECT — closed, typed, translated once
public sealed interface UserError { data object NotFound : UserError; /* … */ }
public class UserException(public val error: UserError) : Exception()

// WRONG — the caller learns nothing it can act on
suspend fun user(): User? = try { remote.fetch() } catch (e: Exception) { null }
```

`Nothing` return type on a function that always throws (`fun fail(msg: String): Nothing`) lets the compiler treat the call site as terminating — useful for `?: fail("...")`.

**Verified reference:** `../../examples/user-profile/` compiles both the exception-based and the `Result`-based forms and tests cancellation propagation for each.

---

## Kotlin 2.3+ checks that change what compiles cleanly

[OFFICIAL] <https://kotlinlang.org/docs/whatsnew23.html>

- **Unused return value checker** — a function's return value that is ignored is reported. Consume it or return `Unit`.
- **Data-flow-based `when` exhaustiveness** — an `else` branch may now be flagged unreachable; a previously accepted `when` may be flagged non-exhaustive.
- **Nested type aliases** — stable.

---

## Android / iOS differences

| Concern | JVM / Android | Kotlin/Native (iOS) |
|---|---|---|
| Platform types | from Java | from Objective-C; `nil` is representable, nullability annotations are honoured when present |
| `value class` | needs `@JvmInline` | needs `import kotlin.jvm.JvmInline` to resolve in `commonMain`; collapses to the primitive in the exported header |
| Exceptions crossing the boundary | ordinary | **terminate the app** unless declared with `@Throws` |
| `Array` vs `List` | both fine | `List` maps to `NSArray`; prefer `List` at the boundary |
| Sealed / data classes | ordinary | limited support in the Objective-C export — see `../kmp/ios-interop.md` |

---

## Testing recommendations

- **MUST** test every branch of a sealed error type's consumer — the `when` is where "unknown state" bugs live.
- **MUST** test the null path of any function with a nullable parameter or return.
- **SHOULD** test boundary values on collections: empty, single element, duplicates.
- **MUST** test that cancellation propagates through any function that catches exceptions.

---

## Cross-references

- Style, naming, API-surface rules: `coding-conventions.md`
- Scopes, dispatchers, cancellation: `coroutines-and-flow.md`
- Compose stability consequences of `var`: `../android/compose-ui.md`
- Error representation trade-offs in full: `../../examples/DECISIONS.md`
- Export constraints: `../kmp/ios-interop.md`
