#!/usr/bin/env bash
# PostToolUse formatter: runs ktlint on the edited Kotlin file when ktlint is installed.
# Never blocks; formatting problems are caught by `./gradlew ktlintCheck` in CI.
set -uo pipefail

INPUT="$(cat)"
FILE_PATH="$(printf '%s' "$INPUT" | jq -r '.tool_input.file_path // empty')"

case "$FILE_PATH" in
  *.kt|*.kts) ;;
  *) exit 0 ;;
esac

if command -v ktlint >/dev/null 2>&1 && [ -f "$FILE_PATH" ]; then
  ktlint --format "$FILE_PATH" >/dev/null 2>&1 || true
fi
exit 0
