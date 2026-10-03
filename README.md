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

The skill is a directory. Install it wherever your agent host discovers skills.

**Claude Code (project-scoped):**
```bash
mkdir -p .claude/skills
cp -r /path/to/android-kmp-development .claude/skills/
```

**Claude Code (user-scoped, all projects):**
```bash
mkdir -p ~/.claude/skills
cp -r /path/to/android-kmp-development ~/.claude/skills/
```

**Other Agent-Skills-compatible hosts:** copy the directory into the host's skills location; the host reads `SKILL.md`'s YAML frontmatter (`name`, `description`) for discovery.

Invoke by task — the description triggers on Android/KMP/Compose/Kotlin work — or explicitly by name.

**Nothing in the skill needs installing into the target project.** The compiled example is self-contained; to re-verify it:

```bash
cd examples/user-profile
# Gradle ≥ 9.0 and a JDK AGP 9 supports (JDK 25 was used); iOS targets need macOS + Xcode; Android needs the SDK
gradle allTests compileAndroidMain compileKotlinIosArm64 linkDebugFrameworkIosSimulatorArm64
```

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
