# ADR-0014: Randomize game track selection and order

- Status: Accepted
- Date: 2026-07-30
- Deciders: Product owner and engineering
- Related tickets: DG-08, GAME-03
- Supersedes: N/A
- Superseded by: N/A

## Context

Administrator playlists have a stable explicit order, but a game may request
fewer tracks than the playlist contains. The game needs a deterministic rule for
which eligible tracks are selected and in what order they are played.

Always taking the first tracks in playlist order makes repeated games predictable.
The game domain already requires an injectable random source so tests can remain
reproducible.

## Decision

For each new game:

- consider only the playlist's eligible published tracks;
- select the requested number uniformly without replacement;
- uniformly shuffle the selected tracks into their play order;
- when every eligible track is requested, shuffle the entire eligible playlist;
- snapshot the resulting track identities, order, answer metadata, playback
  metadata, and media references before the game starts.

The selection never contains a duplicate. Playlist or catalogue changes after
session creation do not alter the snapshot.

Randomness is provided through a game-domain port. Production uses an
unpredictable per-game source or seed; tests inject a known seed or deterministic
sequence. The seed is internal and is not exposed as a player-controlled game
parameter in the first release.

## Consequences

- Repeated games from the same playlist can contain different tracks and orders.
- Administrator playlist order remains meaningful for catalogue display and
  future ordered modes, but does not control V1 gameplay order.
- Failures and history can be explained from the stored snapshot without
  rerunning randomness.
- Statistical and property-oriented tests must cover eligibility, uniform
  selection assumptions, no duplicates, exact requested count, and deterministic
  seeded outcomes.

## Alternatives Considered

- **Strict playlist order:** supports editorial progression but makes repeated
  games and partial selections predictable.
- **Random starting point followed by playlist order:** provides limited variation
  and is harder to explain than a full shuffle.
- **Player-selectable ordering mode:** adds a configurable game mode that is
  explicitly deferred beyond the first release.

## Follow-up

- Implement selection and snapshotting in GAME-03.
- Include selected order in active-session and completed-history snapshots.
- Add deterministic seeded tests and duplicate-prevention properties.

## References

- [GAME-03](../plan.md#game-03--select-and-snapshot-tracks)
- [Decision gates](../plan.md#5-decision-gates)
- [ADR-0002](0002-server-authoritative-sessions.md)
