# Failure-injection tests

[Up: Operations](README.md) · [Testing](../testing.md) · [Lot 7](../roadmap/lot-7-hardening.md)

E2E-06 injects the seven failure scenarios the ticket names and checks two things for
each: does it fail cleanly (no crash, no corrupt track, no duplicate history), and can
a player or admin tell what happened instead of the app silently hanging. Coverage sits
at whichever layer actually exercises the failure — a backend integration test with a
real, controllable dependency where the browser adds nothing, or a full-stack Playwright
spec where the browser is the point.

## Scenario coverage

| Scenario | Where | What it proves |
|---|---|---|
| MinIO temporarily unavailable | `MinioMediaStorageIntegrationTest#unavailableStorageIsTranslatedAndTheOperationCanBeRetried` | An unreachable endpoint is translated to `MediaStorageException`, not a raw driver exception or a hang; the same storage instance succeeds once reachable again. |
| PostgreSQL transient failure | `GameHistoryPersistencePostgresFailureIntegrationTest` | A real TCP proxy ([Toxiproxy](https://github.com/Shopify/toxiproxy)) cuts the connection mid-write; the durable game-history write fails cleanly with no partial row, and retrying the same completed session afterward persists exactly one aggregate — never a duplicate. |
| Backend restart during an active in-memory game | `frontend/e2e-full-stack/failure-injection.spec.ts` | See [data-loss boundary](#the-in-memory-data-loss-boundary) below. |
| Browser disconnect/reconnect | `frontend/e2e-full-stack/failure-injection.spec.ts` | A real WebSocket drop mid-round recovers automatically: the resilient STOMP client reconnects, re-requests a snapshot, and the round stays playable with no manual recovery action. |
| Import process failure | `ImportJobRunnerTest` (unit, exhaustive: provider failure, timeout, size limit, invalid media, storage failure) + `failure-injection.spec.ts` (one browser-observable case) | A failed import job never uploads media, never fires the done event, and never leaves a published track — the pending placeholder track stays unpublished and the admin sees a clear, safe error message and can start over immediately. |
| Expired pre-signed URL | `MinioMediaStorageIntegrationTest#expiredPresignedUrlIsRejectedAndAFreshOneStillWorks` | A link held past its TTL is refused by MinIO (403), not silently served; the underlying object remains retrievable through a freshly issued URL — expiry rejects the link, not the media. |
| Timer/answer race | `SoloGameBackendFlowIntegrationTest#answerRaceWithDeadlineTimerYieldsExactlyOneAuthoritativeOutcome` (alongside the pre-existing `duplicateDeadlineTimerProducesOneRevealAcrossTheFullStack`) | A correct answer arriving at the exact instant the authoritative deadline timer fires resolves to exactly one outcome and exactly one version bump — never a double reveal or a double-scored round. |

Backend-only scenarios (MinIO, PostgreSQL, expired URL, timer race) have no
meaningful browser angle: a Playwright spec driving them would just add browser
startup time around the same assertions a JVM-level integration test already
makes with a real MinIO/PostgreSQL container. Browser-only scenarios (restart,
disconnect/reconnect) are about what the *client* does when the backend fails,
which only a real browser can observe.

## The in-memory data-loss boundary

`InMemorySessionStore` is a process-local `ConcurrentHashMap` by design
(`docs/architecture.md`, `docs/roadmap/product-decisions.md`) — the game domain
must not couple to JPA, STOMP, or the system clock, and a durable write happens
exactly once, at natural game completion (`GameHistoryPersistenceService`,
called by `GameHistoryCompletionService` from `SessionGameplayService` when the
game reaches `GAME_FINISHED`). The consequence is exact
and worth stating plainly:

- **A backend restart loses every active game outright.** Current round, score,
  connected participants, everything — gone. Nothing partial is written; there
  is no partial state to write, because nothing is durable until completion.
- **A player reconnecting to a restarted backend gets no game back**, ever —
  there is nothing to recover from. This is different from a network blip,
  where the session is still alive in memory and a snapshot resumes it.
- **A finished game is never at risk.** Once `GAME_FINISHED` persists, restarts,
  catalogue edits, and further backend crashes cannot touch it
  (`docs/operations/README.md`: "Completed-game snapshots survive catalogue
  edits and backend restarts").

This isolated stack's browser-driven test runner has no way to actually restart
the `backend` container — that would need a Docker socket mounted into the
`tests` container, which this stack deliberately doesn't grant (consistent with
the conservative infrastructure stance in
[completion rules](../roadmap/completion-rules.md)). Instead, the e2e-only
`DELETE /api/e2e/active-sessions` backdoor
(`E2eTestSupportController#discardActiveSessions`) reproduces the one
externally observable effect a real restart has on gameplay: every active
session disappears, exactly as `InMemorySessionStore` would start empty after a
process restart. What a real restart does differently — also dropping the
TCP/WebSocket connections themselves — does not change what the client
observes, because the STOMP authorization interceptor already re-validates
session membership on every command against the (now empty) store.

### A bug this surfaced and fixed

Before this ticket, a client whose session vanished from the store — by this
backdoor, by natural TTL expiry, or by a real restart — got no error at all.
`SessionDestinationAuthorizationInterceptor` rejects any command or
resubscription for an unknown session with a STOMP `ERROR` frame, which per the
STOMP spec closes that connection; RxStomp's automatic reconnect then opened a
new connection, immediately tried to resubscribe to the same now-gone session,
got rejected the same way, and reconnected again — forever. The player just
saw "Connecting you to the live game…" with no way out except manually leaving
the page.

`GameSocketService` now treats any STOMP `ERROR` frame as fatal: it stops the
client instead of letting it reconnect into the same rejection, and emits
`fatalError$` so `GameRoomComponent` shows a normal terminal error ("Something
went wrong", with the server's own denial message) instead of spinning
indefinitely. This mirrors the precedent set by E2E-02's refresh-cookie fix —
full-stack failure injection surfacing a real, fixable gap rather than just
documenting one. See `game-socket.service.ts`/`game-room.component.ts` and
their specs for the change.

### Simulating a browser network drop

`context.setOffline(true)` was the first thing tried for the disconnect/reconnect
scenario and doesn't work here: confirmed empirically (40s of "offline" with no
detected drop), Chromium's offline network emulation does not interrupt a
WebSocket connection that is already established — it only affects new
connections. `failure-injection.spec.ts` instead uses
`context.routeWebSocket()` to track the page's real WebSocket route (every
message still flows to and from the real backend via `connectToServer()`'s full
pass-through) and calls `.close()` on it at the moment the test wants a blip.
This is not protocol mocking — no message is faked or altered — it is the one
reliable way to sever a specific real connection on demand instead of waiting
on non-deterministic Chromium/heartbeat timing.

### A production reliability gap this surfaced

Building the PostgreSQL scenario surfaced a second real gap, this time outside
the test itself. The first version of `GameHistoryPersistencePostgresFailureIntegrationTest`
hung indefinitely: Toxiproxy's connection cut blackholes bytes on an
already-open connection rather than resetting it, so a query already in flight
blocked on a plain socket read with no signal it would ever resume. Hikari's
`connection-timeout` only bounds *acquiring* a connection from the pool, not a
read on one already checked out, so nothing rescued it.

The test now sets `spring.datasource.hikari.data-source-properties.socketTimeout=3`
to bound that read. **The application's own datasource configuration
(`application.properties`, `application-e2e.properties`) sets no such
timeout**, so the same class of network partition against the real database
could hang a real backend request thread indefinitely today. This is a real
finding, not just a test-harness fix: deliberately out of scope here because
choosing a safe production `socketTimeout` needs its own tuning and testing
against real query latencies, not a value picked to make one test fast. Flagged
for a follow-up ticket rather than changed silently.

## Running it

Backend scenarios run with the rest of the suite:

```bash
cd backend
mvn --batch-mode checkstyle:check test
```

`GameHistoryPersistencePostgresFailureIntegrationTest` needs a Docker-compatible
container runtime (it starts its own PostgreSQL and Toxiproxy containers, like
the existing MinIO integration test).

Browser scenarios run with the full-stack suite:

```bash
./scripts/e2e-test.sh
```

## Known limitations

- A literal backend process restart is not exercised, only its one observable
  effect on the session store — see above. Container-level restart behavior
  (graceful shutdown, health-check flapping, restart policy) belongs to
  [Lot 8 operations](../roadmap/lot-8-operations.md), not this ticket.
- The PostgreSQL scenario cuts the connection outright (Toxiproxy
  `setConnectionCut`) rather than injecting latency or packet loss. A full cut
  is the scenario that actually matters here — a slow-but-alive connection
  degrades performance (covered by [load testing](load-testing.md)'s pool
  metrics) but doesn't test the "fails cleanly, no partial write" property this
  scenario targets.
- The expired pre-signed URL test uses a 1-second TTL rather than the stack's
  real 5-minute `S3_PRESIGNED_URL_TTL`, to keep the test fast; MinIO's own
  expiry check is what's being verified, and it doesn't depend on the TTL's
  magnitude.
