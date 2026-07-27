# ADR-0004: Keep active sessions in memory behind a port

- Status: Accepted
- Date: 2026-07-27
- Deciders: Product owner and engineering
- Related tickets: FND-06, SESSION-01, SESSION-07, OPS-04
- Supersedes: N/A
- Superseded by: N/A

## Context

Active game sessions are short-lived, frequently updated state. The first release
runs one backend instance on one VPS and does not require active games to survive a
process restart. Direct use of a static map would be simple but would couple
orchestration, concurrency, expiry, and tests to one implementation.

A later multi-instance deployment needs shared state or routing guarantees without
changing game rules or command handlers.

## Decision

Session orchestration depends on a `SessionStore` port. The port exposes atomic
create, read, compare-and-update, remove, and expiry operations using immutable
session snapshots and versions.

V1 uses an `InMemorySessionStore` with per-session concurrency control. It rejects
lost updates, expires abandoned sessions, and exposes safe metrics without player
answers or personal data. The store is process-local and is not a cache for a
second canonical copy: while active, it is the canonical session state described
by ADR-0002.

Completed games are written once to PostgreSQL before active state is removed. A
backend restart may abort unfinished games; the UI must report that the session no
longer exists rather than reconstructing it from partial history.

## Consequences

- The first implementation has low latency and no additional infrastructure.
- Tests can substitute a deterministic store and exercise version conflicts.
- Active games are lost during crashes, restarts, and deployments.
- Multiple backend instances cannot safely serve the same sessions with this
  adapter.
- A Redis implementation can replace the adapter if it preserves atomic updates,
  versioning, expiry, and serialization semantics.

Deployment rollback does not recover active sessions; deployments should drain or
announce interruption until a durable/shared adapter exists.

## Alternatives Considered

- **Use a `ConcurrentHashMap` directly from services:** rejected because it leaks
  storage and locking decisions into orchestration.
- **Store live state in PostgreSQL:** rejected for V1 because high-frequency state
  updates and timers do not need relational durability.
- **Use Redis now:** rejected because a single-instance release does not justify
  another production dependency.
- **Sticky sessions across multiple instances:** deferred; they do not preserve
  state after instance loss and complicate deployments.

## Follow-up

- Define port semantics and concurrency tests in SESSION-01.
- Add expiry cleanup, capacity limits, metrics, and graceful shutdown behavior.
- Document session loss in the deployment runbook.
- Consider Redis when horizontal scaling or active-session continuity becomes a
  requirement; migrate serialization with versioned formats.

## References

- [Roadmap scaling path](../plan.md#46-scaling-path)
- [Component diagram](../diagrams/01-composants.puml)
- [ADR-0002](0002-server-authoritative-sessions.md)
- [ADR-0006](0006-postgresql-history-snapshots.md)

