# Release Checklist — <version> (<build>) — <Android | iOS | both>

Mirror of `workflows/prepare-release.md`. Tick only what was **executed**.

## Blockers — any failure is a no-go
- [ ] B1 `targetSdk` ≥ Play floor (verified live: <URL, date>)
- [ ] B2 Billing Library ≥ publishing floor (monetised)
- [ ] B3 iOS build string unique
- [ ] B4 `CADisableMinimumFrameDurationOnPhone` present (CMP iOS)
- [ ] B5 Usage-description strings complete
- [ ] B6 No `debuggable` in **merged** release manifest
- [ ] B7 No credentials in source control
- [ ] B8 Working tree clean; commit `<sha>`
- [ ] B9 iOS certs/profiles valid past the window
- [ ] B10 Restore-purchases present (monetised iOS)

## Android
- [ ] Unit tests · [ ] KMP `allTests` · [ ] `lintRelease` (no new baseline)
- [ ] `bundleRelease` with R8 + resource shrinking
- [ ] **Instrumented suite on the minified variant**
- [ ] Merged release manifest inspected
- [ ] `mapping.txt` archived at `<path>` and uploaded
- [ ] `bundletool` install on physical device `<model/API>`; smoke-tested
- [ ] Baseline Profile current
- [ ] **Symbolication verified** (crash id `<id>`, shared-Kotlin frame readable)

## iOS
- [ ] `iosSimulatorArm64Test` · [ ] release framework links · [ ] Swift smoke test
- [ ] Archive · [ ] `altool --validate-app`
- [ ] TestFlight on physical device `<model/iOS>`; smoke-tested
- [ ] Restore-purchases on clean install (monetised)
- [ ] dSYMs archived (incl. Kotlin framework) · **symbolication verified** (crash id `<id>`)

## Change-triggered
- [ ] Schema migration → upgrade-in-place from `<previous version>` with real data
- [ ] `targetSdk` bump → behaviour matrix (insets, back, ≥600dp)
- [ ] Billing → purchase / restore / acknowledge
- [ ] Auth/storage → sessions survive or reset deliberately; key-invalidation path
- [ ] Messaging → push end-to-end both platforms
- [ ] Shared module → exported surface unchanged or coordinated

## Verdict
**<GO | CONDITIONAL GO | NO-GO>**
NOT RUN items and their risk:
Rollout plan and halt criteria:
