# ADR-0009: Let administrators configure a track playback offset

- Status: Accepted
- Date: 2026-07-30
- Deciders: Product owner and engineering
- Related tickets: DG-03, CAT-02, CAT-05, CAT-06, GAME-08, SESSION-06, UI-04
- Supersedes: N/A
- Superseded by: N/A

## Context

Starting every blindtest round at the beginning of its source audio is simple but
can produce silence, spoken introductions, long instrumental openings, or an
unrepresentative section. Administrators already preview imported audio before
publication and are best placed to select a useful starting point.

The game also has a player-selected round duration from 10 through 60 seconds.
Persisting a second administrator-selected excerpt duration would create
conflicting duration rules and additional eligibility states.

## Decision

Each track has an optional administrator-configured playback offset named
`excerptStartSeconds`:

- it is a non-negative whole number of seconds;
- its default value is zero, meaning playback starts at the beginning;
- administrators set or clear it while previewing the imported audio;
- no separate excerpt-duration field is stored;
- playback continues from the offset for the round duration selected when the
  game is created;
- at least 60 seconds of playable audio must remain after a non-zero configured
  offset so the track supports every approved round duration.

Publication rejects a non-zero offset when the finalized media duration is
unknown or insufficient. The game snapshots the validated offset and sends it to
authorized clients with the round-start event. Catalogue edits after session
creation cannot change an active game's offset.

The backend remains authoritative for the offset. The browser may seek to the
provided position but cannot request an arbitrary alternative playback window.

## Consequences

- Administrators can select a recognizable and technically clean passage.
- Existing and newly imported tracks naturally start at zero until configured.
- Only one duration control exists: the game's round duration.
- Finalized media duration must be available when validating a non-zero offset.
- Preview, publication, game snapshots, playback events, reconnects, and history
  must represent the offset consistently.
- A track with less than 60 seconds remaining after its chosen offset cannot be
  published with that offset.

## Alternatives Considered

- **Always start at zero:** simplest, but provides no remedy for unsuitable
  introductions.
- **Store both offset and excerpt duration:** offers more editorial control but
  conflicts with the game-level duration and multiplies validation cases.
- **Choose an offset automatically for every game:** makes playback
  nondeterministic and removes administrator editorial control.

## Follow-up

- Add the offset field and validation in CAT-02.
- Add offset selection to administrator preview interfaces in CAT-05 and CAT-06.
- Snapshot and apply the offset in GAME-08, SESSION-06, and UI-04.
- Add boundary tests for zero, negative, exact-duration, insufficient-duration,
  and post-snapshot catalogue changes.

## References

- [Roadmap decision gates](../plan.md#5-decision-gates)
- [Catalogue tickets](../plan.md#10-lot-3--administrator-catalogue-and-playlists)
- [ADR-0005](0005-private-s3-media-and-presigned-playback.md)
- [ADR-0008](0008-solo-game-configuration-limits.md)
