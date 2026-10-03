# Workflow: Plan a Feature

Standing contract: `README.md`. Prerequisites: `clarify-requirements.md` (confirmed spec), `inspect-project.md` (Project Profile).

**Primitives:** `../process/grilling.md` (unresolved forks), `../process/vertical-slice.md` (sequencing), `../process/tdd.md` (agreeing the test seams).

---

## 1. Objective

Produce an implementation plan for a non-trivial change: the file list, the layer-by-layer design matched to the codebase, the risks, the test plan, and the decision points — **before** implementation, so that scope, structure, and risk are agreed once rather than discovered in review.

Produces no code.

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| Confirmed acceptance criteria | yes | run `clarify-requirements.md` |
| Project Profile | yes | run `inspect-project.md` |
| The comparable existing feature, identified | yes | from the Profile's "patterns to follow" table |
| Approved structural decisions, if any were flagged | yes if flagged | **MUST NOT** plan on an unapproved structural change |

**Trivial changes do not need this workflow.** A one-file change with no new types goes straight to implementation with the standing contract's plan step (present the file list, wait).

---

## 3. Initial project inspection

Already done by `inspect-project.md`. Additionally **MUST** read, in full:

- the comparable feature's every file
- the repository/data source the feature will touch
- the navigation registration point
- the DI registration point
- the nearest existing test of each kind the plan will add

**MUST** record any pre-existing problem on the path the feature will cross (a deprecated API, a missing test, an inconsistent pattern) under **Observations** — it affects risk but is not part of the plan.

---

## 4. Step-by-step procedure

### 4.1 Map acceptance criteria to layers

For each criterion, name the layer that satisfies it and the type that owns it.

| AC | Layer | Owner | New or existing |
|---|---|---|---|
| AC1 show cached profile | data (cache) + presentation | `UserRepository.observeUser`, `UserViewModel` | existing + new |
| AC3 401 → sign out | data (auth) | `TokenManager`, `SessionEventBus` | existing |

A criterion with no owner is a gap in the design. A criterion owned by the UI alone is usually a business rule in the wrong layer.

### 4.2 Design each layer, matching the codebase

Per layer, state what is reused, what is added, and which existing file the new code mirrors. Apply the reference set **as defaults** and the Project Profile **as the override**.

| Layer | Decision | Mirrors | Reference |
|---|---|---|---|
| Domain | new `UserProfile` + use case? or reuse? | `path` | `../references/architecture/clean-architecture.md` |
| Data | repository method / DTO / entity / migration | `path` | `../references/libraries/*.md` |
| Presentation | `UiState` shape; ViewModel or presenter | `path` | `../references/architecture/mvvm-udf.md` |
| UI | route/content split; states rendered | `path` | `../references/android/compose-ui.md` |
| Navigation | key + registration | `path` | `../references/android/navigation.md` |
| DI | bindings + scopes | `path` | `../references/libraries/koin-di.md` |
| Platform | any `expect`/interface needed | `path` | `../references/kmp/project-structure.md` |

**MUST** name the mirrored file for every new file. "Match the codebase" without a path is not a plan.

### 4.3 Write the file list

Every file to be created or modified, with its role. This is the artefact the user approves and the implementation is checked against.

### 4.4 Identify risks and regressions

| Risk | Where | Mitigation |
|---|---|---|
| shared `UiState` changes affect screen Y | `path` | keep Y's fields; add, don't rename |
| schema version bump | `path` | migration + upgrade-in-place test (`add-persistence.md`) |
| exported Swift surface changes | facade | coordinate with iOS (`review-ios-interop.md`) |

**MUST** list every shared type the plan modifies and every consumer of it.

### 4.5 Agree the test seams — MUST

Name the **seams** this feature will be tested at, and the tool for each, before any code exists:
`../process/tdd.md` § Seams. Testing capacity is finite; agreeing the seams here is what directs it
at the logic that can actually be wrong, instead of at everything.

For each seam: the test file, what it asserts, and which tool (Turbine, `MockEngine`, in-memory
Room, `runComposeUiTest`). Strategy and tooling: `../references/quality/testing-strategy.md`.

**MUST NOT** plan tests as a final step after the implementation. Each slice carries its own.

### 4.6 Sequence the work — vertical slices, MUST

Run `../process/vertical-slice.md`. Each step cuts a **narrow but complete** path through every
layer it touches, is demoable on its own, and fits one fresh context window.

**MUST NOT** sequence by layer. "All of domain, then all of data, then all of presentation" is a
horizontal slice: nothing is demoable until the last step, and every integration defect arrives at
once. In the house package-by-layer structure the directories *are* the layers, so layer-shaped
sequencing is the path of least resistance — this rule exists to counteract that.

Instead: one state of the screen end to end, then the next capability. Cache before network. One
platform before both. Declare each slice's blocking edges; at least one slice has none.

For a wide refactor, sequence expand → migrate → contract, each stage independently green
(`../process/vertical-slice.md` § Wide refactors, `refactor.md`).

Where a feature must ship dark, a flag is orthogonal to slicing — slice vertically *and* gate it.

### 4.7 Present and stop — MUST

The plan is presented and confirmed before `add-feature.md` begins. Changes to the plan during implementation go back through this step.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | Feature needs a **new module** | (a) new module; (b) package in existing | **MUST** ask — structural. |
| D2 | Feature needs a **new library** | (a) add; (b) use what exists | **MUST** ask; present the existing-library route first. |
| D3 | Feature touches a **shared type** with many consumers | (a) additive change; (b) breaking change with all consumers updated | **MUST** ask; recommend (a). |
| D4 | Acceptance criteria require **data that does not exist** (API/table) | — | **MUST** plan `add-network-endpoint.md` / `add-persistence.md` as prerequisite steps. |
| D5 | Two valid designs with different trade-offs | — | **MUST** present both with costs; recommend one; do not choose silently. |
| D6 | The comparable feature uses a pattern the reference set discourages | (a) match it; (b) deviate | **MUST** plan (a) and record the deviation; (b) only with approval. |
| D7 | Feature is large enough to need **incremental delivery** | (a) feature flag + steps; (b) one change | **SHOULD** recommend (a) above ~15 files. |
| D8 | Plan reveals the **spec is infeasible** as written | — | **MUST** return to `clarify-requirements.md` with the specific conflict. |

---

## 6. Implementation rules

**MUST:**

1. Produce a file list with a mirrored file per new file.
2. Map every acceptance criterion to an owner.
3. List shared types modified and their consumers.
4. Plan tests per layer before code.
5. Record reference-set deviations the codebase forces.

**MUST NOT:**

6. Write code.
7. Plan on an unapproved structural change.
8. Leave a criterion without an owner.
9. Plan a design the comparable feature does not use without flagging it (D6).

---

## 7. Validation requirements

| # | Check | Required |
|---|---|---|
| V1 | Every AC mapped to a layer and owner | MUST |
| V2 | Every new file has a mirrored existing file | MUST |
| V3 | Shared-type impact listed with consumers | MUST |
| V4 | Test plan per layer | MUST |
| V5 | Risks listed with mitigations | MUST |
| V6 | Structural changes flagged, none assumed approved | MUST |
| V7 | Zero project files changed | MUST |
| V8 | Work sequenced so each step compiles | MUST |

---

## 8. Failure handling

| Failure | Response |
|---|---|
| No comparable feature exists | **MUST** stop and ask which pattern to establish; present the reference default as one option. |
| Spec conflicts with the architecture (e.g. requires UI to call the network) | → D8; **MUST NOT** plan a layering violation. |
| Plan exceeds the approved scope | **MUST** separate the extra into a follow-up and say so. |
| Required data/API missing | → D4. |
| Too many consumers of a shared type to change safely | → D3; recommend additive. |

---

## 9. Completion criteria

1. File list approved.
2. V1–V8 pass.
3. Every decision point resolved or listed under **Decisions needed**.
4. Test plan accepted.
5. Hand-off to `add-feature.md` (or the relevant workflow) with the plan as its 4.1 input.

---

## 10. Final report format

```markdown
## Plan — <feature>

### Outcome
<APPROVED | NEEDS DECISION | INFEASIBLE AS SPECIFIED>

### Acceptance criteria → owners
| AC | Layer | Owner | New/existing |
|---|---|---|---|

### Design per layer
| Layer | Decision | Mirrors | Deviation from reference (if any) |
|---|---|---|---|

### File list
| File | Create/modify | Role | Mirrors |
|---|---|---|---|

### Shared types affected
| Type | Change | Consumers |
|---|---|---|

### Risks
| Risk | Where | Mitigation |
|---|---|---|

### Test plan
| Layer | Test | Asserts |
|---|---|---|

### Sequence
1. …

### Observations
### Decisions needed
```
