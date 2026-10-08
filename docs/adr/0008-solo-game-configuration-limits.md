# ADR-0008: Use the reference solo-game configuration limits

- Status: Partially superseded
- Date: 2026-07-30
- Deciders: Product owner and engineering
- Related tickets: DG-02, GAME-01, GAME-02, UI-02
- Supersedes: N/A
- Superseded by: [ADR-0024](0024-play-all-playlist-tracks.md) for the 30-track maximum

## Context

The first-release solo game lets an authenticated player select a public playlist,
a number of tracks, and a maximum answer time per track. The backend must enforce
bounded values independently of the browser, while the frontend must present the
same allowed values and defaults.

The generated reference interface already proposes track and timer controls. A
product decision was required before those values could become authoritative.

## Decision

Use the configuration represented by the reference interface:

- track count ranges from 5 through 30 inclusive, in increments of 1;
- round duration ranges from 10 through 60 seconds inclusive, in increments of
  5 seconds;
- the default configuration is 15 tracks and 30 seconds per track;
- the selected playlist must contain at least the requested number of eligible
  published tracks when the game is created.

The backend is authoritative for all validation. The frontend exposes only valid
increments and shares the same defaults, but client-side constraints do not
replace server validation.

Configuration values are snapshotted when a game is created. Later catalogue or
playlist edits do not alter an active game's requested count or round duration.

## Consequences

- The delivered controls remain aligned with the approved visual reference.
- Players can tune game length more precisely than with a preset-only selector.
- A maximum configuration contains 30 one-minute answer windows, before reveal
  and transition time.
- APIs must reject out-of-range values, timer values that are not multiples of
  five seconds, and requests exceeding the playlist's eligible track count.
- Backend and frontend boundary tests must cover both endpoints and invalid
  increments.

## Alternatives Considered

- **Preset values only:** simpler to display but unnecessarily removes the
  track-by-track flexibility already represented in the reference interface.
- **Smaller V1 limits:** reduces maximum game duration but diverges from the
  reference without evidence of a capacity need.
- **Frontend-only limits:** rejected because clients can be modified and the
  server owns game rules.

## Follow-up

- Implement shared contract constants or equivalent synchronized validation in
  GAME-02 and UI-02.
- Return stable validation errors for invalid counts, durations, increments, and
  insufficient eligible tracks.
- Include minimum, maximum, increment, and default boundary cases in backend and
  frontend tests.

## References

- [Roadmap solo game rules](../roadmap/product-decisions.md#solo-game-rules)
- [Decision gates](../roadmap/decision-gates.md#5-decision-gates)
- [Reference lobby view](../Blindtest/views/lobby.jsx)
- [ADR-0002](0002-server-authoritative-sessions.md)
