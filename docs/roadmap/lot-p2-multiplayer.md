# Lot P2 — Multiplayer and Guests

[Up: Post-V1 roadmap](post-v1.md) · [Roadmap](../plan.md)

### Objective

Let up to ten people play the same server-authoritative game from a lobby, first
as registered users, then with guests who join by code.

Decisions: [ADR-0019](../adr/0019-multiplayer-session-rules.md) (rules) and
[ADR-0020](../adr/0020-guest-participants.md) (guests). Scheduled on 2026-10-02
right after CAT-11, ahead of the remaining Lot 8 and Lot 9 tickets; the product
owner accepted the unguarded push-to-deploy risk until TEST-06 and OPS-09.

Dependencies: Lots 4–6 (complete). Every ticket keeps solo behavior unchanged:
existing solo tests keep their assertions, and only signature or fixture updates
are allowed unless the ticket says otherwise.

Blocker for MP-05, MP-06, and MP-09: the mocked Playwright suite has 29
pre-existing failures (views stop updating after mocked HTTP successes; see
[Next Ticket](../plan.md#next-ticket)). It must be green before their browser
acceptance can be signed off; MP-01 to MP-04 and MP-07 to MP-08 are backend-only
and not affected.

Order: MP-01 to MP-06 deliver a playable registered-user game; MP-07 to MP-10
add resilience, guests, and multiplayer hardening; MP-11 lets a group play again
from the same lobby.

### Tickets

#### MP-01 — Generalize the game domain to several participants

Status: completed on 2026-10-02. `GameStateSnapshot` lists its participants,
`RoundScoreState` keeps per-participant awards and skip votes, `GameStateMachine`
exposes `awardTitle`, `voteSkip`, and `reevaluateRound`, and `GameSummary` carries
ranked `ParticipantStanding`s; see `MultiplayerGameDomainTest`. Round `points` in
summaries and history still add up every participant until MP-04.

Work:

- per-participant title award, score, and skip vote in round state, replacing
  the single `RoundScoreState` award;
- round completion when every active participant found the title, the skip vote
  reaches 60% rounded up, or the deadline passes;
- an explicit set of active (connected) participants passed into completion
  checks, with no clock or STOMP dependency;
- per-participant totals and shared final ranks in `GameSummary`;
- solo reduces to one participant: one skip vote reveals at once.

Acceptance:

- parameterized domain tests cover 1, 2, and 10 participants, ties, duplicate
  and late commands, and deadline/command races;
- every existing solo domain and session test passes with unchanged assertions.

#### MP-02 — Add lobbies with join codes

Status: completed on 2026-10-02. Lobbies are a separate in-memory aggregate
(`LobbySnapshot`, `LobbyStore`) promoted to a `CREATED` game session at start, rather
than a new `GameStatus`. Lobby actions are REST rather than STOMP commands so that
failures carry stable HTTP error codes; STOMP only broadcasts. Covered by
`LobbyControllerIntegrationTest`, `LobbySnapshotTest`, and `InMemoryLobbyStoreTest`.

Work:

- `POST /api/lobbies` (registered user, solo configuration limits) returning the
  session ID and 6-character code; tracks are snapshotted at start, not creation;
- `GET /api/lobbies/{code}` for members; `POST /api/lobbies/{code}/participants` to
  join; `DELETE .../participants/me` to leave; `PUT .../participants/me/ready`;
  host-only `DELETE .../participants/{participantId}` and `POST .../start`;
- public `LOBBY_UPDATED` (participants, host, ready flags, `canStart`) and
  `LOBBY_STARTED` events on the session topic, which lobby members may subscribe to;
- 10-participant cap, unique accounts, code invalidation at start or after the
  30-minute lobby inactivity TTL;
- OpenAPI and [WebSocket protocol](../websocket-protocol.md) updates.

Acceptance:

- two registered users join by code, become ready, and the host starts;
- an 11th join, a stale code, a non-host start, or a start with unready players
  fails with a stable error code;
- code lookup failures do not distinguish expired and unknown codes.

#### MP-03 — Play multiplayer rounds over STOMP

Status: completed on 2026-10-02. Every participant may vote to skip; a multiplayer
command is fresh from the version that started its round (`roundStartedVersion`),
and the per-session command budget scales with participant count. Covered by
`MultiplayerGameBackendFlowIntegrationTest`.

Work:

- a shared round `startAt` a short lead time after round start, used for
  scoring and sent in `ROUND_STARTED`;
- `SCORE_UPDATED` per participant, a public "participant found the title" event
  without the answer, and a `SKIP_VOTE_UPDATED` event;
- private answer feedback and secondary discoveries stay on each user queue;
- host-only early advance; reveal and next-round scheduling as in solo;
- per-participant command rate limits and idempotency keys.

Acceptance:

- a backend integration test drives three STOMP clients through a full game with
  mixed finds, a skip vote, and a timeout, and reconciles every score;
- no participant receives another participant's answer text or secondary discovery.

#### MP-04 — Persist multiplayer history and summaries

Status: completed on 2026-10-02. Migration V23 relaxes game and round totals to
combined scores and adds `game_history_round_results`, written only for
multiplayer games. The summary is per caller (`totalScore`, `titlesFound`, round
`points`, `titleFound`) and adds `gameMode`, `participantId`, and `standings`.
Covered by `MultiplayerGameBackendFlowIntegrationTest` and the solo flow test.

Work:

- persist every participant with final score and shared rank, instead of only the
  owner with rank 1;
- per-participant round outcomes in history, with a Flyway migration if the
  current round snapshot cannot hold them;
- the summary endpoint serves every registered participant of the game, not only
  the owner.

Acceptance:

- a completed three-player game has three history participants with correct ranks;
- a registered non-participant gets the same not-found response as for an unknown game;
- solo history rows keep their current shape.

#### MP-05 — Build the create, join, and lobby screens

Status: completed on 2026-10-02. `LobbyResponse` also returns the playlist name,
track count, and timer for the rules card. Covered by the lobby and join unit
specs, `e2e/lobby.spec.ts`, and `e2e-full-stack/multiplayer-lobby-journey.spec.ts`.

Work:

- a solo/multiplayer choice on the create screen reusing the configuration form;
- the `join-session` screen with the six-cell code input;
- the `lobby-room` screen: code with copy action, participant list with host,
  ready and registered/guest markers, ready toggle, host start and kick;
- match `docs/Blindtest/views/lobby.jsx`; the friend-invite panel stays hidden
  until Lot P4.

Acceptance:

- mocked Playwright specs cover create, join, ready, start, kick, and every
  join error state;
- a full-stack Playwright spec shows the lobby updating live in two browser
  contexts.

#### MP-06 — Show multiplayer state in the game room and end screen

Status: completed on 2026-10-02. The `scoreboard` widget carries the live
per-player state; `player-list` stays an unused scaffold because the scoreboard
already shows who found each title. Clients disable answers and skip votes until
the round's `startsAt`. Covered by the game-room multiplayer unit specs and
`e2e-full-stack/multiplayer-lobby-journey.spec.ts`, which plays two browsers
through five rounds to the ranked end screen.

Work:

- wire `player-list` and `scoreboard` to live per-participant events;
- show who found the title, skip-vote progress, and host-only "Next now";
- end screen with the ranked scoreboard, following `game.jsx`.

Acceptance:

- a full-stack Playwright spec plays a two-player game to the end screen;
- the solo game room and end screen look and behave as before.

#### MP-07 — Handle disconnects, rejoin, and host departure

Work:

- track participant presence from STOMP connect and disconnect;
- exclude disconnected participants from completion and skip-vote counts;
- host transfer after 30 seconds of absence or on leave, with an event;
- rejoin restores the current round, score, and private discoveries;
- abort the game when nobody stays connected for the inactivity window.

Acceptance:

- tests cover a player dropping mid-round, the host leaving in the lobby and
  mid-game, a reconnect during reveal, and everyone leaving.

#### MP-08 — Issue guest participant tokens

Status: completed on 2026-10-06, out of sequence at the product owner's request,
together with a host-chosen audio mode ([ADR-0022](../adr/0022-host-only-audio.md)).
The guest token lives six hours instead of the inactivity window; liveness is
checked per request (see ADR-0020 implementation notes). Covered by
`LobbyControllerIntegrationTest`, `SecurityConfigIntegrationTest`,
`SessionSummaryControllerIntegrationTest`, `JwtTokenProviderTest`, and a guest
STOMP game in `MultiplayerGameBackendFlowIntegrationTest`.

Work:

- `POST /api/lobbies/{code}/guests` with nickname and Turnstile token;
- guest JWT audience, scope, and expiry as in ADR-0020, validated by the HTTP
  and STOMP security paths;
- per-IP and per-code rate limits; nickname rules;
- guests persisted only as `GUEST` history participants.

Acceptance:

- a guest token cannot call any account endpoint or reach another session;
- the guest join limit and Turnstile rejection return stable error codes;
- the security verification matrix lists the guest cases.

#### MP-09 — Let guests join from the browser

Status: completed on 2026-10-06 with MP-08. `e2e-full-stack/guest-journey.spec.ts`
plays a registered host (host-only audio) and a guest to the guest end screen,
including a reload rejoin and a refused new tab; `e2e/guest.spec.ts` covers the
mocked join, every join error, and the audio switch.

Work:

- guest nickname step on the join screen when not logged in, with Turnstile;
- guest token in `sessionStorage`, used by the STOMP client;
- a guest end screen with no history link and an invitation-only notice.

Acceptance:

- a full-stack Playwright spec plays a registered host plus a guest to the end;
- reloading a guest tab rejoins the game; a new tab cannot.

#### MP-10 — Harden multiplayer under load

Work:

- extend `load-tests/` with concurrent 10-player sessions;
- failure injection: backend restart and network loss with several players;
- reconcile [status](../product/status.md), [gameplay](../product/gameplay.md),
  [sessions](../backend/sessions.md), and operations runbooks (each earlier
  ticket still documents its own behavior change).

Acceptance:

- the recorded load target completes with reconciled scores and no unexplained errors;
- known multiplayer limitations are listed in current status.

#### MP-11 — Return to the lobby and change lobby settings

Status: completed on 2026-10-08, out of sequence at the product owner's request
([ADR-0023](../adr/0023-return-to-lobby-and-lobby-settings.md)). Covered by
`LobbyControllerIntegrationTest`, `LobbySnapshotTest`, `InMemoryLobbyStoreTest`,
the end-screen, lobby-room, and create-session component specs, and
`e2e/lobby.spec.ts`. When MP-07 lands, host transfer must also apply to the
reopened lobby.

Work:

- keep the started lobby with its code while the game lives;
  `POST /api/sessions/{sessionId}/rematch` reopens it under a new session ID or joins
  the reopened lobby, and reissues guest tokens;
- `PUT /api/lobbies/{code}/settings` for the host; `LOBBY_UPDATED` carries the
  settings;
- "Back to lobby" on the multiplayer end screen; a settings page reusing the
  creation form.

Acceptance:

- players of a finished game return to a lobby with the same code and settings,
  the previous host hosts it, and a guest's new token reaches the reopened session;
- only the host changes settings, changes are validated like creation, broadcast,
  and clear ready flags; the next game uses them.

### Lot exit criteria

- registered users and guests can play a game of up to ten players to a ranked
  end screen;
- disconnects and host departure never block a game;
- solo behavior and tests are unchanged.
