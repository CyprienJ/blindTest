# 17. Post-V1 Roadmap

[Up: Roadmap](../plan.md)

### Lot P1 — Profiles and history browsing

- profile read/update;
- avatar strategy;
- password change;
- history list/detail UI;
- public/private history policy decision;
- Playwright and authorization coverage.

Architecture already prepared by durable V1 history and authenticated participant
identity.

### Lot P2 — Multiplayer and guests

- lobby and short join codes;
- guest participant tokens;
- registered and guest players;
- host controls;
- readiness;
- up to ten participants;
- synchronized `startAt`;
- per-player private secondary discoveries;
- scores and scoreboard;
- disconnect/rejoin;
- host departure policy;
- multiplayer concurrency tests.

The V1 server-authoritative engine, participant abstraction, private queues, and
session store port are prerequisites.

### Lot P3 — Additional game modes

- reveal-mode product decisions;
- alternative scoring;
- skip voting;
- categories/decades and mixed selections;
- mode-versioned configuration;
- compatibility with historical snapshots.

### Lot P4 — Friends, invitations, and notifications

- user search;
- friend requests;
- friend list/removal;
- session invitations;
- per-user notifications;
- privacy/rate-limit decisions.

### Lot P5 — Horizontal scaling

Trigger: measured load or availability need, not speculation.

- Redis `SessionStore`;
- distributed locks/versioning;
- pub/sub event propagation;
- multiple backend instances;
- load-balanced WebSocket strategy;
- distributed timer ownership;
- chaos/restart tests.

### Lot P6 — Storage evolution

Trigger: VPS capacity, reliability, or traffic.

- change S3 endpoint/credentials to managed object storage or distributed MinIO;
- copy objects with checksum verification;
- optional CDN;
- preserve object keys and application interfaces;
- rehearse rollback.
