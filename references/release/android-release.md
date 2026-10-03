# Android Release Engineering

**Scope:** App Bundle, signing, release build configuration, Play tracks, staged rollout, in-app updates, release checklist.
**Applies to:** Android app modules.
**Official sources:**
- <https://developer.android.com/guide/app-bundle>
- <https://developer.android.com/studio/publish/app-signing>
- <https://developers.google.com/android-publisher/tracks>
- <https://support.google.com/googleplay/android-developer/answer/9859348>
- <https://developer.android.com/guide/playcore/in-app-updates>

**Rule levels:** see `../README.md`.

---

## Android App Bundle — MUST

The `.aab` is the publishing format. You upload one bundle; Play generates and signs per-device APKs so users download only what their device needs.

| Requirement | Since |
|---|---|
| New apps **MUST** publish as an App Bundle | August 2021 |
| New and existing **TV** apps **MUST** use App Bundles | June 2023 |
| Apps over **200 MB** **MUST** use Play Feature Delivery or Play Asset Delivery | — |

```
./gradlew bundleRelease     # app/release/app-release.aab
```

### Size limits — MUST respect

- **4 GB** cap on the compressed download of base APK + configuration APKs.
- Each **on-demand** feature download is subject to the same 4 GB cap.
- Asset packs have separate limits and do not count toward the APK cap.

**MUST NOT** use `.obb` APK expansion files. App Bundles do not support them.

If the cap is hit:

```kotlin
android {
    bundle { enableSplit = true }        // all configuration APKs
    buildTypes {
        release { isMinifyEnabled = true; isShrinkResources = true }
    }
}
```

and convert features used by only some users into dynamic feature modules.

### Known pitfalls — MUST know

- A sideloaded app missing its split APKs **fails on Android 10+** and on Google-certified devices. Use `bundletool` to install correctly.
- Tools that rewrite the resource table at build time can corrupt a bundle. Disable them for bundle builds.
- Property conflicts between the base module and a feature module (e.g. differing `debuggable`) cause build or runtime failures. Feature modules inherit base configuration — override deliberately.

### Local verification with bundletool — SHOULD

```
java -jar bundletool-all.jar build-apks \
  --bundle=app-release.aab \
  --output=app.apks \
  --ks=keystore.jks --ks-pass=pass:*** \
  --ks-key-alias=upload --key-pass=pass:***

java -jar bundletool-all.jar install-apks --apks=app.apks
java -jar bundletool-all.jar get-device-spec --output=device-spec.json
```

**SHOULD** run a bundletool install on at least one physical device before uploading. It is the only local check that the split configuration works.

---

## Signing — MUST

**MUST** sign every app before publishing.

**Play App Signing:** you sign the bundle with the **upload key**; Play re-signs the delivered APKs with the **app signing key** it holds.

- **MUST NOT** commit keystores or passwords to version control. Supply them from CI secrets or a git-ignored properties file. See `../quality/security.md`.
- **MUST** back up the upload key. Losing it requires a key-reset request to Google.

```kotlin
// CORRECT — credentials from the environment, never from source
android {
    signingConfigs {
        create("release") {
            storeFile = System.getenv("UPLOAD_KEYSTORE")?.let(::file)
            storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
            keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
        }
    }
    buildTypes { release { signingConfig = signingConfigs.getByName("release") } }
}
```

```kotlin
// WRONG — keystore password in the build file
signingConfigs {
    create("release") {
        storeFile = file("release.jks")
        storePassword = "hunter2"
    }
}
```

---

## Release build configuration — MUST

Each item is covered in detail elsewhere; this is the release gate.

| Requirement | Reference |
|---|---|
| `targetSdk` at the Play floor (API 36) | `../android/platform-requirements.md` |
| R8 minification + resource shrinking, `proguard-android-optimize.txt` | `../quality/performance.md` |
| Baseline Profile generated from a **non-minified** benchmark variant and consumed by release | `../quality/performance.md` |
| `mapping.txt` archived and uploaded to the crash reporter | `../quality/performance.md` |
| No `android:debuggable="true"`, no cleartext, no verbose HTTP logging | `../quality/security.md` |
| Play Billing at or above the publishing floor | `../integrations/subscriptions.md` |
| Signed with the upload key | above |

**MUST** verify these against the **built artifact and the merged manifest**, not the source. Manifest merging from libraries can reintroduce `debuggable` or a permission that the app manifest never declared.

```
build/intermediates/merged_manifests/release/AndroidManifest.xml
```

---

## Play release tracks

| Track | Audience |
|---|---|
| **Internal testing** | a small set of chosen testers; fastest propagation |
| **Closed testing** | chosen testers or groups |
| **Open testing** | public beta, discoverable on Play |
| **Production** | all users in the selected countries |

Internal test builds are deployed differently from closed and open tracks, which go through Play's normal distribution.

> **[UNVERIFIED]** Secondary sources report: internal testing is capped at **100 testers**, and a new developer account must run **12 testers on closed testing for 14 days** before requesting production access. **MUST** confirm on Play Console help before planning a launch around these numbers.

**SHOULD** promote through internal → closed → production rather than publishing directly to production.

**MUST** use the **internal testing track**, not Play internal app sharing, when validating Baseline Profiles — internal app sharing is explicitly unsupported for them. See `../quality/performance.md`.

---

## Staged rollout — SHOULD

Release to a percentage of production users and increase it as confidence grows. Controls available: halt, resume, and roll back.

- **Available for updates.** **MUST NOT** plan a staged rollout for a first production publish.
- **MAY** also be applied to test tracks.
- **SHOULD** start low (a few percent), watch crash-free rate and ANR rate, then ramp.
- **MUST** define the halt criteria *before* starting the rollout. A rollout with no threshold is not a safety mechanism.

```
internal  →  closed  →  production @ 5%  →  20%  →  50%  →  100%
                              │
                              └── halt on crash-free rate regression
```

---

## In-app updates — MAY

`AppUpdateManager` (Play Core) prompts active users to update.

| Flow | Behaviour | Use for |
|---|---|---|
| **Flexible** | background download and install; app stays usable; state is observable | non-critical improvements |
| **Immediate** | blocking full-screen flow; Play handles install and restart | critical fixes, forced minimum versions |

Requirements: API 21+, Android mobile/tablet/ChromeOS, and **incompatible with `.obb` expansion files**.

**SHOULD** use flexible updates by default and reserve immediate updates for genuine breakage.
**MUST NOT** use an immediate update as a routine release mechanism — it interrupts the user's task.

---

## Play Feature Delivery and Asset Delivery — MAY

- **Play Feature Delivery**: feature modules delivered conditionally, on demand, or as instant. Needed above 200 MB. No iOS equivalent — see `../architecture/modularization.md`.
- **Play Asset Delivery**: large game assets, with separate size limits.

**SHOULD NOT** adopt dynamic feature modules unless the size cap or a genuine on-demand requirement forces it. They add build complexity and a runtime install failure mode.

---

## Release checklist [DEFAULT — assembled from the official requirements above]

Pre-build:

1. `targetSdk` at the Play floor; `compileSdk` supported by the AGP version.
2. Billing Library at or above the publishing floor (if monetised).
3. Dependency vulnerability scan clean.
4. Version code incremented; version name set from a single source shared with iOS.

Build:

5. R8 on, resource shrinking on, `proguard-android-optimize.txt`.
6. Baseline Profile regenerated if critical flows changed.
7. Signed with the upload key from CI secrets.
8. `./gradlew bundleRelease`.

Verify:

9. Merged release manifest: not debuggable, `exported` explicit, network security config referenced.
10. Full instrumented suite green against the **minified** variant.
11. `bundletool install-apks` on a physical device; smoke-test the critical flows.
12. Macrobenchmark startup and frame metrics within threshold.
13. Crash symbolication verified — send a deliberate non-fatal from the release-configured build and confirm the report is readable, including shared Kotlin frames. See `../integrations/firebase.md`.

Ship:

14. `mapping.txt` archived and uploaded.
15. Internal track → smoke test → closed track.
16. Production staged rollout with predefined halt criteria.
17. Watch crash-free rate, ANR rate and the store's vitals dashboard before each ramp.

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| Publishing an APK where Play requires a bundle | upload rejected |
| Keystore or password in version control | signing identity compromised |
| Not archiving `mapping.txt` | release crash reports unreadable, permanently for that build |
| Verifying the source manifest instead of the merged one | a library-introduced `debuggable` or permission reaches production |
| Running the instrumented suite only against a debug build | R8 breakage reaches users |
| Generating a Baseline Profile from a minified build | gain silently lost |
| Play internal app sharing for Baseline Profile validation | unsupported |
| Straight-to-100% production rollout | no containment for a regression |
| Staged rollout with no halt criteria | a safety mechanism nobody acts on |
| Immediate in-app update as routine release flow | interrupts users on every release |
| `.obb` expansion files | unsupported with App Bundles and in-app updates |
| Letting `targetSdk` or Billing drift past a Play deadline | cannot ship any update, including a crash fix |
| Dynamic feature modules adopted without a size or on-demand driver | build complexity and a new runtime failure mode |

---

## Android / iOS differences

| Concern | Android | iOS |
|---|---|---|
| Artifact | `.aab`, Play generates APKs | `.ipa` archive |
| Signing | upload key + Play-held app signing key | Apple certificates + provisioning profiles |
| Review | automated policy checks; usually fast | **human App Review** |
| Gradual release | staged rollout, haltable and rollback-able | phased release |
| Test distribution | internal / closed / open tracks | TestFlight internal / external |
| Forced update | in-app updates (immediate flow) | **no equivalent** — must be implemented in-app |
| Version floor | `targetSdk` enforced by Play | Xcode version enforced for uploads |
| Symbolication input | `mapping.txt` | dSYM |
| Dynamic delivery | Play Feature Delivery | **no equivalent** |

**MUST** derive `versionName`/`versionCode` and `CFBundleShortVersionString`/`CFBundleVersion` from **one** source of truth, so a crash in shared Kotlin code maps to one commit on both stores. See `../release/ios-release.md`.

**MUST** plan for the two platforms **not** being in lockstep: Play staged rollout and Apple review plus phased release have different timelines. Feature flags, not simultaneous releases, are what keep a shared codebase shippable.

---

## Testing recommendations

- **MUST** gate the release on the instrumented suite passing against the **minified** variant (see `../quality/performance.md`).
- **MUST** smoke-test a `bundletool`-installed build on a physical device. Emulator-only validation misses split-APK problems.
- **MUST** verify the merged release manifest in CI rather than by inspection.
- **SHOULD** run a scheduled Macrobenchmark job on a fixed physical device and fail the release on a startup or frame regression beyond a defined threshold.
- **SHOULD** verify the release artifact has no verbose HTTP logging and no debug trust anchors reachable. See `../quality/security.md`.
- **SHOULD** check AAB size against the previous release and investigate any jump — usually a new transitive dependency or an over-broad keep rule.

---

## Cross-references

- `targetSdk` floor and API 36 behaviour changes: `../android/platform-requirements.md`
- R8, Baseline Profiles, Macrobenchmark, mapping files: `../quality/performance.md`
- Keystore handling, release hardening, logging: `../quality/security.md`
- Billing publishing floor: `../integrations/subscriptions.md`
- Crash symbolication pipeline: `../integrations/firebase.md`
- Dynamic feature modules in the module graph: `../architecture/modularization.md`
- iOS counterpart and shared versioning: `ios-release.md`
- Versions and deadline verification: `../version-matrix.md`
