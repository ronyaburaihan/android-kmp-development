# Workflow: Audit Compose Performance

Standing contract: `README.md`. Prerequisite: `inspect-project.md`.

**Primitives:** `../process/diagnostic-loop.md` — the loop is a Macrobenchmark run; never diagnose jank by eye.

---

## 1. Objective

Diagnose a reported performance problem — jank, slow startup, excessive recomposition — with **measurement**, then fix only what measurement implicates.

**MUST measure before changing anything.** Compose performance work done from intuition typically makes code worse and slower.

Two modes:

| Mode | Output |
|---|---|
| **Audit** (default) | Measurements, diagnosis, prioritised fix plan. No code changes. |
| **Fix** | Minimal changes to measurement-implicated code, re-measured. |

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| The symptom — what is slow, where | yes | **MUST** ask. "The app is slow" is not actionable. |
| Reproduction: screen, interaction, data volume | yes | **MUST** ask. Jank often appears only at a certain list size. |
| Device and OS where it reproduces | yes | **MUST** ask. A low-end device is where this matters. |
| Debug or release build | yes | **MUST** ask. **A debug build is not a measurement** — Compose is slower and R8 is off. |
| Target: a number, or "better" | no | Default: better than baseline, quantified. |

**MUST** establish whether the symptom reproduces on a **release-like build on a physical device**. If the report is from a debug build or an emulator, that is the first finding.

---

## 3. Initial project inspection

Run `inspect-project.md`, then establish measurement capability.

```bash
# can anything be measured?
grep -rn 'androidx.benchmark\|MacrobenchmarkRule\|baselineprofile' --include='*.gradle.kts' --include='*.kt' . | grep -v build/
find . -name 'baseline-prof.txt' -o -name 'startup-prof.txt' -not -path '*/build/*'
grep -rn 'profileable' --include='AndroidManifest.xml' . | grep -v build/
grep -rn 'isMinifyEnabled\|optimization\s*{' --include='*.gradle.kts' . | grep -v build/
grep -rn 'reportsDestination\|metricsDestination\|composeCompiler\s*{' --include='*.gradle.kts' . | grep -v build/
grep -rn 'testTagAsResourceId' --include='*.kt' . | grep -v build/
```

**MUST** record which of these exist, because they determine which measurements are possible:

| Capability | Needed for |
|---|---|
| Benchmark variant (release-like, debug-signed) | any Macrobenchmark |
| `<profileable android:shell="true" />` | trace data |
| `androidx.benchmark:benchmark-macro-junit4` | `StartupTimingMetric`, `FrameTimingMetric` |
| `composeCompiler { reportsDestination }` | stability reports |
| `testTagAsResourceId = true` | UI Automator finding Compose nodes |
| Baseline Profile | ~30% first-launch execution gain; its absence is often the whole answer for startup |
| R8 on release | realistic measurement at all |

**MUST** report a missing capability as a finding. "Cannot measure" is a legitimate first result, and establishing measurement is then the deliverable.

---

## 4. Step-by-step procedure

### 4.1 Classify the symptom — determines the measurement

| Symptom | Primary metric | Likely causes |
|---|---|---|
| Slow cold start | `StartupTimingMetric` | no Baseline Profile; work in `Application.onCreate`; eager DI; synchronous I/O on the main thread |
| Scroll jank | `FrameTimingMetric` | missing lazy-list keys; expensive work in composition; reads high in the tree; unstable parameters |
| Jank during animation | `FrameTimingMetric` | non-lambda modifiers reading animated values; recomposition per frame |
| Slow interaction response | `TraceSectionMetric` | main-thread work; a blocking suspend function |
| Whole-screen churn | Compose compiler report + recomposition counts | state read too high; non-skippable composables |
| High memory / GC pressure | profiler | allocation in composition; large bitmaps; retained scopes |

### 4.2 Measure the baseline — MUST, before any change

**MUST** measure on a **physical device**, with a **non-debuggable, minified** build.

```kotlin
@LargeTest
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollFeed() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.WARM,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        val list = device.findObject(By.res("feed_list"))
        list.setGestureMargin(device.displayWidth / 5)
        list.fling(Direction.DOWN)
        list.fling(Direction.UP)
    }
}
```

**MUST** record the baseline numbers verbatim — percentiles, not an average. P50 improvements with a worse P99 are a regression in user experience.

**MUST NOT** measure on an emulator, a debug build, or a device on low battery.

### 4.3 Gather the Compose compiler report

```kotlin
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_metrics")
}
```

```bash
./gradlew assembleRelease
cat <module>/build/compose_compiler/*-composables.txt | grep -v 'skippable'
cat <module>/build/compose_compiler/*-classes.txt | grep '^unstable'
```

**MUST** correlate the report against the measurement. A composable reported `restartable` but not `skippable` matters only if it is on the janky path.

**MUST NOT** treat every `unstable class` in the report as a defect. **Strong skipping is on by default since Kotlin 2.0.20**, so an unstable parameter is still skippable, compared by instance equality. The report is a lead, not a verdict.

### 4.4 Diagnose against the known causes

Check, in the order of how often they are the answer:

| # | Check | Evidence |
|---|---|---|
| 1 | Baseline Profile missing or stale | no `baseline-prof.txt`, or critical flows changed since generation |
| 2 | Lazy list without `key` | `items(list) { }` with no `key =` |
| 3 | Expensive work in a composable body without `remember` | sorting, filtering, mapping, formatting in composition |
| 4 | High-frequency state read high in the tree | `scrollState.value` read above the consumer |
| 5 | Non-lambda modifier on an animated value | `Modifier.background(animatedColor)`, `Modifier.offset(x.dp)` |
| 6 | Whole object passed where two fields are used | `Header(news: News)` |
| 7 | `remember` with a missing key | a stale value — correctness, not just performance |
| 8 | Backwards write | state written during composition |
| 9 | Main-thread I/O | a suspend function not using `withContext` |
| 10 | Eager startup work | heavy `Application.onCreate`, eager DI singletons |

See `../references/android/compose-ui.md` and `../references/quality/performance.md`.

**MUST** tie every proposed fix to a measurement or a report line. A fix with no evidence does not go in the plan.

### 4.5 Report and stop — audit mode

Present: baseline numbers, diagnosis, and a fix plan ordered by expected gain ÷ risk. **MUST** stop here in audit mode.

**MUST** state where the evidence is weak. "Probably the list keys" is an honest finding; presenting it as diagnosed is not.

### 4.6 Fix — one change at a time, re-measured

**MUST** apply one fix, re-measure, record the delta. A batch of fixes produces one unattributable number.

```kotlin
// CORRECT — stable key; only changed items recompose
LazyColumn {
    items(items = notes, key = { it.id }) { NoteRow(it) }
}
```

```kotlin
// CORRECT — work moved out of composition
val sorted = remember(contacts, comparator) { contacts.sortedWith(comparator) }
```

```kotlin
// CORRECT — read deferred to the layout phase
Column(modifier = Modifier.offset { IntOffset(x = 0, y = scrollProvider()) })
```

```kotlin
// CORRECT — read deferred to the draw phase
Box(Modifier.fillMaxSize().drawBehind { drawRect(color) })
```

```kotlin
// WRONG — "optimisation" with no evidence, and it introduces a correctness bug
val sorted = remember { contacts.sortedWith(comparator) }   // missing keys → stale list
```

**MUST NOT** add `remember` without the correct keys. A missing key converts a performance concern into a correctness defect.

**MUST NOT** add `@Stable` or `@Immutable` to a type that does not satisfy the contract. Lying to the compiler produces missed recompositions — stale UI, which is worse than slow UI.

**MUST NOT** add `kotlinx-collections-immutable` reflexively. With strong skipping on, it is optional; add it only where a stable `equals` comparison is demonstrably needed.

### 4.7 Validate

Run section 7.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | **No measurement capability** (no benchmark variant, not profileable) | (a) add the benchmark infrastructure first — this becomes the deliverable; (b) proceed on static analysis only, clearly labelled as unverified | **MUST** ask. Strongly recommend (a). |
| D2 | Symptom was observed on a **debug build or emulator** | (a) re-measure release-like on a device before any work; (b) proceed | **MUST** choose (a). A debug-build measurement is not evidence. |
| D3 | **No Baseline Profile** and the symptom is startup | (a) generate one first — documented ~30% first-launch gain; (b) investigate code first | **MUST** recommend (a). It is usually the single largest startup win and needs no app-code change. |
| D4 | Fix requires **restructuring state ownership** | (a) in scope; (b) minimal fix plus a reported follow-up | **MUST** ask. A state-ownership change is an architecture change. |
| D5 | Cause is in a **third-party composable** | (a) upgrade the library; (b) wrap/replace it; (c) accept | **MUST** ask. **MUST NOT** fork it silently. |
| D6 | Cause is **data volume**, not rendering (10,000 items in one list) | (a) paging; (b) rendering fixes only | **MUST** ask. Rendering fixes will not save an unbounded list. |
| D7 | Fix needs a **stability configuration file** for a type you do not own | (a) add the config file; (b) wrap the type locally | **SHOULD** recommend (a) — it is less invasive than wrappers. |
| D8 | Measured improvement is **within noise** | (a) revert the change; (b) keep it on correctness grounds | **MUST** report honestly. **SHOULD** revert a change that does not measurably help. |
| D9 | The real cause is **outside Compose** (slow network, slow query, main-thread I/O) | — | **MUST** report that and stop. Recomposition fixes will not help. |
| D10 | Fix requires enabling a **compiler flag or build-config change** | — | **MUST** ask. Build-configuration changes affect every module. |

---

## 6. Implementation rules

**MUST:**

1. Measure before changing anything, on a physical device with a release-like build.
2. Record percentiles, not averages.
3. Tie every fix to a measurement or a compiler-report line.
4. Apply one fix at a time and re-measure.
5. Report deltas honestly, including changes that did not help.
6. Pass all inputs as `remember` keys.
7. Prefer the lambda overloads of modifiers for frequently changing values.
8. Defer state reads to the latest phase that can satisfy them.
9. Keep the Compose compiler report in the project if it was added, so the next investigation starts warmer.

**MUST NOT:**

10. Change code before measuring (unless D1(b) is approved and labelled unverified).
11. Trust a debug-build or emulator measurement.
12. Add `remember` without correct keys.
13. Annotate a type `@Stable`/`@Immutable` unless it satisfies the contract.
14. Add `@NonSkippableComposable` or `@DontMemoize` to work around a stability problem.
15. Add `kotlinx-collections-immutable` without a demonstrated need.
16. Set `enableStrongSkippingMode` — it is default since Kotlin 2.0.20.
17. Batch multiple fixes into one measurement.
18. Report an improvement that is within measurement noise as a win.
19. Refactor beyond the implicated code.
20. Change `targetSdk`, dependency versions, or R8 settings as part of this work.

---

## 7. Validation requirements

| # | Check | How | Required |
|---|---|---|---|
| V1 | Baseline measured: physical device, release-like, minified | Macrobenchmark output | MUST |
| V2 | Post-fix measured under **identical** conditions | same device, same build type, same data volume | MUST in fix mode |
| V3 | Improvement reported with percentiles and iteration count | review | MUST in fix mode |
| V4 | Each fix measured **individually** | one re-measure per fix | MUST in fix mode |
| V5 | Compose compiler report gathered | `build/compose_compiler/*` | MUST |
| V6 | Correctness preserved: full test suite passes | module test tasks | MUST |
| V7 | No stale-state defect introduced by a new `remember` | review keys; UI test the affected screen | MUST |
| V8 | Compiles all targets | `assembleDebug` + KMP native | MUST |
| V9 | Release build with R8 | `bundleRelease` | MUST |
| V10 | Baseline Profile regenerated if critical flows changed | `generateBaselineProfile` | MUST if flows changed |
| V11 | No `@Stable`/`@Immutable` added to a non-conforming type | review | MUST |
| V12 | P99 did not regress while P50 improved | compare both | MUST |
| V13 | Visual/interaction behaviour unchanged | manual or screenshot comparison | SHOULD |

V12 exists because the common failure of this work is improving the median while making the worst frames worse.

---

## 8. Failure handling

| Failure | Response |
|---|---|
| Cannot measure — no benchmark infrastructure | → D1. **MUST NOT** proceed with unlabelled guesses. |
| Measurement is noisy across runs | Increase iterations; fix the device state (battery, thermal, background apps). **MUST NOT** pick the best run. |
| Numbers differ between runs more than the improvement | The result is not significant. **MUST** report that → D8. |
| Fix measurably helps nothing | → D8. **SHOULD** revert. **MUST** report it. |
| Fix helps P50, hurts P99 | **MUST** treat as a regression and investigate. Do not report as a win. |
| Compiler report shows many unstable classes but the path is not janky | **MUST NOT** fix them. Report as an observation. |
| Jank disappears on a high-end device | **MUST** measure on the low-end device the report came from. Do not close on better hardware. |
| Cause is a slow network call or query | → D9. **MUST** report and stop. |
| Cause is in generated or third-party code | → D5. **MUST NOT** patch a library silently. |
| A test fails after a performance change | **MUST** stop. A correctness regression outranks any measured gain. Revert. |
| Startup improves only after a Baseline Profile is generated from a minified build | That is the documented failure mode — a profile generated from an obfuscated build silently fails to match. **MUST** generate from a non-minified benchmark variant. |
| Only an emulator is available | **MUST** mark V1/V2 `NOT RUN`, downgrade the Outcome, and label every conclusion unverified. |

---

## 9. Completion criteria

### Audit mode

1. Measurement capability assessed; gaps reported.
2. Baseline measured on a physical device with a release-like build, or D1/D2 recorded.
3. Compose compiler report gathered and correlated with the measurement.
4. Diagnosis tied to evidence; weak evidence labelled as such.
5. Fix plan ordered by expected gain ÷ risk, each item evidence-linked.
6. Zero files changed, other than adding measurement infrastructure if approved.

### Fix mode

1. Every fix traceable to a measurement or report line.
2. Each fix measured individually, under identical conditions (V2, V4).
3. Improvement reported with percentiles and iteration counts; non-improvements reported too.
4. V6–V9, V11, V12 `PASS`.
5. V10 `PASS` if critical flows changed.
6. No correctness regression; no stale-state defect from a new `remember`.
7. No stability annotation added to a non-conforming type.
8. No change made outside the implicated code.

---

## 10. Final report format

Base skeleton from `README.md`, with these additions.

```markdown
## Compose Performance — <AUDIT | FIX>: <symptom>

### Outcome
<AUDIT COMPLETE | IMPROVED | NO MEASURABLE IMPROVEMENT | CANNOT MEASURE | BLOCKED | NEEDS DECISION> — one sentence.

### Symptom
| | |
|---|---|
| Reported | |
| Reproduction | <screen, interaction, data volume> |
| Device / OS | |
| Build measured | <release-like, minified, physical device> |

### Measurement capability
| Capability | Present |
|---|---|
| Benchmark variant | |
| `<profileable>` | |
| Macrobenchmark dependency | |
| Compose compiler reports | |
| `testTagAsResourceId` | |
| Baseline Profile | |
| R8 on release | |

### Baseline measurement
| Metric | P50 | P90 | P99 | Iterations | Device |
|---|---|---|---|---|---|

<State `NOT MEASURED — <reason>` if measurement was impossible; every conclusion
below is then explicitly unverified.>

### Compose compiler report — relevant lines only
| Composable / class | Report says | On the implicated path? |
|---|---|---|

### Diagnosis
| # | Cause | Evidence | Confidence |
|---|---|---|---|
| 1 | | measurement / report line / static | high / medium / low |

### Fix plan — ordered by gain ÷ risk
| Order | Fix | Expected gain | Risk | Evidence |
|---|---|---|---|---|

### Fixes applied and measured
| Fix | File | P50 before → after | P99 before → after | Verdict |
|---|---|---|---|---|
| | | | | improved / within noise (reverted) / regressed |

### Changes made
| File | Change |
|---|---|

<"None — audit only." where applicable.>

### Validation performed
| # | Check | How | Result |
|---|---|---|---|
| V1 | Baseline measured correctly | | |
| V2 | Post-fix measured identically | | |
| V3 | Percentiles reported | | |
| V4 | Each fix measured individually | | |
| V5 | Compiler report gathered | | |
| V6 | Test suite passes | | |
| V7 | No stale-state defect | | |
| V8 | Compiles all targets | | |
| V9 | Release build with R8 | | |
| V10 | Baseline Profile regenerated | | |
| V11 | No false stability annotations | | |
| V12 | P99 did not regress | | |
| V13 | Behaviour unchanged | | |

### Not done
### Observations
<Compiler-report findings off the implicated path, missing infrastructure, data-volume
concerns. No fixes applied.>

### Decisions needed
```
