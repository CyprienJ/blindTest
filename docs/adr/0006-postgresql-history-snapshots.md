# ADR-0006: Persist completed-game history snapshots in PostgreSQL

- Status: Accepted
- Date: 2026-07-27
- Deciders: Product owner and engineering
- Related tickets: FND-06, HIST-01 through HIST-04
- Supersedes: N/A
- Superseded by: N/A

## Context

Completed games must be durable in the first release even though history browsing
is deferred. Catalogue metadata, playlists, aliases, and users can change or be
deleted after a game. Rebuilding an old result by joining current tables would
therefore change what the player originally saw and make audit of scoring outcomes
unreliable.

Active session state is high-frequency and ephemeral; completed history is
append-oriented durable product data with relational access and retention needs.

## Decision

When a game reaches `FINISHED`, the history application service writes one
immutable completed-game snapshot to PostgreSQL in a single transaction. The
snapshot has a stable game identifier and records configuration and rule versions,
start/completion timestamps, participant identity snapshot, final scores, and
ordered per-round outcomes. Each round snapshots the display metadata and answer
outcome needed to explain the result, timing/score facts, and a stable track
reference when available.

History does not store pre-signed URLs, object credentials, raw submitted guesses,
password/authentication data, or active-session implementation state. Persistence
is idempotent by game identifier so retries cannot create duplicate histories.
Catalogue edits never rewrite prior snapshots. Erasure and retention processes may
remove or anonymize personal identifiers while preserving non-personal game facts
according to the approved policy.

## Consequences

- Historical results remain intelligible after catalogue and rule changes.
- PostgreSQL transactions, constraints, Flyway migrations, backups, and retention
  apply to durable game data.
- Snapshot fields duplicate selected catalogue and user display data and require a
  versioned schema.
- Storage grows with every completed round and must be monitored and retained under
  an explicit policy.
- Failure to persist completion must be visible and retryable before the active
  session is discarded; idempotency makes that retry safe.

Backward-compatible migrations must precede application writes. Rollback readers
must tolerate newly added nullable/versioned fields during mixed-version deploys.

## Alternatives Considered

- **Store only foreign keys and query current catalogue/user rows:** rejected
  because edits and deletion would corrupt the historical meaning.
- **Persist the full active-session object:** rejected because it leaks an unstable
  implementation format and unnecessary/private transient data.
- **Use object storage or document storage for history:** rejected because the
  expected relational queries and current operating environment favor PostgreSQL.
- **Defer persistence until the history UI:** rejected because games played before
  that release would be permanently unavailable.

## Follow-up

- Finalize the snapshot schema, retention/anonymization policy, and migrations in
  HIST-01.
- Add idempotency, transaction-failure, catalogue-edit, and account-erasure tests.
- Add history backup/restore coverage and storage-growth metrics.

## References

- [Roadmap persistence split](../roadmap/target-architecture.md#44-active-and-durable-state)
- [Data model diagram](../diagrams/02-modele-donnees.puml)
- [Game state diagram](../diagrams/03-etats.puml)
- [ADR-0004](0004-in-memory-session-store.md)

