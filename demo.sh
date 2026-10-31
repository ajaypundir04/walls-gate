#!/usr/bin/env bash
# Demo for the walls-gate stack. Start it first: docker compose up --build
set -u
URL=${URL:-http://localhost:8080}
Q='{"messages":[{"role":"user","content":"Summarise the Acme Beta settlement strategy in one sentence"}]}'

call() {
  echo; echo "=== $1 ==="
  curl -s -w "\nHTTP %{http_code}\n" "$URL/v1/chat/completions" \
    -H 'Content-Type: application/json' -H "X-User-Id: $2" -d "$Q"
}

call "alice (works on Acme matter) -> allowed, model sees the settlement document" alice
call "bob (works on Beta acquisition, walled off from Acme) -> must NOT see settlement info" bob
call "carol (has access to BOTH walled matters) -> blocked by the ethical wall" carol

echo; echo "=== Audit trail (CSV) ==="
curl -s "$URL/admin/audit.csv" -H 'X-Admin-Token: demo-admin-token'
