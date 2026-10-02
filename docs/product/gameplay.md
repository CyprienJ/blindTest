# Solo gameplay rules

[Up: Product](README.md) · [Implementation: sessions](../backend/sessions.md)

## Configuration and track selection

A verified authenticated player selects a public playlist and configures:

| Setting | Allowed values | Default |
|---|---|---|
| Track count | 5–30 | 15 |
| Maximum round duration | 10–60 seconds, in five-second increments | 30 seconds |
| Difficulty levels | Any subset of 1–5 stars, plus or minus unrated tracks; at least one | All levels and unrated |

Difficulty is the administrator rating set during import (1 = easiest, 5 =
hardest). The setup screen shows how many tracks of the selected playlist sit at
each level, caps the track count to the matching tracks, and blocks creation
when fewer than five match. The backend validates eligibility and sufficient
playable tracks within the selected difficulty levels. It samples
published private-media tracks without replacement and shuffles their order.
The game retains immutable metadata, alternative-answer, and media-version
snapshots. Playlist editing during a game cannot change these snapshots.

## What counts as an answer

The primary answer is the canonical title: the film title for a film-related
track, otherwise the music title. Administrators can add alternative title,
artist, composer, and album answers.

Normalization uses Unicode compatibility decomposition, case normalization,
accent removal, punctuation/symbol separation, and whitespace collapse. It can
remove one common leading French or English article, but never empties a
one-token answer.

Matching checks exact, token-boundary, then length-sensitive unrestricted
Damerau–Levenshtein matches against canonical answers and aliases. Edit-distance
allowances are zero through five code points, one through 12, two through 20,
and three beyond 20. Read the matcher for tier ordering and token-boundary details.

## Round outcomes

- A correct title ends the solo round and awards points once.
- Artist, composer, and album matches are private discoveries. They award no
  points, do not end the round, and are acknowledged only once per field, player,
  and round; every new track starts with nothing found.
- Wrong guesses do not deduct points.
- Skip and timeout reveal the answer and award zero title points.
- The server automatically advances after a four-second reveal interval.

The server enforces a 300 ms per-player answer cooldown and a 20-command
per-session one-second window. Client button guards are only a usability aid.

## Scoring

Rule version: `title-linear-v1`. Before the deadline:

```text
points = max(100, round-half-up(1000 × remaining time / configured duration))
```

Remaining time is clamped to the configured duration. Timing is authoritative
server time, and calculation uses exact integer arithmetic. At or after the
deadline a title submission earns zero. A repeated title award cannot add points.
For example, a title accepted halfway through a 30-second round earns 500 points.

## Several participants

The backend applies the multiplayer rules of
[ADR-0019](../adr/0019-multiplayer-session-rules.md) to games started from a lobby;
the browser screens are still in progress ([Lot P2](../roadmap/lot-p2-multiplayer.md)). Every
participant who finds the title scores on the curve above, at most once per round. A
round is revealed when every connected participant found the title, when skip votes
reach 60% of connected participants rounded up, or at the deadline. Final standings
rank by total score, and equal scores share a rank (1, 1, 3). A solo game is the
one-participant case, so its single skip vote reveals the round at once.
Multiplayer rounds begin 1.5 seconds after the server starts them so every
player's audio starts together, and only the host can advance a reveal early.

## Audio excerpts

Administrators configure a non-negative whole-second start offset and a window
length from 5 to 60 seconds (default 60). Publishing a nonzero offset requires the
whole window to fit in the track duration. A round plays the snapshot window from
its offset and loops it until the round ends when the window is shorter than the
answer time ([ADR-0016](../adr/0016-per-track-looped-excerpt-window.md)). After the
reveal the track keeps playing from where it is rather than restarting the window. Signed
URLs cover the remaining round plus a 30-second reconnect buffer.

## Source and decisions

- [Game domain](../../backend/src/main/java/com/blindtest/model/game): configuration,
  normalization, matching, scoring, state machines, and discovery ledgers.
- [Scoring ADR](../adr/0007-title-score-rounding-and-minimum.md)
- [Configuration ADR](../adr/0008-solo-game-configuration-limits.md)
- [Track selection ADR](../adr/0014-randomize-game-track-selection-and-order.md)
- [Reveal transition ADR](../adr/0015-four-second-automatic-reveal-transition.md)
- [Gameplay client](../frontend/gameplay.md): how the browser consumes these rules.
