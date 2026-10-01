# Active sessions and completed games

[Up: Backend](README.md) · [Rules](../product/gameplay.md)

## Creation

`POST /api/sessions` requires a verified authenticated account. `SoloSessionService`
validates configuration, loads eligible published private-media tracks and aliases,
counts them per difficulty for the requested `DifficultyFilter`, randomly selects
matching tracks and their order, and creates immutable snapshots. The owner receives
an opaque participant identity; the initial session version is zero.

The response includes session identity, expiry, and STOMP connection destinations.
Accounts can create multiple independent solo sessions. Lookup/join and guest
flows are not delivered.

## Active state and commands

`SessionStore` is a replaceable port for atomic creation, reading, compare-and-set,
removal, and expiry. Its concurrent process-local adapter rejects stale writes.
An `ActiveSessionSnapshot` contains configuration, participants, game state,
secondary discoveries, version, command ledger, and lifecycle timestamps.

`SessionCommandController` routes STOMP commands to `SessionGameplayService`.
Membership and destination checks happen at the WebSocket boundary. Commands are
serialized per session and applied against the expected version. A bounded
idempotency ledger prevents a retried state-changing command from taking effect
twice. State is saved before events are published.

The pure game domain owns allowed transitions and scoring. The service owns the
clock, scheduling, rate limits, media signing, storage updates, and event delivery.
This separation lets domain tests run without Spring or external infrastructure.

## Timing and recovery

The server schedules round deadlines and the four-second reveal transition.
Callbacks verify current state so duplicate or late timers cannot resolve a round
twice. Answers at or after the deadline earn no title points.

`ROUND_STARTED` includes authoritative timing, excerpt offset and window length
(capped to the round duration), and an authorized
short-lived media URL. Reconnection requests a private `CURRENT_SNAPSHOT`, including
a fresh URL for a playing round. Snapshot recovery uses server state rather than
replaying local guesses. The [protocol contract](../websocket-protocol.md) specifies
commands, event payloads, versions, and destinations.

Per-player answer cooldown and per-session command windows bound input volume.
Repeated rate feedback is suppressed; aggregate metrics exclude session, account,
participant, and answer labels.

## Completion and retention

At game finish, history services persist one transactional aggregate keyed by
session UUID. Concurrent duplicate inserts are confirmed after rollback. Transient
database failures use deduplicated bounded-backoff retries; active state remains
until durable completion succeeds.

`GET /api/sessions/{sessionId}/summary` returns the authenticated owner's immutable,
ordered summary with non-cacheable responses. It excludes raw guesses, aliases,
provider URLs, and private media identifiers. Catalogue changes do not alter past
results. See [data and persistence](../architecture/data.md).

Default sliding inactivity TTL is 30 minutes; terminal retention is five minutes.
A one-minute cleanup task expires abandoned state, cancels timers, clears anti-spam
state, and records safe aggregate metrics. Durable completed history is retained
separately. Backend restarts lose active state.

## Source and tests

- [SoloSessionService](../../backend/src/main/java/com/blindtest/service/SoloSessionService.java)
- [SessionGameplayService](../../backend/src/main/java/com/blindtest/service/SessionGameplayService.java)
- [Session model](../../backend/src/main/java/com/blindtest/model/session)
  and [session store](../../backend/src/main/java/com/blindtest/store/)
- [GameHistoryCompletionService](../../backend/src/main/java/com/blindtest/service/GameHistoryCompletionService.java)
- [SessionCleanupService](../../backend/src/main/java/com/blindtest/service/SessionCleanupService.java)
- [Browser client](../frontend/gameplay.md) and [integration coverage](../testing.md)
