# Workflow: Diagnose and Fix a Bug

Standing contract: `README.md`. Prerequisite: `inspect-project.md`.

**Primitives:** `../process/diagnostic-loop.md` — **mandatory gate** at step 4.1, `../process/tdd.md` (the regression test is the red step).

---

## 1. Objective

Reproduce a reported defect, locate its cause, fix the cause, and pin the fix with a regression test.

**The order is mandatory: reproduce → locate → test → fix → verify.** A fix written before a reproduction is a guess, and a fix without a failing test first cannot be shown to work.

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| Observed behaviour | yes | **MUST** ask. |
| Expected behaviour | yes | **MUST** ask. Without it there is no definition of fixed. |
| Reproduction steps | no | **MUST** attempt derivation; if it cannot be reproduced → D1. |
| Stack trace / error text, verbatim | no | **SHOULD** ask. A paraphrased trace is not usable. |
| Affected platform(s), OS version, device | no | Default: assume all; narrow during reproduction. |
| Affected app version; when it started | no | **SHOULD** ask — enables `git bisect`. |
| Frequency and user impact | no | Affects whether a workaround is acceptable. |

**MUST NOT** begin changing code from a description alone.

---

## 3. Initial project inspection

Run `inspect-project.md`, then scope to the defect.

```bash
# the exact frames from the trace
grep -rn '<ClassFromTrace>' --include='*.kt' . | grep -v build/

# recent history in the suspect area
git log --oneline -20 -- <suspect-path>

# when it worked before, find the change
git log --oneline <lastGoodTag>..HEAD -- <suspect-path>
```

**MUST** establish a green baseline before changing anything: the existing suite passes, except any test that already fails for this defect.

**MUST** record whether existing tests cover the defective path. A defect in covered code means the test asserts the wrong thing — a separate finding.

---

## 4. Step-by-step procedure

### 4.1 Build the feedback loop — MUST, before any theory and before any code change

Run `../process/diagnostic-loop.md`. This step **is** that primitive; the rules below are its
summary, not a substitute for it.

**MUST NOT** form a hypothesis, and **MUST NOT** read source looking for a cause, until a
red-capable command exists. Jumping to a theory first is the failure this step prevents.

Reproduce at the cheapest level that shows the defect:

| Level | When |
|---|---|
| A failing unit test in `commonTest` | logic, mapping, state production — always try first |
| Turbine on the emitting `Flow` | wrong state, wrong order, missing emission |
| `MockEngine` replaying the exact payload | triggered by a specific server response |
| Real in-memory Room DB, seeded | query, constraint, converter, or migration defect |
| The same `commonTest` on `iosSimulatorArm64` | works on Android, fails on iOS — the divergence *is* the finding |
| A failing Compose UI test | rendering, interaction, state-to-UI wiring |
| Macrobenchmark | jank, startup, scroll. Numbers, never impressions |
| Instrumented / on-device | platform behaviour, permissions, process death, lifecycle, insets |
| `bundleRelease` and install | "only in release" — R8 |
| Manual on device | only when no automated level can express it |

Full ranked list with costs: `../process/diagnostic-loop.md` § Loop constructions.

The loop is ready only when **all four** hold — **red-capable** (fails on *this* bug, not a
neighbour), **deterministic** (same verdict every run; pin time, seed RNG, fix the locale),
**fast** (seconds), **agent-runnable** (no human tapping a screen).

**MUST NOT** proceed to a fix without a reproduction, or without an approved D1.

**MUST** record the exact reproduction: command, test name, or step sequence — verbatim, so anyone
can re-run it.

### 4.2 Locate the cause, not the symptom — MUST

**MUST** distinguish the two, explicitly.

```kotlin
// SYMPTOM — the crash site
val article = articles[index]        // IndexOutOfBoundsException

// CAUSE — the list and the index come from two separate flows that emit
// independently, so the UI can render an index from a stale list
```

**MUST** be able to state: *this input, through this path, produces this wrong output, because of this.* If the cause cannot be stated that way, it is not yet located.

**SHOULD** use `git bisect` when the defect is a regression and the last-good version is known.

**MUST NOT** fix at the symptom site when the cause is upstream. A null check at the crash site leaves the inconsistent state in place.

### 4.3 Write the failing regression test — MUST, before the fix

**MUST** write a test that fails **because of the defect**, for the reason identified in 4.2, and confirm it fails before the fix exists.

```kotlin
// Written BEFORE the fix. MUST fail now, for the cause identified in 4.2.
@Test
fun selectionIsClearedWhenTheListChanges() = runTest {
    val viewModel = FeedViewModel(repository)
    viewModel.uiState.test {
        repository.emit(listOf(a, b, c))
        awaitItem()
        viewModel.onSelect(index = 2)
        awaitItem()

        repository.emit(listOf(a))          // list shrinks
        val state = awaitItem()

        assertNull(state.selectedId)        // fails before the fix
        cancelAndIgnoreRemainingEvents()
    }
}
```

**MUST** place the test at the level that matches the cause — a state-consistency bug belongs in a ViewModel test, not a UI test.

**MUST NOT** write a test that passes before the fix. Such a test proves nothing and will not catch a regression.

### 4.4 Fix the cause — minimally

**MUST** make the smallest change that addresses the cause.

```kotlin
// CORRECT — one source of truth; selection cannot outlive the list
val uiState: StateFlow<FeedUiState> =
    combine(repository.observeFeed(), selectedId) { articles, selected ->
        FeedUiState(
            articles = articles,
            selectedId = selected?.takeIf { id -> articles.any { it.id == id } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())
```

```kotlin
// WRONG — symptom suppression; the inconsistent state remains
val article = articles.getOrNull(index) ?: return
```

**MUST NOT**, while fixing:

- refactor surrounding code
- rename anything not required by the fix
- reformat untouched lines
- fix other defects noticed in passing — those go in **Observations**

If the minimal fix is unacceptable (it papers over a design flaw), **MUST** report that and present the larger option at D4 rather than performing it unasked.

### 4.5 Verify the test now passes, and nothing else broke

```bash
./gradlew :<module>:allTests --tests '*<RegressionTest>*'   # now passes
./gradlew :<module>:allTests                                 # nothing regressed
```

**MUST** re-run the original reproduction from 4.1 — including the manual one, if that was the only level that showed it.

### 4.6 Check for siblings — SHOULD

**MUST** search for the same cause elsewhere.

```bash
grep -rn '<the-faulty-pattern>' --include='*.kt' . | grep -v build/
```

**MUST** report siblings. **MUST NOT** fix them in this change without approval (→ D5) — a bug fix that touches six files is no longer reviewable as a bug fix.

### 4.7 Validate

Run section 7.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | **Cannot reproduce** | (a) get more information — device, OS, version, account state; (b) fix defensively from the trace; (c) add logging and close pending data | **MUST** ask. **MUST NOT** silently choose (b). A defensive fix to an unreproduced defect is unverifiable. |
| D2 | Defect is in **covered** code (tests pass but behaviour is wrong) | (a) fix the code and correct the test; (b) investigate why the test asserted wrong | **MUST** report. The wrong assertion is a second finding. |
| D3 | Cause is in a **third-party library** | (a) upgrade it; (b) work around it, with a comment naming the cause; (c) report upstream | **MUST** ask. **MUST NOT** fork or patch a library silently. |
| D4 | Minimal fix **papers over a design flaw** | (a) minimal fix now + a reported follow-up; (b) the larger change now | **MUST** ask. Recommend (a) unless the flaw will re-manifest immediately. |
| D5 | The same cause exists **elsewhere** | (a) fix all now; (b) fix the reported one, report the rest | **MUST** ask. Recommend (b) to keep the change reviewable. |
| D6 | Fix requires a **data migration** | — | **MUST** stop. → `add-persistence.md`. |
| D7 | Fix changes an **exported Swift API** | — | **MUST** stop. → `review-ios-interop.md`. |
| D8 | Fix requires a **version upgrade** | — | **MUST** stop. → `upgrade-dependencies.md`. |
| D9 | Behaviour is **correct**; the expectation is wrong | — | **MUST** report that, with evidence. Do not change correct code to match a wrong expectation. |
| D10 | Defect only reproduces on **one platform** in a KMP project | (a) fix in the platform source set; (b) fix in common if the cause is shared | **MUST** determine which. A common-code fix for a platform-specific cause changes behaviour on platforms that were fine. |
| D11 | Defect is **security-relevant** (data exposure, auth bypass, injection) | — | **MUST** flag it as such immediately and **MUST NOT** include exploit detail in the report. → `audit-security.md`. |

---

## 6. Implementation rules

**MUST:**

1. Reproduce before fixing.
2. Write a test that fails for the identified cause, before the fix.
3. Confirm the test fails pre-fix and passes post-fix. Report both observations.
4. Fix the cause, minimally.
5. Re-run the original reproduction.
6. Place the test at the level matching the cause.
7. Search for siblings and report them.
8. State the cause in the form: input → path → wrong output → why.

**MUST NOT:**

9. Change code before reproducing (unless D1 is approved).
10. Write a test that already passes.
11. Fix the symptom when the cause is upstream.
12. Refactor, rename, or reformat beyond the fix.
13. Modify, skip, or delete an existing test to make the suite green — unless it asserted wrong behaviour, which is D2 and **MUST** be reported.
14. Add `try`/`catch`, `?:`, `getOrNull`, or a null check purely to stop a crash whose cause is unaddressed.
15. Add a `delay`, retry, or `Thread.sleep` to fix a race.
16. Suppress a warning or lint error instead of fixing the cause.
17. Fix sibling occurrences without approval.
18. Leave debug logging in the fix.

---

## 7. Validation requirements

| # | Check | Command | Required |
|---|---|---|---|
| V1 | **Regression test failed before the fix** | run it on the pre-fix code; record the failure message | MUST |
| V2 | **Regression test passes after the fix** | `./gradlew :<module>:allTests --tests '*<Name>*'` | MUST |
| V3 | Original reproduction no longer reproduces | repeat the 4.1 steps exactly | MUST |
| V4 | Full module test suite passes | module test task | MUST |
| V5 | Compiles on all targets | `assembleDebug` + KMP metadata/native | MUST |
| V6 | No existing test modified or skipped | review the diff | MUST |
| V7 | Diff contains only the fix and its test | review the diff | MUST |
| V8 | Lint / detekt / ktlint | project tasks | MUST if configured |
| V9 | Release build with R8 | `bundleRelease` | SHOULD; MUST if the defect only manifested in release |
| V10 | Siblings searched and reported | the 4.6 grep | MUST |
| V11 | Manual verification on the affected device/OS | manual | MUST if the defect was platform-specific |
| V12 | No debug logging left | review the diff | MUST |

V1 is the check that separates a verified fix from a hopeful one. **MUST NOT** report a fix as done without it.

---

## 8. Failure handling

| Failure | Response |
|---|---|
| Cannot reproduce after reasonable effort | → D1. **MUST NOT** apply a speculative fix and call it done. |
| Reproduces only on a device you do not have | **MUST** say so. Narrow by OS/API level and report what is needed. Mark V3/V11 `NOT RUN`. |
| Reproduces intermittently | Likely a race or an uncollected flow. **MUST** find the ordering dependency. **MUST NOT** add a delay or a retry. |
| Regression test passes before the fix | The test does not capture the cause. **MUST** rewrite it; the cause may also be misidentified. |
| Fix makes the regression test pass but breaks another test | The cause was misidentified, or the other test encoded the defective behaviour. **MUST** determine which and report — do not edit either test to go green. |
| Fix works in debug, not in release | R8. **MUST** fix the keep rule; **MUST NOT** disable minification. |
| Cause is in generated code | **MUST NOT** edit generated code. Report the generator, its version, and the input that produced it. |
| Cause is a platform/OS defect | **MUST** report with the OS versions affected, and propose a guarded workaround with a comment naming the platform issue. |
| Fix would be large | → D4. **MUST NOT** begin a large refactor under a bug-fix label. |
| Multiple independent defects found | **MUST** fix the reported one and report the others. One defect per change. |
| Behaviour turns out to be correct | → D9. **MUST** report with evidence rather than changing working code. |
| Defect is security-relevant | → D11. **MUST** escalate; **MUST NOT** publish exploit detail. |

---

## 9. Completion criteria

**MUST** all hold:

1. Defect reproduced, with the reproduction recorded — or D1 approved and the limitation stated.
2. Cause stated as input → path → wrong output → why, and distinguished from the symptom.
3. Regression test written **before** the fix, observed failing (V1), now passing (V2).
4. Fix addresses the cause, minimally.
5. Original reproduction no longer reproduces (V3).
6. V4–V8, V10, V12 `PASS`.
7. V9 `PASS` if the defect was release-only; V11 `PASS` if it was platform-specific.
8. No existing test modified, skipped, or deleted — except a D2 wrong assertion, reported.
9. Diff contains only the fix and its test.
10. Siblings searched, reported, and not fixed without approval.
11. No defensive suppression, delay, retry, or lint suppression used as the fix.

---

## 10. Final report format

Base skeleton from `README.md`, with these additions.

```markdown
## Fix Bug — <one-line defect summary>

### Outcome
<FIXED | FIXED WITH CAVEATS | NOT REPRODUCED | BLOCKED | NEEDS DECISION | NOT A DEFECT> — one sentence.

### Defect
| | |
|---|---|
| Reported behaviour | |
| Expected behaviour | |
| Platforms affected | |
| First broken in | <version / commit, or unknown> |

### Reproduction
- Level: <commonTest | Compose test | instrumented | manual>
- Exact reproduction: `<command or step sequence>`
- Pre-fix failure output:
```
<verbatim>
```

### Cause
**Symptom:** `path:line` — <what the user sees>

**Cause:** `path:line` — <input> → <path> → <wrong output>, because <why>.

**Why the symptom site is not the cause:** <one sentence>

### Fix
<What changed and why this is the minimal change addressing the cause.>

### Changes made
| File | Change |
|---|---|

### Regression test
| | |
|---|---|
| Test | `path::name` |
| Level | |
| Failed before the fix | **yes** — <message> |
| Passes after the fix | yes |
| What a future regression it catches | |

### Validation performed
| # | Check | Command | Result |
|---|---|---|---|
| V1 | **Test failed pre-fix** | | |
| V2 | Test passes post-fix | | |
| V3 | Original reproduction gone | | |
| V4 | Full module suite | | |
| V5 | Compiles all targets | | |
| V6 | No existing test modified | | |
| V7 | Diff is fix + test only | | |
| V8 | Lint / detekt / ktlint | | |
| V9 | Release build with R8 | | |
| V10 | Siblings searched | | |
| V11 | Manual on affected device | | |
| V12 | No debug logging left | | |

### Sibling occurrences — found, not fixed
| Location | Same cause? | Recommendation |
|---|---|---|

<"None found." if none.>

### Not done
### Observations
### Decisions needed
```
