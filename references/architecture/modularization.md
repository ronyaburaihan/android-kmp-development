# Modularization

**Scope:** When to modularize, module taxonomy, granularity, dependency direction, visibility, convention plugins, build performance.
**Applies to:** multi-module Android and KMP projects.
**Official sources:**
- <https://developer.android.com/topic/modularization>
- <https://developer.android.com/topic/modularization/patterns>
- Reference sample: <https://github.com/android/nowinandroid>

**Rule levels:** see `../README.md`.

---

## When NOT to modularize — MUST evaluate first

Google's position: modularization is **optional** unless the project needs one of:

- code reuse across multiple apps,
- strict visibility control,
- Play Feature Delivery.

It is **worth considering** for scalability, ownership, encapsulation, or build times.

**MUST NOT** split a small project into modules by reflex. Google names three pitfalls explicitly:

| Pitfall | Symptom |
|---|---|
| **Too fine-grained** | Build overhead and boilerplate exceed the benefit; configuration becomes a burden. |
| **Too coarse-grained** | A monolith with extra steps; none of the benefits materialise. |
| **Too complex** | Modularization does not suit the project's size at all. |

**SHOULD** start with one module per app plus one shared module, and split when a specific pain appears: a slow build, a team boundary, a reuse requirement, or a visibility leak.

**The cost is higher in KMP**, because every module multiplies by the number of declared targets. A 40-module KMP project with three targets configures 120 compilations.

---

## Benefits, as officially stated

Reusability, visibility control (`internal`/`private` across boundaries), customizable delivery (Play Feature Delivery), scalability (change containment), ownership (a named owner per module), encapsulation, testability (isolation), build performance (incremental builds, caching, parallel compilation).

---

## Module taxonomy

```
:app                    application module. Wiring only: DI graph assembly,
                        navigation host, manifest, no feature logic.

:feature:<name>         one user-facing feature. Compose UI + ViewModels +
                        feature-scoped navigation. Depends on :core:* and :domain.

:core:<concern>         shared capability: :core:network, :core:database,
                        :core:designsystem, :core:ui, :core:common, :core:datastore.

:domain                 entities, use cases, repository interfaces. No frameworks.
                        See clean-architecture.md.

:data                   repository implementations, DTOs, mappers, data sources.

:core:testing           shared test fixtures, fakes, rules, dispatchers.

:build-logic            included build. Convention plugins. Not a project module.
```

**MUST:**

- `:app` contains no feature logic. If `:app` grows a screen, it belongs in a `:feature:*`.
- A `:feature:*` module **MUST NOT** depend on another `:feature:*`. Feature-to-feature coupling is what modularization exists to prevent.
- `:core:*` modules **MUST NOT** depend on `:feature:*`.
- Dependencies point in one direction: `:app` → `:feature:*` → `:domain`/`:core:*` → nothing.
- The graph **MUST** be acyclic.

```
// CORRECT
:app ──► :feature:feed ──► :domain ◄── :data ──► :core:network
     └──► :feature:settings ──► :domain

// WRONG — feature-to-feature dependency
:feature:feed ──► :feature:settings
```

Why the wrong form is a problem: a change in `:feature:settings` now recompiles and re-tests `:feature:feed`, the two features cannot be owned independently, and the dependency will become bidirectional within two sprints. Share through `:domain` or `:core:*` instead, or navigate by route rather than by direct call.

---

## Cross-feature communication — MUST invert

When feature A needs something feature B owns, the shared thing moves **down**, not sideways.

```kotlin
// CORRECT — the contract lives in :domain; both features depend on :domain
// :domain
interface BookmarkRepository {
    fun observeBookmarks(): Flow<List<ArticleId>>
    suspend fun toggle(id: ArticleId)
}

// :feature:feed and :feature:bookmarks both inject BookmarkRepository
```

```kotlin
// WRONG — :feature:feed calls into :feature:bookmarks
// :feature:feed
class FeedViewModel(private val bookmarksViewModel: BookmarksViewModel)
```

**For navigation between features**, **SHOULD** have each feature expose its routes and have `:app` own the navigation graph assembly. With Navigation 3 the back stack is user-owned, so `:app` holds the stack and each feature contributes entries. See `../kmp/compose-multiplatform.md`.

---

## The API/Impl split — MAY

`nowinandroid` uses an API/Impl split in feature modules. **MAY** adopt it when:

- two implementations must coexist (a real one and a demo/offline one), **or**
- compile avoidance matters: consumers depend on `:api` only, so changing `:impl` does not recompile them.

**SHOULD NOT** adopt it everywhere. It doubles the module count, which is the "too fine-grained" pitfall.

```
:feature:feed:api     interfaces + route declarations
:feature:feed:impl    implementation; only :app depends on it
```

---

## Visibility — MUST use `internal`

The main concrete benefit of a module boundary is that `internal` becomes enforceable.

```kotlin
// CORRECT — only the repository interface and the DI module are public
// :data
internal class OfflineFirstNewsRepository(...) : NewsRepository
internal fun ArticleDto.toEntity() = ...
val dataModule = module { single<NewsRepository> { OfflineFirstNewsRepository(get(), get(), get()) } }

// WRONG — implementation and mappers are public, so consumers bypass the interface
class OfflineFirstNewsRepository(...) : NewsRepository
fun ArticleDto.toEntity() = ...
```

Why the wrong form is a problem: a consumer can construct the implementation directly, bypassing the DI graph and the interface, and can depend on the mapper — so the DTO type becomes part of the module's effective public API and cannot change without breaking callers.

**MUST** make everything `internal` by default in `:data` and `:feature:*` modules, and promote to `public` only with a reason.

**MUST** apply the API-surface rules from `../kotlin/coding-conventions.md` (explicit visibility, explicit return types, KDoc) to whatever remains public.

---

## Convention plugins — SHOULD

[DEFAULT — the pattern; the `nowinandroid` sample is OFFICIAL]

**SHOULD** extract shared build configuration into convention plugins in a `build-logic` **included build**, not `buildSrc`.

```
/settings.gradle.kts
    pluginManagement { includeBuild("build-logic") }

/build-logic/
    settings.gradle.kts
    convention/
        build.gradle.kts
        src/main/kotlin/
            AndroidApplicationConventionPlugin.kt
            AndroidLibraryConventionPlugin.kt
            KotlinMultiplatformConventionPlugin.kt
            ComposeMultiplatformConventionPlugin.kt
```

```kotlin
// build-logic/convention/src/main/kotlin/KotlinMultiplatformConventionPlugin.kt
class KotlinMultiplatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")

        extensions.configure<KotlinMultiplatformExtension> {
            androidTarget()
            iosArm64()
            iosSimulatorArm64()

            sourceSets.commonTest.dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
```

```kotlin
// a module build file becomes declarative
plugins {
    alias(libs.plugins.convention.kmp)
    alias(libs.plugins.convention.compose)
}
```

**MUST NOT** use `buildSrc` for this. A change to any file in `buildSrc` invalidates the configuration cache and the build classpath for the whole project; an included build does not.

**MUST** declare every version in `gradle/libs.versions.toml`, including plugin versions used by the convention plugins. See `../version-matrix.md`.

**MUST** keep the Kotlin ↔ Compose-compiler ↔ KSP ↔ CMP lockstep in one place — the catalog — so a convention plugin cannot drift from a module.

---

## Granularity — the decision test

Split a module when **at least one** is true:

1. A team boundary needs an ownership boundary.
2. The code is reused by more than one app or more than one feature.
3. A visibility leak is causing real misuse.
4. Build measurement shows the module is a bottleneck on the critical path.
5. Play Feature Delivery requires a dynamic feature module.

**MUST NOT** split because a layer diagram has four boxes in it. Google's data-layer example is explicit that granularity should **increase as the codebase grows**, not be front-loaded.

**SHOULD** measure before splitting for build performance:

```
./gradlew assembleDebug --scan
./gradlew :feature:feed:compileDebugKotlin --profile
```

---

## Android / iOS differences

| Concern | Android | KMP |
|---|---|---|
| Module cost | one compilation per variant | one compilation **per target per variant** — a 3-target module costs ~3× |
| Dynamic feature modules | Play Feature Delivery supported | **no iOS equivalent**; dynamic delivery is Android-only |
| Visibility | `internal` is per-module | `internal` is per-module **per target set**; a declaration `internal` in `commonMain` is not visible from `androidMain` of a different module |
| iOS framework | n/a | **one framework per exported module** under Swift export; on the Objective-C path, multiple modules export into one framework and **same-named classes across packages are renamed unpredictably** — see `../kmp/ios-interop.md` |
| Feature modules on iOS | n/a | the Xcode project consumes the aggregate framework, so iOS does not see the module graph. Module boundaries buy build time and discipline, not iOS-side structure. |

**MUST** audit class-name uniqueness across modules that export to iOS on the Objective-C path. Two modules each declaring `com.example.<pkg>.User` produce unpredictable Swift names.

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| `:feature:a` → `:feature:b` | recompilation coupling; becomes bidirectional |
| `:core:*` → `:feature:*` | inverts the dependency direction; cycles |
| Feature logic in `:app` | `:app` recompiles on every change and is on every build's critical path |
| `public` implementation classes | the interface boundary stops being enforceable |
| `buildSrc` for convention plugins | whole-project configuration invalidation on every change |
| Hardcoded versions in module build files | guaranteed drift from the lockstep |
| One module per layer per feature, from day one | the "too fine-grained" pitfall; build time dominated by configuration |
| A module with one file in it | all of the overhead, none of the benefit |

---

## Testing recommendations

- **MUST** give each module its own tests. A module whose tests live in `:app` is not independently testable, which removes the main reason to have split it.
- **SHOULD** create a `:core:testing` module holding shared fakes, test dispatchers and fixtures, consumed via `testImplementation` / `commonTest`.

```kotlin
// :core:testing/src/commonMain/kotlin/.../FakeNewsRepository.kt
class FakeNewsRepository : NewsRepository {
    private val feed = MutableStateFlow<List<Article>>(emptyList())
    override fun observeFeed(): Flow<List<Article>> = feed
    override suspend fun refresh() = Unit
    fun emit(value: List<Article>) { feed.value = value }
}
```

**MUST** keep fakes in `commonMain` of `:core:testing` (not `commonTest`) so other modules can consume them; `commonTest` of one module is not visible to another.

- **SHOULD** enforce the dependency graph mechanically rather than by review — a Gradle task or a test that asserts no `:feature:*` depends on another `:feature:*`. A convention violated once is violated permanently. [DEFAULT]
- **SHOULD** verify in CI that every module's `commonTest` actually runs for every declared target (`./gradlew allTests`). Platform source sets with zero tests are a common blind spot.

---

## Cross-references

- Layer responsibilities and the repository rule: `../android/app-architecture.md`
- Domain/data split and the dependency rule: `clean-architecture.md`
- Targets, source sets, Gradle lockstep: `../kmp/project-structure.md`
- Visibility and API-surface conventions: `../kotlin/coding-conventions.md`
- Class-name collisions in the exported framework: `../kmp/ios-interop.md`
- Dynamic feature modules and Play Feature Delivery: `../release/android-release.md`
