# Primitive: Two-Axis Review

Adapted from Matt Pocock's `code-review` skill — see `README.md` § Attribution. The smell baseline
below is Kotlin/Android/KMP specific.

## Purpose

Review a change along two **independent** axes and report them separately:

| Axis | Question | Sources |
|---|---|---|
| **Standards** | Is this good code *in this repository*? | the repo's own conventions, then `../references/`, then the smell baseline below |
| **Spec** | Does this do what was asked — all of it, and nothing more? | the spec, plan, ticket, or acceptance criteria |

A change can pass Standards and fail Spec (clean code, wrong requirement) or pass Spec and fail
Standards (right behaviour, built in a way the codebase will reject). **MUST NOT** merge them into a
single verdict: one axis's findings mask the other's.

---

## When to invoke

- `../workflows/review-code.md` — the review procedure proper.
- `../workflows/add-feature.md` § 4.9 — close implementation with a review before reporting done.
- After any multi-file change, before a commit the user asked for.

---

## The mechanic

### 1. Pin the fixed point — MUST

Every review is "changes since **X**". Confirm X resolves:

```bash
git rev-parse --verify <fixed-point>            # a commit, tag, branch, or merge base
git diff --stat <fixed-point>...HEAD            # three dots — compares against the merge base
git log --oneline <fixed-point>..HEAD
```

**MUST** verify the diff is non-empty before reviewing. **MUST NOT** guess the fixed point. If the
user did not supply one, ask — with a recommendation (usually the base branch's merge base).

### 2. Find the spec

In order: an issue reference in the commit messages → a path the user supplied → a matching document
under `docs/`, `specs/` or `.scratch/` → the output of `../workflows/clarify-requirements.md` earlier
in this session. If none surfaces, **MUST** ask rather than invent the intent.

If there is genuinely no spec, say so and run the Standards axis alone. **MUST NOT** reconstruct a
spec from the diff — a spec inferred from the code can never find a missing requirement.

### 3. Assemble the Standards sources

Precedence, highest first:

1. **The repository's own conventions** — a `CONTRIBUTING.md`, `CODING_STANDARDS.md`,
   `PROJECT_STRUCTURE.md`, `detekt.yml`, `.editorconfig`, `lint.xml`, or the pattern in the nearest
   comparable file. **The repo overrides everything below.**
2. **`../references/`** — defaults for new code, with `MUST` / `SHOULD` / `MAY` and provenance tags.
   `../references/deprecations.md` and `../references/version-matrix.md` catch the most real defects.
3. **The smell baseline** below — heuristics, not rules. A baseline smell is a *question*, never a
   finding on its own.

### 4. Run the two axes independently

**If the host supports subagents:** spawn one per axis, in parallel, each with a fresh context. A
fresh context matters — an agent that just wrote the code cannot review it without bias.

Each subagent receives: the diff command, the commit log, its own sources, and:

> Report violations of the documented standards, citing the source file for each. Then baseline
> smells, labelled as smells. Under 400 words. Cite `file:line` for every finding.

> Report missing requirements, scope creep, and anything that looks wrong against this spec. Under
> 400 words. Cite `file:line` and the criterion for every finding.

**If the host has no subagents:** run two sequential passes, each reading only its own axis's
sources, and **MUST** state in the report that the axes were run sequentially in one context rather
than isolated. Honest degradation; not a silent one.

### 5. Aggregate without merging — MUST

Present each axis under its own heading, findings as the axis reported them. Close with the count per
axis and the worst item in each. **MUST NOT** pick a single overall winner across axes.

---

## The Kotlin / Android / KMP smell baseline

Heuristics. Each is a prompt to look, and each has a reference that decides it.

### Correctness and concurrency

| Smell | Reference |
|---|---|
| `GlobalScope`, or a scope with no lifecycle owner | `../references/kotlin/coroutines-and-flow.md` |
| `CancellationException` caught and swallowed by a broad `catch (e: Exception)` | same |
| `runBlocking` on a production path | same |
| Hardcoded `Dispatchers.*` instead of an injected dispatcher | same |
| `MutableStateFlow` / `MutableSharedFlow` exposed publicly | `../references/architecture/mvvm-udf.md` |
| Unbounded `Channel`, or an `Effect` channel that drops on no subscriber | `../templates/structure/PROJECT_STRUCTURE.md` |
| `!!`, or a platform-type dereference with no null check | `../references/kotlin/language-essentials.md` |
| `collectAsState` where lifecycle-aware collection is required | `../references/android/compose-ui.md` |
| Suspend work started in composition rather than an effect | same |

### Layering

| Smell | Reference |
|---|---|
| A DAO, DataStore, `HttpClient`, `SharedPreferences` or platform SDK reached from a ViewModel or composable | `../references/android/app-architecture.md` |
| A `Context`, `Activity`, `android.*` or Apple type in `commonMain` | `../references/kmp/project-structure.md` |
| A DTO or Room entity crossing into the UI layer | `../references/architecture/clean-architecture.md` |
| Business logic in a composable | `../references/android/compose-ui.md` |
| A new file in a package the structure convention does not put it in | `../templates/structure/PROJECT_STRUCTURE.md` |

### KMP and iOS

| Smell | Reference |
|---|---|
| `Flow`, generics on an interface, default arguments, or a sealed hierarchy on the Swift-facing surface | `../references/kmp/ios-interop.md` |
| `suspend` exported to Swift without `@Throws` | same |
| `expect class` where an interface plus DI would do | same |
| A target added, or a source set introduced, without an approval gate | `../workflows/README.md` § 3 |

### Build and dependency

| Smell | Reference |
|---|---|
| A dependency coordinate or version written inline instead of in the version catalog | `../references/architecture/modularization.md` |
| Kotlin / Compose-compiler / KSP / CMP versions out of lockstep | `../references/version-matrix.md` |
| A deprecated API used in new code | `../references/deprecations.md` |
| A new dependency introduced without a decision point | `../workflows/README.md` § 3 |

### Product surface

| Smell | Reference |
|---|---|
| A hardcoded user-visible string | `../references/android/localization.md` |
| An interactive element with no accessible label; an image with no `contentDescription` decision | `../references/android/accessibility.md` |
| A secret, token, or key in source or in a build file | `../references/quality/security.md` |
| `TODO()`, `NotImplementedError`, or a stub on a delivered path | `../workflows/README.md` § 4 |
| Loading, empty, or error state unhandled | `../references/architecture/mvvm-udf.md` |

### Generic (Fowler), lowest priority

Mysterious name · duplicated code · long function · large class · feature envy · data clumps ·
primitive obsession · shotgun surgery · speculative generality.

**MUST NOT** report a generic smell above a correctness finding. Ranking is part of the review.

---

## Anti-patterns

| Anti-pattern | Why it fails |
|---|---|
| One merged verdict | The axis with more findings hides the other |
| Reviewing without a fixed point | "Changes" is undefined; the review drifts over unrelated history |
| Inferring the spec from the diff | Structurally cannot find a missing requirement |
| Reviewing your own work in the same context | You re-read your intent, not the code |
| Reporting style nits alongside a crash | Buries the finding that matters |
| Rewriting the code | A review is a report. It changes no files unless the user asks afterwards |
| Citing `../references/` over a consistent codebase convention | Breaks standing contract rule 2 |
| Findings with no `file:line` | Unactionable, and unverifiable |

---

## It's working if

- The fixed point was resolved with `git rev-parse` and the diff was confirmed non-empty.
- Two separate blocks appear in the report, never merged.
- Every standards finding cites its source document; every spec finding cites its criterion.
- Every finding carries `file:line`.
- The report names the worst item **per axis**, with no overall winner.
- Spec-axis absence is stated explicitly rather than papered over.
- No file was modified.

---

## Where it fits

```
add-feature / refactor / diagnose-and-fix-bug
   → two-axis-review → (user asks) → fix → commit
```

Full procedure, decision points and report skeleton: `../workflows/review-code.md`.
PR body: `../templates/pr-description.md`.
