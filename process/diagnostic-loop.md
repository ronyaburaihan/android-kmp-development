# Primitive: The Diagnostic Loop

Adapted from Matt Pocock's `diagnosing-bugs` skill — see `README.md` § Attribution. The loop
constructions and platform rules below are specific to Android and KMP.

## Purpose

Build a **tight pass/fail signal for the bug before forming any theory about it**. With a tight
signal you will find the cause. Without one you will read code, build a plausible story, change
something, and declare victory on a bug that still exists.

> This is the skill. Everything else is mechanical.

---

## When to invoke

- `../workflows/diagnose-and-fix-bug.md` § 4.1 — **mandatory**, it is that step.
- `../workflows/audit-compose-performance.md` — the loop is a Macrobenchmark run.
- Any "it works on my machine" / "only in release" / "only on iOS" report.

---

## The hard gate — MUST

**MUST NOT** form hypotheses, and **MUST NOT** read code looking for a cause, until a red-capable
command exists.

If you catch yourself reading source to build a theory before you can make the bug fail on demand,
**stop**. Jumping to hypothesis is the exact failure this primitive prevents.

A loop is ready when you can name **one command** that you have **already run at least once** —
showing the invocation and its output — and **all four** of these hold:

| Property | Test |
|---|---|
| **Red-capable** | It fails on *this* bug — not on a nearby or more general failure |
| **Deterministic** | Same verdict every run. Pin time, seed RNG, isolate the file system, fix the locale |
| **Fast** | Seconds. A two-minute loop will not be run enough times to localise anything |
| **Agent-runnable** | Executes unattended, no human tapping a screen |

A command you believe would fail is not a loop. **MUST** run it and **MUST** show the output;
"runs without erroring" is not red-capable — it must be able to catch *this specific* bug.

No red-capable command → no Phase 2. If you cannot build one, **MUST** stop and ask for what is
missing: device access, a crash report, a captured response body, a user ID that reproduces, or
permission to add temporary instrumentation.

---

## Loop constructions, best first

| # | Construction | Use when | Cost |
|---|---|---|---|
| 1 | **Failing `commonTest`/`jvmTest`** at the nearest seam (`tdd.md`) | The bug is in shared logic — mapping, repository, use case, state | seconds |
| 2 | **Turbine on the emitting Flow** | Wrong state, wrong order, missing emission, a stuck `StateFlow` | seconds |
| 3 | **Ktor `MockEngine` replaying the exact bad payload** | Triggered by a real server response. Paste the body into the mock | seconds |
| 4 | **Real in-memory Room DB, seeded** | Query, constraint, or type-converter defect | seconds |
| 5 | **`MigrationTestHelper` from the exported schema** | Upgrade crash or data loss between schema versions | seconds |
| 6 | **`iosSimulatorArm64Test` of the same `commonTest`** | Android works, iOS does not. Divergence *is* the finding | tens of seconds |
| 7 | **Compose `runComposeUiTest`** | Wrong render for a state, or an interaction emitting the wrong intent | tens of seconds |
| 8 | **Macrobenchmark** with `StartupTimingMetric` / `FrameTimingMetric` | Jank, slow startup, scroll regression. Numbers, never impressions | minutes |
| 9 | **Instrumented `connectedAndroidTest`** | Genuine platform behaviour: permissions, process death, `WorkManager`, `Activity` lifecycle | minutes |
| 10 | **`bundleRelease` + install** | "Only in release" — R8 stripped a reflectively used member, or a `kotlinx.serialization` class lost its constructor | minutes |
| 11 | **Filtered log capture** — `adb logcat`, Xcode console | Crash-only, no test harness reachable yet. A step towards a loop, not a loop |
| 12 | **Human-in-the-loop script** | Last resort. A numbered bash script telling the human exactly what to tap, and what to paste back |

**SHOULD** climb this list as the investigation narrows: a crash first seen in logcat (11) usually
becomes a `commonTest` (1) once the input is known. Promote the loop as soon as you can; every rung
up makes it faster and more deterministic.

### Tightening

- **Faster** — cache fixture setup, narrow to one module, one target, one test class.
- **Sharper** — assert the specific wrong value, not merely "did not crash".
- **Deterministic** — inject a fixed `Clock`, seed every random source, use Okio `FakeFileSystem`,
  pin the locale and time zone. A flaky loop is worse than none: it will confirm whatever you hope.

---

## After the gate

**Phase 2 — Reproduce and minimise.** Run the loop; confirm it reproduces the *user's* symptom, not a
neighbour. Then strip load-bearing elements — inputs, callers, config, data, modules, targets — until
what remains is minimal. In a KMP project, minimising *across targets* is itself diagnostic: a bug
present on `iosSimulatorArm64` and absent on `jvm` points at `expect`/`actual`, a native-only
dependency, or a platform-specific default.

**Phase 3 — Hypothesise.** Write **3–5 ranked, falsifiable** hypotheses *before testing any*. Each in
the form:

```
If <cause>, then <change Y> makes the bug disappear, and <change Z> makes it worse.
```

A hypothesis with no prediction cannot be wrong, so it cannot be useful.

**Phase 4 — Instrument.** Each probe maps to one Phase 3 prediction. Change one variable at a time.
Prefer a debugger or REPL > targeted logs > broad logging. Tag every temporary statement uniquely
(`[DEBUG-a4f2]`) so cleanup is a single `grep`.

**Phase 5 — Fix with a regression test.** Write the test **before** the fix, at the seam where the
bug pattern lives in production (`tdd.md`). Sequence: fail → fix → pass → re-run the original
user-level scenario. Then fix the **cause**, not the symptom — see
`../workflows/diagnose-and-fix-bug.md` § 4.2 and § 4.4.

**Phase 6 — Clean up.**

- [ ] Original scenario no longer reproduces
- [ ] Regression test passes, and was seen failing before the fix
- [ ] Every `[DEBUG-…]` statement removed
- [ ] Throwaway harnesses deleted
- [ ] Root cause stated in the commit message (`../templates/commit-message.md`)
- [ ] Sibling occurrences of the same pattern listed in the report, not silently fixed

---

## Android/KMP specifics

| Symptom | What the loop must control |
|---|---|
| "Only in release" | R8. The loop is `bundleRelease`; check `-keep` rules and `kotlinx.serialization` members. `../references/quality/performance.md` |
| "Only on iOS" | Target divergence. Run the same `commonTest` on both; difference narrows to `expect`/`actual` or a native default |
| "Only after rotation / returning to the app" | Process death and `SavedStateHandle`. Needs an instrumented loop (9), not a unit test |
| "Only for some users" | Locale, time zone, right-to-left layout, API level, or a stale cached payload. Pin each in the loop |
| Crash with no Kotlin frames on iOS | Symbolication. `../references/release/ios-release.md` |
| Jank | Never diagnose by eye. Macrobenchmark (8) or it did not happen |
| Flaky test | Treat the flake as the bug. **MUST NOT** add a retry or `@Ignore` to get green |

**MUST NOT**, to make the build pass: modify, skip, `@Ignore` or delete an existing test; add a
sleep; or widen a timeout. Those convert a reproducible defect into an intermittent one.

---

## Anti-patterns

| Anti-pattern | Why it fails |
|---|---|
| Reading code to build a theory first | The failure this primitive exists to prevent |
| Fixing on one plausible hypothesis, untested | You will "fix" a bug that is still there |
| A loop that asserts "no crash" | Passes for most wrong behaviour |
| Loop that needs a human to tap | Cannot be run fifty times, so localisation never happens |
| Multiple probes added at once | No probe's result is attributable |
| Declaring it fixed without re-running the user's scenario | The seam passes; the app still fails |
| Leaving debug logging in the diff | Ships noise, and signals the investigation was never closed |

---

## It's working if

- A command existed that failed **only** on this bug, before any hypothesis was written.
- That command is in the report, verbatim, so anyone can run it.
- Hypotheses were written down and ranked before being tested, each with a prediction.
- The regression test was seen red before the fix made it green.
- The original user-level scenario was re-run, not only the test.
- Nothing `[DEBUG-…]` survives in the diff.

---

## Where it fits

```
diagnostic-loop (gate) → minimise → hypothesise → instrument
   → tdd (regression test, red) → fix → allTests → two-axis-review
```

Procedure, decision points and report format: `../workflows/diagnose-and-fix-bug.md`.
