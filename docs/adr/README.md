# Architecture Decision Records

Architecture Decision Records (ADRs) capture decisions that materially affect the
structure, operation, security, or evolution of Blindtest. The delivery roadmap in
[`../plan.md`](../plan.md) remains authoritative for product scope and sequencing;
ADRs explain the technical choices made while delivering it.

## Create an ADR

1. Copy [`0000-template.md`](0000-template.md).
2. Assign the next four-digit sequence number and a short kebab-case title, for
   example `0001-modular-monolith.md`.
3. Set the status to `Proposed` while the decision is under review.
4. Record the decision, its trade-offs, and rejected alternatives before dependent
   implementation begins.
5. Change the status to `Accepted` when approved. Do not rewrite an accepted
   decision to change its meaning; supersede it with a new ADR and link both.

Allowed statuses are `Proposed`, `Accepted`, `Rejected`, `Deprecated`, and
`Superseded by ADR-NNNN`.

## Index

| ADR | Status | Decision |
|---|---|---|
| [ADR-0001](0001-modular-monolith.md) | Accepted | Use a modular monolith for the first release |
| [ADR-0002](0002-server-authoritative-sessions.md) | Accepted | Make the server authoritative for game sessions |
| [ADR-0003](0003-stomp-for-game-events-from-v1.md) | Accepted | Use STOMP for game events from V1 |
| [ADR-0004](0004-in-memory-session-store.md) | Accepted | Keep active sessions in memory behind a port |
| [ADR-0005](0005-private-s3-media-and-presigned-playback.md) | Accepted | Store media privately in S3 and use pre-signed playback URLs |
| [ADR-0006](0006-postgresql-history-snapshots.md) | Accepted | Persist completed-game history snapshots in PostgreSQL |
