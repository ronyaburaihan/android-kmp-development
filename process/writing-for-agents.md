# Primitive: Writing for Agents

Adapted from Matt Pocock's `writing-for-agents` skill — see `README.md` § Attribution.

## Purpose

Conventions for writing documents an **agent** reads: this skill, a repository's `AGENTS.md` or
`CLAUDE.md`, a standards document, a workflow, a primitive.

---

## When to invoke

- Editing anything in this skill.
- Writing or trimming a repository's `AGENTS.md` / `CLAUDE.md` / standards document.
- `retro.md` invokes this before proposing any instruction.

---

## The two loads

Every sentence in an always-loaded document costs twice:

| Load | Paid by | Cost |
|---|---|---|
| **Context load** | the agent's window, on every task | crowds out the task |
| **Cognitive load** | the human maintaining the index | they must know what is in there to keep it true |

A sentence must earn both. This is why the always-loaded file is the most expensive place to put
anything, and why `SKILL.md` is a routing table rather than a manual.

---

## Context pointers

A **pointer** names out-of-context material and encodes the condition for reaching it. A pointer's
*wording*, not its target, decides whether the agent reaches the material and how reliably.

```
Weak:    See the Ktor documentation for more on networking.
Strong:  Adding or changing an API call → `references/libraries/ktor-networking.md`
```

The strong form states the trigger condition first. That is what makes a routing table work.

---

## Information hierarchy

Three tiers, by immediacy:

| Tier | Form | Use for |
|---|---|---|
| 1 | In-file step | What **every** path through this document needs |
| 2 | In-file reference | Consulted on demand, but cheap enough to keep inline — a table, a command list |
| 3 | Disclosed reference | A separate file behind a pointer: what only **some** branches reach |

**Inline what every branch needs; push behind a pointer what only some branches reach.** That single
rule is the whole of progressive disclosure.

---

## Rules

1. **Co-locate.** A concept's definition, rules and caveats live together under one heading.
   Scattering them means a reader following a pointer lands on a fragment.
2. **One source of truth per meaning.** Duplicating a rule guarantees the copies drift. Point
   instead. (This is why the workflows cite `references/` rather than restating it.)
3. **Cache only what cannot be looked up.** A fact the agent can find with one `grep` does not need
   to be written down. A version lockstep rule that is wrong in the top three search results does.
4. **Prompt the positive.** State the target behaviour, so the banned one is never spoken. "Inject
   the dispatcher" outperforms "don't hardcode `Dispatchers.IO`". Where a prohibition is genuinely
   load-bearing — a security or correctness boundary — state it as `MUST NOT` and keep it.
5. **Use words from pretraining.** Established terms beat coinages: *repository*, *seam*, *vertical
   slice*, *expand–contract*. A novel name for a known concept forces the agent to learn it before
   it can use it.
6. **Hunt no-ops sentence by sentence.** An instruction the model already obeys by default pays
   context load to say nothing. Delete it.
7. **Sharpen completion criteria before splitting steps.** Two properties make a criterion useful:
   *clarity* — can the agent tell done from not-done? — and *demand* — how much work it requires.
   Sharpen the bound first; it is local and cheap. Only if the bound is irreducibly fuzzy **and** you
   observe the agent rushing the later steps should you split the document.
8. **Mark the rule level and the provenance.** In this skill every normative statement carries
   `MUST` / `SHOULD` / `MAY` and `[OFFICIAL]` / `[DEFAULT]` / `[UNVERIFIED]`. An unmarked claim is
   read as fact, and an engineering preference presented as an official requirement is a defect in
   the document.

---

## Specifics for this skill

| Document | Load | Rule |
|---|---|---|
| `SKILL.md` | always | Routing table and non-negotiable rules only. Anything a task-specific branch needs goes behind a pointer. |
| `install/AGENTS.md` and its adapters | always, for agents with no skill system | ~7 KB ceiling. Codex caps combined instructions at 32 KiB; most agents load the file in full. |
| `references/*` | on demand | One topic per file. Correct code, incorrect code with the reason, Android/iOS differences, official links, verification date. |
| `workflows/*` | on demand | The ten mandatory sections. Procedure, decision points, report. |
| `process/*` | on demand | Purpose · When to invoke · The mechanic · Android/KMP specifics · Anti-patterns · It's working if · Where it fits. |
| `examples/*` | on demand | **MUST NOT** claim a verification the record in `examples/README.md` does not support. |

Adding a document **MUST** come with its routing-table row. A file nothing points at will not be
read, and an agent cannot route to what it cannot see.

---

## Anti-patterns

| Anti-pattern | Why it fails |
|---|---|
| Vague pointer ("see the docs") | No trigger condition; the agent never reaches it, or reaches it always |
| Same rule in three files | They drift; the agent follows whichever it loaded |
| Prohibition-only phrasing | Names the wrong behaviour without naming the right one |
| Reference material in the always-loaded file | Pays context on every task for a few tasks' benefit |
| A new document with no routing row | Invisible |
| Unmarked claims | A preference gets followed as a requirement |
| Restating what a lint rule enforces | Costs context to duplicate a mechanical check (`retro.md` § 2) |

---

## It's working if

- Every always-loaded sentence would be missed if deleted.
- Every pointer states its trigger condition before its target.
- Each meaning appears in exactly one place.
- Every normative statement carries a rule level and a provenance tag.
- Every new document has a routing-table row.
- At least one deletion was considered on every edit.

---

## Where it fits

Meta. Invoked when editing agent-facing documents; `retro.md` invokes it before proposing an
instruction. Contributing rules for this skill: `../README.md` § Contributing.
