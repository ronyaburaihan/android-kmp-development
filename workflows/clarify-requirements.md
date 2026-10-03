# Workflow: Clarify Requirements

Standing contract: `README.md`. Prerequisite: none — this runs **before** `inspect-project.md` when the request is ambiguous, and is the first step of `plan-feature.md`.

---

## 1. Objective

Turn a request into a set of acceptance criteria precise enough that "done" is unambiguous — **before** any inspection effort is spent on the wrong problem and before any code is written.

The deliverable is a short written specification the user confirms. It is not design and not a plan.

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| The request, in the user's words | yes | — |
| Who the user of the feature is | yes | **MUST** ask |
| What problem it solves for them | yes | **MUST** ask; a feature with no problem has no acceptance criteria |
| Platforms in scope | no | default: all the project ships; **MUST** confirm if the request implies one |
| Constraints: deadline, compatibility, offline, accessibility, regulatory | no | **SHOULD** ask once |
| Out of scope | no | **SHOULD** ask; the absence of a boundary is how scope grows |

---

## 3. Initial project inspection

Minimal and read-only. Enough to ask informed questions, not enough to design.

```bash
# does something like this already exist?
grep -rli '<keyword from the request>' --include='*.kt' . | grep -v build/ | head
# what screens/features exist, to position the request
find . -path '*feature*' -type d -not -path '*/build/*' | head -20
```

**MUST** check whether the request describes something that already exists in part. "Add X" when X exists as Y is a different task (extend Y).

**MUST NOT** run the full `inspect-project.md` yet. Its depth depends on the answers this workflow produces.

---

## 4. Step-by-step procedure

### 4.1 Restate the request

Write one paragraph: who, does what, to achieve what, on which platforms. Present it. A wrong restatement caught here costs one message.

### 4.2 Identify the gaps — the question set

Ask **once**, as a single list, only the questions whose answers change the work. Typical ones:

| Area | Question |
|---|---|
| Behaviour | What happens on success? On failure? When offline? When empty? |
| Entry | How does the user reach it? Where does it go afterwards? |
| Data | Where does the data come from? Does it exist in the API / database today? |
| State | Must it survive process death? Rotation? App restart? |
| Platforms | Android only, or iOS too? Native SwiftUI or CMP on iOS? |
| Edge cases | Limits, concurrency, permissions needed, auth required? |
| Non-functional | Performance budget? Accessibility? Localisation? Analytics events? |
| Scope | What is explicitly **not** part of this? |

**MUST NOT** ask what the codebase can answer (which DI, which navigation). **MUST NOT** ask one question per message.

### 4.3 Resolve or default

For each gap, either the user answers, or you **state a default and label it an assumption**. Assumptions **MUST** appear in the specification verbatim so the user can object.

### 4.4 Write the acceptance criteria

Given/When/Then, or a checklist — match whatever the project uses in its issues. Each criterion **MUST** be observable: a tester can confirm it without reading code.

```
AC1  Given the user is signed in, when they open Profile, then their display name and
     avatar are shown within 1 s on a cached profile.
AC2  Given the device is offline, when they open Profile, then the cached profile is shown
     with an "offline" indicator and no error dialog.
AC3  Given the refresh fails with 401, then the user is signed out and sees the sign-in screen.
Out of scope: editing the profile; iOS native UI (CMP only this iteration).
```

### 4.5 Confirm and stop

Present the specification. **MUST** wait for confirmation before `plan-feature.md` or `inspect-project.md` proceeds.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | The request describes something that **already exists** | (a) extend the existing feature; (b) replace it; (c) the request is a misunderstanding | **MUST** ask. Present what exists with paths. |
| D2 | Two **conflicting** requirements | — | **MUST** surface the conflict; do not pick silently. |
| D3 | The request implies a **structural change** (new module, library, migration) | (a) in scope; (b) separate approved task | **MUST** flag now, before planning. |
| D4 | Success/failure/offline behaviour **unspecified** and the user does not answer | (a) default to loading/empty/error/content states + offline reads cached data | **MUST** state the default as an assumption in the spec. |
| D5 | The user wants to **skip clarification** ("just build it") | (a) proceed with assumptions listed; (b) refuse without them | **MUST** list the assumptions anyway, in one message, and proceed on them. |
| D6 | The request is **security-sensitive** (auth, payments, PII) | — | **MUST** add the relevant checks from `audit-security.md` as acceptance criteria. |
| D7 | Platform scope **unclear** in a KMP project | (a) shared logic + both UIs; (b) Android first | **MUST** ask. It changes the plan materially. |

---

## 6. Implementation rules

**MUST:**

1. Produce a written specification; **MUST NOT** proceed on a verbal understanding.
2. Make every criterion observable.
3. Record every assumption verbatim.
4. State out-of-scope explicitly.
5. Ask all questions in one batch.

**MUST NOT:**

6. Design the solution here. Architecture comes in `plan-feature.md`.
7. Create or modify any file in the project.
8. Invent a requirement the user did not state and did not accept as an assumption.
9. Ask questions the codebase answers.

---

## 7. Validation requirements

| # | Check | Required |
|---|---|---|
| V1 | Every acceptance criterion is observable without reading code | MUST |
| V2 | Success, failure, empty, and offline behaviour each covered or explicitly out of scope | MUST |
| V3 | Platforms in scope stated | MUST |
| V4 | Assumptions listed verbatim | MUST |
| V5 | Out-of-scope stated | MUST |
| V6 | D1 checked: no duplicate of an existing feature | MUST |
| V7 | Zero project files changed | MUST |

---

## 8. Failure handling

| Failure | Response |
|---|---|
| User cannot say what problem the feature solves | **MUST** report that the request is not yet specifiable; offer to draft two candidate problem statements. |
| Answers contradict each other | → D2. |
| User unavailable | **MUST** proceed on stated assumptions (D5) and mark the spec provisional. |
| Request is actually a bug report | **MUST** redirect to `diagnose-and-fix-bug.md`. |
| Request is actually a question | **MUST** redirect to `research-technical-question.md`. |

---

## 9. Completion criteria

1. Specification written, with observable acceptance criteria, assumptions, platforms, and out-of-scope.
2. V1–V7 pass.
3. User has confirmed, or the spec is marked provisional under D5.

---

## 10. Final report format

```markdown
## Requirements — <feature>

### Outcome
<CONFIRMED | PROVISIONAL (assumptions unconfirmed) | NEEDS DECISION>

### Restated request
<one paragraph: who, what, why, platforms>

### Acceptance criteria
AC1 …
AC2 …

### Assumptions (unconfirmed defaults)
- …

### Out of scope
- …

### Existing related code
| What | Where | Relationship |
|---|---|---|

### Decisions needed
<D1–D7 triggered>
```
