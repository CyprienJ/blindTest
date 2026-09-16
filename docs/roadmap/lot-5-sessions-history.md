# 12. Lot 5 — Sessions, WebSocket, and History Backend

[Up: Roadmap](../plan.md)

### Objective

Expose the game domain as an authenticated, server-authoritative application.

Dependencies: Lots 3 and 4.

### Tickets

#### SESSION-01 — Add active session models and store port

Status: completed on 2026-08-04.

Work:

- session ID, owner/host, participants, configuration, round state, version;
- `SessionStore` atomic read/update/remove operations;
- `InMemorySessionStore` with concurrency control;
- expiry metadata.

Acceptance:

- concurrent commands cannot overwrite newer state;
- the interface can be implemented by Redis later.

#### SESSION-02 — Create the solo-session REST endpoint

Status: completed on 2026-08-04.

Work:

- require verified authenticated user;
- validate configuration;
- snapshot/select tracks;
- create the one-player session;
- return session ID and connection instructions.

Acceptance:

- anonymous or unverified users cannot create a session;
- one user can create multiple sequential games safely.

#### SESSION-03 — Define STOMP commands and events

Status: completed on 2026-08-04.

Commands:

- connect/subscribe;
- start game;
- submit answer;
- skip round;
- request current snapshot/recover.

Events:

- session ready;
- round preparing/start;
- private secondary match;
- answer rejected/cooldown;
- round revealed;
- score updated;
- game finished;
- error.

Acceptance:

- payloads are versioned/documented;
- public session events and private player feedback are distinct;
- commands include idempotency IDs.

#### SESSION-04 — Authorize WebSocket destinations

Status: completed on 2026-08-04.

Work:

- authenticate STOMP connect;
- authorize session membership for commands/subscriptions;
- use user queues for private feedback;
- reject destination manipulation;
- define behavior for expired access tokens during a connected game.

Acceptance:

- a user cannot observe or command another user's session;
- integration tests use a real STOMP connection.

#### SESSION-05 — Orchestrate server timers

Status: completed on 2026-08-04.

Decision: [ADR-0015](../adr/0015-four-second-automatic-reveal-transition.md).

Work:

- schedule authoritative round deadlines;
- handle scheduler races with title/skip;
- persist state version before publishing;
- recover current snapshot after browser reconnect;
- inject scheduler/clock in tests.

Acceptance:

- title-at-deadline, skip-at-deadline, duplicate timer, and reconnect races produce
  one valid outcome.

#### SESSION-06 — Resolve playback URLs for active rounds

Status: completed on 2026-08-04.

Work:

- authorize session participant;
- pre-sign the snapshotted object key;
- include URL, `startAt`, deadline, and playback offset in round start;
- regenerate on authorized reconnect.

Acceptance:

- URL outlives the configured round plus buffer;
- object key is never sent to the browser as an authorization mechanism.

#### SESSION-07 — Enforce answer rate limits

Status: completed on 2026-08-04.

Work:

- short per-player cooldown;
- per-session command rate guard;
- stable feedback without score penalty;
- metrics for abusive rates.

Acceptance:

- normal typing is unaffected;
- automated spam cannot saturate matching or event publishing.

#### HIST-01 — Add durable history schema

Status: completed on 2026-08-04.

Decision: [ADR-0011](../adr/0011-data-and-rejected-media-retention.md).

Work:

- game history, participant, and ordered round tables;
- authenticated nullable user model prepared for future guests;
- configuration and track-answer snapshots;
- outcome, timing, and score;
- retention metadata.

Acceptance:

- a completed game is reproducible without current catalogue rows;
- deleting a track does not destroy historical summaries.

#### HIST-02 — Persist game completion idempotently

Status: completed on 2026-08-04.

Work:

- completion transaction;
- unique session/history key;
- retry after transient database failure;
- remove active state only after durable success or safe retry scheduling.

Acceptance:

- duplicate completion creates one history;
- failure does not silently lose a completed result.

#### HIST-03 — Add current-game summary endpoint

Status: completed on 2026-08-04.

Work:

- authorize the game owner;
- return total score and ordered round results;
- include correct title and secondary metadata after completion;
- do not expose YouTube URL or storage key.

Acceptance:

- summary survives browser refresh after completion.

#### SESSION-08 — Expire and clean abandoned sessions

Status: completed on 2026-08-04.

Work:

- define inactive/finished TTLs;
- scheduled cleanup;
- mark abandoned games according to product rules;
- remove timers and sensitive URLs;
- report cleanup metrics.

Acceptance:

- abandoned sessions cannot leak memory indefinitely;
- completed history is not removed.

#### SESSION-09 — Add backend integration and concurrency tests

Status: completed on 2026-09-02.

Work:

- REST + STOMP + PostgreSQL + MinIO/fake media;
- timer races;
- reconnect;
- idempotency;
- authorization;
- history completion.

Acceptance:

- the complete backend game flow passes without a browser.

### Lot exit criteria

- an authenticated player can complete a server-authoritative solo game;
- WebSocket authorization and reconnection work;
- results are durably persisted;
- active sessions are bounded and cleanable.
