# Clean Architecture in a KMP Codebase

**Scope:** The dependency rule, domain-layer design, boundary mapping, where each type belongs.
**Applies to:** the shared module's layering; applicable to Android-only projects too.
**Official source for the pattern:** <https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html>
**Related official Android guidance:** <https://developer.android.com/topic/architecture>

**Rule levels:** see `../README.md`.

---

## Framing — MUST state accurately

**There is no Google or JetBrains specification of "Clean Architecture."** Google's official guidance is the UI / domain(optional) / data layering in `../android/app-architecture.md`.

**MUST NOT** tell a user that Clean Architecture is the Android-recommended architecture. The accurate statement:

- Google recommends UI / domain (optional) / data layers with dependencies pointing inward. [OFFICIAL]
- Clean Architecture is a broader, older formulation of the same dependency discipline. Many teams adopt its vocabulary — entities, use cases, interface adapters. [the pattern is OFFICIAL to its author; adopting it is DEFAULT]
- Google explicitly labels the domain layer **optional** and "recommended in big apps". **MUST NOT** present a mandatory use-case layer as an official requirement. [OFFICIAL]

---

## The original formulation

Concentric circles: outer = low-level mechanism, inner = high-level policy. Integrates Hexagonal, Onion and DCI.

**The Dependency Rule:** *"source code dependencies can only point inwards."* Inner circles know nothing of outer circles.

| Circle | Contents |
|---|---|
| **Entities** | Enterprise-wide business rules. Most stable layer. |
| **Use cases** | Application-specific rules. Orchestrate entities. Insulated from DB and UI changes. |
| **Interface adapters** | Convert between use-case format and external format. MVC/MVVM, DB queries, API mapping. |
| **Frameworks and drivers** | Database, HTTP client, UI toolkit, third-party SDKs. "Details", kept at arm's length. |

**Crossing boundaries against the flow of control** uses polymorphism and the Dependency Inversion Principle: the inner circle declares an interface it owns; the outer circle implements it.

**What crosses a boundary:** *"Simple data structures are passed across the boundaries."* Never an entity bound to a framework, never a framework type.

---

## Why this earns its keep in KMP specifically

Beyond design purity, there is a concrete payoff:

A `commonMain` domain module with **no platform dependencies** compiles for every target and runs in `commonTest` on the host JVM. That is the fastest test loop available in a KMP project — no emulator, no simulator, no device. Every platform dependency admitted into the domain module removes a target or a test environment.

This is the same conclusion `../kmp/project-structure.md` reaches from the `expect`/`actual` side: platform capabilities belong behind a domain-owned interface.

---

## Module shape

```
:domain          commonMain only. No Ktor, no Room, no AndroidX, no platform API.
                 Entities, value types, use cases, repository INTERFACES.
                     ▲
                     │  implements
:data            Repository implementations. Ktor, Room, DataStore, DTOs, mappers.
                     ▲
                     │  depends on
:ui / :feature:* Compose, ViewModels, UiState. Depends on :domain only.
```

**MUST:**

- `:domain` declares repository interfaces. `:data` implements them.
- `:ui` depends on `:domain`, never on `:data` implementations.
- `:domain` has **zero** dependencies on Ktor, Room, DataStore, AndroidX, Foundation, or any `expect`/`actual` that reaches a platform SDK directly.

**MUST NOT** define a repository interface in `:data` and import it from `:domain`. That inverts the dependency rule and makes the domain depend on the framework layer's release cycle.

```kotlin
// CORRECT — interface owned by the domain
// :domain/src/commonMain/kotlin/.../NewsRepository.kt
interface NewsRepository {
    fun observeFeed(): Flow<List<Article>>
    suspend fun refresh()
}

// :data/src/commonMain/kotlin/.../OfflineFirstNewsRepository.kt
internal class OfflineFirstNewsRepository(
    private val remote: NewsRemoteDataSource,
    private val dao: ArticleDao,
    private val ioDispatcher: CoroutineDispatcher,
) : NewsRepository {
    override fun observeFeed(): Flow<List<Article>> =
        dao.observeAll().map { entities -> entities.map(ArticleEntity::toDomain) }

    override suspend fun refresh() = withContext(ioDispatcher) {
        dao.upsertAll(remote.fetchFeed().map(ArticleDto::toEntity))
    }
}
```

```kotlin
// WRONG — the domain now depends on Room and on the DTO shape
// :domain/.../NewsRepository.kt
interface NewsRepository {
    fun observeFeed(): Flow<List<ArticleEntity>>   // Room entity in the domain API
    suspend fun refresh(): Response<ArticleDto>    // transport type in the domain API
}
```

Why the wrong form is a problem: adding a column to the Room entity now changes the domain API and every consumer; a change to the JSON contract propagates to the UI; and the domain module can no longer compile without Room and the serialization runtime, so it cannot be tested on the host without them.

---

## Entities and value types

**MUST** keep domain models free of serialization and persistence annotations.

```kotlin
// CORRECT — :domain
data class Article(
    val id: ArticleId,
    val title: String,
    val publishedAt: Instant,
    val isBookmarked: Boolean,
)

@JvmInline
value class ArticleId(val value: String)
```

```kotlin
// WRONG — one class serving as DTO, entity and domain model
@Serializable
@Entity
data class Article(
    @PrimaryKey @SerialName("article_id") val id: String,
    @SerialName("title") val title: String,
    val publishedAt: Long,
    @Transient val isBookmarked: Boolean = false,
)
```

Why the wrong form is a problem: the three roles have different change drivers. The API renames a field, so the database schema needs a migration. A column is added for a local-only flag, so it leaks into the JSON contract. `@Transient` and `@Ignore` accumulate until no reader can tell which fields are real. Google's own recommendation is a **model per layer** in complex apps.

**Version-sensitive note:** `kotlin.time.Instant` is stable in the standard library from Kotlin 2.3. **SHOULD** prefer it over `kotlinx-datetime` types (still pre-1.0) for instants in domain models, reserving `kotlinx-datetime` for calendar and time-zone work. See `../kotlin/coding-conventions.md`.

**MUST NOT** use `@JvmInline`-only constructs in the exported iOS surface — inline value classes map to the underlying primitive in Objective-C export, so the type safety disappears at the boundary. See `../kmp/ios-interop.md`. They remain correct *inside* the shared module.

---

## Use cases — SHOULD, with a size test

Google labels the domain layer **optional** and justified by complexity or reuse. Apply that test before adding one.

**SHOULD** add a use case when it:

- contains policy that more than one ViewModel needs, or
- orchestrates two or more repositories, or
- holds a non-trivial rule that deserves its own test.

**SHOULD NOT** add a use case that forwards a single repository call.

```kotlin
// CORRECT — real orchestration, inherits the caller's lifecycle
class GetFeedWithAuthorsUseCase(
    private val news: NewsRepository,
    private val authors: AuthorRepository,
) {
    suspend operator fun invoke(): List<ArticleWithAuthor> = coroutineScope {
        val articles = async { news.latest() }
        val byId = async { authors.all().associateBy(Author::id) }
        articles.await().map { ArticleWithAuthor(it, byId.await()[it.authorId]) }
    }
}

// CORRECT — real policy worth its own test
class CanAccessPremiumContentUseCase(
    private val entitlements: EntitlementRepository,
) {
    operator fun invoke(): Flow<Boolean> =
        entitlements.observe().map { it.isActive && !it.isInGracePeriodExpired }
}

// WRONG — a layer with no policy in it
class GetArticlesUseCase(private val repository: NewsRepository) {
    suspend operator fun invoke(): List<Article> = repository.latest()
}
```

Why the wrong form is a problem: it adds a file, a DI binding, a constructor parameter and a test per repository method, and the only behaviour it can be tested for is delegation. It also obscures the real use cases by burying them among forwarders.

**SHOULD** use `operator fun invoke` so call sites read as `getFeedWithAuthors()`. [DEFAULT]

**MUST** follow the scope rules in `../kotlin/coroutines-and-flow.md`: a use case that launches parallel work uses `coroutineScope`/`supervisorScope` inside a `suspend fun`, never an injected scope, so cancellation follows the caller.

---

## Boundary mapping — MUST

Map at every boundary crossing. One mapper per direction, placed in the outer layer.

```
ArticleDto ──toEntity()──► ArticleEntity ──toDomain()──► Article ──toUiState()──► ArticleUiState
   :data                      :data                      :domain                   :ui
```

**MUST** place each mapper in the **outer** layer of the pair, so the inner layer stays unaware of the outer type.

```kotlin
// CORRECT — mapper lives in :data, which already knows both types
// :data/.../ArticleMappers.kt
internal fun ArticleDto.toEntity() = ArticleEntity(
    id = articleId,
    title = title,
    publishedAtEpochMs = publishedAt,
)

internal fun ArticleEntity.toDomain() = Article(
    id = ArticleId(id),
    title = title,
    publishedAt = Instant.fromEpochMilliseconds(publishedAtEpochMs),
    isBookmarked = isBookmarked,
)
```

```kotlin
// WRONG — mapper in :domain, so :domain must see the DTO
// :domain/.../ArticleMappers.kt
fun ArticleDto.toDomain() = Article(...)   // :domain now depends on :data
```

**MUST** make mappers `internal` unless another module genuinely needs them. A `public` mapper re-exports the type it was meant to hide.

**SHOULD** keep mappers as pure extension functions with no injected dependencies, so they are testable with no setup.

---

## Platform capabilities — MUST invert

Any platform capability — secure storage, biometrics, file paths, connectivity, notifications, clipboard — **MUST** be reached through an interface declared in `:domain`, implemented in the outer layer.

```kotlin
// CORRECT
// :domain
interface SecureStore {
    suspend fun put(key: String, value: String)
    suspend fun get(key: String): String?
}

// :data (androidMain) — Keystore-backed
// :data (iosMain)     — Keychain-backed
```

```kotlin
// WRONG — the domain reaches a platform API via expect/actual
// :domain/commonMain
expect suspend fun secureStorePut(key: String, value: String)
```

Why the wrong form is a problem: `:domain` now requires an `actual` for every declared target, cannot be unit-tested without one, and gains a new compile error whenever a target is added. The interface form has a trivial in-memory fake and needs no `actual` at all. This is the same rule the official KMP documentation states as "prefer interfaces and dependency injection over expected/actual classes" — see `../kmp/project-structure.md`.

Concrete platform split for secure storage: `../quality/security.md`.

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| Repository interface defined in `:data` | dependency rule inverted; domain tied to the framework layer |
| Room entity or Ktor DTO in a domain or UI signature | schema and transport changes propagate to the UI; domain cannot compile without the frameworks |
| One class as DTO + entity + domain model | three change drivers in one file; annotation sprawl |
| A use case per repository method | a layer with no policy; obscures the real use cases |
| `public` mappers | re-exports the type the mapping was meant to hide |
| `expect`/`actual` in `:domain` reaching a platform SDK | domain untestable and target-coupled |
| Four mandatory modules on a three-screen app | Google explicitly warns against over-modularization — `modularization.md` |
| `:ui` depending on `:data` to "just read one thing" | the repository boundary stops being enforceable |

---

## Android / iOS differences

The layering itself is the portable part — it is identical on both platforms. What differs:

| Concern | Android | iOS |
|---|---|---|
| `:domain` | ordinary Gradle dependency | compiled into the framework; **MUST** respect export constraints if domain types cross to Swift |
| Value classes in the domain | full type safety | map to the underlying primitive in Objective-C export |
| Generic repository interfaces | fine | **not exportable** — generics are unsupported on exported interfaces |
| `Flow` from a repository | the normal API | **MUST NOT** cross to Swift directly on the Objective-C path |
| Domain exceptions | ordinary | **MUST** be listed in `@Throws` at the exported boundary or the app terminates |

**SHOULD** keep the Swift-facing surface as a dedicated facade in `iosMain` rather than exporting `:domain` wholesale, so the export constraints shape one small file instead of the whole domain. See `../kmp/ios-interop.md`.

---

## Testing recommendations

The domain layer is the highest-value test tier in a KMP codebase: pure functions over injected interfaces, in `commonTest`, with no platform, no emulator and no mocking framework.

```kotlin
// commonTest — no framework, no mocks, no dispatcher games
class CanAccessPremiumContentUseCaseTest {

    private val entitlements = FakeEntitlementRepository()
    private val useCase = CanAccessPremiumContentUseCase(entitlements)

    @Test
    fun grantsAccessWhenActive() = runTest {
        entitlements.emit(Entitlement(isActive = true, isInGracePeriodExpired = false))
        assertTrue(useCase().first())
    }

    @Test
    fun deniesAccessWhenGraceExpired() = runTest {
        entitlements.emit(Entitlement(isActive = true, isInGracePeriodExpired = true))
        assertFalse(useCase().first())
    }
}
```

**MUST:**

- Put domain and use-case tests in `commonTest` with `kotlin-test` only.
- Hand-write fakes for repository interfaces. **MUST NOT** use a mocking framework in `commonTest` — most are JVM-only and will not compile for native, and Google's guidance is fakes over mocks regardless.
- Test mappers with real captured payloads, round-tripping where the mapping is bidirectional.

**SHOULD** treat "can this be tested in `commonTest` with no platform dependency?" as the acceptance test for whether a type is in the right layer. If a domain type needs an emulator to test, it is in the wrong module.

Full strategy: `../quality/testing-strategy.md`.

---

## Cross-references

- Google's official layering and the recommendation set: `../android/app-architecture.md`
- UI state modelling and state holders: `mvvm-udf.md`
- Module taxonomy, granularity, dependency direction: `modularization.md`
- `expect`/`actual` vs interfaces: `../kmp/project-structure.md`
- Scope and cancellation rules for use cases: `../kotlin/coroutines-and-flow.md`
- Export constraints on domain types: `../kmp/ios-interop.md`
- Repository implementation details: `../libraries/ktor-networking.md`, `../libraries/room-datastore.md`
