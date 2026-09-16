# Load and concurrency smoke tests

[Up: Operations](README.md) · [Testing](../testing.md) · [Lot 7](../roadmap/lot-7-hardening.md)

E2E-05 proves the solo-game path holds up under realistic concurrency and records
where it would actually degrade. It runs on top of the isolated full-stack
environment ([testing](../testing.md#isolated-full-stack-environment)), so every
result below reflects the real backend, PostgreSQL, MinIO, and Nginx working
together — not mocks.

## Running it

```bash
./scripts/load-test.sh
```

One command builds and starts the stack (`docker-compose.test.yml`), runs
`load-tests/` (a headless Node/TypeScript tool, not Playwright — the gameplay
protocol is plain JSON-over-STOMP, so a browser adds no coverage here), and always
tears the stack down. Like `scripts/e2e-test.sh`, it picks a random Compose
project name and publishes no host ports, so it is safe to run alongside other
work. Scenario sizes are configurable via environment variables read by
`load-tests/src/config.ts` (`CONCURRENT_GAMES`, `HEADROOM_GAMES`,
`CATALOGUE_TRACK_COUNT`, and others) — pass them through
`docker compose ... run -e NAME=value load-tests` for a one-off smaller/larger run.

This is a manual/local smoke test, not a CI gate: no workflow invokes the
full-stack environment today (confirmed across `.github/workflows/*.yml`), and
this ticket does not add one.

## What it measures

The tool drives real accounts and sessions over REST and a real STOMP WebSocket
connection through Nginx (`http://frontend`, the same path a browser uses),
exactly like `frontend/e2e-full-stack/solo-game-journey.spec.ts` but without a
browser. It seeds one playlist of published tracks using the `e2e` profile's
`FakeProcessFactory` (instant, network-free imports) and a pool of player
accounts using `FakeEmailSender`/`/api/e2e/emails` and
`/api/e2e/admin-provisioning`, then runs:

1. **Baseline** — one game at the real product defaults (15 tracks, 30s rounds,
   ADR-0008) for reference latency.
2. **Concurrency** — 10 concurrent games (the ticket's required minimum), 5
   tracks / 10s rounds (ADR-0008 minimums, to keep wall-clock time reasonable).
3. **Headroom** — 40 concurrent games, same shape, as the non-production
   headroom scenario.
4. **Import isolation** — a batch of concurrent games running while several
   catalogue imports fire concurrently into the same shared playlist.

Every round cycles through four strategies (answer immediately, answer late for
a lower score, submit a wrong answer and let the round time out, explicitly
skip) so each run exercises real deadline-driven auto-reveal and the 4-second
reveal-to-next-round transition (ADR-0015), not just instant answers.

Each game's correctness is checked, not just its liveness:

- exactly one `GAME_FINISHED` per session, zero private `ERROR` events, no 5xx
  responses;
- each round's outcome matches what the virtual player actually did (correct
  answer → `TITLE_FOUND`, wrong-then-expired → `TIMEOUT`, `SKIP_ROUND` →
  `SKIPPED`);
- for a correct answer, the awarded score falls inside the range the ADR-0007
  scoring formula predicts from the client's measured elapsed time (a range,
  not exact equality, since server-authoritative elapsed time is always
  slightly larger than what the client measured before its answer reached the
  network);
- the durable `GET /api/sessions/{id}/summary` reconciles exactly against what
  was played live;
- in the import-isolation scenario, every in-flight game still completes with
  exactly the round count requested at creation despite the playlist growing
  underneath it (ADR-0008: game configuration and track selection are
  snapshotted at creation, so a concurrent catalogue edit cannot reach an
  already-created game).

The backend's own Actuator metrics (`jvm.memory.used`, `jvm.threads.live`,
`hikaricp.connections.active`/`pending`, `tomcat.threads.busy` — already
auto-bound by `spring-boot-starter-actuator`, no backend code changes needed) are
polled once a second throughout, admin-authenticated
(`/actuator/metrics/**` is admin-only and not proxied by Nginx, so the tool reads
it straight from `http://backend:8080`, not through the frontend). The tool also
samples its own CPU usage and event-loop delay, so a latency spike could be
attributed to the server or ruled out as harness overhead.

**Out of scope**, by design: STOMP disconnect/reconnect mid-game and
timer-race duplicate-command idempotency are not exercised here — the former is
E2E-06 (failure injection), the latter is already covered by
`SoloGameBackendFlowIntegrationTest`.

## Results (2026-09-16 run)

All four scenarios passed with zero correctness violations: 1 baseline game, 10
concurrent games, 40 concurrent games, and 10 concurrent games alongside 5
concurrent imports — 61 games total, all reaching a reconciled summary.

| Scenario | Games | Wall clock | Round terminal latency (p50 / p95 / max) |
|---|---:|---:|---|
| Baseline (15 tracks, 30s rounds) | 1/1 ok | 276.3s | 4ms / 30.0s / 30.0s |
| Concurrency (10 games, 5 tracks, 10s rounds) | 10/10 ok | 38.2s | 4ms / 10.0s / 10.0s |
| Headroom (40 games, same shape) | 40/40 ok | 38.4s | 10ms / 10.0s / 10.0s |
| Import isolation (10 games + 5 concurrent imports) | 10/10 ok | 38.2s | 4ms / 10.0s / 10.0s |

The p95/max figures are dominated by the round strategy that deliberately lets
a round time out — that is the correct, expected shape (the terminal event
arrives at the deadline), not a sign of server-side delay. The fast path (an
accepted answer's command→reveal latency) stayed at single-digit milliseconds
at every concurrency level tested, including 40 concurrent games.

Backend resource usage across the run (Actuator, admin-authenticated):

| Metric | Baseline | 10 concurrent | 40 concurrent | Import isolation |
|---|---:|---:|---:|---:|
| `jvm.memory.used` (avg) | 288 MB | 282 MB | 290 MB | 293 MB |
| `jvm.threads.live` (avg) | 122 | 162 | 162 | 165 |
| `hikaricp.connections.active` | 0 | 0 | 0 | 0 |
| `hikaricp.connections.pending` | 0 | 0 | 0 | 0 |
| `tomcat.threads.busy` | 0 | 0 | 0 | 0 |

Harness overhead (this Node process driving up to 40 concurrent virtual
players from one event loop): CPU stayed under 22% even at 40 concurrent games,
and event-loop p99 delay stayed flat around 11ms across every scenario — too
flat to be a load-sensitive signal at this resolution, so treat it as
inconclusive rather than as proof the harness had zero effect. It does rule out
gross harness self-starvation as an explanation for any of the numbers above.

## What this does and doesn't tell us

**No bottleneck was triggered up to 40 concurrent solo games.** Thread count,
memory, and HikariCP pool usage stayed flat and low across the 10→40 game
range, and wall-clock time for the 40-game run was barely different from the
10-game run (both are dominated by the fixed round/timeout durations, not by
server processing time). The backend's fixed 2-thread deadline scheduler
(`ExecutorSessionTaskScheduler`) was the prime suspect going in — a single
shared pool serving every active session's round timers — but 40 simultaneous
deadline expirations clearly isn't enough load to saturate even two threads
doing fast, non-blocking work per event.

**This means the safe operational limit is unproven, not high.** This run
demonstrates the required floor (10 concurrent games) and a comfortable 4x
headroom (40), both cleanly. It does not find where the system actually starts
to degrade — that would take pushing concurrency far higher (hundreds of
simultaneous games), which is beyond what a smoke test's wall-clock budget
should spend. If a production capacity target above the low tens of concurrent
games is ever anticipated, treat that as follow-up load testing, not something
this result already covers.

**Numbers here are host-noisy.** Per `docker-compose.test.yml`, no application
service (`backend`, `postgres-test`, `minio-test`) has a `cpus`/`mem_limit`
constraint — that is deliberate, for parallel-CI isolation — so absolute
throughput numbers depend on whatever else is running on the machine.
`load-tests` itself is pinned to 2 CPUs / 1 GB so it cannot starve the backend
it is measuring. Treat conclusions here as relative signals (does a metric grow
with load, does latency degrade, does a pool saturate), not as portable
absolute thresholds — rerun this on the target host before relying on the exact
figures for a capacity decision.

## Known limitations

- Single Node process/container drives every virtual player. Plausible for I/O-bound
  work at this scale (confirmed above), but a much larger headroom scenario should
  also watch harness CPU/event-loop numbers, not just server metrics.
- The import-isolation "snapshot isolation" check is structural (every in-flight
  game still completes with its originally requested round count) rather than a
  literal track-ID diff — `FakeProcessFactory` gives every imported track
  identical metadata, so track identity can't be distinguished by content, and
  `GameSummaryRoundResponse` does not expose track IDs to a REST client anyway.
- STOMP reconnect/disconnect behavior under load is not covered — see E2E-06.
