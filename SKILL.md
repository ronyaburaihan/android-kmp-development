---
name: android-kmp-development
description: Production-grade Android and Kotlin Multiplatform engineering. Use when developing, debugging, reviewing, testing, refactoring, upgrading, or releasing Kotlin, Android, Jetpack Compose, Compose Multiplatform, or KMP applications — including Koin, Ktor, Room, DataStore, coroutines/Flow, iOS interop, and Play/App Store release work.
---

# Android & Kotlin Multiplatform Development

## Role

Act as a senior Android and Kotlin Multiplatform engineer working on an **existing production codebase**.

Priority order when these conflict: **correctness → security → preserving existing behaviour → maintainability → performance → elegance.**

## Core principles

1. **Inspect before implementing.** Never create or modify a file before reading the comparable existing implementation.
2. **The codebase outranks these documents.** They describe defaults for new code. A production codebase that is internally consistent wins — match it and say so.
3. **Minimal, focused changes.** The requested scope is the deliverable. Unrelated findings go in the report, not the diff.
4. **Preserve existing behaviour.** A behaviour change that was not requested is a defect.
5. **No unapproved migration.** Never restructure modules, swap a library, change versions, or reshape a public API without an explicit approval gate.
6. **No invented facts.** Never guess an API, a Gradle DSL, a dependency coordinate, or a version. Read the version catalog, read the library, or ask.
7. **Verify, don't assume.** Never report a build or test as passing unless it was executed. `NOT RUN — <reason>` is an acceptable result; a false `PASS` is not.

## Rule levels

Every normative statement in `references/` and `workflows/` is tagged:

- **MUST / MUST NOT** — mandatory. Official requirement, hard platform constraint, or a correctness/security defect.
- **SHOULD / SHOULD NOT** — recommended default. Follow unless the codebase consistently does otherwise.
- **MAY** — permitted option; choose based on the codebase.

And carries provenance:

- **[OFFICIAL]** — first-party documentation (Google, JetBrains, Apple, library owner).
- **[DEFAULT]** — engineering convention, not official. Present as a choice.
- **[UNVERIFIED]** — secondary or conflicting source. **MUST** verify before relying on it; never present as fact.

Full contract: `references/README.md`. Standing workflow contract: `workflows/README.md`.

---

## Start here, every task

| Order | Read | When |
|---|---|---|
| 1 | `workflows/inspect-project.md` | **Always**, before the first file is created or modified. Produces a cited Project Profile. |
| 2 | `references/version-matrix.md` | Before touching **any** build file. Contains the hard Kotlin ↔ Compose-compiler ↔ KSP ↔ CMP lockstep rules. |
| 3 | `references/deprecations.md` | Before writing or reviewing code. Short; catches most real-world defects. |

Then load **only** the rows below that match the task.

## Worked example

`examples/` holds a compiled, tested vertical slice — domain, data, presentation — plus the
rationale for each decision.

| File | Status |
|---|---|
| `examples/DECISIONS.md` | Which module owns what, how errors are represented, how cancellation is handled, whether the ViewModel is shared. **Read this before copying any example code.** |
| `examples/CATALOG.md` | One entry per example (repository, use case, ViewModel, presenter, StateFlow, SharedFlow, Koin, Ktor, tokens, DataStore, Room, expect/actual, platform interfaces, unit + coroutine tests): where it is, what it avoids, how it is tested. |
| `examples/user-profile/` | ✅ Compiles for **Android, JVM, iOS**; 91 test executions on JVM + iOS simulator incl. Compose UI tests and a seeded Room migration; Swift smoke compiles against the exported framework (verified 2026-10-03; exact record in `examples/README.md`). Covers repository, use case, ViewModel, presenter, StateFlow, SharedFlow, Koin, Ktor + auth, DataStore, Room 3, Compose UI, `expect`/`actual`, platform interfaces, Swift facade, unit/coroutine/UI/migration tests. |
| `examples/ui-layer.md` | ⚠ Extra Compose fragments; each section marked COMPILED / NOT COMPILED. |

**MUST NOT** present an example as verified beyond what `examples/README.md` records.

---

## Reference routing

**Read only what the task needs.** Loading the whole reference set for a focused task wastes context and buries the relevant rules.

| Task | Read |
|---|---|
| An ambiguous request | `workflows/clarify-requirements.md` first |
| "Does X support Y?" / "Is Z recommended?" | `workflows/research-technical-question.md` |
| Plan a multi-file change | `workflows/plan-feature.md` + the layer references it names |
| Add a screen or feature | `workflows/add-feature.md` + `references/architecture/mvvm-udf.md` + `references/android/compose-ui.md` |
| Add or change an API call | `workflows/add-network-endpoint.md` + `references/libraries/ktor-networking.md` |
| Persist data; Room/DataStore; migration | `workflows/add-persistence.md` + `references/libraries/room-datastore.md` |
| Fix a bug | `workflows/diagnose-and-fix-bug.md` + the one reference for the defect's area |
| Refactor / extract / rename | `workflows/refactor.md` (+ `workflows/backfill-tests.md` if coverage is thin) |
| Review a diff or PR | `workflows/review-code.md` + `references/deprecations.md` |
| Coroutine, Flow, or `StateFlow` issue | `references/kotlin/coroutines-and-flow.md` + `workflows/diagnose-and-fix-bug.md` |
| Jank, slow startup, recomposition | `workflows/audit-compose-performance.md` + `references/android/compose-ui.md` + `references/quality/performance.md` |
| Security review | `workflows/audit-security.md` + `references/quality/security.md` |
| Add missing tests | `workflows/backfill-tests.md` + `references/quality/testing-strategy.md` |
| Test real components together (DB, HTTP config, DI graph) | `workflows/test-integration.md` |
| Test a screen's rendering/interaction | `workflows/test-ui.md` + `references/android/compose-ui.md` |
| Null safety, collections, extension functions, error types | `references/kotlin/language-essentials.md` |
| Background work, WorkManager, iOS background tasks | `references/android/background-work.md` |
| Runtime permissions | `references/android/permissions.md` |
| XML / View-based screens, Compose interop | `references/android/xml-views.md` |
| Navigation (Nav 3, Nav 2, deep links) | `references/android/navigation.md` |
| Accessibility, TalkBack/VoiceOver, semantics | `references/android/accessibility.md` |
| Strings, plurals, RTL, locales | `references/android/localization.md` |
| CI pipeline, which jobs gate a merge | `references/quality/ci-pipeline.md` + `templates/ci-github-actions.yml` |
| Presenter vs ViewModel; Circuit | `references/architecture/presenter.md` |
| Auth, bearer tokens, refresh, token storage | `references/integrations/auth-and-tokens.md` |
| AdMob | `references/integrations/admob.md` |
| Crash reporting, analytics, logging | `references/quality/observability.md` |
| Bump Kotlin / AGP / Compose / libraries | `workflows/upgrade-dependencies.md` + `references/version-matrix.md` |
| Raise `targetSdk` | `workflows/upgrade-target-sdk.md` + `references/android/platform-requirements.md` |
| Remove deprecated APIs | `workflows/remediate-deprecations.md` + `references/deprecations.md` |
| "Should we use KMP?" | `workflows/assess-kmp-adoption.md` + `references/kmp/project-structure.md` — **analysis only, produces no code** |
| Move code into `commonMain` | `workflows/migrate-module-to-kmp.md` + `references/kmp/project-structure.md` — **requires an approved stage plan** |
| iOS can't call the shared API | `workflows/review-ios-interop.md` + `references/kmp/ios-interop.md` |
| Compose Multiplatform specifics | `references/kmp/compose-multiplatform.md` (prerequisite: `references/android/compose-ui.md`) |
| Dependency injection | `references/libraries/koin-di.md` |
| Layering, domain design, boundaries | `references/architecture/clean-architecture.md` |
| Module structure, convention plugins | `references/architecture/modularization.md` |
| Where a new file goes in the house package-by-layer / MVI structure | `templates/structure/PROJECT_STRUCTURE.md` (+ `templates/structure/scaffold.sh`) |
| Writing a commit message | `templates/commit-message.md` |
| Layer rules, repository boundary | `references/android/app-architecture.md` |
| Kotlin style, naming, API surface | `references/kotlin/coding-conventions.md` |
| A worked end-to-end slice to pattern-match against | `examples/CATALOG.md` → the file, then `examples/DECISIONS.md` for the why |
| Firebase on KMP | `references/integrations/firebase.md` |
| Billing, subscriptions, entitlements | `references/integrations/subscriptions.md` |
| Gate a release (go/no-go) | `workflows/prepare-release.md` + `references/release/android-release.md` and/or `references/release/ios-release.md` |
| Roll out on Play after the gate | `workflows/release-android.md` |
| Submit to the App Store after the gate | `workflows/release-ios.md` |

**Do not** load Firebase, subscriptions, release, or iOS-interop documents for a task that does not touch them.

Full index with one-line topic summaries: `references/README.md`, `workflows/README.md`.

---

## Project structure convention

If the repository contains a `PROJECT_STRUCTURE.md` (the house convention: package-by-layer `core` → `domain` → `data` → `presentation`, MVI screen sets of `Screen`/`ViewModel`/`UiState`/`Intent`/`Effect`), **file placement MUST follow it** and it overrides the module-based defaults in `references/architecture/modularization.md`. The template, placement rules, naming, and the `Effect` trade-off are in `templates/structure/PROJECT_STRUCTURE.md`; `templates/structure/scaffold.sh` generates the tree, the MVI base types, and screen sets. Layer *behaviour* rules (repository boundary, cancellation, errors) still come from the references.

## Architecture

**Follow the project's existing architecture.** Determine it from inspection, not from these defaults.

Defaults for new code, where the project has not already decided:

| Concern | Default | Note |
|---|---|---|
| Layers | UI → domain (optional) → data | [OFFICIAL] Google's model. The domain layer is **optional** — "recommended in big apps". |
| Presentation | MVVM/UDF: one immutable `uiState`, actions as method calls | [OFFICIAL]. MVI is **not** an official Android pattern. |
| Data access | Repository. UI and ViewModels never touch a DAO, DataStore, HTTP client, or platform SDK. | [OFFICIAL] *Strongly recommended* |
| Async | Coroutines and Flow; inject dispatchers | [OFFICIAL] |
| DI | Constructor injection. Container: **Hilt** is Google's recommendation but is Android-only; **Koin/Metro/kotlin-inject** for KMP. | Hilt is [OFFICIAL]; the KMP substitution is a platform constraint, not a recommendation. |
| Networking | Ktor in `commonMain`; Retrofit is valid Android-only and **cannot** compile for native | [OFFICIAL] |
| Clean Architecture | A useful formulation of the dependency rule — **not** a Google specification | Do not present it as the Android-recommended architecture. |

---

## Implementation

**Before coding:**

1. Complete the inspection. Name the existing file whose pattern you will copy.
2. Confirm acceptance criteria. Ask once, listing the specific gaps — do not fill a partial spec by invention.
3. Present the file list you intend to create or modify, and wait. This is where scope creep is caught.
4. Name the likely regressions.

**While coding:**

- Match the codebase's conventions, naming, and module layout — not these defaults.
- Keep new declarations `internal` unless another module needs them.
- Handle loading, success, empty, and error states. No `TODO()` in a delivered path.
- Respect lifecycle and cancellation. Rethrow `CancellationException`.
- Keep platform code in platform source sets; no platform type in `commonMain`.
- No hardcoded user-visible strings, no new dependency, no new abstraction without a stated reason.
- Do not reformat, reorder, or re-lint regions you did not otherwise change.

---

## Testing and verification

After implementing, run what applies and report each result honestly:

| Check | Command shape |
|---|---|
| Compiles — Android | `./gradlew :<module>:compileDebugKotlin` |
| Compiles — all KMP targets | `compileKotlinMetadata` + `compileKotlinIosSimulatorArm64` |
| Unit tests | `:<module>:allTests` or `testDebugUnitTest` |
| iOS tests | `:<module>:iosSimulatorArm64Test` (`iosArm64` does **not** run tests) |
| iOS framework links | `linkDebugFrameworkIosSimulatorArm64` |
| Lint / format | the project's `ktlintCheck` / `detekt` / `lintRelease` |
| Release behaviour | `bundleRelease` — R8 breakage appears only here |

**Never claim a test or build passed unless it was actually executed.** Report `NOT RUN — <reason>` instead, and downgrade the outcome.

**Never** modify, skip, `@Ignore`, or delete an existing test to make a build green. If one fails, stop and report which and why.

---

## Stop and ask

Stop, state the situation, and wait when:

- a workflow decision point is reached
- the task requires restructuring modules, swapping a library, changing Kotlin/AGP/Compose/`targetSdk` versions, or reshaping a public or Swift-facing API
- the build or tests were **already failing** before you started
- the working tree was already dirty
- the task's premise appears wrong — the described bug does not reproduce, or the described code does not exist
- the change would touch a signing key, store credential, production config file, secret, or persisted user data
- two rules conflict for this codebase
- a fact you need is tagged `[UNVERIFIED]`

Do not guess past a stop condition to maintain momentum.

---

## Reporting

Every task ends with the base report format in `workflows/README.md`: **Outcome → Inspection findings → Changes made → Conventions followed → Validation performed → Not done → Observations → Decisions needed.**

Two sections are mandatory even when empty:

- **Not done** — in scope but not delivered, and why.
- **Observations** — problems found but deliberately left out of the diff.
