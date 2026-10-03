# Reference Implementations

A worked vertical slice — domain, data, network, DI, persistence, presentation, **Compose UI**, and a **Swift-facing facade** — with the decisions behind it written down, and a verification record stating exactly what was compiled and run.

---

## Verification record

**Verified: 2026-10-03.**

| Artefact | Status |
|---|---|
| `user-profile/` — Kotlin sources | ✅ **COMPILED for Android, JVM, iOS device and iOS simulator; TESTED on JVM and iOS simulator** |
| `user-profile/.../ui/` — Compose Multiplatform screen | ✅ compiled on all targets; 6 UI tests run on JVM **and** iOS |
| `user-profile/.../database/` — Room 3 with a real 1→2 migration | ✅ migration test seeds a row at v1 and asserts it at v2 |
| `user-profile/swift-smoke/SharedApiSmoke.swift` | ✅ **compiles** against the generated framework header (compile-only; not run) |
| `CATALOG.md`, `DECISIONS.md` | ✅ every excerpt from verified source |
| `ui-layer.md` | ⚠ partially compiled — per-section status at the top of the file |

### What was actually run

```
Toolchain: Gradle 9.8.0 · OpenJDK 25.0.3 (Android Studio JBR) · Xcode 27.0 · macOS
Kotlin 2.4.20 · Compose Multiplatform 1.12.1 · AGP 9.4.1 (com.android.kotlin.multiplatform.library)
KSP 2.3.12 · Room 3.0.3 (+ room3-testing) · Koin 4.2.2 · Ktor 3.6.0 · DataStore 1.2.1
coroutines 1.11.0 · serialization 1.11.0 · lifecycle 2.11.0 · Okio 3.18.2 · Turbine 1.2.1

$ rm -rf build && gradle --rerun-tasks allTests compileAndroidMain compileKotlinIosArm64 linkDebugFrameworkIosSimulatorArm64
BUILD SUCCESSFUL

  KSP (Room 3)        android / jvm / iosArm64 / iosSimulatorArm64   ✅  3 generated files each
  :compileAndroidMain                                               ✅  AGP 9 KMP library target
  :compileKotlinJvm                                                 ✅
  :compileKotlinIosArm64                                            ✅  device target
  :compileKotlinIosSimulatorArm64                                   ✅
  :linkDebugFrameworkIosSimulatorArm64                              ✅  UserProfile.framework
  :jvmTest                                                          ✅  46 tests, 0 failed
  :iosSimulatorArm64Test                                            ✅  45 tests, 0 failed
                                                                        ────────────────────
                                                                        91 test executions
$ xcrun swiftc -F build/bin/iosSimulatorArm64/debugFramework -parse-as-library -emit-object swift-smoke/SharedApiSmoke.swift
  Swift smoke                                                       ✅  compiled

Warnings in example sources: none
Exported Room schemas: schemas/.../AppDatabase/1.json, 2.json
```

| Test class | JVM | iOS | Covers |
|---|---|---|---|
| `ObserveUserProfileUseCaseTest` | 6 | 6 | use case, boundaries |
| `UserProfileScreenTest` | 6 | 6 | **Compose UI**: every state, interactions, acknowledgement (v2 API) |
| `DefaultUserRepositoryTest` | 5 | 5 | repository, error translation, **cancellation propagation** |
| `UserViewModelTest` | 5 | 5 | shared ViewModel, state-held messages |
| `UserMappersTest` | 4 | 4 | DTO parsing, unknown/absent fields |
| `TokenManagerTest` | 4 | 4 | refresh, rejection → sign-out, serialisation |
| `KtorUserRemoteDataSourceTest` | 3 | 3 | real client config over `MockEngine` |
| `SessionEventBusTest` | 3 | 3 | SharedFlow broadcast semantics |
| `UserRowDaoTest` | 2 | 2 | real Room 3 in-memory DB |
| `KoinGraphTest` | 2 | 2 | every binding resolves; scoping |
| `DataStoreSettingsRepositoryTest` | 2 | 2 | real DataStore over `FakeFileSystem` |
| `UserProfilePresenterTest` | 2 | 2 | presenter without `Dispatchers.setMain` |
| `PlatformTest` | 1 | 1 | `expect`/`actual` on every target |
| `AppDatabaseMigrationTest` (jvmTest) | **1** | — | **1→2 migration with seeded data** (`MigrationTestHelper` reads schema files) |

Every dependency version was confirmed to resolve from Maven Central / Google Maven on the verification date.

### What the compile caught

Sixteen things that looked correct and failed on exactly one target or at configuration time. Full table with the rule each produced: `DECISIONS.md` § *What the compiler taught us*. Headlines:

- `value class` in `commonMain` needs **both** `@JvmInline` and `import kotlin.jvm.JvmInline`.
- `google()` is required in **both** repository blocks even with no Android target.
- Room 3: the extension is `room3 {}`; `Migration.migrate` is **`suspend`**; `MigrationTestHelper` takes the `schemas/` **root**.
- **KSP 2.3.12 works under Kotlin 2.4.20** on all four targets.
- DataStore's duplicate-instance guard is process-global by path; the Kotlin/Native test runner re-instantiates the class per test.
- Swift sees **unprefixed** names (`SharedApi`) via `swift_name`; Objective-C sees `UserProfileSharedApi`.
- AGP 9 needs **Gradle 9 and a JDK newer than 17**; Gradle 8.14 fails on JDK 25 with an error that is just the version string.

### Reproducing it

```bash
cd examples/user-profile
export JAVA_HOME=<a JDK AGP 9 supports — JDK 25 was used>
gradle allTests compileAndroidMain compileKotlinIosArm64 linkDebugFrameworkIosSimulatorArm64   # Gradle ≥ 9.0
xcrun swiftc -target arm64-apple-ios15.0-simulator -sdk "$(xcrun --sdk iphonesimulator --show-sdk-path)" \
  -F build/bin/iosSimulatorArm64/debugFramework -parse-as-library -emit-object -o /tmp/smoke.o swift-smoke/SharedApiSmoke.swift
```

Ships **without** a Gradle wrapper on purpose. **iOS targets and the Swift step require macOS and Xcode**; the Android target requires the Android SDK (`compileSdk 36`). On Linux, `gradle jvmTest compileAndroidMain` verifies everything but iOS.

---

## What is here

```
examples/
  README.md        this file — what is verified and what is not
  CATALOG.md       one entry per example: where, what it avoids, how it is tested
  DECISIONS.md     the rationale, plus what the compiler taught us
  ui-layer.md      extra Compose fragments; per-section compiled/not-compiled status
  user-profile/    the compiled, tested project
```

### `user-profile/` layout

```
src/commonMain/kotlin/com/example/userprofile/
  domain/        User, UserId, UserError/UserException, UserRepository (interface),
                 DisplayPreferences + SettingsRepository, UserProfile, ObserveUserProfileUseCase
  data/          UserDto, UserEntity, UserMappers, data-source interfaces, DefaultUserRepository
  network/       Tokens (TokenStore, TokenRefresher, TokenManager), HttpClientFactory (Ktor + bearer auth),
                 KtorUserRemoteDataSource
  session/       SessionEventBus (SharedFlow broadcast)
  settings/      DataStoreSettingsRepository (DataStore Preferences via Okio)
  database/      AppDatabase — Room 3 entity, DAO, @ConstructedBy constructor, MIGRATION_1_2
  di/            Koin modules; expect fun platformModule()
  platform/      DeviceInfo (interface); expect fun deviceInfo()
  presentation/  UiText, UserUiState, UserViewModel (shared), UserProfilePresenter
  ui/            UserProfileScreen (route + content, Compose Multiplatform), UiTextResolver
  alternatives/  ResultStyle — the Result<T> error variant, also compiled
src/commonMain/composeResources/values/strings.xml
src/androidMain, src/jvmMain, src/iosMain
  di/PlatformModule.<platform>.kt, platform/Platform.<platform>.kt
src/iosMain/.../api/SharedApi.kt      the Swift-facing facade (no Flow, @Throws, callback + handle)
src/commonTest/                        13 test classes (see table above)
src/jvmTest/.../AppDatabaseMigrationTest.kt
swift-smoke/SharedApiSmoke.swift       compiled against the framework header
schemas/                               exported Room schemas (committed — migration-test input)
```

### What it deliberately stubs

| Stubbed | Real version |
|---|---|
| HTTP engine — `MockEngine` in tests; no `OkHttp`/`Darwin` engine bound | host-app platform dependency: `../references/libraries/ktor-networking.md` |
| `TokenStore` — in-memory in tests | Keystore / Keychain: `../references/quality/security.md` |
| `UserLocalDataSource` — in-memory fake in repository tests | the Room DAO in `database/` is the real storage; wiring it behind the interface is a two-line class the host app owns |
| Android `platformModule()` — common bindings only | `androidContext()`-based bindings: `../templates/platform-module.kt` |
| Koin environment for the iOS facade | the host app calls `startKoin` with `sharedModules()` + its environment module |

---

## How to use these

**Start with `CATALOG.md`** to find the example you need, then **read `DECISIONS.md`** before copying it.

**MUST** match the existing codebase over anything here. These are defaults for new code; a production codebase that is internally consistent wins. If you deviate from this reference to match the codebase, say so in your report. See `../references/README.md`.

### Known gaps

- **Library, not app.** No Activity, no navigation host, no `Application`, no Xcode project. Android has no test source set here (Compose UI tests run on JVM and iOS instead); Android *compiles*, it is not *run*.
- **Swift is compile-only.** The smoke file proves the exported surface is callable; no XCTest executes on a simulator.
- **`ui-layer.md`'s list, deferred-read, `R.string`, and Android-DI fragments** remain uncompiled — stated per section in that file.
- **Accessibility** is demonstrated minimally (`contentDescription`, `heading()`); the full guidance is `../references/android/accessibility.md`.
