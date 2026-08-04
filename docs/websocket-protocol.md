# Gameplay STOMP Protocol

Status: V1 contract defined by SESSION-03. Authentication, membership authorization,
and private user-queue routing are implemented by SESSION-04. SESSION-05 delivers
the authoritative command orchestration, timers, versioning, and recovery snapshot.
SESSION-06 adds participant-authorized signed playback URLs.

## Connection and destinations

Clients use STOMP 1.2 and authenticate the `CONNECT` frame:

```text
WebSocket: /ws
SockJS fallback: /ws-sockjs
Authorization: Bearer <access-token>
```

`POST /api/sessions` returns the protocol version and the exact destinations for
the created UUID session:

| Direction | Destination | Purpose |
|---|---|---|
| client → server | `/app/sessions/{sessionId}` | All gameplay commands |
| server → session | `/topic/sessions/{sessionId}` | Shared authoritative events |
| server → player | `/user/queue/sessions/{sessionId}` | Private feedback and recovery |

The client subscribes to both outbound destinations before sending `START_GAME`.
STOMP `CONNECT` and `SUBSCRIBE` are protocol frames, not application command
payloads.

## Authentication and authorization

- `CONNECT` must carry a valid access JWT in the native `Authorization` header.
- Every gameplay `SEND` and `SUBSCRIBE` resolves the canonical UUID destination
  against the active `SessionStore` and verifies that the authenticated account is
  a participant.
- Only the exact destinations returned by `POST /api/sessions` are accepted.
  Malformed UUIDs, added path segments, cross-channel use, direct sends to topics or
  queues, and attempts to name another user's queue are rejected.
- Missing, expired, and non-member sessions share the same denial response so the
  WebSocket boundary does not disclose whether a session identifier exists.
- Private events are routed server-side with the authenticated principal name and
  `convertAndSendToUser`; a client-provided recipient path is never trusted.

Access-token validity is checked when the STOMP connection is established. An
already authenticated connection may finish its active game after that token's
expiry, subject to session membership and session expiry checks on every gameplay
frame. A disconnected client must obtain a valid access token before reconnecting;
tokens cannot be refreshed inside an existing STOMP connection.

## Versioning and ordering

The current `protocolVersion` is `1`. Every command includes it directly and every
event includes it in `metadata`. A server supporting only V1 rejects another
version rather than guessing its shape.

Every command contains:

- `commandId`: client-generated UUID used as its idempotency identity;
- `expectedSessionVersion`: last authoritative session version rendered by the
  client;
- `type`: stable command discriminator;
- `answer`: required only for `SUBMIT_ANSWER`, otherwise `null` or omitted.

Every event contains metadata with a server-generated `eventId`, `sessionId`,
monotonically increasing `sessionVersion`, and authoritative `occurredAt` instant.
Clients ignore an event older than their rendered session version and replace
local state from `CURRENT_SNAPSHOT` after reconnect.

## Commands

```json
{
  "protocolVersion": 1,
  "commandId": "33333333-3333-4333-8333-333333333333",
  "expectedSessionVersion": 7,
  "type": "SUBMIT_ANSWER",
  "answer": "Take on me"
}
```

| Type | Additional data | Meaning |
|---|---|---|
| `START_GAME` | none | Owner requests the first round |
| `SUBMIT_ANSWER` | non-blank `answer` | Participant submits one guess |
| `SKIP_ROUND` | none | Owner requests the current round to be skipped |
| `ADVANCE_ROUND` | none | Owner requests an idempotent early transition during the reveal window |
| `REQUEST_SNAPSHOT` | none | Requests private recovery state from the expected version |

All five command types carry an idempotency UUID. The server keeps a bounded
per-session ledger for state-changing commands. It rejects duplicate, stale,
unauthorized, and out-of-state commands without applying a second transition.
`REQUEST_SNAPSHOT` is read-only and always returns the latest private projection,
even when the caller's expected version is stale.

## Shared events

Shared events use this envelope and are published only to the member-authorized
session topic:

```json
{
  "metadata": {
    "protocolVersion": 1,
    "eventId": "44444444-4444-4444-8444-444444444444",
    "sessionId": "11111111-1111-4111-8111-111111111111",
    "sessionVersion": 8,
    "occurredAt": "2026-08-04T13:00:00Z"
  },
  "type": "ROUND_STARTED",
  "payload": {
    "roundIndex": 2,
    "totalRounds": 10,
    "startsAt": "2026-08-04T13:00:02Z",
    "deadline": "2026-08-04T13:00:32Z",
    "excerptStartSeconds": 15,
    "playbackUrl": "https://media.example/audio.mp3?signature=short-lived"
  }
}
```

| Type | Payload |
|---|---|
| `SESSION_READY` | game `state`, `totalRounds`, `expiresAt` |
| `ROUND_PREPARING` | `roundIndex`, `totalRounds` |
| `ROUND_STARTED` | round position, `startsAt`, `deadline`, excerpt offset, short-lived `playbackUrl` |
| `ROUND_REVEALED` | outcome, canonical title/artist, optional composer/album, round and total points, `transitionAt` |
| `SCORE_UPDATED` | `participantId`, round points, total score |
| `GAME_FINISHED` | total score and title/skip/timeout aggregate counts |

The server never publishes an object-storage key as a separate browser credential.
For each round start it signs the immutable media snapshot only after session
authorization. The URL remains valid through the round deadline plus the fixed
30-second reconnect buffer and is treated as a short-lived bearer credential.

## Private events

Private events are sent through the recipient's user queue. They add
`recipientParticipantId` and an optional `commandId` to the common metadata. The
command ID is mandatory for command feedback and may be absent only for an
asynchronous `ERROR`.

```json
{
  "metadata": {
    "protocolVersion": 1,
    "eventId": "44444444-4444-4444-8444-444444444444",
    "sessionId": "11111111-1111-4111-8111-111111111111",
    "sessionVersion": 8,
    "occurredAt": "2026-08-04T13:00:00Z"
  },
  "recipientParticipantId": "22222222-2222-4222-8222-222222222222",
  "commandId": "33333333-3333-4333-8333-333333333333",
  "type": "SECONDARY_MATCH",
  "payload": {
    "answerType": "ARTIST",
    "matchedValue": "a-ha",
    "alreadyFound": false
  }
}
```

| Type | Payload |
|---|---|
| `SECONDARY_MATCH` | secondary `answerType`, display value, `alreadyFound` |
| `ANSWER_REJECTED` | stable rejection `reason`, optional cooldown `retryAt` |
| `CURRENT_SNAPSHOT` | game/round state, score, playback/reveal projection, only this player's secondary discoveries, expiry |
| `ERROR` | stable `code`, safe `message`, `recoverable` |

`ANSWER_REJECTED.reason` is one of `WRONG_ANSWER`, `ALREADY_FOUND`, `COOLDOWN`,
`STALE_SESSION_VERSION`, `INVALID_ROUND_STATE`, or `DUPLICATE_COMMAND`.
`retryAt` is present only for `COOLDOWN`.

Title success is never a `SECONDARY_MATCH`: it ends the round and is represented by
the shared reveal/score events. Artist, composer, and album discoveries remain in
the private queue and in only that participant's recovery snapshot.

## Authoritative timing and recovery

- A round deadline is scheduled from the persisted `startsAt` and `deadline`; the
  browser never decides that a round timed out.
- Title, skip, and deadline commands race through one compare-and-set transition.
  The winning snapshot is persisted with its new session version before any event
  carrying that version is published.
- A revealed round exposes `transitionAt`, exactly four seconds after `revealedAt`.
  The server advances automatically then; an owner `ADVANCE_ROUND` command may win
  the same transition earlier, and duplicate or late timer callbacks are harmless.
- `CURRENT_SNAPSHOT` retains the original playback window while playing. During
  reveal it also contains the canonical title, artist, optional composer/album,
  outcome, points, and `advanceAt`, plus only the requesting participant's private
discoveries.

## Command-rate limits

- Accepted answer attempts establish a 300 ms cooldown for that participant. The
  first faster attempt receives `ANSWER_REJECTED` with reason `COOLDOWN` and the
  authoritative `retryAt`; further attempts before that instant are silently
  dropped and never reach normalization, matching, scoring, or event publication.
- Each session accepts at most 20 authenticated command frames in a fixed
  one-second window. The first excess frame receives a private `ERROR` with code
  `SESSION_RATE_LIMITED`; later excess frames in that window are silently dropped.
- Rate rejection never changes the session version or score. Normal commands at
  the boundary are accepted, and rate state is isolated between sessions and
  participants.
- Operational counters are aggregate-only and never label answers, session IDs,
  participant IDs, or account IDs.
- An authorized `REQUEST_SNAPSHOT` during `PLAYING` generates a fresh signed URL
  whose validity covers the remaining playback window and reconnect buffer. A
  reconnect never reuses or exposes the raw object key.

## Evolution rules

- Existing discriminator meanings and required fields do not change within V1.
- Additive optional fields may be introduced only when old clients can ignore them.
- Breaking shape or semantic changes require a new `protocolVersion` and explicit
  server/client support during rollout.
- Event timestamps and versions are server authoritative; browser clocks and local
  ordering never decide score or state transitions.
