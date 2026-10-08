# ADR-0023: Return to the lobby after a game and change lobby settings

- Status: Accepted
- Date: 2026-10-08
- Deciders: Product owner and engineering
- Related tickets: MP-11
- Supersedes: N/A
- Superseded by: N/A

## Context

Starting a game removed its lobby ([ADR-0019](0019-multiplayer-session-rules.md)).
After the game, "Play again" sent registered players to create a new lobby with a
new code and guests to the join screen, so a group had to regroup by hand for every
game. The lobby also fixed the playlist and rules chosen at creation. On 2026-10-08
the product owner asked for a return to the same lobby and for the host to change
the rules and playlist from the lobby.

A lobby's session ID becomes its game's session ID, and a guest token reaches only
the session it names ([ADR-0020](0020-guest-participants.md)), so the next game
cannot reuse the finished game's ID.

## Decision

- starting a game keeps the lobby as a *started* lobby: it accepts no member
  operations, its code looks closed to joiners, and it remembers the roster,
  settings, audio mode, and code while the game session lives (at most 12 hours);
  a scheduled sweep forgets it once the game session is gone;
- `POST /api/sessions/{sessionId}/rematch` returns a player of that game to its
  lobby once the game is no longer `CREATED` or `PLAYING`. The first caller reopens
  the lobby under a new session ID with the same code, settings, and audio mode,
  alone and as host; later callers join it unready, and the previous host takes the
  host role back on return. Nobody is pulled back automatically: each player opts in
  from the end screen, and anyone else can join the reopened lobby by code;
- a returning guest keeps its participant ID and nickname and gets a new guest token
  for the reopened session, without another Turnstile check: having played the game
  proves admission;
- the host changes the playlist, rules, and audio mode with
  `PUT /api/lobbies/{code}/settings`, validated like creation. A changed playlist or
  rule clears every ready flag, because members agreed to the previous settings.
  `LOBBY_UPDATED` carries the playlist name, track count, and answer time so every
  member sees the change.

## Consequences

- A group plays several games with one code and one invite.
- The return is possible only while the finished game is retained (five minutes
  after it ends by default); after that, players open or join a lobby as before.
- A player removed from the reopened lobby can return through the end screen while
  the finished game is retained, as a registered player can by code; there is no
  ban list.
- Lobby settings reuse the creation screen at `/lobby/{code}/settings`. The host
  leaves the lobby screen while editing, which has no effect until presence
  tracking (MP-07) lands.

## Alternatives Considered

- **Carry every player into the reopened lobby:** fewer clicks, but players who
  closed the tab would block the start until the host removes them.
- **A rematch vote on the end screen:** faster for two players, but harder to get
  right and it cannot change settings between games.
- **Keep the same session ID for the next game:** would break the history and
  summary of the finished game, which are keyed by session ID.

## References

- [Multiplayer session rules](0019-multiplayer-session-rules.md)
- [Guest participants](0020-guest-participants.md)
- [Gameplay rules](../product/gameplay.md#several-participants)
