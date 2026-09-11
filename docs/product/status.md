# Current implementation and limitations

[Up: Product](README.md) · [Roadmap pointer](../plan.md)

Reviewed against source and test files on 2026-09-11. This is a code inventory,
not a claim that all release acceptance checks have passed. No application tests
were run as part of the documentation reorganization.

## Present in code

| Area | Backend | Browser |
|---|---|---|
| Accounts | Registration, verification, login, refresh, logout, password recovery | Corresponding forms, Turnstile, session recovery, guards |
| Home and navigation | Account role information | Public home, authenticated home, shared application shell |
| Catalogue | Public tracks/playlists; administrator track, alias, and playlist management | Track review, import workflow, playlist editor |
| Imports and media | Bounded YouTube imports, waveform generation, private MinIO, signed URLs, deletion outbox | Import progress/polling, waveform excerpt selection, administrator preview |
| Solo configuration | Validated creation, randomized immutable track snapshots | Playlist/count/duration controls, REST creation, navigation to game |
| Solo gameplay | STOMP authorization, state machines, matching, scores, timers, recovery | Game room, answer/skip actions, STOMP client, round audio, basic finish state |
| Completed games | Durable snapshots and owner summary endpoint | Separate end-screen route remains a placeholder |

Source entry points: [backend controllers](../../backend/src/main/java/com/blindtest/controller),
[frontend routes](../../frontend/src/app/app.routes.ts), and
[game room](../../frontend/src/app/features/game/game-room/game-room.component.ts).

## Partial and deferred experiences

- The game room handles secondary-match and rejected-answer messages by clearing
  submission state; dedicated feedback presentation remains incomplete.
- The game room has an in-session finish view. It does not implement the separate,
  reloadable persisted-summary page required by UI-08.
- Recovery uses the active session retained in browser `sessionStorage`; an
  unknown session ID sends the user back to configuration.
- Lobby joining/room, profile/history browsing, friends/requests, admin user
  management, and several extracted gameplay widgets remain scaffolds.
- Backend multiplayer joining, guest tokens, social APIs, profile management,
  general history browsing, and administrator user management are absent.
- Restarting the backend loses active games; completed history is durable.

## Roadmap discrepancy

UI-01 acceptance was validated on 2026-09-11; the authoritative pointer now selects
UI-02. The shell includes public playlist navigation, reactive role visibility,
safe session-recovery redirects, and logout failure handling. See the
[frontend guide](../frontend/README.md) for navigation behavior.

Source and focused tests already include REST session creation, a STOMP client,
audio playback, and a game room. Do not infer acceptance completion for subsequent
tickets from these files alone; review the [frontend lot](../roadmap/lot-6-frontend.md).

## Validation boundaries

Backend tests include deterministic game-domain coverage and a full REST/STOMP
solo flow using PostgreSQL Testcontainers. Frontend tests include session and
socket services, audio playback, and browser entry/routing checks. Those checks
are not equivalent to full-stack browser gameplay acceptance. See
[testing](../testing.md) and [hardening tickets](../roadmap/lot-7-hardening.md).

## Known release risks

- Beta deployment triggers independently of CI; required checks do not currently
  gate a push-triggered deployment. TEST-06 tracks this prerequisite.
- OpenAPI includes intended endpoints that do not exist; verify controllers before
  generating clients or documenting a delivered operation.
- The production frontend build fetches Google Fonts and requires network access.
- Access JWTs are stored in `localStorage`, making script injection a relevant
  security concern.
- Password-reset JWTs lack a persisted one-time-use marker and can be replayed
  while valid.

[Operations](../operations/README.md) explains deployment behavior;
[security](../backend/authentication.md) explains the authentication boundaries.
