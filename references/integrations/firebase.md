# Firebase on KMP

**Scope:** The three integration approaches, their trade-offs, per-service platform differences, Crashlytics symbolication.
**Applies to:** KMP projects needing Firebase. Android-only projects use the official Firebase Android SDK directly and need none of this.
**Official sources:**
- <https://firebase.google.com/docs> (official native SDKs)
- <https://developer.android.com/kotlin/multiplatform> (AndroidX KMP support table — Firebase is not on it)

**Rule levels:** see `../README.md`.

---

## The honest position — MUST state accurately

Firebase ships **official native SDKs** for Android, iOS, Web, Flutter and Unity. **There is no official Google/Firebase SDK for Kotlin Multiplatform.**

> **[UNVERIFIED]** Searches of `firebase.google.com` surfaced no KMP page, and a secondary source reports the request "still has not landed on Firebase's public feature board" as of mid-2026. **MUST** confirm directly on `firebase.google.com/docs` before telling a user that official KMP support does not exist. Absence of evidence in a search is weaker than a positive statement.

**MUST NOT** describe any third-party Firebase KMP library as "official" or "officially supported."

---

## Three approaches

| # | Approach | Verdict |
|---|---|---|
| **A** | Official native SDKs behind a domain-owned interface, with `expect`/`actual` or DI | **SHOULD be the default.** |
| **B** | GitLive `firebase-kotlin-sdk` (`dev.gitlive`, 2.7.0) — community Kotlin-first wrapper | **MAY**, with the maintenance risk recorded. |
| **C** | "KFire" | **MUST NOT adopt yet.** See below. |

### Approach C — why it is excluded

The only sources found for "KFire" are blog posts, one of which calls it "officially-structured" — a phrase that does **not** mean official Google support. Its provenance, maintainer, license and maturity were not established; one source describes it as "in beta".

**MUST NOT** add it to a project until provenance, maintainer and license are verified. **MUST NOT** repeat the "officially-structured" framing.

---

## Approach A — native SDKs behind an interface (default)

**MUST** declare the capability in the domain layer and keep both Firebase SDKs in platform source sets. This is the same inversion as every other platform capability — see `../architecture/clean-architecture.md`.

```kotlin
// :domain/commonMain — no Firebase types
interface AnalyticsClient {
    fun logEvent(name: String, params: Map<String, String> = emptyMap())
    fun setUserProperty(name: String, value: String?)
}

interface CrashReporter {
    fun recordNonFatal(throwable: Throwable)
    fun setUserId(id: String?)
    fun log(message: String)
}

interface RemoteConfigClient {
    suspend fun refresh()
    fun booleanOf(key: String, default: Boolean): Boolean
}
```

```kotlin
// :data/androidMain
internal class FirebaseAnalyticsClient(
    private val analytics: FirebaseAnalytics,
) : AnalyticsClient {
    override fun logEvent(name: String, params: Map<String, String>) {
        analytics.logEvent(name, Bundle().apply { params.forEach { (k, v) -> putString(k, v) } })
    }
    override fun setUserProperty(name: String, value: String?) =
        analytics.setUserProperty(name, value)
}

// :data/iosMain
internal class FirebaseAnalyticsClient : AnalyticsClient {
    override fun logEvent(name: String, params: Map<String, String>) {
        FIRAnalytics.logEventWithName(name, params as Map<Any?, *>)
    }
    override fun setUserProperty(name: String, value: String?) =
        FIRAnalytics.setUserPropertyString(value, forName = name)
}
```

Bound through the DI platform module — see `../libraries/koin-di.md`:

```kotlin
// androidMain
actual fun platformModule(): Module = module {
    single<AnalyticsClient> { FirebaseAnalyticsClient(Firebase.analytics) }
    single<CrashReporter> { FirebaseCrashReporter(Firebase.crashlytics) }
}
```

### Why this is the default

- Both SDKs are official and first-party, so there is no third-party supply-chain or lag risk on the critical path.
- Each platform gets the full, current SDK surface.
- The domain interface is trivially fakeable in `commonTest`.
- The cost is boilerplate, which is bounded and mechanical.

```kotlin
// WRONG — Firebase types in commonMain
// :domain/commonMain
expect class AnalyticsClient {
    fun logEvent(event: FirebaseEvent)      // no such common type exists
}
```

Why the wrong form is a problem: there is no shared Firebase type system, so the `expect` declaration cannot express the parameter. Attempting it forces either a lowest-common-denominator shape (which is what the interface above already is) or a leak of one platform's types into common code, which breaks the other platform's compilation.

---

## Approach B — GitLive `firebase-kotlin-sdk`

A community Kotlin-first wrapper over the native SDKs, letting Firebase be used from `commonMain`.

```kotlin
commonMain.dependencies {
    implementation("dev.gitlive:firebase-auth:<version>")
    implementation("dev.gitlive:firebase-firestore:<version>")
}
```

**MAY** adopt. If adopted, **MUST**:

- Record it as a **community-maintained** dependency, with an owner on the team responsible for tracking it.
- Verify that every Firebase service the project needs is covered — service and API coverage is not complete.
- Still wrap it behind a domain interface. The library solves the *implementation* boilerplate; it does not change the layering rule, and wrapping it keeps the exit route open.

```kotlin
// CORRECT — GitLive used as an implementation detail of :data
internal class GitLiveAuthClient : AuthClient {
    override suspend fun signIn(email: String, password: String): UserId =
        UserId(Firebase.auth.signInWithEmailAndPassword(email, password).user!!.uid)
}
```

```kotlin
// WRONG — GitLive types in the domain and the UI
// :domain
interface AuthClient {
    suspend fun signIn(email: String, password: String): FirebaseUser   // library type
}
```

Why the wrong form is a problem: a community library's types now appear in the domain API and in the UI. If the library stalls, lags a Firebase SDK change, or is abandoned, the migration touches every layer instead of one class in `:data`.

---

## Per-service platform differences — MUST account for

These differences exist regardless of which approach is chosen. No wrapper removes them.

### Crashlytics

| Concern | Android | iOS |
|---|---|---|
| Build integration | Crashlytics Gradle plugin; symbol upload | dSYM upload |
| Kotlin/Native frames | n/a | **require the framework's dSYMs to be uploaded**, or shared-code crashes are unsymbolicated |
| Obfuscation | R8 `mapping.txt` must be uploaded | **R8 does not apply to the iOS framework** — see `../quality/performance.md` |

> **[UNVERIFIED]** The exact current procedure for symbolicating Kotlin/Native frames in Crashlytics was not established. **MUST** verify before relying on iOS crash reports from shared code — this is the difference between actionable reports and unreadable addresses.

**MUST** treat crash symbolication as a release-pipeline requirement on both platforms, not an afterthought. See `../release/android-release.md` and `../release/ios-release.md`.

### Cloud Messaging (FCM)

| Concern | Android | iOS |
|---|---|---|
| Receiving | a `FirebaseMessagingService` subclass | `UNUserNotificationCenter` delegation |
| Permission | `POST_NOTIFICATIONS` runtime permission (API 33+) | `UNUserNotificationCenter.requestAuthorization` |
| Transport | FCM direct | **APNs certificate or key must be configured** in the Firebase console |
| Capability | — | Push Notifications capability in the Xcode project |

The token lifecycle is platform code either way. **MUST** expose it to common code as an interface:

```kotlin
// :domain
interface PushTokenSource {
    suspend fun currentToken(): String?
    fun observeTokenRefresh(onToken: (String) -> Unit): Cancellable
}
```

**MUST NOT** model push message *handling* in common code as a `Flow` exported to Swift on the Objective-C path — see `../kotlin/coroutines-and-flow.md`.

### Analytics

**MUST** define event names and parameter keys as constants in `commonMain` and use them from both platforms. Event-name and parameter-limit semantics differ subtly between the two SDKs, so divergent string literals produce two different event schemas in one project.

```kotlin
// CORRECT — :domain or :core:analytics, commonMain
object AnalyticsEvents {
    const val ARTICLE_OPENED = "article_opened"
    const val PARAM_ARTICLE_ID = "article_id"
}
```

### Firebase project configuration

| Platform | File | Notes |
|---|---|---|
| Android | `google-services.json` | per build variant / per package name |
| iOS | `GoogleService-Info.plist` | per bundle ID |

- **MUST NOT** commit production configuration files to a public repository. See `../quality/security.md`.
- **MUST** align the Android package name and the iOS bundle ID with the Firebase app registrations. A mismatch fails at runtime with an unhelpful error.
- iOS Firebase SDK integration interacts with the KMP framework-integration choice — CocoaPods vs SPM. **MUST** check compatibility with the chosen option in `../kmp/ios-interop.md` before committing to one.

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| Describing any third-party Firebase KMP library as official | factually wrong |
| Adopting "KFire" on blog-post evidence | unverified provenance in the critical path |
| Firebase types in `:domain` or the UI layer | migration cost spreads across every layer |
| `expect class` wrapping a Firebase type | no shared type system to express it |
| Firebase SDK called directly from a ViewModel or composable | violates the repository rule — `../android/app-architecture.md` |
| Divergent analytics event strings per platform | two event schemas in one project |
| Shipping without dSYM / mapping upload | unreadable crash reports |
| Committing production `google-services.json` / `GoogleService-Info.plist` | configuration and project identifiers in version control |
| Assuming a wrapper removes the APNs or `POST_NOTIFICATIONS` work | push silently does not work on one platform |

---

## Testing recommendations

**MUST:**

- Fake the domain interfaces in `commonTest`. Firebase SDKs **MUST NOT** appear in a unit test.

```kotlin
class FakeAnalyticsClient : AnalyticsClient {
    val events = mutableListOf<Pair<String, Map<String, String>>>()
    override fun logEvent(name: String, params: Map<String, String>) { events += name to params }
    override fun setUserProperty(name: String, value: String?) = Unit
}

@Test
fun opensArticleAndLogsEvent() = runTest {
    val analytics = FakeAnalyticsClient()
    val viewModel = FeedViewModel(FakeNewsRepository(), analytics)

    viewModel.onArticleClick(ArticleId("a1"))

    assertEquals(AnalyticsEvents.ARTICLE_OPENED, analytics.events.single().first)
}
```

- Verify analytics **call sites** against the shared constants, so a renamed event breaks a test rather than silently splitting a funnel.
- Verify the real implementations in `androidDeviceTest` / `iosTest` only where behaviour cannot be faked meaningfully (token retrieval, Remote Config fetch).

**SHOULD:**

- Verify push delivery end-to-end manually on both platforms before every release that touches messaging. There is no useful automated substitute, and the iOS failure mode (missing APNs key or capability) produces silence rather than an error.
- Send a deliberate non-fatal from a release-configured build on both platforms and confirm the report is **symbolicated**, including a frame from shared Kotlin code. This is the only way to find a broken dSYM/mapping pipeline before a real crash does.
- Keep Remote Config defaults in `commonMain` so the app behaves correctly before the first fetch completes, and test that default path.

**MUST NOT** write a test that depends on a live Firebase project in the merge-gating job.

---

## Cross-references

- Capability inversion and the dependency rule: `../architecture/clean-architecture.md`
- Repository rule — no SDK in a ViewModel: `../android/app-architecture.md`
- Platform DI bindings: `../libraries/koin-di.md`
- `Flow` across the Objective-C boundary: `../kotlin/coroutines-and-flow.md`, `../kmp/ios-interop.md`
- CocoaPods vs SPM integration choice: `../kmp/ios-interop.md`
- Secret and config-file handling: `../quality/security.md`
- Mapping/dSYM in the release pipeline: `../release/android-release.md`, `../release/ios-release.md`
- Play Integrity for high-value operations: `../quality/security.md`
