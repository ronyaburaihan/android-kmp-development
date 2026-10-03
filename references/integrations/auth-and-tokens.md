# API Authentication and Token Management

**Scope:** Bearer-token flows with Ktor, refresh coordination, where tokens are stored, sign-out propagation, platform differences.
**Official sources:**
- <https://ktor.io/docs/client-bearer-auth.html>
- <https://developer.android.com/privacy-and-security/keystore>
- Verified reference implementation: `../../examples/user-profile/src/commonMain/kotlin/com/example/userprofile/network/`

**Rule levels:** see `../README.md`. Ktor behaviour is [OFFICIAL]; the architecture is [DEFAULT].

---

## The shape — three seams

```
TokenStore (interface)  ──►  platform secure storage      Keystore-wrapped / Keychain
TokenRefresher (interface) ──►  the refresh endpoint      plain suspend fun
TokenManager            ──►  one refresh at a time; publishes SessionEvent on sign-out
        │
        ▼
Ktor Auth plugin, bearer { loadTokens / refreshTokens / sendWithoutRequest }
```

All of this is `commonMain`. The compiled example has every piece and tests for each.

---

## Ktor bearer auth — MUST configure all three callbacks

```kotlin
install(Auth) {
    bearer {
        loadTokens { tokens.current()?.toBearer() }           // from the store; cached by Ktor
        refreshTokens { tokens.refresh()?.toBearer() }        // called once after a 401
        sendWithoutRequest { it.url.host == apiHost }         // attach on first request to our API
    }
}
```

[OFFICIAL] behaviour that shapes the design:

- Credentials are sent **only after a 401** unless `sendWithoutRequest` says otherwise. For your own API, **MUST** return `true` for its host — otherwise every session starts with a wasted 401 round-trip.
- On a 401, Ktor calls `refreshTokens` **once** even when several requests fail concurrently, then retries them with the new token.
- Inside `refreshTokens`, the refresh request itself **MUST** call `markAsRefreshTokenRequest()`, or a 401 from the refresh endpoint triggers another refresh — an infinite loop.
- `refreshTokens` returning `null` means "no credentials"; the original 401 is then surfaced to the caller.
- Ktor caches loaded tokens; `cacheTokens = false` reloads per request (rarely wanted).

**MUST** keep `sendWithoutRequest` host-scoped. Returning `true` unconditionally sends your bearer token to every third-party host the app ever calls.

---

## Refresh coordination outside Ktor — MUST

Ktor serialises refreshes it triggers. Anything else that can trigger one — a background sync, a WebSocket reconnect, a second `HttpClient` — can race it. The example's `TokenManager` holds a `Mutex`:

```kotlin
public suspend fun refresh(): AuthTokens? = refreshMutex.withLock {
    val existing = store.read() ?: return@withLock null
    val fresh = refresher.refresh(existing.refresh)
    if (fresh == null) { store.clear(); sessionEvents.publish(SessionEvent.SignedOut); null }
    else { store.write(fresh); sessionEvents.publish(SessionEvent.TokenRefreshed(fresh.expiresAtEpochMs)); fresh }
}
```

**MUST:**

- One refresh in flight at a time, process-wide.
- A rejected refresh token ends the session: clear the store and **broadcast** it. Repositories drop caches, the UI navigates to sign-in, analytics resets its user id. This is the legitimate `SharedFlow` use — see `../kotlin/coroutines-and-flow.md` § 9.
- Refresh **before** expiry when the expiry is known, not only on 401. A 401-only strategy fails the first request of every session after a long background.

**MUST NOT** retry a refresh that returned a definitive rejection. The refresh token is gone; retrying is a login loop.

---

## Where tokens live — MUST

| Store | Verdict |
|---|---|
| Platform secure storage behind a domain interface — Keystore-wrapped on Android, Keychain on iOS | **MUST** |
| DataStore Preferences / Room, plaintext | **MUST NOT** — plaintext at rest |
| `EncryptedSharedPreferences` | **MUST NOT** — deprecated, unmaintained |
| In memory only | acceptable for a session that may be re-established silently; otherwise the user signs in on every launch |
| `BuildConfig`, a constant, `commonMain` | **MUST NOT** for any credential; shared code ships unobfuscated to iOS |

`TokenStore` is the seam. Its platform implementations and the invalidation handling (a Keystore key can be permanently invalidated by biometric re-enrolment; a Keychain item can vanish on restore) are in `../quality/security.md`.

**MUST** handle the "store returns null unexpectedly" path as *signed out*, never as a crash.

---

## Token lifecycle rules — MUST

- **Access tokens are short-lived; treat them as disposable.** Never persist an access token without its refresh token and expiry.
- **Clock skew.** Compare expiry against server time where available, or refresh a margin early (60 s) rather than at the exact instant.
- **Sign-out clears everything**: store, Ktor's cached tokens (`client.authProviders` / recreating the client), repository caches, DataStore keys that identify the user.
- **Logging**: never. `LogLevel.ALL`/`BODY` print the `Authorization` header. See `../libraries/ktor-networking.md`.
- **Rotation on refresh**: when the server issues a new refresh token, **MUST** persist it atomically with the access token. Writing the access token first and crashing leaves a mismatched pair.

---

## OAuth / third-party sign-in

**MUST** use the system browser flow (Custom Tabs on Android, `ASWebAuthenticationSession` on iOS) via PKCE for an OAuth provider. **MUST NOT** collect a third-party password in-app, and **MUST NOT** use a WebView for OAuth — providers block it and it exposes the cookie jar.

The shared module owns: the PKCE verifier/challenge generation, the token exchange call, `TokenManager`. The platform owns: launching the browser session and receiving the redirect.

---

## Android / iOS differences

| Concern | Android | iOS |
|---|---|---|
| Secure store | Keystore-wrapped key + encrypted blob in `filesDir` | Keychain (`kSecClassGenericPassword`), accessibility `AfterFirstUnlockThisDeviceOnly` for a session token |
| Backup exposure | encrypted blob may be in Auto Backup; key is not — restored blob is unreadable → treat as signed out | Keychain may sync to iCloud unless the accessibility class forbids it; **SHOULD** forbid for session tokens |
| Invalidation | `KeyPermanentlyInvalidatedException` after biometric change | item missing after restore to a new device |
| Browser auth | Custom Tabs + app link redirect | `ASWebAuthenticationSession` + URL scheme / universal link |
| Shared code | `TokenStore` interface, `TokenManager`, Ktor config | same — the interface implementation differs |
| Swift surface | n/a | `TokenManager` must not expose `StateFlow`; sign-out notification goes through a callback on the facade — `../kmp/ios-interop.md` |

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| Tokens in DataStore/Room plaintext | session theft on a compromised device |
| `sendWithoutRequest { true }` | bearer token sent to every host |
| No `markAsRefreshTokenRequest()` on the refresh call | infinite refresh loop on a 401 from the refresh endpoint |
| Refresh triggered from several places with no mutex | duplicate refreshes; one invalidates the other's rotated token |
| Retrying a rejected refresh | login loop |
| Refresh only on 401 | first request after background always fails |
| Access token persisted without expiry/refresh | cannot decide whether to refresh |
| OAuth in a WebView | blocked by providers; cookie exposure |
| Sign-out that clears the store but not caches | next user sees previous user's data |
| Credential in `commonMain` | readable in the iOS binary |
| Logging request headers in release | tokens in logs |

---

## Testing recommendations

All in `commonTest`, verified in the example:

- **MUST** test `TokenManager`: successful refresh persists and publishes; rejected refresh clears and signs out; concurrent calls serialise; no stored token → no-op.
- **MUST** test the Ktor configuration with `MockEngine`: the bearer header is attached on the first request to the API host (`sendWithoutRequest`); a 401 triggers one refresh and a retry; a 401 from the refresh endpoint does not loop.
- **MUST** test the `TokenStore` fake contract and, separately, the real platform implementations in `androidDeviceTest` / `iosTest` including the invalidated-key path.
- **MUST** test sign-out propagation: every `SessionEvent.SignedOut` consumer resets.
- **SHOULD** test expiry-margin refresh with an injected clock.

---

## Cross-references

- Client construction and the single-`HttpClient` rule: `../libraries/ktor-networking.md`
- `SharedFlow` for sign-out broadcast: `../kotlin/coroutines-and-flow.md`
- Secure storage implementations and key invalidation: `../quality/security.md`
- Facade shape for Swift (no `StateFlow`): `../kmp/ios-interop.md`
- The compiled, tested implementation: `../../examples/user-profile/`
