# Android Platform Requirements

**Scope:** `targetSdk` obligations, Android 16 (API 36) behaviour changes that force code changes, manifest requirements, build-config requirements that are policy rather than preference.
**Applies to:** Android app and library modules.
**Official sources:**
- <https://developer.android.com/about/versions/16/behavior-changes-16>
- <https://developer.android.com/privacy-and-security/security-best-practices>

**Rule levels:** see `../README.md`. Behaviour-change details and the Play deadline dates are [OFFICIAL] — see `../version-matrix.md`.

---

## targetSdk obligation

| Form factor | Minimum `targetSdk` |
|---|---|
| Phone / tablet / foldable | **36** (Android 16) |
| Wear OS, Android Automotive OS | **35** |
| Android TV, Android XR | **34** |

- **MUST** target API 36 to publish a new app or an app update from **2026-08-31**; extension available to **2026-11-01**. [OFFICIAL — <https://support.google.com/googleplay/android-developer/answer/11926878>, verified 2026-10-03]
- An existing app **MUST** target at least API 35 by the same date; otherwise it is available only on devices running an OS at or below its target API level — effectively invisible to new users on newer devices. [OFFICIAL]
- AGP 9.4 supports up to API 37. **MUST** check that the AGP version supports the chosen `compileSdk`.

**MUST** set `compileSdk` ≥ `targetSdk`. **SHOULD** set `compileSdk` to the latest stable API level.

---

## Android 16 behaviour changes that require code changes

These apply **when the app targets API 36**. Each one is a real change, not a warning.

### 1. Edge-to-edge is mandatory — MUST handle insets

`R.attr#windowOptOutEdgeToEdgeEnforcement` is **deprecated and disabled**. There is no opt-out.

**MUST** consume window insets explicitly. In Compose:

```kotlin
// CORRECT — content respects system bars
Scaffold(
    modifier = Modifier.fillMaxSize(),
) { innerPadding ->
    Column(
        modifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
    ) { /* ... */ }
}

// CORRECT — a surface that should draw behind the bars but keep content clear
Box(
    Modifier
        .fillMaxSize()
        .background(brush)             // draws edge to edge
) {
    Content(Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
}
```

```xml
<!-- WRONG — no longer has any effect -->
<style name="AppTheme">
    <item name="android:windowOptOutEdgeToEdgeEnforcement">true</item>
</style>
```

Why the wrong form is a problem: the attribute is ignored, so the app ships with content drawn under the status bar and the navigation bar — unreadable text and untappable controls at the screen edges.

Official inset guidance: <https://developer.android.com/develop/ui/compose/layouts/insets>

### 2. Predictive back is on by default — MUST migrate back handling

Back-to-home, cross-task and cross-activity animations are enabled by default. Consequences:

- **`onBackPressed()` is no longer called.**
- **`KeyEvent.KEYCODE_BACK` is not dispatched.**

```kotlin
// CORRECT — Compose
BackHandler(enabled = isDrawerOpen) { closeDrawer() }

// CORRECT — Views / Activity
onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() { closeDrawer() }
})
```

```kotlin
// WRONG — dead code when targeting API 36
override fun onBackPressed() {
    if (isDrawerOpen) closeDrawer() else super.onBackPressed()
}
```

A temporary escape hatch exists and **SHOULD NOT** be used in new code:

```xml
<activity android:enableOnBackInvokedCallback="false" />
```

### 3. Large screens ignore orientation and resizability restrictions — MUST build adaptive layouts

On displays with smallest width **≥ 600dp**, these are **ignored**:

Manifest attributes: `android:screenOrientation`, `android:resizableActivity`, `android:minAspectRatio`, `android:maxAspectRatio`.
Runtime APIs: `setRequestedOrientation()`, `getRequestedOrientation()`.
All orientation values: `portrait`, `reversePortrait`, `sensorPortrait`, `userPortrait`, `landscape`, `reverseLandscape`, `sensorLandscape`, `userLandscape`.

**MUST** design layouts that work in any orientation and any window size at ≥600dp. Derive adaptive state from `currentWindowAdaptiveInfo()` and use the canonical layouts.

Exceptions: games (declared via `android:appCategory`), a user opt-in setting, and screens below 600dp.

A temporary activity- or application-level opt-out exists:

```xml
<property
    android:name="android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY"
    android:value="true" />
```

**MUST NOT** rely on it: it will not apply when targeting API 37 or later.

### 4. `scheduleAtFixedRate` runs at most one missed task

`ScheduledExecutorService.scheduleAtFixedRate()` now executes at most **one** missed task after a delay, where it previously executed all of them.

**SHOULD** audit any code that depends on catch-up executions (counters, batch flushes). Test with the app-compatibility framework flag `STPE_SKIP_MULTIPLE_MISSED_PERIODIC_TASKS`.

### 5. `elegantTextHeight` is ignored

The attribute no longer has an effect. Text rendering changes for Arabic, Lao, Myanmar, Tamil, Gujarati, Kannada, Malayalam, Odia, Telugu and Thai.

**MUST** verify layouts in those locales do not clip or overlap.

### 6. Health permissions are granular — MUST migrate

```xml
<!-- WRONG — no longer grants heart-rate access at API 36 -->
<uses-permission android:name="android.permission.BODY_SENSORS" />
<uses-permission android:name="android.permission.BODY_SENSORS_BACKGROUND" />

<!-- CORRECT -->
<uses-permission android:name="android.permission.READ_HEART_RATE" />
<uses-permission android:name="android.permission.READ_HEALTH_DATA_IN_BACKGROUND" />
```

Affected APIs: `Sensor.TYPE_HEART_RATE`, `HEART_RATE_BPM` (Health Services on Wear OS), `ProtoLayout` heart-rate metrics, `FOREGROUND_SERVICE_TYPE_HEALTH`. Mobile apps additionally **MUST** declare an activity that displays the privacy policy.

### 7. `MediaStore.getVersion()` is per-app and opaque

The returned string is now unique per app and non-standard in format.

**MUST** treat it as an opaque change-detection token. **MUST NOT** parse it or infer anything from its contents.

### 8. Safer intents — opt-in, SHOULD adopt

Stricter intent matching: an explicit intent must match the target component's intent filter, and an intent with no action matches no filter.

```xml
<application android:intentMatchingFlags="enforceIntentFilter">
    <receiver
        android:name=".MyBroadcastReceiver"
        android:exported="true"
        android:intentMatchingFlags="none">
        <intent-filter>
            <action android:name="com.example.MY_ACTION" />
        </intent-filter>
    </receiver>
</application>
```

Flags: `enforceIntentFilter` (strict matching), `none` (disable all matching rules, overriding the parent), `allowNullAction`.

**SHOULD** enable `enforceIntentFilter` and watch Logcat during testing:

```
tag=PackageManager & (message:"Intent does not match component's intent filter:" | message:"Access blocked:")
```

### 9. Local network permission — opt-in phase, SHOULD prepare

Access to the local network (mDNS, SSDP, `NsdManager`) will require a new permission. Currently in an opt-in testing phase.

Test with:

```
adb shell am compat enable RESTRICT_LOCAL_NETWORK <package_name>
adb reboot
```

Restore access by declaring `android.permission.NEARBY_WIFI_DEVICES`.

Local network ranges include IPv4 `169.254.0.0/16`, `100.64.0.0/10`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`; IPv6 link-local and directly-connected routes; multicast `224.0.0.0/4` and `ff00::/8`.

**MUST** audit any device-discovery or local-server feature now, before enforcement.

### 10. Bluetooth bond loss and encryption changes

New intents: `ACTION_KEY_MISSING` (bond loss detected) and `ACTION_ENCRYPTION_CHANGE`.

```xml
<intent-filter>
    <action android:name="android.bluetooth.device.action.KEY_MISSING" />
</intent-filter>
```

New unbond API for companion-managed devices: `CompanionDeviceManager.removeBond(deviceId)`. Monitor `ACTION_BOND_STATE_CHANGED`.

**SHOULD** handle `ACTION_KEY_MISSING` in any app that pairs with a peripheral — without it, a bond loss presents as an unexplained connection failure.

### 11. App-owned photos pre-selected in the picker

When targeting SDK 36 on Android 16, limited photo/video access shows app-owned photos pre-selected in the picker. **SHOULD** verify the selection flow still reads correctly.

### 12. GPU syscall filtering (Pixel 6–9, Mali)

Deprecated and development Mali GPU IOCTLs are blocked in production builds; GPU profiling IOCTLs are restricted to shell and debuggable apps. Standard graphics APIs (Vulkan, OpenGL) are unaffected. No action for ordinary apps.

---

## Manifest requirements — MUST

| Requirement | Rule |
|---|---|
| `android:exported` | **MUST** be declared explicitly on every activity, service and receiver that has an intent filter. |
| Non-shared providers/receivers | **MUST** set `android:exported="false"`. |
| `android:debuggable="true"` | **MUST NOT** appear in a release manifest. |
| Network security config | **SHOULD** be declared, with `cleartextTrafficPermitted="false"` for production domains. See `../quality/security.md`. |
| `android.permission.INTERNET` | **MUST** be declared if the app makes network requests. |
| `<profileable android:shell="true" />` | **MUST** be present in the benchmark variant for Macrobenchmark. See `../quality/performance.md`. |
| Permissions | **MUST** request the minimum set; **SHOULD** defer to system intents where that removes a permission. See `../quality/security.md`. |

---

## Build-config requirements that are policy, not preference — MUST

| Setting | Rule | Detail |
|---|---|---|
| App Bundle | **MUST** publish `.aab`, not `.apk` | `../release/android-release.md` |
| R8 on release | **MUST** enable code and resource shrinking | `../quality/performance.md` |
| `proguard-android-optimize.txt` | **MUST** use it, not `proguard-android.txt` | `../deprecations.md` |
| Mapping file upload | **MUST** upload `mapping.txt` for readable crash reports | `../release/android-release.md` |
| Play Billing version | **MUST** be v8+ to publish | `../integrations/subscriptions.md` |

---

## Android / iOS differences

These requirements are Android-only. The iOS equivalents, which have no overlap in mechanism:

| Android | iOS equivalent |
|---|---|
| `targetSdk` floor enforced by Play | Minimum Xcode version enforced by App Store Connect (**Xcode 14+** for uploads from 2026); deployment target set in the Xcode project |
| Edge-to-edge enforcement | Safe-area layout guides, always in effect |
| Predictive back | No system back gesture to handle; interactive pop gesture on `UINavigationController` |
| Network security config | App Transport Security in `Info.plist` |
| Runtime permission model | `Info.plist` usage-description strings (`NSCameraUsageDescription`, etc.) — a **missing string is a launch-time crash**, not a denied permission |
| `AndroidManifest.xml` | `Info.plist` + entitlements |
| Kotlin/Native OS floor | iOS 15.0 default — see `../version-matrix.md` |

CMP on iOS additionally **MUST** have `CADisableMinimumFrameDurationOnPhone` in `Info.plist` — see `../kmp/ios-interop.md`.

---

## Testing recommendations

- **MUST** run the instrumented suite on an emulator or device running **API 36** before release, not only on the minimum supported level.
- **MUST** test on a ≥600dp device (tablet or unfolded foldable) in both orientations, because orientation locks no longer apply there.
- **MUST** test the back gesture — predictive back changes the dispatch path, so a regression here is silent.
- **SHOULD** run a locale sweep covering at least one RTL locale and one of the scripts affected by the `elegantTextHeight` change.
- **SHOULD** enable `enforceIntentFilter` in a debug variant and watch Logcat for blocked intents before enabling it in release.
- **SHOULD** verify the release manifest (`build/intermediates/merged_manifests/release/AndroidManifest.xml`) in CI for `debuggable`, `exported` and cleartext settings, rather than trusting the source manifest.

---

## Cross-references

- Security rules referenced above: `../quality/security.md`
- R8, Baseline Profiles, Macrobenchmark: `../quality/performance.md`
- Bundle, signing, tracks, rollout: `../release/android-release.md`
- Version and deadline details: `../version-matrix.md`
- Full deprecation list: `../deprecations.md`
