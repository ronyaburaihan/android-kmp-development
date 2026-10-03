# Subscriptions and In-App Purchases

**Scope:** Play Billing obligations, StoreKit, entitlement architecture, RevenueCat, purchase lifecycle, testing.
**Applies to:** Android and iOS monetised apps, including KMP.
**Official sources:**
- <https://developer.android.com/google/play/billing>
- <https://developer.android.com/google/play/billing/release-notes>
- Apple StoreKit 2 and App Store Connect subscription documentation
- <https://www.revenuecat.com/docs/getting-started/installation/kotlin-multiplatform>

**Rule levels:** see `../README.md`.

---

## Play Billing version — a publishing gate, not a preference

[OFFICIAL] <https://developer.android.com/google/play/billing/deprecation-faq> — verified 2026-10-03. Every Billing Library version has a **two-year deprecation cycle**.

| Version | New apps and updates must move off it by | Extension to |
|---|---|---|
| 7 | **2026-08-31** | 2026-11-01 |
| 8 | 2027-08-31 | 2027-11-01 |
| 9 (latest) | 2028-08-31 | 2028-11-01 |

Unmaintained APKs keep working; the deadline applies to new apps and updates. Deprecated versions produce warnings in Play Console; extensions are requested from the **Policy status** page.

- **MUST** keep the Billing Library at or above the published floor. This blocks releases; it is not a code-quality concern.
- **SHOULD** move to v9 rather than stopping at v8 — v8's own deadline is only a year later. Migration guide: `/google/play/billing/migrate-gpblv9`.
- **MUST** verify the current floor before every major release cycle. See `../version-matrix.md`.

---

## There is no multiplatform billing API

Neither Google nor Apple ships a cross-platform purchase API. Two approaches:

| Approach | Summary |
|---|---|
| **A. Native per platform behind a common interface** | Play Billing `BillingClient` in `androidMain`, StoreKit 2 in Swift or `iosMain`. Full control; two implementations of flow handling. |
| **B. RevenueCat `purchases-kmp`** | Vendor SDK wrapping Play Billing and StoreKit plus RevenueCat's backend. |

### Approach B — RevenueCat `purchases-kmp` (3.7.0)

What it provides: a single `commonMain` integration wrapping Google Play Billing on Android and StoreKit on iOS, server-side receipt validation, entitlement state, cross-platform subscriber identity, and a **Compose Multiplatform paywall component**. `purchases-kmp` 3.0.0 significantly simplified iOS integration.

Trade-offs that **MUST** be disclosed:

- A paid third-party dependency sits **in the purchase path**.
- The vendor becomes a source of truth for entitlement state.
- Migrating existing subscribers onto it is a project in itself.

Sample: <https://github.com/RevenueCat/cat-paywall-kmp>.

**MAY** choose either approach. **MUST** record the decision, because it is difficult to reverse once subscribers exist.

---

## Entitlement architecture — MUST

### Entitlement state is server-authoritative

```kotlin
// :domain/commonMain
data class Entitlements(
    val isPremiumActive: Boolean,
    val expiresAt: Instant?,
    val isInGracePeriod: Boolean,
)

interface EntitlementRepository {
    fun observe(): Flow<Entitlements>
    suspend fun refresh()
}

interface PurchaseClient {
    suspend fun products(ids: List<String>): List<Product>
    suspend fun purchase(productId: String): PurchaseResult
    suspend fun restore()
}
```

```kotlin
// CORRECT — the gate reads entitlement state, which the server confirmed
class CanAccessPremiumUseCase(private val entitlements: EntitlementRepository) {
    operator fun invoke(): Flow<Boolean> =
        entitlements.observe().map { it.isPremiumActive || it.isInGracePeriod }
}
```

```kotlin
// WRONG — client-side gate from a local purchase flag
class CanAccessPremiumUseCase(private val prefs: SettingsDataSource) {
    operator fun invoke(): Flow<Boolean> = prefs.observeBoolean("is_premium")
}
```

Why the wrong form is a problem: a local boolean is trivially modified on a rooted or jailbroken device, survives a refund, and is wrong after a subscription lapses, a chargeback, or a cross-device sign-in. Revenue is being protected by a value the attacker controls.

- **MUST** verify receipts server-side — Google Play Developer API on Android, App Store Server API on iOS — or delegate that to a provider that does.
- **MUST NOT** gate a paid feature on a client-side purchase flag alone. [DEFAULT as an architectural rule; it reflects both stores' documented verification model]
- **SHOULD** cache the server's entitlement answer locally so the app works offline, and **MUST** treat the cache as advisory with an expiry, not as truth.
- **SHOULD** use the **Play Integrity API** before granting a high-value entitlement. See `../quality/security.md`.

### Layering — MUST

**MUST** keep `BillingClient`, StoreKit and any vendor SDK type inside `:data`. **MUST NOT** let them appear in `:domain`, a ViewModel, or the UI. This is the repository rule — see `../android/app-architecture.md`.

```kotlin
// WRONG
class PaywallViewModel(private val billingClient: BillingClient) : ViewModel()
```

---

## Purchase lifecycle — MUST handle all of it

The failure modes below are the ones that produce support tickets and refunds. Each **MUST** be handled explicitly.

| State | Requirement |
|---|---|
| **Pending purchase** | A purchase may be `PENDING` (cash, delayed payment). **MUST NOT** grant entitlement on a pending purchase, and **MUST** handle the later completion. |
| **Acknowledgement** | A Play purchase **MUST** be acknowledged, or Play refunds it automatically. **[UNVERIFIED]** the window is commonly cited as 3 days — confirm in the Play Billing documentation for the version in use. |
| **Restore / transfer** | **MUST** provide a restore path. On iOS this is an App Review requirement; on Android it is a reinstall and multi-device requirement. |
| **Grace period** | Payment failed but access continues. **MUST** keep access and prompt for payment-method update. |
| **Account hold** | Access **MUST** be revoked, and **MUST** be restorable without a new purchase. |
| **Upgrade / downgrade / proration** | Proration semantics differ between stores. **MUST** implement and test per platform. |
| **Refund / revocation** | **MUST** react to Real-time Developer Notifications (Play) and App Store Server Notifications (Apple) and revoke server-side. |
| **Cancellation** | Access continues until expiry. **MUST NOT** revoke at cancellation time. |

**MUST NOT** treat "purchase succeeded" as the end of the flow. It is the beginning of a state machine.

---

## Store-side configuration

**MUST** mirror product identifiers across both stores where a product is the same offering, and keep them in one place in code:

```kotlin
// :domain/commonMain
object ProductIds {
    const val PREMIUM_MONTHLY = "premium_monthly"
    const val PREMIUM_ANNUAL = "premium_annual"
}
```

**MUST NOT** assume parity in: price points, currency handling and rounding, introductory and trial offer semantics, offer eligibility rules, family sharing, or regional tax display. These differ between Play and the App Store and are the usual source of a paywall that is correct on one platform and wrong on the other.

**SHOULD** fetch product metadata (price, title, period) from the store at runtime and render it, rather than hardcoding prices. A hardcoded price is wrong in every currency but one, and changes without a release.

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| Client-only entitlement check | revenue protected by an attacker-controlled value |
| Not acknowledging a Play purchase | Play auto-refunds; the user paid and lost access |
| Ignoring `PENDING` state | entitlement granted for an unpaid purchase, or never granted after payment clears |
| No restore path | iOS App Review rejection; broken reinstall on both platforms |
| Revoking access at cancellation instead of expiry | paying users lose access early |
| `BillingClient` / StoreKit type in a ViewModel or `:domain` | untestable; leaks the store into the UI |
| Hardcoded prices | wrong in most currencies; needs a release to change |
| Assuming proration and trial semantics match across stores | silent revenue and eligibility bugs |
| Letting the Billing Library fall below the publishing floor | cannot ship any update, including a crash fix |
| Treating a vendor SDK's local state as authoritative | same exposure as a client-only flag |
| Testing only the happy path | every listed lifecycle state is a production incident waiting |

---

## Android / iOS differences

| Concern | Android (Play Billing) | iOS (StoreKit) |
|---|---|---|
| Client API | `BillingClient` | StoreKit 2 (`Product.purchase`) |
| Server verification | Google Play Developer API | App Store Server API |
| Server notifications | Real-time Developer Notifications (RTDN) | App Store Server Notifications |
| Acknowledgement | **required**, or auto-refund | not applicable |
| Restore | query existing purchases | explicit restore; **App Review requires it** |
| Version floor | **enforced publishing gate** | no equivalent library floor; Xcode version floor applies |
| Test accounts | Play license testers + closed testing track | sandbox accounts; StoreKit configuration files for local testing |
| Proration | Play proration modes | StoreKit upgrade/downgrade rules |
| Paywall review | Play policy | **App Review inspects the paywall and the restore flow** |

**MUST** implement and test the Android acknowledgement step and the iOS restore step as distinct work items. Each is mandatory on exactly one platform and has no counterpart on the other, which is why each is routinely forgotten.

---

## Testing recommendations

**MUST:**

- Fake `PurchaseClient` and `EntitlementRepository` in `commonTest` and test the **entitlement state machine** exhaustively: active, pending, grace period, account hold, expired, refunded, restored.

```kotlin
@Test
fun grantsAccessDuringGracePeriod() = runTest {
    val repo = FakeEntitlementRepository()
    val useCase = CanAccessPremiumUseCase(repo)

    repo.emit(Entitlements(isPremiumActive = false, expiresAt = past, isInGracePeriod = true))

    assertTrue(useCase().first())
}

@Test
fun deniesAccessOnAccountHold() = runTest {
    val repo = FakeEntitlementRepository()
    val useCase = CanAccessPremiumUseCase(repo)

    repo.emit(Entitlements(isPremiumActive = false, expiresAt = past, isInGracePeriod = false))

    assertFalse(useCase().first())
}
```

- Test on **Android with Play license testers**, through a **closed testing track**. Billing behaviour cannot be validated from a locally built debug APK.
- Test on **iOS with sandbox accounts**, and with **StoreKit configuration files** for fast local iteration.
- Test the **restore** flow on a clean device install on both platforms, and verify entitlement returns without a new charge.
- Test **acknowledgement** on Android by confirming a purchase is not auto-refunded.

**SHOULD:**

- Run server-side verification against the stores' sandbox environments in a scheduled integration job, not in the merge gate.
- Exercise RTDN and App Store Server Notification handlers with recorded payloads, including refund and revocation.
- Verify the paywall renders correct store-fetched prices in at least two currencies before release.

**MUST NOT** ship a monetisation change validated only by unit tests. Every store integration bug of consequence lives in the real purchase flow.

---

## Cross-references

- Entitlement repository layering: `../android/app-architecture.md`, `../architecture/clean-architecture.md`
- Play Integrity before granting entitlements: `../quality/security.md`
- Why the local cache must not be authoritative: `../quality/security.md`
- Closed testing track and rollout: `../release/android-release.md`
- App Review and TestFlight: `../release/ios-release.md`
- Billing version floor: `../version-matrix.md`
- Paywall UI state modelling: `../architecture/mvvm-udf.md`
