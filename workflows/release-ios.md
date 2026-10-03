# Workflow: iOS Release

Standing contract: `README.md`. Prerequisite: `prepare-release.md` with a **GO** or **CONDITIONAL GO** verdict for iOS.

---

## 1. Objective

Take a gated, validated iOS archive through TestFlight and App Review to a phased App Store release, with the KMP-specific checks that have no compile-time signal done before submission.

The agent prepares, verifies, and instructs; **uploads, submissions, and release actions are the user's** in Xcode / App Store Connect / the API.

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| `prepare-release.md` report with verdict and archive path | yes | **MUST** stop; run the gate |
| Version / build string | yes | from the gate; **MUST** be unique |
| Release notes ("What's New") per locale | yes | **MUST** ask |
| App Review material: demo account, review notes, privacy answers | yes if applicable | **MUST** ask |
| Phased release: on/off, who monitors | no | default on |
| Submission mechanism: Xcode, `altool`, Transporter, API/Xcode Cloud | no | match the project |

---

## 3. Initial project inspection

```bash
ls build/*.xcarchive 2>/dev/null; ls build/*.ipa 2>/dev/null
grep -n 'CFBundleShortVersionString\|CFBundleVersion' iosApp/Info.plist iosApp/*.xcodeproj/project.pbxproj 2>/dev/null | head
grep -n 'CADisableMinimumFrameDurationOnPhone\|UsageDescription\|GADApplicationIdentifier\|SKAdNetworkItems\|BGTaskSchedulerPermittedIdentifiers' iosApp/Info.plist
```

**MUST** confirm the archive is the gated one. **MUST** re-verify the `Info.plist` crash-level entries — they have no compile-time signal and a last-minute Xcode change can drop one.

---

## 4. Step-by-step procedure

### 4.1 Pre-flight — MUST

| Check | Why |
|---|---|
| Build string unique vs. App Store Connect | a reused triple is rejected on upload |
| Xcode ≥ 14 | upload requirement from 2026 |
| Certificates / provisioning valid past the release window | gate B9 |
| `CADisableMinimumFrameDurationOnPhone` present (CMP) | crash without it |
| Every usage-description string present for capabilities used | crash without it |
| dSYMs archived, **including the Kotlin framework's** | symbolication of shared code |
| `altool --validate-app` passed on this archive | catches most rejections pre-upload |
| Restore-purchases path present and tested on a clean install (monetised) | App Review requirement |
| Privacy nutrition labels match what the app collects | review rejection otherwise |

### 4.2 Upload and processing

The user uploads (Xcode / `xcrun altool --upload-app` / Transporter / API). Processing is asynchronous; wait for the email.

**MUST** then: upload dSYMs to the crash reporter, trigger a deliberate non-fatal from a release-configured TestFlight build, and confirm a **symbolicated frame from shared Kotlin code** appears. This is the `[UNVERIFIED]` item in the reference set; it gets verified here, per project, or the release ships blind to shared-code crashes.

### 4.3 TestFlight internal

Install on a **physical device** via TestFlight (the simulator does not exercise the device framework slice, memory pressure, or production ATS). Smoke-test critical flows. Verify restore-purchases on a clean install if monetised.

### 4.4 TestFlight external — SHOULD

The first build of a version needs beta App Review. **SHOULD** soak with external testers for at least a day; longer for a release with migration, auth, or billing changes.

### 4.5 Submit for App Review

Provide: demo credentials for anything behind sign-in, review notes explaining non-obvious flows, accurate privacy answers, export-compliance answer. **MUST NOT** assume same-day review; **MUST NOT** plan a coordinated Android launch on an assumed review date → D4.

### 4.6 Release with phased rollout — SHOULD

Enable phased release. Apple ramps over seven days to automatic-update users; it can be **paused**. Define halt criteria before release:

| Signal | Halt if |
|---|---|
| Crash-free sessions (crash reporter, this build) | below the previous build minus 0.5 pp |
| New crash signature in shared Kotlin code | any |
| App Store Connect crash metrics | regression vs previous |
| Billing: purchase/restore failure rate | up |

**Pausing does not revert** users who already updated. A fix is a new build through review (expedited review may be requested for a critical crash).

### 4.7 Completion

At the end of the phase with criteria met, record metrics, tag the release (user), close.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | Gate is **CONDITIONAL GO** | (a) proceed with named risks and a longer TestFlight soak; (b) resolve first | **MUST** ask. |
| D2 | Symbolication of shared frames **cannot be confirmed** | (a) fix the dSYM pipeline first; (b) ship blind | **MUST** recommend (a); state the cost of (b) plainly. |
| D3 | App Review **rejects** | — | **MUST** capture the reason verbatim; fix; resubmit; add the check to `prepare-release.md`. |
| D4 | Android and iOS **must launch together** | (a) feature flag, ship as each is ready; (b) hold Android until Apple approves | **MUST** present (a). |
| D5 | A crash-level `Info.plist` entry is **missing** | — | **MUST** stop; one-line fix, new build, new build string. |
| D6 | Phased release criterion tripped | (a) pause and investigate; (b) pause and prepare an expedited fix | **MUST** recommend (a). |
| D7 | Need an **expedited review** | — | the user requests it from Apple; **MUST** state it is not guaranteed. |

---

## 6. Implementation rules

**MUST:**

1. Release the gated archive; no rebuild between gate and submission.
2. Verify `Info.plist` crash-level entries again before upload.
3. Confirm shared-Kotlin symbolication before external testing.
4. Test on a physical device via TestFlight.
5. Define halt criteria before enabling phased release.
6. Keep an expedited-fix branch ready.

**MUST NOT:**

7. Upload, submit, or release on the user's behalf.
8. Reuse a build string.
9. Describe a phased-release pause as a rollback.
10. Promise a review date.
11. Submit without the demo account / review notes the flows need.

---

## 7. Validation requirements

| # | Check | Required |
|---|---|---|
| V1 | Archive = gated archive | MUST |
| V2 | `altool --validate-app` passed | MUST |
| V3 | `Info.plist` crash-level entries present | MUST |
| V4 | dSYMs (incl. Kotlin framework) uploaded; **symbolicated shared frame observed** | MUST |
| V5 | TestFlight install on a physical device; smoke test passed | MUST |
| V6 | Restore-purchases on a clean install (monetised) | MUST if monetised |
| V7 | External TestFlight soak ≥ 24 h | SHOULD |
| V8 | Halt criteria set before phased release | MUST |
| V9 | Metrics recorded through the phase | MUST |

---

## 8. Failure handling

| Failure | Response |
|---|---|
| Upload rejected | build string reuse, missing entitlement, or `Info.plist` — **MUST** read the validation output; fix; new build string. |
| TestFlight build crashes at launch | check `CADisableMinimumFrameDurationOnPhone` and usage strings **first**. |
| Unsymbolicated shared-code crash | → D2; the Kotlin framework's dSYM was not uploaded. |
| App Review rejection | → D3. Common: restore-purchases missing, privacy mismatch, demo account broken. |
| Crash spike during phase | → D6 pause; expedited fix via D7. |
| Android already at 100% and iOS held in review | → D4 — this is why feature flags, not dates, coordinate platforms. |

---

## 9. Completion criteria

1. V1–V6, V8, V9 pass.
2. App Review approved; phased release completed or paused with a documented decision.
3. Shared-code symbolication confirmed for this build.
4. Metrics recorded; release tagged (user).
5. Any rejection or gate miss fed back into `prepare-release.md`.

---

## 10. Final report format

```markdown
## iOS Release — <version> (<build>)

### Outcome
<RELEASED (phased complete) | PHASED — paused at day <n> | IN REVIEW | REJECTED | NOT STARTED>

### Pre-flight
| Check | Result |
|---|---|

### Symbolication
- dSYMs uploaded: <yes, incl. Kotlin framework>
- Symbolicated shared-Kotlin frame observed: <yes — <crash id> | NO (D2)>

### TestFlight
| Stage | Date | Devices | Result |
|---|---|---|---|

### App Review
| Submitted | Outcome | Reason (if rejected) |
|---|---|---|

### Phased release
| Day | Crash-free | New signatures | Decision |
|---|---|---|---|

### Halt criteria (set before release)
### Incidents
### Gate misses to feed back
### Decisions needed
```
