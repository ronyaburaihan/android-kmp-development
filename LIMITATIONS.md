# Known Limitations

What this skill does **not** verify, does not cover, or could not confirm. Read before relying on it for anything in the first two tables.

**As of:** 2026-10-03.

---

## 1. Claims marked `[UNVERIFIED]` in the references

Each appears in the reference set with that tag. None should be repeated to a user as fact. Verify against the named source, or treat as a risk.

| Claim | Where it appears | What would verify it |
|---|---|---|
| Play Billing **3-day acknowledgement window** (the version floors and dates are verified) | `references/integrations/subscriptions.md` | Play Billing docs for the version in use |
| Play Console track details (internal tester cap, 12-testers-for-14-days production access) | `references/release/android-release.md` | Play Console help |
| Navigation 2.x "maintenance mode" | `references/deprecations.md`, `references/android/navigation.md` | androidx navigation release notes |
| Navigation 3 API names (`rememberNavBackStack`, `entryProvider { entry<T> }`, decorator factories) and the KMP deep-link package | `references/android/navigation.md`, `references/kmp/compose-multiplatform.md` | the `navigation3` 1.2.0 API reference |
| `EncryptedSharedPreferences` deprecation specifics (date, stated reasons, officially suggested replacement) — only the *deprecated status* is confirmed | `references/quality/security.md`, `references/deprecations.md` | the Jetpack Security release notes |
| Firebase's official position on KMP (no first-party SDK) — inferred from absence | `references/integrations/firebase.md` | firebase.google.com |
| "KFire" provenance, maintainer, licence | `references/integrations/firebase.md` | not recommended until established |
| Kotlin `suspend` from Swift "must be called on the main thread" | `references/kotlin/coroutines-and-flow.md` | current `kotlinx.coroutines` native docs |
| Swift export Gradle DSL (`swiftExport {}` vs `export { swift {} }`) — sources conflict | `references/kmp/ios-interop.md` | the `native-swift-export` page for the Kotlin version in use |
| SKIE's supported Kotlin range (one source caps it at 2.1.0) | `references/kmp/ios-interop.md`, `references/version-matrix.md` | SKIE's compatibility matrix |
| OkHttp 5 dropping Kotlin Multiplatform support | `references/libraries/ktor-networking.md`, `references/version-matrix.md` | OkHttp CHANGELOG |
| Ktor certificate pinning configuration per engine | `references/libraries/ktor-networking.md`, `references/quality/security.md` | engine-specific Ktor docs |
| Crashlytics symbolication of Kotlin/Native frames — procedure | `references/integrations/firebase.md`, `references/release/ios-release.md`, `references/quality/observability.md` | an empirical check per project (the release workflows require it) |
| `SavedStateHandle` behaviour in `commonMain` | `references/kmp/compose-multiplatform.md`, `references/architecture/mvvm-udf.md` | CMP ViewModel docs / experiment |
| DataStore "one instance per file" wording for KMP | `references/libraries/room-datastore.md` | DataStore KMP docs |
| Paparazzi / Roborazzi KMP support status | `references/quality/testing-strategy.md`, `workflows/test-ui.md` | their READMEs |
| Community KMP wrappers for WorkManager, AdMob, Firebase — maturity and Kotlin support | `references/android/background-work.md`, `references/integrations/admob.md`, `references/integrations/firebase.md` | each project's README |

---

## 2. What was compiled, and what was not

`examples/README.md` holds the exact verification record. Summary:

| | Verified |
|---|---|
| **Compiled for Android, JVM, iOS device, iOS simulator** | `examples/user-profile/` — domain, data, network (Ktor + bearer auth), DI (Koin), DataStore, **Room 3 with KSP on all four targets**, SharedFlow, tokens, presentation (ViewModel **and** presenter), **Compose Multiplatform UI**, `expect`/`actual` + platform interfaces |
| **Tested on JVM and iOS simulator** | 13 `commonTest` classes (91 executions), including 6 **Compose UI tests** via the v2 API and a **seeded Room 1→2 migration test** (`jvmTest`, `MigrationTestHelper`) |
| **Linked** | `UserProfile.framework` (iOS simulator) with a Swift-facing facade |
| **Swift compiled** | `swift-smoke/SharedApiSmoke.swift` compiles against the generated header (`async throws`, callback + handle, non-reified factory) |

| | Not verified |
|---|---|
| **Android not executed** | the Android target compiles; it has no test source set and no Activity. Nothing ran on an Android device or emulator. |
| **Swift not executed** | compile-only; no XCTest ran on a simulator (needs an Xcode project). |
| **Snippets inside `references/*.md` and `workflows/*.md`, the templates, and the marked sections of `examples/ui-layer.md`** | structurally checked against the compiled example where they overlap; otherwise unverified. Treat as patterns. |
| **CI template** | `templates/ci-github-actions.yml` is valid YAML; it was not executed on a hosted runner. |

## 3. Topics not covered

| Topic | Status |
|---|---|
| **Accessibility, localisation, CI** | now covered in `references/android/accessibility.md`, `references/android/localization.md`, `references/quality/ci-pipeline.md` — guidance from first-party docs; the CI template is not executed |
| **Compose Hot Reload** | mentioned as stable in CMP 1.10; not covered |
| **Gradle convention plugins** in concrete form | pattern described in `references/architecture/modularization.md`; no template |
| **Kotlin 2.4-specific language features** | 2.3's stable features are covered; 2.4.0's own changes were not read |
| **`kotlin.time` vs `kotlinx-datetime` division** post-Kotlin-2.3 | one-line guidance only |
| **Wear OS, Android TV, Automotive, XR** | only their `targetSdk` floors |
| **Widgets (Glance), Android Auto, Play Feature Delivery mechanics** | not covered |
| **Server-side** (Ktor server, receipt validation implementation) | out of scope; the client-side rules state it must exist |
| **Compose Multiplatform web** | marked Beta throughout; no specific guidance |
| **Desktop (JVM) apps** | targets compile in the example; no desktop-specific UI guidance |

---

## 4. Structural limitations of the skill

- **Version numbers rot.** Every version is dated 2026-10-03. `references/version-matrix.md` says where to re-check; the agent is instructed never to state a version from memory.
- **Platform deadlines rot faster.** Play and Billing floors are `[UNVERIFIED]` by design — they must be confirmed live before a release decision.
- **The reference defaults are opinions in places.** Everything tagged `[DEFAULT]` is a defensible engineering choice, not a requirement, and the standing contract tells the agent to match the codebase over it. A codebase that consistently does otherwise is not wrong.
- **Hilt vs Koin.** Google recommends Hilt; this skill's examples use Koin because Hilt cannot compile in `commonMain`. That is a constraint, not a preference, and the skill says so — but Android-only projects on Hilt should stay on Hilt.
- **The compiled example is a library module, not an app.** It has a Compose screen and a Swift-facing facade, but no navigation host, no Activity/`Application`, and no Xcode project. It proves every layer compiles and the UI renders in tests; it does not prove an app launches.
- **No agent-run eval suite ships with the skill.** `EVALUATION.md` defines what to check and suggests scenarios; it does not include executable evals.
- **Secondary sources were used for leads.** Where a first-party source could not be reached, the claim is tagged rather than omitted. Tagged claims are the honest boundary of what this skill knows. (Play/Billing deadlines and Circuit's test API were subsequently confirmed from first-party sources and untagged.)

---

## 5. Corrections made during verification

Facts the research had wrong, found by compiling or by checking registries, and fixed in the references:

| Original claim | Reality | Fixed in |
|---|---|---|
| Room 3 (`androidx.room3`) is alpha-only | **3.0.3 is stable** (2026-09-09); 3.1.0-alpha01 is the alpha | `references/version-matrix.md`, `references/libraries/room-datastore.md` |
| KSP versions are Kotlin-prefixed (`2.4.20-x.y.z`) and MUST match Kotlin | KSP 2.x uses its **own** line; latest **2.3.12**; works under Kotlin 2.4.20 (verified by compiling Room 3) | `references/version-matrix.md`, `references/kmp/project-structure.md`, `references/libraries/room-datastore.md` |
| Whether a Koin BOM exists was unknown | `io.insert-koin:koin-bom` **4.2.2 exists** | `references/version-matrix.md` |
| `google()` repository needed only with an Android target | Required for **any** module using AndroidX KMP artifacts (lifecycle, Room 3, DataStore) **and** for the `androidx.room3` Gradle plugin in `pluginManagement` | `references/version-matrix.md`, `references/libraries/room-datastore.md`, `examples/user-profile/settings.gradle.kts` |
| `value class` in `commonMain` needs only `@JvmInline` | Needs `@JvmInline` **and** `import kotlin.jvm.JvmInline`; each omission fails on a different target | `references/kotlin/language-essentials.md`, `examples/DECISIONS.md` |
| Room 3 Gradle extension is `room {}` | It is **`room3 {}`** | `references/libraries/room-datastore.md` |
| `lifecycle-viewmodel-navigation3` is a stable dependency | **2.12.0-alpha04** — alpha | `references/kmp/compose-multiplatform.md`, `references/version-matrix.md` |
| Room 3 migration signature is `fun migrate(connection)` | it is **`suspend fun migrate(connection: SQLiteConnection)`** | `references/libraries/room-datastore.md` |
| KMP migration testing unverified | `androidx.room3:room3-testing` 3.0.3 `MigrationTestHelper` works from `jvmTest` with the `schemas/` root | `references/libraries/room-datastore.md`, `workflows/test-integration.md` |
| Swift sees framework-prefixed class names | Objective-C does; the header's `swift_name` gives Swift the unprefixed Kotlin names | `references/kmp/ios-interop.md` |
| Play/Billing deadlines `[UNVERIFIED]` | confirmed on Play Console help and the Billing deprecation FAQ | `references/version-matrix.md`, `references/android/platform-requirements.md`, `references/integrations/subscriptions.md` |
| Circuit test API unreachable | read from the `circuit-test` 0.39.0 sources jar | `references/architecture/presenter.md` |
| AGP 9 unbuildable here | buildable with JDK 25 (Android Studio JBR) + Gradle 9.8.0; Android target + Compose now compiled | `references/version-matrix.md`, `examples/README.md` |
