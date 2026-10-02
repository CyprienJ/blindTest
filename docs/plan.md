# Blindtest Product Delivery Roadmap

Status: delivery authority; detailed sections are linked below

Structure and implementation history reconciled: 2026-09-11

Target: first public production release, followed by multiplayer and social features

Deployment target: one VPS, Docker Compose, existing host reverse proxy

## Next Ticket

**[MP-07 — Handle disconnects, rejoin, and host departure](roadmap/lot-p2-multiplayer.md#mp-07--handle-disconnects-rejoin-and-host-departure)**

Status: ready. On 2026-10-02 the product owner moved
[Lot P2 — Multiplayer and guests](roadmap/lot-p2-multiplayer.md) ahead of
[OPS-04 — Manage production secrets](roadmap/lot-8-operations.md#ops-04--manage-production-secrets)
(see the [execution sequence](roadmap/execution-sequence.md)).
[MP-01](roadmap/lot-p2-multiplayer.md#mp-01--generalize-the-game-domain-to-several-participants),
[MP-02](roadmap/lot-p2-multiplayer.md#mp-02--add-lobbies-with-join-codes),
[MP-03](roadmap/lot-p2-multiplayer.md#mp-03--play-multiplayer-rounds-over-stomp),
[MP-04](roadmap/lot-p2-multiplayer.md#mp-04--persist-multiplayer-history-and-summaries),
[MP-05](roadmap/lot-p2-multiplayer.md#mp-05--build-the-create-join-and-lobby-screens), and
[MP-06](roadmap/lot-p2-multiplayer.md#mp-06--show-multiplayer-state-in-the-game-room-and-end-screen)
are complete: registered users can play multiplayer games end to end.

[CAT-11 — Import tracks from uploaded audio files](roadmap/lot-3-catalogue.md#cat-11--import-tracks-from-uploaded-audio-files)
awaits only one product-owner check: publish a track from a local audio file on the
production VPS. Every automated check passes as of 2026-10-02. The 29 mocked
Playwright failures recorded on 2026-09-29 no longer reproduce; only the known
flaky focus test fails in full parallel runs. The full-stack "Validate selection"
stall was in the specs, not in the product: difficulty became a required review
field, and the admin catalogue, solo game, and failure-injection specs never chose
one. They now pick a rating, and all eight full-stack specs pass.

[FND-07 — Make registration invitation-only](roadmap/lot-0-foundation.md#fnd-07--make-registration-invitation-only)
and [FND-08 — Let administrators manage accounts](roadmap/lot-0-foundation.md#fnd-08--let-administrators-manage-accounts)
were completed on 2026-10-02 at the product owner's request without moving this
pointer.

Git history and source confirm UI-01 through UI-05 were implemented on 2026-09-02
(`f8febc9`, `e9cac42`, `4548500`, `65ea8ec`, and `8228f4b`). UI-06 through UI-10
are complete, closing Lot 6. E2E-01 is complete: `docker-compose.test.yml` boots
PostgreSQL, MinIO, the backend under a dedicated `e2e` Spring profile, and the
Nginx-served frontend with no fixed host ports; `./scripts/e2e-test.sh` starts the
stack, runs the full-stack Playwright suite, and always tears it down, with a
random Compose project name per run so parallel CI cannot share data. Resend and
yt-dlp/FFmpeg are faked in-process (`com.blindtest.testing.e2e`, exposed at
`/api/e2e/emails`); Turnstile reuses Cloudflare's documented test key pair and is
the one dependency that still needs outbound network access. E2E-02 is complete:
`frontend/e2e-full-stack/account-journey.spec.ts` drives one real browser session
through registration, captured-email verification, login, an authenticated
refresh using the real HttpOnly cookie, password reset, and logout against the
real backend/database, with no API mocking. That work surfaced and fixed a real
bug: the refresh cookie's `Secure` flag was hardcoded `true`, which silently
depended on Chrome's "localhost is a secure context" exception and would have
broken over any other plain-HTTP origin; it now follows `app.base-url`'s scheme
(`AuthController`), unaffected in production since
`ProductionConfigurationValidator` already requires HTTPS there. E2E-03 (admin
catalogue journey) and E2E-04 (solo game journey) are complete, both validated
against the same real full-stack stack. E2E-05 is complete:
`load-tests/` (a headless Node/TypeScript REST+STOMP tool, no browser) proved 10,
then 40, concurrent solo games complete correctly — round outcomes, scores, and
durable summaries all reconciled against what was actually played — that a
concurrent catalogue import does not perturb an in-flight game's requested round
count, and recorded backend Actuator metrics and event latency throughout; no
bottleneck was triggered up to 40 concurrent games. Run it with
`./scripts/load-test.sh`. See [testing](testing.md#load-and-concurrency-smoke-tests)
and [load testing results](operations/load-testing.md). E2E-06 is complete: all
seven failure scenarios are covered, each at whichever layer actually exercises
it — backend integration tests with a real, controllable dependency
(`MinioMediaStorageIntegrationTest` for MinIO unavailability and expired
pre-signed URLs, a new Toxiproxy-backed
`GameHistoryPersistencePostgresFailureIntegrationTest` for a transient
PostgreSQL connection loss, `SoloGameBackendFlowIntegrationTest` for the
timer/answer race, `ImportJobRunnerTest` for import-process failure), and a new
`frontend/e2e-full-stack/failure-injection.spec.ts` for the two scenarios only a
real browser can observe — a mid-game network disconnect/reconnect, and a
simulated backend restart (the e2e-only `DELETE /api/e2e/active-sessions`
backdoor empties `InMemorySessionStore`, reproducing the one externally
observable effect a real restart has, since this isolated stack's test runner
has no Docker socket access to actually restart the `backend` container). That
work surfaced and fixed a real bug: a client whose session vanished from the
store — by restart or natural expiry — got no error at all; the STOMP
authorization interceptor's rejection closed the connection, and RxStomp's
automatic reconnect looped into the same rejection forever, leaving the player
stuck on "Connecting you to the live game…" indefinitely.
`GameSocketService` now treats a STOMP `ERROR` frame as fatal and surfaces a
normal terminal error instead of retrying forever. Expected data-loss
boundaries of the in-memory session architecture are documented alongside the
full scenario table in [failure injection](operations/failure-injection.md).
E2E-07 is complete: security verification covers the REST and STOMP authorization
matrix, enumeration boundaries, request and gameplay limits, browser headers,
CORS/cookies, private signed-media handling, secret scanning, dependency auditing,
and documented residual findings in [security verification](operations/security-verification.md).
There is no separate beta since the 2026-10-02 Lot 9 re-baseline; pushes to `main`
deploy production without a CI gate until TEST-06 and OPS-09. MEDIA-08 remains deferred until legacy files
need migration.

This is the authoritative next ticket to implement. When it meets the
[definition of done](roadmap/completion-rules.md#definition-of-done), replace it with the next ready,
unfinished ticket from the [recommended execution sequence](roadmap/execution-sequence.md#19-first-recommended-execution-sequence).
Do not advance this pointer while acceptance criteria or required validation are
still incomplete. If the next ticket is blocked, record the blocker here and move
to the next ready ticket only when the roadmap dependencies allow it.

This document supersedes the previous testing-and-storage-only plan. It organizes
delivery into lots and implementation tickets rather than time-boxed sprints.
Testing, S3-compatible storage, security, and operations are integrated into the
product roadmap.

`docs/architecture.md` remains useful target documentation, but it is not an
unquestionable source of truth. Any contradiction between that document and the
validated decisions below must be resolved through an explicit architecture or
product decision before implementation.


The [code inventory](product/status.md#roadmap-discrepancy) records the reconciliation
of frontend implementation history with this pointer.

## Roadmap tree

Read only the relevant lot and its dependencies. Ticket criteria and decisions
were moved intact; this documentation reorganization does not complete tickets.

- [1. Validated Product Decisions](roadmap/product-decisions.md)
- [2. Explicitly Deferred Features](roadmap/deferred-features.md)
- [3. First-Release User Journeys](roadmap/user-journeys.md)
- [4. Target Architecture](roadmap/target-architecture.md)
- [5. Decision Gates](roadmap/decision-gates.md)
- [6. Delivery Overview](roadmap/delivery-overview.md)
- [7. Lot 0 — Repository, Security, and Decision Baseline](roadmap/lot-0-foundation.md)
- [8. Lot 1 — Automated Quality Foundation](roadmap/lot-1-quality.md)
- [9. Lot 2 — S3/MinIO Media Foundation](roadmap/lot-2-media.md)
- [10. Lot 3 — Catalogue and Playlist Administration](roadmap/lot-3-catalogue.md)
- [11. Lot 4 — Pure Solo-Game Domain](roadmap/lot-4-game-domain.md)
- [12. Lot 5 — Sessions, WebSocket, and History Backend](roadmap/lot-5-sessions-history.md)
- [13. Lot 6 — Solo-Game Frontend and Summary](roadmap/lot-6-frontend.md)
- [14. Lot 7 — Full-Stack Validation and Hardening](roadmap/lot-7-hardening.md)
- [15. Lot 8 — VPS Operations and Deployment Readiness](roadmap/lot-8-operations.md)
- [16. Lot 9 — Beta Validation and Production Launch](roadmap/lot-9-release.md)
- [17. Post-V1 Roadmap](roadmap/post-v1.md)
- [18. Ticket Readiness and Completion Rules](roadmap/completion-rules.md)
- [19. First Recommended Execution Sequence](roadmap/execution-sequence.md)
- [20. Reference Documentation](roadmap/references.md)

[Up: Project overview](../README.md) · [Code status](product/status.md)
