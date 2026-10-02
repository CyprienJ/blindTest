# Testing and Coverage

[Up: Development](development/README.md) · [Project overview](../README.md)

## Backend coverage

Run the backend checks and generate the JaCoCo report with:

```bash
cd backend
mvn --batch-mode checkstyle:check test
```

The readable HTML report is written to `backend/target/site/jacoco/index.html`.
The machine-readable XML report is written to
`backend/target/site/jacoco/jacoco.xml`. CI uploads the complete report as the
`backend-coverage` artifact on every backend run and retains it for 14 days.

JaCoCo enforces regression floors on critical packages during the Maven
`test` phase:

| Package | Minimum lines | Minimum branches |
| --- | ---: | ---: |
| `com.blindtest.security` | 90% | 80% |
| `com.blindtest.websocket` | 95% | 80% |
| `com.blindtest.service` | 75% | 59% |

When new critical domain packages are delivered, add package-specific floors
based on meaningful behavior tests. Raise an existing floor when tests establish
a durable higher baseline; do not lower one to make an unrelated change pass.

## Real MinIO integration

`MinioMediaStorageIntegrationTest` starts the same pinned MinIO image used by
Docker Compose and validates the provider-neutral adapter against its real API.
The suite covers upload, existence, recursive listing, pre-signed GET URLs, HTTP
byte ranges, idempotent overwrite/deletion retries, and unavailable-storage error
translation. It runs as part of the normal Maven `test` phase and therefore
requires a Docker-compatible container runtime.

## Backend solo-game flow

`com.blindtest.session.SoloGameBackendFlowIntegrationTest` exercises the complete
server-authoritative solo game without a browser. It boots the full application on
a random port against a PostgreSQL Testcontainers database with Flyway migrations
and Hibernate `ddl-auto=validate`, then:

- creates a session through `POST /api/sessions` as a verified account;
- opens a real STOMP connection over `/ws`, subscribes to the shared and private
  destinations, and plays five rounds (correct title, secondary artist match then
  timeout, skip, reconnect snapshot, duplicate-command idempotency);
- drives authoritative deadline and reveal timers through a controllable
  `SessionTaskScheduler` and a mutable `Clock`, including a duplicate-timer race
  that must yield exactly one reveal;
- confirms cross-session authorization: a non-member cannot subscribe to or
  command another owner's session;
- asserts durable history is written once to PostgreSQL on completion, the active
  session is removed, and `GET /api/sessions/{sessionId}/summary` returns the
  ordered, refresh-safe owner projection while hiding it from other accounts.

Media playback URLs resolve through the in-memory storage adapter, so the suite
needs a Docker-compatible container runtime only for PostgreSQL. Run it on JDK 21
with the rest of the backend tests:

```bash
cd backend
mvn --batch-mode checkstyle:check test
```

## Browser media seek

`frontend/e2e/media-playback.spec.ts` serves a deterministic range-capable WAV
fixture to Chromium. It verifies media duration, changes `currentTime`, and
asserts that the browser requests a nonzero byte range. Run it with the complete
Playwright suite:

```bash
cd frontend
npm run test:e2e
```

## Frontend checks

From the repository root:

```bash
cd frontend
npm run lint
npm run build
npm run test -- --watch=false --browsers=ChromeHeadless
npm run test:e2e
```

Install browser dependencies once with `npx playwright install chromium`.
On this workstation, use these overrides when necessary:

```bash
CHROME_BIN=/snap/bin/chromium npm run test -- --watch=false --browsers=ChromeHeadless
PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/snap/bin/chromium npm run test:e2e
```

Unit tests include configuration, session storage, STOMP commands/reconnection,
and audio scheduling/autoplay recovery. Playwright covers authentication, home,
shell, administrator workflows, creation, and game-room connection/routing.
The shared fixture mocks API/Turnstile behavior and blocks third-party traffic.
These browser tests do not establish full-stack gameplay acceptance.

`test:e2e:ui` opens Playwright's interactive runner; `test:e2e:debug` enables its
debugger. Reports are in `frontend/playwright-report/`, with failure traces,
screenshots, and videos in `frontend/test-results/`.

## Isolated full-stack environment

`docker-compose.test.yml` boots a real frontend, backend, PostgreSQL, and MinIO
together, proving the pieces work as one system rather than through mocks. Run
it with:

```bash
./scripts/e2e-test.sh
```

This one command builds and starts the stack, runs the full-stack Playwright
suite (`frontend/e2e-full-stack/`, `frontend/playwright.full-stack.config.ts`)
against it, and tears the stack down whether the run passes or fails. Each
invocation picks a random Compose project name, and the file publishes no host
ports, so concurrent CI runs never share a database, bucket, or network.

Every credential is deterministic and test-only. External dependencies are
faked out except Turnstile, which needs outbound HTTPS to Cloudflare:

- the backend runs with `SPRING_PROFILES_ACTIVE=e2e`
  (`application-e2e.properties`, `com.blindtest.testing.e2e`).
  `FakeEmailSender` captures verification/reset/welcome email instead of
  calling Resend, exposed at `GET`/`DELETE /api/e2e/emails` for specs that
  need a captured token; `FakeProcessFactory` writes a deterministic MP3
  fixture instead of invoking yt-dlp/FFmpeg against YouTube, and answers
  `ffprobe` with fixed tags for MP3-looking uploads while rejecting anything
  else, so upload imports run without real media tools;
- Turnstile uses Cloudflare's documented always-pass test key pair, the same
  one `docker-compose.local.yml` already uses for local development. The
  frontend still loads the real widget script and the backend still calls
  the real siteverify endpoint, so the environment needs outbound network
  access to `challenges.cloudflare.com`; nothing else does.

Neither adapter can affect a real deployment: `ProductionConfigurationValidator`
(`@Profile("production")`) rejects the Cloudflare test key outright, and the
fake beans/controller only register under the `e2e` profile, which no
production or development Compose file sets.

`frontend/e2e-full-stack/account-journey.spec.ts` (E2E-02) drives one browser
session through the real account lifecycle against the real backend and
PostgreSQL: an invitation issued through the e2e-only `POST /api/e2e/invitations`
backdoor, invitation-only registration (which signs the player in and sends only
a welcome email, checked through `/api/e2e/emails`), login, an authenticated
`/api/auth/refresh` call against the real HttpOnly cookie, a password-reset
round trip (capture the reset email, set a new password, log in with it), and
logout — verified by confirming the refresh cookie no longer works afterward.
This required making the refresh cookie's `Secure` flag follow `app.base-url`'s
scheme (`AuthController`) instead of being hardcoded `true`: browsers refuse to
store a `Secure` cookie over the stack's plain-HTTP `http://frontend` origin,
which also means the flag now correctly reflects that local development is
HTTP too — production is unaffected since `ProductionConfigurationValidator`
already requires an HTTPS `app.base-url`.

`environment.spec.ts` (E2E-01) covers the wiring itself (health check through
the real nginx proxy, the real Angular build serving, the e2e profile active).
`admin-catalogue-journey.spec.ts` (E2E-03) and `solo-game-journey.spec.ts`
(E2E-04) exercise the catalogue and gameplay journeys — see the later Lot 7
tickets in [`lot-7-hardening.md`](roadmap/lot-7-hardening.md).

## Load and concurrency smoke tests

`load-tests/` (E2E-05) drives the same real backend/PostgreSQL/MinIO/Nginx stack
with a headless Node/TypeScript tool speaking REST and STOMP directly (no
browser — the gameplay protocol is plain JSON-over-STOMP). Run it with:

```bash
./scripts/load-test.sh
```

It proves 10, then 40, concurrent solo games complete correctly (round
outcomes, scores, and durable summaries all reconciled against what was
actually played), that a concurrent catalogue import does not perturb an
in-flight game, and records backend Actuator metrics (JVM memory/threads,
HikariCP pool, Tomcat threads) alongside event latency throughout. Like
`scripts/e2e-test.sh`, it is manual/local only — no CI workflow invokes it. See
[load testing](operations/load-testing.md) for what it measures and the latest
recorded results.

## Failure-injection tests

E2E-06 proves the seven required failure scenarios fail cleanly — no corrupt
track, no duplicate history, no silent hang — at whichever layer actually
exercises them. `MinioMediaStorageIntegrationTest` covers MinIO unavailability
and expired pre-signed URLs against a real MinIO container.
`GameHistoryPersistencePostgresFailureIntegrationTest` cuts a real PostgreSQL
connection mid-write with a Toxiproxy container and proves the durable
game-history write fails without a partial row, then retries cleanly with no
duplicate. `SoloGameBackendFlowIntegrationTest` races a correct answer against
the authoritative deadline timer and proves exactly one outcome wins.
`ImportJobRunnerTest` already exhaustively covers import-process failure at the
unit level. `frontend/e2e-full-stack/failure-injection.spec.ts` covers the two
scenarios only a real browser can observe: a mid-game network
disconnect/reconnect, and a simulated backend restart via the e2e-only
`DELETE /api/e2e/active-sessions` backdoor, which reproduces
`InMemorySessionStore` starting empty the way a real restart leaves it. See
[failure injection](operations/failure-injection.md) for the full scenario
table, the in-memory data-loss boundary this documents, and a real bug this
surfaced and fixed (a session-loss STOMP error used to leave the client
reconnecting forever with no feedback).

## Schema and configuration checks

PostgreSQL Testcontainers coverage runs Flyway against an empty PostgreSQL 16
database and validates Hibernate mappings, constraints, cascades, and queries.
Schema-independent Spring tests may use H2 with Flyway disabled and
`ddl-auto=create-drop`. H2 does not substitute for PostgreSQL schema validation.

## Security verification

E2E-07 security evidence and the release finding register are maintained in
[security verification](operations/security-verification.md). The verification
covers authorization, enumeration, request bounds, gameplay rate limits, browser
headers/CORS/cookies, private media URLs, secret scanning, and dependency audits.

After Compose edits, run `docker compose config --quiet` from the repository root.
Use populated environment values when validating deployment configuration.

## Documentation-only changes

Check local Markdown links and heading anchors, parent/child navigation, and page
lengths. Aim for roughly 200 lines; split by topic before a page exceeds 300.
When moving roadmap or historical design sections, compare their content so
acceptance criteria and decisions are preserved. Application tests are unnecessary
when only prose and navigation change.

Current implementation and validation gaps are recorded in
[status](product/status.md); avoid copying historical test counts into overview pages.
