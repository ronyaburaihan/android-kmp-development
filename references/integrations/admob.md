# AdMob

**Scope:** Google Mobile Ads on Android and iOS, consent, test ads, KMP layering.
**Official sources:**
- <https://developers.google.com/admob/android/quick-start>
- <https://developers.google.com/admob/ios/quick-start>
- <https://developers.google.com/admob/android/privacy> (User Messaging Platform)

**Rule levels:** see `../README.md`. SDK facts are [OFFICIAL]; the KMP layering is [DEFAULT].

---

## The honest position

**There is no official Google Mobile Ads SDK for Kotlin Multiplatform.** Google ships native SDKs for Android (`com.google.android.gms:play-services-ads`, **25.5.0** verified 2026-10-03) and iOS (`Google-Mobile-Ads-SDK` via SPM/CocoaPods). Community KMP wrappers exist (`KMP-Ads`, `admob-compose-multiplatform`, `MultiAds`, `basic-ads`); each is third-party and **MUST** be recorded as such if adopted. [OFFICIAL by absence]

**MUST NOT** describe any wrapper as official.

---

## Android setup — MUST

```kotlin
dependencies { implementation("com.google.android.gms:play-services-ads:25.5.0") }
```

```xml
<application>
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY" />   <!-- the app id, not an ad unit id -->
</application>
```

```kotlin
// Once, at launch, off the main thread. The listener fires on completion or after ~30 s.
appScope.launch(Dispatchers.IO) { MobileAds.initialize(applicationContext) { } }
```

Requirements [OFFICIAL]: `minSdk` ≥ 24, `compileSdk` ≥ 35. Initialise on a background thread — it blocks.

**Consent first.** For EEA/UK/Switzerland users the **User Messaging Platform (UMP)** consent flow **MUST** complete before the SDK is initialised and before any ad request. Load ads only when `canRequestAds` is true.

---

## Test ads — MUST during development

**MUST** use Google's sample ad unit IDs, or register the device as a test device, in every non-production build. Requesting live ads from a debug build is a policy violation that can suspend the account. The sample app id above (`ca-app-pub-3940256099942544~3347511713`) and the per-format sample unit ids are published on the quick-start page.

**MUST** keep live ad unit ids out of debug variants (`BuildConfig` field per variant, or remote config), and **MUST NOT** hardcode them in `commonMain`.

---

## Layering — MUST

Ads are a platform SDK with a UI component. The shared module owns the **policy**; the platforms own the **SDK**.

```kotlin
// commonMain — what the app decides
public interface AdPolicy {
    public fun observeAdsEnabled(): Flow<Boolean>      // premium users see none; consent not granted → none
}
public interface InterstitialAds {
    public suspend fun preload(placement: AdPlacement)
    public suspend fun show(placement: AdPlacement): AdResult   // Shown | NotReady | Dismissed
}
```

```kotlin
// androidMain — InterstitialAd.load / show over an Activity
// iosMain     — GADInterstitialAd over a UIViewController (or the Swift side implements the interface)
```

- Banner and native ad **views** are platform UI. In Compose on Android, `AndroidView { AdView(...) }`; in CMP on iOS, `UIKitView` with a `GADBannerView`. See `../android/xml-views.md` and `../kmp/ios-interop.md`.
- An interstitial/rewarded needs an `Activity` / `UIViewController`; **MUST NOT** thread one through common code — the platform implementation obtains it.
- **MUST** gate every ad request on `AdPolicy` and on consent. Entitlement state is server-authoritative — `subscriptions.md`.

---

## Rules — MUST

- UMP consent before initialisation and before every request where required.
- Preload interstitials/rewarded ahead of the moment; `show` on a cold load fails `NotReady`.
- Honour the SDK's frequency and placement policies; interstitials on app open or during a task are policy violations.
- App Tracking Transparency prompt on iOS before any personalised request; SKAdNetwork identifiers in `Info.plist` per Google's list.
- Never call ad APIs from a ViewModel or a repository. They are UI/platform concerns reached through the interfaces above.

---

## Android / iOS differences

| Concern | Android | iOS |
|---|---|---|
| SDK | `play-services-ads` 25.5.0 | `Google-Mobile-Ads-SDK` (SPM / CocoaPods) |
| App id | manifest `meta-data` | `Info.plist` `GADApplicationIdentifier` — **missing = crash at init** |
| Consent | UMP SDK | UMP SDK + ATT prompt (`NSUserTrackingUsageDescription`) |
| Attribution | Play Install Referrer | SKAdNetwork `SKAdNetworkItems` in `Info.plist` |
| Ad views | `AdView` via `AndroidView` | `GADBannerView` via `UIKitView` / SwiftUI representable |
| Full-screen ads | need an `Activity` | need a `UIViewController` |
| CocoaPods vs SPM | n/a | interacts with the KMP framework-integration choice — `../kmp/ios-interop.md` |

---

## Anti-patterns — MUST NOT

| Anti-pattern | Consequence |
|---|---|
| Live ad unit ids in a debug build | account policy risk |
| Initialising on the main thread | ANR-length block |
| Ads before UMP consent in the EEA/UK/CH | policy violation; SDK refuses or serves non-personalised |
| Ad SDK calls in `commonMain` | no such SDK exists; will not compile |
| Ads shown to entitled (premium) users | the entitlement check was bypassed |
| Missing `GADApplicationIdentifier` on iOS | crash |
| `show()` without `preload()` | `NotReady` on first use |
| Calling a wrapper "official" | factually wrong |

---

## Testing recommendations

- **MUST** test `AdPolicy` and every consumer in `commonTest` with fakes: premium → no ads; no consent → no ads; otherwise ads.
- **MUST** test `InterstitialAds` consumers against a fake returning `NotReady`/`Dismissed`, since those paths are where flows stall.
- **SHOULD** run the real SDK only with test ad unit ids, on a device, in a manual pre-release check on both platforms.
- **MUST** verify the iOS `Info.plist` entries (`GADApplicationIdentifier`, `NSUserTrackingUsageDescription`, `SKAdNetworkItems`) on the release checklist — `../release/ios-release.md`.

---

## Cross-references

- Entitlement gating of ads: `subscriptions.md`
- Wrapping platform views: `../android/xml-views.md`, `../kmp/ios-interop.md`
- Permissions / ATT: `../android/permissions.md`
- Secrets and ids out of `commonMain`: `../quality/security.md`
