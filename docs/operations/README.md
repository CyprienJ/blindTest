# Operations and deployment

[Up: Project overview](../../README.md) · [Local setup](../development/README.md)

## Runtime topology

Docker Compose runs PostgreSQL 16, MinIO, a one-shot bucket initializer, the backend,
and the Nginx-served frontend. Nginx proxies REST and WebSocket traffic and retains
legacy `/media` serving. Named volumes persist PostgreSQL, MinIO, and legacy audio.
Active gameplay state is process-local and is lost on backend restart.

For the single-VPS target, a host reverse proxy supplies TLS and routes the app
and public media endpoint. Keep PostgreSQL, the backend port, and the MinIO console
private or localhost-bound. Signed media URLs require a reachable browser-facing
MinIO endpoint and matching origin configuration.

## Deployment workflows

- [CI](../../.github/workflows/ci.yml) runs backend and frontend validation.
- [Beta](../../.github/workflows/deploy-beta.yml) builds/pushes beta images and
  deploys on pushes to `main`; it also supports manual dispatch.
- [Production](../../.github/workflows/deploy-prod.yml) is manually triggered and
  builds/pushes production images.

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
- [Release acceptance](../roadmap/lot-9-release.md): beta, recovery, security, launch.
