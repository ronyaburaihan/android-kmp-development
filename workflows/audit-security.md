# Workflow: Audit Security

Standing contract: `README.md`. Prerequisite: `inspect-project.md`.

---

## 1. Objective

Review an existing application against `../references/quality/security.md`, classify findings by exploitability, and fix only what is approved.

Default mode is **audit**. Security fixes change authentication, storage, and transport behaviour — the blast radius is users locked out or data made unreadable, so each one needs its own decision.

| Mode | Output |
|---|---|
| **Audit** (default) | Classified findings and a remediation plan. No code changes. |
| **Fix** | Minimal changes to approved findings, each verified. |

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| Mode | no | Default: audit. |
| Scope — whole app, one area, or the current diff | no | Default: whole app at audit depth. |
| Driver — review, incident, compliance, store requirement | no | Affects prioritisation. An incident changes everything. |
| Threat model / what matters most | no | Default: credentials, PII, auth bypass, transport. |
| Known accepted risks | no | **SHOULD** ask, to avoid re-reporting decided items. |

**MUST** ask, before anything else, whether this is an **active incident**. An incident is not an audit and takes a different path — contain first, report to the user immediately, and **MUST NOT** publish exploit detail.

---

## 3. Initial project inspection

Run `inspect-project.md`, then audit against each section of `../references/quality/security.md`.

```bash
# --- secure storage ---
grep -rn 'EncryptedSharedPreferences\|security-crypto\|EncryptedFile' --include='*.kt' --include='*.toml' . | grep -v build/
grep -rn 'getSharedPreferences\|MODE_WORLD' --include='*.kt' . | grep -v build/
grep -rniE 'token|password|secret|apiKey|credential' --include='*.kt' . | grep -v build/ | grep -iE 'datastore|preferences|room|@Entity|dao'

# --- secrets in source ---
grep -rnE '"(sk_|pk_|AIza|ghp_|xox[baprs]-)[A-Za-z0-9_-]{10,}"' --include='*.kt' --include='*.xml' --include='*.properties' . | grep -v build/
grep -rn 'const val.*[Kk]ey\|const val.*[Ss]ecret\|BuildConfig\.' --include='*.kt' . | grep -v build/
git ls-files | grep -iE 'google-services.json|GoogleService-Info.plist|\.jks$|\.keystore$|\.p8$|\.p12$'
grep -rn 'storePassword\|keyPassword\|signingConfig' --include='*.gradle.kts' . | grep -v build/

# --- transport ---
find . -name 'network_security_config.xml' -not -path '*/build/*'
grep -rn 'cleartextTrafficPermitted\|usesCleartextTraffic\|certificates src="user"' --include='*.xml' . | grep -v build/
grep -rn 'NSAllowsArbitraryLoads\|NSExceptionDomains' --include='Info.plist' . 2>/dev/null
grep -rn '"http://' --include='*.kt' --include='*.xml' . | grep -v build/
grep -rn 'TrustManager\|HostnameVerifier\|checkServerTrusted' --include='*.kt' . | grep -v build/

# --- logging ---
grep -rn 'LogLevel.ALL\|LogLevel.BODY\|HttpLoggingInterceptor.Level.BODY' --include='*.kt' . | grep -v build/
grep -rn 'Log\.[dveiw]\|println(' --include='*.kt' . | grep -v build/ | grep -iE 'token|password|auth|secret|user|email'

# --- IPC / components ---
grep -rn 'android:exported' --include='AndroidManifest.xml' . | grep -v build/
grep -rn '<provider\|<receiver\|<service' --include='AndroidManifest.xml' . | grep -v build/
grep -rn 'PendingIntent.get\|FLAG_IMMUTABLE\|FLAG_MUTABLE' --include='*.kt' . | grep -v build/
grep -rn 'Uri.fromFile\|"file://"' --include='*.kt' . | grep -v build/
grep -rn 'sendStickyBroadcast\|registerReceiver' --include='*.kt' . | grep -v build/

# --- WebView ---
grep -rn 'addJavascriptInterface\|setJavaScriptEnabled\|loadUrl\|loadDataWithBaseURL' --include='*.kt' . | grep -v build/

# --- crypto ---
grep -rniE '"(MD5|SHA-1|SHA1|DES|DESede|RC4|AES/ECB)"' --include='*.kt' . | grep -v build/
grep -rn 'SecureRandom\|Random()\|Math.random' --include='*.kt' . | grep -v build/
grep -rn 'KeyStore\|KeyGenParameterSpec\|setUserAuthenticationRequired' --include='*.kt' . | grep -v build/

# --- release hardening ---
grep -rn 'debuggable' --include='AndroidManifest.xml' --include='*.gradle.kts' . | grep -v build/
grep -rn 'isMinifyEnabled\|optimization\s*{' --include='*.gradle.kts' . | grep -v build/
grep -rn 'Play Integrity\|IntegrityManager' --include='*.kt' . | grep -v build/

# --- KMP-specific: the framework is not obfuscated ---
grep -rniE 'const val|private const' --include='*.kt' */src/commonMain 2>/dev/null | grep -iE 'key|secret|url|token'
```

**MUST** audit the **merged release manifest**, not the source manifest. Library manifest merging can reintroduce `debuggable` or a permission the app never declared.

```bash
./gradlew :app:processReleaseManifest
cat app/build/intermediates/merged_manifests/release/AndroidManifest.xml
```

---

## 4. Step-by-step procedure

### 4.1 Classify every finding by exploitability — MUST

Severity is about reachability by an attacker, not how bad the API looks.

| Severity | Definition | Examples |
|---|---|---|
| **S1 — Exploitable now** | A remote or local attacker can reach it on a shipped build | Hardcoded production credential; `addJavascriptInterface` on remote content; cleartext to a production host; custom `TrustManager` accepting all certificates; auth bypass; `debuggable=true` in release |
| **S2 — Exploitable with preconditions** | Needs rooted device, physical access, a malicious app, or a specific flow | Token in plaintext DataStore/Room; mutable `PendingIntent`; exported component with no permission; sensitive data in external storage |
| **S3 — Weakness** | No direct exploit, but removes a defence or will become one | `EncryptedSharedPreferences` (unmaintained); weak primitives where not load-bearing; missing Play Integrity on a high-value action; verbose logging gated only by a flag |
| **S4 — Hygiene** | Best-practice gap | Missing network security config where defaults already suffice; unexplicit `android:exported`; no secret scanning in CI |

**MUST** report S1 findings **immediately**, before completing the rest of the audit. **MUST NOT** hold an S1 until the report is finished.

**MUST** state, per finding: what is reachable, by whom, under what precondition. A finding without that is a lint result, not a security finding.

**MUST NOT** include a working exploit, a step-by-step extraction path, or a proof-of-concept payload. Describe the class of problem and its consequence.

### 4.2 Check the high-frequency real-world items

These account for most genuine findings in production Android/KMP codebases:

| # | Item | Why it is usually the answer |
|---|---|---|
| 1 | Secrets in source, `BuildConfig`, or `commonMain` | Extractable with `strings`. **R8 does not apply to the iOS framework**, so `commonMain` constants ship in clear on iOS. |
| 2 | Tokens in DataStore / Room / SharedPreferences | Plaintext at rest. |
| 3 | `EncryptedSharedPreferences` in use | Deprecated and unmaintained, with reported device-specific crash modes. |
| 4 | Verbose HTTP logging reachable in release | Writes `Authorization` headers and bodies to logs. |
| 5 | Committed `google-services.json`, keystores, `.p8` keys | Credential exposure in history, not just HEAD. |
| 6 | Cleartext or user-CA trust outside `<debug-overrides>` | Trivially MITM-able. |
| 7 | Mutable `PendingIntent` | Intent-redirect attacks. |
| 8 | Exported components without permission | Reachable by any installed app. |
| 9 | WebView JavaScript interface on content not fully controlled | Remote code execution. |
| 10 | No key-invalidation handling | Biometric re-enrolment or device restore makes the app permanently unusable — availability, not confidentiality. |

**MUST** check git **history** for secrets, not only the working tree. A removed key is still exposed.

### 4.3 Audit the KMP-specific exposure

**MUST** check, since it is routinely missed:

- **No secret in `commonMain`.** R8 does not obfuscate the iOS framework; shared Kotlin strings are readable in the shipped iOS binary.
- Secure storage reached through a **domain interface** with Keystore and Keychain implementations — not `expect`/`actual` into a platform SDK from the domain.
- iOS transport: no blanket `NSAllowsArbitraryLoads`.
- Entitlement checks are **server-authoritative**, not a local flag. See `../references/integrations/subscriptions.md`.

### 4.4 Report and stop — audit mode

Present: S1 items first and already-escalated; then the full classified table; then a remediation plan ordered by severity, with the blast radius of each fix.

**MUST** state, per proposed fix, what breaks for existing users — a storage change can log everyone out or orphan their data.

**MUST** stop here in audit mode.

### 4.5 Fix — approved findings only, one at a time

```kotlin
// WRONG — extractable from the APK and, in commonMain, from the iOS binary too
internal const val API_SECRET = "sk_live_9f3b..."

// CORRECT — supplied at build time from CI secrets, and preferably not on the client at all
// (high-value secrets belong server-side, exchanged for a short-lived token)
```

```kotlin
// WRONG — logs Authorization headers and response bodies
install(Logging) { level = LogLevel.ALL }

// CORRECT
if (isDebug) install(Logging) { level = LogLevel.HEADERS }
```

```kotlin
// WRONG — accepts any certificate; defeats TLS entirely
object : X509TrustManager {
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
}

// CORRECT — remove it; use network security config, or a properly scoped custom CA
```

**MUST**, for any storage or auth fix, handle the existing-user path explicitly: migrate, or force re-authentication deliberately. **MUST NOT** silently orphan stored data.

**MUST** handle key invalidation — a key can become permanently unusable after biometric re-enrolment (Android) or a device restore (iOS). The app **MUST** degrade to re-authentication, not crash.

### 4.6 Validate

Run section 7.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D0 | **Active incident** | — | **MUST** stop the audit and report immediately. Containment is the user's decision, not the agent's. **MUST NOT** include exploit detail. |
| D1 | **S1 finding** | — | **MUST** escalate immediately, before continuing. Then ask whether to fix now or hand off. |
| D2 | A secret is in **git history** | (a) rotate the credential (the only real fix) + remove from history; (b) rotate only | **MUST** state plainly that removing the file does **not** un-expose it. **Rotation is mandatory.** |
| D3 | `EncryptedSharedPreferences` in use | (a) migrate to Keystore/DataStore + explicit crypto; (b) accept and document | **MUST** report. **MUST** note the reference set marks the replacement path `[UNVERIFIED]` — the plan must include verifying it. Migration → `add-persistence.md`. |
| D4 | Tokens stored in plaintext | (a) move to platform secure storage behind a domain interface; (b) accept | **MUST** ask. (a) logs existing users out unless migrated — state that. |
| D5 | Fix requires **re-authenticating all users** | (a) accept the forced re-auth; (b) dual-read migration | **MUST** ask. This is a product decision with a support cost. |
| D6 | Certificate pinning requested | (a) implement with a rotation plan and backup pins; (b) do not pin | **MUST** require a rotation plan first. An expired pin bricks every installed client and cannot be fixed server-side. Per-engine work in KMP. |
| D7 | Weak primitive is **load-bearing** for existing data (e.g. SHA-1 in stored hashes) | (a) dual-path migration; (b) accept with documentation | **MUST** ask. Swapping the primitive invalidates existing data. |
| D8 | Exported component has no permission | (a) `exported="false"`; (b) signature permission; (c) it is intentionally public | **MUST** confirm intent before closing it — closing a deliberately public component breaks integrations. |
| D9 | Secret must stay on the client | (a) move it server-side with a token exchange; (b) obfuscate and accept | **MUST** state that obfuscation is not key storage. Recommend (a). |
| D10 | Finding is in a **third-party SDK** | (a) upgrade; (b) remove; (c) accept and document | **MUST** report with the SDK and version. **MUST NOT** patch it silently. |
| D11 | Compliance driver (PCI, HIPAA, GDPR) | — | **MUST** state that this workflow audits against the engineering reference set, **not** a compliance standard, and that a compliance review is a different exercise. |

---

## 6. Implementation rules

**MUST:**

1. Default to audit; obtain approval per finding before fixing.
2. Escalate S1 findings immediately, not at the end.
3. State reachability for every finding: what, by whom, under what precondition.
4. Audit the **merged release manifest** and the **release** build, not source and debug.
5. Check git history for secrets, and require **rotation** for any exposure.
6. Verify, for every storage/auth fix, what happens to existing users' data and sessions.
7. Handle key invalidation so the app degrades to re-authentication.
8. Keep secrets out of `commonMain` — the iOS framework is not obfuscated.
9. Fix one finding per change, verified independently.

**MUST NOT:**

10. Include a working exploit, extraction path, or proof-of-concept payload in any output.
11. Fix an S1 finding without telling the user first — they may need to rotate credentials or ship a hotfix on a different timeline.
12. Change authentication, token storage, or transport trust as a side effect of another fix.
13. Silently orphan or delete stored user data.
14. Implement certificate pinning without an approved rotation plan.
15. Replace a cryptographic primitive that existing stored data depends on, without a migration (D7).
16. Treat obfuscation, `BuildConfig`, or NDK placement as secret storage.
17. Add a blanket iOS ATS exemption or Android cleartext permission.
18. Commit a credential, keystore, or config file as part of a fix.
19. Suppress a security lint finding to make the build pass.
20. Claim a finding is fixed without verifying against the **release** artifact.

---

## 7. Validation requirements

| # | Check | How | Required |
|---|---|---|---|
| V1 | **Merged release manifest** audited | inspect the merged file | MUST |
| V2 | No `debuggable=true` in the release manifest | inspect | MUST |
| V3 | Every component's `android:exported` explicit and intentional | inspect | MUST |
| V4 | No credential literal in source or `BuildConfig` | grep the release artifact and source | MUST |
| V5 | No credential in git history for findings raised | `git log -p -S'<pattern>'` | MUST |
| V6 | Verbose HTTP logging unreachable in release | inspect the release configuration path | MUST |
| V7 | Cleartext and user-CA trust confined to debug | inspect network security config and `Info.plist` | MUST |
| V8 | **Release build** behaves correctly after fixes | `bundleRelease` + install + exercise the affected flows | MUST in fix mode |
| V9 | Full test suite passes | module test tasks | MUST in fix mode |
| V10 | Existing users' sessions and data behave as decided (migrated or deliberately reset) | manual: upgrade over the previous release | MUST if storage/auth changed |
| V11 | Key invalidation path degrades to re-authentication, not a crash | simulate an invalidated key | MUST if key storage changed |
| V12 | No secret in `commonMain` | grep | MUST if KMP |
| V13 | iOS release binary contains no shared-code secret | `strings` on the framework for the specific patterns | SHOULD if KMP |
| V14 | Dependency vulnerability scan | project's scanner | SHOULD |
| V15 | No security lint suppression added | review the diff | MUST |

---

## 8. Failure handling

| Failure | Response |
|---|---|
| S1 finding discovered mid-audit | **MUST** stop and escalate before continuing. |
| Secret found in git history | → D2. **MUST** state that rotation is required and removal alone is insufficient. |
| Cannot determine whether a component is intentionally exported | → D8. **MUST** ask rather than close it. |
| Fix logs all users out | → D5. **MUST** surface before shipping, not after. |
| Storage migration loses data in testing | **MUST** stop immediately and report. Do not iterate on a migration that has destroyed test data without restating the plan. |
| Pinning implemented and staging breaks | Expected if the pin set is wrong. **MUST NOT** disable pinning app-wide as the fix; correct the pin set and the rotation plan. |
| Release build behaves differently from debug after a fix | R8 or a keep rule. **MUST NOT** disable minification. |
| A finding is in generated code | **MUST NOT** edit generated code. Report the generator and its input. |
| Finding is in a third-party SDK | → D10. |
| Audit scope too large to complete | **MUST** narrow by the threat model, complete that scope fully, and state what was not covered. **MUST NOT** report partial coverage as complete. |
| User asks for a penetration test or exploit development | **MUST** decline within this workflow — it audits code against a reference set. Offer the audit instead. |
| Compliance certification expected | → D11. **MUST** be explicit that this is not a compliance review. |

---

## 9. Completion criteria

### Audit mode

1. Every section of `../references/quality/security.md` covered, or the narrowing stated.
2. Merged release manifest audited (V1).
3. Git history checked for secrets (V5).
4. Every finding classified S1–S4 with reachability stated.
5. All S1 findings escalated immediately, before the report was completed.
6. KMP-specific exposure checked (V12) if applicable.
7. Remediation plan ordered by severity, each item with its blast radius and existing-user impact.
8. No exploit detail in any output.
9. Zero files changed.

### Fix mode

1. Only approved findings changed; one per change.
2. V1–V9, V12, V15 `PASS`.
3. V10 `PASS` if storage or auth changed; V11 `PASS` if key storage changed.
4. Existing-user path handled deliberately and recorded.
5. Any exposed credential rotated, or rotation explicitly handed to the user.
6. No pinning shipped without an approved rotation plan.
7. No security lint suppression added.
8. Verified against the **release** artifact, not debug.

---

## 10. Final report format

Base skeleton from `README.md`, with these additions.

> **MUST NOT** include exploit steps, payloads, or extraction procedures. Describe the class of problem, its reachability, and its consequence.

```markdown
## Security Audit — <AUDIT | FIX>: <scope>

### Outcome
<AUDIT COMPLETE | DONE | DONE WITH CAVEATS | BLOCKED | NEEDS DECISION> — one sentence.

### ⚠ Immediate escalations
<S1 findings, already reported to the user before this document was completed.
Each: what is reachable, by whom, consequence, required action (e.g. rotate credential X).
"None." if none.>

### Scope and basis
- Basis: `../references/quality/security.md`
- Scope audited: <whole app | area | current diff>
- Not covered: <explicit>
- Driver: <review | incident (D0) | compliance (D11) | store requirement>
- Merged release manifest audited: <yes>
- Git history checked for secrets: <yes>

### Findings
| # | Sev | Finding | Location | Reachable by | Precondition | Consequence | Status |
|---|---|---|---|---|---|---|---|
| 1 | S1 | production credential in source | `path:line` | anyone with the APK | none | account compromise | **ESCALATED** |
| 2 | S2 | token in plaintext DataStore | `path:line` | local attacker | rooted device | session theft | PLAN (D4) |
| 3 | S3 | `EncryptedSharedPreferences` | `path` | — | — | unmaintained; crash risk | PLAN (D3) |

### Remediation plan — ordered by severity
| Order | Finding | Fix | Blast radius | Existing-user impact | Approval needed |
|---|---|---|---|---|---|

### Fixes applied
| Finding | File | Change | Existing-user handling |
|---|---|---|---|

<"None — audit only." where applicable.>

### Credential rotation required
| Credential | Exposed where | Rotated by | Status |
|---|---|---|---|

<"None." if none. Note: removing a secret from source does not un-expose it.>

### Accepted risks
| Finding | Sev | Why accepted | Decided by |
|---|---|---|---|

### Validation performed
| # | Check | How | Result |
|---|---|---|---|
| V1 | Merged release manifest | | |
| V2 | No debuggable in release | | |
| V3 | `exported` explicit | | |
| V4 | No credential literals | | |
| V5 | Git history clean for raised findings | | |
| V6 | No verbose logging in release | | |
| V7 | Cleartext / user CA debug-only | | |
| V8 | Release build behaves correctly | | |
| V9 | Test suite | | |
| V10 | Existing users' data/sessions | | |
| V11 | Key invalidation degrades gracefully | | |
| V12 | No secret in commonMain | | |
| V13 | iOS binary free of shared secrets | | |
| V14 | Dependency vulnerability scan | | |
| V15 | No security lint suppression added | | |

### Not done
### Observations
### Decisions needed
<D1–D11 triggered, with options and a recommendation.>
```
