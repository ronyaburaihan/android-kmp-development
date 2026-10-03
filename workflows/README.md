# Android / KMP Engineering Workflows

Reusable procedures for an AI coding agent working on **existing production** Android and Kotlin Multiplatform applications.

**Companion reference set:** `../references/` — these workflows cite it rather than restating rules. `../references/README.md` defines the `MUST` / `SHOULD` / `MAY` contract and the `[OFFICIAL]` / `[DEFAULT]` / `[UNVERIFIED]` provenance tags used throughout.

**Companion process layer:** `../process/` — the *mechanics* a workflow invokes: the frontier interview, red-green at agreed seams, feedback-loop-before-hypothesis, the two-axis review, vertical slicing. A workflow is a procedure; a primitive is a mechanic. Each workflow that invokes one names it under **Primitives** in its header. `../process/README.md`.

---

## The standing contract

These rules apply to **every** workflow in this directory. A workflow may add constraints; none may relax these.

### 1. Inspect before you write — MUST

**MUST** complete `inspect-project.md` (or the workflow's own inspection step, which supersedes it) **before** creating or modifying any file.

**MUST NOT** create a file whose purpose is already served by an existing file. Find the existing one and extend it.

**MUST NOT** introduce a library, pattern, or module that the project does not already use, without an explicit decision point and user approval.

### 2. The codebase outranks the reference set — MUST

The reference documents describe defaults for new code. **An existing production codebase that is internally consistent outranks them.**

| Situation | Action |
|---|---|
| Codebase matches the reference default | Follow it. |
| Codebase consistently does something else that works | **MUST** match the codebase. **MUST** state in the report that you did, and what the reference default was. |
| Codebase is inconsistent (two patterns in use) | **MUST** match the pattern used by the nearest comparable code. **SHOULD** note the inconsistency; **MUST NOT** unify it as part of this task. |
| Codebase violates a reference `MUST` tagged `[OFFICIAL]` | **MUST** report it. **MUST NOT** fix it outside the task's scope unless it is the direct cause of the task. |

### 3. No unapproved migration — MUST

**MUST NOT**, without an explicit approval gate:

- restructure modules, or move code between modules
- replace a DI container, HTTP client, persistence library, navigation library, or state-management approach
- introduce or remove a `commonMain` source set, or add a KMP target
- change `targetSdk`, `compileSdk`, AGP, Kotlin, or Compose versions
- rename or reshape a public API consumed by another module or by Swift
- reformat, reorder, or re-lint files the task did not otherwise change
- enable or disable R8, resource shrinking, or a build-type optimization

Where a task genuinely requires one of these, the workflow defines a **decision point**. **MUST** stop there, present the options and their costs, and wait.

### 4. Scope discipline — MUST

**MUST** deliver the whole requested task. **MUST NOT** narrow it silently.

**MUST NOT** widen it. Observed-but-unrelated problems go in the report's **Observations** section, not into the diff.

If part of the task is blocked, **MUST** complete every unblocked part and state plainly what was left and why.

### 5. Verify, do not assume — MUST

**MUST NOT** state that something works without having run it. If a build or test could not be run, say so.

**MUST NOT** present an `[UNVERIFIED]` reference claim as fact. Verify it, or surface the uncertainty.

**MUST NOT** invent an API, a Gradle DSL, a dependency coordinate, or a version. If unsure, read the project's version catalog, read the library's source in the Gradle cache, or ask.

### 6. Stop conditions — MUST

**MUST** stop and ask the user when:

- a decision point in the workflow is reached
- the task cannot proceed without one of the forbidden actions in rule 3
- the build was already broken before you started
- the task's premise appears wrong (the described bug does not reproduce; the described code does not exist)
- two reference rules conflict for this codebase
- the change would touch a signing key, a store credential, a production config file, or a secret

**MUST NOT** guess past a stop condition to keep momentum.

### 7. Commits — MUST

**MUST NOT** commit or push unless the user asked. When a workflow does produce commits — `refactor.md`, `upgrade-dependencies.md` and `remediate-deprecations.md` work in independently verified steps — each message follows `../templates/commit-message.md`: Conventional Commits, scope matching the existing history, and **no `Co-Authored-By` or AI attribution**.

---

## Base report format

Every workflow ends with a report. Workflows specialise the middle; this skeleton is mandatory.

```markdown
## <Workflow name> — <task summary>

### Outcome
<DONE | DONE WITH CAVEATS | BLOCKED | NEEDS DECISION> — one sentence.

### Inspection findings
<What the project actually is: module layout, versions, patterns in use. Only the
facts that shaped the work.>

### Changes made
| File | Change |
|---|---|
| `path:line` | what and why |

<Omit the table and write "None." when nothing was modified.>

### Conventions followed
<Which existing codebase patterns were matched. Name the file you copied the
pattern from. State any reference default deliberately not followed, and why.>

### Validation performed
| Check | Command | Result |
|---|---|---|
| ... | ... | PASS / FAIL / NOT RUN — reason |

<NOT RUN is acceptable. Claiming PASS without running is not.>

### Not done
<Explicitly in scope but not delivered, and why. Write "Nothing." if complete.>

### Observations
<Problems found but out of scope. Each with file:line and a one-line
recommendation. No fixes applied. Write "None." if none.>

### Decisions needed
<Open questions blocking completion, each with options and a recommendation.
Write "None." if none.>
```

---

## Workflow index

### Process primitives — invoked by the workflows below

| Primitive | Mechanic |
|---|---|
| `../process/grilling.md` | Frontier interview: look facts up, ask only decisions, one numbered round with recommendations |
| `../process/vertical-slice.md` | Tracer-bullet slicing; expand → migrate → contract for wide refactors |
| `../process/tdd.md` | Red before green, at pre-agreed seams. The KMP seam table |
| `../process/diagnostic-loop.md` | A red-capable command **before** any hypothesis. Ranked loop constructions |
| `../process/two-axis-review.md` | Standards and spec as independent axes, never merged |
| `../process/handoff.md` | Session state for the next agent, including the toolchain |
| `../process/retro.md` | Improve the environment — checks over prose |
| `../process/writing-for-agents.md` | Conventions for agent-facing documents |

### Foundation — read first

| Workflow | Use when |
|---|---|
| `inspect-project.md` | **Prerequisite for every other workflow.** Establishes what the project is before anything is written. |
| `clarify-requirements.md` | Turning a request into observable acceptance criteria before any inspection or design. |
| `research-technical-question.md` | Answering "does X support Y / is Z recommended" with sourced, dated, version-anchored evidence. |
| `plan-feature.md` | Designing a non-trivial change layer by layer, with a file list, before implementation. |

### Feature work

| Workflow | Use when |
|---|---|
| `add-feature.md` | Adding a screen or user-facing capability through all layers. |
| `add-network-endpoint.md` | Adding or changing a remote API call. |
| `add-persistence.md` | Adding persisted data (Room entity/DAO, DataStore key) including migration. |
| `refactor.md` | Changing structure without changing behaviour, in independently green steps. |

### Build and platform maintenance

| Workflow | Use when |
|---|---|
| `upgrade-dependencies.md` | Bumping Kotlin, AGP, Compose, CMP, KSP, or library versions. |
| `upgrade-target-sdk.md` | Raising `targetSdk`, including the API 36 behaviour changes. |
| `remediate-deprecations.md` | Removing deprecated APIs and practices from existing code. |

### Kotlin Multiplatform

| Workflow | Use when |
|---|---|
| `assess-kmp-adoption.md` | Evaluating whether and how to adopt KMP. **Analysis only — produces no code.** |
| `migrate-module-to-kmp.md` | Moving existing Android code into `commonMain`. **Requires an approved plan.** |
| `review-ios-interop.md` | Auditing the shared module's Swift-facing surface. |

### Quality

| Workflow | Use when |
|---|---|
| `diagnose-and-fix-bug.md` | A reported defect. Reproduce, locate, fix, regression-test. |
| `audit-compose-performance.md` | Jank, slow startup, excessive recomposition. |
| `audit-security.md` | Security review against `../references/quality/security.md`. |
| `backfill-tests.md` | Adding test coverage to existing untested code. |
| `test-integration.md` | Real components together: Room, Ktor config, DI graph, migrations, DataStore. |
| `test-ui.md` | Compose rendering/interaction tests in `commonTest`; instrumented only where needed. |
| `review-code.md` | Reviewing a diff: defects first, verified, ranked; conventions judged against the codebase. |

### Release

| Workflow | Use when |
|---|---|
| `prepare-release.md` | Pre-release verification gate for Android, iOS, or both. Produces go/no-go. |
| `release-android.md` | After the gate: internal → closed → staged production with halt criteria. |
| `release-ios.md` | After the gate: TestFlight → App Review → phased release, with KMP symbolication checks. |

---

## Choosing a workflow

| The user asks for | Workflow |
|---|---|
| "what exactly do you want?" / an ambiguous request | `clarify-requirements.md` |
| "does Ktor support…?" / "is this recommended?" | `research-technical-question.md` |
| "plan this feature" / a multi-file change | `plan-feature.md` → `add-feature.md` |
| "add a screen that shows X" | `add-feature.md` |
| "call this new endpoint" | `add-network-endpoint.md` |
| "remember the user's choice" / "cache this" | `add-persistence.md` |
| "update Kotlin" / "bump dependencies" | `upgrade-dependencies.md` |
| "we need to target Android 16" | `upgrade-target-sdk.md` |
| "fix the deprecation warnings" | `remediate-deprecations.md` |
| "should we use KMP?" / "can we share this with iOS?" | `assess-kmp-adoption.md` |
| "move the repository into shared code" | `assess-kmp-adoption.md` first, then `migrate-module-to-kmp.md` |
| "the iOS team can't call this" | `review-ios-interop.md` |
| "X is broken" | `diagnose-and-fix-bug.md` |
| "the list stutters" / "startup is slow" | `audit-compose-performance.md` |
| "review this for security" | `audit-security.md` |
| "this has no tests" | `backfill-tests.md` |
| "test the repository against a real database" | `test-integration.md` |
| "test the screen" | `test-ui.md` |
| "review this PR" | `review-code.md` |
| "clean this up" / "extract this" / "rename" | `refactor.md` |
| "we're shipping tomorrow" | `prepare-release.md` |
| "roll it out on Play" | `release-android.md` |
| "submit to the App Store" | `release-ios.md` |

| "what did we decide / I'm out of context" | `../process/handoff.md` |
| "how could this session have gone better?" | `../process/retro.md` |

If no workflow fits, **MUST** still apply the standing contract above: inspect first, match the codebase, do not migrate without approval, report in the base format. The primitives in `../process/` are available to any task, whether or not a workflow names them.
