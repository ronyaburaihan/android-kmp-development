# Workflow: Migrate Code to KMP

Standing contract: `README.md`. Prerequisites: `inspect-project.md`, **and an approved stage plan from `assess-kmp-adoption.md`**.

**Primitives:** `../process/vertical-slice.md` — each stage is a slice with its own green build.

---

## 1. Objective

Execute **one approved stage** of a KMP migration: move a bounded set of existing Android code into `commonMain`, keeping Android behaviour unchanged.

**MUST NOT** start without an approved plan naming the files in scope. "Make this shared" is not a scope.

**The invariant:** Android ships unchanged. Every step is verified against the existing Android behaviour first; iOS consumption comes after.

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| The approved stage, with its file list | yes | **MUST** stop. Run `assess-kmp-adoption.md`. |
| Stage exit criteria | yes | **MUST** stop — without them, "done" is undefined. |
| Target module: existing shared module, or new | yes | **MUST** ask. A new module is a structural change. |
| Targets to declare | no | Default: `androidTarget`, `iosArm64`, `iosSimulatorArm64`. |
| Does iOS consume it in this stage? | yes | **MUST** ask. Determines whether a facade and framework link are in scope. |
| Approved structural items (DI swap, library replacement) | yes if any | **MUST NOT** perform one that is not explicitly approved. |

---

## 3. Initial project inspection

Run `inspect-project.md`, then inspect the **exact files in scope**, line by line.

```bash
# every Android/JVM dependency in the files being moved — this is the work list
for f in <files-in-scope>; do
  echo "== $f"; grep -nE '^import (android|androidx|java|javax|kotlinx\.android|dagger|retrofit2|com\.squareup|com\.google)' "$f"
done

# who depends on the code being moved — the blast radius
grep -rn '<ClassBeingMoved>' --include='*.kt' . | grep -v build/

# existing shared module conventions, if one exists
find . -type d -name commonMain -not -path '*/build/*'
cat <shared-module>/build.gradle.kts 2>/dev/null
```

**MUST** produce a per-file dependency ledger before moving anything:

| File | Android/JVM imports | Resolution | Target source set |
|---|---|---|---|
| `Pricing.kt` | none | move as is | `commonMain` |
| `PricingRepository.kt` | `android.content.Context` | extract `AppPaths` interface | `commonMain` + platform impls |
| `Formatter.kt` | `java.time.*` | `kotlin.time` | `commonMain` |

**MUST** record every call site of every moved type. A move that changes a package breaks callers silently until compile.

**MUST** establish a green baseline: Android build plus full test run. Without it, no later failure is attributable.

**MUST** verify the existing test coverage of the in-scope files. If coverage is thin → D1.

---

## 4. Step-by-step procedure

### 4.1 Confirm the execution plan — MUST

Present and wait:

- the dependency ledger from section 3
- the exact file moves: from → to, including source set
- interfaces being extracted, and their platform implementations
- call sites that change
- whether a new module is created (→ D2)
- the rollback: a single revert, or an ordered sequence

### 4.2 Characterisation tests first — MUST if coverage is thin

**MUST** pin the current Android behaviour before moving code, when existing tests do not already do so.

```kotlin
// Written against the CURRENT Android implementation, before any move.
// These tests MUST pass unchanged after the migration.
class PricingCharacterisationTest {
    @Test fun appliesVolumeDiscountAtTenUnits() { /* ... */ }
    @Test fun roundsHalfUpAtTwoDecimals() { /* ... */ }
}
```

**MUST NOT** modify a characterisation test later to accommodate the migration. If one fails after the move, the migration changed behaviour — that is the finding.

### 4.3 Prepare the module

**If an existing shared module is in scope:** use it. **MUST NOT** create a second one.

**If a new module is approved (D2):** match the project's existing module conventions exactly — naming, convention plugin, package root.

```kotlin
// MUST follow the default hierarchy template; MUST NOT hand-wire dependsOn
kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies { /* only KMP-capable dependencies */ }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
```

**MUST NOT** declare `iosX64` (Tier 3) or any deprecated target unless explicitly approved. See `../references/version-matrix.md`.

### 4.4 Move in dependency order — leaves first

**MUST** move in an order where each step compiles: models and pure functions before the code that uses them.

```
models / value types  →  pure logic  →  interfaces  →  implementations
```

**MUST** move one coherent group at a time and compile after each. A batch move that fails gives one error list with many causes.

**MUST** preserve the package name where possible. A package change is a wider diff and, on iOS, changes the generated Objective-C names.

### 4.5 Resolve each Android dependency

**MUST** apply these resolutions, not improvisations:

| Dependency | Resolution |
|---|---|
| `Context` for file paths | `interface AppPaths` in common; platform implementations |
| `Context` for strings | return a resource key or a sealed `UiText` from shared code; resolve in the UI |
| `SharedPreferences` | DataStore via the existing data layer → may require `add-persistence.md` |
| `java.time` | `kotlin.time` (stdlib, stable since Kotlin 2.3) for instants/durations; `kotlinx-datetime` only for calendar/time-zone work |
| `java.io.File` | `okio` if already present, or a path-based interface |
| `@Parcelize` / `Parcelable` | does not cross; use a different mechanism or keep the type Android-side |
| Retrofit data source | Ktor in `commonMain`, behind the **existing** repository interface |
| Moshi / Gson | kotlinx.serialization — **MUST** audit naming strategy, every `@SerialName`, and nullability |
| Hilt annotations | per the approved DI decision only; **MUST NOT** swap containers unprompted |
| Reflection (`::class.java`, `Class.forName`) | usually requires redesign — → D5 |
| `Dispatchers` hardcoded | inject a `CoroutineDispatcher` |

**MUST** prefer an interface plus DI over `expect`/`actual`. Where `expect` is genuinely needed, use `expect fun` factories, not `expect class` (Beta). See `../references/kmp/project-structure.md`.

```kotlin
// CORRECT — interface in common, platform implementations, no expect class
// commonMain
interface AppPaths { fun databaseFile(name: String): String }

// androidMain
internal class AndroidAppPaths(private val context: Context) : AppPaths {
    override fun databaseFile(name: String) = context.getDatabasePath(name).absolutePath
}
```

```kotlin
// WRONG — expect class, and it reaches a platform SDK from common code
// commonMain
expect class AppPaths() { fun databaseFile(name: String): String }
```

The wrong form permits one implementation per target, cannot be faked in a test, and emits a Beta warning that invites an `-Xexpect-actual-classes` suppression.

### 4.6 Keep Android compiling and passing, continuously

After each group:

```bash
./gradlew :<module>:compileKotlinMetadata
./gradlew :<module>:compileDebugKotlin
./gradlew :<androidApp>:assembleDebug
./gradlew :<module>:allTests
```

**MUST NOT** proceed to the next group while Android is red.

### 4.7 Add the iOS target and compile it

Native compilation is where remaining JVM-only usage surfaces.

```bash
./gradlew :<module>:compileKotlinIosSimulatorArm64
./gradlew :<module>:iosSimulatorArm64Test
```

**MUST** fix native failures by relocating code to a platform source set or substituting a KMP API. **MUST NOT** remove a target to make the build pass.

### 4.8 Design the exported surface — if iOS consumes it in this stage

**MUST** export a small facade, not the whole domain. See `../references/kmp/ios-interop.md`.

**MUST** at the exported boundary:

- not expose `Flow` — provide a subscription function returning a cancellation handle
- not use generics on exported interfaces
- not rely on default arguments
- annotate throwing functions `@Throws(...)`, including `CancellationException` for suspend functions
- ensure no two exported classes share a simple name across packages

```kotlin
// CORRECT — iosMain facade
class SharedPricing internal constructor(private val calculate: CalculatePriceUseCase) {
    @Throws(PricingException::class, CancellationException::class)
    suspend fun price(units: Int): String = calculate(units).formatted

    fun observePrice(units: Int, onEach: (String) -> Unit): Cancellable { /* ... */ }
}
```

```bash
./gradlew :<module>:linkDebugFrameworkIosSimulatorArm64
```

### 4.9 Validate

Run section 7.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | In-scope code has **thin or no test coverage** | (a) characterisation tests first (4.2); (b) move without a net | **MUST** ask. Strongly recommend (a). |
| D2 | A **new module** is needed | (a) create it, matching conventions; (b) use an existing shared module | **MUST** ask — structural. |
| D3 | A move requires replacing a **library** (Retrofit→Ktor, Moshi→kotlinx) beyond the approved stage | (a) in scope now; (b) separate stage | **MUST** ask unless the approved plan already named it. |
| D4 | A move requires replacing the **DI container** | — | **MUST** stop. Structural, project-wide. Only proceed if the plan explicitly approved it. |
| D5 | Code uses **reflection** with no native equivalent | (a) redesign; (b) keep the type Android-side behind an interface | **MUST** ask. Recommend (b) to keep the stage small. |
| D6 | A moved type's **package must change** | (a) keep the package by relocating directories; (b) change it and update call sites | **SHOULD** prefer (a). A package change alters generated Objective-C names. |
| D7 | Moved code touches **persisted data** | — | **MUST** stop. → `add-persistence.md`. Data migration is not part of a code move. |
| D8 | Only an `expect class` will do | (a) redesign as interface + factory; (b) `expect class` with `-Xexpect-actual-classes` | **MUST** ask. Recommend (a); `expect class` is Beta. |
| D9 | A dependency has **no KMP artifact** | (a) replace it; (b) keep it Android-side behind a common interface; (c) descope | **MUST** ask. Recommend (b). |
| D10 | A characterisation test **fails after the move** | — | **MUST** stop. Behaviour changed. **MUST NOT** adjust the test. |
| D11 | Stage would make Android ship **differently** | — | **MUST** stop and surface it. The invariant is that Android is unchanged. |
| D12 | `@Parcelize`/`Parcelable` on a moved type | (a) keep the type Android-side; (b) replace the mechanism | **MUST** ask. Recommend (a). |

---

## 6. Implementation rules

**MUST:**

1. Work only the approved file list. Anything else is a new stage.
2. Build the dependency ledger before moving anything.
3. Move leaves first, in dependency order, compiling after each group.
4. Keep Android green at every step.
5. Prefer interface + DI; then `expect fun`; `expect class` only with approval.
6. Preserve package names where possible.
7. Keep moved types `internal` unless another module or the facade needs them.
8. Use the default source-set hierarchy template.
9. Keep characterisation tests byte-identical through the migration.
10. Move tests alongside the code: shared logic's tests belong in `commonTest`.

**MUST NOT:**

11. Begin without an approved stage plan.
12. Change Android-observable behaviour.
13. Swap the DI container, HTTP client, or serialization library beyond the approved scope.
14. Add `-Xexpect-actual-classes` without an approved D8.
15. Declare `iosX64` or a deprecated target unprompted.
16. Hand-wire `dependsOn` instead of the default hierarchy template.
17. Remove or descope a target to make a native build pass.
18. Put a platform type in `commonMain`.
19. Expose `Flow`, generics on interfaces, or default arguments at the iOS boundary.
20. Modify or `@Ignore` an existing test.
21. Put a secret or credential in `commonMain` — R8 does not apply to the iOS framework.
22. Reformat or re-lint code while moving it. A move diff must read as a move.

---

## 7. Validation requirements

| # | Check | Command | Required |
|---|---|---|---|
| V1 | `commonMain` compiles | `./gradlew :<module>:compileKotlinMetadata` | MUST |
| V2 | Android compiles | `./gradlew :<module>:compileDebugKotlin` | MUST |
| V3 | **Android app builds** | `./gradlew :<androidApp>:assembleDebug` | MUST |
| V4 | iOS target compiles | `./gradlew :<module>:compileKotlinIosSimulatorArm64` | MUST |
| V5 | **Characterisation tests pass, unmodified** | `git diff` on the test files shows no change; tests green | MUST |
| V6 | Full existing test suite passes | module + app test tasks | MUST |
| V7 | `commonTest` runs on **every** target | `./gradlew :<module>:allTests` | MUST |
| V8 | iOS tests run | `./gradlew :<module>:iosSimulatorArm64Test` | MUST |
| V9 | iOS framework links | `./gradlew :<module>:linkDebugFrameworkIosSimulatorArm64` | MUST if iOS consumes it |
| V10 | Swift call site compiles | Xcode build / `xcodebuild` | MUST if iOS consumes it |
| V11 | **Android release build with R8** | `./gradlew bundleRelease` | MUST |
| V12 | **Android app runs; migrated flows behave identically** | manual | MUST |
| V13 | No platform imports in `commonMain` | `grep -rE '^import (android|androidx|java|javax)' <module>/src/commonMain` → empty | MUST |
| V14 | No exported `Flow` or generic interface at the boundary | review the facade | MUST if iOS consumes it |
| V15 | Lint / detekt / ktlint | project tasks | MUST if configured |
| V16 | No test assertion modified | review the diff | MUST |
| V17 | Shared-code crash symbolicates on iOS | deliberate non-fatal from a release-configured build | SHOULD |

V12 is the check on the invariant. Compilation success proves the code moved, not that it still behaves the same.

---

## 8. Failure handling

| Failure | Response |
|---|---|
| Native compile fails, Android passes | A JVM-only API or dependency in `commonMain`. **MUST** relocate or substitute. **MUST NOT** drop the target. |
| A dependency has no native artifact | → D9. **MUST NOT** vendor or reimplement a library to force the move. |
| A characterisation test fails | → D10. **MUST** stop. The migration changed behaviour. Find the cause; do not edit the test. |
| An Android test unrelated to the stage breaks | A moved type's visibility or package changed. **MUST** fix the call site, not the test. |
| `expect`/`actual` mismatch errors | Signature or package drift between source sets. **MUST** align them; **MUST NOT** suppress. |
| `expect class` Beta warning | → D8 before adding the suppression flag. |
| KSP generates nothing for a native target | The processor is not registered per target. **MUST** add `add("ksp<Target>", ...)`. |
| iOS framework links but Swift cannot call the API | An export constraint — generics, `Flow`, default arguments, a name collision. → `review-ios-interop.md`. |
| Swift build fails after the move | **MUST** treat as a stage failure, not an iOS-team problem. Fix the exported surface. |
| Name collision in the generated framework | Two exported classes share a simple name. **MUST** rename one in Kotlin. |
| Android release build fails after the move | R8 keep rules referencing moved classes. **MUST** update them. |
| Behaviour differs between Android and iOS for shared code | **MUST** stop and report. Usually a time-zone, locale, string-comparison, or floating-point difference. Add a `commonTest` case covering it. |
| Migration half-done and blocked | **MUST** revert to the baseline rather than leaving a half-migrated module. A partially shared module is worse than either end state. |
| Stage exceeds its approved file list | **MUST** stop and request a scope extension. Do not absorb the extra files. |

---

## 9. Completion criteria

**MUST** all hold:

1. Only the approved file list moved.
2. Dependency ledger produced, and every Android/JVM dependency resolved by a recorded method.
3. `commonMain` contains no platform imports (V13).
4. Characterisation tests present if coverage was thin, and passing **unmodified**.
5. Android behaviour unchanged, verified by V12.
6. V1–V8, V11–V13, V15, V16 `PASS`.
7. V9, V10, V14 `PASS` if iOS consumes the module this stage.
8. Shared logic's tests live in `commonTest` and run on every target.
9. No DI, HTTP, or serialization library swapped beyond the approved scope.
10. No `expect class` added without approved D8; no `-Xexpect-actual-classes` added without it.
11. No target removed, and no Tier 3 or deprecated target added.
12. No existing test modified, skipped, or deleted.
13. Stage exit criteria from the approved plan met and quoted in the report.

---

## 10. Final report format

Base skeleton from `README.md`, with these additions.

```markdown
## Migrate to KMP — Stage <n>: <name>

### Outcome
<DONE | DONE WITH CAVEATS | BLOCKED | NEEDS DECISION> — one sentence.

### Stage definition
- Approved plan: <reference>
- Scope: <n> files
- Exit criteria (quoted from the plan):
- iOS consumes this stage: <yes/no>

### Inspection findings
- Target module: <existing `path` | new, approved at D2>
- Targets declared: <androidTarget, iosArm64, iosSimulatorArm64>
- Baseline Android build + tests: <PASS | FAIL>
- Existing coverage on in-scope files: <good | thin → characterisation tests added>

### Dependency ledger
| File | Android/JVM dependency | Resolution | Destination |
|---|---|---|---|

### Moves performed
| From | To | Source set | Package preserved |
|---|---|---|---|

### Interfaces extracted
| Interface (commonMain) | Android impl | iOS impl |
|---|---|---|

### Exported iOS surface — if applicable
| Function | `@Throws` | Return type | Export-safe |
|---|---|---|---|

<State "Not exported this stage." if iOS does not consume it.>

### Changes made
| File | Change |
|---|---|

### Conventions followed
<Matched patterns with source files. Deliberate deviations from `../references/`
with reasons.>

### Validation performed
| # | Check | Command | Result |
|---|---|---|---|
| V1 | commonMain compiles | | |
| V2 | Android compiles | | |
| V3 | **Android app builds** | | |
| V4 | iOS target compiles | | |
| V5 | **Characterisation tests pass unmodified** | | |
| V6 | Full existing suite | | |
| V7 | commonTest on every target | | |
| V8 | iOS tests run | | |
| V9 | iOS framework links | | |
| V10 | Swift call site compiles | | |
| V11 | Android release build with R8 | | |
| V12 | **Android behaviour unchanged (manual)** | | |
| V13 | No platform imports in commonMain | | |
| V14 | No exported Flow / generic interface | | |
| V15 | Lint / detekt / ktlint | | |
| V16 | No test assertion modified | | |
| V17 | iOS symbolication of shared frames | | |

### Android invariant
<Confirm explicitly: Android behaviour unchanged, and how it was verified.
If it did change, say exactly how — this is a stage failure requiring approval.>

### Tests added or moved
| Test | From → To | Covers |
|---|---|---|

### Stage exit criteria
| Criterion | Met |
|---|---|

### Not done
### Observations
### Decisions needed
```
