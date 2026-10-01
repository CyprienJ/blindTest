# Current implementation and limitations

[Up: Product](README.md) · [Roadmap pointer](../plan.md)

Reviewed against source and test files on 2026-09-16. This is a code inventory,
not a claim that all release acceptance checks have passed. UI-07 validation includes
backend checks, frontend lint/build/unit tests, mocked browser scenarios, and Compose
configuration; full-stack browser acceptance remains separate.

## Present in code

| Area | Backend | Browser |
|---|---|---|
| Accounts | Registration, verification, login, refresh, logout, password recovery | Corresponding forms, Turnstile, session recovery, guards |
| Home and navigation | Account role information | Public home, authenticated home, shared application shell |
| Catalogue | Public tracks/playlists; administrator track, alias, and playlist management | Track review, import workflow, playlist editor |
| Imports and media | Bounded audio-file uploads and best-effort YouTube imports, waveform generation, private MinIO, signed URLs, deletion outbox | Import progress/polling, waveform excerpt selection, administrator preview |
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
- Track difficulty is set in the import review and track editor and filters
  solo game setup; completed history records the filter and each round's
  difficulty but no screen displays it yet. Streaming links are shown when a round
  is revealed; they are frozen at game creation and not kept in history.

## Roadmap discrepancy

Git history confirms UI-01 through UI-05 were implemented on 2026-09-02. The
subsequent shell fixes are maintenance of UI-01, not its original implementation.
The roadmap now records those implementation commits explicitly. UI-06 private
answer feedback, UI-07 reveal/round transitions, UI-08 persisted end summary,
UI-09 Playwright game coverage, and UI-10 desktop-browser/accessibility checks
are complete and validated, closing Lot 6. E2E-01 is complete: `docker-compose.test.yml`
plus `./scripts/e2e-test.sh` boot an isolated, parallel-safe full-stack environment
with Resend/yt-dlp faked out (Turnstile still needs network access to Cloudflare);
see [testing](../testing.md#isolated-full-stack-environment). E2E-02 is complete:
`account-journey.spec.ts` proves registration, captured-email verification, login,
access refresh, password reset, and logout against the real backend/database, and
fixed the refresh cookie's `Secure` flag to follow `app.base-url`'s scheme instead
of being hardcoded. E2E-03 (admin catalogue journey) and E2E-04 (solo game journey)
are complete, both validated against the same real full-stack stack. E2E-05 is
complete: `load-tests/` (headless REST+STOMP, no browser) proved 10 and 40
concurrent solo games complete correctly with reconciled scores/summaries and no
bottleneck triggered at that scale; see
[load testing results](../operations/load-testing.md). E2E-06 is complete: all
seven required failure scenarios (MinIO/PostgreSQL unavailability, backend
restart, browser disconnect/reconnect, import failure, expired pre-signed URL,
timer/answer race) fail cleanly, and fixed a real bug where a client whose
session vanished from the store reconnected into the same rejection forever
with no feedback; see [failure injection](../operations/failure-injection.md).
E2E-07 is complete: security verification covers the authorization matrix,
enumeration boundaries, browser transport controls, request/rate limits, private
media URLs, secret scanning, dependency auditing, and documented residual
findings; see [security verification](../operations/security-verification.md).
OPS-01 and OPS-02 are complete. OPS-03 integrates the existing Nginx Proxy
Manager through the shared `proxy` network, with separate application and MinIO
media proxy hosts documented in the [reverse-proxy runbook](../operations/reverse-proxy.md).
Later tickets still require their own validation.
See the [frontend lot](../roadmap/lot-6-frontend.md) and
[gameplay client](../frontend/gameplay.md).

## Validation boundaries

Backend tests include deterministic game-domain coverage and a full REST/STOMP
solo flow using PostgreSQL Testcontainers. Frontend tests include session and
socket services, audio playback, and browser entry/routing checks. Those checks
are not equivalent to full-stack browser gameplay acceptance. See
[testing](../testing.md) and [hardening tickets](../roadmap/lot-7-hardening.md).

## Known release risks

- Production YouTube imports fail when YouTube flags the server IP as a bot,
  even with cookies. Audio-file upload (CAT-11) is the supported workaround;
  YouTube import is kept as best effort.

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
