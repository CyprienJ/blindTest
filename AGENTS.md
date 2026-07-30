# Blindtest Repository Guide

This file is the operational context for coding agents working in this repository.
Keep it accurate when the architecture, tooling, or implementation status changes.

## Project Summary

Blindtest is intended to be a real-time multiplayer music blindtest web application.
It is a monorepo with:

- a Java 21 / Spring Boot 3.2 backend;
- an Angular 21 standalone-component frontend;
- PostgreSQL 16 with Flyway migrations;
- STOMP over WebSocket for real-time events;
- Nginx and Docker Compose for deployment;
- `yt-dlp`/FFmpeg for server-side YouTube audio imports.

The product specification is broad, but the application is only partially
implemented. Do not assume that a feature described in the architecture documents
exists in code.

## Source-of-Truth Order

When sources disagree, use this order:

1. executable code and tests;
2. database migrations;
3. validated product decisions and delivery scope in
   `docs/plan.md`;
4. `docs/swagger.yaml` for intended API contracts;
5. `docs/architecture.md` and `docs/diagrams/*.puml` for the original target design;
6. the generated UI reference under `docs/Blindtest/`;
7. `README.md` and `frontend/README.md`.

`AGENTS.md` is the single tool-independent operating guide for coding agents in
this repository. Keep agent instructions here instead of adding assistant-specific
guides.

## Delivery Roadmap

The authoritative roadmap is [`docs/plan.md`](docs/plan.md). It covers the complete
delivery through the first production release and the post-V1 trajectory. Its
validated product decisions override the original target design, and its `Next
Ticket` section identifies the default implementation ticket.

## Ticket Execution Workflow

- Read the `Next Ticket` section at the top of `docs/plan.md` before starting
  roadmap implementation work. It is the authoritative default ticket unless the
  user explicitly selects another ticket.
- Work on one ticket at a time and use its acceptance criteria together with the
  roadmap definition of done as the completion checklist.
- A ticket is finished only after its implementation, relevant tests, required
  validation, and documentation updates are complete. Do not advance the roadmap
  pointer for partial work or failing checks.
- When a ticket is finished, update the `Next Ticket` section in `docs/plan.md` to
  the next ready, unfinished ticket in the recommended execution sequence. Keep
  the ticket title as a direct Markdown link to its detailed heading in the plan,
  then continue with that ticket without waiting for another prompt when the
  user's request authorizes continued roadmap implementation.
- If the next sequential ticket is blocked by a dependency or decision gate,
  record that blocker in `Next Ticket` and select the next ready ticket only when
  the roadmap permits parallel work. Ask the user when a product decision is
  required.
- If the user scoped the request to a single ticket, still update `Next Ticket`
  after completion, but stop after reporting the handoff.

## Current Implementation Snapshot

Last reviewed: 2026-07-30.

### Implemented backend features

- Account registration with Cloudflare Turnstile verification.
- Email verification and password-reset emails through Resend.
- Login, JWT access tokens, opaque rotating refresh tokens, logout.
- Refresh tokens stored in PostgreSQL and sent as Secure, HttpOnly, SameSite=Strict
  cookies scoped to `/api/auth`.
- Public published-track catalogue and public playlists.
- Admin track listing, update, delete, validate, and reject operations.
- Admin-only short-lived preview URLs for completed unpublished imports, with
  lifecycle, finalized-metadata, and object-existence checks.
- Admin YouTube import jobs, asynchronous `yt-dlp` execution, persisted progress,
  per-user STOMP progress messages, MP3 validation, checksum generation, and
  finalized upload to private object storage.
- Import polling exposes safe errors, preview availability, and lifecycle timestamps;
  completed imports transition transactionally and idempotently to published or
  rejected, while rejected media is retained pending DG-05.
- YouTube imports accept canonical single-video watch, short-link, and Shorts
  URLs; metadata is bounded, active duplicate videos are rejected, output remains
  inside isolated temporary directories, and provider failures are sanitized.
- The non-root backend runtime installs pinned, build-smoke-tested `yt-dlp` and
  FFmpeg versions; imports enforce time, temporary-file-size, FFmpeg-thread, and
  container CPU limits and terminate unsafe processes.
- Injectable email, Turnstile, import-process, scheduling, clock, and opaque-token
  boundaries with deterministic test adapters.
- Admin playlist CRUD and ordered track membership.
- JaCoCo XML/HTML backend coverage reports with CI artifact publication and
  package-level regression gates for security, WebSocket, and service code.
- PostgreSQL 16 Testcontainers integration coverage for Flyway migrations,
  Hibernate schema validation, constraints, cascades, and repository queries.
- JWT authentication for HTTP and STOMP `CONNECT`.
- A provider-neutral media-storage port with a typed MinIO Java SDK adapter for
  upload, existence checks, pre-signed GET URLs, and deletion, plus an in-memory
  test adapter. Imports use content-addressed object keys and confirm uploads
  before completion.
- Testcontainers coverage validates put, existence, listing, pre-signing, byte-range
  GET, idempotent retries, deletion, and unavailable-storage translation against a
  real pinned MinIO API.
- Local Docker Compose runs pinned MinIO and `mc` images, persists `minio_data`,
  creates a private `blindtest-media` bucket automatically, and restricts API CORS
  to the configured application origin.
- Tracks can retain a nullable legacy audio URL or reference private storage with
  a server-internal object key, checksum, and byte size; object keys are excluded
  from public and administrator DTOs.
- Track metadata includes a canonical primary `title`, required display `artist`,
  and optional album, composer, work, category, decade, duration, and image fields
  through immutable public and administrator snapshots. Publication requires the
  primary metadata plus a legacy URL or complete object-storage metadata.
- Track deletion and media replacement use a transactional deletion outbox with a
  scheduled retry/backoff worker. Administrator reconciliation reports missing
  and orphaned objects using safe key fingerprints, with aggregate Actuator metrics.
- A common safe REST error contract with stable codes and request IDs propagated
  through `X-Request-ID` and logging MDC.
- Typed application configuration with separate development, test, and production
  profiles and fail-fast production validation for secrets and endpoints.
- Flyway migrations V1 through V11 for users, refresh tokens, tracks, playlists,
  playlist tracks, import jobs, database enum constraints, object-storage track
  metadata, the media-deletion outbox, extended track metadata, active-import
  deduplication, and import workflow timestamps.

The backend currently has controllers for:

- `/api/auth/**`
- `/api/tracks`
- `/api/playlists/**`
- `/api/admin/tracks/**`
- `/api/admin/tracks/import/**`
- `/api/admin/media/**`
- `/api/admin/playlists/**`

### Not implemented in the backend

- User profile management and user search.
- Friends and friend requests.
- Game session creation and joining, including guest tokens.
- In-memory `SessionStore` and the session/round state machines.
- Gameplay STOMP handlers, answer validation, scoring, timers, skip votes, and
  synchronized audio playback.
- Game history.
- Admin user management.

There are no `model/session`, `store`, or gameplay service/controller packages yet.
The WebSocket infrastructure exists only for authentication and import progress.

### Implemented frontend features

- Login.
- Registration with Turnstile widget integration.
- Email verification.
- Forgot-password flow.
- Reset-password flow.
- Auth service, route guard, JWT interceptor, refresh flow, and local session state.
- Authenticated sign-out control with local cleanup on both successful and failed
  server-side logout.
- Shared global styling based on the target visual design.
- Playwright Chromium authentication coverage with mocked API and Turnstile
  behavior, blocked third-party traffic, browser audio range/seek coverage, and CI
  failure artifacts.

### Frontend placeholders

All non-auth feature components are currently scaffolds whose templates only render
`"<Feature> works"`:

- lobby creation, joining, and room;
- game room, audio player, answer form, scoreboard, and player list;
- end screen;
- profile and game history;
- friends and friend requests;
- admin track list/import, playlist editor, and user management;
- notification toast.

Do not treat the presence of routes or component files as feature completion.

## Repository Layout

```text
.
├── backend/
│   ├── pom.xml
│   ├── checkstyle.xml
│   └── src/
│       ├── main/java/com/blindtest/
│       │   ├── config/
│       │   ├── controller/
│       │   ├── error/
│       │   ├── infra/
│       │   ├── model/{dto,entity}/
│       │   ├── repository/
│       │   ├── security/
│       │   ├── service/
│       │   └── websocket/
│       ├── main/resources/
│       │   ├── application.properties
│       │   └── db/migration/
│       └── test/
├── frontend/
│   ├── package.json
│   ├── angular.json
│   └── src/app/
│       ├── core/
│       ├── features/
│       └── shared/
├── docs/
│   ├── adr/
│   ├── architecture.md
│   ├── swagger.yaml
│   ├── diagrams/
│   └── Blindtest/
├── .github/workflows/
├── docker-compose.yml
└── .env.example
```

## Local Development

Copy `.env.example` to `.env` and replace every placeholder. The main variables are:

- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`;
- `JWT_SECRET` (at least 32 ASCII characters);
- `RESEND_API_KEY`;
- `CLOUDFLARE_TURNSTILE_SECRET_KEY`;
- `APP_BASE_URL` and `APP_PORT`;
- `S3_ENDPOINT`, `S3_PUBLIC_ENDPOINT`, `S3_REGION`, `S3_BUCKET`,
  `S3_ACCESS_KEY`, and `S3_SECRET_KEY`;
- optional `APP_IMPORT_TEMP_DIR`, `APP_IMPORT_MAX_SIZE_BYTES`,
  `APP_IMPORT_TIMEOUT`, and `BACKEND_CPU_LIMIT`;
- optional `MEDIA_DELETION_POLL_DELAY_MS`;
- optional `MINIO_API_PORT` and `MINIO_CONSOLE_PORT`;
- optional image overrides used by deployment.

Run the full container stack from the repository root:

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f backend
docker compose down
```

For a host-based development loop:

```bash
# PostgreSQL must already be reachable on localhost:5432.
cd backend
mvn spring-boot:run

# In another terminal:
cd frontend
npm ci
npm start
```

Angular's development proxy sends `/api` and `/media` to `localhost:8080`.

## Required Validation

Run checks relevant to every changed area. Before a full handoff, prefer:

```bash
cd backend
mvn --batch-mode checkstyle:check test

cd ../frontend
npm run lint
npm run build
npm run test -- --watch=false --browsers=ChromeHeadless
npm run test:e2e

cd ..
docker compose config --quiet
```

On this workstation, Karma may need:

```bash
CHROME_BIN=/snap/bin/chromium npm run test -- --watch=false --browsers=ChromeHeadless
```

Baseline at the review date:

- backend: 0 Checkstyle violations and 199 passing tests; JaCoCo coverage gates
  pass for security, WebSocket, and service packages;
- frontend: lint passes, production build passes, 1 passing scaffold unit test,
  and 27 passing Playwright authentication/media/smoke tests;
- Docker Compose configuration parses successfully when unset secret warnings are
  ignored.

Schema-agnostic backend integration tests use H2 with Flyway disabled and Hibernate
`ddl-auto=create-drop`. The PostgreSQL Testcontainers suite runs Flyway from an
empty database with Hibernate `ddl-auto=validate` and requires a Docker-compatible
container runtime.

## Coding Conventions

- Use English for code, tests, comments, commits, and technical documentation.
- Follow TDD where practical and add tests for behavior changes.
- Keep controllers thin; business behavior belongs in services.
- Persist durable domain data through JPA repositories.
- Keep live game state in memory as described by the target architecture unless a
  design change is explicitly agreed.
- Use DTOs at HTTP and WebSocket boundaries; do not expose JPA entities.
- Add schema changes as new Flyway migrations. Never edit an applied migration.
- Keep the OpenAPI file and architecture documentation synchronized with delivered
  endpoints and behavior.
- Match frontend work to the corresponding reference screen in
  `docs/Blindtest/Blindtest Views.html` and its JSX sources.
- Preserve standalone Angular components and lazy feature routes.
- Never commit `.env`, credentials, generated build output, media files, or
  dependency directories.

## Known Risks and Inconsistencies

Address these before considering the application production-ready:

1. `frontend/src/environments/environment.prod.ts` still uses the documented
   Turnstile test site key. Production anti-bot protection is therefore not ready.
2. The beta deployment workflow runs independently from the CI workflow. A push to
   `main` can deploy without waiting for Checkstyle/tests/lint to pass.
3. `docs/swagger.yaml` represents a mixture of intended and delivered endpoints.
   It omits some implemented operations and includes many unimplemented product
   endpoints. Verify contracts against controllers before using it to generate
   clients.
4. Frontend coverage is effectively absent: only the root app creation test exists.
5. The committed production frontend build fetches Google Fonts during compilation,
   making builds depend on external network availability.
6. Access tokens are stored in `localStorage`; account for XSS exposure in the
   frontend security model.
7. Password-reset JWTs are not persisted as one-time tokens, so a token can be
   replayed until it expires.

## Delivery and Deployment

- `.github/workflows/ci.yml` runs backend Checkstyle/tests and frontend
  lint/build/tests on pushes and pull requests.
- `.github/workflows/deploy-beta.yml` builds and pushes `:beta` images and deploys
  every push to `main`.
- `.github/workflows/deploy-prod.yml` is manually triggered and deploys `:prod`
  images.
- Nginx serves the Angular SPA and `/media`, and proxies `/api` and `/ws` to the
  backend.
- PostgreSQL, MinIO, and legacy media use named Docker volumes.

When updating this file, keep the implementation snapshot factual and concise.
Move features from “not implemented” to “implemented” only after their code and
tests exist.
