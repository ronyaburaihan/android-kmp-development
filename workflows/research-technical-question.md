# Workflow: Research a Technical Question

Standing contract: `README.md`.

---

## 1. Objective

Answer a technical question — "does library X support Y?", "what is the current API for Z?", "is this approach officially recommended?" — with **sourced, dated** evidence, distinguishing official fact from engineering opinion, and never inventing an API.

---

## 2. Required inputs

| Input | Required | If absent |
|---|---|---|
| The question | yes | — |
| Why it matters — the decision it feeds | yes | **MUST** ask; it sets how deep to go |
| Versions in use (Kotlin, library) | no | read from the version catalog; the answer is version-specific |
| What has already been tried / read | no | **SHOULD** ask to avoid repeating it |

---

## 3. Initial project inspection

```bash
cat gradle/libs.versions.toml 2>/dev/null | grep -i '<library>'
grep -rn '<API or symbol>' --include='*.kt' . | grep -v build/ | head
```

**MUST** anchor the question to the project's versions. "Does Ktor support X" has a different answer at 2.x and 3.x.

**MUST** check whether the project already uses the thing being asked about — the codebase is often the best evidence of what works.

---

## 4. Step-by-step procedure

### 4.1 Classify the question

| Kind | Evidence that settles it |
|---|---|
| **Fact about an API** (exists? signature? deprecated?) | the library's source or generated docs for the version in use; the Gradle cache has the jar |
| **Fact about a platform rule** (store policy, OS behaviour) | the platform's official documentation; release notes |
| **Official recommendation** ("does Google recommend…") | first-party docs only; a blog post is not a recommendation |
| **Engineering judgement** ("should we…") | reasoning from facts, labelled as judgement |

### 4.2 Consult sources in this order — MUST

1. **The reference set:** `../references/` — check `../references/version-matrix.md`, `../references/deprecations.md`, and the topic document. They are dated; note the date.
2. **The library itself:** the version in the Gradle cache (`~/.gradle/caches/modules-2/files-2.1/<group>/<artifact>/<version>/`) — read the API, or the sources jar.
3. **First-party documentation** for the version in use.
4. **Release notes / changelog** for the versions between the project's and the latest.
5. **Reputable implementations** (official samples, well-known OSS) — for *how*, never for *whether it is recommended*.
6. Secondary sources (blogs, Q&A) — only for leads, and **MUST** be verified against 1–4 before being repeated.

**MUST NOT** answer from memory for anything version-sensitive. **MUST NOT** state an API exists without seeing it in source or docs.

### 4.3 Verify the claim

For an API: find it in the jar or docs, note the exact signature and the version. For a behaviour: find the sentence in the official doc. For a recommendation: find the first-party page and quote it.

If it cannot be verified: say so. **`[UNVERIFIED]` is an acceptable answer; a confident guess is not.**

### 4.4 Record the answer with provenance

Every factual claim gets: the source URL or file path, the version it applies to, the date checked, and a provenance tag (`[OFFICIAL]`, `[DEFAULT]`, `[UNVERIFIED]`) per `../references/README.md`.

### 4.5 Fold it back — SHOULD

If the answer corrects or extends the reference set, **SHOULD** propose the edit (file and line) rather than leaving the knowledge in a chat message.

---

## 5. Decision points

| # | Trigger | Options | Action |
|---|---|---|---|
| D1 | Sources **conflict** | (a) prefer the more recent first-party source; (b) report both | **MUST** report the conflict with both sources; recommend (a). |
| D2 | The answer depends on a **version the project does not have** | (a) upgrade (→ `upgrade-dependencies.md`); (b) use the older approach | **MUST** report both paths with the version gap stated. |
| D3 | The reference set is **wrong or stale** on this point | — | **MUST** report the correction explicitly and propose the edit. |
| D4 | The question is really a request to **make a decision** | — | **MUST** present options with trade-offs; **MUST NOT** choose unless asked. |
| D5 | Only secondary sources exist | — | **MUST** label the answer `[UNVERIFIED]` and say what would verify it. |
| D6 | The question requires running code to answer (does this compile? does this behave so?) | (a) spike in the scratch directory; (b) answer theoretically, labelled | **SHOULD** choose (a). An executed answer beats a reasoned one. |

---

## 6. Implementation rules

**MUST:**

1. Anchor to the project's versions.
2. Prefer source and first-party docs over everything else.
3. Tag every claim with provenance and date.
4. Say "unverified" when it is.
5. Quote recommendations; do not paraphrase them into something stronger.

**MUST NOT:**

6. Invent an API, a parameter, a Gradle DSL, or a version.
7. Present a blog post as an official recommendation.
8. Modify project files during research (a spike goes in the scratch directory).
9. Answer a version-sensitive question without stating the version.

---

## 7. Validation requirements

| # | Check | Required |
|---|---|---|
| V1 | Every claim has a source and a date | MUST |
| V2 | Every claim has a provenance tag | MUST |
| V3 | Version anchored | MUST |
| V4 | APIs cited were seen in source or docs, not recalled | MUST |
| V5 | Conflicts reported, not resolved silently | MUST |
| V6 | Zero project files changed | MUST |
| V7 | Spike (if any) actually executed, output recorded | MUST if D6(a) |

---

## 8. Failure handling

| Failure | Response |
|---|---|
| Documentation unreachable | **MUST** fall back to the library source in the Gradle cache; if neither, `[UNVERIFIED]`. |
| Documentation stale vs the code | **MUST** trust the code for *what exists*, the docs for *what is recommended*; report the discrepancy. |
| Answer requires a paid/private resource | **MUST** say so; do not guess. |
| Question has no good answer at the project's version | → D2. |
| Spike fails to build | **MUST** report the exact error; it is itself evidence. |

---

## 9. Completion criteria

1. Question classified and version-anchored.
2. Answer given with sources, dates, and provenance tags.
3. Unverifiable parts labelled.
4. Conflicts and version gaps reported.
5. Reference-set corrections proposed if found.
6. Zero project files changed.

---

## 10. Final report format

```markdown
## Research — <question>

### Answer
<one paragraph; the decision-relevant conclusion first>

### Evidence
| Claim | Source | Version | Checked | Provenance |
|---|---|---|---|---|

### Version anchoring
- Project: <lib> <version>
- Latest: <version> — <what changes if upgraded>

### Unverified
<claims that could not be confirmed, and what would confirm them>

### Conflicts
<"None." or both sides with sources>

### Proposed reference-set edits
<file:line → change, or "None.">

### Decisions needed
```
