# Android + KMP Engineering References

Reference set for the `android-kmp-development` skill. Each document covers exactly one engineering topic. Content is not duplicated between documents; cross-references are used instead.

**Verified:** 2026-10-03. Re-verify version-sensitive claims against `version-matrix.md` before acting on them.

## Rule levels

Every normative statement in these documents carries one of these keywords. They are used in the RFC 2119 sense, narrowed for this skill:

| Keyword | Meaning | Agent behaviour |
|---|---|---|
| **MUST** / **MUST NOT** | Mandatory. Either an official requirement, a hard platform constraint, or something that breaks the build, fails review at Google/Apple, or creates a security defect. | Never violate. If the user's instruction conflicts, say so explicitly before proceeding. |
| **SHOULD** / **SHOULD NOT** | Recommended default. Official recommendation or strong engineering consensus. | Follow unless the user asked otherwise or the codebase already does something different and consistent. Do not silently "fix" existing deviations. |
| **MAY** | Permitted option. | Choose based on the existing codebase. |

Each rule is also tagged with its provenance:

- **[OFFICIAL]** — stated in first-party documentation (Google for Android/AndroidX, JetBrains for Kotlin/KMP/CMP/Ktor, Apple for App Store, library owner for library APIs).
- **[DEFAULT]** — engineering convention, not an official recommendation. Defensible, contestable, and must be presented as a choice rather than a requirement.
- **[UNVERIFIED]** — the claim comes from a secondary source or conflicts with another source. MUST be verified before it is relied on. Never present an `[UNVERIFIED]` claim to a user as fact.

## Document index

### `kotlin/`
| File | Topic |
|---|---|
| `kotlin/coding-conventions.md` | Kotlin naming, formatting, idioms, API surface rules, linting |
| `kotlin/coroutines-and-flow.md` | Dispatchers, scopes, cancellation, `StateFlow`/`SharedFlow`, lifecycle-aware collection |
| `kotlin/language-essentials.md` | Null safety, immutability, collections, extension functions, error-handling mechanisms |

### `android/`
| File | Topic |
|---|---|
| `android/app-architecture.md` | UI/domain/data layers, the official recommendation set, repository rule |
| `android/compose-ui.md` | Compose state, state hoisting, recomposition performance, stability, strong skipping |
| `android/platform-requirements.md` | `targetSdk` gate, Android 16 behaviour changes, manifest and build-config requirements |
| `android/background-work.md` | WorkManager vs foreground service vs coroutine; iOS `BGTaskScheduler` has no shared abstraction |
| `android/permissions.md` | Runtime permission flow, denial handling, one-time grants, iOS usage strings |
| `android/xml-views.md` | Working in View-based code; `ComposeView` / `AndroidView` interop; migration posture |
| `android/navigation.md` | Navigation 3 model, entry decorators, deep links, Navigation 2 in existing apps |
| `android/accessibility.md` | Compose semantics, touch targets, state/live announcements, TalkBack and VoiceOver, CMP iOS |
| `android/localization.md` | String resources (Android + CMP), plurals, formatting, RTL, per-app language, pseudolocales |

### `kmp/`
| File | Topic |
|---|---|
| `kmp/project-structure.md` | Targets, source-set hierarchy, `expect`/`actual`, module layout, version lockstep |
| `kmp/compose-multiplatform.md` | CMP vs Jetpack Compose, per-target stability, resources, lifecycle, ViewModel, navigation |
| `kmp/ios-interop.md` | Objective-C export constraints, Swift export, framework integration, Compose↔SwiftUI |

### `architecture/`
| File | Topic |
|---|---|
| `architecture/clean-architecture.md` | Dependency rule, domain layer design, boundary mapping |
| `architecture/mvvm-udf.md` | UI state modelling, event handling, state holders |
| `architecture/modularization.md` | Module taxonomy, granularity, dependency direction, convention plugins |
| `architecture/presenter.md` | Presenter pattern vs ViewModel; when each is right; Circuit as the library form |

### `libraries/`
| File | Topic |
|---|---|
| `libraries/koin-di.md` | Koin in KMP, Compose integration, platform modules, DI alternatives |
| `libraries/ktor-networking.md` | Ktor client setup, engines, plugins, error handling, DTO mapping |
| `libraries/room-datastore.md` | Room KMP, DataStore Preferences KMP, migrations, platform builders |

### `quality/`
| File | Topic |
|---|---|
| `quality/testing-strategy.md` | Test scopes, KMP test source sets, Compose UI tests, fakes, Flow testing |
| `quality/security.md` | Storage, IPC, network, crypto, key storage, Android/iOS secure-storage split |
| `quality/performance.md` | R8, Baseline Profiles, Macrobenchmark, startup and frame metrics |
| `quality/observability.md` | Crash reporting, analytics, logging — vendor-neutral layering and rules |
| `quality/ci-pipeline.md` | Concrete CI job layout: Linux + macOS jobs, caching, gates, Swift smoke compile |

### `integrations/`
| File | Topic |
|---|---|
| `integrations/firebase.md` | Firebase on KMP: the three integration approaches and their trade-offs |
| `integrations/subscriptions.md` | Play Billing, StoreKit, entitlement architecture, RevenueCat |
| `integrations/auth-and-tokens.md` | Ktor bearer auth, refresh coordination, token storage, sign-out propagation |
| `integrations/admob.md` | Google Mobile Ads on both platforms, consent, test ads, KMP layering |

### `release/`
| File | Topic |
|---|---|
| `release/android-release.md` | App Bundle, signing, tracks, staged rollout, release checklist |
| `release/ios-release.md` | Archive, upload paths, TestFlight, App Review, phased release |

### Root
| File | Topic |
|---|---|
| `version-matrix.md` | Current versions, compatibility lockstep, where to re-verify |
| `deprecations.md` | APIs and practices that MUST NOT appear in new code |

## How an agent should use these

1. **Before writing any build file**, read `version-matrix.md`. Version lockstep errors (Kotlin ↔ Compose compiler ↔ KSP ↔ CMP) are the most common cause of a non-building KMP project.
2. **Before writing or reviewing code**, read `deprecations.md`. It is short and catches the majority of real-world defects in existing Android/KMP codebases.
3. Read the one topic document that matches the task. Do not read all of them.
4. When a rule is `[DEFAULT]` and the codebase already does something else consistently, match the codebase and say that you did.
5. When a rule is `[UNVERIFIED]`, verify it (official docs, release notes, or the user) before acting. State the uncertainty.
