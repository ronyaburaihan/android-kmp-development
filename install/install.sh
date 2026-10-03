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
# The skill directory is cloned/copied once; each adapter is a ~7 KB pointer file that carries
# the always-on rules and the routing table, so the agent reads the 24k-line skill on demand.
set -euo pipefail

AGENT="${1:-}"; shift || true
SCOPE="project"; PROJECT_DIR="$PWD"
while [ $# -gt 0 ]; do case "$1" in
  --scope) SCOPE="$2"; shift 2;;
  --project-dir) PROJECT_DIR="$2"; shift 2;;
  *) echo "unknown option: $1" >&2; exit 2;; esac; done
[ -n "$AGENT" ] || { sed -n '2,31p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKILL_SRC="$(dirname "$HERE")"
SKILL_NAME="android-kmp-development"

# Where the skill itself lives, and the path the adapters point at.
if [ "$SCOPE" = user ]; then
  SKILL_DEST="$HOME/.$SKILL_NAME"
else
  SKILL_DEST="$PROJECT_DIR/.agent-skills/$SKILL_NAME"
fi

place_skill() {
  if [ "$(cd "$SKILL_SRC" && pwd)" = "$(cd "$SKILL_DEST" 2>/dev/null && pwd || echo _)" ]; then
    echo "  skill already at $SKILL_DEST"; return
  fi
  mkdir -p "$(dirname "$SKILL_DEST")"
  rm -rf "$SKILL_DEST"
  cp -R "$SKILL_SRC" "$SKILL_DEST"
  rm -rf "$SKILL_DEST/.git"
  echo "  skill  -> $SKILL_DEST"
}

# render <template> <destination-file>  — substitutes SKILL_PATH, never clobbers silently
render() {
  local tpl="$HERE/$1" dest="$2"
  mkdir -p "$(dirname "$dest")"
  if [ -e "$dest" ] && ! grep -q "$SKILL_NAME" "$dest" 2>/dev/null; then
    local bak="$dest.bak.$(date +%s)"; cp "$dest" "$bak"
    echo "  NOTE: existing $dest backed up to $bak"
    { cat "$bak"; echo; echo "---"; echo; sed "s|SKILL_PATH|$SKILL_DEST|g" "$tpl"; } > "$dest"
  else
    sed "s|SKILL_PATH|$SKILL_DEST|g" "$tpl" > "$dest"
  fi
  echo "  adapter -> $dest"
}

echo "Installing $SKILL_NAME for: $AGENT  (scope: $SCOPE)"

case "$AGENT" in
  claude)
    # Native skill system — no adapter needed; the host reads SKILL.md's frontmatter.
    DEST="$([ "$SCOPE" = user ] && echo "$HOME/.claude/skills" || echo "$PROJECT_DIR/.claude/skills")/$SKILL_NAME"
    mkdir -p "$(dirname "$DEST")"; rm -rf "$DEST"; cp -R "$SKILL_SRC" "$DEST"; rm -rf "$DEST/.git"
    echo "  skill  -> $DEST"
    echo "  Restart the session, then /skills lists it."
    ;;
  agents)
    place_skill; render AGENTS.md "$PROJECT_DIR/AGENTS.md" ;;
  codex)
    place_skill
    [ "$SCOPE" = user ] && render AGENTS.md "${CODEX_HOME:-$HOME/.codex}/AGENTS.md" \
                        || render AGENTS.md "$PROJECT_DIR/AGENTS.md" ;;
  opencode)
    place_skill
    [ "$SCOPE" = user ] && render AGENTS.md "$HOME/.config/opencode/AGENTS.md" \
                        || render AGENTS.md "$PROJECT_DIR/AGENTS.md" ;;
  cursor)
    place_skill; render cursor.mdc "$PROJECT_DIR/.cursor/rules/$SKILL_NAME.mdc" ;;
  kiro)
    place_skill
    [ "$SCOPE" = user ] && render kiro-steering.md "$HOME/.kiro/steering/$SKILL_NAME.md" \
                        || render kiro-steering.md "$PROJECT_DIR/.kiro/steering/$SKILL_NAME.md" ;;
  trae)
    place_skill
    [ "$SCOPE" = user ] && render trae-rule.md "$HOME/.trae/user_rules/$SKILL_NAME.md" \
                        || render trae-rule.md "$PROJECT_DIR/.trae/rules/$SKILL_NAME.md" ;;
  windsurf)
    place_skill; render windsurf-rule.md "$PROJECT_DIR/.windsurf/rules/$SKILL_NAME.md" ;;
  antigravity)
    # Antigravity also reads AGENTS.md natively; .agents/rules gives a declared trigger.
    place_skill
    [ "$SCOPE" = user ] && render antigravity-rule.md "$HOME/.gemini/config/rules/$SKILL_NAME.md" \
                        || render antigravity-rule.md "$PROJECT_DIR/.agents/rules/$SKILL_NAME.md" ;;
  copilot)
    place_skill; render AGENTS.md "$PROJECT_DIR/.github/copilot-instructions.md" ;;
  all)
    place_skill
    render AGENTS.md       "$PROJECT_DIR/AGENTS.md"
    render cursor.mdc      "$PROJECT_DIR/.cursor/rules/$SKILL_NAME.mdc"
    render kiro-steering.md "$PROJECT_DIR/.kiro/steering/$SKILL_NAME.md"
    render trae-rule.md    "$PROJECT_DIR/.trae/rules/$SKILL_NAME.md"
    render windsurf-rule.md "$PROJECT_DIR/.windsurf/rules/$SKILL_NAME.md"
    render antigravity-rule.md "$PROJECT_DIR/.agents/rules/$SKILL_NAME.md"
    render AGENTS.md       "$PROJECT_DIR/.github/copilot-instructions.md"
    ;;
  *) echo "unknown agent: $AGENT" >&2; exit 2;;
esac

echo "Done. Restart your agent session — instruction files are read at session start."
