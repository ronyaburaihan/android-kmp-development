# Workflow: Integration Testing

Standing contract: `README.md`. Prerequisite: `inspect-project.md`.

**Primitives:** `../process/tdd.md` — real components at the DAO, HTTP and DI seams.

---

## 1. Objective

Add or run tests that verify **two or more real units working together** — repository + real database, data source + real HTTP client configuration, DI graph resolution, migration from a real previous schema — in the fastest environment that keeps fidelity.

Unit tests with fakes are `backfill-tests.md`. On-device UI flows are `test-ui.md`. This workflow is the layer between.

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| The seam to test (which real components, together) | yes | **MUST** ask; "integration tests" over a whole app is not a scope |
| Why — a bug class, a migration, a release gate, a refactor net | yes | sets fidelity vs speed |
| Allowed environment: host JVM, emulator/simulator, staging host | yes | default: host JVM; **MUST NOT** hit a live host in a merge gate |
| Existing integration tests to match | no | derive from inspection |

---

## 3. Initial project inspection

```bash
find . -type d \( -name androidDeviceTest -o -name androidTest -o -name jvmTest -o -name iosTest \) -not -path '*/build/*'
grep -rln 'MockEngine\|inMemoryDatabaseBuilder\|MigrationTestHelper\|koinApplication\|FakeFileSystem' --include='*Test.kt' . | grep -v build/
grep -rn 'schemaDirectory' --include='*.gradle.kts' . | grep -v build/; ls */schemas 2>/dev/null
```

**MUST** record: which integration seams already have tests, where they run, and what harness they use. New tests match the harness.

**MUST** confirm a green baseline.

---

## 4. Step-by-step procedure

### 4.1 Pick the seam and the fidelity level

| Seam | Fastest faithful environment | Harness |
|---|---|---|
| Repository + real Room | host JVM or iOS simulator, in-memory DB | `Room.inMemoryDatabaseBuilder` + `BundledSQLiteDriver` — see `../examples/user-profile/` |
| Schema migration N→N+1 | Android instrumented (where `MigrationTestHelper` exists); other targets `[UNVERIFIED]` | exported schemas + seeded rows |
| Data source + real client config | any target, `MockEngine` | exercises ContentNegotiation/Auth/defaultRequest — verified in the example |
| DI graph resolves | any target | `koinApplication { }` + `get<T>()` per binding — verified in the example |
| DataStore + real serializer | any target, `FakeFileSystem` | verified in the example |
| Auth refresh end-to-end | any target, `MockEngine` scripted 401 → refresh → retry | — |
| Real backend contract | staging host, **non-gating** scheduled job | recorded fixtures + live run |

**MUST** choose the fastest environment that exercises the real components. Instrumented tests are for what cannot run on the host.

### 4.2 Write the test against real components, fake only the edge

```kotlin
// Real repository, real Room, real mapper — only the network edge is faked.
class UserRepositoryIntegrationTest {
    private lateinit var db: AppDatabase
    @BeforeTest fun setUp() { db = Room.inMemoryDatabaseBuilder<AppDatabase>().configure(Dispatchers.Default) }
    @AfterTest fun tearDown() = db.close()

    @Test fun refreshPersistsAndObserveEmits() = runTest {
        val repo = DefaultUserRepository(FakeRemote { dto }, RoomUserLocalDataSource(db.userRowDao()), UnconfinedTestDispatcher(testScheduler))
        repo.refresh()
        assertEquals("u1", repo.observeUser().first()?.id?.value)
    }
}
```

**MUST** fake exactly one edge per test (the network, the clock, the platform store). Faking two means the test proves nothing about their interaction.

### 4.3 Migration tests — MUST seed data at the old version

A migration test that only runs the migration proves the SQL parses. **MUST** insert rows at version N, migrate, and assert values at N+1. See `add-persistence.md` § 4.7.

### 4.4 Contract tests — SHOULD, non-gating

Record real payloads as fixtures; parse them in `commonTest` (gating). Run the live staging call in a scheduled job (non-gating) that fails loudly when the contract drifts.

### 4.5 Isolation

Each test gets its own in-memory DB, its own `FakeFileSystem` path, its own `koinApplication`. **MUST** close/clean in teardown. Shared state is how an integration suite becomes order-dependent.

### 4.6 Validate

Run section 7.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | Seam cannot run on the host (platform API, Keystore, Keychain) | (a) instrumented/simulator test; (b) extract the logic so most of it can | **MUST** ask; (b) is a refactor. |
| D2 | Migration test needed on a **non-Android** target | — | `MigrationTestHelper`'s KMP equivalent is `[UNVERIFIED]`; **MUST** verify what the Room line offers before promising it. |
| D3 | Test needs a **live backend** | (a) fixtures in the gate + live in a scheduled job; (b) live in the gate | **MUST** choose (a). |
| D4 | Integration test exposes a **defect** | — | **MUST** report; `diagnose-and-fix-bug.md`. Do not fix inside the test task. |
| D5 | No exported schemas exist | (a) enable and commit the current schema first; (b) skip migration tests | **MUST** ask; recommend (a). |
| D6 | Suite would exceed a few minutes on the host | (a) split: fast subset gates, full suite nightly | **SHOULD** recommend (a). |

---

## 6. Implementation rules

**MUST:**

1. Use real components for the seam under test; fake exactly one edge.
2. Isolate every test's state; clean up in teardown.
3. Seed migration tests with data at the old version.
4. Keep live-host tests out of the merge gate.
5. Use `kotlin-test` in `commonTest`; platform frameworks only in platform source sets.
6. Match the project's existing harness.

**MUST NOT:**

7. Fake the component the test exists to verify.
8. Share a database, file system, or Koin container across tests.
9. Add sleeps or retries.
10. Change production code to make an integration test pass without a decision point.
11. Modify or skip an existing test.

---

## 7. Validation requirements

| # | Check | Required |
|---|---|---|
| V1 | Tests pass on the host target | MUST |
| V2 | Tests pass on every KMP target they live in (`allTests`) | MUST if KMP |
| V3 | Mutation check: break the real component, test goes red | MUST |
| V4 | No cross-test state (run in random order / parallel) | MUST |
| V5 | Teardown closes DB / cleans FS / stops Koin | MUST |
| V6 | Migration tests seed and assert data, not just schema | MUST if migration |
| V7 | No live host in the gating job | MUST |
| V8 | Existing tests unmodified and green | MUST |
| V9 | Lint/format on test sources | MUST if configured |

---

## 8. Failure handling

| Failure | Response |
|---|---|
| Test passes with the component replaced by a no-op (V3 fails) | the test asserts nothing real — **MUST** rewrite. |
| Flaky across runs | shared state or a real dispatcher — **MUST** isolate; never retry. |
| Native target fails, JVM passes | platform behaviour difference — a real finding; **MUST** report. |
| Room/KSP generates nothing for a target | `add("ksp<Target>", …)` missing — see `../references/libraries/room-datastore.md`. |
| Migration test needs data that cannot be seeded | the schema export is missing or wrong → D5. |
| Live staging job fails | contract drift — **MUST** report with the diff; do not loosen the fixture. |

---

## 9. Completion criteria

1. Seam named; fidelity level justified.
2. Tests use real components with one faked edge.
3. V1–V9 pass; mutation check recorded.
4. Isolation and teardown verified.
5. Live-host tests, if any, non-gating.
6. Defects found reported, not fixed here.

---

## 10. Final report format

```markdown
## Integration Tests — <seam>

### Outcome
<DONE | DONE WITH CAVEATS | BLOCKED | NEEDS DECISION>

### Seam and environment
| Real components | Faked edge | Runs on |
|---|---|---|

### Tests added
| Test | Asserts | Target(s) |
|---|---|---|

### Mutation check
| Component broken | Test that went red |
|---|---|

### Validation performed
| # | Check | Result |
|---|---|---|

### Defects found — reported, not fixed
### Not done
### Observations
### Decisions needed
```
