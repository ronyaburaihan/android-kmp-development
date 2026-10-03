# Background Work

**Scope:** Choosing between coroutines, WorkManager, foreground services, and AlarmManager; WorkManager usage; what is restricted on recent Android versions; the iOS counterpart.
**Official sources:**
- <https://developer.android.com/develop/background-work/background-tasks>
- <https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started>
- <https://developer.android.com/jetpack/androidx/releases/work>

**Rule levels:** see `../README.md`. Decision rules and API facts are [OFFICIAL].

---

## The decision — MUST follow

Google's decision tree, condensed:

| Question | Answer → mechanism |
|---|---|
| Does the work need to continue after the user leaves the screen or the app? | **No** → coroutine in `viewModelScope` / an injected scope. Done. |
| Would deferring or interrupting it be bad UX? | **No** → **WorkManager** (the answer for ~90% of background work). |
| Is it short (< ~3 min) and critical? | **Yes** → foreground service with `shortService` type. |
| Is there a purpose-built alternative? | Location → Geofence; large transfer → User-Initiated Data Transfer; Bluetooth → Companion Device Manager; video → Picture-in-Picture. **MUST** prefer these over a foreground service. |
| None of the above and it must not be interrupted (media playback, active fitness tracking) | foreground service, with a notification and a declared type. |

**Event-triggered work** (FCM, broadcast, alarm): finishes in seconds → coroutine; longer → WorkManager; > ~10 minutes and triggered by an approved exemption → foreground service.

**MUST NOT** use a plain coroutine or thread for work that must survive the app going to the background. It stops when the scope does.

**MUST NOT** reach for a foreground service because WorkManager "might be delayed". Deferral is the point; the notification requirement is the price of avoiding it.

---

## WorkManager

Current stable: **2.12.0** [OFFICIAL, verified 2026-10-03]. Artifacts: `androidx.work:work-runtime-ktx`, test helpers `androidx.work:work-testing`.

### Shape

```kotlin
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Resolve dependencies through the app's DI, never a static holder.
        val repository = (applicationContext as App).koin.get<NewsRepository>()
        return try {
            repository.refresh()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoConnectivityException) {
            Result.retry()                      // transient — backoff and try again
        } catch (e: ServerException) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
```

```kotlin
val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
    )
    .build()

WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "news-sync",
    ExistingPeriodicWorkPolicy.KEEP,
    request,
)
```

### Rules — MUST

- Use `CoroutineWorker`, not `Worker`, in Kotlin. `doWork` is then a `suspend` function and cancellation is cooperative.
- Rethrow `CancellationException`. A stopped worker is cancelled; swallowing it reports success.
- Return `Result.retry()` for transient failures and `Result.failure()` for permanent ones. Returning `success()` on failure silences the retry policy.
- Use `enqueueUniqueWork` / `enqueueUniquePeriodicWork` with an explicit `ExistingWorkPolicy` for anything enqueued from more than one call site, or duplicates accumulate.
- Keep a single unit of work under ~10 minutes; longer work is interrupted. Chain or split it.
- Resolve dependencies in `doWork` through the application's DI entry point. **MUST NOT** hold a repository in a static field.
- Minimum periodic interval is 15 minutes. Code asking for less silently gets 15.

### What WorkManager is not

- Not exact-time. For a wake at a precise time, `AlarmManager` with the appropriate exact-alarm permission — and that permission is restricted.
- Not for foreground-visible progress. That is a foreground service, or an expedited work request with `setForeground` where justified.
- Not a replacement for a push. Periodic sync plus push is the usual pairing.

---

## Foreground services — the restrictions that bite

[OFFICIAL]

| Version | Rule |
|---|---|
| Android 12+ | **cannot be started from the background** except via approved exemptions (high-priority FCM, user action, boot, geofence, …); the notification may be hidden for the first 10 seconds |
| Android 13+ | runtime `POST_NOTIFICATIONS` permission needed for the notification to show |
| Android 14+ | a **foreground service type** is mandatory (`location`, `mediaPlayback`, `dataSync`, `shortService`, …), each with its own prerequisites and some with Play policy review |
| Android 10+ | background location needs `ACCESS_BACKGROUND_LOCATION` |

**MUST** declare the type in the manifest and pass it when starting. **MUST** have a user-visible notification. **MUST** stop the service when the work ends — a running foreground service is a battery complaint and a Play vitals signal.

---

## Android / iOS differences — no shared abstraction exists

**There is no first-party KMP background-work library.** [OFFICIAL by absence] The platforms' models differ fundamentally:

| Concern | Android | iOS |
|---|---|---|
| Deferred work | WorkManager | `BGTaskScheduler` (`BGAppRefreshTask`, `BGProcessingTask`) |
| Guarantees | persisted, retried, constraint-driven | **opportunistic**; the OS decides when, based on usage patterns; no fixed interval |
| Long-running | foreground service | background modes (audio, location, VoIP, …) with App Review scrutiny; `BGContinuedProcessingTask` on newer iOS |
| Exact wake | `AlarmManager` (restricted) | none for apps |
| Push-triggered | FCM data message → work | silent push with `content-available`, budgeted by the OS |
| Testing | `WorkManagerTestInitHelper` | `e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"id"]` in the debugger |

**MUST** model background sync as a **domain-level operation** (`suspend fun sync()`) in `commonMain` and keep the scheduling in platform code. The shared part is *what* runs; *when* and *whether* is platform-owned.

**SHOULD NOT** promise iOS users a periodic sync interval. `BGAppRefreshTask` has none.

Community KMP wrappers (brewkits `kmpworkmanager`, `kmpworker`, `worker-kmp`) exist and unify the two APIs. **MAY** evaluate; **MUST** record them as third-party dependencies and check their Kotlin-version support before adopting. [DEFAULT]

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| `GlobalScope.launch` / a bare thread for background work | dies with the process; untraceable |
| `Worker` with blocking I/O instead of `CoroutineWorker` | non-cancellable; blocks a WorkManager thread |
| Swallowing `CancellationException` in `doWork` | stopped work reported as success |
| `Result.success()` on failure | retry policy silenced |
| `enqueue` without unique-work policy from a repeated call site | duplicate periodic jobs accumulate |
| Static holder for dependencies inside a worker | leaks; untestable |
| Foreground service for deferrable work | battery, notification noise, Play policy risk |
| Foreground service without a type on Android 14+ | crash at start |
| Assuming iOS will run a background task on a schedule | it will not |
| Scheduling logic in `commonMain` | no shared API exists; it will not compile for native |

---

## Testing recommendations

- **MUST** unit-test the `suspend fun sync()` the worker calls, in `commonTest`, with fakes. The worker itself should be thin enough that there is little to test.
- **SHOULD** test the worker's `Result` mapping with `work-testing`'s `TestListenableWorkerBuilder` — success, retry, failure for each exception class.
- **SHOULD** verify constraints with `WorkManagerTestInitHelper` and `TestDriver` (`setAllConstraintsMet`) in an instrumented test.
- **MUST** test the cancellation path: a worker stopped mid-run must not report success.
- iOS: **MUST** exercise the `BGTaskScheduler` registration on a device at least once per release — a missing `Info.plist` `BGTaskSchedulerPermittedIdentifiers` entry is a silent no-op.

---

## Cross-references

- Scope rules and `CancellationException`: `../kotlin/coroutines-and-flow.md`
- `POST_NOTIFICATIONS` and foreground-service permissions: `permissions.md`
- API 36 behaviour changes affecting scheduled work (`scheduleAtFixedRate`): `platform-requirements.md`
- Keeping platform scheduling out of `commonMain`: `../kmp/project-structure.md`
- Push delivery as the iOS trigger: `../integrations/firebase.md`
