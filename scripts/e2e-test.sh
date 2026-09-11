#!/usr/bin/env bash
# One command to start, test, and stop the isolated full-stack environment (E2E-01).
#
# Usage: ./scripts/e2e-test.sh
#
# Picks a random Compose project name per invocation so concurrent CI runs never share the
# Postgres database, the MinIO bucket, or the internal Docker network: Compose namespaces
# containers, networks, and named volumes by project name, and this stack publishes no host
# ports, so nothing here can collide with another run on the same machine. The stack and its
# volumes are always torn down on exit, pass or fail.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

project="blindtest-e2e-$$-${RANDOM}"
compose=(docker compose -p "$project" -f docker-compose.test.yml)

cleanup() {
  local status=$?
  "${compose[@]}" logs --no-color --timestamps > "e2e-stack-${project}.log" 2>&1 || true
  "${compose[@]}" down --volumes --remove-orphans
  exit "$status"
}
trap cleanup EXIT

# Only the app services: `tests` is a one-shot runner, not something to wait healthy.
"${compose[@]}" up --detach --build --wait frontend
"${compose[@]}" run --rm --build tests
