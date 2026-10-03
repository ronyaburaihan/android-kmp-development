# Ktor Client Networking

**Scope:** Client setup, engine selection, plugin configuration, DTO design and mapping, error handling, platform constraints.
**Applies to:** `commonMain` networking in KMP; the Retrofit comparison applies to Android-only modules.
**Official sources:**
- <https://ktor.io/docs/client-create-multiplatform-application.html>
- <https://ktor.io/docs/whats-new-340.html>
- <https://github.com/ktorio/ktor/releases>

**Rule levels:** see `../README.md`.

---

## Platform decision — MUST

| Context | Choice |
|---|---|
| `commonMain` in a KMP project | **Ktor Client** |
| Android-only module | Ktor **or** Retrofit + OkHttp — both valid |
| A shared repository interface with an Android-only implementation | **MAY** keep Retrofit behind the interface, but running two HTTP stacks is a cost |

**Retrofit cannot be used in `commonMain`.** It is JVM-bound and will not compile for native targets. Retrofit 3.0 is Kotlin-first but that does not change the constraint. **MUST NOT** place a Retrofit interface in `commonMain`.

> **[UNVERIFIED]** A Kotlinlang Slack thread states OkHttp 5 dropped its own Kotlin Multiplatform support. Not confirmed in Square's changelog. Irrelevant to most projects (Ktor is the KMP answer) but **MUST NOT** be stated as fact.

---

## Engines

| Target | Engine artifact | Backing implementation |
|---|---|---|
| Android | `ktor-client-okhttp` | OkHttp |
| iOS / Apple | `ktor-client-darwin` | `NSURLSession` |
| JVM desktop | `ktor-client-okhttp` or `ktor-client-cio` | OkHttp / Ktor coroutine I/O |
| Any Kotlin target | `ktor-client-cio` | Ktor's own engine |
| JS / Wasm | `ktor-client-js` | Fetch |
| Tests | `ktor-client-mock` | in-memory |

As of Ktor **3.4.0**, native engines (Curl, Darwin, WinHttp) respect the configured engine dispatcher and default to `Dispatchers.IO`. [OFFICIAL]

---

## Setup

```toml
# gradle/libs.versions.toml
[versions]
ktor = "3.6.0"

[libraries]
ktor-client-core           = { module = "io.ktor:ktor-client-core",                 version.ref = "ktor" }
ktor-client-okhttp         = { module = "io.ktor:ktor-client-okhttp",               version.ref = "ktor" }
ktor-client-darwin         = { module = "io.ktor:ktor-client-darwin",               version.ref = "ktor" }
ktor-client-mock           = { module = "io.ktor:ktor-client-mock",                 version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json    = { module = "io.ktor:ktor-serialization-kotlinx-json",  version.ref = "ktor" }
ktor-client-logging        = { module = "io.ktor:ktor-client-logging",              version.ref = "ktor" }
ktor-client-auth           = { module = "io.ktor:ktor-client-auth",                 version.ref = "ktor" }
```

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)
        }
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies     { implementation(libs.ktor.client.darwin) }
        commonTest.dependencies  { implementation(libs.ktor.client.mock) }
    }
}
```

---

## Client construction — MUST

**MUST** create exactly **one** `HttpClient` per application and inject it. The client owns a connection pool and a coroutine scope.

**MUST** configure the client in `commonMain` and inject the **engine** from the platform source set, so tests can inject `MockEngine` without touching the configuration.

```kotlin
// commonMain — one configured client, engine supplied by the caller
fun createHttpClient(
    engine: HttpClientEngine,
    tokenProvider: TokenProvider,
    isDebug: Boolean,
): HttpClient = HttpClient(engine) {
    expectSuccess = true

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        )
    }

    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
    }

    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 3)
        exponentialDelay()
    }

    install(Auth) {
        bearer {
            loadTokens { tokenProvider.current()?.let { BearerTokens(it.access, it.refresh) } }
            refreshTokens { tokenProvider.refresh()?.let { BearerTokens(it.access, it.refresh) } }
        }
    }

    if (isDebug) {
        install(Logging) { level = LogLevel.HEADERS }
    }

    defaultRequest {
        url(BASE_URL)
        contentType(ContentType.Application.Json)
    }
}
```

```kotlin
// Koin wiring — see koin-di.md
val networkModule = module {
    single { createHttpClient(get(), get(), isDebug = BuildConfigFlags.isDebug) }
}
// androidMain platformModule(): single<HttpClientEngine> { OkHttp.create() }
// iosMain     platformModule(): single<HttpClientEngine> { Darwin.create() }
```

```kotlin
// WRONG — a new client per call
class NewsRemoteDataSource {
    suspend fun fetchFeed(): List<ArticleDto> =
        HttpClient().get("$BASE_URL/feed").body()
}
```

Why the wrong form is a problem: each `HttpClient` allocates a connection pool, a thread pool (on OkHttp) and a coroutine scope, and none of them are closed. Under list scrolling this exhausts file descriptors and sockets. There is also no shared auth state, so every request re-authenticates.

```kotlin
// WRONG — LogLevel.ALL unconditionally
install(Logging) { level = LogLevel.ALL }
```

Why: `LogLevel.ALL` logs request and response **bodies and headers**, which means bearer tokens, session cookies and personal data land in logcat and in any log collector. **MUST** gate logging on a debug flag and **MUST NOT** use `LogLevel.ALL` or `LogLevel.BODY` in a release build. See `../quality/security.md`.

---

## DTOs and serialization

**MUST** define transport types separately from domain models, and map at the boundary. See `../architecture/clean-architecture.md`.

```kotlin
// CORRECT — :data, internal, transport-shaped
@Serializable
internal data class ArticleDto(
    @SerialName("article_id") val articleId: String,
    @SerialName("title") val title: String,
    @SerialName("published_at") val publishedAtEpochMs: Long,
)

internal fun ArticleDto.toDomain() = Article(
    id = ArticleId(articleId),
    title = title,
    publishedAt = Instant.fromEpochMilliseconds(publishedAtEpochMs),
)
```

```kotlin
// WRONG — the wire format is the domain model
@Serializable
data class Article(
    @SerialName("article_id") val id: String,
    @SerialName("published_at") val publishedAt: Long,
)
```

Why the wrong form is a problem: a field rename on the server changes the domain type and every consumer; the UI ends up formatting epoch millis; and the domain module cannot compile without the serialization runtime, so it is no longer testable in isolation.

**MUST** set `ignoreUnknownKeys = true`. A server adding a field **MUST NOT** break the client.

**SHOULD** set `explicitNulls = false` when the API omits null fields rather than sending `null`.

**MUST NOT** use `@Transient` on a DTO field to carry local state. Local state belongs on the entity or domain model.

---

## Data source and repository shape

```kotlin
// CORRECT — :data
internal class NewsRemoteDataSource(private val client: HttpClient) {
    suspend fun fetchFeed(page: Int): List<ArticleDto> =
        client.get("feed") { parameter("page", page) }.body()
}

internal class OfflineFirstNewsRepository(
    private val remote: NewsRemoteDataSource,
    private val dao: ArticleDao,
    private val ioDispatcher: CoroutineDispatcher,
) : NewsRepository {

    override fun observeFeed(): Flow<List<Article>> =
        dao.observeAll().map { it.map(ArticleEntity::toDomain) }

    override suspend fun refresh(): Unit = withContext(ioDispatcher) {
        dao.upsertAll(remote.fetchFeed(page = 0).map(ArticleDto::toEntity))
    }
}
```

**MUST:**

- The repository returns domain types only. **MUST NOT** return `HttpResponse`, a DTO, or a Ktor exception type.
- `suspend` for one-shot calls, `Flow` for streams. See `../kotlin/coroutines-and-flow.md`.
- Suspend functions are main-safe — the `withContext(ioDispatcher)` is inside, not at the call site.

---

## Error handling — MUST

With `expectSuccess = true`, non-2xx responses throw `ClientRequestException` (4xx), `ServerResponseException` (5xx) or `RedirectResponseException`. Transport failures throw `IOException` and friends; timeouts throw `HttpRequestTimeoutException`.

**MUST** translate Ktor exceptions into a domain error type at the data-layer boundary.

```kotlin
// CORRECT — one translation point, domain-typed errors
internal suspend fun <T> networkCall(block: suspend () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ClientRequestException) {
        throw when (e.response.status) {
            HttpStatusCode.Unauthorized -> AuthException(e)
            HttpStatusCode.NotFound -> NotFoundException(e)
            else -> ApiException(e.response.status.value, e)
        }
    } catch (e: ServerResponseException) {
        throw ServerException(e.response.status.value, e)
    } catch (e: HttpRequestTimeoutException) {
        throw NetworkTimeoutException(e)
    } catch (e: IOException) {
        throw NoConnectivityException(e)
    }
```

```kotlin
// WRONG — swallows cancellation and loses all error detail
suspend fun fetchFeed(): List<Article> = try {
    remote.fetchFeed().map { it.toDomain() }
} catch (e: Exception) {
    emptyList()
}
```

Why the wrong form is a problem: catching `Exception` captures `CancellationException`, which breaks structured concurrency (see `../kotlin/coroutines-and-flow.md`). Returning an empty list makes "no articles" and "network is down" indistinguishable, so the UI shows an empty state instead of a retry affordance, and the failure never reaches crash reporting.

**MUST NOT** catch `Exception` or `Throwable` around a request without rethrowing `CancellationException`.

---

## Platform constraints — MUST handle

### Permissions and transport security

| Platform | Requirement |
|---|---|
| Android | `<uses-permission android:name="android.permission.INTERNET" />` **MUST** be declared |
| Android | Cleartext HTTP is blocked by default via network security config. **MUST NOT** enable `cleartextTrafficPermitted="true"` outside `<debug-overrides>` |
| iOS | App Transport Security blocks plain HTTP unless exempted in `Info.plist`. **MUST NOT** add a blanket `NSAllowsArbitraryLoads` exemption |

Full rules: `../quality/security.md`, `../android/platform-requirements.md`.

### Engine behaviour differences — MUST NOT assume parity

OkHttp and `NSURLSession` differ in redirect handling, timeout semantics, proxy behaviour and TLS trust configuration. **MUST** verify any behaviour that matters on both engines rather than assuming the Ktor abstraction hides it.

### Certificate pinning

**[UNVERIFIED]** Pinning is configured per engine (OkHttp's `CertificatePinner` vs a Darwin trust-evaluation callback), so a pinning requirement means two platform implementations. The exact current Ktor API for each was not established. **MUST** read the engine-specific Ktor documentation before implementing, and **MUST** define a certificate-rotation plan before pinning anything — a pinned certificate that expires bricks every installed client.

### Background transfers

**[UNVERIFIED]** Long-running or background transfers are platform work that Ktor does not abstract: Android uses WorkManager, iOS uses an `NSURLSession` background configuration. **MUST NOT** assume a shared abstraction exists.

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| `HttpClient()` per call | socket and descriptor exhaustion; no shared auth |
| `LogLevel.ALL` / `LogLevel.BODY` in release | tokens and PII in logs |
| Retrofit interface in `commonMain` | does not compile for native |
| DTO used as the domain model | transport changes propagate to the UI |
| Missing `ignoreUnknownKeys` | a new server field breaks the client |
| `catch (e: Exception)` without rethrowing `CancellationException` | breaks structured concurrency |
| Returning an empty list on failure | error states indistinguishable from empty states |
| `HttpResponse` or a DTO returned from a repository | leaks the transport layer upward |
| Hardcoded engine in `commonMain` | cannot inject `MockEngine`; cannot vary per platform |
| Pinning without a rotation plan | expiry bricks installed clients |
| Blanket iOS ATS exemption | disables transport security app-wide |

---

## Testing recommendations

### `MockEngine` in `commonTest` — MUST

```kotlin
// commonTest — runs on every target, no network
class NewsRemoteDataSourceTest {

    private fun dataSource(handler: MockRequestHandler): NewsRemoteDataSource {
        val engine = MockEngine(handler)
        val client = createHttpClient(engine, FakeTokenProvider(), isDebug = false)
        return NewsRemoteDataSource(client)
    }

    @Test
    fun parsesFeedResponse() = runTest {
        val source = dataSource { request ->
            assertEquals("/feed", request.url.encodedPath)
            respond(
                content = """[{"article_id":"a1","title":"T","published_at":1700000000000}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = source.fetchFeed(page = 0)

        assertEquals(1, result.size)
        assertEquals("a1", result.single().articleId)
    }

    @Test
    fun mapsUnauthorizedToAuthException() = runTest {
        val source = dataSource { respondError(HttpStatusCode.Unauthorized) }
        assertFailsWith<AuthException> { source.fetchFeed(page = 0) }
    }
}
```

**MUST:**

- Put HTTP tests in `commonTest` with `MockEngine`. **MUST NOT** hit a real network in a unit test.
- Assert on the **request** as well as the response — path, query parameters, headers — or a broken URL passes silently.
- Test the error-translation paths, not only the happy path. Error mapping is where most networking bugs live.

**SHOULD:**

- Keep real captured payloads as test fixtures and assert the DTO parses them, so a server contract change is caught by a failing test rather than a crash in production.
- Run contract or integration tests against a staging host in a **separate, non-blocking** CI job. They are useful and they are flaky; they **MUST NOT** gate a merge.
- Verify the release client configuration in a test: `expectSuccess`, timeouts set, logging disabled.

Full strategy: `../quality/testing-strategy.md`.

---

## Cross-references

- Repository and DTO/domain separation: `../architecture/clean-architecture.md`
- `HttpClient` as a `single`, engine as a platform binding: `koin-di.md`
- Main-safety, cancellation, `CancellationException`: `../kotlin/coroutines-and-flow.md`
- Transport security, cleartext, pinning context: `../quality/security.md`
- Caching layer that pairs with `refresh()`: `room-datastore.md`
- Versions: `../version-matrix.md`
