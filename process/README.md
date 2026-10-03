# Process Primitives

Domain-agnostic engineering *mechanics*, made concrete for Android and Kotlin Multiplatform.

`../workflows/` answers **"what are the steps for this Android task?"**
`../references/` answers **"what is true about this API?"**
`process/` answers **"how do I work?"** — interview, test-first, diagnose, review, slice, hand off, reflect.

A workflow is a procedure you run. A primitive is a mechanic a workflow *invokes*. `add-feature.md`
drives `tdd.md` at its seams and closes with `two-axis-review.md`; `clarify-requirements.md` runs
`grilling.md`; `diagnose-and-fix-bug.md` cannot pass its first step without `diagnostic-loop.md`.

---

## Why this layer exists

The three layers fail differently, and each one covers for the others:

| Layer | Failure it prevents |
|---|---|
| `references/` | Invented APIs, wrong versions, deprecated practice |
| `workflows/` | Skipped inspection, unapproved migration, unverified claims |
| `process/` | **Misalignment** — building the wrong thing correctly — and **non-functional code**: code that compiles, reads well, matches every convention, and does not work |

A correct `StateFlow` that answers the wrong requirement is still a defect. No reference document
catches that.

---

## The primitives

| Primitive | Mechanic | Invoked by |
|---|---|---|
| `grilling.md` | Frontier interview — ask the questions answerable *now*, in one numbered round, each with a recommended answer; dispatch a subagent for facts instead of asking | `../workflows/clarify-requirements.md`, `../workflows/plan-feature.md`, `../workflows/assess-kmp-adoption.md` |
| `vertical-slice.md` | Cut work into tracer bullets: narrow but complete paths through every layer, each demoable, each sized to one context window | `../workflows/plan-feature.md`, `../workflows/migrate-module-to-kmp.md` |
| `tdd.md` | Red before green, at pre-agreed seams. Names the actual KMP seams and the tool for each | `../workflows/add-feature.md`, `../workflows/add-network-endpoint.md`, `../workflows/add-persistence.md`, `../workflows/diagnose-and-fix-bug.md` |
| `diagnostic-loop.md` | Build a red-capable, deterministic, fast, agent-runnable command **before** hypothesising. Ranked list of loop constructions for Android/KMP | `../workflows/diagnose-and-fix-bug.md`, `../workflows/audit-compose-performance.md` |
| `two-axis-review.md` | Review *standards* and *spec* as independent axes, in parallel, reported separately and never merged into one verdict | `../workflows/review-code.md`, `../workflows/add-feature.md` |
| `handoff.md` | Compact a session into a document the next agent can resume from — including the toolchain and the last green Gradle command | any session hitting a context limit |
| `retro.md` | After a session, propose changes to the *environment* — checks, pointers, standards — not to the code | end of a session, on request |
| `writing-for-agents.md` | Conventions for editing this skill, `AGENTS.md`, or `CLAUDE.md`. Context load vs cognitive load | any task that edits an agent-facing document |

---

## Shape of a primitive

Primitives do **not** use the ten mandatory sections of `../workflows/`. A workflow is a procedure
with inputs, decision points and a report; a primitive is a mechanic with a loop and a bound. Each
primitive has: **Purpose · When to invoke · The mechanic · Android/KMP specifics · Anti-patterns ·
It's working if · Where it fits.**

The standing contract in `../workflows/README.md` still binds every primitive. A primitive may add
constraints; none may relax inspect-first, no-unapproved-migration, or verify-don't-assume.

---

## Attribution

The *mechanics* in `grilling.md`, `tdd.md`, `diagnostic-loop.md`, `two-axis-review.md`,
`vertical-slice.md`, `handoff.md`, `retro.md` and `writing-for-agents.md` are adapted from
**[Matt Pocock's skills repository](https://github.com/mattpocock/skills)** — the frontier-interview
model, testing only at pre-agreed seams, feedback-loop-before-hypothesis, the two-axis review, and
tracer-bullet slicing are his. Used and adapted under the MIT licence:

```
MIT License

Copyright (c) 2026 Matt Pocock

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

What is **not** his: every Android, Kotlin, KMP, Compose, Ktor, Koin, Room and Gradle specific in
these files — the seam table, the loop constructions, the smell baseline, the slice rules for
package-by-layer. His skills are deliberately domain-blind. Mapping each mechanic onto a real
toolchain is where this layer earns its place, and any error in that mapping is ours, not his.
