# Templates

Fill-in skeletons. Each is the shape the matching workflow expects; none contains guidance — that lives in `../references/` and `../workflows/`.

| Template | Used by | Fill in when |
|---|---|---|
| `feature-spec.md` | `workflows/clarify-requirements.md` | turning a request into acceptance criteria |
| `implementation-plan.md` | `workflows/plan-feature.md` | planning a non-trivial change |
| `task-report.md` | every workflow (base report) | finishing any task |
| `bug-report.md` | `workflows/diagnose-and-fix-bug.md` | recording a defect to reproduce or hand off |
| `adr.md` | any decision point marked structural | recording a decision and its alternatives |
| `pr-description.md` | `workflows/review-code.md` | opening a PR |
| `release-checklist.md` | `workflows/prepare-release.md` | gating a release |
| `libs.versions.toml` | `workflows/upgrade-dependencies.md`, new modules | starting a version catalog; shows the lockstep comments |
| `kmp-module.build.gradle.kts` | new shared module | declaring targets and source sets |
| `platform-module.kt` | `references/libraries/koin-di.md` | the `expect fun platformModule()` pattern |
| `ui-state-and-viewmodel.kt` | `references/architecture/mvvm-udf.md` | a new screen's state + ViewModel |
| `structure/PROJECT_STRUCTURE.md` + `structure/scaffold.sh` | `SKILL.md` § Project structure convention | the house package-by-layer / MVI layout: placement rules, naming, the `Effect` trade-off; the script creates the tree, MVI base types, and screen sets |
| `ci-github-actions.yml` | `references/quality/ci-pipeline.md` | setting up CI; valid YAML, not executed on a hosted runner |

Rules for using a template: copy, fill every `<placeholder>`, delete sections that do not apply **and say so** in the report, never leave a placeholder in a delivered file. Versions in `libs.versions.toml` are examples verified on 2026-10-03 — re-verify against `../references/version-matrix.md`.
