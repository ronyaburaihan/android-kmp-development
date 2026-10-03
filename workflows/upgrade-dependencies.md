# Workflow: Upgrade Dependencies

Standing contract: `README.md`. Prerequisite: `inspect-project.md`.

---

## 1. Objective

Raise dependency versions — Kotlin, AGP, Gradle, Compose, Compose Multiplatform, KSP, or libraries — without breaking the build, the tests, or the shipped app.

Scope is version numbers and the minimum code changes those versions force. It is **not** a refactor, a migration, or a deprecation cleanup (see `remediate-deprecations.md`).

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| What to upgrade | yes | **MUST** ask. "Update everything" is not a scope — see D1. |
| Target version, or "latest stable" | no | Default: latest stable, verified against the canonical source. |
| Reason | no | Affects risk appetite. A CVE fix justifies more disruption than tidiness. |
| Release window | no | Affects whether a toolchain upgrade is acceptable now. |

**MUST NOT** invent a version number. **MUST** verify every target version against the canonical source listed in `../references/version-matrix.md`.

---

## 3. Initial project inspection

Run `inspect-project.md`, then:

```bash
cat gradle/libs.versions.toml
cat gradle/wrapper/gradle-wrapper.properties
grep -rn 'kotlin(\|org.jetbrains.kotlin\|com.android\|org.jetbrains.compose' --include='*.gradle.kts' . | grep -v build/
ls build-logic/convention/src/main/kotlin/ 2>/dev/null
```

**MUST** record, before changing anything:

| Fact | Why |
|---|---|
| Current Kotlin, Compose-compiler plugin, KSP, CMP, AGP, Gradle versions | The lockstep set |
| Whether a version catalog exists | Without one, every module file changes |
| Whether convention plugins pin versions independently of the catalog | A second place that can drift |
| Which libraries are KSP-based (Room, Koin annotations, Moshi…) | KSP-based processors break on Kotlin bumps |
| Which libraries are compiler plugins (Compose, serialization, Parcelize) | These are Kotlin-version-locked |
| Baseline: does the build currently pass? | Required to attribute failures |

**MUST** establish a green baseline before touching a version:

```bash
./gradlew assembleDebug <module>:allTests --quiet
```

**MUST NOT** start an upgrade on a red build. → D7.

---

## 4. Step-by-step procedure

### 4.1 Classify the upgrade — determines everything after

| Class | Examples | Risk |
|---|---|---|
| **A — Toolchain** | Kotlin, AGP, Gradle, Compose compiler, KSP, CMP | High. Lockstep-bound. Can break code generation and every compiler plugin. |
| **B — Framework** | Compose BOM, Lifecycle, Navigation, Room | Medium. Behaviour and deprecations. |
| **C — Library** | Ktor, Koin, Coil, kotlinx.* | Low–medium. API changes. |
| **D — Patch** | same major.minor, patch bump only | Low. |

**MUST NOT** mix classes in one change. One class per change, verified independently. A combined failure cannot be attributed.

### 4.2 Resolve the lockstep set — MUST

Upgrading Kotlin is never a single-version change. See `../references/version-matrix.md`.

| If upgrading | MUST also move, in the same change |
|---|---|
| Kotlin | Compose compiler plugin (**== Kotlin version exactly**), KSP (Kotlin-matched), and check CMP compatibility |
| CMP | check the CMP release note for its supported Kotlin version |
| AGP | check Gradle minimum and the maximum supported API level |
| Gradle | check the AGP compatibility matrix |

**MUST** write down the full resolved set before editing, and present it at 4.3.

```toml
# Example resolved set — verify each against its canonical source
[versions]
kotlin          = "2.4.20"   # compose-compiler plugin MUST equal this
ksp             = "2.4.20-<ksp>"
composeMultiplatform = "1.12.1"
agp             = "9.4.0"
```

### 4.3 Confirm the plan — MUST

Present and wait:

- the resolved version set, each with the source URL it was verified against
- the classification (A/B/C/D)
- known breaking changes from the release notes, and the code they will force
- whether `targetSdk`/`compileSdk` must move (→ `upgrade-target-sdk.md`, a separate task)
- rollback: the current versions, to revert to

**MUST NOT** proceed past this point for a class A upgrade without approval.

### 4.4 Read the release notes — MUST

**MUST** read the release notes or migration guide for each version crossed, not only the target. Crossing 2.2 → 2.4 means reading 2.3 as well.

**MUST** record each breaking change that applies to this codebase, with the file it affects. A breaking change that affects nothing in the project **MUST** be recorded as not applicable, not ignored silently.

### 4.5 Apply the version change

**MUST** edit the version catalog only, where one exists. One commit-sized change.

**MUST** check convention plugins and `build-logic` for independently pinned versions — a catalog bump plus a hardcoded plugin version produces a confusing mismatch.

**MUST NOT** change dependency **scopes** (`implementation` → `api`), add dependencies, or reorganise the catalog while upgrading.

### 4.6 Fix only what the upgrade forces

**MUST** fix: compile errors, and APIs removed in the new version.

**SHOULD** fix: new warnings the upgrade introduces **only** where the underlying API is now removed or behaviour-changed.

**MUST NOT** fix: pre-existing deprecations, newly surfaced warnings on unrelated code, formatting the new toolchain prefers differently. Those belong to `remediate-deprecations.md`.

```kotlin
// IN SCOPE — the old API no longer exists
// before (removed in the new version)
composeCompiler { enableStrongSkippingMode = true }
// after
// (deleted — default since Kotlin 2.0.20)
```

```kotlin
// OUT OF SCOPE — a deprecation the upgrade merely made louder
@Deprecated("Use X") fun oldThing()   // still compiles → leave it, report it
```

**MUST** record every forced code change in the report, with the release-note item that forced it.

### 4.7 Verify incrementally

Verify after **each** class, not at the end:

```bash
./gradlew --stop                     # stale daemons cause misleading failures
./gradlew help --quiet               # configuration resolves
./gradlew assembleDebug
./gradlew <module>:allTests
```

For KMP, **MUST** additionally compile every target — a toolchain upgrade commonly breaks only native:

```bash
./gradlew compileKotlinMetadata
./gradlew :<shared>:compileKotlinIosSimulatorArm64
./gradlew :<shared>:linkDebugFrameworkIosSimulatorArm64
```

### 4.8 Check what the version numbers do not show

**MUST** verify, for class A and B upgrades:

| Check | Why |
|---|---|
| Release build with R8 enabled | New library versions change what R8 can strip; keep rules go stale silently |
| Generated code present for every KSP target | A KSP/Kotlin mismatch can produce *empty* output rather than an error |
| APK/AAB size vs baseline | A sudden jump means a new transitive dependency or a broken keep rule |
| Compose compiler stability report, if one is tracked | A Compose upgrade can change skippability |
| App launches and the primary flow works | Compilation success is not runtime success |

### 4.9 Validate

Run section 7.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | Request is "**upgrade everything**" | (a) sequence by class: D → C → B → A, verified at each step; (b) one named upgrade now | **MUST** ask. Recommend (a) as separate changes, never one diff. |
| D2 | Upgrade requires a **`targetSdk`/`compileSdk`** move | (a) do the SDK upgrade first via `upgrade-target-sdk.md`; (b) stop here | **MUST** stop. These are separate tasks with separate risk. |
| D3 | Target version is **not stable** (alpha/beta/RC) | (a) take the latest stable instead; (b) take the pre-release for a named reason | **MUST** ask. **MUST NOT** introduce a pre-release version unprompted. |
| D4 | Upgrade forces a **source-incompatible API migration** across many files | (a) proceed, mechanical change recorded; (b) stay on the current version; (c) stage it | **MUST** ask with the file count. |
| D5 | A dependency **has no version** compatible with the target Kotlin/CMP | (a) hold the upgrade; (b) replace the dependency (a structural change); (c) take its pre-release | **MUST** stop. This is the usual blocker for a Kotlin bump. |
| D6 | Upgrade **removes** a library the project depends on | — | **MUST** stop and report the replacement path. Do not improvise. |
| D7 | **Build was already red** before the upgrade | (a) fix the break first; (b) abort | **MUST** stop. Without a green baseline, no failure can be attributed. |
| D8 | Only a **transitive** dependency needs the bump | (a) upgrade the direct dependency that brings it; (b) add a constraint/resolution override | **SHOULD** prefer (a). Report if (b) is the only option — it is a hidden pin. |
| D9 | Upgrade changes a **wire or storage format** (serialization, Room, DataStore) | — | **MUST** stop. Treat it as a data-compatibility change, not a version bump. |

---

## 6. Implementation rules

**MUST:**

1. Verify every target version against the canonical source in `../references/version-matrix.md`. Never from memory.
2. Keep the lockstep set internally consistent: Compose compiler plugin **==** Kotlin; KSP matched to Kotlin; CMP compatible with Kotlin.
3. Change versions in the version catalog only, where one exists.
4. Change one class (A/B/C/D) per change.
5. Run `./gradlew --stop` before the first verification after a toolchain change.
6. Record the previous version set for rollback.
7. Fix exactly the code the upgrade forces, and cite the release-note item for each fix.

**MUST NOT:**

8. Mix a toolchain upgrade with a feature change, a refactor, or a deprecation cleanup.
9. Change `targetSdk`, `compileSdk`, `minSdk`, or `jvmTarget` as part of a dependency upgrade.
10. Introduce an alpha/beta/RC version unprompted.
11. Add, remove, or re-scope a dependency.
12. Reformat or reorder the version catalog.
13. Suppress a new error with `@Suppress`, `-Xsuppress`, or a lint baseline entry to make the build pass.
14. Disable R8 full mode, strong skipping, or a compiler check to work around a failure.
15. Modify or `@Ignore` a test that fails after the upgrade.
16. Declare success on `assembleDebug` alone for a class A or B upgrade.

---

## 7. Validation requirements

| # | Check | Command | Required |
|---|---|---|---|
| V1 | Configuration resolves | `./gradlew help --quiet` | MUST |
| V2 | Debug build | `./gradlew assembleDebug` | MUST |
| V3 | **Release build with R8** | `./gradlew assembleRelease` (or `bundleRelease`) | MUST for class A/B |
| V4 | All unit tests | `./gradlew <module>:allTests` / `testDebugUnitTest` | MUST |
| V5 | KMP: every target compiles | `compileKotlinMetadata` + each native target | MUST if KMP |
| V6 | KMP: iOS framework links | `linkDebugFrameworkIosSimulatorArm64` | MUST if KMP with iOS |
| V7 | KSP output present for every target | inspect generated sources per target | MUST if KSP used |
| V8 | Lockstep consistent | review the catalog against `../references/version-matrix.md` | MUST |
| V9 | Lint / detekt / ktlint | project tasks | MUST if configured |
| V10 | Instrumented tests | `connectedDebugAndroidTest` | SHOULD |
| V11 | App launches; primary flow works | manual | MUST for class A/B |
| V12 | Artifact size vs baseline | compare AAB/APK size | SHOULD |
| V13 | No new `@Suppress` or baseline entries added | review the diff | MUST |

---

## 8. Failure handling

| Failure | Response |
|---|---|
| Configuration fails after the bump | Usually a plugin/Gradle incompatibility. **MUST** check the AGP↔Gradle and Kotlin↔plugin matrices before editing code. |
| "Compose Compiler / Kotlin version mismatch" | The lockstep rule. **MUST** set the plugin version equal to Kotlin. **MUST NOT** suppress the check. |
| KSP fails, or silently generates nothing | KSP version not matched to Kotlin. **MUST** use the Kotlin-matched KSP release. Empty output with a green build is the dangerous case — V7 exists for it. |
| Only a native target fails | Typically a dependency with no native artifact at the new version → D5. **MUST NOT** drop the target to make it pass. |
| A test fails after the upgrade | **MUST** determine whether behaviour changed or the test was wrong. If behaviour changed, report it as a behaviour change — that is a product decision. **MUST NOT** adjust the assertion to match the new output without saying so. |
| Release build fails while debug passes | R8. A keep rule has gone stale or a library's consumer rules changed. **MUST** fix the keep rule; **MUST NOT** disable minification. |
| Build passes, app crashes at runtime | Usually R8 stripping something newly reflected over, or a changed default. **MUST** reproduce on the release build and fix the keep rule. |
| Transitive conflict / duplicate class | **MUST** resolve with the dependency tree (`./gradlew :app:dependencies`), not a blanket exclusion. Report any exclusion added. |
| Upgrade works locally, fails in CI | Usually a cached Gradle/konan state or a different JDK. **MUST** report the environment difference rather than changing code. |
| Cannot find a compatible version set | → D5. **MUST** report the specific blocking dependency and its latest compatible version. |
| Partway through, build is red and the cause is unclear | **MUST** revert to the recorded baseline set, then re-apply one version at a time. **MUST NOT** keep layering fixes on an unattributed failure. |

---

## 9. Completion criteria

**MUST** all hold:

1. One upgrade class in the change; no feature or refactor mixed in.
2. Every version verified against its canonical source, with the URL recorded.
3. Lockstep set consistent and verified.
4. Only version-forced code changes present, each cited to a release-note item.
5. V1, V2, V4, V5, V7, V8, V13 `PASS`.
6. V3 and V11 `PASS` for a class A or B upgrade, or the Outcome is `DONE WITH CAVEATS`.
7. No test modified, skipped, or deleted.
8. No `@Suppress`, lint-baseline entry, or disabled compiler check added.
9. `targetSdk`/`compileSdk`/`minSdk` unchanged.
10. Rollback version set recorded in the report.

---

## 10. Final report format

Base skeleton from `README.md`, with these additions.

```markdown
## Upgrade Dependencies — <class A/B/C/D>: <what>

### Outcome
<DONE | DONE WITH CAVEATS | BLOCKED | NEEDS DECISION> — one sentence.

### Inspection findings
- Version catalog: <yes/no>
- Convention plugins pinning versions independently: <yes — `path` | no>
- KSP-based libraries: <list>
- Compiler plugins: <list>
- Baseline build before changes: <PASS | FAIL (D7)>

### Version changes
| Component | From | To | Verified against |
|---|---|---|---|
| Kotlin | | | <URL> |
| Compose compiler plugin | | | == Kotlin |
| KSP | | | <URL> |
| ... | | | |

**Lockstep check:** <CONSISTENT | explain>

**Rollback set:** <the exact previous versions, copy-pasteable>

### Breaking changes reviewed
| Release-note item | Applies here? | Code change |
|---|---|---|
| e.g. `X` removed in 2.4 | yes — `path:line` | <what was done> |
| e.g. `Y` behaviour change | no — project does not use `Y` | none |

### Changes made
| File | Change |
|---|---|

### Validation performed
| # | Check | Command | Result |
|---|---|---|---|
| V1 | Configuration resolves | | |
| V2 | Debug build | | |
| V3 | **Release build with R8** | | |
| V4 | Unit tests | | |
| V5 | KMP targets compile | | |
| V6 | iOS framework links | | |
| V7 | KSP output per target | | |
| V8 | Lockstep consistent | | |
| V9 | Lint / detekt / ktlint | | |
| V10 | Instrumented tests | | |
| V11 | App launches, primary flow | | |
| V12 | Artifact size vs baseline | | |
| V13 | No suppressions added | | |

### Behaviour changes to confirm with the product owner
<Any test whose expected output legitimately changed, or any runtime behaviour
difference. "None." if none.>

### Not done
<Deprecations surfaced but deliberately left — hand off to `remediate-deprecations.md`.>

### Observations
### Decisions needed
```
