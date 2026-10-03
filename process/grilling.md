# Primitive: Grilling

Adapted from Matt Pocock's `grilling` skill — see `README.md` § Attribution.

## Purpose

Resolve every branch of a design before building, by asking only the questions that can be answered
**now**, and by going to find facts instead of asking for them.

The failure this prevents: you believe you understood the request, you build, and the result reveals
you did not. No amount of correct Kotlin recovers that.

---

## When to invoke

- A request is ambiguous → `../workflows/clarify-requirements.md` runs this as its question step.
- A plan has an unresolved fork → `../workflows/plan-feature.md`.
- "Should we use KMP?" → `../workflows/assess-kmp-adoption.md`, where almost every question is a decision, not a fact.

**MUST NOT** invoke for a request that is already unambiguous. An interview over a one-line bug fix
is a no-op that costs the user a round trip.

---

## The mechanic

### 1. Build the frontier

The **frontier** is the set of questions you can ask now *without guessing at answers you have not
heard yet*. A question whose phrasing depends on an unsettled decision is **not** on the frontier —
it waits.

```
"Should the profile cache survive process death?"        → frontier (independent)
"Which Room migration strategy for the cache table?"     → NOT frontier
                                                           (presumes caching, presumes Room)
```

### 2. Split facts from decisions — MUST

| Kind | Who answers | Action |
|---|---|---|
| **Fact** — true of the codebase or the platform right now | the repository, the library, the official docs | **MUST** go find it. Dispatch a subagent, or read the file. **MUST NOT** ask the user. |
| **Decision** — a choice only the user can make | the user | Ask. |

This is the single rule that makes the interview short. Asking a user which DI container their own
project uses spends their patience on something `grep -rl "startKoin"` answers in one call.

Facts, in an Android/KMP repository, almost always include:

| Question | Where the answer is |
|---|---|
| Which DI container? | `grep -rl 'startKoin\|@HiltAndroidApp\|dagger'` |
| Which navigation library and version? | `gradle/libs.versions.toml`, `references/android/navigation.md` |
| Does this endpoint already exist? | the Ktor/Retrofit service interfaces |
| Is there a Room database? At what version? | `@Database(version = …)` |
| Which KMP targets are configured? | the `kotlin { }` block |
| Is there an iOS consumer of this API? | `iosMain`, the exported framework, the Xcode project |
| Does an equivalent screen already exist? | `../workflows/inspect-project.md` |
| Does the platform support X on minSdk? | `references/android/platform-requirements.md` |

Decisions are almost always: platform scope, offline behaviour, what happens on failure, whether
state survives process death, native iOS UI vs Compose Multiplatform, what is explicitly out of
scope, and any trade-off with a cost the user bears.

### 3. Ask the whole frontier in one round — MUST

One message. Number each question. **Give your recommended answer to each**, so the user can reply
"1, 3 yes; 2 no" instead of composing prose.

```markdown
**Q1 — Offline behaviour.** When the profile fetch fails and a cached copy exists, show the cache
with an offline indicator, or show an error?
*Recommendation:* show the cache with an indicator. Matches `FeedViewModel.kt:64`.

**Q2 — Platform scope.** Android only this iteration, or iOS too?
*Recommendation:* shared logic in `commonMain`, Android UI only. The iOS app has no Profile entry point yet.

**Q3 — Persistence.** Must the cache survive process death?
*Recommendation:* yes, Room — the project already caches `Feed` this way (`FeedDao.kt`).
```

**MUST NOT** ask one question per message. **MUST NOT** ask a question whose answer you could have
looked up while waiting.

### 4. Do not block on lookups

Dispatch fact-finding and keep asking settleable decisions in the same round. Only questions
*downstream* of a pending fact wait for it.

### 5. Stop when the frontier is empty

Empty frontier = every branch visited, nothing silently assumed. Then — and only then — state the
shared understanding and **wait for the user to confirm it** before any file is written.

If the user declines to answer (or is unavailable), **MUST** list each unanswered question as a
labelled assumption and proceed on the recommendation. An unconfirmed assumption written down is
recoverable; an unconfirmed assumption in your head is not.

---

## Android/KMP specifics

Three decisions reshape the entire plan and **MUST** be on the first frontier if they are unsettled:

1. **Platform scope.** Android-only, shared-logic-plus-two-UIs, or Compose Multiplatform on both.
   This decides which source set every new file lands in, and it is expensive to reverse.
2. **Where the state boundary sits.** A shared ViewModel in `commonMain` (lifecycle-viewmodel KMP)
   versus an Android `ViewModel` plus a separate iOS presenter. See `references/architecture/presenter.md`.
3. **Whether the Swift-facing API changes.** Any new type crossing the Objective-C boundary brings
   constraints — no `Flow`, no generics on interfaces, no default arguments, `@Throws` required.
   `references/kmp/ios-interop.md`. This is a decision *and* an approval gate under the standing
   contract.

---

## Anti-patterns

| Anti-pattern | Why it fails |
|---|---|
| Asking the user a fact | Spends the user's attention on a `grep`. Erodes trust in the questions that matter. |
| Drip-feeding one question per message | Turns a five-minute alignment into twenty minutes of latency. |
| Asking a non-frontier question | Forces the user to answer hypothetically, then re-answer when the premise changes. |
| Omitting your recommendation | Makes the user do the design work the interview was supposed to de-risk. |
| Interviewing, then building something the answers did not describe | The interview becomes theatre. Restate before building. |
| Interviewing a trivial request | A no-op that reads as process for its own sake. |

---

## It's working if

- Every question asked was a decision, not a fact — and you can say where each fact came from.
- The whole frontier arrived in one numbered message, each item with a recommendation.
- No question presumed an answer the user had not given.
- The round ended with a restatement the user explicitly confirmed, or a written assumption list.
- Zero project files changed. Grilling writes nothing but the specification.

---

## Where it fits

```
grilling → clarify-requirements → inspect-project → plan-feature → vertical-slice
                                                                  → add-feature (tdd) → two-axis-review
```

Output feeds `../templates/feature-spec.md`. Decisions with lasting architectural weight go to
`../templates/adr.md`.
