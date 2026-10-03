# android-kmp-development

An [Agent Skill](https://docs.claude.com/en/docs/claude-code/skills) that makes an AI coding agent
competent on **existing** Android and Kotlin Multiplatform codebases — developing, debugging,
reviewing, testing, refactoring, upgrading and releasing.

Built for brownfield work. Its first instruction is to read your project before writing anything,
and its second is that your codebase outranks every default it carries.

**Verified 2026-10-03** against first-party documentation, with a compiled and tested example.
Version numbers and store deadlines go stale — [`references/version-matrix.md`](references/version-matrix.md)
is the single place they live and names the source to re-check each one against.

---

## Why this exists

Generic Android advice misleads on KMP in specific, expensive ways. An agent working without this
skill will confidently tell you to use Hilt in `commonMain` (it cannot compile there), treat Clean
Architecture as a Google specification (it is not), describe Compose Multiplatform for web as
production-ready (it is Beta), or return an empty list to signal a network failure. It will also
restructure your modules on the way to adding a button.

This skill is a set of rules, procedures and verified examples that prevent those outcomes, with
every claim labelled by how well it is known.

---

## What's inside

| Part | Contents |
|---|---|
| [`SKILL.md`](SKILL.md) | The entry point. Role, seven non-negotiable principles, the rule-level legend, and a **routing table** so a focused task loads two or three documents instead of sixty. |
| [`references/`](references/) | **35 topic documents.** Every normative statement tagged **MUST / SHOULD / MAY** and **[OFFICIAL] / [DEFAULT] / [UNVERIFIED]**. |
| [`workflows/`](workflows/) | **24 step-by-step procedures**, each with objective, required inputs, project inspection, procedure, decision points, implementation rules, validation requirements, failure handling, completion criteria and report format. |
| [`examples/`](examples/) | A **compiled and tested** KMP vertical slice — Android, JVM and both iOS targets; Compose UI; Room 3 with a seeded migration test; a Swift-facing facade whose smoke file compiles against the generated framework — plus the rationale for every decision. |
| [`templates/`](templates/) | 15 fill-in skeletons: spec, plan, task report, ADR, PR, bug report, commit message, release checklist, version catalog, module build file, CI config, and a project-structure convention with a scaffold script. |
| [`EVALUATION.md`](EVALUATION.md) | 48 checks and 8 scenarios for judging whether an agent is actually using the skill correctly. |
| [`LIMITATIONS.md`](LIMITATIONS.md) | Every unverified claim, exactly what was and was not compiled, topics not covered, and the facts corrected during verification. |
| [`research/RESEARCH.md`](research/RESEARCH.md) | The sourced research the skill was built from, with an addendum of corrections found by compiling. |

### Reference coverage

| Area | Documents |
|---|---|
| `kotlin/` | coding conventions · language essentials (null safety, collections, error handling) · coroutines and Flow |
| `android/` | app architecture · Compose UI · platform requirements (API 36) · background work · permissions · XML/View interop · navigation · accessibility · localization |
| `kmp/` | project structure · Compose Multiplatform · iOS interop |
| `architecture/` | Clean Architecture · MVVM/UDF · modularization · presenter pattern |
| `libraries/` | Koin · Ktor · Room and DataStore |
| `quality/` | testing strategy · security · performance · observability · CI pipeline |
| `integrations/` | Firebase · subscriptions · auth and tokens · AdMob |
| `release/` | Android release · iOS release |
| root | version matrix · deprecations · the rule contract |

### Workflows

| Group | Workflows |
|---|---|
| Foundation | `inspect-project` (prerequisite for all others) · `clarify-requirements` · `research-technical-question` · `plan-feature` |
| Feature work | `add-feature` · `add-network-endpoint` · `add-persistence` |
| Maintenance | `upgrade-dependencies` · `upgrade-target-sdk` · `remediate-deprecations` · `refactor` |
| Kotlin Multiplatform | `assess-kmp-adoption` (analysis only) · `migrate-module-to-kmp` · `review-ios-interop` |
| Quality | `diagnose-and-fix-bug` · `review-code` · `backfill-tests` · `test-integration` · `test-ui` · `audit-compose-performance` · `audit-security` |
| Release | `prepare-release` (go/no-go gate) · `release-android` · `release-ios` |

---

## Installation

The skill is a plain directory containing `SKILL.md`. A host discovers it by scanning its skills
folder and reading the YAML frontmatter (`name`, `description`).

> **The directory must be named `android-kmp-development`**, matching the `name` in the frontmatter.
> This repository is named `…-skill`, so a bare `git clone` creates the wrong directory name and the
> skill will not resolve. The commands below name the target explicitly.

**All projects (user scope):**

```bash
mkdir -p ~/.claude/skills
git clone https://github.com/ronyaburaihan/android-kmp-development-skill.git \
  ~/.claude/skills/android-kmp-development
```

**One project (project scope):**

```bash
mkdir -p .claude/skills
git clone https://github.com/ronyaburaihan/android-kmp-development-skill.git \
  .claude/skills/android-kmp-development
```

Project scope takes precedence over user scope, which is useful for pinning a project to a
particular revision.

**From a local copy, or a downloaded archive:**

```bash
mkdir -p ~/.claude/skills
cp -r /path/to/android-kmp-development ~/.claude/skills/
```

**Other Agent-Skills-compatible hosts:** copy the directory into the host's skills location. Nothing
here is Claude Code specific — `SKILL.md` is the entry point and every internal path is relative.

### Verify

```bash
ls   ~/.claude/skills/android-kmp-development/SKILL.md   # must exist
head -5 ~/.claude/skills/android-kmp-development/SKILL.md # must show the YAML frontmatter
```

**Restart your session.** A skill added mid-session is not discovered until the next one. Then
`/skills` lists it and `/android-kmp-development` invokes it explicitly.

### Update

```bash
git -C ~/.claude/skills/android-kmp-development pull
```

Re-read `references/version-matrix.md` afterwards — it carries a verification date.

### Uninstall

```bash
rm -rf ~/.claude/skills/android-kmp-development
```

---

## Usage

The skill triggers on its description: Android, Kotlin, KMP, Compose, Compose Multiplatform, Koin,
Ktor, Room, DataStore, coroutines and Flow, iOS interop, and Play or App Store release work. Just
describe the task.

```
Add a settings screen with a dark-mode toggle
```
→ inspects the project, finds the nearest comparable feature, presents a file list for approval,
then implements it matching your conventions.

```
The conversation list crashes when I scroll fast
```
→ reproduces it, writes a regression test that fails for the identified cause, fixes the cause
rather than the crash site, reports siblings without fixing them.

```
Update Kotlin to the latest version
```
→ resolves the whole lockstep set (Compose compiler, KSP, CMP), verifies each against its registry,
refuses to mix in a deprecation cleanup, and checks the release build because R8 breakage only
appears there.

```
Should we share this with iOS?
```
→ produces a portability assessment and a staged plan with a mandatory spike. Writes no code.
"Do not adopt" is a valid answer.

```
Store the user's auth token
```
→ refuses plaintext DataStore and the deprecated `EncryptedSharedPreferences`, designs a
`TokenStore` interface with Keystore and Keychain implementations, and handles key invalidation.

To force a specific procedure, name it: *"use the refactor workflow"*, *"run the security audit"*.

### Project structure convention

If your repository contains a `PROJECT_STRUCTURE.md`, file placement follows it and overrides the
skill's module-based defaults. A ready-made convention — package-by-layer `core → domain → data →
presentation` with MVI screen sets — ships at
[`templates/structure/`](templates/structure/PROJECT_STRUCTURE.md), with a `scaffold.sh` that
generates the tree, the MVI base types and screen sets.

---

## Design principles it enforces

1. **Inspect before implementing.** Every workflow routes through `workflows/inspect-project.md`
   first, and patterns must be cited to at least two real files.
2. **Your codebase outranks these documents.** The defaults are for new code. A consistent
   production codebase wins, and the agent must say in its report when it matched your codebase
   instead of a default.
3. **No unapproved migration.** Module restructuring, library swaps, version bumps, architecture
   changes and public-API reshaping all stop at an explicit decision point.
4. **No invented facts.** Versions come from your catalog or a registry; APIs from source or
   first-party docs. `[UNVERIFIED]` is an acceptable answer; a confident guess is not.
5. **Verify, don't assume.** `PASS` means a command was executed. `NOT RUN — <reason>` is honest;
   a false `PASS` is a defect.
6. **Report honestly.** Every task ends with *Not done* and *Observations* sections, present even
   when empty, so an omitted caveat cannot read as no caveat.
7. **Stop rather than guess.** Dirty working tree, already-failing build, wrong task premise,
   conflicting rules — all are stop-and-ask conditions.

---

## Verification

The example is not illustrative — it compiles and runs. From a clean clone:

```
Gradle 9.8.0 · JDK 25 · Xcode 27 · Kotlin 2.4.20 · Compose Multiplatform 1.12.1 · AGP 9.4.1
KSP 2.3.12 · Room 3.0.3 · Koin 4.2.2 · Ktor 3.6.0 · DataStore 1.2.1

gradle allTests compileAndroidMain compileKotlinIosArm64 linkDebugFrameworkIosSimulatorArm64
  → BUILD SUCCESSFUL
     jvmTest                46 / 46
     iosSimulatorArm64Test  45 / 45      91 test executions
     Android, JVM, iOS device and iOS simulator all compile
     Swift smoke file compiles against the generated framework header
```

Reproduce it:

```bash
cd examples/user-profile
export JAVA_HOME=<a JDK that AGP 9 supports — JDK 25 was used>
gradle allTests compileAndroidMain compileKotlinIosArm64 linkDebugFrameworkIosSimulatorArm64
```

Requires Gradle ≥ 9.0. The Android target needs the Android SDK (`compileSdk 36`); the iOS targets
and the Swift check need macOS with Xcode. On Linux, `gradle jvmTest compileAndroidMain` verifies
everything except iOS.

Compiling the example corrected eighteen things that looked right on paper — including that
`value class` in `commonMain` needs both `@JvmInline` **and** an explicit import (each omission
fails on a different target), that Room 3's `Migration.migrate` is a `suspend` function, and that
`google()` is required even in a module with no Android target. The full list is in
[`examples/DECISIONS.md`](examples/DECISIONS.md).

---

## What it does not do

Stated plainly, because a skill that hides its edges is worse than one that names them. Full detail
in [`LIMITATIONS.md`](LIMITATIONS.md).

- **It has never been run end-to-end.** `EVALUATION.md` defines 48 checks and 8 scenarios; none has
  been executed. Static validation and a compiling example are what exist today.
- **39 `[UNVERIFIED]` claims remain** in the references, each tagged with what would settle it —
  Kotlin/Native crash symbolication, Swift export's Gradle DSL, SKIE's Kotlin ceiling, and others.
- **The example is a library, not an app.** No Activity, no navigation host, no Xcode project.
  Android compiles but is never executed; the Swift check is compile-only.
- **Not covered:** Wear OS, TV, Automotive and XR beyond their `targetSdk` floors; Glance widgets;
  server-side code; Compose Multiplatform for web beyond noting it is Beta.
- **Version numbers and store deadlines rot.** Everything is dated 2026-10-03.

---

## Requirements

Nothing is installed into your project — the skill is documentation plus a self-contained example.

| To | You need |
|---|---|
| Use the skill | an Agent Skills host (Claude Code or compatible) |
| Re-verify the example | Gradle ≥ 9.0, a JDK that AGP 9 supports, the Android SDK (`compileSdk 36`) |
| Re-verify the iOS targets and the Swift check | macOS with Xcode |

---

## Sources

First-party documentation wherever it exists — Google for Android and AndroidX, JetBrains for
Kotlin, KMP, Compose Multiplatform and Ktor, Apple for App Store, and each library's owner.
Secondary sources were used only for leads and are labelled `[UNVERIFIED]` until confirmed against
a first-party source.

Every reference document ends with a **Sources** section carrying its URLs and verification date.
The full research record is [`research/RESEARCH.md`](research/RESEARCH.md).

---

## Contributing

The skill's own quality bar applies to changes to it:

- A factual claim needs a first-party source and a date, or an `[UNVERIFIED]` tag.
- A version number is read from a registry, never from memory.
- Code in `examples/` must compile, and the verification record in `examples/README.md` must be
  updated with what was actually run.
- A new workflow needs all ten sections — `EVALUATION.md` § S3 checks this.
- A new document must be routed from `SKILL.md`, or an agent will never load it.
- Conventional Commits, per [`templates/commit-message.md`](templates/commit-message.md).

---

## Licence

<set by the repository owner>
