#!/usr/bin/env bash
# smoke-test.sh <host> — the deploy gate. Exercises liveness, readiness, and a
# full create/read/advance/delete round-trip through all three tiers.
set -euo pipefail

HOST="${1:?usage: smoke-test.sh <alb-dns-or-host[:port]>}"
BASE="http://${HOST}"

fail() { echo "SMOKE FAIL: $*" >&2; exit 1; }

echo "==> /healthz"
curl -fsS --max-time 10 "${BASE}/healthz" | grep -q '"status":"ok"' || fail "/healthz not ok"

echo "==> /readyz (database round-trip)"
curl -fsS --max-time 10 "${BASE}/readyz" | grep -q '"database":"ok"' || fail "/readyz not ok"

echo "==> POST /api/tasks"
CREATED=$(curl -fsS --max-time 10 -X POST "${BASE}/api/tasks" \
  -H 'Content-Type: application/json' \
  -d '{"title":"smoke-test task"}')
ID=$(echo "$CREATED" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])') \
  || fail "create did not return a task: $CREATED"

echo "==> PATCH /api/tasks/${ID} -> InProgress"
curl -fsS --max-time 10 -X PATCH "${BASE}/api/tasks/${ID}" \
  -H 'Content-Type: application/json' \
  -d '{"status":"InProgress"}' | grep -q '"status":"InProgress"' || fail "transition failed"

echo "==> DELETE /api/tasks/${ID}"
code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 -X DELETE "${BASE}/api/tasks/${ID}")
[[ "$code" == "204" ]] || fail "delete returned $code"

echo "==> GET deleted task returns 404"
code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "${BASE}/api/tasks/${ID}")
[[ "$code" == "404" ]] || fail "expected 404, got $code"

echo "SMOKE OK ${BASE}"
