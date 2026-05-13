# Blindtest

Real-time multiplayer music blindtest web application.

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) 24+
- [Docker Compose](https://docs.docker.com/compose/install/) v2+

## Local development setup

### 1. Configure environment variables

```bash
cp .env.example .env
```

Edit `.env` to set your credentials:

```env
POSTGRES_DB=blindtest
POSTGRES_USER=blindtest
POSTGRES_PASSWORD=changeme
```

### 2. Start the database

```bash
docker compose up -d
```

This starts PostgreSQL 16 on port `5432`. Data is persisted in the `postgres_data` Docker volume across restarts.

### 3. Verify the database is ready

```bash
docker compose ps          # postgres should be healthy
docker compose logs postgres
```

### 4. Run database migrations

Flyway migrations are embedded in the Spring Boot backend and run automatically on startup. The migration files live in:

```
backend/src/main/resources/db/migration/
└── V1__initial_schema.sql   # creates all tables
```

### 5. Stop the environment

```bash
docker compose down          # stop containers (data preserved)
docker compose down -v       # stop containers and delete all volumes
```

## Volumes

| Volume | Purpose |
|---|---|
| `postgres_data` | PostgreSQL data directory — persisted across restarts |
| `media` | Audio files served by the backend — referenced at `/media` |

## Architecture overview

See [`docs/architecture.md`](docs/architecture.md) for the full technical specification.
