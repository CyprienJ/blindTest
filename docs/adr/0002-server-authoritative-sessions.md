# ADR-0002: Make the server authoritative for game sessions

- Status: Accepted
- Date: 2026-07-27
- Deciders: Product owner and engineering
- Related tickets: FND-06, GAME-01 through GAME-10, SESSION-01 through SESSION-08
- Supersedes: N/A
- Superseded by: N/A

## Context

Answers, timers, scores, skips, and round transitions affect the outcome of a
game. Client clocks drift, browsers pause background work, messages can be retried
or reordered, and browser state can be modified by a player. Treating a browser as
the authority would create inconsistent results and an easy cheating path.

The first release is solo, but the model must extend to multiplayer without
replacing its rules or transport contract.

## Decision

Every game, including a solo game, is a server-side session. The backend owns the
canonical configuration snapshot, participants, current round and game states,
deadlines, discoveries, scores, and version. It validates all commands and applies
state transitions using server time.

Clients render projections of that state. They may animate a local countdown and
schedule playback from server timestamps, but those calculations never decide a
timeout, score, answer result, skip result, or transition. Commands carry an
idempotency identifier; duplicate, stale, unauthorized, and out-of-state commands
are rejected or return their prior result without applying an effect twice.

The server emits state events containing authoritative timestamps and enough
version information for a reconnecting client to replace stale local state.

## Consequences

- All participants observe one result, and browser manipulation cannot award
  points or extend a deadline.
- Deterministic domain tests can cover transitions with injected clocks and IDs.
- The backend must schedule expiry, serialize concurrent commands, retain active
  state for reconnects, and define failure behavior when the process restarts.
- Perceived countdown and playback can briefly differ because of latency, but the
  server result remains final.
- V1 process loss can abort active games; completed histories remain durable under
  ADR-0006.

## Alternatives Considered

- **Client-authoritative solo mode:** rejected because it creates a second rule
  model that would be discarded for multiplayer and is easy to manipulate.
- **Hybrid authority by command type:** rejected because conflicting clocks and
  transition ownership make race conditions difficult to resolve.
- **Persist every active transition in PostgreSQL:** rejected for V1 because it
  adds contention and persistence complexity to ephemeral live state.

## Follow-up

- Inject clocks, schedulers, random sources, and ID generators.
- Define command idempotency, optimistic versioning, reconnect snapshots, expiry,
  and process-restart behavior in the session tickets.
- Add concurrency and deadline-boundary tests before exposing gameplay.

## References

- [Roadmap game-domain principles](../plan.md#43-game-domain-principles)
- [Game state diagram](../diagrams/03-etats.puml)
- [Gameplay sequence diagrams](../diagrams/04-sequences.puml)
- [ADR-0004](0004-in-memory-session-store.md)

