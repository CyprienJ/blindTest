# Current implementation and limitations

[Up: Product](README.md) · [Roadmap pointer](../plan.md)

Reviewed against source and test files on 2026-09-11. This is a code inventory,
not a claim that all release acceptance checks have passed. UI-07 validation includes
backend checks, frontend lint/build/unit tests, mocked browser scenarios, and Compose
configuration; full-stack browser acceptance remains separate.

## Present in code

| Area | Backend | Browser |
|---|---|---|
| Accounts | Registration, verification, login, refresh, logout, password recovery | Corresponding forms, Turnstile, session recovery, guards |
| Home and navigation | Account role information | Public home, authenticated home, shared application shell |
| Catalogue | Public tracks/playlists; administrator track, alias, and playlist management | Track review, import workflow, playlist editor |
| Imports and media | Bounded YouTube imports, waveform generation, private MinIO, signed URLs, deletion outbox | Import progress/polling, waveform excerpt selection, administrator preview |
| Solo configuration | Validated creation, randomized immutable track snapshots | Playlist/count/duration controls, REST creation, navigation to game |
| Solo gameplay | STOMP authorization, state machines, matching, scores, timers, recovery | Game room, answer/skip actions, STOMP client, round audio, basic finish state |
| Completed games | Durable snapshots and owner summary endpoint | Refresh-safe end-screen route rendering the durable summary |

Source entry points: [backend controllers](../../backend/src/main/java/com/blindtest/controller),
[frontend routes](../../frontend/src/app/app.routes.ts), and
[game room](../../frontend/src/app/features/game/game-room/game-room.component.ts).

## Partial and deferred experiences

- Recovery uses the active session retained in browser `sessionStorage`; an
  unknown session ID sends the user back to configuration.
- Lobby joining/room, profile/history browsing, friends/requests, admin user
  management, and several extracted gameplay widgets remain scaffolds.
- Backend multiplayer joining, guest tokens, social APIs, profile management,
  general history browsing, and administrator user management are absent.
- Restarting the backend loses active games; completed history is durable.

## Roadmap discrepancy

Git history confirms UI-01 through UI-05 were implemented on 2026-09-02. The
subsequent shell fixes are maintenance of UI-01, not its original implementation.
The roadmap now records those implementation commits explicitly. UI-06 private
answer feedback, UI-07 reveal/round transitions, UI-08 persisted end summary,
UI-09 Playwright game coverage, and UI-10 desktop-browser/accessibility checks
are complete and validated, closing Lot 6; the next ticket is E2E-01 (Lot 7).
Later tickets still require their own acceptance validation.
See the [frontend lot](../roadmap/lot-6-frontend.md) and
[gameplay client](../frontend/gameplay.md).

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
