# Primitive: Retro

Adapted from Matt Pocock's `retro` skill — see `README.md` § Attribution.

## Purpose

After a session, propose changes to the **environment** — checks, pointers, standards, instructions —
so the next session goes better. A retro reviews the *agent's working conditions*, not the code.

Code review is `two-axis-review.md`. This is a different question: what made the session harder than
it needed to be?

---

## When to invoke

**User-invoked only**, at the end of a session. Changes nothing by itself: it produces ranked
candidates and says where each belongs.

---

## The mechanic

1. Read `writing-for-agents.md` first — it governs how any resulting instruction is written.
2. Review the session: what was hunted for, what broke, what was re-run, what the user corrected.
3. Collect candidates in the categories below.
4. **Rank by severity** and present with the destination for each. **MUST NOT** apply them unasked.

---

## Categories

### 1. Navigation

Did the agent spend turns *finding* things? A session that hunts for where a feature lives, which
module owns a binding, or where a convention is defined is paying for a missing pointer.

**Destination:** a navigation pointer in `PROJECT_STRUCTURE.md`, a module `README.md`, or the routing
table in `AGENTS.md` / `SKILL.md`.

### 2. Automated checks — prefer these

Could a lint rule, a compiler setting, or a test have caught the mistake mechanically? A check is
worth more than an instruction: it fires every time, costs no context, and does not depend on an
agent remembering.

**MUST** check whether the repository already has the check and simply does not run it, before
proposing a new one. In this stack the usual candidates:

| Problem observed | Mechanical fix |
|---|---|
| Formatting or style argued over | `ktlintCheck` / `spotlessCheck` wired into the build |
| A code smell repeated across the session | a `detekt` rule |
| An Android API misuse | Android Lint — and `lintVitalRelease` in CI |
| A public or Swift-facing API changed unnoticed | `apiDump` / binary-compatibility-validator |
| A Room migration shipped without a schema | schema export committed, migration test required |
| Versions drifting out of lockstep | a version-catalog check in CI; `../references/version-matrix.md` |
| A release-only R8 failure found late | `bundleRelease` in CI, not only `assembleDebug` |
| Recomposition regressions | Compose compiler metrics, or a Macrobenchmark gate |
| iOS breaking after a shared change | `linkDebugFrameworkIosSimulatorArm64` in CI |

An unguarded repository is itself the finding: if nothing mechanical runs on a merge, that outranks
every prose instruction. `../references/quality/ci-pipeline.md`.

### 3. Coding standards

Does a *reviewer* need a new rule? Standards are read during review, where context is cheap — not by
the implementing agent, which is under context pressure.

**Reserve a standards document for genuine judgement calls.** Anything mechanical belongs in
category 2. "Prefer `sealed interface` for closed error hierarchies" is a judgement call; "4-space
indent" is a lint rule and **MUST NOT** be written as prose.

### 4. `AGENTS.md` / `CLAUDE.md` economy

These are pushed into **every** agent's context on **every** task. Use sparingly. Candidates to move
out:

- An instruction that only matters during review → the standards document.
- An instruction that is mechanically checkable → category 2.
- Reference material → a document behind a pointer.

If the file has grown past the point where an agent reliably follows all of it, that is the finding.

### 5. Tool economy

Which calls were expensive or wasteful? In this stack, Gradle dominates:

- `allTests` run per red-green cycle instead of one test class (`tdd.md`).
- A full `clean` where none was needed.
- Configuration re-run because a build file was edited mid-loop.
- A broad `grep` over `build/` output directories.
- Reading a whole reference document when the routing table named a section.

### 6. No-ops

Hunt sentence by sentence for instructions the model already obeys by default. A no-op costs context
on every task and buys nothing. Equally: an instruction that was present and *did not change
behaviour* this session is a candidate for deletion, not for emphasis.

---

## Android/KMP specifics

Three findings recur and are worth checking for explicitly:

1. **The toolchain was rediscovered.** If the session spent turns finding the right JDK or Gradle
   version, that belongs in the repo's README or a `.sdkmanrc`/toolchain declaration — see the
   Toolchain section of `handoff.md`.
2. **Only one target was verified.** If `jvmTest` was treated as sufficient, the gap is a CI matrix,
   not an instruction.
3. **A green debug build was treated as release-ready.** R8 failures appear only in `bundleRelease`;
   the fix is a CI job.

---

## Anti-patterns

| Anti-pattern | Why it fails |
|---|---|
| Reviewing the code instead of the environment | That is `two-axis-review.md`; this question goes unanswered |
| Proposing prose where a check would do | Depends on recall, costs context, fires inconsistently |
| Adding to `AGENTS.md` by default | The always-loaded file is the most expensive place to put anything |
| Writing a mechanical rule as a standard | Lint enforces it for free and exactly |
| Applying changes without asking | A retro proposes; the user decides |
| Presenting candidates unranked | The expensive fix and the trivial one look alike |

---

## It's working if

- Candidates are ranked by severity, each with a destination.
- Every mechanical candidate was checked against what the repo already runs.
- At least one candidate is a **deletion** — a no-op found and named.
- Nothing was changed without the user asking.
- No candidate restates something a check already enforces.

---

## Where it fits

End of a session, after the task report. Outputs land in the repository's own `AGENTS.md`, standards
document, CI configuration, or `PROJECT_STRUCTURE.md` — never in this skill, unless the user is
working on this skill.
