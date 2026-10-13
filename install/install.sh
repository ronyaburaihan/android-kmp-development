#!/usr/bin/env bash
# Install the android-kmp-development skill for a coding agent.
#
#   ./install.sh <agent> [--scope user|project] [--project-dir DIR]
#
# Agents:
#   claude     Claude Code — native skill (progressive disclosure, best fidelity)
#   agents     AGENTS.md — the open standard. Covers Codex, OpenCode, Cursor, Aider, goose,
#              Zed, Warp, VS Code, Copilot, Gemini CLI, Jules, Amp, Junie, Devin, Factory,
#              RooCode, Kilo Code, Augment Code, Windsurf, Ona, Phoenix
#   codex      OpenAI Codex CLI   (~/.codex/AGENTS.md | ./AGENTS.md)
#   opencode   OpenCode           (~/.config/opencode/AGENTS.md | ./AGENTS.md)
#   cursor     Cursor             (.cursor/rules/*.mdc — more control than AGENTS.md)
#   kiro       AWS Kiro           (~/.kiro/steering | .kiro/steering)
#   trae       Trae               (~/.trae/user_rules | .trae/rules)
#   windsurf   Windsurf           (.windsurf/rules)
#   antigravity  Google Antigravity (~/.gemini/config/rules | .agents/rules)
#   copilot    GitHub Copilot     (.github/copilot-instructions.md)
#   all        AGENTS.md + every project-scope adapter above
#
# The skill directory is copied once. Every adapter is rendered from the single bootstrap,
# install/AGENTS.md, plus the frontmatter its agent needs — there are no per-agent copies to
# drift. Project scope writes a path relative to the project root, so the rendered files can
# be committed and work for every clone.
set -euo pipefail

AGENT="${1:-}"; shift || true
SCOPE="project"; PROJECT_DIR="$PWD"
while [ $# -gt 0 ]; do case "$1" in
  --scope) SCOPE="$2"; shift 2;;
  --project-dir) PROJECT_DIR="$2"; shift 2;;
  *) echo "unknown option: $1" >&2; exit 2;; esac; done
[ -n "$AGENT" ] || { sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKILL_SRC="$(dirname "$HERE")"
SKILL_NAME="android-kmp-development"
TEMPLATE="$HERE/AGENTS.md"
BEGIN="<!-- $SKILL_NAME:begin -->"
END="<!-- $SKILL_NAME:end -->"

# SKILL_DEST is where the skill is copied; SKILL_REF is the path written into adapters.
if [ "$SCOPE" = user ]; then
  SKILL_DEST="$HOME/.$SKILL_NAME"
  SKILL_REF="$SKILL_DEST"
else
  SKILL_DEST="$PROJECT_DIR/.agent-skills/$SKILL_NAME"
  SKILL_REF=".agent-skills/$SKILL_NAME"
fi

# copy_skill <destination>  — the skill without its git history, research record or build output
copy_skill() {
  local dest="$1"
  if [ "$(cd "$SKILL_SRC" && pwd)" = "$(cd "$dest" 2>/dev/null && pwd || echo _)" ]; then
    echo "  skill already at $dest"; return
  fi
  mkdir -p "$(dirname "$dest")"
  rm -rf "$dest"
  cp -R "$SKILL_SRC" "$dest"
  rm -rf "$dest/.git" "$dest/.agents" "$dest/skills-lock.json" "$dest/research"
  find "$dest" -type d \( -name build -o -name .gradle -o -name .kotlin \) -prune -exec rm -rf {} +
  echo "  skill  -> $dest"
}

place_skill() { copy_skill "$SKILL_DEST"; }

body() { sed "s|SKILL_PATH|$SKILL_REF|g" "$TEMPLATE"; }

# frontmatter <agent>  — the only thing that differs between adapters
frontmatter() {
  local short="Android and Kotlin Multiplatform engineering rules"
  local long="$short; inspect before implementing, match the codebase, never invent versions or APIs"
  case "$1" in
    cursor) cat <<F
---
description: $long
globs: ["**/*.kt","**/*.kts","**/*.java","**/*.swift","**/*.toml","**/*.pro","**/AndroidManifest.xml","**/Info.plist"]
alwaysApply: true
---
F
      ;;
    kiro) printf -- '---\ninclusion: always\n---\n' ;;
    trae) cat <<F
---
description: $short
globs: ["**/*.kt","**/*.kts","**/*.swift","**/*.toml"]
alwaysApply: true
---
F
      ;;
    windsurf) printf -- '---\ntrigger: always_on\ndescription: %s\n---\n' "$short" ;;
    antigravity) printf -- '---\ntrigger: always_on\ndescription: %s\n---\n' "$long" ;;
  esac
}

# render_owned <agent> <file>  — a rule file this skill owns outright; rewritten on every run
render_owned() {
  local dest="$2"
  mkdir -p "$(dirname "$dest")"
  { frontmatter "$1"; body; } > "$dest"
  echo "  adapter -> $dest"
}

# render_shared <file>  — a file the user may also write in (AGENTS.md, copilot-instructions.md).
# Only the text between the markers is ever replaced; everything else is left as it was.
render_shared() {
  local dest="$1" tmp
  mkdir -p "$(dirname "$dest")"
  if [ ! -e "$dest" ]; then
    { echo "$BEGIN"; body; echo "$END"; } > "$dest"
  elif grep -qF "$BEGIN" "$dest" && grep -qF "$END" "$dest"; then
    tmp="$(mktemp)"
    {
      awk -v b="$BEGIN" '$0 == b { exit } { print }' "$dest"
      echo "$BEGIN"; body; echo "$END"
      awk -v e="$END" 'seen { print } $0 == e { seen = 1 }' "$dest"
    } > "$tmp"
    cat "$tmp" > "$dest"; rm -f "$tmp"
  else
    local bak="$dest.bak.$(date +%s)"; cp "$dest" "$bak"
    echo "  NOTE: existing $dest backed up to $bak; bootstrap appended"
    if grep -q "$SKILL_NAME" "$dest"; then
      echo "  NOTE: $dest already mentions $SKILL_NAME outside the markers (an install from an"
      echo "        older version?). Remove the old block by hand; this run did not touch it."
    fi
    { echo; echo "$BEGIN"; body; echo "$END"; } >> "$dest"
  fi
  echo "  adapter -> $dest"
}

echo "Installing $SKILL_NAME for: $AGENT  (scope: $SCOPE)"

case "$AGENT" in
  claude)
    # Native skill system — no adapter needed; the host reads SKILL.md's frontmatter.
    copy_skill "$([ "$SCOPE" = user ] && echo "$HOME/.claude/skills" || echo "$PROJECT_DIR/.claude/skills")/$SKILL_NAME"
    echo "  Restart the session, then /skills lists it."
    ;;
  agents)
    place_skill; render_shared "$PROJECT_DIR/AGENTS.md" ;;
  codex)
    place_skill
    if [ "$SCOPE" = user ]; then render_shared "${CODEX_HOME:-$HOME/.codex}/AGENTS.md"
    else render_shared "$PROJECT_DIR/AGENTS.md"; fi ;;
  opencode)
    place_skill
    if [ "$SCOPE" = user ]; then render_shared "$HOME/.config/opencode/AGENTS.md"
    else render_shared "$PROJECT_DIR/AGENTS.md"; fi ;;
  cursor)
    place_skill; render_owned cursor "$PROJECT_DIR/.cursor/rules/$SKILL_NAME.mdc" ;;
  kiro)
    place_skill
    if [ "$SCOPE" = user ]; then render_owned kiro "$HOME/.kiro/steering/$SKILL_NAME.md"
    else render_owned kiro "$PROJECT_DIR/.kiro/steering/$SKILL_NAME.md"; fi ;;
  trae)
    place_skill
    if [ "$SCOPE" = user ]; then render_owned trae "$HOME/.trae/user_rules/$SKILL_NAME.md"
    else render_owned trae "$PROJECT_DIR/.trae/rules/$SKILL_NAME.md"; fi ;;
  windsurf)
    place_skill; render_owned windsurf "$PROJECT_DIR/.windsurf/rules/$SKILL_NAME.md" ;;
  antigravity)
    # Antigravity also reads AGENTS.md natively; .agents/rules gives a declared trigger.
    place_skill
    if [ "$SCOPE" = user ]; then render_owned antigravity "$HOME/.gemini/config/rules/$SKILL_NAME.md"
    else render_owned antigravity "$PROJECT_DIR/.agents/rules/$SKILL_NAME.md"; fi ;;
  copilot)
    place_skill; render_shared "$PROJECT_DIR/.github/copilot-instructions.md" ;;
  all)
    place_skill
    render_shared            "$PROJECT_DIR/AGENTS.md"
    render_owned cursor      "$PROJECT_DIR/.cursor/rules/$SKILL_NAME.mdc"
    render_owned kiro        "$PROJECT_DIR/.kiro/steering/$SKILL_NAME.md"
    render_owned trae        "$PROJECT_DIR/.trae/rules/$SKILL_NAME.md"
    render_owned windsurf    "$PROJECT_DIR/.windsurf/rules/$SKILL_NAME.md"
    render_owned antigravity "$PROJECT_DIR/.agents/rules/$SKILL_NAME.md"
    render_shared            "$PROJECT_DIR/.github/copilot-instructions.md"
    ;;
  *) echo "unknown agent: $AGENT" >&2; exit 2;;
esac

echo "Done. Restart your agent session — instruction files are read at session start."
