---
inclusion: always
---
# Android / Kotlin Multiplatform engineering

This project uses the **android-kmp-development** skill: 35 reference documents, 24 workflows,
8 process primitives and a compiled example at `SKILL_PATH`. Everything below is the always-loaded summary — **read the files
named in the routing table on demand.** Do not try to hold the whole set in context.

Entry point: `SKILL_PATH/SKILL.md`.

## Non-negotiable rules

1. **Inspect before implementing.** Read `SKILL_PATH/workflows/inspect-project.md` and follow it
   before creating or modifying any file. Cite patterns to at least two real files.
2. **The codebase outranks these documents.** They describe defaults for new code. A consistent
   production codebase wins — match it, and say in your report that you did.
3. **Minimal, focused changes.** The requested scope is the deliverable. Unrelated findings go in
   the report, not the diff.
4. **Preserve existing behaviour.** An unrequested behaviour change is a defect.
5. **No unapproved migration.** Never restructure modules, swap a library, change Kotlin/AGP/
   Compose/`targetSdk` versions, or reshape a public or Swift-facing API without stopping to ask.
6. **No invented facts.** Never guess an API, Gradle DSL, dependency coordinate or version. Read the
   version catalog, read the library, or ask.
7. **Verify, don't assume.** `PASS` means a command was executed. `NOT RUN — <reason>` is honest; a
   false `PASS` is a defect. Never modify, skip or delete an existing test to make a build green.
8. **Align before building.** Look facts up in the codebase; ask the user only decisions. Code that
   is correct and answers the wrong requirement is still a defect. `process/grilling.md`
9. **Signal before theory, red before green.** For a bug, build a command that fails on it before
   forming any hypothesis. For a feature, write the failing test before the code that satisfies it.
   `process/diagnostic-loop.md`, `process/tdd.md`

## Rule levels

- **MUST / MUST NOT** — mandatory. Official requirement, hard platform constraint, or a
  correctness/security defect.
- **SHOULD / SHOULD NOT** — recommended default. Follow unless the codebase consistently differs.
- **MAY** — permitted option.

Provenance tags on every claim: **[OFFICIAL]** first-party docs · **[DEFAULT]** engineering
convention, present as a choice · **[UNVERIFIED]** must be verified before use, never stated as fact.

## Start here, every task

| Order | Read | When |
|---|---|---|
| 1 | `workflows/inspect-project.md` | always, before the first file change |
| 2 | `references/version-matrix.md` | before touching any build file — holds the hard Kotlin ↔ Compose-compiler ↔ KSP ↔ CMP lockstep |
| 3 | `references/deprecations.md` | before writing or reviewing code; short, catches most real defects |

## Routing — load only what the task needs

| Task | Read |
|---|---|
| Ambiguous request | `workflows/clarify-requirements.md` + `process/grilling.md` |
| Which seam to test at; test-first | `process/tdd.md` — names the tool and a verified example test per seam |
| Break work into demoable steps | `process/vertical-slice.md` |
| Out of context; handing over | `process/handoff.md` |
| "How could this session have gone better?" | `process/retro.md` |
| Editing this file or a standards doc | `process/writing-for-agents.md` |
| "Does X support Y?" / "Is Z recommended?" | `workflows/research-technical-question.md` |
| Plan a multi-file change | `workflows/plan-feature.md` |
| Add a screen or feature | `workflows/add-feature.md` + `references/architecture/mvvm-udf.md` + `references/android/compose-ui.md` |
| Add or change an API call | `workflows/add-network-endpoint.md` + `references/libraries/ktor-networking.md` |
| Persist data, Room/DataStore, migration | `workflows/add-persistence.md` + `references/libraries/room-datastore.md` |
| Fix a bug | `workflows/diagnose-and-fix-bug.md` + `process/diagnostic-loop.md` + the one reference for the defect's area |
| Coroutine / Flow / StateFlow issue | `references/kotlin/coroutines-and-flow.md` |
| Refactor, extract, rename | `workflows/refactor.md` |
| Review a diff or PR | `workflows/review-code.md` + `process/two-axis-review.md` + `references/deprecations.md` |
| Jank, slow startup, recomposition | `workflows/audit-compose-performance.md` + `references/quality/performance.md` |
| Security review | `workflows/audit-security.md` + `references/quality/security.md` |
| Add missing tests | `workflows/backfill-tests.md` + `references/quality/testing-strategy.md` |
| Test real components together | `workflows/test-integration.md` |
| Test a screen | `workflows/test-ui.md` |
| Bump Kotlin / AGP / Compose / libraries | `workflows/upgrade-dependencies.md` + `references/version-matrix.md` |
| Raise `targetSdk` | `workflows/upgrade-target-sdk.md` + `references/android/platform-requirements.md` |
| Remove deprecated APIs | `workflows/remediate-deprecations.md` |
| "Should we use KMP?" | `workflows/assess-kmp-adoption.md` — analysis only, produces no code |
| Move code into `commonMain` | `workflows/migrate-module-to-kmp.md` — needs an approved plan |
| iOS can't call the shared API | `workflows/review-ios-interop.md` + `references/kmp/ios-interop.md` |
| Compose Multiplatform specifics | `references/kmp/compose-multiplatform.md` |
| Dependency injection | `references/libraries/koin-di.md` |
| Layering, domain design | `references/architecture/clean-architecture.md` |
| Module structure | `references/architecture/modularization.md` |
| Kotlin style / null safety / collections | `references/kotlin/coding-conventions.md`, `references/kotlin/language-essentials.md` |
| Background work, WorkManager | `references/android/background-work.md` |
| Permissions | `references/android/permissions.md` |
| XML / View screens, Compose interop | `references/android/xml-views.md` |
| Navigation | `references/android/navigation.md` |
| Accessibility | `references/android/accessibility.md` |
| Strings, plurals, RTL | `references/android/localization.md` |
| Auth, tokens, refresh | `references/integrations/auth-and-tokens.md` |
| Firebase | `references/integrations/firebase.md` |
| Billing, subscriptions | `references/integrations/subscriptions.md` |
| AdMob | `references/integrations/admob.md` |
| Crash reporting, analytics, logging | `references/quality/observability.md` |
| CI pipeline | `references/quality/ci-pipeline.md` |
| Ship a release | `workflows/prepare-release.md`, then `workflows/release-android.md` / `release-ios.md` |
| A worked, compiled example | `examples/CATALOG.md`, then `examples/DECISIONS.md` |
| Where a new file goes (house structure) | `templates/structure/PROJECT_STRUCTURE.md` |
| Commit message | `templates/commit-message.md` |

**Do not** load Firebase, billing, AdMob, release or iOS-interop documents for a task that does not
touch them.

## Three accuracy guards

Generic Android advice misleads on KMP here specifically:

- **Hilt is Google's recommendation but is Android/JVM-only** — it cannot compile in `commonMain`.
  Koin/Metro/kotlin-inject are a platform constraint, not a claim that Google recommends them.
- **MVI is not an official Android pattern.** UDF is. Clean Architecture is not a Google
  specification either.
- **Compose Multiplatform for web is Beta**; **Swift export is Alpha**. Neither is production-ready.

## Stop and ask

Stop, state the situation, and wait when: a workflow decision point is reached; the task needs one
of the forbidden actions in rule 5; the build or tests were already failing; the working tree was
already dirty; the task's premise appears wrong; the change would touch a signing key, store
credential, secret or persisted user data; or a fact you need is tagged `[UNVERIFIED]`.

Do not guess past a stop condition to maintain momentum.

## Reporting

End every task with: **Outcome · Inspection findings · Changes made · Conventions followed ·
Validation performed · Not done · Observations · Decisions needed.** The last two are mandatory
even when empty — an omitted caveat reads as no caveat. Format:
`SKILL_PATH/templates/task-report.md`.
