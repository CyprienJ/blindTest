# Data and persistence

[Up: Architecture](../architecture.md)

PostgreSQL owns durable relational data. MinIO owns audio objects. Active games
are immutable in-memory snapshots; completed games become durable history.

## Durable record groups

| Group | Purpose |
|---|---|
| Users and refresh tokens | Account identity, verification/roles, rotating login sessions |
| Tracks and answer aliases | Canonical metadata, alternative answers, publication state, excerpt offsets, waveform peaks, private-media references |
| Playlists and ordered membership | Public catalogue collections of published tracks |
| Import jobs | Durable progress, lifecycle timestamps, safe failure and recovery state |
| Media deletion outbox | Transactional handoff to retryable external object deletion |
| Administrator provisioning audit | Safe, persistent record of first-administrator command outcomes |
| Game history, participants and rounds | Immutable completion snapshots independent of catalogue edits |

The [JPA entities](../../backend/src/main/java/com/blindtest/model/entity) and
[Flyway migrations](../../backend/src/main/resources/db/migration) define the exact
schema. Migrations currently run from V1 through V16. Hibernate validates schema
at startup; production schema creation is owned by Flyway.

## Integrity rules

Playlist membership is unique and zero-based. Reordering is atomic; removing,
rejecting, or deleting a member track compacts positions. Publication validates
primary metadata and playable media. Active import deduplication prevents
concurrent jobs for equivalent YouTube video identifiers.

A track can retain a nullable legacy audio URL or private object-storage metadata.
Private references include the object key, checksum, and size. DTO projections
keep internal keys and normalized alias duplicate keys out of API responses.

Media deletion cannot be made atomic with a PostgreSQL transaction. The outbox
records deletion intent alongside the database change; a worker retries MinIO
calls independently. See [media operations](../media-operations.md).

## Active state versus history

`ActiveSessionSnapshot` retains configuration, participants, game/discovery state,
version, and lifecycle timestamps. The replaceable `SessionStore` port uses a
concurrent process-local adapter. It is not a JPA repository or a restart journal.

At completion, the history services persist one aggregate keyed by session UUID.
Round metadata and outcomes are snapshots, so deleting or editing a catalogue
track does not rewrite previous game results. Duplicate completion is idempotent;
transient failures are retried before active state is released.

The owner summary endpoint returns ordered round and score data without raw
guesses, aliases, media object identifiers, or provider URLs. General history
browsing is deferred. See [session internals](../backend/sessions.md).

## Schema changes and verification

Add a new Flyway migration for every schema change; never rewrite an applied one.
PostgreSQL Testcontainers tests migrate an empty database and validate JPA mappings,
constraints, cascades, and repository queries. H2 tests cover schema-independent
behavior and cannot replace that verification. See [testing](../testing.md).
