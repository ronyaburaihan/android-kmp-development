# Workflow: Backfill Tests

Standing contract: `README.md`. Prerequisite: `inspect-project.md`.

---

## 1. Objective

Add tests to existing untested code **without changing its behaviour**.

The deliverable is a safety net that pins current behaviour, so a later change can be made safely. The hardest constraint: **current behaviour may be wrong, and the tests must still pin it** — then report the suspected defect separately.

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| What to cover — module, class, or flow | yes | **MUST** ask. "Add tests" over a whole app is not a scope. |
| Why now — refactor coming, incident, coverage target | yes | **MUST** ask. A pre-refactor net differs from general coverage. |
| Is the current behaviour authoritative? | yes | **MUST** ask. Determines whether a surprising result is pinned or reported. |
| Coverage target | no | Default: cover behaviour, not lines. State what was covered. |
| May the code be changed to make it testable? | yes | Default: **no**. → D1. |

**MUST** establish the "why". A net for an imminent refactor should cover the seams that refactor will cross; a general-coverage task should start with the highest-risk logic.

---

## 3. Initial project inspection

Run `inspect-project.md`, then inspect the target code for **testability**, which determines the cost.

```bash
TARGET=<path>

# hard dependencies that block testing
grep -n 'Dispatchers\.' $TARGET
grep -nE '^import (android|androidx|java\.io|javax)' $TARGET
grep -n 'object \|companion object' $TARGET
grep -n 'System.currentTimeMillis\|Clock.System\|Date()\|UUID.randomUUID' $TARGET
grep -n 'GlobalScope\|runBlocking' $TARGET
grep -nE 'class [A-Za-z]+(\(|\s*:)' $TARGET     # constructor shape

# existing test style to match
find . -name '*Test.kt' -not -path '*/build/*' | head -5
find . -name 'Fake*.kt' -not -path '*/build/*' | head -5
```

**MUST** produce a testability ledger:

| Obstacle | Found at | Blocks | Resolution |
|---|---|---|---|
| Hardcoded `Dispatchers.IO` | `path:line` | determinism | → D1: inject, or use `Dispatchers.setMain` |
| `Context` dependency | `path:line` | host-JVM testing | → D1: interface, or test on device |
| `System.currentTimeMillis()` | `path:line` | time-dependent assertions | → D1: inject a clock, or assert ranges |
| `object` singleton | `path:line` | isolation | → D1: cannot be substituted |
| `UUID.randomUUID()` | `path:line` | deterministic assertions | assert shape, not value |

**MUST** record where the tests will live, based on the project's existing source sets: `commonTest` for shared logic, `androidHostTest` for Android-specific host-testable logic, `androidDeviceTest` for instrumented, `iosTest` for iOS actuals.

**MUST** read two existing tests near the target and match their style — assertion library, fake style, naming, structure.

---

## 4. Step-by-step procedure

### 4.1 Prioritise by risk, not by coverage percentage — MUST

| Priority | Target | Why |
|---|---|---|
| 1 | Business rules, calculations, validation, state machines | wrong answers reach users; cheapest to test |
| 2 | Error and edge paths in repositories | where production failures live and coverage is thinnest |
| 3 | Mappers (DTO ↔ entity ↔ domain) | field-mismatch bugs; pure functions, trivial to test |
| 4 | ViewModel state production | the behaviour the UI depends on |
| 5 | Caching / offline-first policy | ordering bugs that only appear under failure |
| 6 | UI rendering per state | catches crashes in rarely-seen states |
| 7 | Navigation | regression value, higher cost |

**MUST NOT** chase a line-coverage number. Covering getters and `toString()` raises the percentage and catches nothing.

**MUST** state what was covered in behavioural terms.

### 4.2 Confirm the plan — MUST

Present and wait:

- the prioritised list of behaviours to cover
- where the tests will live
- the testability ledger, with any production change needed (→ D1)
- whether surprising behaviour will be pinned or reported (from input 3)

### 4.3 Write characterisation tests — pin actual behaviour

A characterisation test documents what the code **does**, not what it should do.

```kotlin
// CORRECT — pins the observed behaviour, including the surprising part
class PriceCalculatorTest {

    @Test
    fun appliesVolumeDiscountFromTenUnits() {
        assertEquals(Money("90.00"), calculate(units = 10, unit = Money("10.00")))
    }

    // Behaviour is surprising but current. Pinned deliberately; see the report.
    @Test
    fun negativeUnitsReturnZeroRatherThanThrowing() {
        assertEquals(Money("0.00"), calculate(units = -1, unit = Money("10.00")))
    }
}
```

**MUST**, when a test reveals surprising behaviour:

1. pin the actual behaviour,
2. add a comment saying the behaviour is suspected-wrong and pinned deliberately,
3. report it under **Suspected defects** — do **not** fix it here.

**MUST NOT** write the test to the behaviour you think is correct and leave it failing. A failing test in a backfill task is indistinguishable from a broken build.

**MUST NOT** fix the behaviour. Fixing while backfilling means neither the test nor the fix is reviewable. → `diagnose-and-fix-bug.md`.

### 4.4 Determine behaviour empirically where it is unclear

**MUST** determine the actual result by running the code, not by reading it and inferring.

```kotlin
// Determine, then assert. Reading the implementation and guessing produces
// a test that encodes the reader's misunderstanding.
@Test
fun emptyInputBehaviour() {
    val result = runCatching { calculate(units = 0, unit = Money("10.00")) }
    assertEquals(Money("0.00"), result.getOrThrow())   // written after observing
}
```

### 4.5 Build fakes in the project's existing style

```kotlin
// CORRECT — a fake: real implementation, simplified, controllable
internal class FakeArticleDao : ArticleDao {
    private val rows = MutableStateFlow<List<ArticleEntity>>(emptyList())
    var upsertCount = 0
        private set

    override suspend fun upsertAll(items: List<ArticleEntity>) {
        upsertCount++
        rows.update { existing -> (items + existing).distinctBy(ArticleEntity::id) }
    }
    override fun observeAll(): Flow<List<ArticleEntity>> = rows

    fun seed(items: List<ArticleEntity>) { rows.value = items }
}
```

```kotlin
// WRONG — asserts how the subject works, not what it produces
verify(dao, times(1)).upsertAll(any())
```

**MUST** place reusable fakes where other modules can consume them — `commonMain` of a shared testing module, not `commonTest` of one module. **MUST** match the project's existing location if one exists.

**MUST NOT** introduce a mocking framework the project does not already use. In `commonTest` most are unavailable — they will not compile for native.

### 4.6 Cover the paths that production actually exercises

**MUST** include, for each covered unit:

| Path | Why |
|---|---|
| Happy path | baseline |
| Each error branch | where production failures live |
| Boundary values (0, 1, max, empty, null) | off-by-one and null-handling |
| Cancellation, for suspend functions | `CancellationException` must propagate |
| Concurrent/ordering behaviour, where state is shared | races that only appear under load |

```kotlin
// CORRECT — pins that cancellation propagates rather than being swallowed
@Test
fun refreshPropagatesCancellation() = runTest {
    val repository = ArticlesRepository(HangingDataSource(), UnconfinedTestDispatcher(testScheduler))
    val job = launch { repository.refresh() }
    job.cancelAndJoin()
    assertTrue(job.isCancelled)
}
```

### 4.7 Make the tests deterministic

**MUST NOT** add `Thread.sleep`, a real `delay`, or a retry to stabilise a test. A test that needs one is testing non-deterministic code — report that as a finding.

**MUST** use `runTest` with an injected `TestDispatcher`. If the code hardcodes a dispatcher → D1.

**MUST** subscribe to any `StateFlow` created with `stateIn(..., WhileSubscribed(), ...)`. Reading `.value` without a collector sees the initial value forever — a test that passes while asserting nothing.

### 4.8 Validate

Run section 7.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | Code is **untestable as written** (hardcoded dispatcher, `Context`, singleton, direct clock) | (a) minimal testability change — inject the dependency, no behaviour change; (b) test at a higher level (instrumented); (c) skip | **MUST** ask. Recommend (a), and **MUST** verify behaviour is unchanged. |
| D2 | A test reveals **surprising behaviour** | (a) pin it, report as a suspected defect; (b) stop and fix first | **MUST** default to (a). Fixing in a backfill task makes neither change reviewable. |
| D3 | Behaviour is **non-deterministic** (depends on timing, ordering, or real time) | (a) pin the invariant only; (b) report as untestable-as-written | **MUST** report. **MUST NOT** add sleeps or retries. |
| D4 | Coverage requires a **new test dependency** (Turbine, Robolectric, a screenshot tool) | (a) add it; (b) work within what exists | **MUST** ask. Prefer (b) in `commonTest`, where many libraries are unavailable. |
| D5 | Target code has **no seams** — one large function with inline dependencies | (a) extract seams (a refactor); (b) test only through the public surface; (c) skip | **MUST** ask. Recommend (b) first — it needs no production change. |
| D6 | A behaviour **cannot be reached** from the public surface | (a) make it `internal` and use the project's internal-test visibility; (b) accept it as uncovered | **SHOULD** prefer (a) if the project already does that. **MUST NOT** widen visibility to `public`. |
| D7 | Target code **requires a device** (platform APIs throughout) | (a) instrumented tests; (b) extract the logic first | **MUST** ask. (b) is a refactor. |
| D8 | Existing test for this code **asserts wrong behaviour** | — | **MUST** report. **MUST NOT** change it inside this task. |
| D9 | Test reveals a **security-relevant** defect | — | **MUST** escalate immediately. → `audit-security.md`. |
| D10 | KMP: logic sits in a platform source set but is platform-independent | (a) report that moving it to `commonMain` would make it testable everywhere; (b) test it per platform | **MUST NOT** move it here — that is `migrate-module-to-kmp.md`. Report and test in place. |

---

## 6. Implementation rules

**MUST:**

1. Pin actual behaviour; report suspected defects separately.
2. Determine behaviour empirically where it is unclear.
3. Match the project's existing test style, assertion library, and fake style.
4. Place tests in the correct source set — `commonTest` for shared logic.
5. Use `kotlin-test` only in `commonTest`.
6. Use `runTest` with an injected `TestDispatcher`.
7. Cover error branches, boundaries, and cancellation, not only happy paths.
8. Put reusable fakes where other modules can consume them.
9. Name tests for the behaviour asserted, not the method called.
10. Comment any deliberately pinned suspicious behaviour.

**MUST NOT:**

11. Change production behaviour. Only an approved D1 testability change, verified behaviour-neutral.
12. Fix a defect discovered while testing.
13. Leave a failing test in the delivered change.
14. Add `Thread.sleep`, a real `delay`, or a retry.
15. Introduce a mocking framework the project does not use, or any mocking framework into `commonTest`.
16. Assert on call counts or invocation order where an output assertion is possible.
17. Widen a declaration to `public` for testability.
18. Modify or delete an existing test — except reporting a D8 wrong assertion.
19. Write tests that assert on implementation details (private state, internal call sequences).
20. Chase line coverage with tests of getters, `toString`, or generated code.
21. Reformat or re-lint the code under test.

---

## 7. Validation requirements

| # | Check | How | Required |
|---|---|---|---|
| V1 | All new tests pass | `./gradlew :<module>:allTests` | MUST |
| V2 | Tests run on **every** target they live in | `allTests`, confirming iOS/native execution | MUST if KMP |
| V3 | **Production code unchanged**, or only an approved D1 change | `git diff` on non-test files | MUST |
| V4 | Existing tests still pass | whole-module test task | MUST |
| V5 | Tests **fail when the behaviour is broken** — mutation check | deliberately break the code, confirm a red test, revert | MUST |
| V6 | No `Thread.sleep`, real `delay`, or retry in the new tests | grep the new tests | MUST |
| V7 | No flakiness across repeated runs | run the new tests 5+ times | MUST |
| V8 | Compiles on all targets | `assembleDebug` + KMP native | MUST |
| V9 | Lint / detekt / ktlint on test sources | project tasks | MUST if configured |
| V10 | `stateIn(WhileSubscribed())` flows are collected, not read via `.value` | review | MUST where applicable |
| V11 | Behaviour unchanged after a D1 testability change | existing tests pass; manual check of the affected flow | MUST if D1 applied |
| V12 | Coverage reported in behavioural terms | review the report | MUST |

V5 is the check that distinguishes a real net from tests that pass regardless. **MUST NOT** report a backfill as done without it.

---

## 8. Failure handling

| Failure | Response |
|---|---|
| Code is untestable as written | → D1. **MUST NOT** refactor beyond the approved minimal change. |
| A test reveals a defect | → D2. Pin, comment, report. **MUST NOT** fix. |
| Cannot determine the intended behaviour | **MUST** pin the actual behaviour and report the ambiguity. Do not invent a specification. |
| A new test is flaky | **MUST** find the cause — usually a hardcoded dispatcher, a shared fixture, or an uncollected flow. **MUST NOT** add a sleep or a retry. |
| A test passes even when the code is broken (V5 fails) | The test asserts nothing meaningful. **MUST** rewrite it. |
| `commonTest` will not compile | A JVM-only test library reached the common source set. **MUST** move the test to a platform source set or replace the library. |
| Native target test run fails but JVM passes | Platform behaviour differs — time zone, locale, string comparison, floating point. **MUST** report; this is a genuine finding. |
| An existing test breaks | **MUST** stop. A backfill should not affect existing tests; if it does, a D1 change altered behaviour. |
| Coverage target cannot be met without testing trivia | **MUST** report the behavioural coverage achieved and decline to inflate the number. |
| Target code is generated | **MUST NOT** test generated code. Test its inputs and its consumers. |
| Test reveals a security issue | → D9. Escalate; **MUST NOT** include exploit detail. |
| Scope too large | **MUST** cover the highest-priority behaviours fully and state exactly what is uncovered. Partial coverage honestly reported beats thin coverage everywhere. |

---

## 9. Completion criteria

**MUST** all hold:

1. Prioritised plan confirmed at 4.2, covering behaviours rather than lines.
2. Tests pin **actual** behaviour; every surprising result commented and reported.
3. Production code unchanged, or only an approved D1 change verified behaviour-neutral (V3, V11).
4. Error branches, boundaries, and cancellation covered for each unit.
5. Tests in the correct source sets; `commonTest` uses `kotlin-test` only.
6. Fakes in the project's style, placed where consumers can reach them.
7. V1–V10, V12 `PASS`.
8. V5 mutation check performed and reported.
9. No failing test left in the change.
10. No defect fixed, and no existing test modified or deleted.
11. Suspected defects reported separately with enough detail to open a bug.

---

## 10. Final report format

Base skeleton from `README.md`, with these additions.

```markdown
## Backfill Tests — <target>

### Outcome
<DONE | DONE WITH CAVEATS | BLOCKED | NEEDS DECISION> — one sentence.

### Scope and purpose
| | |
|---|---|
| Target | `path` |
| Why now | <pre-refactor net / incident / coverage> |
| Current behaviour authoritative | <yes — pinned as is / no — defects reported> |

### Inspection findings
- Test style matched from: `path`, `path`
- Fake style / location: `path`
- Source sets used: <commonTest | androidHostTest | ...>
- Assertion library: <kotlin-test | ...>

### Testability ledger
| Obstacle | Location | Resolution | Production change? |
|---|---|---|---|
| hardcoded `Dispatchers.IO` | `path:line` | injected dispatcher (D1) | **yes — behaviour-neutral** |
| `System.currentTimeMillis()` | `path:line` | asserted a range instead | no |

### Behaviours covered
| Priority | Behaviour | Test | Source set |
|---|---|---|---|
| 1 | volume discount from 10 units | `path::name` | commonTest |
| 2 | network failure surfaces as `NetworkException` | `path::name` | commonTest |

### Behaviours deliberately not covered
| Behaviour | Why |
|---|---|
| | <non-deterministic (D3) / requires a device (D7) / no seam (D5)> |

### Suspected defects — pinned, not fixed
| # | Behaviour pinned | Why it looks wrong | Test that pins it | Suggested next step |
|---|---|---|---|---|
| 1 | negative units return 0 | likely should reject the input | `path::name` | `diagnose-and-fix-bug.md` |

<"None." if none.>

### Changes made
| File | Change |
|---|---|
| `path` | created — <n> tests |
| `path:line` | **production** — D1 testability change: <what>, behaviour-neutral |

### Validation performed
| # | Check | Command | Result |
|---|---|---|---|
| V1 | New tests pass | | |
| V2 | Run on every target | | |
| V3 | **Production code unchanged** | | |
| V4 | Existing tests pass | | |
| V5 | **Mutation check — tests fail when code is broken** | | |
| V6 | No sleeps / retries | | |
| V7 | No flakiness over 5+ runs | | |
| V8 | Compiles all targets | | |
| V9 | Lint / detekt / ktlint | | |
| V10 | `WhileSubscribed` flows collected | | |
| V11 | Behaviour unchanged after D1 | | |
| V12 | Coverage stated behaviourally | | |

### Mutation check detail
| Code deliberately broken | Test that went red |
|---|---|

### Not done
### Observations
### Decisions needed
```
