# android-kmp-development

A production-grade Agent Skill for developing, debugging, reviewing, testing, refactoring, and releasing **Android and Kotlin Multiplatform** applications on existing codebases.

**Verified:** 2026-10-03. Versions and platform deadlines rot; `references/version-matrix.md` is the single place they live and says where to re-verify.

## What it is

| Part | Contents |
|---|---|
| `SKILL.md` | Entry point. Role, non-negotiable rules, and a **routing table** so an agent loads only the two or three documents a task needs. |
| `references/` |       32 topic documents. Every rule tagged **MUST / SHOULD / MAY** and **[OFFICIAL] / [DEFAULT] / [UNVERIFIED]**. |
| `workflows/` |       24 step-by-step procedures for an agent, each with objective, inputs, inspection, procedure, decision points, rules, validation, failure handling, completion criteria, and report format. |
| `examples/` | A **compiled and tested** KMP vertical slice — Android, JVM and iOS targets; Compose UI; Room 3 with a migration test; a Swift-facing facade whose smoke file compiles against the framework — with the rationale for every decision. |
| `templates/` | Fill-in skeletons: spec, plan, report, ADR, PR, bug report, release checklist, version catalog, module build file. |
| `EVALUATION.md` | How to judge whether an agent is using the skill correctly. |
| `LIMITATIONS.md` | What is unverified, missing, or out of scope. |
| `research/RESEARCH.md` | The sourced research the skill was built from. |

## Installation

The skill is a plain directory containing `SKILL.md`. A host discovers it by scanning its skills
folder and reading the YAML frontmatter (`name`, `description`).

> **The directory must be named `android-kmp-development`** — matching the `name` in the
> frontmatter. The git repository is named `…-skill`, so a bare `git clone` produces the wrong
> directory name. The commands below handle that.

### Claude Code — all projects (user scope)

```bash
mkdir -p ~/.claude/skills
git clone https://github.com/ronyaburaihan/android-kmp-development-skill.git \
  ~/.claude/skills/android-kmp-development
```

### Claude Code — one project (project scope)

```bash
mkdir -p .claude/skills
git clone https://github.com/ronyaburaihan/android-kmp-development-skill.git \
  .claude/skills/android-kmp-development
```

Project scope wins over user scope when both are present — useful for pinning a project to a
particular revision.

### From a local copy (no git)

```bash
mkdir -p ~/.claude/skills
cp -r /path/to/android-kmp-development ~/.claude/skills/
```

Download-and-extract works too; just rename the extracted folder to `android-kmp-development`.

### Other Agent-Skills-compatible hosts

Copy the directory into the host's skills location. Nothing in the skill is Claude Code specific —
`SKILL.md` is the entry point and every internal path is relative.

### Verify it installed

```bash
ls ~/.claude/skills/android-kmp-development/SKILL.md     # must exist
head -5 ~/.claude/skills/android-kmp-development/SKILL.md # must show the frontmatter
```

In a new Claude Code session, `/skills` lists it, and `/android-kmp-development` invokes it
explicitly. Otherwise it triggers on its description — Android, Kotlin, KMP, Compose, Compose
Multiplatform, Koin, Ktor, Room, DataStore, coroutines/Flow, iOS interop, or Play/App Store
release work. A skill added mid-session is not picked up until the session restarts.

### Update

```bash
git -C ~/.claude/skills/android-kmp-development pull
```

Re-read `references/version-matrix.md` after updating: it carries a verification date, and version
numbers and store deadlines go stale.

### Uninstall

```bash
rm -rf ~/.claude/skills/android-kmp-development
```

### Optional: re-verify the compiled example

Nothing needs installing into your own project — the example is self-contained. To reproduce its
verification record (`examples/README.md`):

```bash
cd ~/.claude/skills/android-kmp-development/examples/user-profile
export JAVA_HOME=<a JDK that AGP 9 supports — JDK 25 was used>
gradle allTests compileAndroidMain compileKotlinIosArm64 linkDebugFrameworkIosSimulatorArm64
```

Requires Gradle ≥ 9.0. The Android target needs the Android SDK (`compileSdk 36`); the iOS targets
and the Swift check need macOS with Xcode. On Linux, `gradle jvmTest compileAndroidMain` verifies
everything except iOS.

## Design principles the skill enforces

1. **Inspect before implementing.** Every workflow routes through `workflows/inspect-project.md` first.
2. **The codebase outranks the documents.** Defaults are for new code; a consistent production codebase wins, and the agent must say when it matched the codebase instead.
3. **No unapproved migration.** Module restructuring, library swaps, version bumps, and public-API changes stop at a decision point.
4. **No invented facts.** Versions come from the catalog or the registry; APIs from source or first-party docs; `[UNVERIFIED]` is an acceptable answer, a guess is not.
5. **Verify, don't assume.** `PASS` means executed. `NOT RUN — <reason>` is honest; a false `PASS` is a defect.
6. **Report honestly.** Every task ends with *Not done* and *Observations* sections, even when empty.

## Where the research came from

First-party documentation (Google, JetBrains, Apple, library owners) wherever it exists; secondary sources only for leads, labelled `[UNVERIFIED]` until confirmed. Full source list with dates: `research/RESEARCH.md` and the **Sources** section of each reference document.

## Licence

<set by the repository owner>
