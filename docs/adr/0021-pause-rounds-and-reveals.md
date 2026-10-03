# ADR-0021: Let the owner pause a round or its reveal

- Status: Accepted
- Date: 2026-10-03
- Deciders: Product owner and engineering
- Related tickets: none (requested directly by the product owner)
- Supersedes: N/A
- Superseded by: N/A

## Context

Players need to step away during a game without losing the round. The server owns
round deadlines, title scoring, and the four-second reveal transition
([ADR-0002](0002-server-authoritative-sessions.md),
[ADR-0015](0015-four-second-automatic-reveal-transition.md)), so a browser-only pause
would desynchronize scoring and let a round time out behind a paused screen.

In multiplayer, freezing a playing round would stop every other player's guessing.

## Decision

The session owner (the solo player, or the multiplayer host) may pause and resume:

- **A playing round, in solo games only.** The music and the timer stop. Answers and
  skip votes are rejected while paused. On resume, the server moves `startsAt` and the
  deadline later by the paused time, so the remaining time and the title score curve
  are unchanged, and it issues a fresh media URL. Clients restart playback where it
  stopped from the moved timeline. A round can pause only after it started and before
  its deadline.
- **A reveal, in solo and multiplayer games.** The music keeps playing; the automatic
  transition to the next track waits. On resume, the transition is delayed by the
  paused time, so the remaining reveal time is unchanged. "Next now" still advances a
  paused reveal immediately.

A multiplayer host trying to pause a playing round receives the recoverable error
`ROUND_PAUSE_UNAVAILABLE`; other participants receive `SESSION_OWNER_REQUIRED`.

Pause state belongs to the round snapshot in the pure game domain (`pausedAt` and the
accumulated reveal pause), so scoring, timers, and reconnect snapshots derive from one
source. Paused rounds are still subject to the sliding session inactivity TTL; a
session paused longer than that expires like any idle session.

## Consequences

- A solo player can stop mid-round without losing points.
- Multiplayer players cannot be frozen mid-guess; a host can still hold the reveal,
  for example to discuss the answer.
- The protocol gains `PAUSE_ROUND`/`RESUME_ROUND` commands, `ROUND_PAUSED`/`ROUND_RESUMED`
  events, and `pausedAt` in the round snapshot. These are additive, so the protocol
  version is unchanged.
- Timer callbacks must ignore a paused round and recompute the reveal transition from
  the accumulated pause.
