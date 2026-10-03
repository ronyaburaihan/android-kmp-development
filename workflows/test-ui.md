# Workflow: UI Testing

Standing contract: `README.md`. Prerequisite: `inspect-project.md`.

**Primitives:** `../process/tdd.md` — the Compose screen seam.

---

## 1. Objective

Add or run tests that verify what the user sees and does — rendering per state, interaction wiring, navigation flows, accessibility semantics — at the cheapest level that proves it: Compose tests in `commonTest` first, instrumented/simulator only for what needs a device.

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| Screen or flow under test | yes | **MUST** ask |
| Level: component render, screen interaction, end-to-end flow | no | default: component + screen; end-to-end only for critical journeys |
| Platforms | no | default: every target the UI module has |
| Design states to cover | no | default: loading, empty, error, content |
| Device availability | no | determines what is `NOT RUN` |

---

## 3. Initial project inspection

```bash
grep -rln 'runComposeUiTest\|createComposeRule\|createAndroidComposeRule\|onNodeWithTag' --include='*Test.kt' . | grep -v build/
grep -rn 'testTag(' --include='*.kt' . | grep -v build/ | wc -l
grep -rn 'ui-test\|ui-test-junit4\|ui-test-manifest' --include='*.toml' --include='*.gradle.kts' . | grep -v build/
grep -rn 'import androidx.compose.ui.test.runComposeUiTest' --include='*.kt' . | grep -v build/    # non-v2: deprecated
```

**MUST** record: harness in use (CMP v2 `runComposeUiTest` / `createComposeRule`), whether content composables are stateless (testable) or take a ViewModel (not), and how existing tests find nodes.

**MUST** confirm a green baseline.

---

## 4. Step-by-step procedure

### 4.1 Make the composable testable — or stop

A content composable **MUST** take state and lambdas. One that takes a ViewModel cannot be driven from a test without a DI graph and a store owner, and on iOS native there is none. If the screen is the latter → D1.

### 4.2 Component and screen tests in `commonTest` — the default level

```kotlin
class UserProfileScreenTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun rendersEachState() = runComposeUiTest {          // androidx.compose.ui.test.v2
        setContent { UserProfileScreen(UserUiState(), onRefresh = {}, onMessageShown = {}) }
        onNodeWithTag("empty").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun refreshInvokesCallback() = runComposeUiTest {
        var refreshed = false
        setContent { UserProfileScreen(UserUiState(profile = sample), onRefresh = { refreshed = true }, onMessageShown = {}) }
        onNodeWithTag("refresh").performClick()
        assertTrue(refreshed)
    }
}
```

**MUST:**
- Import from `androidx.compose.ui.test.v2`; the non-`v2` entry points are deprecated (CMP 1.11).
- One test per state: loading, empty, error, content — each asserts the distinguishing node.
- One test per interaction: performs the action, asserts the lambda was invoked with the right argument.
- Find nodes by `testTag`, not rendered text — localisation breaks text matching.
- No `TestRule` in `commonTest` (desktop-only).

### 4.3 Accessibility semantics — SHOULD

For the primary actions on a screen, assert the semantics assistive technology reads: `onNodeWithContentDescription`, `assertHasClickAction`, role/state descriptions. It is the same tree the tests use, so this is cheap.

### 4.4 Navigation flows — MUST for critical journeys

With Navigation 3 the back stack is data: test it as a list where possible (`../references/android/navigation.md`). For a true end-to-end flow, a Compose test that drives through screens asserting the destination's `testTag`; instrumented only if the flow needs a real Activity, permission dialog, or system UI.

### 4.5 Instrumented / simulator — only what needs it

| Needs a device | Why |
|---|---|
| Permission dialogs, system back, insets, edge-to-edge | system UI |
| `AndroidView` / `UIKitView` content | real platform views |
| Deep links from an Intent / `onOpenURL` | OS entry |
| API 36 behaviour changes | runtime behaviour |

Android: `createAndroidComposeRule<MainActivity>()` only when the Activity matters. iOS: `iosSimulatorArm64Test` for CMP; XCTest for native SwiftUI over the facade.

### 4.6 Screenshot tests — MAY, with the caveat

The first-party Compose Preview Screenshot Testing tool is alpha, its standalone-plugin path is deprecated, and it does not support non-Android KMP targets. **MAY** use for local visual review; **MUST NOT** make it a merge gate. Third-party alternatives' KMP status is `[UNVERIFIED]`. See `../references/quality/testing-strategy.md`.

### 4.7 Validate

Run section 7.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | Content composable takes a **ViewModel** | (a) refactor to route/content split (a small refactor); (b) test via instrumented with a DI graph | **MUST** ask; recommend (a). |
| D2 | Nodes have **no `testTag`s** | (a) add tags (production change, no behaviour change); (b) match by text | **MUST** choose (a); tags are the stable contract. |
| D3 | Flow needs **system UI** | — | instrumented/simulator; mark `NOT RUN` if no device. |
| D4 | Screenshot testing requested | — | **MUST** state alpha/deprecated/no-KMP status; **MUST NOT** gate on it. |
| D5 | Test needs a **real backend** | — | **MUST NOT**; drive the screen with state, or fake the repository behind the ViewModel. |
| D6 | UI test reveals a **defect** | — | report → `diagnose-and-fix-bug.md`. |
| D7 | Flaky animation timing | — | Compose synchronisation handles it; if a test needs a sleep, the composable has unsynchronised work — **MUST** find it, not wait. |

---

## 6. Implementation rules

**MUST:**

1. Test stateless content composables by passing state and lambdas.
2. Cover every design state and every interaction.
3. Address nodes by `testTag`.
4. Use the `v2` CMP API in `commonTest`.
5. Reserve instrumented tests for system-UI-dependent behaviour.
6. Match the project's harness and tag conventions.

**MUST NOT:**

7. `Thread.sleep` / arbitrary waits.
8. Match on rendered text in a localised app.
9. Boot a DI graph or a ViewModel for a component test.
10. Hit a network.
11. Gate on screenshot tooling in its current state.
12. Modify production code beyond adding `testTag`s / `contentDescription`s (D2), or a D1 split with approval.

---

## 7. Validation requirements

| # | Check | Required |
|---|---|---|
| V1 | Tests pass in `commonTest` on the host | MUST |
| V2 | Tests pass on every KMP target (`allTests`, incl. `iosSimulatorArm64Test`) | MUST if KMP |
| V3 | Every design state has a test | MUST |
| V4 | Every interaction has a test asserting its lambda | MUST |
| V5 | Mutation check: break the rendering branch, test goes red | MUST |
| V6 | No sleeps; no text matching in localised screens | MUST |
| V7 | Instrumented tests pass on an API 36 device if any were added | MUST (or `NOT RUN`) |
| V8 | Accessibility semantics asserted for primary actions | SHOULD |
| V9 | Existing tests unmodified and green | MUST |
| V10 | No production behaviour change | MUST |

---

## 8. Failure handling

| Failure | Response |
|---|---|
| Composable not testable (ViewModel parameter) | → D1. |
| `commonTest` will not compile | a JUnit rule or Android import — move to `androidDeviceTest` or remove. |
| iOS simulator test fails, JVM passes | platform rendering/semantics difference — real finding; report. |
| Test needs a sleep | → D7; find the unsynchronised work. |
| No device/simulator | mark instrumented checks `NOT RUN`; downgrade outcome. |
| Screenshot tool breaks the build | remove it from the gate; report. |

---

## 9. Completion criteria

1. Composables under test are stateless (or D1 resolved).
2. Every state and interaction covered; mutation check recorded.
3. V1–V7, V9, V10 pass.
4. Instrumented tests limited to system-dependent behaviour.
5. No screenshot gate added.
6. Defects found reported, not fixed.

---

## 10. Final report format

```markdown
## UI Tests — <screen / flow>

### Outcome
<DONE | DONE WITH CAVEATS | BLOCKED | NEEDS DECISION>

### Harness
- API: <runComposeUiTest v2 | createComposeRule | …>
- Node addressing: testTag
- Targets: <jvm, iosSimulatorArm64, android device>

### Coverage
| State / interaction | Test | Level |
|---|---|---|

### Instrumented (device) tests
| Test | Why it needs a device | Result |
|---|---|---|

### Mutation check
| Rendering broken | Test that went red |
|---|---|

### Validation performed
| # | Check | Result |
|---|---|---|

### Production changes (tags only)
| File | Change |
|---|---|

### Defects found — reported, not fixed
### Not done
### Observations
### Decisions needed
```
