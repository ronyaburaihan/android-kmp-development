# Observability: Crash Reporting, Analytics, Logging

**Scope:** Vendor-neutral rules for crash reporting, analytics events, and logging in a shared codebase. Firebase-specific setup is in `../integrations/firebase.md`.
**Official sources:**
- <https://developer.android.com/topic/performance/vitals> (Android vitals)
- <https://developer.android.com/privacy-and-security/security-best-practices> (logging)
- `../integrations/firebase.md` for Crashlytics / Analytics specifics

**Rule levels:** see `../README.md`. Layering rules are [DEFAULT]; logging security rules are [OFFICIAL].

---

## Layering — MUST

Every observability SDK is a platform SDK. The shared module sees interfaces; the platforms bind implementations. This is the same inversion as every other capability (`../architecture/clean-architecture.md`).

```kotlin
// commonMain
public interface CrashReporter {
    public fun recordNonFatal(throwable: Throwable, context: Map<String, String> = emptyMap())
    public fun log(message: String)                      // breadcrumb, not a user-facing log
    public fun setUserId(id: String?)
}

public interface AnalyticsClient {
    public fun track(event: AnalyticsEvent)
    public fun setUserProperty(name: String, value: String?)
}

/** Events are a closed, typed set — never ad-hoc strings at call sites. */
public sealed interface AnalyticsEvent {
    public val name: String
    public val params: Map<String, String>
    public data class ArticleOpened(val id: String) : AnalyticsEvent {
        override val name: String = "article_opened"
        override val params: Map<String, String> = mapOf("article_id" to id)
    }
}
```

**MUST NOT** call Firebase, Sentry, Datadog, etc. from a ViewModel, use case, or repository. **MUST NOT** put vendor types in `commonMain`.

---

## Crash reporting — MUST

- **Symbolication is a release gate.** Android needs `mapping.txt` uploaded; iOS needs dSYMs, **including the Kotlin framework's**. Without them, shared-code crashes are unreadable addresses. **MUST** verify with a deliberate non-fatal from a release-configured build before every release — `../release/android-release.md`, `../release/ios-release.md`. The Kotlin/Native symbolication procedure is `[UNVERIFIED]` in this skill; this check is where a project confirms it.
- **Non-fatals for handled domain errors that indicate a defect** (an `Unexpected` branch), not for expected failures (`Offline`). Reporting every network error as a non-fatal drowns real signal.
- **Breadcrumbs, not PII.** `log()` records navigation and actions; **MUST NOT** record emails, tokens, or request bodies.
- **User id, not identity.** `setUserId` with an opaque internal id, cleared on sign-out via the `SessionEvent.SignedOut` broadcast.
- **Uncaught exceptions on Kotlin/Native** terminate the process like any crash; coroutine exceptions in a scope with no handler reach the crash reporter as uncaught. **MUST** keep a `CoroutineExceptionHandler` on long-lived injected scopes that records and continues where continuing is correct.

---

## Analytics — MUST

- **Typed events defined once in `commonMain`**, as above. The characteristic bug is two platforms emitting slightly different strings for one event, splitting every funnel. The compiled example's `AnalyticsEvent` shape prevents it at the type level.
- **Parameter limits differ per vendor** (name length, count, value types). **MUST** keep params to short strings and numbers; **MUST** check the vendor's limits before adding a parameter.
- **Consent-gated.** Analytics collection **MUST** be off until consent where required (GDPR/EEA, ATT on iOS). The `AnalyticsClient` implementation enforces it; call sites do not check.
- **Screen views** are tracked from the navigation layer (one place), not from each screen.
- **No analytics in tests' merge gate.** Fake `AnalyticsClient`; assert on recorded events.

---

## Logging — MUST

[OFFICIAL — security best practices]

- **MUST NOT** log tokens, credentials, PII, request/response bodies, or headers in a release build.
- **MUST** gate verbose logging on a debug flag that is a build-time constant, so R8 strips the calls. A runtime boolean leaves the string-building in the binary.
- **MUST NOT** use `LogLevel.ALL`/`BODY` for Ktor in release — `../libraries/ktor-networking.md`.
- **SHOULD** route logging through one `Logger` interface in `commonMain` with platform sinks (Logcat / `OSLog`), so release behaviour is decided in one place.
- Kotlin/Native has no `android.util.Log`; `println` goes to stderr. A shared `Logger` interface is the only portable option.

---

## Android vitals — SHOULD watch

Play's vitals dashboard tracks crash rate, ANR rate, excessive wakeups, and stuck wake locks, and **affects store ranking** when thresholds are crossed. **SHOULD** set staged-rollout halt criteria on crash-free and ANR rates — `../release/android-release.md`. ANRs are a main-thread problem: see `../kotlin/coroutines-and-flow.md` § 2 (main-safety) and `performance.md`.

---

## Android / iOS differences

| Concern | Android | iOS |
|---|---|---|
| Symbol input | `mapping.txt` (R8) | dSYM — and **R8 does not apply to the Kotlin framework**, so shared code is unobfuscated |
| Platform crash stats | Play vitals | Xcode Organizer / App Store Connect metrics |
| ANR equivalent | ANR, tracked by Play | watchdog termination (0x8badf00d) |
| System log | Logcat (`android.util.Log`) | `OSLog` / unified logging; `println` → stderr |
| Consent | GDPR consent for analytics | ATT for tracking (`NSUserTrackingUsageDescription`) + GDPR |
| Shared code | interfaces + typed events | same; implementations differ |

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| Vendor SDK calls from a ViewModel/repository | untestable; vendor lock across every layer |
| Ad-hoc event strings at call sites | split funnels across platforms |
| Non-fatal for every expected error | signal drowned |
| PII in breadcrumbs or logs | data exposure; compliance incident |
| Runtime-flag-gated verbose logging | strings still built in release |
| Shipping without symbolication verified | blind to shared-code crashes until the first incident |
| Analytics before consent | compliance violation |
| `println` as the shared logger | unstructured, unlevelled, stderr on iOS |

---

## Testing recommendations

- **MUST** fake `CrashReporter`, `AnalyticsClient`, and `Logger` in `commonTest`; assert the typed events a flow emits.
- **MUST** test that sign-out clears the user id on every observability client.
- **MUST** verify symbolication end-to-end per platform before release (manual).
- **SHOULD** assert in a release-variant test that no verbose logging configuration is reachable.
- **SHOULD** test consent gating: analytics implementation records nothing until consent is granted.

---

## Cross-references

- Firebase Crashlytics/Analytics specifics and the three integration approaches: `../integrations/firebase.md`
- Logging and secrets: `security.md`
- Sign-out broadcast: `../kotlin/coroutines-and-flow.md`, `../integrations/auth-and-tokens.md`
- Symbolication in the release gates: `../release/android-release.md`, `../release/ios-release.md`
- Main-thread discipline behind ANRs: `performance.md`
