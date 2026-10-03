# Installing for any coding agent

The skill is 35 references, 24 workflows, 8 process primitives and a compiled example — about 24,000 lines. Most agents
load their instruction file **fully into context** every session, and OpenAI Codex caps combined
instructions at **32 KiB**. Pasting the skill into an `AGENTS.md` is therefore not an option.

Instead each agent gets a **~8 KB bootstrap**: the non-negotiable rules, the rule-level legend, the
stop conditions, and the **routing table** naming the file to open for each task. The agent reads
the rest on demand. That is the same progressive disclosure Claude Code's skill system gives
natively, reproduced for agents that have no skill system.

```
install/
  AGENTS.md          the portable bootstrap — the open AGENTS.md standard
  cursor.mdc         Cursor project rule (.mdc frontmatter: globs, alwaysApply)
  kiro-steering.md   Kiro steering file (inclusion: always)
  trae-rule.md       Trae project rule
  windsurf-rule.md   Windsurf rule (trigger: always_on)
  antigravity-rule.md  Google Antigravity rule (trigger: always_on)
  install.sh         places the skill and renders the right adapter
```

Every adapter is generated from `AGENTS.md`, so there is one source of truth. The literal
`SKILL_PATH` token is substituted with the installed location at install time.

## One command

```bash
# from a clone of this repository
./install/install.sh <agent> [--scope user|project] [--project-dir DIR]
```

| `<agent>` | Mechanism | Project scope | User scope |
|---|---|---|---|
| `claude` | **native skill** — best fidelity | `.claude/skills/…` | `~/.claude/skills/…` |
| `agents` | `AGENTS.md` open standard | `./AGENTS.md` | — |
| `codex` | `AGENTS.md` | `./AGENTS.md` | `~/.codex/AGENTS.md` |
| `opencode` | `AGENTS.md` | `./AGENTS.md` | `~/.config/opencode/AGENTS.md` |
| `cursor` | `.mdc` project rule | `.cursor/rules/*.mdc` | — |
| `kiro` | steering file | `.kiro/steering/*.md` | `~/.kiro/steering/*.md` |
| `trae` | project rule | `.trae/rules/*.md` | `~/.trae/user_rules/*.md` |
| `windsurf` | rule | `.windsurf/rules/*.md` | — |
| `antigravity` | rule with a declared trigger | `.agents/rules/*.md` | `~/.gemini/config/rules/*.md` |
| `copilot` | instructions | `.github/copilot-instructions.md` | — |
| `all` | `AGENTS.md` + every project-scope adapter | all of the above | — |

Default scope is `project`. Examples:

```bash
./install/install.sh claude --scope user        # Claude Code, every project
./install/install.sh codex  --scope user        # Codex CLI, every project
./install/install.sh agents                     # AGENTS.md in the current repo
./install/install.sh all                        # every adapter, current repo
```

**Restart the agent session afterwards** — instruction files are read at session start.

## `AGENTS.md` covers most agents already

`AGENTS.md` is an open format read natively by **24 products** listed at
[agents.md](https://agents.md/), including:

OpenAI **Codex** · **OpenCode** · **Cursor** · **Aider** · **goose** · **Zed** · **Warp** ·
**VS Code** · **GitHub Copilot** (coding agent) · **Gemini CLI** · **Jules** · **Amp** ·
**Junie** (JetBrains) · **Devin** · **Windsurf** · **Factory** · **RooCode** · **Kilo Code** ·
**Augment Code** · **Ona** · **Phoenix** · **Semgrep** · UiPath Autopilot

Not on that list but reading `AGENTS.md` natively all the same: **Google Antigravity** (IDE 1.20.5+,
which also honours `GEMINI.md`).

So for most of the list, `./install/install.sh agents` is the whole job. The dedicated adapters
exist where the agent's own format offers something `AGENTS.md` cannot:

| Agent | Why a dedicated adapter |
|---|---|
| **Claude Code** | a real skill system — the host loads `SKILL.md` and its files on demand, with no always-on context cost at all |
| **Cursor** | `.mdc` frontmatter adds `globs` and `alwaysApply`; `AGENTS.md` always applies with no scoping. Note Cursor Agent mode reads `AGENTS.md` and `.cursor/rules/*.mdc` but **not** legacy `.cursorrules` |
| **Kiro** | steering `inclusion:` modes (`always`, `fileMatch`, `manual`, `auto`) |
| **Trae** | `.trae/rules/` with Cursor-style frontmatter; global rules live at `~/.trae/user_rules` |
| **Windsurf** | `trigger: always_on` frontmatter |
| **Google Antigravity** | reads `AGENTS.md` natively, but `.agents/rules/*.md` requires a declared `trigger:` and adds a user scope under `~/.gemini/config/rules/`. Antigravity also honours `GEMINI.md` and legacy `.agent/rules/` |

## Manual install

If you would rather not run the script: copy this repository anywhere, then copy the adapter your
agent reads and replace the `SKILL_PATH` token with the path to the copy.

```bash
git clone https://github.com/ronyaburaihan/android-kmp-development-skill.git ~/.android-kmp-development
sed "s|SKILL_PATH|$HOME/.android-kmp-development|g" \
  ~/.android-kmp-development/install/AGENTS.md > ./AGENTS.md
```

## Existing instruction files are not clobbered

If the target file exists and does not already mention this skill, the installer backs it up to
`<file>.bak.<timestamp>` and **appends** the bootstrap after a `---` separator. Your own rules stay
first, which matters: in most agents later content wins on conflict, and the skill's rules are
meant to be the more specific layer.

## Verifying an install

```bash
grep -c 'android-kmp-development' ./AGENTS.md        # adapter present
grep -o '/[^ `]*android-kmp-development' ./AGENTS.md | head -1   # where it points
ls "$(grep -o '/[^ `]*android-kmp-development' ./AGENTS.md | head -1)/SKILL.md"
```

Then ask the agent something concrete — *"what is the Kotlin ↔ Compose compiler version rule?"* It
should read `references/version-matrix.md` rather than answer from memory.

## Caveat

The bootstrap and the adapters are **generated and path-verified** — the installer was run into a
throwaway project and all 57 routing targets resolved from the installed copy. They have **not**
been behaviourally tested inside Codex, Cursor, Kiro, Trae, Windsurf, Antigravity, OpenCode or
Copilot. Whether
each agent honours an always-on rule file of this shape, and actually follows the routing table
rather than guessing, is unverified. See `../LIMITATIONS.md`.

**Verified 2026-10-03** against [agents.md](https://agents.md/),
[Codex docs](https://developers.openai.com/codex/guides/agents-md),
[Cursor docs](https://cursor.com/docs/rules), [Kiro docs](https://kiro.dev/docs/steering/),
[Trae docs](https://docs.trae.ai/ide/rules), [OpenCode docs](https://opencode.ai/docs/rules/).
Agent conventions change quickly; re-check before relying on a path.
