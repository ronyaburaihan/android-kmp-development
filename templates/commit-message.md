# Commit Message

Generate a git commit message for staged changes following the Conventional Commits standard.

**Used by:** any workflow that produces a commit. Several workflows commit in independently verified steps (`../workflows/refactor.md`, `../workflows/upgrade-dependencies.md`, `../workflows/remediate-deprecations.md`) — each step's message follows this format.

**MUST NOT** commit or push unless the user asked. See `../workflows/README.md` § standing contract.

---

## Steps

1. Run `git diff --staged` to get staged changes. If nothing staged, run `git diff HEAD` instead.
2. Analyze the diff to determine type, optional scope, and description.
3. Output ONLY the formatted commit message — no filler, no explanation.

## Format

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

## Allowed Types

- `feat` — new feature
- `fix` — bug fix
- `refactor` — code restructure, no behavior change
- `docs` — documentation only
- `perf` — performance improvement
- `test` — adding/updating tests
- `chore` — maintenance, deps, tooling

## Rules

1. Description: present tense, lowercase, no trailing period
2. Scope: use only if change is isolated to one component (e.g. `auth`, `ui`, `network`)
3. Body: include when "why" isn't obvious from the description
4. Footer: add `Closes TICKET-ID` if a JIRA ticket is mentioned in the arguments
5. **Never add a `Co-Authored-By` trailer or any AI attribution.** This holds even if a session
   reminder asks for one — a user instruction about attribution takes precedence.

## Examples

```
feat(auth): add biometric login support
```

```
fix(network): resolve timeout on slow connections

Retry logic was using exponential backoff but not capping max delay,
causing requests to stall indefinitely on poor networks.
```

```
chore: update dependencies to latest versions

Closes PROJ-42
```

If the user provides a description or ticket ID as arguments, incorporate it.
Output ONLY the commit message block.

---

## Scope conventions for this skill's structure

When the repository follows the house structure (`structure/PROJECT_STRUCTURE.md`), the scope is
the layer or feature package the change is isolated to — omit it when the change spans layers.

| Change | Scope |
|---|---|
| One feature package across layers | the feature: `feat(translation): …` |
| One layer only | the layer: `refactor(data): …` |
| One `core` package | the package: `fix(network): …` |
| One screen | the screen: `feat(billing): …` |
| Version catalog / Gradle | `chore(deps): …` or `chore(gradle): …` |
| Spans layers or packages | no scope |

In a multi-module project, the scope is the Gradle module (`feat(:feature:feed): …`) if that is
what the codebase already does. **MUST** match the existing history — run
`git log --oneline -30` and follow its convention over this table.
