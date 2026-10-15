#!/usr/bin/env bash
# Stop hook: before Claude finishes, run the fast engine tests if Kotlin files changed.
# Exit 2 keeps Claude working and feeds the failure back.
set -uo pipefail

INPUT="$(cat)"
if [ "$(printf '%s' "$INPUT" | jq -r '.stop_hook_active // false')" = "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
[ -x ./gradlew ] || exit 0
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || exit 0

CHANGED="$( { git diff --name-only HEAD 2>/dev/null; git ls-files --others --exclude-standard; } | grep -E '\.(kt|kts)$' || true)"
[ -z "$CHANGED" ] && exit 0

# Only run test tasks for engine modules that already exist (they appear during M0 and M1).
TASKS=""
for m in model parse layout; do
  [ -f "core/$m/build.gradle.kts" ] && TASKS="$TASKS :core:$m:test"
done
[ -z "$TASKS" ] && exit 0

if ! OUTPUT="$(./gradlew --quiet $TASKS 2>&1)"; then
  echo "Engine tests failed. The task is not done until they pass." >&2
  printf '%s\n' "$OUTPUT" | tail -n 60 >&2
  exit 2
fi
exit 0
