# Workflow: Add or Change a Network Endpoint

Standing contract: `README.md`. Prerequisite: `inspect-project.md`.

---

## 1. Objective

Add a remote API call, or change an existing one, through the data layer: DTO, data-source method, error translation, repository exposure, tests.

Scope ends at a repository method returning domain types. UI consumption belongs to `add-feature.md`.

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| HTTP method and path | yes | **MUST** ask. |
| Request shape — path/query params, headers, body | yes | **MUST** ask. |
| Response shape — a real example payload | yes | **MUST** ask. **MUST NOT** guess field names or nullability from a prose description. |
| Error responses — status codes and bodies | no | Default: translate by status code only. **MUST** state the assumption. |
| Auth requirement | no | Default: the same as comparable endpoints. |
| Pagination | no | Default: none. **MUST** ask if the response contains a cursor or page field. |
| Is this replacing an existing call? | yes | **MUST** ask — determines whether the old path is removed. |

**MUST** obtain a real sample payload. Field nullability and casing are not inferable, and a wrong guess produces a runtime deserialization failure that no compile check catches.

---

## 3. Initial project inspection

Run `inspect-project.md`, then:

```bash
# the HTTP client and its configuration
grep -rn 'HttpClient(\|Retrofit.Builder\|install(ContentNegotiation' --include='*.kt' . | grep -v build/
# existing data sources and DTOs
find . -name '*RemoteDataSource*.kt' -o -name '*ApiService*.kt' -o -name '*Api.kt' -not -path '*/build/*'
find . -name '*Dto.kt' -o -name '*Response.kt' -not -path '*/build/*' | head -20
```

**MUST** read one complete existing endpoint implementation end to end and record:

| Question | Why |
|---|---|
| Ktor or Retrofit? | Determines the whole implementation shape |
| Is the client a singleton, injected? Where is it configured? | New calls reuse it; **MUST NOT** create a second client |
| `expectSuccess` set? `ignoreUnknownKeys` set? | Determines whether errors throw, and whether unknown fields break parsing |
| DTO naming and `@SerialName` usage | Match it |
| Is there a shared envelope/wrapper type? | New DTOs may need to nest inside it |
| Error translation: where, and into what domain types? | Reuse the existing translator |
| Does the data source return DTOs or domain models? | Match the layer boundary in use |
| Base URL / `defaultRequest` configuration | Paths are usually relative |
| How are tests written — `MockEngine`, MockWebServer, or none? | Match it |

**MUST** record the exact file that will be extended. **MUST NOT** create a new data-source class when an existing one covers the same API area.

---

## 4. Step-by-step procedure

### 4.1 Confirm the plan — MUST

Present and wait for confirmation:

- the DTO field list, with Kotlin types and nullability, derived from the sample payload
- the data-source method signature
- the repository method signature and its domain return type
- the error cases translated and their domain types
- whether an existing call is being removed (→ D4)

### 4.2 Define the DTO

**MUST:**

- place it in the data layer, `internal`, matching the project's DTO naming
- mark nullable every field the sample payload or the API contract allows to be absent — **MUST NOT** mark a field non-null because the sample happened to include it
- use the project's `@SerialName` convention
- keep it transport-shaped; no domain logic, no computed properties

```kotlin
// CORRECT — transport-shaped, internal, nullability from the contract
@Serializable
internal data class ArticleDto(
    @SerialName("article_id") val articleId: String,
    @SerialName("title") val title: String,
    @SerialName("summary") val summary: String?,          // documented as optional
    @SerialName("published_at") val publishedAtEpochMs: Long,
)
```

```kotlin
// WRONG — doubles as the domain model and the persistence entity
@Serializable
@Entity
data class Article(
    @SerialName("article_id") @PrimaryKey val id: String,
    val summary: String,                                   // actually nullable
)
```

The wrong form breaks in two ways: a missing `summary` throws at parse time in production, and a server field rename now changes the domain type, the database schema, and every consumer.

**MUST NOT** add a DTO→domain mapper in the domain module. The mapper lives in the data layer. See `../references/architecture/clean-architecture.md`.

### 4.3 Add the data-source method

**MUST** extend the existing data source. Match its signature style and its dispatcher handling.

```kotlin
// CORRECT — Ktor, relative path, reuses the injected client
internal class NewsRemoteDataSource(private val client: HttpClient) {
    suspend fun fetchFeed(page: Int): List<ArticleDto> =
        client.get("feed") { parameter("page", page) }.body()
}
```

```kotlin
// WRONG — new client per call
internal class NewsRemoteDataSource {
    suspend fun fetchFeed(page: Int): List<ArticleDto> =
        HttpClient().get("https://api.example.com/feed").body()
}
```

The wrong form allocates an unclosed connection pool per call, shares no auth state, and cannot be tested with `MockEngine`.

See `../references/libraries/ktor-networking.md`.

### 4.4 Error translation

**MUST** route the call through the project's existing error translator. **MUST NOT** add a second translation strategy.

If no translator exists → **D2**.

**MUST:**
- rethrow `CancellationException` before any other catch
- map to domain error types, not pass Ktor/Retrofit exceptions upward
- **MUST NOT** return an empty list, `null`, or a default value to signal failure

### 4.5 Expose it on the repository

**MUST** add the method to the existing repository interface and implementation for this data domain. The interface is owned by whichever layer owns it in this project — do not change that.

**MUST** return domain types. `suspend` for one-shot, `Flow` for streams.

If the endpoint feeds a cache, **MUST** follow the project's existing read-through/write-through pattern rather than inventing one. If the caching policy is new → **D3**.

### 4.6 Tests

**MUST** add, matching the project's test style:

| Test | Asserts |
|---|---|
| Parses the real sample payload | every field maps; use the payload from section 2 verbatim |
| Request is correct | path, query params, headers, body |
| Optional fields absent | parses without throwing |
| Unknown field present | parses without throwing (guards `ignoreUnknownKeys`) |
| Each translated error status | the expected domain exception |
| Repository method | returns domain types; propagates the domain error |

```kotlin
// CORRECT — commonTest, MockEngine, asserts request and response
@Test
fun fetchFeedParsesPayloadAndSendsPage() = runTest {
    var observedPath: String? = null
    var observedPage: String? = null
    val engine = MockEngine { request ->
        observedPath = request.url.encodedPath
        observedPage = request.url.parameters["page"]
        respond(
            content = SAMPLE_FEED_JSON,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
    val source = NewsRemoteDataSource(createHttpClient(engine, FakeTokenProvider(), isDebug = false))

    val result = source.fetchFeed(page = 2)

    assertEquals("/feed", observedPath)
    assertEquals("2", observedPage)
    assertEquals("a1", result.single().articleId)
}
```

**MUST NOT** hit a real network in a test that gates a merge.

### 4.7 Validate

Run section 7.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | No data source exists for this **API area** | (a) new data source matching convention; (b) extend the nearest existing one | **SHOULD** choose (a) when the API area is distinct. **MUST** ask if it is borderline. |
| D2 | **No error-translation layer** exists; errors propagate raw | (a) add a translator for this endpoint only; (b) add a shared translator (wider change); (c) match the existing raw propagation | **MUST** ask. Recommend (a) — scoped, and does not restructure existing calls. |
| D3 | The endpoint needs a **caching policy** the project has no precedent for | (a) network-only; (b) read-through cache via `add-persistence.md` | **MUST** ask. Recommend (a) unless offline support was requested. |
| D4 | This **replaces** an existing call | (a) remove the old path now; (b) leave it and mark deprecated | **MUST** ask. **MUST NOT** delete a call that other code still uses without confirming no caller remains. |
| D5 | Response contains **pagination** metadata not in the input spec | (a) expose paging; (b) fetch the first page only | **MUST** ask. **MUST NOT** silently drop a cursor. |
| D6 | Endpoint needs **auth** the client is not configured for | — | **MUST** stop. Auth configuration is a shared client change affecting every call. |
| D7 | Project uses **Retrofit** and the call must be in `commonMain` | (a) implement with Ktor in `commonMain`; (b) keep it Android-only behind the repository interface | **MUST** ask. Retrofit cannot compile for native. See `../references/libraries/ktor-networking.md`. |
| D8 | Endpoint requires **certificate pinning** or a non-default TLS trust | — | **MUST** stop. Per-engine work plus a rotation plan. See `../references/quality/security.md`. |

---

## 6. Implementation rules

**MUST:**

1. Reuse the single injected `HttpClient` / Retrofit instance.
2. Keep DTOs, mappers, and data sources `internal` to the data module.
3. Derive nullability from the API contract, not from the sample's happen-to-be-present fields.
4. Keep the endpoint path relative if the project configures a base URL.
5. Add the sample payload as a test fixture, verbatim.
6. Rethrow `CancellationException` ahead of every other catch clause.

**MUST NOT:**

7. Construct an HTTP client inside a data source.
8. Put a DTO, `HttpResponse`, or transport exception in a repository signature.
9. Add `LogLevel.ALL` or `LogLevel.BODY` logging — it writes tokens and PII to logs. See `../references/quality/security.md`.
10. Change the shared client configuration (timeouts, retry, auth, serializer settings) as a side effect. That affects every call → ask.
11. Set `ignoreUnknownKeys = false`, or remove it if present.
12. Catch `Exception`/`Throwable` without rethrowing `CancellationException`.
13. Return an empty collection or a default value on failure.
14. Add a Retrofit interface to `commonMain`.
15. Hardcode a base URL, API key, or token in source.

---

## 7. Validation requirements

| # | Check | Command | Required |
|---|---|---|---|
| V1 | Compiles — all targets | `./gradlew :<module>:compileDebugKotlin` (+ KMP metadata/native) | MUST |
| V2 | Parse test against the real sample payload | `./gradlew :<module>:allTests --tests '*<Name>*'` | MUST |
| V3 | Request assertions pass | same | MUST |
| V4 | Error-translation tests pass | same | MUST |
| V5 | Existing network tests still pass | whole-module test task | MUST |
| V6 | Unknown-field tolerance | test from 4.6 | MUST |
| V7 | Absent-optional-field tolerance | test from 4.6 | MUST |
| V8 | No credential or URL literal added | `grep` the diff for `http`, `Bearer`, key-shaped literals | MUST |
| V9 | Lint / format clean | project task | MUST if configured |
| V10 | Live call against staging succeeds | manual or a non-gating integration job | SHOULD |

---

## 8. Failure handling

| Failure | Response |
|---|---|
| Deserialization fails on the sample payload | **MUST** fix the DTO, not the payload. Common causes: wrong nullability, wrong `@SerialName`, a wrapper envelope not modelled. |
| Works in the test, fails against the real API | The sample payload is unrepresentative. **MUST** obtain a real capture and re-derive the DTO. **MUST NOT** add `?` to every field to make it pass. |
| Unknown-field failure | `ignoreUnknownKeys` is not set on the project's `Json`. That is a shared client setting → ask before changing it. |
| 401 in testing | **MUST NOT** work around it by disabling auth or pinning. Confirm the auth requirement; may be D6. |
| Native target fails to compile | A JVM-only serialization or HTTP API reached `commonMain`. **MUST** fix by relocating the code, not by dropping the target. |
| Existing network test breaks | **MUST** stop. Likely a shared client configuration change. **MUST NOT** modify or skip the existing test. |
| Endpoint not yet implemented server-side | **MUST** stop and ask. **MUST NOT** ship a client against a guessed contract. |
| API returns a different shape per status code | **MUST** model each shape explicitly. **MUST NOT** make every field nullable to cover both. |
| Timeout under real conditions | Report it. **MUST NOT** raise the shared client timeout unilaterally — it affects every call. |

---

## 9. Completion criteria

**MUST** all hold:

1. DTO matches the real sample payload, with nullability from the contract.
2. Data-source method added to the existing data source, using the injected client.
3. Errors translated into domain types; `CancellationException` rethrown.
4. Repository exposes domain types only.
5. Tests from 4.6 added and passing, using the verbatim sample payload.
6. V1–V9 `PASS` or explicitly `NOT RUN` with a reason.
7. Shared client configuration unchanged, or changed only with approval.
8. No existing test modified or skipped.
9. If replacing a call, the old path handled per D4 with no orphaned callers.
10. No credential, token, or absolute URL added to source.

---

## 10. Final report format

Base skeleton from `README.md`, with these additions.

```markdown
## Add Network Endpoint — <METHOD /path>

### Outcome
<DONE | DONE WITH CAVEATS | BLOCKED | NEEDS DECISION> — one sentence.

### Inspection findings
- HTTP stack: <Ktor <version> | Retrofit + OkHttp>
- Client: singleton at `path:line`; `expectSuccess`=<y/n>, `ignoreUnknownKeys`=<y/n>
- Data source extended: `path`
- Error translator: `path:line` | none (D2)
- Pattern source: `path` (existing endpoint mirrored)
- Test style: <MockEngine | MockWebServer | none>

### Endpoint summary
| | |
|---|---|
| Method / path | |
| Auth | |
| Request params | |
| Response type | |
| Pagination | |
| Caching | |
| Replaces | <existing call or none> |

### DTO
| Field | Kotlin type | `@SerialName` | Nullable — source of truth |
|---|---|---|---|

### Error mapping
| Condition | Domain type |
|---|---|

### Changes made
| File | Change |
|---|---|

### Conventions followed
<Matched patterns with the source file. Deliberate deviations from `../references/`
with the reason.>

### Validation performed
| # | Check | Command | Result |
|---|---|---|---|
| V1 | Compile | | |
| V2 | Sample payload parses | | |
| V3 | Request assertions | | |
| V4 | Error translation | | |
| V5 | Existing network tests | | |
| V6 | Unknown field tolerated | | |
| V7 | Absent optional tolerated | | |
| V8 | No credentials/URLs added | | |
| V9 | Lint / format | | |
| V10 | Live staging call | | |

### Tests added
| Test | Covers |
|---|---|

### Not done
### Observations
### Decisions needed
```
