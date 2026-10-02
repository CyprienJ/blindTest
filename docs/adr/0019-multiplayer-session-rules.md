# ADR-0019: Multiplayer session rules

- Status: Accepted
- Date: 2026-10-02
- Deciders: Product owner and engineering
- Related tickets: MP-01 through MP-10
- Supersedes: N/A
- Superseded by: N/A

## Context

The solo engine is server-authoritative ([ADR-0002](0002-server-authoritative-sessions.md))
and already models a session as a list of participants, but scoring, round
completion, reveal control, and history assume exactly one owner. Multiplayer
needs explicit rules before those single-owner assumptions are generalized.

The product owner decided on 2026-10-02 that multiplayer starts immediately after
CAT-11 and that every player who finds a title scores on the existing time curve.
The remaining rules below follow the reference screens in
`docs/Blindtest/views/lobby.jsx` and `game.jsx`; they are engineering defaults
that the product owner may revise by superseding this ADR.

## Decision

Lobby:

- a registered user creates a lobby with the solo configuration limits
  ([ADR-0008](0008-solo-game-configuration-limits.md)) and becomes its host;
- the lobby has a 6-character join code from an unambiguous alphabet
  (no `0/O/1/I`), unique among live sessions, and invalid once the game starts
  or the lobby expires; an unstarted lobby expires on the existing 30-minute
  sliding session inactivity TTL ([session internals](../backend/sessions.md));
- a lobby holds at most 10 participants, host included;
- each non-host participant toggles a ready flag; the host can start when at
  least two participants are present and all of them are ready;
- nobody can join after the game starts; a participant already in the game may
  reconnect.

Scoring and round completion:

- every participant who submits a correct title earns title points from the
  shared round start with the formula of
  [ADR-0007](0007-title-score-rounding-and-minimum.md); finding it first gives no bonus;
- secondary discoveries stay private to their participant;
- a participant who found the title cannot submit more title answers in that
  round but still sees the round continue;
- a round is revealed when every connected participant has found the title, when
  the skip vote passes, or when the timer expires, whichever comes first;
- skip is a vote: it passes when at least 60% of connected participants, rounded
  up, have voted; a solo game keeps its immediate skip because one vote is 100%;
- final ranks use total score, with equal scores sharing a rank.

Timing and control:

- every round carries a server `startAt` timestamp slightly in the future so
  clients begin playback together; scoring time is measured from `startAt`;
- the four-second reveal of [ADR-0015](0015-four-second-automatic-reveal-transition.md)
  applies, and only the host may advance early;
- a participant who disconnects keeps their place and score and is ignored by the
  "everyone found" and skip-vote counts until they reconnect;
- if the host leaves or stays disconnected for 30 seconds, the host role moves to
  the connected participant who joined earliest, preferring registered users;
- the game is aborted when no participant remains connected for the session's
  inactivity window.

## Consequences

- Slower players still score, which suits a casual group game.
- Round state becomes per participant for titles, scores, and skip votes; the
  domain and STOMP events must change, and solo behavior must stay unchanged.
- History persists every participant with a real final rank.
- The "first to find" mode and a configurable skip threshold remain in Lot P3.

## Alternatives Considered

- **First finder only:** very competitive but leaves slower players with no points.
- **Everyone plus a first-finder bonus:** adds a rule that can come later as a P3 mode.
- **Host-only skip:** simpler, but the reference design shows a skip vote.
- **Late joining:** complicates fairness and round-count accounting.

## Follow-up

- Implement through [Lot P2](../roadmap/lot-p2-multiplayer.md).
- Revisit the 60% threshold and the 30-second host grace period after real games.

## References

- [Guest participants](0020-guest-participants.md)
- [Gameplay rules](../product/gameplay.md)
- [Lot P2](../roadmap/lot-p2-multiplayer.md)
