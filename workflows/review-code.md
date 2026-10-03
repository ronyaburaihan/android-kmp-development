# Workflow: Review Code

Standing contract: `README.md`. Prerequisite: `inspect-project.md` (light — enough to know the codebase's conventions, which the review judges against).

**Primitives:** `../process/two-axis-review.md` — standards and spec as independent axes.

---

## 1. Objective

Review a diff, branch, or set of files for **defects first**, then for convention violations, and report findings that are specific, verified, and ranked — without rewriting the author's code.

A review is a report. It changes no files unless the user asks for fixes afterwards.

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| What to review: diff, PR, branch, or files | yes | default: the current uncommitted diff |
| The intent — what the change is supposed to do | yes | **SHOULD** read the PR description / commit messages; **MUST** ask if absent |
| Depth: quick (defects only) or full (defects + conventions + tests) | no | default: full |
| Known constraints (deadline, hotfix) | no | affects what is blocking vs. advisory |

---

## 3. Initial project inspection

```bash
git diff --stat <base>...HEAD
git diff <base>...HEAD -- '*.kt' '*.kts' '*.xml' '*.toml'
# conventions the diff must match
# (nearest comparable existing code to each changed file)
```

**MUST** read the **whole** of each changed file, not only the hunks — a hunk that looks fine often breaks an invariant visible only in the surrounding code.

**MUST** identify the comparable existing code for each new type, so "does not match the codebase" is a sourced finding.

**MUST** check whether the diff touches: a shared type, a public API, a Swift-facing type, persisted data, auth/storage/transport, build configuration, or a test file. Each raises the bar.

---

## 4. Step-by-step procedure

Run `../process/two-axis-review.md`. This procedure is its Android/KMP instance: **4.1 is the Spec
axis; 4.2–4.4 are the Standards axis.** Keep them independent — a change can pass one and fail the
other, and a merged verdict hides whichever axis had fewer findings.

### 4.0 Pin the fixed point — MUST

```bash
git rev-parse --verify <fixed-point>       # a commit, tag, branch, or merge base
git diff --stat <fixed-point>...HEAD       # three dots — against the merge base
git log --oneline <fixed-point>..HEAD
```

**MUST** confirm the diff is non-empty before reviewing. **MUST NOT** guess the fixed point — ask,
with a recommendation (usually the base branch's merge base).

**Where the host supports subagents**, run the two axes as parallel subagents with fresh context,
each under 400 words, each citing `file:line`. An agent that just wrote the code cannot review it
without bias. Where it does not, run two sequential passes and **MUST** say so in the report.

### 4.1 Spec axis — verify the intent is met

Does the change do what it claims? Trace each acceptance criterion or stated goal to the code. A change that does something else well is still wrong.

### 4.2 Standards axis — hunt defects, in this order

Sources, highest precedence first: the repository's own conventions, then `../references/`, then the
smell baseline in `../process/two-axis-review.md`. **The repo overrides both.**

| Priority | Look for | Reference |
|---|---|---|
| 1 | **Correctness**: wrong logic, off-by-one, null path, race, missing state | `../references/kotlin/language-essentials.md` |
| 2 | **Cancellation / exceptions**: `catch (e: Exception)` without rethrowing `CancellationException`; `runCatching` in a coroutine; `GlobalScope`; hardcoded dispatchers | `../references/kotlin/coroutines-and-flow.md` |
| 3 | **Security**: secret in source, token in plaintext store, verbose logging, cleartext, `addJavascriptInterface`, mutable `PendingIntent` | `../references/quality/security.md` |
| 4 | **Data**: schema change without migration, destructive fallback, duplicated DataStore key, DAO/entity leaking above the data layer | `../references/libraries/room-datastore.md` |
| 5 | **Layering**: ViewModel touching a DAO/HTTP/SDK; DTO in `UiState`; repository interface in the wrong module | `../references/android/app-architecture.md`, `../references/architecture/clean-architecture.md` |
| 6 | **Compose**: backwards write, missing lazy keys, `remember` without keys, ViewModel in a content composable, `collectAsState` on a ViewModel flow | `../references/android/compose-ui.md` |
| 7 | **KMP boundary**: platform import in `commonMain`; `Flow`/generics/default args on an exported type; missing `@Throws`; `expect class` where an interface would do | `../references/kmp/ios-interop.md`, `../references/kmp/project-structure.md` |
| 8 | **Deprecations** | `../references/deprecations.md` |
| 9 | **Build**: version lockstep, bare `ksp(...)` in KMP, keep-rule changes, new dependency | `../references/version-matrix.md` |

**MUST** verify each suspected defect — read the surrounding code, trace the call, or run the test — before reporting it. A review that reports a non-defect costs the author's trust.

### 4.3 Check the tests

- New behaviour has a test; the test would fail if the behaviour were broken.
- Existing tests are not modified, skipped, or deleted — if they are, **MUST** flag it as a top finding.
- No `Thread.sleep`, retries, or JUnit/mocking frameworks in `commonTest`.
- Fakes over mocks, matching the codebase.

### 4.4 Check conventions — against the codebase, not the reference set

**MUST** judge style against the nearest comparable existing code. A reference-set default the codebase does not follow is **not** a finding. A deviation from the codebase's own pattern is.

### 4.5 Rank and write

Each finding: location, what is wrong, why it matters (the failure scenario), and a fix direction. Ranked: blocking → should-fix → nit. Nits that do not change meaning are omitted unless asked.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | A finding is a **pre-existing** defect the diff merely touches | (a) report as out-of-scope observation; (b) ask the author to fix | **MUST** report under Observations; **MUST NOT** block the review on it. |
| D2 | The diff **modifies or deletes a test** | — | **MUST** flag as blocking until justified. |
| D3 | The diff changes a **Swift-facing signature** | — | **MUST** flag; check for coordination with the iOS side. |
| D4 | A finding depends on a `[UNVERIFIED]` reference claim | — | **MUST** label it as such; do not block on it. |
| D5 | The review finds the **design** wrong, not just a line | (a) report as a design finding with options; (b) request a plan | **MUST** report at the design level; **MUST NOT** rewrite it in the review. |
| D6 | The user asks for **fixes to be applied** | — | **MUST** apply only the findings they select, one per change, each verified; then re-run the review. |
| D7 | The diff includes an **unapproved structural change** (new module, library, migration) | — | **MUST** flag as blocking per the standing contract. |

---

## 6. Implementation rules

**MUST:**

1. Read whole files, not hunks.
2. Verify every finding before reporting it.
3. Rank by severity; state the failure scenario for each.
4. Judge conventions against the codebase.
5. Flag test modification/deletion as blocking.
6. Separate pre-existing problems into Observations.

**MUST NOT:**

7. Change any file during the review.
8. Report reference-set defaults the codebase does not follow as findings.
9. Pad the report with praise, restatements, or formatting nits.
10. Rewrite the author's approach in the review; report the design concern instead.
11. Report an unverified suspicion as a defect.
12. Include exploit detail for a security finding.

---

## 7. Validation requirements

| # | Check | Required |
|---|---|---|
| V1 | Every changed file read in full | MUST |
| V2 | Every finding verified (code trace, test run, or reproduction) | MUST |
| V3 | Every finding has location, failure scenario, severity | MUST |
| V4 | Intent traced to code | MUST |
| V5 | Test changes checked (none modified/deleted, or flagged) | MUST |
| V6 | Build and tests run on the branch, result recorded | SHOULD; MUST if a finding depends on it |
| V7 | Zero files changed by the review | MUST (unless D6) |
| V8 | Conventions judged against named comparable code | MUST |

---

## 8. Failure handling

| Failure | Response |
|---|---|
| Diff too large to read fully | **MUST** say so; review the highest-risk files fully and list the rest as unreviewed. |
| Cannot determine intent | **MUST** ask before reviewing; a review against the wrong intent is noise. |
| Branch does not build | **MUST** report that first; it outranks every other finding. |
| A finding cannot be verified | **MUST** either verify by running code or report it as a question, not a defect. |
| Author disagrees with a finding | **MUST** re-verify; if still correct, present the evidence; if wrong, withdraw it plainly. |

---

## 9. Completion criteria

1. Every changed file read in full.
2. Intent verified.
3. Findings verified, ranked, with failure scenarios.
4. Tests checked.
5. Conventions judged against the codebase.
6. Zero files changed (unless fixes were requested and applied per D6).

---

## 10. Final report format

```markdown
## Review — <branch / PR / diff>

### Verdict
<APPROVE | APPROVE WITH CHANGES | REQUEST CHANGES | BLOCKED (does not build)>

### Intent check
<does the change do what it claims — one paragraph>

### Findings — blocking
| # | Location | Finding | Failure scenario | Fix direction |
|---|---|---|---|---|

### Findings — should fix
| # | Location | Finding | Failure scenario | Fix direction |
|---|---|---|---|---|

### Tests
- New behaviour covered: <yes/no — which>
- Existing tests modified/deleted: <none | flagged above>
- Test quality issues: <sleeps, mocks in commonTest, …>

### Conventions
| Deviation from codebase | Location | Comparable code |
|---|---|---|

### Build / test run
| Check | Result |
|---|---|

### Observations — pre-existing, out of scope
| Location | Issue |
|---|---|

### Decisions needed
```
