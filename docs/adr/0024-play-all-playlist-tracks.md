# ADR-0024: Let players choose long games or every playlist track

- Status: Accepted
- Date: 2026-10-08
- Deciders: Product owner and engineering
- Related tickets: N/A
- Supersedes: The 30-track maximum of [ADR-0008](0008-solo-game-configuration-limits.md)
- Superseded by: N/A

## Context

[ADR-0008](0008-solo-game-configuration-limits.md) capped a game at 30 tracks, so
a larger playlist could never be played through. On 2026-10-08 the product owner
asked to pick any number of tracks, or to play every track of the playlist.

A lobby keeps its requested configuration and selects tracks again at start
([ADR-0023](0023-return-to-lobby-and-lobby-settings.md)), so the playlist can
change between the moment the settings are saved and the start.

## Decision

- the track count ranges from 5 through 500 inclusive; the other ADR-0008 limits
  and defaults are unchanged. 500 bounds in-memory game state and history rows
  while leaving room for any realistic playlist;
- a configuration may set `allTracks`. The server then ignores `trackCount` and
  plays every playable track matching the difficulty selection, resolved when the
  game is created: a lobby start includes tracks published while it waited and
  leaves out tracks no longer playable;
- "all tracks" still needs at least 5 matching tracks and fails with
  `GAME_TRACK_COUNT_INVALID` above 500;
- a lobby response and `LOBBY_UPDATED` carry `allTracks` and, as `trackCount`, the
  matching count when the settings were last saved. `LOBBY_STARTED.totalRounds`
  gives the started game's size.

## Consequences

- A whole playlist can be played in one game, with every track in random order
  ([ADR-0014](0014-randomize-game-track-selection-and-order.md)).
- Very long games are possible (500 one-minute rounds is over eight hours).
  Sessions expire after inactivity, not after a total length, so they can finish.
- Migration V25 widens the history track-count constraint to 500.

## Alternatives Considered

- **Raise the cap only:** the client could send the playlist size, but a lobby
  would then fail to start, or miss new tracks, when the playlist changed.
- **No upper bound:** simplest, but leaves game state and history size unbounded.

## References

- [ADR-0008](0008-solo-game-configuration-limits.md)
- [Gameplay rules](../product/gameplay.md#configuration-and-track-selection)
