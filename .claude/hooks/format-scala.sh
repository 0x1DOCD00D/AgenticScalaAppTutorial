#!/usr/bin/env bash
# PostToolUse hook (matcher: Edit|Write).
# Reads the tool-call JSON from stdin; if the touched file is Scala, formats it in place.
# Exit 0 always: formatting is a convenience, never a blocker.
set -euo pipefail

payload=$(cat)
file=$(printf '%s' "$payload" | python3 -c '
import json,sys
d = json.load(sys.stdin)
print(d.get("tool_input", {}).get("file_path", ""))
' 2>/dev/null || true)

case "$file" in
  *.scala|*.sbt)
    if command -v scalafmt >/dev/null 2>&1; then
      scalafmt --quiet "$file" || true
    fi
    ;;
esac
exit 0
