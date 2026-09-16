# 7. WebSocket / STOMP

[Up: Target design](../target.md)

Historical target design, not an implementation reference. Validated roadmap
decisions and executable code take precedence. See [current architecture](../../architecture.md).

### Connection

```
URL: ws://host/ws
Protocol: STOMP 1.2
Header: Authorization: Bearer <JWT or sessionToken>
```

Native WebSocket clients connect to `/ws`. SockJS clients use the separate
`/ws-sockjs` fallback endpoint.

### Gameplay destinations

The authoritative V1 payload contract, versioning, idempotency, and recovery rules
are documented in [`websocket-protocol.md`](../../websocket-protocol.md).

Gameplay `SEND` and `SUBSCRIBE` frames are authorized against the active in-memory
session snapshot. Only exact UUID destinations are accepted, and private feedback
uses Spring's principal-scoped user queues. JWT validity is enforced at `CONNECT`;
an established connection remains authenticated through token expiry, while a
reconnect requires a fresh valid token.

| Destination | Recipients | Content |
|---|---|---|
| `/topic/sessions/{sessionId}` | Authorized session members | Shared game events |
| `/user/queue/sessions/{sessionId}` | One authenticated participant | Private feedback and recovery |
| `/user/queue/notifications` | 1 connected user | Invitations, friend requests |
| `/user/queue/import/{jobId}` | 1 admin | Import progress |

### Gameplay commands (client → server)

| Destination | Description |
|---|---|
| `/app/sessions/{sessionId}` | Versioned `START_GAME`, `SUBMIT_ANSWER`, `SKIP_ROUND`, `ADVANCE_ROUND`, or `REQUEST_SNAPSHOT` command |

### Shared gameplay event types (`/topic/sessions/{sessionId}`)

| Type | Trigger |
|---|---|
| `SESSION_READY` | Initial safe session projection |
| `ROUND_PREPARING` | New round is being prepared |
| `ROUND_STARTED` | New round with playback URL and authoritative timestamps |
| `ROUND_REVEALED` | Reveal of the correct answer + scores |
| `SCORE_UPDATED` | Authoritative participant score changed |
| `GAME_FINISHED` | End of game |

Private `SECONDARY_MATCH`, `ANSWER_REJECTED`, `CURRENT_SNAPSHOT`, and `ERROR`
events use `/user/queue/sessions/{sessionId}` and are never broadcast to the topic.

### Notification Events (`/user/queue/notifications`)

| Type | Payload |
|---|---|
| `SESSION_INVITE` | `{ fromUser, sessionCode, sessionState }` |
| `FRIEND_REQUEST` | `{ fromUser, requestId }` |
| `FRIEND_REQUEST_ACCEPTED` | `{ byUser }` |

### Import Events (`/user/queue/import/{jobId}`)

| Type | Payload |
|---|---|
| `IMPORT_PROGRESS` | `{ jobId, status, progress }` |
| `IMPORT_DONE` | `{ jobId, trackId }`; the admin then requests an authorized preview URL |
| `IMPORT_FAILED` | `{ jobId, errorMessage }` |

---
