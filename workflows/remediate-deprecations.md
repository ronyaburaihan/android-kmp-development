# Workflow: Remediate Deprecations

Standing contract: `README.md`. Prerequisite: `inspect-project.md`.

---

## 1. Objective

Remove deprecated APIs and practices from an existing codebase, in risk order, as a series of independently verifiable changes.

Scope is replacing deprecated usages with their documented replacements. It is **not** a refactor, an architecture change, or a version upgrade.

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| Which deprecations, or "audit and propose" | yes | Default: audit, report, then **stop for approval** before fixing. |
| Risk appetite / release window | no | Default: fix only zero-behaviour-change items; propose the rest. |
| Is a specific deadline forcing this? | no | A Play deadline changes the priority order. |

**MUST** treat "fix the deprecation warnings" as an audit request, not a licence to change every warning site. Deprecations differ enormously in risk — `../references/deprecations.md` distinguishes them.

---

## 3. Initial project inspection

Run `inspect-project.md`, then audit against `../references/deprecations.md`.

```bash
# compiler-reported deprecations, with counts
./gradlew compileDebugKotlin 2>&1 | grep -i 'deprecat' | sed 's/:[0-9]*:[0-9]*//' | sort | uniq -c | sort -rn

# security / storage
grep -rn 'EncryptedSharedPreferences\|security-crypto\|EncryptedFile' --include='*.kt' --include='*.toml' . | grep -v build/
grep -rn 'getSharedPreferences\|SharedPreferences' --include='*.kt' . | grep -v build/
grep -rn 'MODE_WORLD_READABLE\|MODE_WORLD_WRITABLE' --include='*.kt' . | grep -v build/
grep -rn 'Uri.fromFile\|"file://"' --include='*.kt' . | grep -v build/
grep -rniE '"(MD5|SHA-1|DES|RC4)"' --include='*.kt' . | grep -v build/

# build configuration
grep -rn 'kotlinCompilerExtensionArgs\|enableStrongSkippingMode\|composeOptions' --include='*.gradle.kts' . | grep -v build/
grep -rn "proguard-android.txt" --include='*.gradle.kts' . | grep -v build/
grep -rn 'enableR8.fullMode' --include='*.properties' . | grep -v build/
ls buildSrc 2>/dev/null

# framework / lifecycle
grep -rn 'AndroidViewModel' --include='*.kt' . | grep -v build/
grep -rn 'onBackPressed\|KEYCODE_BACK' --include='*.kt' . | grep -v build/
grep -rn 'windowOptOutEdgeToEdgeEnforcement\|elegantTextHeight' --include='*.xml' . | grep -v build/
grep -rn 'BODY_SENSORS' --include='*.xml' --include='*.kt' . | grep -v build/
grep -rn 'GlobalScope' --include='*.kt' . | grep -v build/
grep -rn 'LiveData\|MutableLiveData' --include='*.kt' . | grep -v build/
grep -rn 'androidx.navigation\b' --include='*.kt' --include='*.toml' . | grep -v build/

# CMP
grep -rn 'runSkikoComposeUiTest\|runDesktopComposeUiTest' --include='*.kt' . | grep -v build/
grep -rn 'import androidx.compose.ui.test.runComposeUiTest' --include='*.kt' . | grep -v build/
grep -rn 'bindToNavigation\|CanvasBasedWindow' --include='*.kt' . | grep -v build/

# KMP targets
grep -rn 'macosX64()\|watchosX64()\|tvosX64()\|watchosArm32()\|iosX64()' --include='*.gradle.kts' . | grep -v build/

# libraries
grep -rn 'billing' --include='*.toml' --include='*.gradle.kts' . | grep -v build/
grep -rn 'retrofit' --include='*.gradle.kts' . | grep -v build/ | grep -i common
```

**MUST** produce an inventory: every finding, with occurrence count, files, replacement, and risk class (section 4.1). **MUST NOT** begin fixing during the audit.

---

## 4. Step-by-step procedure

### 4.1 Classify every finding by risk — MUST

This classification drives everything. Fixing a class 3 item as if it were class 1 is how a deprecation cleanup causes an outage.

| Class | Definition | Examples | Default action |
|---|---|---|---|
| **1 — Mechanical** | Behaviour-identical replacement. No user-visible change, no data change. | `composeOptions` → `composeCompiler {}`; `proguard-android.txt` → `-optimize`; non-`v2` CMP test imports → `v2`; deleting a no-op `enableStrongSkippingMode`; `Uri.fromFile` → `FileProvider` | **MAY** fix in this task |
| **2 — Behavioural** | Replacement changes observable behaviour or needs new code. | `onBackPressed` → `BackHandler`; `AndroidViewModel` → `ViewModel` + injection; `GlobalScope` → injected scope; `LiveData` → `StateFlow`; weak crypto primitives | **MUST** scope and approve per item |
| **3 — Data-affecting** | Touches persisted user data or credentials. Not reversible on-device. | `EncryptedSharedPreferences` → new store; `SharedPreferences` → DataStore; any storage migration | **MUST** be its own task. → `add-persistence.md` |
| **4 — Structural** | Changes the module graph, a library, or a public API. | Navigation 2 → Navigation 3; `buildSrc` → `build-logic`; Retrofit in `commonMain` → Ktor; removing a KMP target; Billing v7 → v8/v9 | **MUST** be its own project with its own plan |

**MUST NOT** mix classes in one change. **MUST NOT** fix a class 3 or 4 item inside this workflow — produce the plan and hand off.

### 4.2 Present the inventory and stop — MUST

Present, and wait for approval on which items to fix now:

- the inventory, grouped by class, ordered by risk within class
- for each item: occurrence count, replacement, blast radius, whether tests cover it
- which items are forced by a deadline (Play `targetSdk` floor, Billing floor)
- the recommended split: class 1 now, class 2 named individually, class 3/4 as separate tasks

**MUST NOT** proceed past this point without explicit selection. "Fix the warnings" is not selection.

### 4.3 Fix class 1 items — one class of finding per change

```kotlin
// CORRECT — mechanical, behaviour-identical
// before
android { composeOptions { kotlinCompilerExtensionArgs += ["...reportsDestination=..."] } }
// after
composeCompiler { reportsDestination = layout.buildDirectory.dir("compose_compiler") }
```

```kotlin
// CORRECT — a no-op on Kotlin 2.0.20+; delete it
// before
composeCompiler { enableStrongSkippingMode = true }
// after — removed
```

```kotlin
// CORRECT — mechanical test-import migration
// before
import androidx.compose.ui.test.runComposeUiTest
// after
import androidx.compose.ui.test.v2.runComposeUiTest
```

**MUST** verify after each finding-type, not once at the end. Attribution matters more than speed here.

### 4.4 Fix approved class 2 items

Each approved item gets: its own change, its own test, its own verification.

```kotlin
// CORRECT — onBackPressed → BackHandler, with the dead override deleted
// before
override fun onBackPressed() {
    if (isDrawerOpen) closeDrawer() else super.onBackPressed()
}
// after, in the composable
BackHandler(enabled = isDrawerOpen) { closeDrawer() }
```

```kotlin
// CORRECT — AndroidViewModel → ViewModel with the dependency injected
// before
class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    fun label() = getApplication<Application>().getString(R.string.title)
}
// after
class SettingsViewModel(private val strings: StringProvider) : ViewModel() {
    fun label() = strings.get(StringKey.Title)
}
```

```kotlin
// CORRECT — GlobalScope → injected scope
// before
fun bookmark(a: Article) { GlobalScope.launch { dataSource.bookmark(a) } }
// after
class ArticlesRepository(
    private val dataSource: ArticlesDataSource,
    private val externalScope: CoroutineScope,
) {
    suspend fun bookmark(a: Article) {
        externalScope.launch { dataSource.bookmark(a) }.join()
    }
}
```

**MUST** add or extend a test for every class 2 fix. A behavioural change with no test is an unverified behavioural change.

**MUST NOT** convert `LiveData` to `StateFlow` piecemeal across a shared type — half-migrated observability is worse than either state. Either the whole type or none of it.

### 4.5 Produce hand-off plans for class 3 and 4

**MUST** write a plan for each, not a fix:

| Field | Content |
|---|---|
| Item | the deprecated thing |
| Why it matters | deadline, security exposure, or maintenance |
| Scope | files, data, public API affected |
| Risk | what breaks for users if it goes wrong |
| Prerequisite | tests or schema export needed first |
| Suggested workflow | `add-persistence.md`, `upgrade-dependencies.md`, a dedicated project |
| Deadline | if any |

**MUST** flag `EncryptedSharedPreferences` as an active risk even if nothing is being done about it now: the library is unmaintained, and its reported failure modes are device-specific crashes. Note that `../references/deprecations.md` marks the replacement path `[UNVERIFIED]` — the plan must include verifying it.

### 4.6 Validate

Run section 7.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | Request is "**fix all deprecations**" | (a) audit, then fix class 1 only; (b) audit and fix a named selection; (c) audit only | **MUST** ask. Recommend (a). **MUST NOT** interpret the request as licence for class 2–4. |
| D2 | A class 2 item has **no test coverage** | (a) add a characterisation test first, then change; (b) skip the item | **MUST** ask. Recommend (a). Changing untested behaviour has no safety net. |
| D3 | `EncryptedSharedPreferences` **in use** | (a) plan the migration as a separate task; (b) leave and accept the risk, documented | **MUST** report either way. **MUST NOT** migrate credentials inside a deprecation-cleanup change. |
| D4 | `SharedPreferences` **in use** with persisted user data | — | **MUST** hand off to `add-persistence.md`. Data migration, not a deprecation fix. |
| D5 | **Navigation 2** in use | (a) plan Navigation 3 migration as a project; (b) stay — Nav 2 still works | **MUST** ask. **MUST NOT** begin a navigation migration here. Note that "maintenance mode" is `[UNVERIFIED]`. |
| D6 | **Billing ≤ v7** in use | — | **MUST** escalate immediately as a **publishing blocker**, not a deprecation. → `../references/integrations/subscriptions.md`. |
| D7 | Deprecated **KMP targets** declared (`macosX64`, `watchosX64`, `tvosX64`, `watchosArm32`) | (a) remove if unshipped; (b) keep — removing drops platform support | **MUST** ask. Removing a target is a product decision. |
| D8 | `iosX64` declared (Tier 3, not deprecated) | (a) keep for Intel-Mac simulators; (b) remove | **SHOULD** ask. Report the tier; do not remove unilaterally. |
| D9 | `buildSrc` in use | (a) plan a `build-logic` migration; (b) leave | **MUST** ask. Build-performance improvement, not a correctness fix. |
| D10 | Fixing an item would change a **public API** consumed by another module or Swift | — | **MUST** stop. → `review-ios-interop.md` for the Swift surface. |
| D11 | Deprecation originates in a **third-party library**, not this code | (a) upgrade the library via `upgrade-dependencies.md`; (b) suppress at the call site with a comment | **MUST** report. **MUST NOT** rewrite around a library's internal deprecation. |

---

## 6. Implementation rules

**MUST:**

1. Audit and classify before fixing anything.
2. Obtain explicit selection at 4.2.
3. Fix one finding-type per change, verified independently.
4. Add or extend a test for every class 2 fix.
5. Delete dead code the replacement orphans — a migrated `onBackPressed` override left in place is a trap for the next reader.
6. Record, for every item fixed, the reference entry that names the replacement.
7. Produce hand-off plans for class 3 and 4 items rather than fixes.

**MUST NOT:**

8. Fix a class 3 or 4 item inside this workflow.
9. Mix classes in one change.
10. Change behaviour as a side effect of a class 1 "mechanical" fix. If behaviour changes, it was misclassified — reclassify and re-approve.
11. Suppress a deprecation with `@Suppress`, `@file:Suppress`, or a lint baseline instead of fixing it — unless it originates in a third-party library (D11), with a comment naming the library and the reason.
12. Reformat, reorder, or re-lint untouched code in files you edit.
13. Migrate a shared observability type (`LiveData` → `StateFlow`) partially.
14. Remove a KMP target without approval.
15. Change dependency versions here. That is `upgrade-dependencies.md`.
16. Touch credentials, keys, or persisted data.

---

## 7. Validation requirements

| # | Check | Command | Required |
|---|---|---|---|
| V1 | Compiles — all targets | `./gradlew assembleDebug` (+ KMP native) | MUST |
| V2 | Full test suite passes | module test tasks / `allTests` | MUST |
| V3 | **Deprecation count decreased, and no new deprecation introduced** | compare the 4.x grep/compiler counts before and after | MUST |
| V4 | Release build with R8 | `./gradlew bundleRelease` | MUST if a build-config item was changed |
| V5 | New test per class 2 fix | review | MUST |
| V6 | Behaviour unchanged for class 1 fixes | existing tests pass with no assertion edits | MUST |
| V7 | No `@Suppress` or lint-baseline entry added | review the diff | MUST |
| V8 | Lint / detekt / ktlint | project tasks | MUST if configured |
| V9 | App launches; flows touched by class 2 fixes work | manual | MUST if any class 2 fix landed |
| V10 | iOS framework still links; exported surface unchanged | `linkDebugFrameworkIosSimulatorArm64` | MUST if KMP and a shared type changed |
| V11 | No test assertion modified | review the diff | MUST |

V11 matters here specifically: the failure mode of a deprecation cleanup is adjusting a test to match newly-wrong behaviour.

---

## 8. Failure handling

| Failure | Response |
|---|---|
| A class 1 fix changes behaviour | It was misclassified. **MUST** revert, reclassify as class 2, and re-approve at 4.2. |
| An existing test fails after a fix | **MUST** stop. Determine whether the fix is wrong or the test encoded the deprecated behaviour. **MUST NOT** edit the assertion to go green — report it and ask. |
| Deprecation count unchanged after the fix | The replacement is itself deprecated, or the usage moved rather than went away. **MUST** re-run the audit and report. |
| New deprecations appear | The chosen replacement is wrong. **MUST** re-read the reference entry and the library's migration guide. |
| Replacement API does not exist at the project's version | **MUST** stop. The fix requires a version upgrade → `upgrade-dependencies.md`. **MUST NOT** bump the version here. |
| Removing a deprecated API breaks a native target only | A platform-specific usage was missed. **MUST** fix per source set. |
| A deprecated usage is load-bearing with no replacement | **MUST** report it as an accepted-risk item with the reason, and leave it. Do not force a worse construct. |
| Deprecation is in generated code | **MUST NOT** edit generated code. Report the generator and its version. |
| Fix would alter a Swift-facing signature | → D10. **MUST** stop. |
| Audit finds a publishing blocker (Billing ≤ v7, `targetSdk` below floor) | **MUST** escalate immediately and separately. It outranks every other item in the inventory. |
| The working tree was dirty at the start | **MUST** stop per the standing contract — deprecation diffs are wide and easily confused with pre-existing changes. |

---

## 9. Completion criteria

**MUST** all hold:

1. Full inventory produced, every finding classified 1–4.
2. Explicit approval obtained at 4.2 for everything fixed.
3. Only approved items changed; no class 3 or 4 item fixed.
4. One finding-type per change.
5. Every class 2 fix has a test.
6. V1–V3, V5–V8, V11 `PASS`.
7. V4 `PASS` if a build-configuration item changed; V9 `PASS` if any class 2 fix landed; V10 `PASS` if a shared type changed.
8. Deprecation count strictly decreased; none introduced.
9. No test assertion modified, and no test skipped or deleted.
10. No suppression or lint-baseline entry added, except a D11 third-party case with a naming comment.
11. Hand-off plans written for every class 3 and 4 item, including the `EncryptedSharedPreferences` risk note if applicable.
12. Any publishing blocker escalated separately.

---

## 10. Final report format

Base skeleton from `README.md`, with these additions.

```markdown
## Remediate Deprecations — <scope fixed>

### Outcome
<DONE | DONE WITH CAVEATS | AUDIT ONLY | BLOCKED | NEEDS DECISION> — one sentence.

### Inspection findings
- Compiler-reported deprecation sites before: <n>
- Audit basis: `../references/deprecations.md`
- Working tree at start: <clean | dirty>
- Baseline build + tests: <PASS | FAIL>

### Inventory
| Item | Class | Occurrences | Files | Replacement | Status |
|---|---|---|---|---|---|
| `composeOptions` | 1 | 3 | `path`, ... | `composeCompiler {}` | FIXED |
| `onBackPressed` | 2 | 4 | `path:line` | `BackHandler` | FIXED |
| `EncryptedSharedPreferences` | 3 | 2 | `path` | DataStore + explicit crypto | **PLAN ONLY (D3)** |
| Navigation 2 | 4 | project-wide | — | Navigation 3 | **PLAN ONLY (D5)** |
| `iosX64` target | — | 1 | `path` | Tier 3 — reported, not changed | REPORTED |

### Publishing blockers found
<Billing ≤ v7, targetSdk below the Play floor, or similar. Escalate separately.
"None." if none.>

### Fixed in this change
| Item | Class | Behaviour change | Test added |
|---|---|---|---|

### Changes made
| File | Change |
|---|---|

### Deferred — hand-off plans
For each class 3 / 4 item:

**<Item>** — class <n>
- Why it matters:
- Scope:
- User-facing risk if mishandled:
- Prerequisite:
- Suggested workflow:
- Deadline:

### Accepted risks — left in place deliberately
| Item | Why no replacement was applied |
|---|---|

### Validation performed
| # | Check | Command | Result |
|---|---|---|---|
| V1 | Compile all targets | | |
| V2 | Full test suite | | |
| V3 | Deprecation count before → after | | |
| V4 | Release build with R8 | | |
| V5 | Test per class 2 fix | | |
| V6 | Class 1 behaviour unchanged | | |
| V7 | No suppressions added | | |
| V8 | Lint / detekt / ktlint | | |
| V9 | App launches, affected flows | | |
| V10 | iOS framework links | | |
| V11 | No test assertion modified | | |

### Deprecation count
| | Before | After |
|---|---|---|
| Compiler-reported sites | | |
| Reference-set findings | | |

### Not done
### Observations
### Decisions needed
```
