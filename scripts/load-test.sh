#!/usr/bin/env bash
# One command to start, load-test, and stop the isolated full-stack environment (E2E-05).
#
# Usage: ./scripts/load-test.sh
#
# Mirrors scripts/e2e-test.sh's isolation pattern (random Compose project name, no
# published host ports, always torn down) but runs the headless load-tests/ runner
# instead of the Playwright suite. See docs/operations/load-testing.md.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

project="blindtest-load-$$-${RANDOM}"
compose=(docker compose -p "$project" -f docker-compose.test.yml)

cleanup() {
  local status=$?
  "${compose[@]}" logs --no-color --timestamps > "load-test-stack-${project}.log" 2>&1 || true
  "${compose[@]}" down --volumes --remove-orphans
  exit "$status"
}
trap cleanup EXIT

# Only the app services: `load-tests` is a one-shot runner, not something to wait healthy.
"${compose[@]}" up --detach --build --wait frontend
"${compose[@]}" run --rm --build load-tests
