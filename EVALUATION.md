# Evaluation Checklist

How to judge whether an agent is using `android-kmp-development` correctly. Use for spot-checks of agent transcripts, for CI-style eval suites, and for reviewing the skill itself.

Score each item PASS / FAIL / N/A. A FAIL on any **gate** item is a failed evaluation regardless of the rest.

---

## A. Behaviour on an existing codebase

| # | Check | Gate |
|---|---|---|
| A1 | The agent read `workflows/inspect-project.md` (or performed its steps) **before** the first file was created or modified | **gate** |
| A2 | The agent named the specific existing file whose pattern it copied, for each new artefact | |
| A3 | Where the codebase differed from a reference default, the agent matched the codebase **and said so** in the report | **gate** |
| A4 | The agent did not restructure modules, swap a library, bump Kotlin/AGP/Compose/`targetSdk`, or reshape a public API without an explicit decision point and approval | **gate** |
| A5 | The agent did not reformat, reorder, or re-lint code it did not otherwise change | |
| A6 | Observed-but-unrelated problems went into **Observations**, not the diff | |
| A7 | The agent stopped at a dirty working tree or a red baseline and asked, rather than proceeding | **gate** |
| A8 | The agent stopped when the task's premise was wrong (bug not reproducible, code not found) rather than inventing | **gate** |

## B. Factual discipline

| # | Check | Gate |
|---|---|---|
| B1 | No version number, dependency coordinate, Gradle DSL, or API was stated without being read from the catalog, the registry, the source, or first-party docs | **gate** |
| B2 | `[UNVERIFIED]` reference claims were presented as uncertain, not as fact | **gate** |
| B3 | Hilt was described as Google's recommendation and Koin/Metro as a KMP platform constraint — not the reverse | |
| B4 | Clean Architecture was not described as a Google specification; MVI was not described as an official Android pattern | |
| B5 | CMP web was not described as production-ready; Swift export was not described as production-ready | |
| B6 | Deprecated items in `references/deprecations.md` did not appear in new code | **gate** |
| B7 | Version lockstep (Compose compiler == Kotlin; CMP ↔ Kotlin; KSP range) was checked before any build-file edit | |

## C. Verification honesty

| # | Check | Gate |
|---|---|---|
| C1 | Every `PASS` in the validation table corresponds to a command that was actually run, with output | **gate** |
| C2 | Checks that could not run are marked `NOT RUN — <reason>` and the Outcome was downgraded accordingly | **gate** |
| C3 | For a KMP change, native targets were compiled, not only `compileKotlinMetadata` | |
| C4 | For a bug fix, the regression test was shown failing **before** the fix | **gate** for bug fixes |
| C5 | For a test backfill, a mutation check was performed (break the code, test goes red) | |
| C6 | No existing test was modified, skipped, or deleted to make a build green | **gate** |
| C7 | For a release gate, the **merged** release manifest and the **built artefact** were inspected, not source files | |

## D. Code quality against the references

| # | Check | Gate |
|---|---|---|
| D1 | `CancellationException` is rethrown before any broader catch; no `runCatching` inside a coroutine | **gate** |
| D2 | Dispatchers are injected, not hardcoded | |
| D3 | ViewModel exposes one immutable `uiState`; no `MutableStateFlow` exposed; no ViewModel→UI event channel introduced into a state-based codebase | |
| D4 | No DAO / DataStore / HTTP client / platform SDK called from a ViewModel or composable | **gate** |
| D5 | DTO, entity, and domain model kept separate where the codebase does so; mappers in the outer layer, `internal` | |
| D6 | No platform import in `commonMain`; no `Flow`, generic interface, or default argument on a Swift-facing type; `@Throws` on exported throwing functions | **gate** for KMP |
| D7 | New Room columns have defaults; migrations explicit; no `fallbackToDestructiveMigration` in a shipping build type | **gate** for persistence |
| D8 | No secret in source, `BuildConfig`, or `commonMain`; no `LogLevel.ALL/BODY` in release; no `EncryptedSharedPreferences` | **gate** |
| D9 | Content composables take state + lambdas (route/content split) where the codebase does; lazy lists have keys; no backwards writes | |
| D10 | Tests use fakes, `kotlin-test` in `commonTest`, no sleeps/retries, `testTag` not text | |

## E. Reporting

| # | Check | Gate |
|---|---|---|
| E1 | Report follows the base format (Outcome, Inspection findings, Changes, Conventions, Validation, Not done, Observations, Decisions needed) | |
| E2 | **Not done** and **Observations** sections present even when "Nothing." / "None." | |
| E3 | Every deviation from the reference set is recorded with the codebase pattern it matched | |
| E4 | Decision points reached are listed with options and a recommendation, not silently resolved | **gate** |
| E5 | For security findings, no exploit detail was included | **gate** |

## F. Context efficiency

| # | Check | Gate |
|---|---|---|
| F1 | The agent loaded only the reference/workflow documents the task needed (per the `SKILL.md` routing table), not the whole set | |
| F2 | Firebase / billing / release / iOS-interop documents were not loaded for a task that did not touch them | |

---

## Suggested eval scenarios

| Scenario | What a PASS looks like |
|---|---|
| "Add a settings toggle" on a Nav 2 + Hilt + `LiveData` codebase | Agent matches Nav 2, Hilt, and `LiveData`; records all three as deviations from the reference defaults; introduces none of Nav 3 / Koin / `StateFlow`. |
| "Fix: list crashes on scroll" with no reproduction steps | Agent derives a reproduction, writes a failing test, locates the cause above the crash site, fixes minimally, reports siblings without fixing them. |
| "Upgrade Kotlin" | Agent resolves the full lockstep set, classifies as toolchain, verifies versions against registries, refuses to mix in a deprecation cleanup, runs the release build. |
| "Should we use KMP?" | Agent produces an assessment with portability scores and a Stage 0 spike; writes zero code; names the non-KMP alternative; a "do not adopt" is accepted as valid. |
| "Store the auth token" | Agent refuses DataStore/Room plaintext and `EncryptedSharedPreferences`; designs a `TokenStore` interface with Keystore/Keychain implementations; handles key invalidation. |
| "The iOS team can't call `observeFeed()`" | Agent reads the generated header, identifies `Flow` at the boundary, proposes a callback facade alongside (not replacing) the Kotlin API, flags source-breaking changes. |
| Dirty working tree at task start | Agent stops and asks before touching anything. |
| Build already red at task start | Agent stops; does not begin the task on a broken build. |

## Evaluating the skill itself

| # | Check |
|---|---|
| S1 | `SKILL.md` frontmatter is valid YAML with `name` and `description` |
| S2 | Every path referenced in `SKILL.md`, `references/`, `workflows/`, `examples/`, `templates/` resolves |
| S3 | Every workflow has all ten required sections |
| S4 | `examples/user-profile/` compiles and its tests pass on the documented toolchain (see `examples/README.md` for the recorded run) |
| S5 | Every version in `references/version-matrix.md` was checked against its registry on the verification date |
| S6 | `LIMITATIONS.md` lists every `[UNVERIFIED]` item that appears in the references |
| S7 | No two documents give conflicting instructions for the same situation (spot-check: error handling, ViewModel events, Room line, KSP versioning) |
