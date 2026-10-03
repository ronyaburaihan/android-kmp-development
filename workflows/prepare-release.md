# Workflow: Prepare Release

Standing contract: `README.md`. Prerequisite: `inspect-project.md`.

---

## 1. Objective

Verify an existing application is ready to ship, produce the release artifacts, and hand back a go/no-go with evidence.

**This workflow is a gate, not a build script.** Its value is finding the problem **before** the store does. A "no-go" with named blockers is a successful outcome.

**MUST NOT** publish, upload, submit, promote a track, or start a rollout. Those are the user's actions. This workflow prepares and verifies.

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| Platforms — Android, iOS, or both | yes | **MUST** ask. |
| Version and build number | yes | **MUST** ask, or derive from the project's single source. |
| What changed since the last release | yes | **MUST** ask. Determines which risk checks matter. |
| Target track / distribution | no | Default: Android internal → closed; iOS TestFlight internal. |
| Monetised app? | yes | Determines whether billing checks apply. |
| Release window / deadline | no | Affects whether a blocker is fixable in time. |
| Devices available for verification | no | Determines which checks become `NOT RUN`. |

**MUST** ask what changed. A release containing a schema migration, a `targetSdk` bump, or a billing change needs checks that a copy-change release does not.

---

## 3. Initial project inspection

Run `inspect-project.md`, then establish release readiness.

```bash
# --- working state ---
git status --porcelain
git log --oneline $(git describe --tags --abbrev=0)..HEAD 2>/dev/null | head -40

# --- versioning: one source of truth for both platforms ---
grep -rn 'versionName\|versionCode' --include='*.gradle.kts' --include='*.properties' . | grep -v build/
grep -rn 'CFBundleShortVersionString\|CFBundleVersion' --include='*.plist' --include='*.pbxproj' . 2>/dev/null

# --- Android release config ---
grep -rn 'targetSdk\|compileSdk' --include='*.gradle.kts' . | grep -v build/
grep -rn 'isMinifyEnabled\|isShrinkResources\|optimization\s*{' --include='*.gradle.kts' . | grep -v build/
grep -rn 'signingConfig\|storePassword\|keyAlias' --include='*.gradle.kts' . | grep -v build/
find . -name 'baseline-prof.txt' -not -path '*/build/*'
grep -rn 'billing' --include='*.toml' --include='*.gradle.kts' . | grep -v build/

# --- iOS release config ---
grep -rn 'CADisableMinimumFrameDurationOnPhone\|UsageDescription' --include='Info.plist' . 2>/dev/null
grep -rn 'swiftExport\|framework\|baseName' --include='*.gradle.kts' . | grep -v build/
```

**MUST** record the gate inputs:

| Fact | Why |
|---|---|
| Working tree clean? | A release built from a dirty tree is not reproducible |
| Version and build number unique vs the last release | A reused iOS build string is rejected on upload |
| `targetSdk` vs the Play floor | A publishing blocker |
| Billing Library version vs the publishing floor | A publishing blocker |
| R8 and resource shrinking on release | Size, startup, obfuscation |
| Baseline Profile present and current | ~30% first-launch gain; stale after flow changes |
| `CADisableMinimumFrameDurationOnPhone` present (CMP iOS) | **Crash without it** |
| Usage-description strings complete | **Crash without them** |
| Signing configured from CI secrets, not source | Credential exposure |

**MUST** verify the floors in `../references/version-matrix.md` against the live Play Console help — the reference set marks those dates `[UNVERIFIED]`.

---

## 4. Step-by-step procedure

### 4.1 Run the blocker checks first — MUST

These make the release unpublishable. **MUST** check before building anything.

| # | Blocker | Check |
|---|---|---|
| B1 | `targetSdk` below the Play floor | compare; verify the floor live |
| B2 | Billing Library below the publishing floor (monetised apps) | compare; verify live |
| B3 | iOS build string reused | compare with App Store Connect |
| B4 | `CADisableMinimumFrameDurationOnPhone` missing with CMP | grep `Info.plist` |
| B5 | A usage-description string missing for a capability in use | grep `Info.plist` vs capabilities |
| B6 | `debuggable=true` in the **merged release manifest** | inspect the merged file |
| B7 | Signing credentials in source control | grep + `git ls-files` |
| B8 | Working tree dirty | `git status --porcelain` |
| B9 | iOS certificates or provisioning profiles expiring inside the release window | check expiry |
| B10 | No restore-purchases path (monetised iOS) | App Review requires it |

**MUST** report any blocker immediately and stop. **MUST NOT** build artifacts for a release with a known blocker.

### 4.2 Confirm the release plan — MUST

Present and wait:

- the blocker check results
- version and build number for both platforms, from one source
- what changed, and which risk checks that triggers
- the verification matrix: which devices, which checks will be `NOT RUN`
- the rollout recommendation with halt criteria

### 4.3 Verify versioning is single-sourced

**MUST** confirm both platforms derive from one source.

```
appVersion = 3.4.1   buildNumber = 412
   ├── Android: versionName 3.4.1   versionCode 412
   └── iOS:     CFBundleShortVersionString 3.4.1   CFBundleVersion 412
```

```
# WRONG — independently maintained
Android 3.4.1 / 412      iOS 3.4.0 / 87
```

The wrong form makes a shared-code crash report impossible to correlate across platforms and makes a support ticket citing "3.4.1" ambiguous.

### 4.4 Android: build and verify the artifact

```bash
./gradlew --stop
./gradlew clean
./gradlew testDebugUnitTest
./gradlew :<shared>:allTests                  # KMP: every target
./gradlew lintRelease
./gradlew bundleRelease
```

**MUST** then verify the artifact, not the source:

```bash
# merged release manifest — library merging can reintroduce problems
cat app/build/intermediates/merged_manifests/release/AndroidManifest.xml

# mapping file must exist and be archived
ls -la app/build/outputs/mapping/release/mapping.txt

# install the real split configuration on a device
java -jar bundletool-all.jar build-apks --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=app.apks --ks=$KEYSTORE --ks-pass=pass:$KS_PASS --ks-key-alias=$ALIAS --key-pass=pass:$KEY_PASS
java -jar bundletool-all.jar install-apks --apks=app.apks
```

**MUST** run the **instrumented suite against the minified variant**. Most R8 breakage — reflection, serialization, DI — appears only there.

**MUST** regenerate the Baseline Profile if critical flows changed, from a **non-minified** benchmark variant. A profile generated from an obfuscated build silently fails to match and the gain is lost with no error.

### 4.5 iOS: build and verify the archive

```bash
./gradlew :<shared>:allTests
./gradlew :<shared>:iosSimulatorArm64Test
./gradlew :<shared>:linkReleaseFrameworkIosArm64
xcodebuild -scheme <scheme> -configuration Release archive -archivePath build/App.xcarchive
xcrun altool --validate-app -f build/App.ipa -t ios --apiKey "$KEY_ID" --apiIssuer "$ISSUER_ID"
```

**MUST** run `--validate-app` before any upload. It catches most rejections before the processing wait.

**MUST** verify on a **physical device** via TestFlight. The simulator does not exercise the device framework slice, real memory pressure, or ATS against production hosts.

### 4.6 Verify crash symbolication — MUST

**MUST** confirm empirically, on both platforms, that a crash report is readable, **including a frame from shared Kotlin code**.

| Platform | Input |
|---|---|
| Android | `mapping.txt` uploaded to the crash reporter |
| iOS | dSYMs uploaded, including the Kotlin framework's |

**MUST** trigger a deliberate non-fatal from a release-configured build and read the resulting report. The reference set marks the Kotlin/Native symbolication procedure `[UNVERIFIED]` — this check is where it gets verified, and the answer is needed before the next production incident, not after.

### 4.7 Run the change-triggered risk checks

**MUST** apply the checks the change set demands:

| If the release contains | MUST also verify |
|---|---|
| A database schema migration | Upgrade-in-place from the **previous released build**, with real data. See `add-persistence.md`. |
| A `targetSdk` bump | The full behaviour-change matrix. See `upgrade-target-sdk.md`. |
| A dependency or toolchain upgrade | Release build with R8, plus the primary flow manually. See `upgrade-dependencies.md`. |
| Billing or subscription changes | Purchase, restore, acknowledgement; license testers / sandbox accounts. See `../references/integrations/subscriptions.md`. |
| Auth or secure-storage changes | Existing users' sessions survive, or are deliberately reset; key-invalidation path degrades to re-auth. |
| Shared-module changes (KMP) | Both platforms; exported surface unchanged or coordinated. See `review-ios-interop.md`. |
| Push or messaging changes | End-to-end delivery on both platforms manually — the iOS failure mode is silence, not an error. |
| New permissions | The grant and denial paths, and the store disclosure. |

### 4.8 Produce the go/no-go

**MUST** give an explicit verdict with evidence, and a rollout recommendation with **predefined halt criteria**. A staged rollout with no threshold is not a safety mechanism.

**MUST NOT** upload, submit, promote, or start a rollout.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | **Any blocker (B1–B10) present** | (a) fix and re-run the gate; (b) no-go | **MUST** stop. **MUST NOT** build release artifacts for a blocked release. |
| D2 | Working tree **dirty** | (a) commit or stash first; (b) abort | **MUST** stop. A release from a dirty tree is not reproducible. |
| D3 | A test **fails** | (a) fix; (b) no-go | **MUST** stop. **MUST NOT** skip or `@Ignore` a test to produce a release. |
| D4 | Release contains a **schema migration** | (a) upgrade-in-place verification on a device with real data; (b) proceed unverified | **MUST** choose (a). On-device migration is not reversible. |
| D5 | Baseline Profile is **stale** (critical flows changed) | (a) regenerate from a non-minified benchmark variant; (b) ship stale | **SHOULD** choose (a). (b) silently loses the startup gain. |
| D6 | **Symbolication unverified** | (a) verify now; (b) ship and accept blind crash reports | **MUST** ask. Recommend (a) — the cost of (b) is paid during the next incident. |
| D7 | No physical device for verification | (a) obtain one; (b) proceed with checks marked `NOT RUN` | **MUST** ask. (b) downgrades the verdict to a conditional go. |
| D8 | Instrumented suite **not run against the minified variant** | (a) run it; (b) accept the R8 risk | **MUST** recommend (a). This is where R8 breakage appears. |
| D9 | iOS certificates or profiles **expire** inside the window | — | **MUST** stop. Renewal is a prerequisite. |
| D10 | Monetised app, **restore path unverified** | (a) verify on a clean install; (b) risk App Review rejection | **MUST** recommend (a). App Review inspects it. |
| D11 | The two platforms **cannot** ship together (App Review timing) | (a) feature-flag and ship independently; (b) hold Android for iOS | **MUST** present (a). Coordinated same-day release is not reliable with human App Review. |
| D12 | A late change is requested **after** the gate passed | (a) re-run the full gate; (b) ship the verified artifact | **MUST** require (a). A verified artifact plus an unverified change is an unverified release. |

---

## 6. Implementation rules

**MUST:**

1. Run blocker checks (B1–B10) before building anything.
2. Verify Play and Billing floors against the live source, not the reference set's `[UNVERIFIED]` dates.
3. Build from a clean working tree.
4. Derive both platforms' versions from one source.
5. Verify the **merged release manifest** and the **built artifact**, not source files.
6. Run the instrumented suite against the **minified** variant.
7. Install via `bundletool` on a physical device and smoke-test the critical flows.
8. Archive the mapping file and the dSYMs, and verify symbolication empirically.
9. Run `--validate-app` before any iOS upload.
10. Apply every change-triggered check from 4.7.
11. Give an explicit go/no-go with evidence and halt criteria.

**MUST NOT:**

12. Publish, upload, submit, promote a track, or start a rollout.
13. Build release artifacts for a release with a known blocker.
14. Skip, `@Ignore`, or delete a test to produce a release.
15. Disable R8, resource shrinking, or lint to get a green build.
16. Add a lint-baseline entry to pass `lintRelease`.
17. Fix application defects inside this workflow — report them and hand off to the right workflow.
18. Change version numbers, signing configuration, or build types beyond what the release requires.
19. Commit a keystore, `.p8` key, or store credential.
20. Report a check as `PASS` without running it.
21. Declare readiness on compilation and unit tests alone.

---

## 7. Validation requirements

### Blockers — any failure is a no-go

| # | Check | How |
|---|---|---|
| B1 | `targetSdk` at or above the Play floor | compare; floor verified live |
| B2 | Billing Library at or above the publishing floor | compare; floor verified live |
| B3 | iOS build string unique | App Store Connect |
| B4 | `CADisableMinimumFrameDurationOnPhone` present (CMP iOS) | grep `Info.plist` |
| B5 | Usage-description strings complete | grep vs capabilities used |
| B6 | No `debuggable=true` in the merged release manifest | inspect |
| B7 | No signing credential in source control | grep + `git ls-files` |
| B8 | Working tree clean | `git status --porcelain` |
| B9 | iOS certificates/profiles valid past the window | check expiry |
| B10 | Restore-purchases path present (monetised iOS) | manual |

### Android

| # | Check | Required |
|---|---|---|
| V1 | Unit tests pass | MUST |
| V2 | KMP: `allTests` across every target | MUST if KMP |
| V3 | `lintRelease` clean, with no new baseline entries | MUST |
| V4 | `bundleRelease` succeeds with R8 and resource shrinking | MUST |
| V5 | **Instrumented suite against the minified variant** | MUST |
| V6 | Merged release manifest correct — exported explicit, no cleartext, not debuggable | MUST |
| V7 | `mapping.txt` exists and is archived | MUST |
| V8 | `bundletool` install on a **physical device**; critical flows smoke-tested | MUST |
| V9 | Baseline Profile present and current | MUST if flows changed |
| V10 | **Symbolication verified**, including a shared-Kotlin frame | MUST |
| V11 | AAB size vs the previous release | SHOULD |
| V12 | Startup and frame metrics within threshold | SHOULD |

### iOS

| # | Check | Required |
|---|---|---|
| V13 | `iosSimulatorArm64Test` passes | MUST |
| V14 | Release framework links | MUST |
| V15 | Swift smoke test over the exported facade passes | MUST |
| V16 | Archive succeeds | MUST |
| V17 | `altool --validate-app` passes | MUST |
| V18 | TestFlight internal build installed on a **physical device**; critical flows smoke-tested | MUST |
| V19 | Restore-purchases verified on a **clean install** | MUST if monetised |
| V20 | dSYMs archived; **symbolication verified** including a shared-Kotlin frame | MUST |

### Change-triggered

| # | Check | Required when |
|---|---|---|
| V21 | **Upgrade-in-place from the previous release with real data** | a schema migration is included |
| V22 | `targetSdk` behaviour matrix verified | a `targetSdk` bump is included |
| V23 | Purchase, restore and acknowledgement verified | billing changes are included |
| V24 | Existing sessions survive, or reset deliberately; key invalidation degrades to re-auth | auth/secure-storage changes |
| V25 | Push delivery end-to-end on both platforms | messaging changes |
| V26 | Exported iOS surface unchanged, or coordinated | shared-module changes |

---

## 8. Failure handling

| Failure | Response |
|---|---|
| A blocker is present | → D1. **MUST** report as a no-go with the specific blocker. **MUST NOT** build artifacts. |
| Working tree dirty | → D2. **MUST** stop. |
| A test fails | → D3. **MUST NOT** skip it. Hand off to `diagnose-and-fix-bug.md`. |
| `lintRelease` fails | **MUST** fix or report. **MUST NOT** add a baseline entry. |
| `bundleRelease` fails while debug passes | R8. **MUST** fix the keep rule; **MUST NOT** disable minification. |
| Instrumented suite fails only on the minified variant | A real R8 defect that would have shipped. **MUST** fix the keep rule and re-run. |
| `bundletool` install fails | Split configuration problem. **MUST** resolve — sideloaded builds missing splits fail on Android 10+. |
| iOS archive succeeds, upload validation fails | **MUST** read the validation output. Common causes: reused build string, missing `Info.plist` entry, invalid entitlement. |
| iOS app crashes immediately on a TestFlight install | Check B4 and B5 **first** — both are crash-level with no compile signal. |
| Symbolication produces unreadable frames | **MUST** report as a release risk. Shipping blind to shared-code crashes is a decision the user must make (D6). |
| Migration verification loses data | **MUST** stop immediately. No-go. Hand back to `add-persistence.md`. |
| No physical device available | → D7. Mark V8, V18 `NOT RUN`; verdict becomes a conditional go at most. |
| Store rejects after submission | Outside this workflow's gate. **MUST** capture the rejection reason verbatim and add the missed check to the gate. |
| A late change arrives after the gate passed | → D12. **MUST** re-run the gate. |

---

## 9. Completion criteria

**MUST** all hold:

1. Blockers B1–B10 all checked; none present, or an explicit no-go issued.
2. Working tree clean; artifacts built from a known commit.
3. Versions single-sourced and consistent across platforms.
4. Android: V1–V8, V10 `PASS`; V9 `PASS` if flows changed.
5. iOS: V13–V18, V20 `PASS`; V19 `PASS` if monetised.
6. Every change-triggered check (V21–V26) applicable to this release `PASS`.
7. Symbolication verified empirically on each platform shipped, including a shared-Kotlin frame.
8. No test skipped; no lint baseline added; no optimization disabled.
9. Mapping file and dSYMs archived.
10. Explicit go/no-go with evidence and halt criteria.
11. Nothing published, uploaded, submitted, promoted, or rolled out.
12. Every `NOT RUN` check listed with its reason and its risk.

---

## 10. Final report format

Base skeleton from `README.md`, with these additions.

```markdown
## Prepare Release — <version> (<build>) — <Android | iOS | both>

### Verdict
**<GO | CONDITIONAL GO | NO-GO>** — one sentence.

<For NO-GO: the blockers, listed first.>
<For CONDITIONAL GO: exactly which checks are NOT RUN and the risk each carries.>

### Blocker checks
| # | Blocker | Result |
|---|---|---|
| B1 | targetSdk at Play floor | PASS — <n> vs floor <n>, verified at <URL> |
| B2 | Billing at publishing floor | PASS / **FAIL** / n/a |
| B3 | iOS build string unique | |
| B4 | `CADisableMinimumFrameDurationOnPhone` | |
| B5 | Usage-description strings | |
| B6 | No debuggable in merged manifest | |
| B7 | No credentials in source control | |
| B8 | Working tree clean | |
| B9 | Certificates valid past the window | |
| B10 | Restore path present | |

### Release identity
| | Android | iOS |
|---|---|---|
| Version | | |
| Build | | |
| Single-sourced from | | |
| Commit | | |
| Artifact | `path` | `path` |

### What changed
<Summary, and the risk checks it triggered.>

### Android validation
| # | Check | Command | Result |
|---|---|---|---|
| V1 | Unit tests | | |
| V2 | KMP allTests | | |
| V3 | lintRelease | | |
| V4 | bundleRelease with R8 | | |
| V5 | **Instrumented on minified variant** | | |
| V6 | Merged release manifest | | |
| V7 | mapping.txt archived | | |
| V8 | bundletool install + smoke on device | | |
| V9 | Baseline Profile current | | |
| V10 | **Symbolication verified** | | |
| V11 | AAB size vs previous | | |
| V12 | Startup / frame metrics | | |

### iOS validation
| # | Check | Command | Result |
|---|---|---|---|
| V13 | iosSimulatorArm64Test | | |
| V14 | Release framework links | | |
| V15 | Swift smoke test | | |
| V16 | Archive | | |
| V17 | altool --validate-app | | |
| V18 | TestFlight on physical device | | |
| V19 | Restore on clean install | | |
| V20 | dSYMs + **symbolication verified** | | |

### Change-triggered validation
| # | Check | Required because | Result |
|---|---|---|---|
| V21 | Upgrade-in-place with real data | schema migration | |
| V22 | targetSdk behaviour matrix | targetSdk bump | |
| V23 | Purchase / restore / acknowledgement | billing change | |
| V24 | Sessions + key invalidation | auth change | |
| V25 | Push end-to-end both platforms | messaging change | |
| V26 | Exported iOS surface | shared-module change | |

### Devices verified
| Device | OS / API | Platform | Flows exercised | Result |
|---|---|---|---|---|

### NOT RUN — risk accepted
| Check | Why not run | Risk if wrong |
|---|---|---|

### Rollout recommendation
- Android: <internal → closed → production, staged from n%>
- iOS: <TestFlight internal → external → App Review → phased release>
- **Halt criteria:** <crash-free rate threshold, ANR threshold, specific metric>
- Watch specifically: <the changes most likely to regress>
- Platforms cannot ship same-day if App Review timing matters → <feature flag plan>

### Artifacts to retain
| Artifact | Location |
|---|---|
| AAB | |
| mapping.txt | |
| dSYMs | |
| IPA / archive | |
| Baseline Profile | |

### Blockers and defects found — handed off
| Issue | Severity | Suggested workflow |
|---|---|---|

### Not done
### Observations
### Decisions needed
```
