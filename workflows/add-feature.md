# Workflow: Add Feature

Standing contract: `README.md`. Prerequisite: `inspect-project.md`.

---

## 1. Objective

Add a user-facing screen or capability to an existing application, through every layer it requires, matching the codebase's existing patterns.

Scope ends at a working, tested feature reachable by the user. It does **not** include restructuring the layers the feature passes through.

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| What the feature does, from the user's perspective | yes | **MUST** ask. Do not infer a spec. |
| Entry point — how the user reaches it | yes | **MUST** ask (new nav destination, tab, dialog, deep link, existing screen section). |
| Data source — existing endpoint/table, or new | yes | **MUST** ask. If new, run `add-network-endpoint.md` / `add-persistence.md` first. |
| Platforms | no | Default: every platform the host module already targets. |
| Design reference | no | Match the existing design system; note that you did. |
| States to handle | no | Default: loading, empty, error, content. **MUST** state the assumption. |

**MUST NOT** begin with a partial spec and fill gaps by invention. Ask once, with the specific gaps listed.

---

## 3. Initial project inspection

Run `inspect-project.md`, then additionally:

```bash
# the nearest comparable feature — the template for everything below
find . -path '*feature*' -type d -not -path '*/build/*' | head -20
```

**MUST** identify and read, in full, **one complete existing feature** of comparable shape: its module/package layout, route composable, content composable, ViewModel, UI state, repository call, DI registration, navigation registration, and tests.

**MUST** record:

| Question | Why it matters |
|---|---|
| Is a feature a module, or a package inside a larger module? | Determines whether to create a module (a structural change — see D1) |
| How is the screen registered with navigation? | Nav 3 entry provider, Nav 2 `NavGraphBuilder` extension, or custom |
| How are ViewModels bound in DI? | `viewModelOf`, `@HiltViewModel`, manual factory |
| Does the content composable take state + lambdas, or a ViewModel? | Match it, even if it is the weaker pattern |
| Is there a domain layer, and do comparable features use it? | Do not add a use case if comparable features do not have one |
| Does the feature need a new string/resource file or an existing one? | Avoid creating a parallel resource file |

**MUST** list the exact files the comparable feature consists of. The new feature mirrors that list.

---

## 4. Step-by-step procedure

### 4.1 Confirm the plan before writing — MUST

Present, and wait for confirmation:

- the file list to be created or modified, with paths
- the `UiState` shape
- the states handled
- the DI and navigation registration points
- whether a new module is required (→ D1)

**MUST NOT** skip this for a "small" feature. The file list is where scope creep is caught.

### 4.2 Model the UI state

Match the dominant shape found in inspection — data class with fields, or sealed interface of states. See `../references/architecture/mvvm-udf.md`.

**MUST** make `UiState` and everything it holds Compose-stable: `val` only, read-only collection types. See `../references/android/compose-ui.md`.

**MUST NOT** place a `Throwable`, `Context`, Room entity, or DTO in `UiState`.

### 4.3 Data access

| Situation | Action |
|---|---|
| Existing repository exposes the data | Use it. Do not add a method if an existing one suffices. |
| Existing repository needs one new method | Add it to the existing interface and implementation. |
| Data is new | Stop. Run `add-network-endpoint.md` or `add-persistence.md`, then return. |
| No repository exists for this data domain | → **D2** |

**MUST NOT** call a DAO, DataStore, HTTP client, or platform SDK from the ViewModel or a composable. See `../references/android/app-architecture.md`.

### 4.4 Domain layer

| Comparable features | Action |
|---|---|
| Use use cases, and this feature has real policy or multi-repository orchestration | Add a use case, matching their style. |
| Use use cases, but this feature only forwards one repository call | → **D3** |
| Have no domain layer | **MUST NOT** introduce one. |

See `../references/architecture/clean-architecture.md` for the size test.

### 4.5 ViewModel

Match the inspected pattern exactly: state production style, `stateIn` policy, error handling, dispatcher injection.

**MUST:**
- expose one immutable `uiState`; keep `MutableStateFlow` private
- receive user actions as methods
- fold one-off outcomes into state with an acknowledgement callback, **unless** comparable ViewModels use an event channel — then match them and note the deviation
- rethrow `CancellationException`
- hold no Android or lifecycle types

See `../references/kotlin/coroutines-and-flow.md`.

### 4.6 UI

**MUST** split route (reads state, resolves the ViewModel) from content (takes values and lambdas) **if comparable features do**. If they pass the ViewModel into the content composable, match that and note it.

**MUST:**
- handle every state agreed in 4.1 — no `TODO()` branch
- use the project's design-system components, not raw Material widgets, if a design system exists
- add `Modifier.testTag(...)` on the nodes the tests will address
- add `contentDescription` on icon-only controls
- accept `modifier: Modifier = Modifier` on reusable composables
- apply the project's string-resource mechanism; **MUST NOT** hardcode user-visible strings

See `../references/android/compose-ui.md`.

### 4.7 Wire DI and navigation

**MUST** register in the same place and style as the comparable feature. **MUST NOT** create a second DI module where an existing feature module exists.

**MUST** verify the destination is actually reachable — an unregistered route is the most common silent failure of this workflow.

### 4.8 Tests

Match the inspected test style. **MUST** add:

| Tier | Coverage |
|---|---|
| ViewModel | every state transition agreed in 4.1, including the error path |
| Content composable | each state renders; each action invokes its lambda |
| Repository method, if one was added | success and failure |
| Acknowledgement path, if state-held messages were used | message added, then cleared |

**MUST** use fakes, in the project's existing fake style. See `../references/quality/testing-strategy.md`.

### 4.9 Validate

Run section 7.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | Feature warrants a **new module** | (a) new module matching existing convention; (b) package inside an existing module | **MUST** ask — a new module is a structural change. Recommend (b) unless the project is consistently module-per-feature. |
| D2 | **No repository exists** for this data domain | (a) new repository + interface, matching convention; (b) extend the nearest existing repository | **MUST** ask. Recommend (a) if the data domain is genuinely distinct. |
| D3 | Domain layer exists but this feature has **no real policy** | (a) pass-through use case for consistency; (b) ViewModel calls the repository directly | **SHOULD** ask. Recommend (a) — consistency outranks the anti-pass-through guidance inside a codebase that already made this choice. |
| D4 | Comparable features use a **ViewModel-to-UI event channel** | (a) match it; (b) use state-held messages | **MUST** match (a) and record the deviation from `../references/architecture/mvvm-udf.md`. **MUST NOT** introduce a second pattern. |
| D5 | Feature needs a **platform capability** with no existing abstraction (camera, biometrics, location) | (a) domain interface + platform implementations; (b) Android-only for now | **MUST** ask in a KMP project. Recommend (a). **MUST NOT** add `expect`/`actual` reaching a platform SDK from the domain. |
| D6 | Feature needs a **new library** | — | **MUST** ask. Present the existing-library alternative first. |
| D7 | The spec implies changing an **existing screen's** behaviour | (a) in scope; (b) separate task | **MUST** ask before modifying an existing screen. |
| D8 | KMP project, feature is **Android-only** in practice | (a) place in the Android module; (b) place in `commonMain` with a stub elsewhere | **MUST** ask. Recommend (a). |

---

## 6. Implementation rules

**MUST:**

1. Mirror the comparable feature's file list, naming, and package layout.
2. Match the project's naming conventions — not the reference defaults — for ViewModels, state classes, repositories, and routes.
3. Keep every new class `internal` unless another module needs it.
4. Add new dependencies to the version catalog if one exists.
5. Keep the diff to files the feature requires.

**MUST NOT:**

6. Reformat, reorder, or re-lint untouched regions of files you edit.
7. "Improve" the comparable feature while copying from it.
8. Add a `TODO()` or `throw NotImplementedError()` in a delivered path.
9. Introduce a navigation, DI, state-management, or image-loading library the project does not use.
10. Change a shared `UiState`, repository interface, or design-system component used by other features without flagging it (→ D7).
11. Hardcode user-visible strings, dimensions, or colours where the project has resources for them.
12. Commit or push unless asked.

**In a KMP project, additionally MUST:**

13. Place shared code in `commonMain` and platform code in platform source sets — never a platform type in `commonMain`.
14. Supply an explicit initializer to `viewModel { }`, or use the project's DI accessor (`koinViewModel()`). A parameterless `viewModel()` fails off-JVM. See `../references/kmp/compose-multiplatform.md`.
15. Not expose `Flow` or generics on any type reaching the iOS framework surface. See `../references/kmp/ios-interop.md`.

---

## 7. Validation requirements

| # | Check | Command | Required |
|---|---|---|---|
| V1 | Compiles — Android | `./gradlew :<module>:compileDebugKotlin` | MUST |
| V2 | Compiles — all KMP targets | `./gradlew :<module>:compileKotlinMetadata :<module>:compileKotlinIosSimulatorArm64` | MUST if KMP |
| V3 | New tests pass | `./gradlew :<module>:testDebugUnitTest` or `:<module>:allTests` | MUST |
| V4 | Existing tests still pass | same task, whole module | MUST |
| V5 | Lint/format clean on changed files | project's `ktlintCheck` / `detekt` / `lint` task | MUST if configured |
| V6 | Destination reachable | launch and navigate, or assert the route is registered in a test | MUST |
| V7 | Every agreed state renders | Compose test per state | MUST |
| V8 | No hardcoded user-visible strings | `grep` the new files for literal UI text | MUST |
| V9 | Accessibility: icon-only controls have `contentDescription` | review | SHOULD |
| V10 | iOS framework still builds | `./gradlew :<shared>:linkDebugFrameworkIosSimulatorArm64` | SHOULD if KMP with iOS |

**MUST NOT** report `PASS` for a check that was not executed. Use `NOT RUN — <reason>`.

---

## 8. Failure handling

| Failure | Response |
|---|---|
| No comparable feature exists (first feature of its kind) | **MUST** stop and ask which pattern to establish, presenting the reference default as one option. **MUST NOT** silently invent a house style. |
| Compilation fails in an untouched module | **MUST** stop. The break is pre-existing or caused by a shared-type change (→ D7). Do not fix unrelated modules. |
| Compilation fails only on a native target | Almost always a JVM-only API or dependency in `commonMain`. **MUST** fix by moving the code to a platform source set, not by removing the target. |
| An existing test breaks | **MUST** stop. Report which test and why. **MUST NOT** modify or `@Ignore` an existing test to make the build green. |
| New test is flaky | **MUST** find the cause — usually a hardcoded dispatcher or a missing `stateIn` collector. **MUST NOT** add a delay or a retry. |
| Required design-system component does not exist | → ask: add to the design system (shared change) or build locally in the feature. Recommend local unless reuse is certain. |
| DI graph fails at runtime with a missing binding | **MUST** check registration placement against the comparable feature. Report if the graph has no verification test. |
| Feature needs an endpoint or table that does not exist | **MUST** stop and run the relevant workflow first. **MUST NOT** stub the data source. |
| User-facing behaviour cannot be verified (no device, no emulator) | **MUST** state it. V6/V7 become `NOT RUN`, and the Outcome becomes `DONE WITH CAVEATS`. |

---

## 9. Completion criteria

**MUST** all hold:

1. Every file in the plan from 4.1 exists, with no placeholder or `TODO()` in a delivered path.
2. Every agreed state is implemented and rendered.
3. Data access goes through a repository; no data-source call from the ViewModel or UI.
4. DI and navigation registration done; destination verified reachable.
5. Tests added at the tiers listed in 4.8, all passing.
6. V1–V8 all `PASS` or explicitly `NOT RUN` with a reason.
7. No existing test modified, skipped, or deleted.
8. No file outside the plan modified, except as recorded and justified.
9. No new library or module added without an approved decision point.
10. Every deviation from the reference set recorded in the report with the codebase pattern it matched instead.

---

## 10. Final report format

Base skeleton from `README.md`, with these additions.

```markdown
## Add Feature — <feature name>

### Outcome
<DONE | DONE WITH CAVEATS | BLOCKED | NEEDS DECISION> — one sentence.

### Inspection findings
- Pattern source: `path/to/ComparableFeature/` — <n> files mirrored
- Feature granularity: <module per feature | package in module>
- State shape: <data class | sealed interface>
- One-off events: <state-held + ack | channel>
- Domain layer: <present, used | present, bypassed per D3 | absent>
- Navigation: <mechanism and registration file>
- DI: <mechanism and registration file>

### Feature summary
- Entry point: <route / tab / dialog / deep link>
- States handled: <loading, empty, error, content, ...>
- Data source: <repository method used or added>
- Platforms: <Android | Android + iOS | ...>

### Changes made
| File | Change |
|---|---|
| `path` | created — <role> |
| `path:line` | modified — <what and why> |

### Conventions followed
| Aspect | Followed | Source |
|---|---|---|
| e.g. state shape | sealed interface | `path` |

**Deliberate deviations from `../references/`:**
| Reference rule | Deviation | Reason |
|---|---|---|
| e.g. mvvm-udf.md: no ViewModel→UI events | used a Channel | codebase-wide pattern (D4); `path:line` |

<Write "None." if there are none.>

### Validation performed
| # | Check | Command | Result |
|---|---|---|---|
| V1 | Android compile | | |
| V2 | KMP targets compile | | |
| V3 | New tests | | |
| V4 | Existing tests | | |
| V5 | Lint / format | | |
| V6 | Destination reachable | | |
| V7 | All states render | | |
| V8 | No hardcoded strings | | |
| V9 | Accessibility | | |
| V10 | iOS framework links | | |

### Tests added
| Test | Covers |
|---|---|
| `path::name` | |

### Not done
<In scope but not delivered, and why. "Nothing." if complete.>

### Observations
<Out-of-scope findings, each `file:line` + one-line recommendation. No fixes applied.>

### Decisions needed
<Open D1–D8 items with options and a recommendation. "None." if none.>
```
