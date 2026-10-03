# Primitive: Vertical Slice

Adapted from Matt Pocock's `to-tickets` skill — see `README.md` § Attribution.

## Purpose

Cut a plan into **tracer bullets**: each a narrow but *complete* path through every layer, demoable
on its own, and sized to fit one fresh context window.

The failure this prevents: a week of correct-looking work with nothing demoable, where the layers
only meet at the end — which is when the integration defects all arrive at once.

---

## When to invoke

- `../workflows/plan-feature.md` § 4.6 — sequencing the work.
- `../workflows/migrate-module-to-kmp.md` — staging a migration behind approval gates.
- Any task too large for one session, or one the user wants to review in pieces.

---

## The rules

1. Each slice cuts a **narrow but complete** path through every layer it touches — model, data,
   domain, state, UI, tests. Vertical, **not** a horizontal slice of one layer.
2. A finished slice is **demoable or verifiable on its own**: something runs and shows a result.
3. Each slice fits in **one fresh context window**. If it does not, split it.
4. Each slice declares its **blocking edges** — which slices must finish first. A slice with no
   blockers starts immediately.

---

## Android/KMP specifics

### The structure convention makes horizontal slicing the default — resist it

In the house package-by-layer structure (`core` → `domain` → `data` → `presentation`, features as
packages — `../templates/structure/PROJECT_STRUCTURE.md`), the *directories are the layers*. The path
of least resistance is therefore "add all the DTOs", then "add all the DAOs", then "add all the
ViewModels". Every one of those is a horizontal slice, and none is demoable.

The slice rule exists to counteract the folder layout. A correct slice for one feature touches
**several directories at once**:

```
Slice 1 — "Profile shows a cached display name"
  domain/model/User.kt                      the one field this slice needs
  domain/repository/UserRepository.kt        one method
  data/local/UserDao.kt + UserEntity.kt      one table, one query
  data/repository/UserRepositoryImpl.kt      cache read only — no network yet
  presentation/profile/ProfileUiState.kt     Loading | Content(name) | Error
  presentation/profile/ProfileViewModel.kt
  presentation/profile/ProfileScreen.kt
  di/                                        the bindings this slice needs
  commonTest/                                mapper, repository, ViewModel  (tdd.md)
Demoable: open Profile, see a name from the database.
Blocked by: nothing.
```

```
Slice 2 — "Profile refreshes from the network"
  data/remote/UserApi.kt, UserDto.kt, mapper
  UserRepositoryImpl                         network → cache → emit
  ProfileUiState                             + refreshing, + stale indicator
Demoable: pull to refresh, value changes.
Blocked by: Slice 1.
```

Note what slice 1 deliberately omits: the network, most fields, the error taxonomy, iOS. Each is a
later slice. Nothing is stubbed with `TODO()` on a delivered path — the slice is *narrow*, not
*unfinished*.

### Slice boundaries that work in this stack

| Good first slice | Why |
|---|---|
| One state of the screen, end to end | Proves the whole wiring — DI, navigation, state collection |
| One field, all layers | Proves the mapper chain and the repository boundary |
| Cache-only before network | Removes HTTP from the first integration |
| One platform before both | `commonMain` + Android UI first; iOS UI as its own slice |

| Bad slice | Why |
|---|---|
| "Add the data layer" | Horizontal. Demoable: nothing |
| "Add all DTOs and entities" | Horizontal, and locks a model shape before any consumer exists |
| "Wire up DI" | Not a slice; part of whichever slice needs the binding |
| "Write the tests" | Tests belong inside the slice (`tdd.md`), never after |
| A slice that adds a KMP target | Not a slice — an approval gate. `../workflows/README.md` § 3 |

### Wide refactors: expand, then contract

A change touching many call sites is not one slice. Sequence it:

1. **Expand** — add the new shape alongside the old. Both compile. Nothing is broken.
2. **Migrate** — move call sites in independently green batches, one commit each
   (`../workflows/refactor.md`).
3. **Contract** — delete the old shape once nothing references it.

Each stage is its own slice with its own green build. For a Swift-facing API the contract stage is an
approval gate: removing a symbol breaks the iOS build.

---

## Ticket format

Local, when there is no tracker — `.scratch/<feature-slug>/issues/<NN>-<slug>.md`:

```markdown
# 02 — Profile refreshes from the network

## What to build
As a signed-in user, pulling to refresh on Profile fetches the latest profile and updates
the displayed name, or keeps the cached value and shows a stale indicator on failure.

## Blocked by
- 01-profile-shows-cached-name

## Acceptance criteria
- [ ] Pull to refresh triggers one network call
- [ ] Success updates both the database and the displayed value
- [ ] Failure keeps the cached value and surfaces a non-blocking indicator
- [ ] 401 signs the user out  (`../references/integrations/auth-and-tokens.md`)
- [ ] Tests at the repository and ViewModel seams  (`tdd.md`)

## Status
Not started
```

On a real tracker, use its native blocking links instead of the `Blocked by` text, and keep the same
three sections. **MUST** match the project's existing issue conventions over this template.

---

## Anti-patterns

| Anti-pattern | Why it fails |
|---|---|
| Layer-shaped tickets | Nothing demoable until the last one lands; all integration risk deferred |
| A slice that needs two context windows | Finishes in a compacted context, with the plan half-forgotten |
| `TODO()` to make a slice "complete" | Ships a stub on a delivered path |
| Tests as a separate final ticket | Tests stop being a design tool and become a chore that gets dropped |
| Implicit ordering | The agent picks an arbitrary slice and blocks |
| Slicing before the seams are agreed | Slice boundaries and test seams are the same decision |

---

## It's working if

- Every slice names something a human could be shown when it is done.
- Every slice touches more than one layer directory.
- Every slice declares its blockers, and at least one has none.
- No slice carries a `TODO()` on a delivered path.
- Each slice's tests are inside it.
- A wide refactor is sequenced expand → migrate → contract, each stage independently green.

---

## Where it fits

```
grilling → clarify-requirements → plan-feature → vertical-slice
   → per slice: add-feature ⇄ tdd → two-axis-review → commit
```

Plan structure: `../templates/implementation-plan.md`. Spec: `../templates/feature-spec.md`.
