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

## Schema and configuration checks

PostgreSQL Testcontainers coverage runs Flyway against an empty PostgreSQL 16
database and validates Hibernate mappings, constraints, cascades, and queries.
Schema-independent Spring tests may use H2 with Flyway disabled and
`ddl-auto=create-drop`. H2 does not substitute for PostgreSQL schema validation.

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
