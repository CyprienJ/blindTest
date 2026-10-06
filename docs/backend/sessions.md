# Active sessions and completed games

[Up: Backend](README.md) · [Rules](../product/gameplay.md)

## Creation

`POST /api/sessions` requires a verified authenticated account. `SoloSessionService`
delegates to `GameSetupService`, which validates configuration, loads eligible
published private-media tracks and aliases,
counts them per difficulty for the requested `DifficultyFilter`, randomly selects
matching tracks and their order, and creates immutable snapshots. The owner receives
an opaque participant identity; the initial session version is zero.

The response includes session identity, expiry, and STOMP connection destinations.
Accounts can create multiple independent solo sessions.

Multiplayer games start from a lobby (`LobbyService`, `InMemoryLobbyStore`). A lobby
holds the requested configuration, a unique six-character code, up to ten members
with ready flags, and the host; it slides on the session inactivity TTL and a
scheduled sweep frees expired codes. Start re-runs `GameSetupService`, creates a
`CREATED` session under the lobby's ID with every member as a participant and the
host as owner, removes the lobby, and publishes `LOBBY_STARTED`. Guests join through
`GuestAdmission` (rate limits, nickname, Turnstile) and become members without an
account; every lookup matches participants by principal ID — the account ID, or a
guest's participant ID — so gameplay, private queues, and summaries treat both alike.
The lobby also holds the host's `AudioMode`, which only the browser acts on.

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

`PAUSE_ROUND` and `RESUME_ROUND` ([ADR-0021](../adr/0021-pause-rounds-and-reveals.md))
record `pausedAt` on the round snapshot. Timer callbacks ignore a paused round.
Resuming a playing round moves its scoring and playback windows by the paused time
and reschedules the deadline; resuming a reveal adds the paused time to the round's
`revealPause`, which delays the transition. Only the owner may pause, and multiplayer
sessions may pause only a reveal.

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

`GET /api/sessions/{sessionId}/summary` returns an immutable, ordered summary to any
registered participant, from their own point of view (their score, titles, and
round points) with ranked standings; non-participants get the same 404 as an
unknown game. Responses are non-cacheable. It excludes raw guesses, aliases,
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
