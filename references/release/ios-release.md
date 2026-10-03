# iOS Release Engineering

**Scope:** Archive and upload paths, build identity, TestFlight, App Review, phased release, KMP-specific release concerns.
**Applies to:** the Xcode project consuming a KMP framework, and iOS-only projects.
**Official sources:**
- <https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds>
- <https://developer.apple.com/documentation/xcode/distributing-your-app-for-beta-testing-and-releases>

**Rule levels:** see `../README.md`. Apple requirements are [OFFICIAL].

---

## Upload paths

| Path | Use |
|---|---|
| **Xcode** (Organizer → Distribute App) | manual releases, first upload |
| **`xcrun altool`** | scripted uploads |
| **Transporter** (Mac App Store app) | manual uploads without Xcode |
| **App Store Connect API** (JWT-authenticated) | **the CI/CD path**; also used by Xcode Cloud |

```bash
xcrun altool --validate-app -f App.ipa -t ios --apiKey "$KEY_ID" --apiIssuer "$ISSUER_ID"
xcrun altool --upload-app   -f App.ipa -t ios --apiKey "$KEY_ID" --apiIssuer "$ISSUER_ID"
```

**MUST** use API-key authentication in CI, not an Apple ID password. **MUST NOT** commit the `.p8` private key; supply it from CI secrets. See `../quality/security.md`.

**SHOULD** run `--validate-app` before `--upload-app`. Validation catches most rejections before the upload and the processing wait.

---

## Requirements — MUST

| Requirement | Detail |
|---|---|
| **Xcode 14 or later** | required for uploads starting in 2026 |
| Role | Account Holder, Admin, App Manager, or Developer |
| Formats | `.ipa` (iOS), `.app`/`.zip` (macOS) |
| Build identity | **bundle ID + version + build string must be unique**; a reused triple is rejected |
| Processing | uploads are processed **asynchronously**; an email arrives when the build is available |

**MUST** increment the build string on every upload, including re-uploads of the same version. A rejected or superseded build consumes its build number permanently.

**SHOULD** derive the build string from CI (a monotonic run number or a commit count), not by hand.

---

## Shared versioning with Android — MUST

**MUST** derive both platforms' versions from **one** source of truth, so a crash in shared Kotlin code maps to one commit on both stores.

```
version.txt / gradle.properties:  appVersion = 3.4.1
                                  buildNumber = 412
          │
          ├── Android: versionName = 3.4.1   versionCode = 412
          └── iOS:     CFBundleShortVersionString = 3.4.1
                       CFBundleVersion = 412
```

```
# WRONG — versions maintained independently
Android versionName 3.4.1 / versionCode 412
iOS     CFBundleShortVersionString 3.4.0 / CFBundleVersion 87
```

Why the wrong form is a problem: a shared-code crash report cannot be correlated across platforms, a support ticket citing "version 3.4.1" is ambiguous, and feature-flag targeting by version behaves differently per platform.

---

## `Info.plist` requirements — MUST

Two classes of entry are release-blocking, and **neither produces a compile error**.

### 1. CMP frame duration

```xml
<key>CADisableMinimumFrameDurationOnPhone</key>
<true/>
```

[OFFICIAL] Required for Compose Multiplatform on iOS. **The documentation states the app will crash at runtime without it.** See `../kmp/ios-interop.md`.

### 2. Usage-description strings

Every protected capability needs one — `NSCameraUsageDescription`, `NSPhotoLibraryUsageDescription`, location strings, and so on.

**A missing usage string is a launch-time or first-use crash**, not a denied permission. Unlike Android, there is no runtime-denial path to fall back to.

**MUST** verify both classes of entry in CI or on a release checklist. **MUST NOT** rely on manual inspection — the failure is invisible until the code path runs.

---

## KMP-specific release concerns

### The Kotlin build runs inside the Xcode build

With **direct integration** (the recommended default — see `../kmp/ios-interop.md`), an Xcode build-phase script invokes Gradle.

**MUST** ensure the macOS CI runner has:

- a JDK matching the project's requirement,
- a warm Gradle cache (a cold cache adds minutes to every archive),
- the Kotlin/Native dependencies (`~/.konan`) cached.

**SHOULD** build the framework as a separate, earlier CI step so a Kotlin compilation failure is reported as a Kotlin failure rather than an opaque Xcode build-phase error.

### Framework debug symbols — MUST plan

> **[UNVERIFIED]** The exact current procedure for symbolicating Kotlin/Native frames in a crash reporter was not established. **MUST** verify before relying on iOS crash reports from shared code.

What is certain: **R8 does not apply to the iOS framework**, so Kotlin code ships unobfuscated, and shared-code frames need the framework's dSYMs uploaded to the crash reporter to be readable. See `../quality/performance.md` and `../integrations/firebase.md`.

**MUST** verify symbolication empirically before release: trigger a deliberate non-fatal from a release-configured build and confirm the report contains a readable frame from shared Kotlin code.

### Minimum OS version

Kotlin/Native defaults: **iOS 15.0**. **MUST** keep the Xcode deployment target consistent with it, or override the Kotlin minimum explicitly:

```kotlin
kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.configureEach {
            freeCompilerArgs += "-Xoverride-konan-properties=minVersion.ios=14.0"
        }
    }
}
```

**MUST** declare `iosArm64` for the device build. See `../version-matrix.md` for target tiers.

---

## TestFlight

| Group | Capacity | Review |
|---|---|---|
| **Internal testers** | account team members | no App Review required |
| **External testers** | up to **10,000** | **beta App Review required** for the first build of a version |

Builds can reach testers **before** App Review for the App Store.

**SHOULD** push an internal TestFlight build on every merge to the release branch. It is the cheapest way to catch an archive-only failure (missing `Info.plist` key, broken framework embedding, signing problem) that no local build exposes.

**MUST** test on a **physical device** via TestFlight before submitting. The simulator does not exercise the device framework slice, real memory pressure, or ATS against production hosts.

---

## App Review — MUST account for

App Store release requires **human App Review**. This is the structural difference from Play.

**MUST** have ready before submission:

- A **restore-purchases** path, if the app sells subscriptions. App Review inspects it. See `../integrations/subscriptions.md`.
- A working demo account, if any content is behind sign-in.
- Accurate privacy disclosures matching what the app actually collects.
- A paywall that displays the real price, period and terms.

**MUST NOT** plan a release schedule that assumes same-day review. Review duration is not guaranteed.

**SHOULD** submit earlier than the Android release for a coordinated launch, and hold the release manually (or via phased release) rather than letting review timing dictate the launch date.

---

## Phased release — SHOULD

Apple's gradual rollout for App Store updates, the counterpart to Play's staged rollout.

- **SHOULD** enable it for any release carrying meaningful risk.
- **MUST** define the halt criteria before starting. Phased release can be paused; a rollout with no threshold is not a safety mechanism.
- **MUST NOT** assume parity with Play staged rollout: the percentage schedule, the pause mechanics and the rollback options differ.

**There is no App Store equivalent of Android's in-app immediate update.** A forced-upgrade requirement **MUST** be implemented in the app itself — a server-provided minimum-version check that blocks the UI.

```kotlin
// :domain/commonMain — works on both platforms
interface MinimumVersionGate {
    suspend fun isUpdateRequired(currentVersion: String): Boolean
}
```

---

## Release checklist [DEFAULT — assembled from the requirements above]

Pre-archive:

1. Shared version and build number bumped from the single source; build string unique.
2. `Info.plist`: `CADisableMinimumFrameDurationOnPhone` present (CMP); every required usage string present.
3. Deployment target consistent with the Kotlin/Native minimum.
4. Certificates and provisioning profiles valid and not expiring inside the release window.
5. Billing/StoreKit products configured and approved in App Store Connect (if monetised).

Build:

6. Kotlin framework builds cleanly as its own CI step.
7. `./gradlew :shared:iosSimulatorArm64Test` green.
8. Swift smoke test over the exported facade green — see `../kmp/ios-interop.md`.
9. Archive and `xcrun altool --validate-app`.

Verify:

10. TestFlight internal build installed on a **physical device**; critical flows smoke-tested.
11. Restore-purchases flow verified on a clean install (if monetised).
12. Symbolication verified: a deliberate non-fatal from a release-configured build produces a readable shared-Kotlin frame.
13. dSYMs uploaded to the crash reporter.

Ship:

14. Submit for App Review with demo credentials and accurate privacy disclosures.
15. Phased release enabled with predefined halt criteria.
16. Watch crash-free rate and the App Store Connect metrics before each phase.

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| Missing `CADisableMinimumFrameDurationOnPhone` with CMP | runtime crash, no compile-time signal |
| Missing an `Info.plist` usage string | launch-time or first-use crash, no fallback path |
| Reusing a build string | upload rejected |
| Independent versioning per platform | shared-code crash reports cannot be correlated |
| Apple ID password in CI | credential exposure; use an API key |
| Committing the App Store Connect `.p8` key | signing identity compromised |
| Shipping without dSYM upload | unreadable shared-code crash reports |
| Validating only on the simulator | misses device slice, memory pressure, production ATS |
| Assuming same-day App Review | missed launch dates |
| No restore-purchases path in a subscription app | App Review rejection |
| Phased release with no halt criteria | a safety mechanism nobody acts on |
| Expecting an in-app forced-update API | none exists; must be built |
| Cold Gradle/konan cache on the macOS runner | archive times inflate and time out |

---

## Android / iOS differences

See the comparison table in `android-release.md`. The items that most often break a shared-codebase release:

| Difference | Implication |
|---|---|
| Human App Review vs automated Play checks | the two platforms **cannot** ship simultaneously on a fixed date without buffer |
| No forced-update API on iOS | a minimum-version gate **MUST** be built into the shared code |
| dSYM vs `mapping.txt` | two separate symbolication pipelines, both required |
| No R8 on the iOS framework | shared Kotlin ships unobfuscated; no secrets in `commonMain` — `../quality/security.md` |
| Acknowledgement (Android) vs restore (iOS) | each is mandatory on exactly one platform — `../integrations/subscriptions.md` |
| Xcode version floor vs `targetSdk` floor | different gates, different cadences |

**MUST** use feature flags rather than coordinated releases to keep a shared codebase shippable when one platform is held in review.

---

## Testing recommendations

- **MUST** run `:shared:iosSimulatorArm64Test` in a macOS CI job on every change to shared code. `iosArm64` is Tier 1 but does not run tests.
- **MUST** build the iOS framework in CI on every change to the shared module's public API, even when no tests run. The framework build is the only validation of Objective-C export.
- **MUST** keep a Swift XCTest smoke test over the exported facade. Export regressions compile fine in Kotlin.
- **MUST** verify `Info.plist` keys mechanically. Both failure modes are crashes with no compile-time signal.
- **SHOULD** install an internal TestFlight build on a physical device for every release candidate. Archive-only failures do not reproduce locally.
- **SHOULD** verify the restore flow on a **clean device install**, not an upgrade — that is the path App Review takes.

Full strategy: `../quality/testing-strategy.md`.

---

## Cross-references

- Framework integration options, `Info.plist` requirement, export constraints: `../kmp/ios-interop.md`
- Android counterpart, shared versioning, comparison table: `android-release.md`
- No R8 on the framework; interop performance: `../quality/performance.md`
- Secrets, API keys, ATS: `../quality/security.md`
- Restore path and store differences: `../integrations/subscriptions.md`
- dSYM and Crashlytics pipeline: `../integrations/firebase.md`
- Target tiers and OS minimums: `../version-matrix.md`
