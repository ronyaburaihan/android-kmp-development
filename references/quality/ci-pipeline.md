# CI Pipeline

**Scope:** A concrete CI layout for an Android + KMP repository: which jobs, on which runners, what gates a merge, caching, and the KMP-specific jobs that are usually missing.
**Sources:** This document is [DEFAULT] engineering practice assembled from the official test-runner and release requirements in `testing-strategy.md`, `../release/*.md`, and `../kmp/*.md`. The template at `../../templates/ci-github-actions.yml` is syntactically valid YAML; it has **not** been executed on a hosted runner as part of this skill's verification.

---

## The jobs — SHOULD

| Job | Runner | Tasks | Gates merge? |
|---|---|---|---|
| **static** | Linux | `ktlintCheck`, `detekt`, `lintRelease` | yes |
| **unit** | Linux | `:shared:jvmTest`, `testDebugUnitTest`, `compileKotlinMetadata` | yes |
| **ios** | **macOS** | `:shared:compileKotlinIosArm64`, `:shared:iosSimulatorArm64Test`, `linkDebugFrameworkIosSimulatorArm64`, Swift smoke test | **yes** |
| **release-build** | Linux | `bundleRelease` (R8 on), merged-manifest check, mapping artefact | yes |
| **instrumented** | Linux + emulator, or device farm | `connectedDebugAndroidTest` on the **minified** variant | yes for release branches; may be nightly for PRs |
| **benchmark** | physical device, scheduled | Macrobenchmark; Baseline Profile regeneration | no — scheduled, alerts on regression |
| **contract** | any, scheduled | live staging API checks | no |

**MUST** gate merges on the **ios** job. The most common CI gap in KMP repos is an Android-only pipeline: a JVM-only dependency or a platform import in `commonMain` then compiles green and breaks the iOS build after merge.

**MUST** gate on the release build. R8 keep-rule breakage appears only there.

---

## Rules — MUST

1. **One macOS job minimum.** Native compilation, `iosSimulatorArm64Test`, and the framework link cannot run on Linux.
2. **Cache three things:** Gradle (`~/.gradle/caches`, `~/.gradle/wrapper`), Kotlin/Native (`~/.konan`), and the Gradle **build cache** (remote if the team is larger than a few people). A cold `~/.konan` adds several minutes to every macOS job.
3. **Pin the JDK** via a toolchain (`actions/setup-java` + `java-version`) matching the project's requirement. AGP 9.x has its own minimum; `references/version-matrix.md`.
4. **Run `./gradlew --stop`-equivalent isolation**: `--no-daemon` or a fresh runner per job; stale daemons produce misleading failures after toolchain changes.
5. **Upload artefacts that verification needs later:** the AAB, `mapping.txt`, the iOS framework, test reports, Compose compiler reports.
6. **Fail on new lint-baseline entries and new `@Suppress`** — check the diff, not just the task result.
7. **Secrets from the CI secret store only**; never a keystore, `.p8`, or `google-services.json` in the repo. `../quality/security.md`.
8. **Release signing on protected branches only**, with environment protection rules.

---

## Gradle flags for CI — SHOULD

```
org.gradle.caching=true
org.gradle.configuration-cache=true        # if the project supports it
org.gradle.parallel=true
kotlin.incremental=false                   # CI builds are clean; incremental state is noise
```

Invoke with `--no-daemon --stacktrace --console=plain`.

---

## The Swift smoke step — SHOULD

After `linkDebugFrameworkIosSimulatorArm64`, compile one Swift file against the generated framework. This is the only automated check that the exported API is callable; Kotlin tests cannot see export regressions. `../kmp/ios-interop.md`.

```bash
xcrun swiftc -target arm64-apple-ios15.0-simulator \
  -sdk "$(xcrun --sdk iphonesimulator --show-sdk-path)" \
  -F shared/build/bin/iosSimulatorArm64/debugFramework \
  -parse-as-library -emit-object -o /tmp/smoke.o ci/SharedApiSmoke.swift
```

A full XCTest run needs an Xcode project and a booted simulator; the compile alone catches the common breakages (generics, `Flow`, dropped default arguments, renamed classes).

---

## Android / iOS differences in CI

| Concern | Android | iOS |
|---|---|---|
| Runner | Linux (cheap) | **macOS** (expensive, slower queue) |
| Emulator | works headless on Linux with KVM | simulator on macOS only |
| Signing | keystore from secrets | certificates + provisioning from secrets (`fastlane match`, or Xcode Cloud) |
| Artefact | AAB + `mapping.txt` | `.xcarchive` / `.ipa` + dSYMs (incl. Kotlin framework) |
| Store upload | Play publishing API / Gradle Play Publisher | App Store Connect API / `altool` |
| Test parallelism | sharded instrumented tests | `xcodebuild -parallel-testing-enabled` |

**SHOULD** keep the macOS job narrow (compile, native tests, link, Swift smoke) and everything else on Linux — macOS minutes cost several times more.

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| No macOS job | native breakage merges |
| Release build not in the gate | R8 breakage reaches users |
| Instrumented tests only on debug | R8/reflection bugs invisible |
| Uncached `~/.konan` | +minutes per macOS run |
| Benchmarks per PR on shared runners | noise exceeds signal |
| Live backend in the merge gate | flaky gate; blocked merges |
| Keystore / `.p8` in the repo | credential exposure |
| Lint baseline grown silently | warnings laundered |
| Release signing on every branch | leaked signing to forks/PRs |

---

## Testing recommendations

- **MUST** verify the pipeline itself once: deliberately introduce a platform import in `commonMain` on a branch and confirm the **ios** job fails.
- **SHOULD** verify the release-build gate the same way: add a reflection use with no keep rule; the instrumented-on-minified job must fail.
- **SHOULD** record pipeline duration per job and treat a doubling as a regression (usually a lost cache).

---

## Cross-references

- Test source sets and what runs where: `testing-strategy.md`
- Release gates the pipeline feeds: `../release/android-release.md`, `../release/ios-release.md`, `../../workflows/prepare-release.md`
- Swift export checks: `../kmp/ios-interop.md`
- Secrets handling: `security.md`
- Template: `../../templates/ci-github-actions.yml`
