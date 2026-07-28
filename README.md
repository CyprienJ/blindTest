# Blindtest

Blindtest is a work-in-progress music blindtest application built as a Java/Spring
Boot and Angular monorepo. The long-term product is real-time and multiplayer, but
the repository currently delivers authentication and music catalogue administration;
gameplay and social features are still planned.

## Delivery status

### Implemented

- Account registration, email verification, login, token refresh, logout, and
  password reset.
- Public published-track and playlist APIs.
- Administrator track review, YouTube import jobs, import progress over STOMP, and
  playlist management APIs.
- Deterministic backend adapters that exercise registration and audio import
  without Resend, Cloudflare, YouTube, `yt-dlp`, or FFmpeg.
- Angular authentication pages and client-side authentication infrastructure.
- PostgreSQL persistence with Flyway migrations V1 through V6.
- A Docker Compose stack containing PostgreSQL, the Spring Boot backend, and the
  Nginx-served Angular frontend.

### Planned for the first production release

- A server-authoritative solo game with playlist and round configuration, answer
  matching, scoring, skip, timeout, reveal, and an end-of-game summary.
- Persistence of completed games.
- Private S3-compatible media storage, initially provided by MinIO.
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
application:

```bash
cp .env.example .env
docker compose up -d --build
docker compose ps
```

The stack starts:

| Service | Purpose | Host access |
|---|---|---|
| `postgres` | PostgreSQL 16 | Internal Compose network only |
| `backend` | Spring Boot API and WebSocket endpoint | Through the frontend proxy |
| `frontend` | Angular SPA, Nginx reverse proxy, and `/media` serving | `http://localhost:${APP_PORT}` (default `8090`) |

Follow backend logs or stop the stack with:

```bash
docker compose logs -f backend
docker compose down
```

`docker compose down` preserves the `postgres_data` and `media` volumes. Adding
`--volumes` deletes both volumes and their data.

Flyway migrations run automatically when the backend starts. Migration files are
under `backend/src/main/resources/db/migration/`.

The current backend runtime image does not yet contain `yt-dlp` or FFmpeg, so
YouTube imports do not work inside that container even though the import API is
implemented.

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
