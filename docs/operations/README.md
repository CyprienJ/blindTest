# Operations and deployment

[Up: Project overview](../../README.md) · [Local setup](../development/README.md)

## Runtime topology

The production Compose project runs PostgreSQL 16, MinIO, a one-shot bucket
initializer, the backend, and the Nginx-served frontend. PostgreSQL, the
initializer, and the backend use the private `internal` bridge network. MinIO
uses `internal` for storage operations and joins the pre-existing external
Docker network `proxy` only for its API, under the alias `blindtest-minio`. The
frontend also uses `proxy`, where it has the stable alias `nosblindtests`.
Nginx Proxy Manager can therefore route both hostnames without binding a second
public host port.

The frontend may also bind `127.0.0.1:${APP_PORT:-8090}:80` for VPS diagnostics.
MinIO's API and console bind localhost only for operator diagnostics; PostgreSQL
has no host port. No service in this stack binds a public host port. Named volumes
persist PostgreSQL, MinIO, and legacy audio. Active gameplay state is process-local
and is lost on backend restart.

The Compose resource budget is PostgreSQL 0.50 CPU/512 MiB, MinIO 0.75 CPU/1 GiB,
the initializer 0.25 CPU/128 MiB, the backend up to `BACKEND_CPU_LIMIT` CPU/1 GiB,
and the frontend 0.25 CPU/256 MiB. Long-running services use
`restart: unless-stopped`; the initializer runs once after MinIO is healthy.

On a reboot, Docker restarts the long-running services and Compose's dependency
health checks gate the backend and frontend. The external `proxy` network must
already exist because it belongs to Nginx Proxy Manager:

```bash
docker network inspect proxy >/dev/null \
  || docker network create --driver bridge proxy
docker compose -p blindtest-prod up -d
docker compose -p blindtest-prod ps
```

The production deployment directory must contain only the production
`docker-compose.yml` and its restricted `.env`; local Compose overrides are not
copied there. The existing Nginx Proxy Manager container must be connected to
`proxy` as well.

For the single-VPS target, a host reverse proxy supplies TLS and routes the app
and public media endpoint. Keep PostgreSQL, the backend port, and the MinIO console
private or localhost-bound. Signed media URLs require a reachable browser-facing
MinIO endpoint and matching origin configuration.

Configure the existing Nginx Proxy Manager with the two proxy hosts and verify
TLS, WebSocket upgrades, range requests, and signed query strings using the
[reverse-proxy runbook](reverse-proxy.md). The application host targets
`nosblindtests:80`; the media host targets `blindtest-minio:9000`.

## Deployment workflows

- [CI](../../.github/workflows/ci.yml) runs backend and frontend validation.
- [Build images](../../.github/workflows/build-images.yml) builds and publishes
  immutable `main-<commit-sha>` images on pushes to `main`.
- [Beta](../../.github/workflows/deploy-beta.yml) and
  [Production](../../.github/workflows/deploy-prod.yml) are manually triggered
  with an existing image tag; deployment does not rebuild the image.

Pull Requests targeting `main` run the test and quality workflow. A push to
`main` only builds and publishes the Docker images. The image tag shown
by the build workflow must be supplied to a later beta or production deployment.
The `production` GitHub environment must provide `SSH_HOST` and `SSH_USER` as
variables, plus the SSH, database, JWT, Resend, Turnstile, GHCR, and S3
credentials as environment secrets. `PROD_YOUTUBE_COOKIES_B64` is optional and
enables authenticated YouTube imports. The deployment workflow writes the S3
endpoint and bucket settings together with those credentials to the VPS `.env`.

Beta currently runs independently of CI, so failing checks do not automatically
block deployment. TEST-06 in the [quality lot](../roadmap/lot-1-quality.md) tracks
this prerequisite. This guide describes existing behavior, not release approval.

Do not copy local Compose overrides into production. The base file expects the
production profile's valid secrets and HTTPS endpoints. See
[configuration](../development/configuration.md) for environment variables.

## Routine diagnostics and data

```bash
docker compose ps
docker compose logs -f backend
```

`/actuator/health` is public; `/actuator/metrics/**` requires an administrator.
HTTP errors and logs share request IDs. Media and gameplay metrics expose safe
aggregates rather than secrets or player identifiers.

`docker compose down` preserves named volumes. Adding `--volumes` deletes their
data. Flyway applies migrations at backend startup; never edit applied migrations.
Completed-game snapshots survive catalogue edits and backend restarts.

The accepted backup decision currently relies on daily VPS backups. Read
[ADR-0013](../adr/0013-rely-on-daily-vps-backups-for-now.md) and the
[operations roadmap](../roadmap/lot-8-operations.md) before assuming a restore
procedure has been rehearsed or automated.

## Detailed runbooks

- [First administrator](../admin-provisioning.md): verified-account promotion,
  command results, role refresh, and recovery.
- [Media operations](../media-operations.md): import limits, deletion backlog,
  reconciliation, and missing/orphan object response.
- [Testing](../testing.md): validation commands and infrastructure requirements.
- [Load and concurrency smoke tests](load-testing.md): `./scripts/load-test.sh`,
  what it measures, and the latest results.
- [Failure injection](failure-injection.md): the seven injected failure
  scenarios, the in-memory data-loss boundary, and where each is covered.
- [Release acceptance](../roadmap/lot-9-release.md): beta, recovery, security, launch.
