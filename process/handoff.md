# Primitive: Handoff

Adapted from Matt Pocock's `handoff` skill — see `README.md` § Attribution.

## Purpose

Compact a session into a document the **next** agent can resume from without re-deriving the state of
the work — or the state of the toolchain.

---

## When to invoke

- Context is nearly exhausted mid-task.
- A long task will continue in a later session.
- Work moves to another agent, or another machine.

**User-invoked only.** **MUST NOT** write a handoff unprompted; it is not a substitute for the task
report in `../workflows/README.md`.

---

## Where it goes — MUST

The operating system's temporary directory, **not the repository**. A handoff is session scratch; it
is not a project artefact, it is not reviewed, and it **MUST NOT** appear in a commit.

```
$TMPDIR/handoff-<slug>-<date>.md
```

---

## What goes in

| Section | Content |
|---|---|
| **Goal** | What the user actually asked for, in their framing. One paragraph. |
| **State** | What is done, what is half-done, what is untouched. |
| **Toolchain** | `JAVA_HOME`, Gradle version, Xcode version, SDK levels — whatever was needed to build. The next agent should not rediscover this. |
| **Last green command** | The exact command, and which targets it covered. `NOT RUN` where that is the truth. |
| **Working tree** | Which files are modified, which are new, whether anything is staged, current branch. |
| **Decisions made** | Choices settled in conversation that are not written anywhere else — and who settled them. |
| **Approval gates granted** | Which standing-contract rule-3 actions the user explicitly approved. **MUST** be explicit: an approval is not inheritable by assumption. |
| **Open decision points** | What the next agent must ask before proceeding. |
| **Suggested next step** | Which workflow or primitive to invoke, by path. |

### Pointers, not copies — MUST

Reference existing artefacts **by path or URL**. A spec, plan, ADR, issue, commit or diff already
written down **MUST NOT** be restated in the handoff. Duplicating it creates a second source of truth
that will drift.

### Exclude — MUST NOT

- Content already captured in a spec, plan, ADR, ticket or commit message.
- Secrets of any kind: keystore paths and passwords, store credentials, API keys, tokens, PII from
  test data.
- A narrative of the session. The next agent needs the state, not the history.

---

## Android/KMP specifics

The toolchain and build state are the expensive things to rediscover, and the most common cause of a
resumed session reporting a false failure:

- The **JDK** that works for this AGP version, by path. A wrong JDK produces errors that do not name
  the JDK.
- The **Gradle** version, and whether the wrapper exists.
- Which **targets** last compiled, and which last ran tests — `jvmTest` green does not mean
  `iosSimulatorArm64Test` green.
- Whether the failure in progress is **release-only** (`bundleRelease`) or target-specific.
- Whether a **long-running** check was mid-flight: `connectedAndroidTest`, Macrobenchmark, a
  simulator boot, a Gradle sync.

```markdown
## Toolchain
JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home   (JDK 25 — AGP 9 needs >17)
Gradle 9.8.0 (no wrapper committed)   ·   Xcode 27   ·   compileSdk 36

## Last green command
./gradlew :shared:jvmTest   — PASS, 46 tests
./gradlew :shared:allTests  — NOT RUN (iOS not yet verified this session)
```

---

## Anti-patterns

| Anti-pattern | Why it fails |
|---|---|
| Written into the repo | Pollutes the diff; gets reviewed or committed by accident |
| Restating the spec | Second source of truth; drifts from the real one |
| A session transcript | The next agent spends context reading history instead of working |
| Omitting the toolchain | The next session rediscovers the JDK/Gradle combination, or reports a false failure |
| Claiming a build state that was not run | The next agent builds on a false premise |
| Carrying an approval gate implicitly | An unapproved migration happens in the next session |
| Including a keystore path or credential | A secret leaves the machine it belonged to |

---

## It's working if

- The next agent can run one command to get a green build, and that command is in the document.
- Every artefact is referenced by path; nothing is restated.
- Approval gates are listed explicitly, or stated as none.
- Build and test claims distinguish PASS from `NOT RUN`.
- The file is in `$TMPDIR`, and `git status` is unchanged by writing it.
- No secret appears anywhere in it.

---

## Where it fits

Orthogonal to the workflows — invoke from inside any of them. The resuming session **MUST** still run
`../workflows/inspect-project.md` before writing files; a handoff records state, it does not replace
inspection.
