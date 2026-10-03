# Workflow: Upgrade targetSdk

Standing contract: `README.md`. Prerequisite: `inspect-project.md`.

---

## 1. Objective

Raise `targetSdk` (and `compileSdk` if required) and handle every behaviour change the new level enforces, so the app remains correct and publishable.

Raising `targetSdk` is a **declaration that the app handles the new platform behaviour**. The version number is one line; the work is the behaviour changes. See `../references/android/platform-requirements.md`.

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| Target API level | no | Default: the Play floor in `../references/version-matrix.md`. **MUST** verify the floor and its deadline against Play Console help. |
| Form factors shipped | yes | **MUST** ask. Wear OS, Automotive, TV and XR have different floors. |
| Minimum release window | no | Affects whether staged rollout is feasible. |
| Device coverage available for testing | no | Default: assume one phone emulator. Determines which checks become `NOT RUN`. |

---

## 3. Initial project inspection

Run `inspect-project.md`, then audit the codebase against **every** behaviour change for the levels being crossed. Crossing 34 → 36 means auditing 35 **and** 36.

```bash
grep -rn 'compileSdk\|targetSdk\|minSdk' --include='*.gradle.kts' . | grep -v build/

# edge-to-edge opt-out
grep -rn 'windowOptOutEdgeToEdgeEnforcement\|enableEdgeToEdge\|WindowCompat' --include='*.kt' --include='*.xml' . | grep -v build/

# back handling
grep -rn 'onBackPressed\|KEYCODE_BACK\|enableOnBackInvokedCallback\|OnBackPressedCallback\|BackHandler' --include='*.kt' --include='*.xml' . | grep -v build/

# orientation / resizability locks
grep -rn 'screenOrientation\|resizableActivity\|minAspectRatio\|maxAspectRatio\|setRequestedOrientation' --include='*.xml' --include='*.kt' . | grep -v build/

# health permissions
grep -rn 'BODY_SENSORS\|TYPE_HEART_RATE\|HEART_RATE_BPM\|FOREGROUND_SERVICE_TYPE_HEALTH' --include='*.kt' --include='*.xml' . | grep -v build/

# misc API 36 items
grep -rn 'elegantTextHeight' --include='*.xml' --include='*.kt' . | grep -v build/
grep -rn 'MediaStore.getVersion\|getVersion(' --include='*.kt' . | grep -v build/
grep -rn 'scheduleAtFixedRate' --include='*.kt' . | grep -v build/
grep -rn 'NsdManager\|MulticastSocket\|SSDP\|mdns' --include='*.kt' . | grep -v build/
grep -rn 'BluetoothDevice\|createBond\|ACTION_BOND_STATE_CHANGED' --include='*.kt' . | grep -v build/
grep -rn 'intentMatchingFlags\|android:exported' --include='*.xml' . | grep -v build/
```

**MUST** produce an **impact table** before changing the version: every behaviour change for the levels crossed, whether it applies, and the files affected. A change that does not apply **MUST** be recorded as not applicable, not omitted.

**MUST** verify the current AGP supports the intended `compileSdk`. AGP 9.4 supports up to API 37.

**MUST** establish a green baseline build and test run before changing the version.

---

## 4. Step-by-step procedure

### 4.1 Build the impact table — MUST, before any edit

For API 36, at minimum (details in `../references/android/platform-requirements.md`):

| # | Behaviour change | Enforcement |
|---|---|---|
| 1 | Edge-to-edge mandatory; opt-out attribute deprecated and disabled | **Mandatory** |
| 2 | Predictive back on by default; `onBackPressed()` not called, `KEYCODE_BACK` not dispatched | **Mandatory** |
| 3 | Large screens (sw ≥ 600dp) ignore orientation / resizability / aspect-ratio restrictions | **Mandatory** |
| 4 | `scheduleAtFixedRate` runs at most one missed task | Behaviour |
| 5 | `elegantTextHeight` ignored | Behaviour |
| 6 | `BODY_SENSORS*` replaced by granular health permissions | **Mandatory if used** |
| 7 | `MediaStore.getVersion()` per-app and opaque | Behaviour |
| 8 | Safer intents | Opt-in |
| 9 | Local network permission | Opt-in phase |
| 10 | Bluetooth bond-loss / encryption-change intents | Behaviour |
| 11 | App-owned photos pre-selected in the picker | Behaviour |
| 12 | GPU syscall filtering (Pixel 6–9 Mali) | No action for ordinary apps |

### 4.2 Confirm the plan — MUST

Present and wait:

- the impact table with affected files
- `compileSdk` / `targetSdk` from → to, and whether AGP supports it
- the work items, with an estimate of UI changes required (edge-to-edge and large-screen items are usually the largest)
- which temporary opt-outs, if any, are proposed (→ D3)
- the test matrix: which devices and form factors

### 4.3 Raise `compileSdk` first, keep `targetSdk` — MUST

Split the change:

```kotlin
// step 1 — compile against the new SDK, keep current runtime behaviour
compileSdk = 36
defaultConfig { targetSdk = 35 }
```

Compile and test. This surfaces removed and deprecated APIs **without** activating the behaviour changes, so compile breakage and behaviour breakage are attributable separately.

**MUST NOT** raise both in one step. A failure then has two possible causes.

### 4.4 Fix compile-level issues

Fix only what `compileSdk` broke: removed APIs, changed signatures.

**MUST NOT** fix unrelated deprecations surfaced by the newer SDK — that is `remediate-deprecations.md`.

### 4.5 Raise `targetSdk`

```kotlin
defaultConfig { targetSdk = 36 }
```

Behaviour changes are now live.

### 4.6 Work the impact table, highest-impact first

**Edge-to-edge (mandatory)**

```kotlin
// CORRECT — content respects system bars; the surface may still draw behind them
Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
    Column(
        Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
    ) { /* ... */ }
}
```

```xml
<!-- WRONG — ignored at API 36; ships with content under the status bar -->
<item name="android:windowOptOutEdgeToEdgeEnforcement">true</item>
```

**MUST** audit **every** screen, not only the main one. Bottom bars, FABs, dialogs, bottom sheets and full-bleed media are the usual failures.

**Predictive back (mandatory)**

```kotlin
// CORRECT
BackHandler(enabled = isDrawerOpen) { closeDrawer() }
```

```kotlin
// WRONG — dead code at API 36
override fun onBackPressed() { if (isDrawerOpen) closeDrawer() else super.onBackPressed() }
```

**MUST** verify every custom back behaviour: drawers, bottom sheets, multi-step forms, WebView history, unsaved-changes prompts.

**Large screens (mandatory)**

**MUST** verify each screen at ≥600dp in both orientations. Orientation locks no longer apply there.

**Health permissions (mandatory if used)**

```xml
<!-- WRONG at API 36 -->
<uses-permission android:name="android.permission.BODY_SENSORS" />
<!-- CORRECT -->
<uses-permission android:name="android.permission.READ_HEART_RATE" />
```

**MUST** also add the privacy-policy activity for a mobile app using health data.

### 4.7 Validate

Run section 7. Behaviour changes are not detectable by compilation — the device matrix is the validation.

### 4.8 Plan the rollout

**MUST** recommend a staged rollout with predefined halt criteria. A `targetSdk` change alters behaviour for every user simultaneously. See `prepare-release.md`.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | Edge-to-edge requires **substantial UI rework** | (a) do it now, in scope; (b) split per screen across releases while staying on the current `targetSdk` | **MUST** ask with the screen count. **MUST NOT** ship partial inset handling. |
| D2 | App **depends on an orientation lock** on large screens | (a) adaptive layout; (b) `android:appCategory` game exemption if genuinely a game | **MUST** ask. **MUST NOT** rely on the temporary resizability opt-out as the plan. |
| D3 | A **temporary opt-out** is proposed (`enableOnBackInvokedCallback="false"`, `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`) | (a) do the real work now; (b) opt out with a tracked follow-up | **MUST** ask. **MUST** state that the resizability opt-out **stops working at API 37**, so (b) buys one cycle only. |
| D4 | AGP does **not** support the target `compileSdk` | (a) upgrade AGP first via `upgrade-dependencies.md`; (b) hold | **MUST** stop. Separate task. |
| D5 | A **third-party SDK** is incompatible with the new level | (a) upgrade it; (b) hold the SDK upgrade; (c) replace it | **MUST** stop and report the specific SDK and its compatible version. |
| D6 | App uses **health sensors** | — | **MUST** confirm the full permission migration plus the privacy-policy requirement before proceeding. |
| D7 | App does **device discovery** on the local network | (a) declare `NEARBY_WIFI_DEVICES` and test with the compat flag now; (b) defer | **SHOULD** choose (a) — enforcement is coming and the failure mode is silent. |
| D8 | No ≥600dp device or emulator available | (a) provision one; (b) proceed and mark the large-screen checks `NOT RUN` | **MUST** ask. (b) downgrades the Outcome to `DONE WITH CAVEATS`. |
| D9 | Baseline build or tests were **already failing** | (a) fix first; (b) abort | **MUST** stop. |
| D10 | Form factors with **different floors** (Wear, TV, Automotive, XR) ship from this project | (a) per-form-factor `targetSdk`; (b) raise all to the highest | **MUST** ask. **MUST NOT** apply the phone floor to a Wear or TV module blindly. |

---

## 6. Implementation rules

**MUST:**

1. Raise `compileSdk` and `targetSdk` in **separate, separately verified** steps.
2. Produce the impact table before editing, covering every level crossed.
3. Handle all three mandatory API 36 items — edge-to-edge, predictive back, large-screen adaptivity — or stop at D1/D2.
4. Audit every screen for insets and back handling, not a sample.
5. Use `BackHandler` / `OnBackPressedDispatcher` for back; delete dead `onBackPressed` overrides.
6. Migrate health permissions completely, including the privacy-policy activity.
7. Verify the **merged** release manifest, not the source manifest.
8. Record every temporary opt-out with its expiry (API 37 for resizability).

**MUST NOT:**

9. Change `minSdk` — it is a separate product decision about device support.
10. Upgrade Kotlin, AGP, Gradle, or libraries as part of this change (→ `upgrade-dependencies.md`), except an AGP bump explicitly approved at D4.
11. Add `android:windowOptOutEdgeToEdgeEnforcement` — it is deprecated and disabled.
12. Add `enableOnBackInvokedCallback="false"` without approval at D3.
13. Ship the resizability opt-out as the permanent answer.
14. Fix unrelated deprecations surfaced by the new SDK.
15. Suppress a new lint error with a baseline entry to make the build pass.
16. Declare the upgrade done on compilation success. Every mandatory item is a runtime behaviour.

---

## 7. Validation requirements

Compilation proves almost nothing here. The device matrix is the validation.

| # | Check | How | Required |
|---|---|---|---|
| V1 | `compileSdk` step compiles | `./gradlew assembleDebug` | MUST |
| V2 | `targetSdk` step compiles | `./gradlew assembleDebug` | MUST |
| V3 | Unit tests pass | module test tasks | MUST |
| V4 | Instrumented tests pass **on an API 36 device/emulator** | `connectedDebugAndroidTest` | MUST |
| V5 | **Insets correct on every screen** — phone, API 36 | manual sweep | MUST |
| V6 | **Back navigation correct on every custom back surface** | manual | MUST |
| V7 | **≥600dp device, both orientations, every screen** | manual | MUST (or `NOT RUN` per D8) |
| V8 | Health permission flow grants and reads | manual | MUST if applicable |
| V9 | Release build with R8 | `./gradlew bundleRelease` | MUST |
| V10 | Merged release manifest correct | inspect `build/intermediates/merged_manifests/release/AndroidManifest.xml` | MUST |
| V11 | Lint clean on release variant | `./gradlew lintRelease` | MUST |
| V12 | Locale sweep incl. one RTL and one `elegantTextHeight`-affected script | manual | SHOULD |
| V13 | Safer intents: no blocked intents in Logcat | enable `enforceIntentFilter` in debug, watch Logcat | SHOULD |
| V14 | Local network discovery with the compat flag enabled | `adb shell am compat enable RESTRICT_LOCAL_NETWORK <pkg>` | SHOULD if applicable |
| V15 | Foldable fold/unfold and multi-window | manual | SHOULD |
| V16 | No new lint-baseline entries | review the diff | MUST |

---

## 8. Failure handling

| Failure | Response |
|---|---|
| `compileSdk` raise breaks compilation | Expected. Fix removed APIs only. **MUST NOT** also fix deprecations. |
| AGP rejects the `compileSdk` | → D4. **MUST NOT** force it with an experimental flag. |
| Content under the status/navigation bar | Inset handling missing on that screen. **MUST** fix with insets; **MUST NOT** add a fixed padding value. |
| Back gesture does nothing, or exits the app unexpectedly | A dead `onBackPressed` override. **MUST** migrate to `BackHandler`/`OnBackPressedDispatcher`. |
| Layout broken on a tablet or unfolded foldable | The orientation lock was load-bearing → D2. **MUST NOT** reach for the resizability opt-out as the fix. |
| Third-party SDK crashes at the new level | → D5. **MUST NOT** lower `targetSdk` back without reporting; that reintroduces the publishing block. |
| Health sensor reads return nothing | Permission migration incomplete. **MUST** complete it, including the privacy-policy activity. |
| Instrumented tests pass on API 33 but fail on API 36 | That is the point of V4. **MUST** fix the behaviour, not the test. |
| Release build fails while debug passes | R8. **MUST** fix the keep rule; **MUST NOT** disable minification. |
| A new lint error appears | **MUST** fix it, or report it. **MUST NOT** baseline it to go green. |
| No ≥600dp hardware or emulator | → D8. Mark V7 `NOT RUN`, downgrade the Outcome. **MUST NOT** claim large-screen verification. |
| Behaviour change affects a flow nobody can test (payment, deep link from a partner) | **MUST** report it explicitly as unverified risk in the rollout recommendation. |

---

## 9. Completion criteria

**MUST** all hold:

1. Impact table complete for every level crossed; each item marked applicable or not.
2. `compileSdk` and `targetSdk` raised in separate verified steps.
3. All three mandatory API 36 items handled across **all** screens, or an approved D1/D2/D3 decision recorded.
4. Health permissions fully migrated, if applicable.
5. V1–V4, V9–V11, V16 `PASS`.
6. V5 and V6 `PASS` — these are mandatory behaviour changes; a `NOT RUN` here means the upgrade is not done.
7. V7 `PASS`, or `NOT RUN` per D8 with the Outcome downgraded to `DONE WITH CAVEATS`.
8. Every temporary opt-out recorded with its expiry and a follow-up item.
9. `minSdk` unchanged; no dependency versions changed outside an approved D4.
10. No lint-baseline entry or suppression added.
11. Rollout recommendation included, with halt criteria.

---

## 10. Final report format

Base skeleton from `README.md`, with these additions.

```markdown
## Upgrade targetSdk — <from> → <to>

### Outcome
<DONE | DONE WITH CAVEATS | BLOCKED | NEEDS DECISION> — one sentence.

### Inspection findings
- compileSdk / targetSdk / minSdk before: <...>
- AGP: <version> — supports compileSdk up to <n>
- Form factors in this project: <phone | + Wear | + TV | ...>
- Baseline build + tests before changes: <PASS | FAIL (D9)>

### Impact table
| # | Behaviour change | Applies | Files affected | Status |
|---|---|---|---|---|
| 1 | Edge-to-edge mandatory | yes | <n> screens | DONE |
| 2 | Predictive back | yes | `path:line` | DONE |
| 3 | Large-screen adaptivity | yes | `path` | DONE / DEFERRED (D2) |
| 4 | `scheduleAtFixedRate` | no | — | n/a |
| ... | | | | |

### Version changes
| Setting | From | To |
|---|---|---|
| compileSdk | | |
| targetSdk | | |
| minSdk | | unchanged |

### Temporary opt-outs in place
| Opt-out | Scope | Expires | Follow-up |
|---|---|---|---|
| e.g. `enableOnBackInvokedCallback="false"` | `path` | — | <ticket/item> |

<Write "None." if there are none.>

### Changes made
| File | Change |
|---|---|

### Validation performed
| # | Check | How | Result |
|---|---|---|---|
| V1 | compileSdk compiles | | |
| V2 | targetSdk compiles | | |
| V3 | Unit tests | | |
| V4 | Instrumented on API 36 | | |
| V5 | **Insets — every screen** | | |
| V6 | **Back — every custom surface** | | |
| V7 | **≥600dp, both orientations** | | |
| V8 | Health permissions | | |
| V9 | Release build with R8 | | |
| V10 | Merged release manifest | | |
| V11 | lintRelease | | |
| V12 | Locale / RTL sweep | | |
| V13 | Safer intents in Logcat | | |
| V14 | Local network with compat flag | | |
| V15 | Foldable / multi-window | | |
| V16 | No lint-baseline entries added | | |

### Device matrix tested
| Device / emulator | API | Form factor | Result |
|---|---|---|---|

### Unverified risk
<Behaviour changes affecting flows that could not be tested. Each with the
user-visible consequence if it is wrong. "None." if none.>

### Rollout recommendation
- Staged rollout: <yes — start at n%>
- Halt criteria: <crash-free rate threshold, ANR threshold>
- Watch specifically: <the behaviour changes most likely to regress>

### Not done
### Observations
### Decisions needed
```
