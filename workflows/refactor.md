# Workflow: Refactor

Standing contract: `README.md`. Prerequisite: `inspect-project.md`.

---

## 1. Objective

Change the structure of existing code **without changing its behaviour**, in a sequence of small, independently verifiable steps, each one leaving the build green and the tests passing.

A refactor with a behaviour change is two tasks. A refactor without a test net is a rewrite.

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| What is being refactored and **why** | yes | **MUST** ask. "Clean up" is not a why. Acceptable: enable a feature, remove a deprecation, fix a measured problem, reduce a known duplication. |
| The target shape | yes | **MUST** ask or propose; approve before starting |
| Scope boundary — which files may change | yes | **MUST** agree; refactors spread |
| Is behaviour allowed to change at all? | yes | default **no** |
| Is this a migration (library, architecture, module graph)? | yes | if yes → **MUST** have approval per the standing contract; large migrations go through `assess-kmp-adoption.md`-style assessment |

---

## 3. Initial project inspection

Run `inspect-project.md`, then establish the **safety net**:

```bash
# tests covering the refactor area
grep -rln '<TypeBeingRefactored>' --include='*Test.kt' . | grep -v build/
# every consumer of what will change
grep -rn '<TypeBeingRefactored>' --include='*.kt' . | grep -v build/ | grep -v Test
# exported to Swift?
grep -rn '<TypeBeingRefactored>' --include='*.kt' */src/iosMain 2>/dev/null
```

**MUST** record:

- which behaviours of the refactor area are pinned by tests, and which are not
- every consumer (the blast radius)
- whether any touched type reaches the Swift boundary (→ D4)
- a green baseline: full build and tests pass before the first change

**MUST NOT** begin on a red baseline or on an uncovered area without D1.

---

## 4. Step-by-step procedure

### 4.1 Pin behaviour first — MUST if coverage is thin

Run `backfill-tests.md` on the area. Characterisation tests written **before** the refactor are the only evidence that behaviour is unchanged after it. **MUST NOT** modify them during the refactor.

### 4.2 Plan the steps

Break the refactor into steps where each one:

- compiles
- passes the full suite
- is small enough to review in minutes
- could be committed on its own

Typical safe sequences: extract interface → add new implementation alongside → switch consumers one at a time → delete old. Rename → move → split.

**MUST** present the step list and the target shape before starting.

### 4.3 Execute one step at a time

After **every** step:

```bash
./gradlew assembleDebug <module>:allTests
```

**MUST** stop at the first red step. **MUST NOT** proceed with a failing step "to fix later".

### 4.4 Keep the diff a refactor

**MUST NOT**, within the refactor:

- fix a bug noticed in passing → **Observations**
- change a public API's behaviour
- add a feature
- reformat untouched regions
- upgrade a dependency
- "improve" a test's assertions

A pure move/rename diff should read as a move/rename. Mixed diffs are unreviewable.

### 4.5 Delete the old path

A refactor that leaves both the old and the new path alive is half-done and worse than either. **MUST** remove the superseded code in the final step, after every consumer has moved.

### 4.6 Validate

Run section 7.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | Area has **no tests** | (a) characterisation tests first; (b) proceed as a rewrite with that label | **MUST** ask; recommend (a). |
| D2 | A step reveals a **bug** in current behaviour | (a) pin it, report, continue; (b) stop and fix first | **MUST** default to (a). Fixing changes behaviour. |
| D3 | The refactor is really a **migration** (DI container, navigation, module graph, Nav 2→3, XML→Compose) | — | **MUST** stop; requires explicit approval and usually its own assessment. |
| D4 | A refactored type is **exported to Swift** | (a) keep the exported signature, refactor behind it; (b) coordinate a breaking change | **MUST** ask; recommend (a). → `review-ios-interop.md`. |
| D5 | A step cannot be made green without changing behaviour | — | **MUST** stop. The step is wrong, or it is not a refactor. |
| D6 | Scope wants to grow ("while we're here…") | — | **MUST** decline within this task; record in Observations. |
| D7 | A **test assertion** looks wrong | — | **MUST NOT** change it during the refactor; report it. |
| D8 | Target shape conflicts with the codebase's dominant pattern | (a) match the codebase; (b) establish a new pattern (approval) | **MUST** ask. |

---

## 6. Implementation rules

**MUST:**

1. Have a green baseline and a test net before the first change.
2. Work in independently green steps.
3. Keep behaviour identical; characterisation tests unmodified.
4. Delete the old path at the end.
5. Preserve public signatures reaching other modules or Swift unless D4 approves otherwise.
6. Keep package names where possible — a package change alters generated Objective-C names.

**MUST NOT:**

7. Mix a behaviour change, bug fix, feature, or dependency change into the refactor.
8. Modify or delete an existing test.
9. Leave both old and new paths alive.
10. Reformat untouched code.
11. Proceed past a red step.
12. Perform a migration under a refactor label.

---

## 7. Validation requirements

| # | Check | Required |
|---|---|---|
| V1 | Green baseline recorded before the first change | MUST |
| V2 | Every step: build + full tests green | MUST |
| V3 | Characterisation/existing tests byte-identical (`git diff` on test files) | MUST |
| V4 | No public behaviour change: consumers' tests pass unmodified | MUST |
| V5 | Old path deleted; no dead code left | MUST |
| V6 | Release build with R8 (keep rules may reference moved classes) | MUST |
| V7 | KMP: all targets compile; iOS framework links; exported header unchanged (or D4 approved) | MUST if KMP |
| V8 | Diff reads as a refactor: no feature/fix/format noise | MUST |
| V9 | Lint / detekt / ktlint | MUST if configured |

---

## 8. Failure handling

| Failure | Response |
|---|---|
| A step goes red | **MUST** revert that step and re-plan it smaller. |
| A test fails and the code looks right | The test pinned behaviour the refactor changed → D5. Behaviour changed. |
| Release build fails after a move | R8 keep rule references the old location. **MUST** update the rule. |
| iOS header changed unexpectedly | A package or name change. **MUST** revert to preserve the export, or go through D4. |
| Refactor stalls half-way | **MUST** revert to the last green step rather than leave a half-refactored state. |
| A consumer in another module breaks | Blast radius was under-counted. **MUST** add it to the plan and fix the consumer, or revert. |

---

## 9. Completion criteria

1. Target shape reached; old path removed.
2. Every step was green; final V1–V9 pass.
3. No test modified; behaviour unchanged by evidence.
4. No feature, fix, or dependency change mixed in.
5. Exported Swift surface unchanged or coordinated.
6. Bugs and scope ideas found are in Observations, untouched.

---

## 10. Final report format

```markdown
## Refactor — <what>

### Outcome
<DONE | DONE WITH CAVEATS | REVERTED | BLOCKED | NEEDS DECISION>

### Why
<the reason this refactor exists>

### Safety net
- Tests covering the area before: <n> (characterisation added: <n>)
- Baseline: build PASS, tests PASS

### Steps
| # | Step | Build | Tests |
|---|---|---|---|

### Target shape
<before → after, one paragraph or a tiny diagram>

### Blast radius
| Consumer | Module | Updated |
|---|---|---|

### Changes made
| File | Change |
|---|---|

### Validation performed
| # | Check | Result |
|---|---|---|

### Behaviour preserved — evidence
<existing tests unmodified and green; consumer tests green>

### Not done
### Observations
<bugs noticed, scope ideas declined>
### Decisions needed
```
