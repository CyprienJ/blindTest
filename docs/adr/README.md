# Architecture Decision Records

[Up: Architecture](../architecture.md)

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
| [ADR-0007](0007-title-score-rounding-and-minimum.md) | Accepted | Round title scores to the nearest integer with a 100-point minimum |
| [ADR-0008](0008-solo-game-configuration-limits.md) | Accepted | Use the reference solo-game configuration limits |
| [ADR-0009](0009-admin-configured-playback-offset.md) | Partially superseded by ADR-0016 | Let administrators configure a track playback offset |
| [ADR-0010](0010-first-administrator-operator-command.md) | Partially superseded by ADR-0017 | Provision the first administrator with an operator command |
| [ADR-0011](0011-data-and-rejected-media-retention.md) | Accepted | Apply balanced retention to histories, imports, and rejected media |
| [ADR-0012](0012-assume-operator-holds-content-rights.md) | Accepted | Treat content rights as an operator-owned prerequisite for now |
| [ADR-0013](0013-rely-on-daily-vps-backups-for-now.md) | Accepted | Rely on the VPS daily backup system for now |
| [ADR-0014](0014-randomize-game-track-selection-and-order.md) | Accepted | Randomize game track selection and order |
| [ADR-0015](0015-four-second-automatic-reveal-transition.md) | Accepted | Advance automatically after a four-second reveal |
| [ADR-0016](0016-per-track-looped-excerpt-window.md) | Accepted | Let administrators set a looped per-track excerpt window |
| [ADR-0017](0017-invitation-only-registration.md) | Accepted | Make registration invitation-only |
| [ADR-0018](0018-administrator-account-management.md) | Accepted | Let administrators manage accounts |
| [ADR-0019](0019-multiplayer-session-rules.md) | Accepted | Define multiplayer lobby, scoring, and round rules |
| [ADR-0020](0020-guest-participants.md) | Accepted | Let guests join multiplayer games with a code |
| [ADR-0021](0021-pause-rounds-and-reveals.md) | Accepted | Let the owner pause a round or its reveal |
