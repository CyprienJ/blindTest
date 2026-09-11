# 11. Lot 4 — Pure Solo-Game Domain

[Up: Roadmap](../plan.md)

### Objective

Implement deterministic game rules independently from transport and persistence.

Dependencies: CAT-01, CAT-02, CAT-07.

### Tickets

#### GAME-01 — Define game configuration and validation

Status: completed on 2026-08-04.

Work:

- playlist ID, requested track count, maximum round duration;
- immutable configuration snapshot;
- validate playlist eligibility and sufficient published tracks;
- reserve extension fields for future game mode without accepting unsupported
  values.

Acceptance:

- invalid configurations return stable domain errors;
- valid configuration is independent from subsequent playlist edits.

#### GAME-02 — Approve and enforce configuration limits

Status: completed on 2026-08-04.

Decision: [ADR-0008](../adr/0008-solo-game-configuration-limits.md).

Work:

- encode approved defaults/minimums/maximums;
- share constraints with frontend DTOs;
- reject abuse-sized games.

Acceptance:

- backend is authoritative for all limits;
- boundary tests cover exact limits.

#### GAME-03 — Select and snapshot tracks

Status: completed on 2026-08-04.

Decision: [ADR-0014](../adr/0014-randomize-game-track-selection-and-order.md).

Work:

- select the requested number without duplicates;
- use injectable randomness;
- apply the approved playlist-order or random-selection rule;
- snapshot primary/secondary answers and media key/version.

Acceptance:

- seeded tests are deterministic;
- active game answers do not change after catalogue edits.

#### GAME-04 — Implement answer normalization

Status: completed on 2026-08-04.

Work:

- Unicode normalization;
- case and accent folding;
- punctuation and extra whitespace removal;
- configurable removal of common articles;
- token normalization;
- no locale-dependent surprises.

Acceptance:

- a reviewed corpus covers French accents, punctuation, articles, apostrophes,
  digits, and short answers.

#### GAME-05 — Implement tolerant matching and aliases

Status: completed on 2026-08-04.

Work:

- exact normalized matching first;
- manually configured aliases;
- word/token comparison;
- proportional Damerau-Levenshtein tolerance;
- stricter thresholds for short answers;
- return the matched answer type without leaking canonical answers on failure.

Acceptance:

- false-positive and false-negative corpus is product-reviewed;
- the engine identifies title, artist, composer, and album matches;
- matching is deterministic and benchmarked.

#### GAME-06 — Implement scoring

Status: completed on 2026-08-04.

Decision: [ADR-0007](../adr/0007-title-score-rounding-and-minimum.md).

Work:

- score only a correct title;
- base score on server-measured remaining time;
- secondary matches, timeout, and skip score zero;
- implement approved rounding/minimum;
- prevent duplicate score awards.

Acceptance:

- boundary tests cover start, midpoint, deadline, after deadline, duplicates, skip,
  and secondary answers.

#### GAME-07 — Implement round and game state machines

Status: completed on 2026-08-04.

States:

```text
Game: CREATED -> PLAYING -> FINISHED | ABORTED
Round: PREPARING -> PLAYING -> REVEALED -> COMPLETED
```

Work:

- explicit allowed transitions;
- title, timeout, and skip outcomes;
- next-round progression;
- final summary;
- no transport or scheduler code in transition rules.

Acceptance:

- illegal transitions fail safely;
- the last round produces exactly one game completion.

#### GAME-08 — Define playback window

Status: completed on 2026-08-04.

Decision: [ADR-0009](../adr/0009-admin-configured-playback-offset.md).

Work:

- apply approved start/excerpt behavior;
- ensure the pre-signed URL lasts beyond the round and reconnect buffer;
- store playback metadata in the game snapshot.

Acceptance:

- every round has deterministic media timing metadata.

#### GAME-09 — Implement private secondary discoveries

Status: completed on 2026-08-04.

Work:

- track discovered secondary fields per participant;
- return `ALREADY_FOUND` without leaking additional data;
- keep the title guess available;
- do not modify score or round state.

Acceptance:

- one participant's discovery cannot be observed by another participant in domain
  projections prepared for future multiplayer.

#### GAME-10 — Add exhaustive domain tests

Status: completed on 2026-08-04.

Work:

- unit, parameterized, property-oriented, and state-transition tests;
- matching corpus;
- injected clock/random;
- performance guard for answer matching.

Acceptance:

- no game-domain test requires Spring, PostgreSQL, WebSocket, or MinIO.

### Lot exit criteria

- a deterministic solo game can run entirely through domain commands;
- title/secondary behavior and score match approved rules;
- future participants/modes do not require rewriting the core.
