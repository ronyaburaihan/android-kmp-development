# Permissions

**Scope:** Runtime permission flow, denial handling, one-time and granular permissions, the iOS counterpart, KMP layering.
**Official sources:**
- <https://developer.android.com/training/permissions/requesting>
- <https://developer.android.com/privacy-and-security/security-best-practices>
- <https://developer.android.com/about/versions/16/behavior-changes-16>

**Rule levels:** see `../README.md`. Flow and API facts are [OFFICIAL].

---

## The flow — MUST

1. Declare the permission in the manifest.
2. **Wait for the user to invoke the feature** that needs it. **MUST NOT** request on launch.
3. Check: already granted → proceed.
4. If `shouldShowRequestPermissionRationale()` is true → show in-context rationale UI first.
5. Request with `ActivityResultContracts.RequestPermission` (or `RequestMultiplePermissions`).
6. Handle the result: granted → proceed; denied → **degrade gracefully**.

```kotlin
// Activity or Fragment — registered once, before STARTED
private val requestCamera = registerForActivityResult(
    ActivityResultContracts.RequestPermission(),
) { granted -> if (granted) openCamera() else showCameraUnavailable() }

private fun onTakePhotoClicked() {
    when {
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED -> openCamera()

        ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) ->
            showRationale(onContinue = { requestCamera.launch(Manifest.permission.CAMERA) })

        else -> requestCamera.launch(Manifest.permission.CAMERA)
    }
}
```

In Compose, the same contract via `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> … }`.

**MUST NOT** use the deprecated `requestPermissions()` / `onRequestPermissionsResult()` request-code API in new code.

---

## Denial — MUST degrade, not nag

[OFFICIAL]

| Do | Don't |
|---|---|
| Keep the rest of the app working | Block the UI with a full-screen warning |
| Say specifically what is unavailable ("Camera access is off, so photo mode is disabled") | "Permission denied" |
| Offer the feature again when the user next invokes it | Re-prompt on every screen |
| Accept a permanent denial | Deep-link to system settings to force a change |

**Permanent denial:** after two denials, `shouldShowRequestPermissionRationale()` returns `false` and the system dialog no longer appears. The app **MUST** treat the feature as unavailable. There is no API to detect "never ask again" directly — the signal is a denial result combined with `shouldShowRequestPermissionRationale() == false`.

Debugging: `adb shell dumpsys package <pkg>` shows `USER_SET` (denied once) / `USER_FIXED` (permanent). Reset with `adb shell pm clear-permission-flags <pkg> <perm> user-set user-fixed`.

---

## Permission facts that change behaviour

| Fact | Consequence |
|---|---|
| **One-time grants** (Android 11+) for location, mic, camera | access ends shortly after the app backgrounds. **MUST NOT** cache "granted" — re-check at use. |
| **Permissions can be revoked at any time**, and auto-revoked for unused apps | **MUST** check before every use, not once at startup |
| **`POST_NOTIFICATIONS`** (Android 13+) is a runtime permission | foreground-service notifications and push are silent without it |
| **Health permissions** replaced `BODY_SENSORS*` at API 36 | `READ_HEART_RATE`, `READ_HEALTH_DATA_IN_BACKGROUND`; mobile apps also need a privacy-policy activity. See `platform-requirements.md` |
| **Local network** permission in opt-in phase (API 36) | device-discovery features **MUST** be audited now |
| **`revokeSelfPermissionOnKill`** (Android 13+) | **SHOULD** drop permissions a feature no longer needs |
| **Background location** needs a separate request after foreground location, with its own rationale | **MUST NOT** request both in one dialog |

---

## Avoiding the permission — SHOULD

Many permissions are avoidable by delegating to the system. [OFFICIAL]

| Need | Instead of | Use |
|---|---|---|
| Pick a photo | `READ_MEDIA_IMAGES` | Photo Picker (`PickVisualMedia`) |
| Pick a file | storage permission | Storage Access Framework (`OpenDocument`) |
| Add a contact | `WRITE_CONTACTS` | `Intent(Intent.ACTION_INSERT)` with `ContactsContract` |
| Take one photo | `CAMERA` (sometimes) | `ActivityResultContracts.TakePicture` |

**SHOULD** prefer the no-permission path. Fewer permissions is both a privacy and a store-review advantage.

---

## Layering in a KMP project — MUST

The permission *mechanism* is platform code. The *decision* ("can this feature run?") is domain state.

```kotlin
// commonMain — domain-owned capability
public interface CameraAccess {
    public fun observeStatus(): Flow<CameraStatus>       // Granted | Denied | NotDetermined
    public suspend fun request(): CameraStatus
}
public enum class CameraStatus { GRANTED, DENIED, NOT_DETERMINED }
```

```kotlin
// androidMain — ActivityResult-backed implementation, injected via the platform DI module
// iosMain    — AVCaptureDevice.requestAccess-backed implementation
```

**MUST NOT** reference `Manifest.permission.*`, `ContextCompat`, or an `ActivityResultLauncher` from `commonMain`. **MUST NOT** model permission state as a boolean cached at startup.

---

## Android / iOS differences

| Concern | Android | iOS |
|---|---|---|
| Declaration | `<uses-permission>` in the manifest | **usage-description string in `Info.plist`** (`NSCameraUsageDescription`, …) |
| Missing declaration | request silently fails / returns denied | **launch-time or first-use crash** |
| Rationale | app-provided UI before the system dialog | the usage-description string *is* the rationale; shown in the system dialog |
| Re-prompt after denial | possible until permanent denial | **never** — the user must go to Settings; the app may deep-link there with `UIApplication.openSettingsURLString` |
| One-time grant | location, mic, camera | "Allow Once" for location |
| Background location | separate permission | "Always" vs "While Using", with a provisional "Always" the OS later confirms |
| Notifications | runtime permission (13+) | `UNUserNotificationCenter.requestAuthorization` |
| Tracking | n/a | App Tracking Transparency (`ATTrackingManager`) — **mandatory before any cross-app tracking** |

The missing-string crash on iOS has no compile-time signal. **MUST** verify `Info.plist` entries mechanically in CI — see `../release/ios-release.md`.

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| Requesting every permission at launch | high denial rate; Play policy risk |
| Caching "granted" | wrong after revocation or a one-time grant expiry |
| Custom-styled system dialog | not possible; attempts are rejected |
| Full-screen block on denial | explicitly discouraged; users uninstall |
| Deep-linking to Settings to force a grant | explicitly discouraged on Android |
| `requestPermissions()` request-code API | deprecated |
| Foreground + background location in one request | the second is silently dropped |
| Permission APIs in `commonMain` | does not compile for native |
| Missing iOS usage string | crash |

---

## Testing recommendations

- **MUST** test the domain logic against a fake `CameraAccess` in `commonTest`: granted, denied, not-determined, and revoked-mid-session.
- **MUST** test the denied path in the UI: the feature degrades, the rest of the screen works, no dialog loop.
- **SHOULD** run an instrumented test with `GrantPermissionRule` for the granted path and a separate one that starts denied.
- **MUST** test one-time-grant expiry by backgrounding the app (manual or UI Automator) and re-invoking the feature.
- iOS: **MUST** include the `Info.plist` strings in the release checklist; **SHOULD** test on a device that has previously denied the permission, since the prompt will not reappear.

---

## Cross-references

- API 36 permission changes (health, local network): `platform-requirements.md`
- Least-privilege and intent-based delegation: `../quality/security.md`
- `POST_NOTIFICATIONS` and foreground services: `background-work.md`
- Interface-over-platform-API layering: `../kmp/project-structure.md`
- `Info.plist` crash-level checks: `../kmp/ios-interop.md`, `../release/ios-release.md`
