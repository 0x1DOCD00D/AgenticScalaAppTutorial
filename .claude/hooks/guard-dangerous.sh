#!/usr/bin/env bash
# PreToolUse hook (matcher: Bash).
# A deterministic backstop under the permission rules: blocks command patterns that must never
# run from an agent context, regardless of how the model was prompted. Exit code 2 = block.
set -euo pipefail

payload=$(cat)
cmd=$(printf '%s' "$payload" | python3 -c '
import json,sys
d = json.load(sys.stdin)
print(d.get("tool_input", {}).get("command", ""))
' 2>/dev/null || true)

blocked_patterns=(
  "DROP TABLE"
  "DROP DATABASE"
  "TRUNCATE "
  "DELETE FROM tasks;"          # unqualified mass delete
  "terraform destroy"
  "aws rds delete"
  "aws ecr delete"
  "--force-delete"
  "rm -rf /"
)

shopt -s nocasematch
for pat in "${blocked_patterns[@]}"; do
  if [[ "$cmd" == *"$pat"* ]]; then
    echo "Blocked by guard-dangerous hook: command matches forbidden pattern '$pat'." \
         "If this is really needed, a human must run it manually." >&2
    exit 2
  fi
done
exit 0
