# Workflow: Assess KMP Adoption

Standing contract: `README.md`. Prerequisite: `inspect-project.md`.

---

## 1. Objective

Evaluate whether and how an existing application should adopt Kotlin Multiplatform, and produce a staged, costed migration plan.

**This workflow produces no code.** Its deliverable is an assessment and a plan. Implementation happens later, per approved stage, via `migrate-module-to-kmp.md`.

**MUST NOT** create a `commonMain` source set, add a KMP target, move a file, or add a dependency during this workflow.

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| The goal — what the business wants from KMP | yes | **MUST** ask. "Share code with iOS" is not a goal; "stop reimplementing pricing rules twice" is. |
| Does an iOS app already exist? | yes | **MUST** ask. Greenfield iOS and an established Swift codebase lead to different plans. |
| Who maintains the iOS app, and have they agreed? | yes | **MUST** ask. KMP changes the iOS team's build and debugging workflow. An assessment that ignores this is incomplete. |
| Share logic only, or UI too? | no | Default: assess both, recommend logic-first. |
| Candidate code to share | no | Default: derive candidates from inspection. |
| Constraints — release cadence, team size, deadline | no | **SHOULD** ask; they dominate the staging. |

---

## 3. Initial project inspection

Run `inspect-project.md`, then assess the Android codebase's **portability**, which is what determines cost.

```bash
# Android-framework coupling in business logic — the main cost driver
grep -rln 'android\.\|androidx\.' --include='*.kt' . | grep -v build/ | grep -iE 'repository|usecase|domain|interactor|model|data' | head -40
grep -rn 'Context\|Application\|Resources\|SharedPreferences\|Parcelable\|@Parcelize' --include='*.kt' . | grep -v build/ | grep -icE 'repository|usecase|domain' 

# JVM-only APIs that will not compile for native
grep -rn 'java\.io\.\|java\.time\.\|java\.util\.concurrent\|javax\.\|java\.net\.\|Thread(' --include='*.kt' . | grep -v build/

# libraries that will not cross
grep -rn 'retrofit2\|com.squareup.moshi\|com.google.gson\|org.greenrobot\|dagger\|hilt' --include='*.kt' --include='*.gradle.kts' . | grep -v build/

# reflection and serialization mechanics that differ on native
grep -rn '::class.java\|Class.forName\|\.javaClass\|KClass' --include='*.kt' . | grep -v build/

# existing test coverage on the candidate code — the safety net for any move
find . -name '*Test.kt' -not -path '*/build/*' | wc -l
```

**MUST** score each candidate area on this portability rubric:

| Dimension | Green | Amber | Red |
|---|---|---|---|
| Android-framework imports | none | `Context` only at the edges | `Context`/`Resources` threaded throughout |
| JVM-only API use | none | a few, with KMP equivalents (`java.time` → `kotlin.time`) | pervasive (`java.io`, `javax.*`, reflection) |
| Libraries | already KMP (Ktor, kotlinx, Koin, Room KMP) | one JVM-only library with a KMP alternative | Retrofit/Moshi/Gson/Hilt throughout |
| Test coverage | good; fakes-based | partial | none |
| Interface boundaries | repository interfaces, DI in place | partial | concrete dependencies everywhere |
| Change rate | stable | moderate | actively churning |

**MUST** record the AndroidX KMP support status for every library the candidate code uses, from <https://developer.android.com/kotlin/multiplatform>. **MUST NOT** assume a library is KMP-ready.

---

## 4. Step-by-step procedure

### 4.1 Establish the actual goal and the alternative

**MUST** state, explicitly, what problem KMP solves here and what the non-KMP alternative is. An assessment that cannot name the alternative is advocacy, not assessment.

| Goal | KMP is a good fit | A cheaper alternative exists |
|---|---|---|
| Stop duplicating complex business rules | yes — this is the core case | — |
| One team instead of two | partly — UI still differs; iOS expertise still needed | — |
| Faster feature delivery | only after the shared layer exists; slower during migration | — |
| Reduce bugs from divergent implementations | yes | a shared spec plus contract tests |
| Share UI | highest cost, highest risk; CMP iOS is Stable, web is Beta | — |
| "It is modern" | **no** | — |

**MUST** report honestly if the goal does not justify the cost. A recommendation not to adopt KMP is a valid outcome of this workflow.

### 4.2 Identify and rank candidates

Rank by `value ÷ cost`, where value is duplication avoided and cost is the portability score.

Typical ranking, best first:

1. **Pure domain logic** — validation, pricing, business rules, state machines. No platform surface, high duplication cost.
2. **Models and DTOs** — cheap, and a prerequisite for everything else.
3. **Networking** — high value if the iOS side can adopt Ktor; the DTO layer is already being duplicated.
4. **Persistence** — Room KMP and DataStore Preferences both support Android/iOS; moderate cost, and touches user data.
5. **ViewModels / presentation** — real value, but needs an iOS observability bridge (third-party) if the UI stays native SwiftUI.
6. **UI (CMP)** — largest change to the iOS team's workflow. Assess separately.

**MUST** recommend the smallest first stage that produces a shippable result. A first stage that cannot ship is a research project, not a migration.

### 4.3 Cost the obstacles concretely

For each candidate, **MUST** enumerate the actual blockers with file counts, not adjectives:

| Obstacle | What it costs |
|---|---|
| `Context` in business logic | one interface + two implementations per capability |
| Retrofit → Ktor | rewrite the data sources; the DTOs mostly survive |
| Moshi/Gson → kotlinx.serialization | annotation changes plus a naming-strategy audit |
| Hilt → Koin/Metro/kotlin-inject | DI container replacement — structural, affects every module |
| `java.time` → `kotlin.time` / `kotlinx-datetime` | mechanical but wide |
| `@Parcelize` | does not cross; needs a different mechanism |
| Reflection | often no native equivalent; may need redesign |
| No tests on the candidate code | a characterisation-test stage **before** any move |

**MUST** flag a DI container replacement as structural. It is usually the single largest line item and is frequently underestimated.

### 4.4 Assess the iOS side

**MUST** cover, because this is where adoption usually fails:

| Question | Why it matters |
|---|---|
| Framework integration option? | Direct integration is the default and the only one Swift export supports. See `../references/kmp/ios-interop.md`. |
| Does iOS CI have a JDK, Gradle cache, and konan cache? | A cold cache adds minutes to every archive. |
| Who debugs a crash in shared Kotlin? | Needs dSYM upload; the procedure is `[UNVERIFIED]` in the reference set and **MUST** be verified during a spike. |
| Objective-C export constraints understood? | No `Flow`, no generics on interfaces, no default arguments, `@Throws` mandatory, cross-package name collisions. |
| Will the shared surface be a facade or the whole domain? | A facade is strongly preferable. |
| Swift observability bridge needed? | Only if the UI stays SwiftUI over shared ViewModels — a third-party dependency. |
| iOS team's appetite | A team that did not agree will route around the shared module. |

**MUST NOT** base a plan on Swift export. It is Alpha and not production-ready.

### 4.5 Produce the staged plan

Each stage **MUST** have: scope, prerequisites, exit criteria, a rollback story, and a shippable outcome.

```
Stage 0  Spike — not shipped
         Move ONE small pure-logic file to commonMain. Build the framework.
         Call it from Swift. Crash it and symbolicate.
         Exit: the toolchain works end to end, and the iOS CI cost is measured.

Stage 1  Characterisation tests on the candidate code (if coverage is thin)
         Exit: behaviour is pinned before anything moves.

Stage 2  Models + pure domain logic → commonMain
         Exit: Android ships unchanged; iOS consumes one facade function.

Stage 3  Networking (Retrofit → Ktor) behind existing repository interfaces
         Exit: Android ships unchanged; iOS uses the shared data source.

Stage 4  Persistence (if justified)        ← touches user data; own risk review
Stage 5  Presentation / ViewModels          ← needs the Swift bridge decision
Stage 6  UI (CMP)                           ← separate assessment
```

**MUST** make Stage 0 non-negotiable. The toolchain and symbolication questions are cheap to answer and expensive to discover late.

**MUST** state, for each stage, that Android ships unchanged — or, if it does not, what the Android-side risk is.

### 4.6 State the ongoing costs

**MUST** include the costs that persist after migration, since these are what teams omit:

- Kotlin ↔ Compose-compiler ↔ KSP ↔ CMP lockstep on every Kotlin upgrade.
- A macOS CI runner for iOS target tests and the framework build.
- Two symbolication pipelines (`mapping.txt` and dSYM).
- The exported API surface becomes a contract with its own review burden.
- R8 does not apply to the iOS framework: shared code ships unobfuscated, so no secrets in `commonMain`.
- Deprecated/Tier-3 target churn (`macosX64`, `iosX64`).

---

## 5. Decision points

All decision points in this workflow produce recommendations for the user. **MUST NOT** resolve any of them by writing code.

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | The stated goal does **not** justify the cost | (a) do not adopt; (b) adopt a narrow slice only; (c) use a shared spec + contract tests instead | **MUST** say so plainly. A "do not adopt" recommendation is a success. |
| D2 | No iOS app exists yet | (a) KMP from the iOS app's start — lowest cost; (b) build iOS native first | **SHOULD** recommend (a). The cheapest time to adopt is before an iOS codebase exists. |
| D3 | iOS team has **not agreed** | — | **MUST** stop short of a plan and report this as the first blocker. |
| D4 | Candidate code is **Red** on portability | (a) refactor for portability first, on Android only, shipping continuously; (b) pick a different candidate; (c) do not adopt | **SHOULD** recommend (a) or (b). An Android-only portability refactor is independently valuable and low-risk. |
| D5 | Project uses **Hilt** | (a) replace the DI container — structural; (b) keep Hilt on Android and use constructor injection in shared code with manual wiring at the Android edge | **MUST** present both with costs. (b) is often viable and much cheaper. |
| D6 | UI sharing requested | (a) logic-first, reassess UI later; (b) CMP now | **SHOULD** recommend (a). **MUST** state CMP status per target: iOS/desktop/Android Stable, **web Beta**. |
| D7 | Candidate code has **no tests** | (a) characterisation tests first; (b) move without a net | **MUST** recommend (a). Moving untested code across platforms has no verification. |
| D8 | A required library has **no KMP support** | (a) replace it; (b) keep it behind an interface, Android-only; (c) exclude the candidate | **MUST** list each such library with its status. Recommend (b) where the capability is platform-specific anyway. |
| D9 | The team wants **Swift export** | — | **MUST** state it is Alpha and not production-ready. Plan on Objective-C export. |
| D10 | Deadline pressure to adopt quickly | (a) Stage 0 + Stage 2 only; (b) defer | **MUST NOT** recommend compressing Stage 0 away. |

---

## 6. Implementation rules

This workflow implements nothing.

**MUST:**

1. Produce no code, no build-file change, no new file in the project. The deliverable is a document.
2. Score every candidate against the rubric in section 3 with real file counts.
3. Verify every library's KMP status from its canonical source. **MUST NOT** assume.
4. Name the non-KMP alternative for the stated goal.
5. Give each stage a shippable outcome and a rollback story.
6. Include Stage 0 (spike) in every plan.
7. State the ongoing costs from 4.6.
8. Distinguish `[OFFICIAL]` facts from `[UNVERIFIED]` ones — notably Crashlytics symbolication of Kotlin/Native frames, Room KMP migration testing, and `SavedStateHandle` in common code.

**MUST NOT:**

9. Create or modify a source set, target, module, or dependency.
10. Recommend adoption because KMP is modern, or because Google supports it.
11. Present Swift export as the integration plan.
12. Describe CMP web as production-ready.
13. Estimate effort without naming the file counts and the obstacles behind the estimate.
14. Omit the iOS team's workflow impact.

---

## 7. Validation requirements

The deliverable is an assessment; validation is that the assessment is sound and the project is untouched.

| # | Check | How | Required |
|---|---|---|---|
| V1 | **Zero files changed** | `git status --porcelain` identical to the start | MUST |
| V2 | Every candidate scored on all six rubric dimensions | review | MUST |
| V3 | Every library's KMP status verified against a canonical source, with URLs | review | MUST |
| V4 | Each stage has scope, prerequisites, exit criteria, rollback, shippable outcome | review | MUST |
| V5 | Stage 0 spike present and scoped | review | MUST |
| V6 | Ongoing costs stated | review | MUST |
| V7 | Non-KMP alternative stated | review | MUST |
| V8 | iOS team impact and agreement status stated | review | MUST |
| V9 | `[UNVERIFIED]` items marked as such, with what a spike must confirm | review | MUST |
| V10 | Effort figures traceable to file counts | review | MUST |
| V11 | CMP per-target status stated correctly if UI sharing is in scope | review | MUST if D6 |

---

## 8. Failure handling

| Failure | Response |
|---|---|
| The goal cannot be articulated | **MUST** stop at 4.1 and report. Do not assess against an unstated goal. |
| iOS team unreachable or has not agreed | → D3. **MUST** report as the primary blocker, not a footnote. |
| Codebase too large to assess fully | **MUST** narrow to the named candidates and state the narrowing. **MUST NOT** extrapolate a whole-codebase estimate from one module. |
| Candidate code's portability cannot be determined (generated, obfuscated, no source) | Record as `unknown` and exclude from stage 1–2 recommendations. |
| Library KMP status cannot be confirmed | **MUST** mark it `unknown` and treat it as a Stage 0 spike question. **MUST NOT** assume either way. |
| A dependency is KMP but only on Tier 2/3 targets | **MUST** record which targets, and whether they are the ones the project needs. |
| User asks for an implementation during the assessment | **MUST** decline within this workflow and point to `migrate-module-to-kmp.md`, which requires an approved plan. |
| Assessment concludes "do not adopt" | **MUST** report it with the reasoning and the alternative. **MUST NOT** soften it into a weak yes. |
| Prior partial KMP adoption already exists | **MUST** assess the existing shared module's health first — half-migrated code is a liability and may be the real finding. |

---

## 9. Completion criteria

**MUST** all hold:

1. The business goal and the non-KMP alternative are both stated.
2. Candidates identified and scored on the full rubric, with file counts.
3. Every library's KMP status recorded with its source.
4. Obstacles enumerated concretely per candidate.
5. iOS-side assessment complete, including integration option, CI cost, symbolication, export constraints, and team agreement.
6. Staged plan with Stage 0 spike; every stage shippable with a rollback story.
7. Ongoing costs stated.
8. A clear recommendation — adopt a named slice, refactor for portability first, or do not adopt.
9. Every decision point triggered is presented with options and a recommendation.
10. V1 holds: zero files changed.
11. `[UNVERIFIED]` items flagged with what the spike must confirm.

---

## 10. Final report format

Base skeleton from `README.md`, with these additions.

```markdown
## Assess KMP Adoption — <project>

### Outcome
<RECOMMEND ADOPT (staged) | RECOMMEND PORTABILITY REFACTOR FIRST | RECOMMEND DO NOT ADOPT | BLOCKED> — one sentence.

### The goal
- Stated business goal:
- What KMP would solve:
- Non-KMP alternative, and why it is or is not sufficient:

### Inspection findings
- Current type: <Android-only | partial KMP>
- Modules: <n>
- Test count on candidate code: ~<n>
- DI: <container> | HTTP: <lib> | Serialization: <lib> | Persistence: <lib>
- iOS app: <exists, Swift, team of n | none yet>
- iOS team agreement: <agreed | not consulted (D3) | declined>

### Candidate portability scores
| Candidate | Android imports | JVM-only APIs | Libraries | Tests | Boundaries | Churn | Score |
|---|---|---|---|---|---|---|---|
| Domain logic (`path`, <n> files) | green | green | green | amber | green | green | **Green** |
| Networking (`path`, <n> files) | green | amber | **red** (Retrofit) | green | green | green | **Amber** |

### Library KMP status
| Library | Used by | KMP? | Targets | Source | Action |
|---|---|---|---|---|---|
| | | yes/no/unknown | | <URL> | keep / replace / wrap |

### Obstacles, costed
| Obstacle | Candidate | Files affected | Work required |
|---|---|---|---|

**Structural items** (each needs its own approval):
| Item | Why structural |
|---|---|
| e.g. Hilt → Koin | affects every module's DI (D5) |

### iOS-side assessment
| Question | Finding |
|---|---|
| Integration option | <direct / CocoaPods / SPM> and why |
| iOS CI readiness | <JDK, Gradle cache, konan cache> |
| Shared-code crash symbolication | **[UNVERIFIED]** — Stage 0 must confirm |
| Export constraints affecting the plan | <no Flow, no generics on interfaces, @Throws, name collisions> |
| Shared surface shape | facade recommended |
| Swift observability bridge needed | <yes — third-party | no> |

### Staged plan
**Stage 0 — Spike (not shipped)**
- Scope:
- Prerequisites:
- Exit criteria:
- Rollback: delete the branch
- Answers: toolchain works; iOS CI cost measured; symbolication confirmed

**Stage <n> — <name>**
- Scope: <files>
- Prerequisites:
- Exit criteria:
- Rollback:
- Shippable outcome: <and whether Android ships unchanged>

### Ongoing costs after adoption
<Lockstep, macOS runner, dual symbolication, exported-API review burden,
no R8 on the framework, target-tier churn.>

### Recommendation
<Explicit. Name the first stage to approve, or state that adoption is not recommended
and why.>

### Changes made
None. <Confirm `git status --porcelain` unchanged.>

### Validation performed
| # | Check | How | Result |
|---|---|---|---|
| V1 | Zero files changed | | |
| V2 | Candidates fully scored | | |
| V3 | Library statuses verified | | |
| V4 | Stages complete | | |
| V5 | Stage 0 present | | |
| V6 | Ongoing costs stated | | |
| V7 | Alternative stated | | |
| V8 | iOS impact stated | | |
| V9 | Unverified items flagged | | |
| V10 | Effort traceable | | |
| V11 | CMP status correct | | |

### Not done
### Observations
### Decisions needed
<D1–D10 triggered, each with options and a recommendation.>
```
