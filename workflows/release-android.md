# Workflow: Android Release

Standing contract: `README.md`. Prerequisite: `prepare-release.md` with a **GO** or **CONDITIONAL GO** verdict for Android.

---

## 1. Objective

Take a gated, verified Android App Bundle through Play's tracks to production safely: internal → closed → staged production, with halt criteria, monitoring, and a rollback path.

`prepare-release.md` decides *whether* the build may ship. This workflow is *how*. It still **does not perform** uploads or promotions — those are the user's actions in Play Console or via the publishing API. It prepares, instructs, verifies, and monitors.

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| `prepare-release.md` report with verdict and artefact path | yes | **MUST** stop; run the gate |
| Version / versionCode | yes | from the gate report |
| Release notes (user-facing) | yes | **MUST** ask; do not invent |
| Rollout plan: start %, ramp steps, halt criteria | no | default below; **MUST** confirm |
| Who can halt/roll back, and when they are available | yes | a rollout with nobody watching is not staged |
| Publishing mechanism: Play Console UI, Gradle Play Publisher, API | no | match what the project uses |

---

## 3. Initial project inspection

```bash
ls app/build/outputs/bundle/release/*.aab app/build/outputs/mapping/release/mapping.txt
grep -rn 'play\b\|gradle-play-publisher\|com.github.triplet' --include='*.gradle.kts' . | grep -v build/
cat .github/workflows/*release* 2>/dev/null | head -60
```

**MUST** confirm the artefact being released is the **same** one the gate verified (hash it). **MUST** confirm `mapping.txt` for that exact build is archived.

**MUST** read the previous release's rollout outcome (crash-free rate, halts) if available — it sets the baseline for halt criteria.

---

## 4. Step-by-step procedure

### 4.1 Pre-flight — MUST

| Check | Source |
|---|---|
| Gate verdict GO / CONDITIONAL GO, caveats acknowledged | `prepare-release.md` report |
| Artefact hash matches the gated build | `shasum -a 256 app-release.aab` |
| `mapping.txt` archived and uploaded to the crash reporter | gate report V7/V10 |
| `versionCode` strictly greater than the live release | Play Console |
| Release notes present for every supported locale the store listing has | user |
| Billing library ≥ floor, `targetSdk` ≥ floor | gate B1/B2 |
| Nobody will be unavailable during the ramp window | user |

### 4.2 Internal testing track

Upload to **internal testing** first (the user does this; the agent provides the exact artefact and notes).

**MUST** then: install from the Play Store on a physical device (not sideload — Play's split delivery is what is being tested), smoke-test the critical flows, and confirm the crash reporter receives a symbolicated event from this build.

**MUST** use the internal **track**, not internal app sharing, if Baseline Profiles are to be validated — app sharing does not install them.

### 4.3 Closed testing track

Promote to closed testing for the tester cohort. **SHOULD** soak for at least one full day of real usage before production, longer for a release containing a migration, a `targetSdk` bump, or a billing change.

Watch: crash-free users, ANR rate, the specific flows the release changed.

### 4.4 Production — staged rollout, MUST

Default plan unless the user overrides:

```
 1%  → 24 h  → 5% → 24 h → 20% → 24–48 h → 50% → 100%
```

**Halt criteria** — define numerically before starting; recommended defaults:

| Signal | Halt if |
|---|---|
| Crash-free users | below the previous release's rate minus 0.5 pp, or below 99.0% |
| ANR rate | above Play's bad-behaviour threshold, or 2× the previous release |
| A new crash signature in the top 5 | any, in shared Kotlin code or in the changed flows |
| Migration failures (if a schema change shipped) | any crash in the migration path |
| Billing errors (if monetised) | purchase/acknowledge failure rate up |

**Staged rollout applies to updates only** — a first production publish has no staging. [OFFICIAL]

### 4.5 Monitoring at each step

Before each ramp, **MUST** check: Play vitals (crash, ANR), the crash reporter filtered to this `versionCode`, store reviews mentioning the release, support tickets. Record the numbers in the report.

### 4.6 Halt and rollback — know it before you need it

Play supports **halt** (stop ramping; existing users keep the version) and **resume**. There is no true "roll back" — the fix is a **new versionCode** with the previous code (or a hotfix) rolled out at 100% to supersede.

**MUST** have the previous release's artefact and a hotfix branch ready before starting the ramp.

### 4.7 Completion

At 100% with criteria met for the final hold window, tag the release in git (the user's action), record the final metrics, and close.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | Gate verdict is **CONDITIONAL GO** | (a) proceed with the `NOT RUN` risks named in the plan and a slower ramp; (b) resolve them first | **MUST** ask; state each risk and what a slower ramp buys. |
| D2 | Halt criterion tripped | (a) halt and investigate; (b) halt and prepare a hotfix; (c) continue | **MUST** recommend (a); **MUST NOT** recommend (c) without a named reason. |
| D3 | Release contains a **schema migration** | — | **MUST** extend the 1% hold to 48 h and add migration-path crashes to halt criteria. |
| D4 | Release contains a **`targetSdk` bump** | — | **MUST** add the behaviour-change flows to the per-step check list. |
| D5 | **First production publish** (no staging available) | — | **MUST** state that there is no partial rollout; recommend a longer closed-testing soak. |
| D6 | **iOS release** is coupled to this one | (a) feature-flag and ship independently; (b) hold Android | **MUST** present (a); App Review timing is not predictable. |
| D7 | Rollout must go **faster** than the default | — | **MUST** state what each skipped hold window gives up; proceed only on the user's call. |
| D8 | The crash reporter shows **unsymbolicated** frames from this build | — | **MUST** halt; the mapping upload failed. |

---

## 6. Implementation rules

**MUST:**

1. Release exactly the artefact the gate verified; hash it.
2. Go through internal → closed → staged production.
3. Define numeric halt criteria before the first ramp.
4. Check vitals and the crash reporter before every ramp; record numbers.
5. Have the previous artefact and a hotfix branch ready.
6. Install from Play (not sideload) for the internal-track smoke test.

**MUST NOT:**

7. Upload, promote, halt, or resume on the user's behalf — provide the exact steps.
8. Rebuild between the gate and the release.
9. Ramp past a tripped halt criterion.
10. Skip the internal track.
11. Edit release notes into claims the build does not deliver.
12. Describe a halt as a rollback — it is not.

---

## 7. Validation requirements

| # | Check | Required |
|---|---|---|
| V1 | Artefact hash = gated artefact | MUST |
| V2 | `mapping.txt` uploaded; a symbolicated event seen from this build | MUST |
| V3 | Internal-track install from Play on a physical device; smoke test passed | MUST |
| V4 | Closed soak ≥ 24 h (longer per D3/D4) with no criterion tripped | SHOULD |
| V5 | Halt criteria written numerically before the first ramp | MUST |
| V6 | Vitals + crash reporter checked and recorded before each ramp | MUST |
| V7 | Previous artefact + hotfix branch ready | MUST |
| V8 | Final 100% hold window clean | MUST |

---

## 8. Failure handling

| Failure | Response |
|---|---|
| Play rejects the upload (policy, `targetSdk`, billing, signing) | **MUST** capture the reason verbatim; it is a gate miss — add the check to `prepare-release.md`. |
| Internal-track install fails or crashes at launch | **MUST** stop; likely a split-APK or Baseline Profile issue not seen in sideload. |
| Crash spike at 1% | → D2 halt. **MUST** symbolicate and localise before any decision. |
| Unsymbolicated crashes | → D8. |
| Migration crashes | halt; → `add-persistence.md`; a hotfix that re-runs a failed migration safely is required. |
| ANR regression | halt; main-thread work — `../references/quality/performance.md`. |
| Need to "roll back" | ship the previous code as a new, higher `versionCode` at 100%. |

---

## 9. Completion criteria

1. V1–V3, V5–V8 pass.
2. Rollout reached 100% with no unresolved halt, or halted with a documented decision and next step.
3. Metrics recorded at every ramp step.
4. Release tagged (by the user).
5. Any gate miss fed back into `prepare-release.md`.

---

## 10. Final report format

```markdown
## Android Release — <version> (<versionCode>)

### Outcome
<RELEASED 100% | HALTED at <n>% | SUPERSEDED by <versionCode> | NOT STARTED>

### Pre-flight
| Check | Result |
|---|---|

### Track progression
| Step | Date/time | Crash-free | ANR | New signatures | Decision |
|---|---|---|---|---|---|
| internal | | | | | |
| closed | | | | | |
| prod 1% | | | | | |
| … | | | | | |

### Halt criteria (set before ramp)
| Signal | Threshold |
|---|---|

### Rollback readiness
- Previous artefact: <path/hash>
- Hotfix branch: <name>

### Incidents
### Gate misses to feed back
### Decisions needed
```
