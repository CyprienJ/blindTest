# ADR-0022: Let the host play the music for a room

- Status: Accepted
- Date: 2026-10-06
- Deciders: Product owner and engineering
- Related tickets: MP-08, MP-09
- Supersedes: N/A
- Superseded by: N/A

## Context

Multiplayer rounds start every player's browser on the same signed excerpt
([ADR-0019](0019-multiplayer-session-rules.md)). When the players sit in the same
room, ten devices playing slightly out of sync drown each other out. On 2026-10-06
the product owner asked that the host choose whether every player hears the music
or only the host's device does.

## Decision

- a lobby carries an `audioMode`: `EVERYONE` (default) or `HOST_ONLY`;
- the host picks it when opening the lobby and may change it until the game starts
  (`PUT /api/lobbies/{code}/audio-mode`); members see it in `LOBBY_UPDATED`;
- the mode is fixed once the game starts; the browser keeps it with the active
  session, and in `HOST_ONLY` every device except the host's skips playback and
  shows that the music plays on the host's device;
- the server still sends the same signed playback URL to every participant. The
  mode is a playback choice for people in one place, not an access control: the URL
  reveals no answer, and those players already hear the music.

## Consequences

- One loudspeaker can serve a room while everyone answers on their own phone.
- Timing and scoring are unchanged: every player still answers from the shared
  `startsAt`.
- If host transfer during a game (MP-07) lands, the audio follows the game's host
  only once the browser knows about the transfer; until then the original host
  keeps playing it.

## Alternatives Considered

- **Withhold the URL from non-hosts:** stronger, but adds a private media event and
  gains nothing for players who already hear the music.
- **Per-player mute:** each player could mute locally, but a group would have to
  coordinate it on every device.

## References

- [Guest participants](0020-guest-participants.md)
- [Gameplay client](../frontend/gameplay.md)
