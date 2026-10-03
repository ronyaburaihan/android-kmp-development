# Primitive: Test-Driven Development

Adapted from Matt Pocock's `tdd` skill — see `README.md` § Attribution. The seam table, tooling and
Gradle mechanics below are specific to this toolchain.

## Purpose

Red before green, at **pre-agreed seams**. Produces code that works, and tests that survive the next
refactor.

The failure this prevents: code that compiles, type-checks, matches every convention in
`../references/`, and does not do the thing.

---

## When to invoke

- `../workflows/add-feature.md` — at the seams the plan agreed.
- `../workflows/add-network-endpoint.md` — mapper and data source first.
- `../workflows/add-persistence.md` — DAO and migration first.
- `../workflows/diagnose-and-fix-bug.md` § 4.3 — the regression test **is** the red step.

**MUST NOT** invoke before `../workflows/inspect-project.md`. Writing a test before knowing the
project's test framework, source-set layout and existing fakes produces a test that does not belong.

**If the project has no test infrastructure at the chosen seam**, that is a decision point, not a
licence to skip: stop, and either add the harness as agreed scope or run
`../workflows/backfill-tests.md` first.

---

## What a good test is

A test examines **behaviour through a public boundary**. Implementation can change entirely; the test
should not. A test that breaks when you refactor without changing behaviour was testing the wrong
thing.

---

## Seams: where tests go — the KMP map

A **seam** is a public boundary where behaviour is observable without inspecting internals. Testing
capacity is finite. **MUST** agree the seams before writing tests, so effort lands on the logic that
can actually be wrong.

| Seam | What you assert | Tool | Verified example |
|---|---|---|---|
| **Use case** | Inputs → emitted domain results, including boundaries | Turbine | `../examples/user-profile/src/commonTest/kotlin/com/example/userprofile/domain/ObserveUserProfileUseCaseTest.kt` |
| **Repository interface** | Error translation, cancellation propagation, cache-vs-network precedence | fakes for data sources, Turbine | `DefaultUserRepositoryTest.kt` |
| **Mapper (DTO ↔ domain ↔ entity)** | Unknown fields, absent fields, bad enums | plain assertions, no framework | `UserMappersTest.kt` |
| **ViewModel / presenter** | `uiState` sequence for each intent | Turbine + `StandardTestDispatcher` | `UserViewModelTest.kt`, `UserProfilePresenterTest.kt` |
| **DAO** | Queries and constraints against a **real in-memory database** | Room in-memory builder | `UserRowDaoTest.kt` |
| **Migration** | Seeded row at version N survives to N+1 | `MigrationTestHelper` + exported schemas | `AppDatabaseMigrationTest.kt` |
| **HTTP layer** | The real client configuration — serialization, auth, error mapping | Ktor `MockEngine` | `KtorUserRemoteDataSourceTest.kt` |
| **Settings / DataStore** | Read-write round trip, defaults | real DataStore over Okio `FakeFileSystem` | `DataStoreSettingsRepositoryTest.kt` |
| **Compose screen** | State → what renders; interaction → which intent | `runComposeUiTest` in `commonTest` | `UserProfileScreenTest.kt` |
| **DI graph** | Every binding resolves; scopes correct | Koin's graph check | `KoinGraphTest.kt` |
| **`expect`/`actual`** | The contract holds on every target | `commonTest` runs on all targets | `PlatformTest.kt` |

### Not a seam — MUST NOT test here

- Private or `internal` functions reached by widening visibility for the test.
- A collaborator *inside* the unit under test. Mocking a DAO in a repository test couples the test to
  the repository's internals; use a real in-memory database, or fake the data-source **interface**.
- Compose internals, recomposition counts, Koin module internals.
- Anything reached through a side channel added only so the test could see it.

The boundary that matters: faking the `UserRepository` **interface** in a ViewModel test is correct —
it is that unit's seam. Faking the Room DAO inside a repository test is implementation-coupled.

---

## Rules of the loop — MUST

1. **Red before green.** Write the failing test, watch it fail *for the expected reason*, then write
   the minimum code to pass it. A test that passes on first run proves nothing — it may assert
   nothing, or assert what already worked.
2. **One slice at a time.** One seam, one test, one implementation, per cycle.
3. **No horizontal slicing.** **MUST NOT** write all the tests for a feature before implementing any
   of it. That tests imagined behaviour and locks the test structure before you know the shape.
4. **Refactoring is not part of the cycle.** Structural cleanup belongs to
   `../workflows/refactor.md` or `two-axis-review.md`, in its own verified step.
5. **Expected values come from an independent source.** If the assertion recomputes the expected
   value the same way the production code does, it passes by construction and tests nothing.

---

## Android/KMP specifics

### The Gradle round trip is the constraint

Red-green is only a loop if it closes in seconds. A full `allTests` per cycle is not a loop.

| Cycle stage | Command |
|---|---|
| Per red-green cycle | `./gradlew :<module>:jvmTest --tests '<fully.qualified.TestClass>'` |
| After a slice is green | `./gradlew :<module>:jvmTest` |
| Before reporting done | `./gradlew :<module>:allTests` — includes `iosSimulatorArm64Test` |

**SHOULD** iterate on `jvmTest`; it is far faster than the native test binary. **MUST** run
`allTests` before claiming done — `commonTest` code that passes on JVM can fail on Kotlin/Native,
and in this skill's own example it did: a DataStore instance guard and a `value class` annotation
both failed only on native (`../examples/DECISIONS.md`).

Test-name filtering on `jvmTest` is standard Gradle. On Kotlin/Native test tasks, filtering support
varies by Kotlin version — **[UNVERIFIED]** for a given version; if it does not filter, narrow by
running the single target task instead of `allTests`.

### Coroutines: the scheduler is driven by suspension

In a `runTest` block with `StandardTestDispatcher`, coroutines do not run until something suspends.
`awaitItem()` is a suspension point and therefore advances the scheduler; a bare
`advanceUntilIdle()` before any `await` can leave a Turbine subscription with nothing collected.
This cost a real debugging cycle in `../examples/user-profile/` — the record is in
`../examples/DECISIONS.md`. Rules in `../references/kotlin/coroutines-and-flow.md`.

**MUST** inject dispatchers rather than reaching for `Dispatchers.Main`. A ViewModel that takes its
dispatcher by constructor needs no `Dispatchers.setMain` and tests identically on every target.

### Test source sets

**SHOULD** write the test in `commonTest` when the code under test is in `commonMain` — it then runs
on every configured target for free. Drop to `jvmTest`/`androidTest`/`iosTest` only for something
genuinely platform-bound (the Room migration test needs the JVM's file system; see the example).

### What TDD does not cover here

Jank, startup time and recomposition counts are not red-green testable at a unit seam. Use
`../workflows/audit-compose-performance.md` with Macrobenchmark. R8-only failures appear solely in
`bundleRelease`; no unit test catches them.

---

## Anti-patterns

| Anti-pattern | Indicator |
|---|---|
| Implementation-coupled | Test breaks on a refactor although behaviour did not change |
| Tautological | Assertion recomputes the expected value exactly as the code does |
| Horizontal slice | Every test written before any implementation exists |
| Seam drift | Visibility widened, or a getter added, so a test can see inside |
| Green-first | Code written, then a test written to match whatever it already does |
| Suite-per-cycle | `allTests` run on every red-green iteration; cycle takes minutes |
| Mock-everything | A repository test with no real database and no real client — asserts only that mocks were called |

---

## It's working if

- Every test was seen **failing for the expected reason** before the code that satisfies it existed.
- The seams tested are the ones agreed in the plan — no more, and none reached by widening visibility.
- Each cycle ran a single test class, not the suite.
- `allTests` was run once before reporting, and its result is reported honestly — including
  `NOT RUN — <reason>`.
- No existing test was modified, `@Ignore`d or deleted to get to green.

---

## Where it fits

```
plan-feature (agrees the seams) → vertical-slice (sizes the work)
   → add-feature ⇄ tdd (per slice)  → two-axis-review → commit
```

Strategy and tooling: `../references/quality/testing-strategy.md`.
Adding tests to code that already exists: `../workflows/backfill-tests.md`.
