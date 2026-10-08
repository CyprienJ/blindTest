# Current implementation and limitations

[Up: Product](README.md) · [Roadmap pointer](../plan.md)

Reviewed against source and test files on 2026-09-16. This is a code inventory,
not a claim that all release acceptance checks have passed. UI-07 validation includes
backend checks, frontend lint/build/unit tests, mocked browser scenarios, and Compose
configuration; full-stack browser acceptance remains separate.

## Present in code

| Area | Backend | Browser |
|---|---|---|
| Accounts | Invitation-only registration, administrator invitations and account management, login, refresh, logout, password recovery | Invitation registration form, Turnstile, administrator Users page (accounts and invitations), session recovery, guards |
| Home and navigation | Account role information | Public home, authenticated home, shared application shell |
| Catalogue | Public tracks/playlists; administrator track, alias, and playlist management; cover suggestions, uploads, and storage | Track review, import workflow, cover picker, playlist editor |
| Imports and media | Bounded audio-file uploads and best-effort YouTube imports, waveform generation, private MinIO, signed URLs, deletion outbox | Import progress/polling, waveform excerpt selection, administrator preview |
| Solo configuration | Validated creation, randomized immutable track snapshots | Playlist/count/duration controls, REST creation, navigation to game |
| Solo gameplay | STOMP authorization, state machines, matching, scores, timers, recovery | Game room, answer/skip actions, STOMP client, round audio, basic finish state |
| Completed games | Durable snapshots of every participant and a per-participant summary endpoint | Refresh-safe end-screen route rendering the durable summary |

Source entry points: [backend controllers](../../backend/src/main/java/com/blindtest/controller),
[frontend routes](../../frontend/src/app/app.routes.ts), and
[game room](../../frontend/src/app/features/game/game-room/game-room.component.ts).

## Partial and deferred experiences

- Recovery uses the active session retained in browser `sessionStorage`; an
  unknown session ID sends the user back to configuration.
- Profile/history browsing and friends/requests remain scaffolds, as does the
  unused `player-list` widget (the multiplayer scoreboard shows who found each title).
- A deleted or demoted account keeps its current access token for up to 15
  minutes; account changes are not audited. See
  [ADR-0018](../adr/0018-administrator-account-management.md).
- The game domain supports up to ten participants with per-participant scores,
  skip votes, and ranked standings (MP-01), and the backend opens lobbies that
  registered users join by code and the host starts (MP-02), and plays their rounds
  over STOMP with per-player scores and skip votes (MP-03), and stores every
  player's result in history (MP-04). The browser creates, joins, and runs lobbies
  (MP-05) and plays them with a live scoreboard and ranked end screen (MP-06), and every member counts as connected until presence
  tracking (MP-07). Guests join by code with a nickname and Turnstile, receive a
  session-scoped token, and play to a guest end screen (MP-08, MP-09); the host
  chooses whether every device or only its own plays the music (ADR-0022). The host
  can change the playlist and rules in the lobby, and players return to the same
  lobby after a game (MP-11, ADR-0023). Social
  APIs, profile management, and general history browsing are absent.
- A guest's token lives in one browser tab; closing it loses the seat. The
  per-client guest join limit trusts the first `X-Forwarded-For` hop, which a
  client can forge. Host-only audio is enforced by the browser only: every device
  still receives the signed playback URL. Each reveal also carries the next round's
  signed URL so it can buffer, so a client could fetch that audio about four seconds
  before the round starts.
- A member who leaves or is kicked from a lobby keeps any existing STOMP
  subscription to its public topic until they disconnect.
- Returning to the lobby works only while the finished game is retained (five
  minutes by default), and a member kicked from the reopened lobby can come back
  through the end screen during that window.
- Restarting the backend loses active games; completed history is durable.
- Administrator invitations are delivered only by email, and Resend failures are
  only logged. The admin page cannot show or copy an invitation link, so an
  invitation is lost if email delivery fails; resend it once delivery works.
- Track difficulty is set in the import review and track editor and filters
  solo game setup; completed history records the filter and each round's
  difficulty but no screen displays it yet. Streaming links and the stored cover
  are shown when a round is revealed; they are frozen at game creation and not
  kept in history, so the end screen shows no cover yet.
- Cover suggestions depend on Deezer, iTunes, and MusicBrainz being reachable from
  the server; tracks imported before covers existed keep only the legacy
  `imageUrl` until an administrator chooses a cover.

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

- Production deployment on push to `main` runs independently of CI; required
  checks do not gate it, and there is no automated rollback. The product owner
  accepted this until TEST-06 and OPS-09, which follow Lot P2.
- OpenAPI includes intended endpoints that do not exist; verify controllers before
  generating clients or documenting a delivered operation.
- The production frontend build fetches Google Fonts and requires network access.
- Access JWTs are stored in `localStorage`, making script injection a relevant
  security concern.
- Password-reset JWTs lack a persisted one-time-use marker and can be replayed
  while valid.

[Operations](../operations/README.md) explains deployment behavior;
[security](../backend/authentication.md) explains the authentication boundaries.
