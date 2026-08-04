# Blindtest

Blindtest is a work-in-progress music blindtest application built as a Java/Spring
Boot and Angular monorepo. The long-term product is real-time and multiplayer. The
repository currently delivers authentication, music catalogue administration, the
pure solo-game domain, and the concurrency-safe active-session storage boundary;
playable session transport/UI and social features are still planned.

## Delivery status

### Minimum ticket distance to features

These counts follow the shortest functional dependency path in
[`docs/plan.md`](docs/plan.md), not the larger production-release checklist. One
unfinished roadmap ticket counts as one task. Multiplayer is not ticketed yet, so
each explicit work item in its post-V1 lot is counted as one provisional task.

| Feature | Minimum tasks remaining | Shortest remaining path |
|---|---:|---|
| Create an account | 0 | Delivered |
| Import and publish a song | 0 | Delivered |
| Create or edit a playlist | 0 | Delivered |
| Configure a solo game | 0 | Delivered locally; server session creation is next |
| Play a complete solo game | 11 | SESSION-02–06 and UI-02–07 |
| Refresh a persisted end summary | 15 | Solo-game path plus HIST-01–03 and UI-08 |
| Play a multiplayer game | At least 23 | Solo-game path plus the 12 provisional Lot P2 work items |

The authoritative current pointer is SESSION-02. Update this table whenever ticket
completion or roadmap dependencies change.

### Implemented

- Account registration, email verification, login, token refresh, logout, and
  password reset.
- Interactive, concurrency-safe provisioning of the first administrator from an
  existing verified account.
- Public published-track and playlist APIs with bounded search, deterministic
  ordering, safe metadata projections, and explicit short-lived cache headers.
- Administrator track review, YouTube import jobs, import progress over STOMP, and
  playlist management APIs.
- Administrator playlist editor with metadata management, published-track search,
  add/remove/reorder controls, and unsaved-change protection.
- A non-root backend runtime with pinned `yt-dlp` and FFmpeg, plus wall-clock,
  temporary-file-size, FFmpeg thread, and container CPU limits for imports.
- Deterministic backend adapters that exercise registration and audio import
  without Resend, Cloudflare, YouTube, `yt-dlp`, or FFmpeg.
- A provider-neutral private-media storage API with a typed MinIO SDK adapter and
  an in-memory test implementation.
- Real-MinIO integration coverage for storage lifecycle, signed byte-range reads,
  retry safety, and unavailable storage, plus a Chromium audio-seek smoke test.
- A pinned local MinIO service with persistent storage, automatic private-bucket
  initialization, application-origin CORS, and localhost-only API and console ports.
- Angular authentication pages and client-side authentication infrastructure.
- Authenticated game-configuration UI for choosing an eligible public playlist,
  track count, and round duration; it retains a local draft until gameplay APIs
  are delivered.
- Pure gameplay configuration, track snapshotting, answer normalization/matching,
  scoring, state machines, playback windows, private secondary discoveries, and
  exhaustive deterministic domain coverage.
- Immutable active-session models and a replaceable optimistic `SessionStore` port,
  with a concurrency-safe process-local implementation and expiry metadata.
- PostgreSQL persistence with Flyway migrations V1 through V15.
- Administrator CRUD for title, artist, composer, and album answer aliases, plus
  a validated whole-second playback offset.
- A Docker Compose stack containing PostgreSQL, MinIO, the Spring Boot backend,
  and the Nginx-served Angular frontend.

### Planned for the first production release

- A server-authoritative solo game with playlist and round configuration, answer
  matching, scoring, skip, timeout, reveal, and an end-of-game summary.
- Persistence of completed games.
- Migration of imported audio to private S3-compatible storage.
- Production hardening, broader automated tests, and complete operational checks.

### Deferred until after the first production release

- Real-time multiplayer sessions and guest players.
- Friends, invitations, profiles, and the full game-history experience.
- Advanced administration, moderation, and horizontal scaling.

The authoritative delivery scope and next ticket are in
[`docs/plan.md`](docs/plan.md). [`docs/architecture.md`](docs/architecture.md)
separates the architecture currently present in code from the original target
design. The target sections are not evidence that a feature has been implemented.

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) 24+
- [Docker Compose](https://docs.docker.com/compose/install/) v2+

For a host-based development loop, also install Java 21, Maven, Node.js, and npm.

## Run the complete Compose stack

Copy the environment template and replace every placeholder before starting the
application. For local browser development, include the local override so Angular
and Spring use Cloudflare's matching public test key pair:

```bash
cp .env.example .env
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
docker compose ps
```

The base Compose file remains production-like and uses the real Turnstile site
key and secret:

```bash
docker compose up -d --build
```

Do not include `docker-compose.local.yml` in a production deployment.
`CORS_ALLOWED_ORIGINS` accepts a comma-separated list of browser origins. The
local override pins it to `http://localhost:${APP_PORT}`; production should keep
it restricted to the public application origin.

The stack starts:

| Service | Purpose | Host access |
|---|---|---|
| `postgres` | PostgreSQL 16 | Internal Compose network only |
| `minio` | Private S3-compatible object storage | API `127.0.0.1:${MINIO_API_PORT}`; console `127.0.0.1:${MINIO_CONSOLE_PORT}` |
| `minio-init` | One-shot private bucket initializer | None |
| `backend` | Spring Boot API and WebSocket endpoint | Through the frontend proxy |
| `frontend` | Angular SPA, Nginx reverse proxy, and `/media` serving | `http://localhost:${APP_PORT}` (default `8090`) |

Follow backend logs or stop the stack with:

```bash
docker compose logs -f backend
docker compose -f docker-compose.yml -f docker-compose.local.yml down
```

`docker compose down` preserves the `postgres_data`, `minio_data`, and legacy
`media` volumes. Adding `--volumes` deletes all three volumes and their data.

Flyway migrations run automatically when the backend starts. Migration files are
under `backend/src/main/resources/db/migration/`.

After registering and verifying the first administrator account, follow the
operator procedure in
[`docs/admin-provisioning.md`](docs/admin-provisioning.md). It uses a one-shot
backend command and does not require a manual role update in PostgreSQL.

The backend image pins and smoke-tests `yt-dlp` and FFmpeg. Imports default to a
10-minute wall-clock limit, a 25 MiB temporary-file limit, one FFmpeg worker
thread, and a 1.0-CPU container quota. Configure these with `APP_IMPORT_TIMEOUT`,
`APP_IMPORT_MAX_SIZE_BYTES`, and `BACKEND_CPU_LIMIT`.

## Run services on the host

Start PostgreSQL separately and make the variables used by
`backend/src/main/resources/application.properties` available in your shell. Then:

```bash
cd backend
mvn spring-boot:run
```

In another terminal:

```bash
cd frontend
npm ci
npm start
```

The Angular development server runs at `http://localhost:4200` and proxies `/api`
and `/media` to `http://localhost:8080`.

## Validation

Run checks for every changed area. The full local validation is:

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

On this workstation, the frontend tests may require
`CHROME_BIN=/snap/bin/chromium`.

## Repository documentation

- [`AGENTS.md`](AGENTS.md): contributor and coding-agent operating guide.
- [`docs/plan.md`](docs/plan.md): authoritative product decisions, roadmap, and
  ticket sequence.
- [`docs/architecture.md`](docs/architecture.md): current architecture snapshot and
  original target architecture.
- [`docs/swagger.yaml`](docs/swagger.yaml): intended API contract; it currently
  mixes implemented and planned operations.
- [`docs/adr/`](docs/adr/): architecture decision records and template.
