# ADR-0016: Let administrators set a looped per-track excerpt window

- Status: Accepted
- Date: 2026-10-01
- Deciders: Product owner and engineering
- Related tickets: N/A (product request)
- Supersedes: [ADR-0009](0009-admin-configured-playback-offset.md) (excerpt length rule only)
- Superseded by: N/A

## Context

[ADR-0009](0009-admin-configured-playback-offset.md) stored only a playback offset
and required 60 playable seconds after it, so every round duration could play
without repeating. Administrators asked for windows shorter than a minute: the
recognizable passage of a track is often shorter, and short tracks could not use
a non-zero offset.

## Decision

Each track stores `excerptDurationSeconds`, a whole-second window length from 5
to 60 seconds that defaults to 60:

- a non-zero `excerptStartSeconds` requires the whole window to fit in the known
  media duration; offset zero still needs no known duration;
- a round's playback window is the track window capped to the round duration;
- `ROUND_STARTED` and `CURRENT_SNAPSHOT` carry `excerptDurationSeconds`, and the
  client loops playback from the offset when the window ends before the round
  (including during the reveal, until the next round replaces it);
- when the media ends inside the window, playback restarts at the offset;
- game snapshots and completed history record the window with the offset.

The round duration remains the only scoring timer.

## Consequences

- Administrators can isolate a short passage; players hear it repeated.
- Existing tracks keep their 60-second behavior through the column default.
- The browser loops locally; the server stays authoritative for the window.
- The import review and track editor share one excerpt picker with a looping
  preview.

## Alternatives Considered

- **Silence after the window:** simpler for clients, but rounds longer than the
  window would end without music.
- **Lower the maximum round duration to 30 seconds:** removes player options.
- **Relax the remaining-audio rule only:** would exclude tracks from games whose
  answer time exceeds the remaining audio.
