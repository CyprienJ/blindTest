# Local development

[Up: Project overview](../../README.md) · [Validation](../testing.md)

See [environment configuration](configuration.md) for variables, profiles, and
internal versus browser-facing storage endpoints.

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) 24+
- [Docker Compose](https://docs.docker.com/compose/install/) v2+

For a host-based development loop, also install Java 21, Maven, Node.js, and npm.

## Run the complete Compose stack

Copy the environment template and replace every placeholder before starting the
application. `docker-compose.override.yml` is loaded automatically for local
commands, so Angular and Spring use the development profile and Cloudflare's
matching public test key pair:

```bash
cp .env.example .env
docker compose build
docker compose up -d
docker compose ps
```

The base Compose file remains production-like. To test it locally without the
automatic development override, select it explicitly and provide production-valid
HTTPS endpoints and real credentials:

```bash
docker compose -f docker-compose.yml up -d --build
```

Do not copy `docker-compose.override.yml` or `docker-compose.local.yml` into a
production deployment.
`CORS_ALLOWED_ORIGINS` accepts a comma-separated list of browser origins. The
automatic local override pins it to `http://localhost:${APP_PORT}`; production
should keep it restricted to the public application origin.

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
docker compose down
```

`docker compose down` preserves the `postgres_data`, `minio_data`, and legacy
`media` volumes. Adding `--volumes` deletes all three volumes and their data.

Flyway migrations run automatically when the backend starts. Migration files are
under `backend/src/main/resources/db/migration/`.

After registering and verifying the first administrator account, follow the
operator procedure in
[`docs/admin-provisioning.md`](../admin-provisioning.md). It uses a one-shot
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

The Angular development server runs at `http://localhost:4200` and proxies `/api`,
`/media`, and `/ws` to `http://localhost:8080`.
