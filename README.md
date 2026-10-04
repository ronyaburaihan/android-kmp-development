# android-kmp-development

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-support-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/ronyaburaihan)

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

It will also build the wrong thing correctly: a `StateFlow` that is idiomatic, conventional,
well-named, and answers a requirement nobody asked for. No amount of API accuracy catches that.

This skill combines three layers to cover both failures — **official documentation** (what is true),
**engineering standards** (how this codebase does it), and **process** (how to work: align before
building, red before green, a failing command before a theory) — with every claim labelled by how
well it is known.

---

## What's inside

| Part | Contents |
|---|---|
| [`SKILL.md`](SKILL.md) | The entry point. Role, nine non-negotiable principles, the rule-level legend, and a **routing table** so a focused task loads two or three documents instead of sixty. |
| [`references/`](references/) | **35 topic documents.** Every normative statement tagged **MUST / SHOULD / MAY** and **[OFFICIAL] / [DEFAULT] / [UNVERIFIED]**. |
| [`process/`](process/README.md) | **8 engineering process primitives** — frontier interview, vertical slicing, red-green at pre-agreed seams, feedback-loop-before-hypothesis, two-axis review, handoff, retro, writing-for-agents. Mechanics adapted from [Matt Pocock's skills](https://github.com/mattpocock/skills) (MIT), mapped onto this toolchain. |
| [`workflows/`](workflows/) | **24 step-by-step procedures**, each with objective, required inputs, project inspection, procedure, decision points, implementation rules, validation requirements, failure handling, completion criteria and report format. |
| [`examples/`](examples/) | A **compiled and tested** KMP vertical slice — Android, JVM and both iOS targets; Compose UI; Room 3 with a seeded migration test; a Swift-facing facade whose smoke file compiles against the generated framework — plus the rationale for every decision. |
| [`install/`](install/README.md) | Bootstrap adapters and an installer for **any** agent — Claude Code, Codex, OpenCode, Cursor, Kiro, Trae, Windsurf, Antigravity, Copilot and every
`AGENTS.md` reader. |
| [`templates/`](templates/) | 15 fill-in skeletons: spec, plan, task report, ADR, PR, bug report, commit message, release checklist, version catalog, module build file, CI config, and a project-structure convention with a scaffold script. |
| [`EVALUATION.md`](EVALUATION.md) | 60 checks and 12 scenarios for judging whether an agent is actually using the skill correctly. |
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

Works with **any** coding agent. One command:

```bash
git clone https://github.com/ronyaburaihan/android-kmp-development-skill.git
cd android-kmp-development-skill
./install/install.sh <agent> [--scope user|project]
```

| `<agent>` | Covers | Project scope | User scope |
|---|---|---|---|
| `claude` | Claude Code — **native skill**, best fidelity | `.claude/skills/` | `~/.claude/skills/` |
| `agents` | the open `AGENTS.md` standard — **24 products**, below | `./AGENTS.md` | — |
| `codex` | OpenAI Codex CLI | `./AGENTS.md` | `~/.codex/AGENTS.md` |
| `opencode` | OpenCode | `./AGENTS.md` | `~/.config/opencode/AGENTS.md` |
| `cursor` | Cursor (`.mdc` rule — adds `globs`, `alwaysApply`) | `.cursor/rules/` | — |
| `kiro` | AWS Kiro (steering, `inclusion: always`) | `.kiro/steering/` | `~/.kiro/steering/` |
| `trae` | Trae | `.trae/rules/` | `~/.trae/user_rules/` |
| `windsurf` | Windsurf (`trigger: always_on`) | `.windsurf/rules/` | — |
| `antigravity` | Google Antigravity ("agy") | `.agents/rules/` | `~/.gemini/config/rules/` |
| `copilot` | GitHub Copilot | `.github/copilot-instructions.md` | — |
| `all` | `AGENTS.md` + every project-scope adapter | all of the above | — |

`agents` alone already covers **Codex, OpenCode, Cursor, Aider, goose, Zed, Warp, VS Code, GitHub
Copilot, Gemini CLI, Jules, Amp, Junie, Devin, Windsurf, Factory, RooCode, Kilo Code, Augment Code,
Ona, Phoenix, Semgrep** and UiPath Autopilot — every product listed at
[agents.md](https://agents.md/). **Google Antigravity** reads `AGENTS.md` natively too (since IDE
1.20.5), so `agents` works there as well — the `antigravity` adapter exists for the user scope and
the declared trigger. The dedicated adapters exist only where an agent's own format adds something
`AGENTS.md` cannot express.

**Restart the agent session afterwards** — instruction files are read at session start.

### How it works, and why it isn't just a paste

The skill is ~24,000 lines. Most agents load their instruction file fully into context every
session, and Codex caps combined instructions at **32 KiB**. So each agent gets an **~8 KB
bootstrap** carrying the non-negotiable rules, the rule-level legend, the stop conditions and a
**routing table** naming the file to open per task; the agent reads the rest on demand. That
reproduces Claude Code's progressive disclosure for agents with no skill system.

Every adapter is generated from one source (`install/AGENTS.md`), and an existing instruction file
is backed up and appended to rather than overwritten. Full detail, manual install and verification
steps: **[`install/README.md`](install/README.md)**.

### Claude Code without the script

```bash
mkdir -p ~/.claude/skills
git clone https://github.com/ronyaburaihan/android-kmp-development-skill.git \
  ~/.claude/skills/android-kmp-development
```

> The directory **must** be named `android-kmp-development`, matching the `name` in `SKILL.md`'s
> frontmatter. This repository is named `…-skill`, so a bare `git clone` creates the wrong directory
> name and the skill will not resolve.

Verify, then restart the session:

```bash
head -5 ~/.claude/skills/android-kmp-development/SKILL.md   # must show the YAML frontmatter
```

`/skills` lists it; `/android-kmp-development` invokes it explicitly.

### Update / uninstall

```bash
git -C ~/.claude/skills/android-kmp-development pull   # then re-read references/version-matrix.md
rm -rf ~/.claude/skills/android-kmp-development
```

Re-run `install.sh` after an update to refresh the adapters for other agents.

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
presentation` with MVVM/UDF screen sets — ships at
[`templates/structure/`](templates/structure/PROJECT_STRUCTURE.md), with a `scaffold.sh` that
generates the tree, the navigation key types and screen sets.

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
8. **Align before building.** Facts about your codebase get looked up; only decisions reach you,
   in one numbered round with a recommendation for each.
9. **Signal before theory, red before green.** A bug gets a reproducible failing command before any
   hypothesis. A feature gets a failing test at an agreed seam before the code that satisfies it.

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

- **It has never been run end-to-end.** `EVALUATION.md` defines 60 checks and 12 scenarios; none has
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

The process layer's mechanics come from **[Matt Pocock's skills repository](https://github.com/mattpocock/skills)**
(MIT, Copyright © 2026 Matt Pocock) — the frontier interview, testing only at pre-agreed seams,
feedback-loop-before-hypothesis, the two-axis review and tracer-bullet slicing are his. Scope of the
adaptation, and the licence notice, are in [`process/README.md`](process/README.md).

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

## Support

If this saved you time, you can [buy me a coffee](https://buymeacoffee.com/ronyaburaihan).

<a href="https://buymeacoffee.com/ronyaburaihan" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" height="48" width="174"></a>

Starring the repository helps too, and issues reporting a wrong or stale claim are the most useful
contribution — see [Contributing](#contributing).

---

## License

[MIT](LICENSE) © Abu Raihan Rony

The skill is documentation, templates and an example project; use it in commercial and private work
without restriction. The attribution requirement is the MIT notice, not a credit line in your app.
