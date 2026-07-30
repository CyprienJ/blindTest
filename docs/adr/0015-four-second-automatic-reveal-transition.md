# ADR-0015: Advance automatically after a four-second reveal

- Status: Accepted
- Date: 2026-07-30
- Deciders: Product owner and engineering
- Related tickets: DG-09, GAME-07, SESSION-05, UI-07
- Supersedes: N/A
- Superseded by: N/A

## Context

After a correct title, timeout, or skip, the player needs enough time to see the
correct answer, available metadata, outcome, and awarded points. The session must
also progress without remaining blocked when the player does nothing.

The generated game reference displays a four-second countdown and a control that
allows the host to advance early. Because the server is authoritative, browser
timers cannot own the transition.

## Decision

Every revealed round has a four-second server-authoritative reveal period:

- the server records `revealedAt` and an `advanceAt` deadline four seconds later;
- at `advanceAt`, the server automatically completes the revealed round and
  starts the next round;
- the authenticated solo-session owner may send an idempotent `advance now`
  command at any point during the reveal;
- the command completes the transition immediately when it is valid;
- an automatic timer, manual command, duplicate command, and reconnect race can
  produce exactly one transition;
- a reconnecting client receives the authoritative reveal snapshot and remaining
  deadline rather than restarting four seconds locally;
- after the final round, the same automatic or manual transition finishes the
  game and exposes the persisted summary.

The frontend displays the remaining reveal time and an immediately available
“Next now” action. It may animate locally from server timestamps but cannot extend
or decide the reveal state.

Future multiplayer authorization may map the early-advance permission to a host
role, but that behavior is outside the first-release solo scope.

## Consequences

- Unattended solo games continue without manual input.
- Players can shorten the pause when they have read the result.
- Four seconds aligns with the approved reference interface.
- Server scheduling, idempotency, persistence ordering, and state versioning must
  handle deadline and command races.
- The reveal may be brief for dense metadata, but the final history summary
  remains available after game completion.

## Alternatives Considered

- **Automatic transition without an early action:** simpler but forces every
  player to wait for every reveal.
- **Manual progression only:** gives unlimited reading time but can leave sessions
  blocked indefinitely.
- **Client-owned reveal timer:** rejected because reconnects, background tabs, and
  modified clients would produce inconsistent state.

## Follow-up

- Implement authoritative scheduling and race tests in SESSION-05.
- Implement the countdown, early action, reconnect projection, and final-round
  behavior in UI-07.
- Cover correct-title, timeout, skip, duplicate command, deadline collision,
  reconnect, and final-round transitions.

## References

- [Decision gates](../plan.md#5-decision-gates)
- [SESSION-05](../plan.md#session-05--orchestrate-server-timers)
- [UI-07](../plan.md#ui-07--implement-reveal-and-round-transition)
- [ADR-0002](0002-server-authoritative-sessions.md)
