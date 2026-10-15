#!/usr/bin/env bash
# PreToolUse guard for Edit/Write/MultiEdit.
# Blocks source changes that would break Bifolio's hard rules (CLAUDE.md):
#   - network permissions in manifests
#   - WebView usage
#   - references to projects excluded by the clean-room rule
# Exit 2 blocks the tool call and shows stderr to Claude.
set -euo pipefail

INPUT="$(cat)"
FILE_PATH="$(printf '%s' "$INPUT" | jq -r '.tool_input.file_path // empty')"
[ -z "$FILE_PATH" ] && exit 0

# Policy enforcement code and lint configuration must be able to name what they forbid.
case "$FILE_PATH" in
  */build-logic/*|*/config/*) exit 0 ;;
esac

# Only guard source and build files. Documentation may mention these names.
case "$FILE_PATH" in
  *.kt|*.kts|*.java|*.xml|*.toml|*.pro|*.properties) ;;
  *) exit 0 ;;
esac

NEW_TEXT="$(printf '%s' "$INPUT" | jq -r '
  [ .tool_input.content // empty,
    .tool_input.new_string // empty,
    ((.tool_input.edits // []) | map(.new_string // "") | join("\n"))
  ] | join("\n")')"

fail() {
  echo "Blocked by .claude/hooks/guard.sh: $1" >&2
  echo "File: $FILE_PATH" >&2
  echo "See CLAUDE.md, section 'Hard rules'." >&2
  exit 2
}

if printf '%s' "$NEW_TEXT" | grep -Eq 'android\.permission\.(INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE)'; then
  fail "network permissions are forbidden (PRV-01)."
fi

if printf '%s' "$NEW_TEXT" | grep -Eq 'android\.webkit|\bWebView\b'; then
  fail "WebView is forbidden (PRV-03, ADR-0002)."
fi

if printf '%s' "$NEW_TEXT" | grep -Eiq 'pilcrow|markwon|jlatexmath|prism4j|com\.marktail'; then
  fail "reference to a project excluded by the clean-room rule (PROVENANCE.md)."
fi

if printf '%s' "$NEW_TEXT" | grep -Eq 'com\.google\.firebase|com\.google\.android\.gms|crashlytics|sentry'; then
  fail "analytics, crash reporting and Play Services are forbidden (PRV-02)."
fi

exit 0
