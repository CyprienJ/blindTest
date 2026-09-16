# Solo gameplay rules

[Up: Product](README.md) · [Implementation: sessions](../backend/sessions.md)

## Configuration and track selection

A verified authenticated player selects a public playlist and configures:

| Setting | Allowed values | Default |
|---|---|---|
| Track count | 5–30 | 15 |
| Maximum round duration | 10–60 seconds, in five-second increments | 30 seconds |

The backend validates eligibility and sufficient playable tracks. It samples
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

- A correct title ends the round and awards points once.
- Artist, composer, and album matches are private discoveries. They award no
  points, do not end the round, and are acknowledged only once per field/player.
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

## Audio excerpts

Administrators configure a non-negative whole-second start offset. Publishing a
nonzero offset requires at least 60 seconds of track duration after that offset.
A round plays from the snapshot offset for its configured duration. Signed URLs
cover the remaining round plus a 30-second reconnect buffer.

## Source and decisions

- [Game domain](../../backend/src/main/java/com/blindtest/model/game): configuration,
  normalization, matching, scoring, state machines, and discovery ledgers.
- [Scoring ADR](../adr/0007-title-score-rounding-and-minimum.md)
- [Configuration ADR](../adr/0008-solo-game-configuration-limits.md)
- [Track selection ADR](../adr/0014-randomize-game-track-selection-and-order.md)
- [Reveal transition ADR](../adr/0015-four-second-automatic-reveal-transition.md)
- [Gameplay client](../frontend/gameplay.md): how the browser consumes these rules.
