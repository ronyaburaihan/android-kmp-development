# Workflow: Review iOS Interop

Standing contract: `README.md`. Prerequisite: `inspect-project.md`.

---

## 1. Objective

Audit the shared module's Swift-facing surface against the Objective-C export constraints, and fix what is unusable or unsafe from Swift.

Two modes:

| Mode | Trigger | Output |
|---|---|---|
| **Audit** (default) | "review the exported API", "the iOS team is struggling" | Findings and a prioritised fix plan. No code changes. |
| **Fix** | A specific reported breakage, or an approved audit finding | Minimal changes to the exported surface. |

**MUST** default to audit. **MUST NOT** reshape an exported API — a contract the iOS app compiles against — without approval.

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| Mode | no | Default: audit. |
| For fix mode: the specific Swift-side failure, verbatim | yes | **MUST** ask. Compiler text or runtime error; not a paraphrase. |
| Which Kotlin declarations the iOS app actually uses | no | **SHOULD** ask. Determines what is load-bearing versus theoretically exported. |
| Swift version / Xcode version | no | Affects `async` availability. |
| Is a facade in place, or is the domain exported wholesale? | no | Derive from inspection. |

---

## 3. Initial project inspection

Run `inspect-project.md`, then audit the exported surface.

```bash
# framework configuration and integration style
grep -rn 'framework\|binaries\|baseName\|XCFramework\|cocoapods\|swiftExport' --include='*.gradle.kts' . | grep -v build/

# generate and read the actual header — the source of truth for what Swift sees
./gradlew :<shared>:linkDebugFrameworkIosSimulatorArm64
find . -name '*.h' -path '*Headers*' -path '*build*' | head
```

**MUST** read the generated Objective-C header. It is the only authoritative statement of what Swift can call. Source-level inspection misses mangled names, dropped default arguments and omitted generics.

Then audit the Kotlin side:

```bash
SHARED=<shared-module>

# Flow at the boundary — not usefully exported
grep -rn 'fun .*: Flow<\|val .*: *Flow<\|StateFlow<\|SharedFlow<' --include='*.kt' $SHARED/src/commonMain $SHARED/src/iosMain

# suspend functions missing @Throws
grep -rn -B2 'suspend fun' --include='*.kt' $SHARED/src/commonMain $SHARED/src/iosMain | grep -v '@Throws'

# generics on interfaces — unsupported
grep -rn 'interface [A-Za-z]*<' --include='*.kt' $SHARED/src/commonMain

# default arguments — dropped from the header
grep -rnE 'fun [a-zA-Z]+\([^)]*=' --include='*.kt' $SHARED/src/commonMain

# inline value classes — collapse to the underlying primitive
grep -rn 'value class\|@JvmInline' --include='*.kt' $SHARED/src/commonMain

# sealed / data classes at the boundary — limited support
grep -rn 'sealed interface\|sealed class' --include='*.kt' $SHARED/src/commonMain

# simple-name collisions across packages — renamed unpredictably
grep -rhoE '^(internal )?(data |sealed |abstract )?class ([A-Za-z0-9_]+)' --include='*.kt' $SHARED/src/commonMain \
  | awk '{print $NF}' | sort | uniq -d

# collections returned to Swift — double conversion per element
grep -rnE 'fun .*: (List|Map|Set)<' --include='*.kt' $SHARED/src/iosMain

# Info.plist requirements
grep -rn 'CADisableMinimumFrameDurationOnPhone\|UsageDescription' --include='Info.plist' . 2>/dev/null
```

**MUST** record the integration option in use (direct, CocoaPods, SPM) and whether Swift export is configured — Swift export is Alpha and changes the entire analysis.

Reference for every constraint: `../references/kmp/ios-interop.md`.

---

## 4. Step-by-step procedure

### 4.1 Build the findings table — MUST

Classify every finding by what it costs the iOS team:

| Severity | Definition | Examples |
|---|---|---|
| **S1 — Unusable** | Swift cannot call it, or calling it crashes | `Flow` returned; generic interface; missing `@Throws` on a throwing function; name collision |
| **S2 — Unsafe** | Callable but wrong at runtime | Non-`CancellationException` escaping a `@Throws`-less suspend function; strongly-linked optional Objective-C class; inline value class losing type safety |
| **S3 — Awkward** | Callable but hostile | Default arguments dropped; `MyLibraryUtilsKt.foo()` wrappers; sealed class with no Swift enum; large collection crossing per call |
| **S4 — Risk** | Not yet broken | Wide exported surface; no Swift smoke test; symbolication unverified |

**MUST** mark whether the iOS app actually uses each declaration. An S1 finding on an unused export is lower priority than an S3 on a hot path.

### 4.2 Audit the missing-safeguard items

**MUST** check all four, since each has no compile-time signal:

| Safeguard | Check |
|---|---|
| `CADisableMinimumFrameDurationOnPhone` in `Info.plist` | **Required for CMP on iOS; the app crashes without it.** |
| Usage-description strings for every capability used | **A missing string is a launch-time crash.** |
| Swift smoke test over the facade | Without it, export regressions reach the iOS team, not CI. |
| dSYM upload / symbolication verified | `[UNVERIFIED]` in the reference set — **MUST** be confirmed empirically, not assumed. |

### 4.3 Report and stop — audit mode

Present the findings table, the fix plan ordered by severity-and-usage, and the API-contract impact of each fix. **MUST** stop here in audit mode.

**MUST** state for each proposed fix whether it is **source-breaking for the Swift app**. That is the decision the iOS team must make.

### 4.4 Fix — only approved findings

#### `Flow` at the boundary (S1)

```kotlin
// WRONG — Swift receives an opaque object it cannot iterate
class FeedFacade(private val repository: FeedRepository) {
    fun observeFeed(): Flow<List<ArticleDto>> = repository.observeFeed()
}

// CORRECT — explicit subscription with a cancellation handle
class FeedFacade(
    private val repository: FeedRepository,
    private val scope: CoroutineScope,
) {
    fun observeFeed(onEach: (List<ArticleDto>) -> Unit): Cancellable {
        val job = scope.launch { repository.observeFeed().collect(onEach) }
        return Cancellable { job.cancel() }
    }
}

class Cancellable(private val onCancel: () -> Unit) {
    fun cancel() = onCancel()
}
```

**MUST** keep the `Flow`-returning method for Kotlin consumers and add the callback form on the facade. **MUST NOT** remove the `Flow` API that Android uses.

#### Missing `@Throws` (S1/S2)

```kotlin
// WRONG — any AuthException terminates the iOS app
suspend fun login(username: String, token: String): Session

// CORRECT
@Throws(AuthException::class, NetworkException::class, CancellationException::class)
suspend fun login(username: String, token: String): Session
```

**MUST** include `CancellationException` for suspend functions.

#### Generics on an exported interface (S1)

```kotlin
// WRONG — generics are unsupported on exported interfaces
interface Cache<T> { fun get(key: String): T? }

// CORRECT — concrete at the boundary; keep the generic version internal
internal interface Cache<T> { fun get(key: String): T? }
interface ArticleCache { fun get(key: String): Article? }
```

#### Name collision (S1)

```kotlin
// WRONG — com.example.data.User and com.example.domain.User in one framework
// CORRECT — rename one in Kotlin
// com.example.data.UserEntity
// com.example.domain.User
```

**MUST** rename on the Kotlin side. **MUST NOT** rely on whichever mangled name the current compiler happens to produce.

#### Default arguments (S3)

```kotlin
// WRONG at the boundary — Swift cannot omit `radix`
fun format(value: Int, radix: Int = 10): String

// CORRECT — explicit overloads on the facade
fun format(value: Int): String = format(value, 10)
fun format(value: Int, radix: Int): String
```

#### Wide exported surface (S4)

**MUST** propose a facade rather than hiding declarations one at a time. `@HiddenFromObjC` applied broadly is a symptom that the surface was never designed.

```kotlin
// iosMain — one designed entry point
class SharedApi internal constructor(private val getFeed: GetFeedUseCase) {
    @Throws(NetworkException::class, CancellationException::class)
    suspend fun feed(): List<ArticleDto> = getFeed().map(::toDto)
}

object SharedApiFactory {
    fun create(): SharedApi = /* resolve from the DI graph */
}
```

**MUST** keep the DI entry point non-reified — Swift cannot call Koin's `get<T>()`. See `../references/libraries/koin-di.md`.

### 4.5 Add the Swift smoke test — MUST when fixing

```swift
final class SharedApiSmokeTests: XCTestCase {
    func testFeedIsCallable() async throws {
        let api = SharedApiFactory().create()
        let feed = try await api.feed()
        XCTAssertNotNil(feed)
    }
}
```

Without it, the next export regression is found by the iOS team, not by CI.

### 4.6 Validate

Run section 7.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | A fix is **source-breaking for the Swift app** | (a) break and coordinate with the iOS team; (b) add the new form alongside and deprecate the old | **MUST** ask. Recommend (b) where feasible. |
| D2 | Domain is **exported wholesale**, no facade | (a) introduce a facade — a larger change; (b) fix findings in place | **MUST** ask. Recommend (a) if findings are numerous; it addresses the cause. |
| D3 | `Flow` is used by the iOS app through a **third-party bridge** (SKIE, KMP-NativeCoroutines) | (a) keep the bridge; (b) replace with callback facades | **MUST** check the bridge's Kotlin compatibility first — `../references/version-matrix.md` flags SKIE's support ceiling as `[UNVERIFIED]`. Report before changing. |
| D4 | **Swift export** is configured | — | **MUST** report that it is Alpha and not production-ready, and that the reference set records a **DSL conflict** (`swiftExport {}` vs `export { swift {} }`) requiring verification. **MUST NOT** migrate to or away from it without approval. |
| D5 | `CADisableMinimumFrameDurationOnPhone` **missing** with CMP in use | — | **MUST** report as a crash-level defect immediately, ahead of every other finding. Fixing it is a one-line `Info.plist` addition; **SHOULD** fix it even in audit mode after telling the user. |
| D6 | A usage-description string is **missing** for a capability in use | — | Same as D5: crash-level, report immediately. |
| D7 | Symbolication of shared Kotlin frames **unverified** | (a) verify now with a deliberate non-fatal; (b) record as a risk | **SHOULD** recommend (a). It is cheap and the answer is needed before the next production incident. |
| D8 | Many declarations need hiding | (a) design a facade (D2); (b) apply `@HiddenFromObjC` individually | **SHOULD** recommend (a). Mass `@HiddenFromObjC` is a smell. |
| D9 | An inline value class crosses the boundary | (a) accept the loss of type safety; (b) use a data class at the boundary | **SHOULD** ask. The type safety is already gone; (b) at least makes that visible. |
| D10 | A hot path returns a **large collection** to Swift | (a) return fewer/smaller values; (b) measure first | **SHOULD** recommend (b) before restructuring — per-element double conversion matters only at volume. |

---

## 6. Implementation rules

**MUST:**

1. Read the **generated Objective-C header**, not only the Kotlin source.
2. Default to audit; obtain approval before changing an exported signature.
3. State, per fix, whether it is source-breaking for Swift.
4. Keep Kotlin-facing APIs intact — add the Swift-friendly form, do not replace the idiomatic one Android uses.
5. Annotate every exported throwing function `@Throws(...)`, including `CancellationException` for suspend functions.
6. Rename colliding classes on the Kotlin side.
7. Add or extend a Swift smoke test for every fixed declaration.
8. Verify the framework links and the Swift side compiles after each change.

**MUST NOT:**

9. Change an exported signature without approval (D1).
10. Remove a `Flow`-returning API that Kotlin consumers use.
11. Apply `@HiddenFromObjC` broadly instead of designing a facade.
12. Add `-Xexport-kdoc`, change the framework `baseName`, or switch the integration option as a side effect.
13. Migrate to Swift export, or present it as production-ready.
14. Suppress an export warning to make the link step pass.
15. Change the iOS project's build settings — that is the iOS team's file. Report what is needed instead.
16. Refactor Kotlin internals while fixing the boundary. Keep the diff at the surface.

---

## 7. Validation requirements

| # | Check | Command | Required |
|---|---|---|---|
| V1 | Framework links | `./gradlew :<shared>:linkDebugFrameworkIosSimulatorArm64` | MUST |
| V2 | Generated header reviewed, findings confirmed against it | read the header | MUST |
| V3 | **Swift side compiles** | `xcodebuild -scheme <scheme> build` or Xcode | MUST in fix mode |
| V4 | Swift smoke test passes | `xcodebuild test` | MUST in fix mode |
| V5 | Kotlin compiles — all targets | `./gradlew :<shared>:allTests` + metadata compile | MUST |
| V6 | Android unaffected | `./gradlew :<androidApp>:assembleDebug` + Android tests | MUST |
| V7 | No exported `Flow`, generic interface, or missing `@Throws` remains among fixed items | re-run the 3.x greps | MUST in fix mode |
| V8 | No simple-name collisions among exported classes | re-run the duplicate-name check | MUST |
| V9 | `CADisableMinimumFrameDurationOnPhone` present if CMP is used | `grep` `Info.plist` | MUST |
| V10 | Usage-description strings present for every capability used | `grep` `Info.plist` | MUST |
| V11 | Shared-code crash symbolicates | deliberate non-fatal from a release-configured build | SHOULD |
| V12 | Exported surface size before → after | count exported declarations in the header | SHOULD |

---

## 8. Failure handling

| Failure | Response |
|---|---|
| Framework link fails | **MUST** fix the Kotlin error. **MUST NOT** disable a target or remove an export to get a link. |
| Swift build fails after a fix | **MUST** treat as the fix being wrong, not an iOS problem. Revert and reconsider. |
| Header shows a mangled name you did not expect | A collision or a name-mapping rule. **MUST** rename in Kotlin; **MUST NOT** code against the mangled name. |
| A fix breaks the Swift app's existing call sites | → D1. **MUST** stop and coordinate. |
| `@Throws` added, exception still terminates the app | The thrown type is not in the list, or a nested call throws something else. **MUST** audit the whole call tree. |
| Suspend function cannot be called from Swift as expected | Swift version below 5.5, or the declaration is not actually exported. **MUST** check the header first. |
| Third-party bridge (SKIE) fails after a Kotlin upgrade | → D3. **MUST** check the bridge's compatibility ceiling. **MUST NOT** downgrade Kotlin to accommodate it without reporting. |
| iOS app crashes immediately on launch | Check `CADisableMinimumFrameDurationOnPhone` and usage strings **first** — both are crash-level with no compile signal. |
| Cannot run Xcode (no macOS, no project access) | **MUST** mark V3, V4, V11 `NOT RUN` and downgrade the Outcome. **MUST NOT** claim the Swift side works. |
| Audit finds dozens of S1 findings | The surface was never designed → D2. **MUST** propose a facade rather than enumerate dozens of individual fixes. |
| Swift export is configured and in use | → D4. **MUST** report its Alpha status and the DSL conflict before touching it. |

---

## 9. Completion criteria

### Audit mode

1. Generated header read and findings confirmed against it.
2. Every finding classified S1–S4, with whether the iOS app uses it.
3. All four safeguards in 4.2 checked.
4. Fix plan ordered by severity and usage, each item marked source-breaking or not.
5. Zero files changed, except an approved crash-level fix under D5/D6.
6. V1, V2, V8, V9, V10 `PASS`.

### Fix mode

1. Only approved findings changed.
2. No Kotlin-facing API removed.
3. `@Throws` complete on every fixed throwing declaration, including `CancellationException`.
4. Swift smoke test covers every fixed declaration.
5. V1–V10 `PASS` or explicitly `NOT RUN` with a reason.
6. Android unaffected (V6).
7. No suppression or `@HiddenFromObjC` used in place of a designed facade.
8. Every source-breaking change recorded, with the Swift call sites it affects.

---

## 10. Final report format

Base skeleton from `README.md`, with these additions.

```markdown
## Review iOS Interop — <AUDIT | FIX>

### Outcome
<AUDIT COMPLETE | DONE | DONE WITH CAVEATS | BLOCKED | NEEDS DECISION> — one sentence.

### Inspection findings
- Integration option: <direct | CocoaPods | SPM>
- Swift export configured: <no | yes — Alpha (D4)>
- Exported surface: <facade at `path` | whole domain>
- Exported declarations in the header: <n>
- Third-party bridge: <none | SKIE <version> | KMP-NativeCoroutines>
- Header read: `path`

### Crash-level safeguards
| Safeguard | Status |
|---|---|
| `CADisableMinimumFrameDurationOnPhone` | PRESENT / **MISSING (D5)** / n/a |
| Usage-description strings | COMPLETE / **MISSING: <keys> (D6)** |
| Swift smoke test | PRESENT / ABSENT |
| Shared-frame symbolication | VERIFIED / **UNVERIFIED (D7)** |

### Findings
| # | Severity | Finding | Declaration | Used by iOS? | Fix | Source-breaking? |
|---|---|---|---|---|---|---|
| 1 | S1 | `Flow` returned | `path:line` | yes | callback + Cancellable | **yes** |
| 2 | S1 | missing `@Throws` | `path:line` | yes | add `@Throws` | no |
| 3 | S3 | default argument dropped | `path:line` | no | explicit overloads | no |

### Fix plan — ordered
| Order | Finding | Why this order | Approval needed |
|---|---|---|---|

### Changes made
| File | Change |
|---|---|

<"None — audit only." where applicable.>

### Source-breaking changes
| Change | Swift call sites affected | Coordinated with iOS team? |
|---|---|---|

<"None." if none.>

### Validation performed
| # | Check | Command | Result |
|---|---|---|---|
| V1 | Framework links | | |
| V2 | Header reviewed | | |
| V3 | **Swift compiles** | | |
| V4 | Swift smoke test | | |
| V5 | Kotlin all targets | | |
| V6 | Android unaffected | | |
| V7 | No S1 patterns remain among fixed items | | |
| V8 | No name collisions | | |
| V9 | `CADisable...` present | | |
| V10 | Usage strings present | | |
| V11 | Symbolication verified | | |
| V12 | Exported surface before → after | | |

### Not done
### Observations
### Decisions needed
<D1–D10 triggered, with options and a recommendation.>
```
