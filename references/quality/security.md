# Security

**Scope:** Data storage, IPC, permissions, network transport, WebView, cryptography, key storage, secure storage across Android and iOS, secret handling.
**Applies to:** Android app modules and the shared module's security-relevant surface.
**Official sources:**
- <https://developer.android.com/privacy-and-security/security-best-practices>
- <https://developer.android.com/privacy-and-security/keystore>
- <https://developer.android.com/google/play/integrity>

**Rule levels:** see `../README.md`. All Android rules are [OFFICIAL] unless tagged.

---

## Data storage

### Internal storage is the default — MUST

**MUST** store all private user data in internal storage (`filesDir`). It is sandboxed per app, needs no permission, is inaccessible to other apps, and is removed on uninstall.

```kotlin
// CORRECT
File(filesDir, "session.json").bufferedWriter().use { it.write(contents) }
```

### External storage

- App-specific files: `getExternalFilesDir()`.
- Media: the MediaStore API.
- Other shared files: the Storage Access Framework.
- **MUST** verify removable-storage availability before access.
- **SHOULD** validate integrity with a hash (the official example uses SHA-512).
- **MUST NOT** place sensitive data in external storage without encryption.

### Cache

- ≤1 MB → `cacheDir` (private to the app).
- \>1 MB → `externalCacheDir`, which has **no enforced security** — any app holding `WRITE_EXTERNAL_STORAGE` can read it.
- **MUST NOT** cache sensitive data in `externalCacheDir`.

### SharedPreferences

- **MUST** pass `MODE_PRIVATE` to `getSharedPreferences`.
- **MUST NOT** use `MODE_WORLD_READABLE` or `MODE_WORLD_WRITABLE` — deprecated.
- **MUST NOT** share preferences between apps; use a deliberate IPC mechanism.
- **SHOULD NOT** use `SharedPreferences` in new code — see `../libraries/room-datastore.md`.

---

## Secure storage — the critical change

### `EncryptedSharedPreferences` is deprecated — MUST NOT use

`androidx.security:security-crypto` (Jetpack Security Crypto), including `EncryptedSharedPreferences` and `EncryptedFile`, is **deprecated** and is no longer recommended or maintained. [OFFICIAL — the deprecated status]

> **[UNVERIFIED]** Secondary sources report: deprecated at 1.1.0-alpha07 in April 2025; causes were Keystore behaviour inconsistency across OEMs and OS versions, main-thread StrictMode violations, and keyset-corruption crashes; the suggested replacement direction is DataStore + Google Tink `StreamingAead`, migrating with `SharedPreferencesMigration`. **MUST** verify the deprecation details and the officially suggested replacement before writing a migration. Only the deprecated status is confirmed.

```kotlin
// WRONG — deprecated, unmaintained library
val prefs = EncryptedSharedPreferences.create(
    context, "secret_prefs", masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
)
```

### What to do instead

**MUST** make the decision explicitly and record it. Three defensible positions:

| Option | When | Notes |
|---|---|---|
| **Android Keystore directly** for the key, app-level encryption for the payload | a small number of secrets | Most control, no deprecated dependency. Keystore operations **MUST** be off the main thread. |
| **DataStore + an explicit crypto layer** (e.g. Tink) | structured encrypted data | The reported replacement direction. DataStore is async by construction, so no main-thread I/O. [UNVERIFIED as the official recommendation] |
| **Platform keychain/keystore behind a domain interface** | KMP projects | The only workable KMP shape. See below. |

**MUST NOT** hold a decryption key in code, resources, `BuildConfig`, or a `.so` file. Obfuscation is not key storage.

### Secure storage in KMP — MUST invert

**There is no AndroidX secure-storage API with a KMP target.** [OFFICIAL by absence — see the AndroidX KMP support table]

**MUST** declare the capability as an interface in the domain layer and implement it per platform:

```kotlin
// :domain/commonMain
interface SecureStore {
    suspend fun put(key: String, value: String)
    suspend fun get(key: String): String?
    suspend fun remove(key: String)
}
```

```kotlin
// :data/androidMain — Keystore-backed; MUST run off the main thread
internal class AndroidSecureStore(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : SecureStore { /* Keystore-wrapped key + encrypted payload */ }

// :data/iosMain — Keychain-backed
internal class KeychainSecureStore : SecureStore { /* Security framework / SecItem* */ }
```

```kotlin
// WRONG — expect/actual reaching the platform SDK from the domain
// :domain/commonMain
expect suspend fun secureStorePut(key: String, value: String)
```

Why the wrong form is a problem: the domain module now needs an `actual` per target, cannot be unit-tested without one, and breaks whenever a target is added. The interface form has a trivial in-memory fake. This is the same rule as `../kmp/project-structure.md` and `../architecture/clean-architecture.md`.

**MUST NOT** put tokens, credentials or PII in DataStore Preferences or Room unencrypted. Both are plaintext at rest.

### Platform secure-storage differences — MUST account for

| Concern | Android Keystore | iOS Keychain |
|---|---|---|
| Hardware backing | when available (StrongBox / TEE) | Secure Enclave for supported key types |
| Backup exposure | keys are non-exportable; **encrypted payloads in `filesDir` may be included in Auto Backup** | Keychain items may sync to iCloud Keychain unless the accessibility class forbids it |
| Biometric gating | `setUserAuthenticationRequired` | `kSecAccessControl` with biometry |
| Failure mode | keyset corruption / `KeyPermanentlyInvalidatedException` after biometric enrolment changes | item not found after a restore to a new device |
| Clearing | on uninstall | **MAY survive app deletion** for some accessibility classes |

**MUST** handle the invalidation case: a key can become permanently unusable (biometric re-enrolment on Android, device restore on iOS). The app **MUST** degrade to re-authentication, not crash.

**SHOULD** exclude sensitive files from platform backup explicitly on each platform when the data must not leave the device.

---

## Inter-process communication (Android)

### Components

- **MUST** declare `android:exported` explicitly on every activity, service and receiver that has an intent filter.
- **MUST** set `android:exported="false"` on components not intended for other apps. This matters because the default was `true` on API ≤16.

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="com.example.myapp.fileprovider"
    android:exported="false" />
```

- **MUST** make broadcast receivers non-exported by default.
- **MUST NOT** use sticky broadcasts.
- **SHOULD** use in-process mechanisms (a shared `Flow`, not a broadcast) for internal signalling.

### Intents and file sharing

- **MUST** share data via `content://` URIs through `FileProvider`. **MUST NOT** put `file://` URIs in intent data.
- **MUST** grant temporary access with `FLAG_GRANT_READ_URI_PERMISSION` / `FLAG_GRANT_WRITE_URI_PERMISSION` rather than broad permissions.
- **MUST** show an app chooser when an implicit intent could resolve to more than one app.
- **MUST** use `FLAG_IMMUTABLE` on every `PendingIntent` unless mutability is genuinely required.
- **MUST NOT** place sensitive data in an implicit intent's extras — implicit-intent hijacking is a real attack.

```kotlin
// CORRECT
Intent(Intent.ACTION_VIEW).apply {
    data = Uri.parse("content://com.example/personal-info.pdf")
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}.also { intent ->
    intent.resolveActivity(packageManager)?.run { startActivity(intent) }
}
```

### Sharing between your own apps

**MUST** use a signature-level permission:

```xml
<permission
    android:name="com.example.permission.SYNC_DATA"
    android:protectionLevel="signature" />
```

**SHOULD NOT** put `android:permission` on an exported component as the only protection; prefer signature permissions or no export.

### Safer intents (API 36, opt-in)

**SHOULD** enable `android:intentMatchingFlags="enforceIntentFilter"`. See `../android/platform-requirements.md`.

---

## Permissions

- **MUST** request only the minimum set, and relinquish permissions no longer needed.
- **SHOULD** defer to the system via an intent where that removes a permission entirely.

```kotlin
// CORRECT — no READ_CONTACTS / WRITE_CONTACTS needed
Intent(Intent.ACTION_INSERT).apply {
    type = ContactsContract.Contacts.CONTENT_TYPE
}.also { intent ->
    intent.resolveActivity(packageManager)?.run { startActivity(intent) }
}
```

- File I/O needs **no** special permission when using the Storage Access Framework or MediaStore.
- **MUST** gate sensitive information behind a device credential (PIN/password/pattern) or **biometric** authentication where the data warrants it.

**iOS difference — MUST:** every protected capability requires an `Info.plist` usage-description string (`NSCameraUsageDescription`, `NSPhotoLibraryUsageDescription`, location strings). **A missing string is a launch-time crash**, not a denied permission. See `../kmp/ios-interop.md`.

---

## Network transport

### HTTPS and cleartext — MUST

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.example.com</domain>
    </domain-config>
    <debug-overrides>
        <trust-anchors>
            <certificates src="user" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

```xml
<application android:networkSecurityConfig="@xml/network_security_config" />
```

- **MUST** use HTTPS with trusted CAs for all traffic.
- **MUST** set `cleartextTrafficPermitted="false"` for production domains.
- **MUST** confine user-installed certificate trust to `<debug-overrides>`. **MUST NOT** trust user certificates in a release build.
- A custom CA cannot be expressed in the config file; it needs a custom `TrustManager`.
- **SHOULD** keep the security provider current with `ProviderInstaller.installIfNeededAsync(...)`.

### iOS — App Transport Security

**MUST NOT** add a blanket `NSAllowsArbitraryLoads` exemption. ATS blocks plain HTTP by default; use a narrowly scoped `NSExceptionDomains` entry if an exemption is unavoidable, and record why.

### Certificate pinning

> **[UNVERIFIED]** Pinning with Ktor is configured per engine (OkHttp vs Darwin), so it requires two platform implementations. The exact current APIs were not established.

**MUST** define a certificate-rotation plan **before** pinning anything. A pinned certificate that expires bricks every installed client and cannot be fixed by a server change. **SHOULD** pin to an intermediate or a backup pin set, not a single leaf certificate.

Networking implementation: `../libraries/ktor-networking.md`.

---

## WebView

- **MUST** restrict loaded content with an allowlist. Load only content you control.
- **MUST NOT** enable a JavaScript interface unless you completely control the WebView's content. `addJavascriptInterface` on untrusted content is remote code execution.
- **SHOULD** use HTML message channels (`createWebMessageChannel`, API 23+) for host↔page communication instead of a JavaScript interface.

```kotlin
// CORRECT — message channel, no JS bridge
val channel = webView.createWebMessageChannel()
channel[0].setWebMessageCallback(object : WebMessagePort.WebMessageCallback() {
    override fun onMessage(port: WebMessagePort, message: WebMessage) { handle(message) }
})
channel[1].postMessage(WebMessage("init"))
```

---

## Cryptography

| Use | MUST use | MUST NOT use |
|---|---|---|
| Symmetric encryption | AES | DES, 3DES, RC4 |
| Asymmetric | RSA | DSA |
| Hashing | SHA-512 | MD5, SHA-1 |
| MAC | HMAC-SHA256 | custom constructions |

- **MUST** store keys in the **Android Keystore** (hardware-backed where available) / iOS **Keychain**.
- **MUST NOT** hardcode cryptographic secrets.
- **MUST NOT** implement a cipher, a key-derivation function, or a padding scheme by hand.
- **SHOULD** use hardware key attestation when verifying that a key pair is hardware-backed.
- **MUST** run crypto operations off the main thread.

```kotlin
// CORRECT — official hashing example, off the main thread
suspend fun calculateHash(stream: InputStream, ioDispatcher: CoroutineDispatcher): String =
    withContext(ioDispatcher) {
        val digest = MessageDigest.getInstance("SHA-512")
        DigestInputStream(stream, digest).use { while (it.read() != -1) Unit }
        digest.digest().joinToString(":") { "%02x".format(it) }
    }
```

---

## Secrets in the build

**MUST NOT** commit API keys, signing keys, service-account JSON, or `google-services.json` for production to version control.

**MUST NOT** place a secret in `commonMain` source or in the exported iOS framework. Both are readable in the shipped binary — the KMP framework is not obfuscated and R8 does not apply to it.

**SHOULD** supply build-time values from CI secrets or a local properties file that is git-ignored, and **SHOULD** keep high-value secrets server-side entirely, exchanging them for short-lived tokens.

```kotlin
// WRONG — shipped in every binary, readable with strings(1)
internal const val API_SECRET = "sk_live_9f3b..."
```

---

## Platform hardening

- **MUST NOT** ship `android:debuggable="true"`.
- **MUST NOT** load or execute code from an untrusted source. `exec()` from the app home directory is blocked on API 29+ (W^X), as is in-memory modification of executable code via `dlopen()` text relocations.
- **SHOULD** use the **Play Integrity API** to check device and app integrity before high-value operations (payments, entitlement grants).
- **SHOULD** integrate Safe Browsing where the app opens arbitrary URLs.
- **SHOULD** support Advanced Protection Mode for users with elevated needs.
- **MUST** keep the SDK, NDK and third-party dependencies patched. A dependency with a known CVE is the most common real-world vulnerability.

---

## Logging — MUST

**MUST NOT** log tokens, credentials, PII, full request/response bodies, or headers in a release build.

```kotlin
// WRONG — logs Authorization headers and response bodies
install(Logging) { level = LogLevel.ALL }

// CORRECT
if (isDebug) install(Logging) { level = LogLevel.HEADERS }
```

**MUST** strip or gate verbose logging in release. **SHOULD** verify this in CI by inspecting the release build rather than trusting the source.

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| `EncryptedSharedPreferences` | deprecated, unmaintained, known OEM failures |
| Tokens in DataStore Preferences or Room | plaintext at rest |
| Secret constant in `commonMain` or the iOS framework | extractable with `strings` |
| `NSAllowsArbitraryLoads` / `cleartextTrafficPermitted="true"` in release | transport security disabled app-wide |
| Trusting user certificates outside `<debug-overrides>` | trivially MITM-able with a sideloaded CA |
| Pinning without rotation | expiry bricks installed clients |
| `addJavascriptInterface` on untrusted content | remote code execution |
| `file://` URI in an intent | throws on modern Android; leaks paths |
| Mutable `PendingIntent` | intent-redirect attacks |
| Sensitive data in `externalCacheDir` | readable by other apps |
| MD5 / SHA-1 / DES / RC4 | broken primitives |
| Hand-rolled crypto | implementation flaws |
| `LogLevel.ALL` in release | credentials in logs |
| `expect`/`actual` reaching the Keychain/Keystore from the domain | untestable, target-coupled |
| Crashing on key invalidation | app becomes permanently unusable after a biometric re-enrolment |

---

## Testing recommendations

Security is verified by review, static analysis and release-artifact inspection more than by unit tests. Concretely:

**MUST:**

- Verify the **merged release manifest** in CI (`build/intermediates/merged_manifests/release/AndroidManifest.xml`) for `debuggable`, explicit `exported`, and the network security config reference. Source manifests do not reflect manifest merging from libraries.
- Verify the **release** build has logging disabled and no debug-only trust anchors. Assert on the built artifact or the merged resources, not on source.
- Test the `SecureStore` contract against an in-memory fake in `commonTest`, and against the real implementation in `androidDeviceTest` / `iosTest` — Keystore and Keychain behaviour cannot be faked meaningfully.
- Test the key-invalidation path explicitly: simulate a missing or invalidated key and assert the app re-authenticates rather than crashing.

**SHOULD:**

- Run a dependency-vulnerability scan in CI and fail on known-exploitable CVEs.
- Assert that no `LogLevel.ALL`/`BODY` configuration is reachable in a release variant.
- Include a secret-scanning hook so a committed key fails pre-commit, not review.
- Review every new `exported="true"` component and every new permission as a deliberate decision.

**MUST NOT** write a test that embeds a real production credential to "test the real path".

---

## Cross-references

- API 36 behaviour changes affecting permissions, intents and local network: `../android/platform-requirements.md`
- Why not `SharedPreferences`, and where structured data goes: `../libraries/room-datastore.md`
- Client logging configuration and transport: `../libraries/ktor-networking.md`
- Interface-over-`expect` rule: `../kmp/project-structure.md`, `../architecture/clean-architecture.md`
- iOS `Info.plist` requirements and crash-on-missing-string: `../kmp/ios-interop.md`
- Signing and release artifact handling: `../release/android-release.md`, `../release/ios-release.md`
- Entitlement verification must be server-side: `../integrations/subscriptions.md`
- Deprecation list: `../deprecations.md`
