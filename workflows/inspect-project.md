# Workflow: Inspect Project

**Prerequisite for every other workflow in this directory.** Read `README.md` for the standing contract.

---

## 1. Objective

Establish what the project **actually is** — module graph, versions, libraries, patterns in use, test posture — so that subsequent work matches the codebase instead of a generic template.

Produces a **Project Profile** that later steps cite. Produces **no code changes**.

**MUST** run this before the first file creation or modification in any task. **MAY** be skipped only if a Project Profile from the same session is already in context and no build files have changed since.

---

## 2. Required inputs

| Input | Required | Default if absent |
|---|---|---|
| Repository root path | yes | the working directory |
| The task that prompted the inspection | yes | — ask; inspection depth depends on it |
| Module or feature area of interest | no | inspect the whole graph at low depth |

---

## 3. Initial project inspection

This workflow **is** the inspection. Execute sections 4.1–4.8 in order.

**MUST NOT** run a Gradle task that writes to the project (`build`, `assemble`, `generate*`) during inspection. Read-only Gradle tasks (`projects`, `dependencies`, `--dry-run`) are permitted but slow — prefer reading files.

---

## 4. Step-by-step procedure

### 4.1 Shape and scale

```bash
ls -la
cat settings.gradle.kts 2>/dev/null || cat settings.gradle
find . -name 'build.gradle.kts' -o -name 'build.gradle' | grep -v build/ | sort
```

Record: module count, module names, whether `build-logic` or `buildSrc` is included, whether an `iosApp/` or `*.xcodeproj` exists.

### 4.2 Versions and lockstep

```bash
cat gradle/libs.versions.toml 2>/dev/null
cat gradle/wrapper/gradle-wrapper.properties
```

Record the Project Profile version block. **MUST** check the lockstep rules in `../references/version-matrix.md`:

- Compose Compiler plugin version **==** Kotlin version
- KSP version matched to Kotlin
- CMP version compatible with Kotlin

**MUST** report a lockstep violation immediately as a finding — it is a latent build failure.

If there is **no** version catalog, record that: version changes will require edits across module files, which raises the cost of any upgrade task.

### 4.3 Is this KMP?

```bash
grep -rl 'kotlin("multiplatform")\|org.jetbrains.kotlin.multiplatform' --include='build.gradle.kts' . | grep -v build/
find . -type d -name commonMain -not -path '*/build/*'
find . -type d \( -name iosMain -o -name androidMain -o -name jvmMain -o -name wasmJsMain \) -not -path '*/build/*'
```

Record: KMP or Android-only; which targets are declared; which source sets exist.

If KMP, record the declared targets verbatim and check them against the tier table in `../references/version-matrix.md`. **MUST** flag a declared deprecated target (`macosX64`, `watchosX64`, `tvosX64`, `watchosArm32`) or a Tier 3 target used as if it were supported (`iosX64`).

### 4.4 Libraries actually in use

```bash
grep -rhoE 'implementation\(libs\.[a-zA-Z0-9.]+\)' --include='build.gradle.kts' . | grep -v build/ | sort -u
```

Fill in the Project Profile library block. For each slot, record the **actual** choice, not the expected one:

| Slot | Look for |
|---|---|
| DI | Koin, Hilt, Dagger, Metro, kotlin-inject, manual |
| HTTP | Ktor, Retrofit + OkHttp, both |
| Serialization | kotlinx.serialization, Moshi, Gson |
| Persistence | Room (`androidx.room` or `androidx.room3`), SQLDelight, Realm, raw SQLite |
| Preferences | DataStore, SharedPreferences, `EncryptedSharedPreferences` |
| Navigation | Navigation 3, Navigation 2 Compose, Voyager, Decompose, custom |
| UI | Compose, Views, both |
| Image loading | Coil, Glide, Landscapist, Kamel |
| Test | kotlin-test, JUnit4, JUnit5, Turbine, MockK, Mockito, Robolectric |

**MUST** record `EncryptedSharedPreferences`, Navigation 2, or `androidx.room3` presence as findings — see `../references/deprecations.md`.

### 4.4b Declared structure convention

```bash
ls PROJECT_STRUCTURE.md docs/PROJECT_STRUCTURE.md .claude/PROJECT_STRUCTURE.md 2>/dev/null
```

**MUST** check for a declared structure file. If present, it decides file placement for every later workflow and overrides the module-based defaults; record it in the Project Profile. The house template is `../templates/structure/PROJECT_STRUCTURE.md`.

### 4.5 Architectural patterns in use

Read **two or three** existing comparable implementations, not one. One file is an anecdote.

```bash
# find the nearest comparable feature to the task at hand
find . -path '*/presentation/*' -o -path '*/ui/*' -name '*ViewModel.kt' -not -path '*/build/*' | head -20
find . -name '*Repository*.kt' -not -path '*/build/*' | head -20
```

For each, record:

| Question | Where to look |
|---|---|
| State: one `uiState` or several flows? data class or sealed interface? | ViewModel |
| One-off events: folded into state, or a `Channel`/`SharedFlow` effect? (house structure: folded into state) | ViewModel |
| `stateIn` policy actually used (`WhileSubscribed(n)`, `Eagerly`, none)? | ViewModel |
| Collection in UI: `collectAsStateWithLifecycle` or `collectAsState`? | screen composable |
| Route/content split, or ViewModel passed into the content composable? | screen composable |
| Domain layer present? Use cases real or pass-through? | `:domain` or `usecase` packages |
| Repository interface owner: `:domain` or `:data`? | import direction |
| Separate DTO / entity / domain model, or one shared class? | `:data` |
| Dispatcher injection or hardcoded `Dispatchers.X`? | repositories |
| Error handling: domain error types, `Result`, or raw exceptions? | repositories |
| `expect`/`actual` used for platform abstraction, or interface + DI? | `commonMain` |
| Module naming convention | module names |
| Package naming convention | directory layout |

**MUST** record the **dominant** pattern and any deviation. The dominant pattern is what new code matches.

### 4.6 Test posture

```bash
find . -type d \( -name test -o -name commonTest -o -name androidTest -o -name iosTest -o -name androidHostTest -o -name androidDeviceTest \) -not -path '*/build/*'
find . -name '*Test.kt' -not -path '*/build/*' | wc -l
find . -name 'Fake*.kt' -not -path '*/build/*' | head -10
```

Record: which test source sets exist, approximate test count, whether fakes or mocks dominate, whether a `:core:testing`-style shared fixture module exists.

Read one existing test near the task area. New tests match its style.

### 4.7 Build and release configuration

```bash
grep -rn 'minSdk\|targetSdk\|compileSdk' --include='build.gradle.kts' . | grep -v build/
grep -rn 'isMinifyEnabled\|isShrinkResources\|optimization\s*{' --include='build.gradle.kts' . | grep -v build/
find . -name 'proguard*.pro' -o -name '*.keep' -not -path '*/build/*'
find . -name 'baseline-prof.txt' -o -name 'startup-prof.txt' -not -path '*/build/*'
ls app/src/*/AndroidManifest.xml 2>/dev/null
```

Record: SDK levels, whether R8 is on for release, whether Baseline Profiles exist, whether a benchmark variant exists.

**MUST** compare `targetSdk` against the Play floor in `../references/version-matrix.md` and flag a gap.

### 4.8 Current health

```bash
git status --porcelain
git log --oneline -10
```

**MUST** record whether the working tree was already dirty. **MUST NOT** mix pre-existing uncommitted changes into the task's diff.

**SHOULD** confirm the project builds before changing it, with the cheapest available check:

```bash
./gradlew help --quiet          # configuration resolves
./gradlew :<module>:compileDebugKotlin --quiet    # or the KMP metadata compile
```

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | **Build already broken** before any change | (a) fix the break first as a separate task; (b) proceed read-only; (c) abort | **MUST** stop. Report the failure verbatim. **MUST NOT** begin the original task on a broken build. |
| D2 | **Working tree already dirty** | (a) user stashes/commits first; (b) proceed and scope the report to your own files | **MUST** stop and ask. Report which files were already modified. |
| D3 | **Version lockstep violated** (e.g. Compose compiler plugin ≠ Kotlin) | (a) fix as a prerequisite; (b) proceed and accept possible build failure | **MUST** stop and ask. A lockstep violation will surface as an unrelated-looking error later. |
| D4 | **No version catalog** and the task requires a dependency change | (a) add a catalog (a structural change — needs approval); (b) edit module files in place, matching existing style | **MUST** ask. Default recommendation: (b), to keep the diff minimal. |
| D5 | **Two conflicting architectural patterns** in the task area | (a) match the nearest comparable code; (b) match the newer pattern; (c) ask | **SHOULD** choose (a) and report. **MUST** ask if "nearest" is ambiguous. |
| D6 | **Task premise not found** — described file, screen, or behaviour does not exist | — | **MUST** stop. Report what was searched and what was found instead. **MUST NOT** create the missing thing on the assumption it was intended. |

---

## 6. Implementation rules

This workflow produces no implementation. The rules it establishes for downstream work:

- **MUST** write the Project Profile into the report, so every later step cites facts rather than assumptions.
- **MUST** name the specific file whose pattern will be copied, for each kind of artefact the task will create.
- **MUST NOT** record a reference-set default as a project fact. The profile describes the project as it is.
- **MUST NOT** run a build task that writes outputs during inspection beyond the health check in 4.8.
- **MUST NOT** modify, format, or reorder any file.

---

## 7. Validation requirements

| Check | How | Required |
|---|---|---|
| Every Project Profile slot filled or explicitly `unknown` | review the report | MUST |
| Patterns derived from ≥2 real files | cite both paths | MUST |
| Version lockstep checked against `../references/version-matrix.md` | review | MUST |
| `targetSdk` compared to the Play floor | review | MUST |
| Declared KMP targets checked against the tier table | review | MUST (if KMP) |
| Working-tree cleanliness recorded | `git status --porcelain` | MUST |
| Build health confirmed or explicitly not confirmed | the 4.8 command | SHOULD |
| No file modified | `git status --porcelain` unchanged from 4.8 | MUST |

---

## 8. Failure handling

| Failure | Response |
|---|---|
| Not a Gradle project / no build files | **MUST** stop. Report what the directory contains. Do not scaffold a project. |
| Gradle configuration fails | **MUST** capture the error verbatim and stop at D1. Do not attempt a fix as part of inspection. |
| Gradle daemon unavailable / offline / cannot resolve dependencies | **MUST** continue with file-reading only. Mark build health `NOT RUN — <reason>`. **MUST NOT** claim the project builds. |
| A module's build file is unreadable or generated | Record it as `unknown` and proceed. Note it in Observations. |
| Repository too large to enumerate | **MUST** narrow to the task's module and its direct dependencies. State the narrowing in the report. |
| Monorepo containing unrelated projects | **MUST** confirm which project is in scope before proceeding. |
| Inspection contradicts the user's description of the project | **MUST** report the contradiction before acting on either version. |

---

## 9. Completion criteria

**MUST** all hold:

1. Project Profile complete, every slot filled or marked `unknown`.
2. Build type identified: Android-only, KMP shared-logic, or KMP shared-UI.
3. Dominant architectural pattern identified and cited to ≥2 files.
4. For each artefact the task will produce, a named existing file to copy the pattern from.
5. Version lockstep verified; violations reported.
6. Working-tree state recorded.
7. Build health confirmed, or explicitly marked `NOT RUN` with a reason.
8. Every triggered decision point either resolved by the user or listed under **Decisions needed**.
9. Zero files modified.

---

## 10. Final report format

Base skeleton from `README.md`, with **Inspection findings** replaced by the Project Profile.

```markdown
## Inspect Project — <task that prompted it>

### Outcome
<READY | NEEDS DECISION | BLOCKED> — one sentence.

### Project Profile

**Shape**
- Type: <Android-only | KMP shared logic | KMP shared logic + UI>
- Modules (<n>): <list, or the relevant subgraph>
- Build logic: <build-logic included build | buildSrc | none>
- iOS app present: <yes/no>

**Versions**
| Component | Project | Lockstep |
|---|---|---|
| Kotlin | | — |
| Compose Compiler plugin | | OK / **MISMATCH** |
| KSP | | OK / **MISMATCH** |
| Compose Multiplatform | | OK / **MISMATCH** / n/a |
| AGP | | — |
| Gradle | | — |
| compileSdk / targetSdk / minSdk | | vs Play floor: OK / **BELOW** |
| Version catalog | yes/no | — |

**KMP targets** (omit if Android-only)
| Target | Tier | Note |
|---|---|---|

**Libraries in use**
| Slot | Choice | Note |
|---|---|---|
| DI | | |
| HTTP | | |
| Serialization | | |
| Persistence | | |
| Preferences | | |
| Navigation | | |
| UI | | |
| Test | | |

**Patterns in use** — cited to real files
| Aspect | Observed | Evidence |
|---|---|---|
| UI state shape | | `path:line` |
| One-off events | | `path:line` |
| `stateIn` policy | | `path:line` |
| State collection | | `path:line` |
| Route/content split | | `path:line` |
| Domain layer | | `path:line` |
| Repository interface owner | | `path:line` |
| Model per layer | | `path:line` |
| Dispatcher injection | | `path:line` |
| Error handling | | `path:line` |
| Platform abstraction | | `path:line` |
| Module / package naming | | |

**Test posture**
- Source sets present: <...>
- Test count: ~<n>
- Doubles: <fakes dominant | mocks dominant | mixed | none>
- Shared fixtures module: <path | none>
- Style reference: `path`

**Build / release**
- R8 on release: <yes/no>
- Resource shrinking: <yes/no>
- Baseline Profile: <present/absent>
- Benchmark variant: <present/absent>
- Keep rules: <path(s) | none>

**Health**
- Working tree: <clean | dirty: files>
- Build check: `<command>` → <PASS | FAIL | NOT RUN — reason>

### Patterns to follow for this task
| Artefact the task needs | Copy the pattern from |
|---|---|
| e.g. ViewModel | `path` |
| e.g. Repository | `path` |
| e.g. Test | `path` |

### Changes made
None.

### Validation performed
| Check | Command | Result |
|---|---|---|

### Not done
<Inspection areas deliberately skipped and why. "Nothing." if complete.>

### Observations
<Findings outside the task's scope: deprecated APIs, lockstep risks, targetSdk gap,
inconsistent patterns. Each with `file:line` and a one-line recommendation. No fixes applied.>

### Decisions needed
<Triggered decision points D1–D6, each with options and a recommendation. "None." if none.>
```
