# 15. Lot 8 — VPS Operations and Deployment Readiness

[Up: Roadmap](../plan.md)

### Objective

Deploy safely beside the existing site on one VPS and make recovery possible.

Dependencies: operational pieces begin early; final exit depends on Lots 2-7.

Deployment target: the existing Nginx Proxy Manager runs in Docker on the
pre-existing external Docker network `proxy`. The Blindtest frontend joins that
network with the Docker alias `nosblindtests`; Nginx Proxy Manager routes the
public application hostname to `nosblindtests:80`. The optional host binding
`127.0.0.1:8090:80` is retained for VPS diagnostics and is not the proxy
connection path.

### Tickets

#### OPS-01 — Produce production-ready images

Status: completed (see [current status](../product/status.md#roadmap-discrepancy)).

Work:

- multi-stage reproducible builds;
- non-root runtime;
- pinned base images and runtime tools;
- health checks;
- read-only filesystem where practical;
- bounded temporary import volume;
- image vulnerability scan.

Acceptance:

- images contain only required runtime tools;
- health and import smoke tests pass;
- images are scanned for vulnerabilities before publication;
- published images have immutable commit-based tags and do not require
  `latest` in production.

#### OPS-02 — Define production Compose topology

Status: completed (see [current status](../product/status.md#roadmap-discrepancy)).

Work:

- PostgreSQL, backend, frontend, MinIO, MinIO init;
- explicit internal and external `proxy` networks;
- frontend is reachable from Nginx Proxy Manager as `nosblindtests:80` and may
  retain a localhost-bound `8090` diagnostic port;
- private database and console;
- named persistent volumes;
- resource limits and restart policies;
- avoid port conflicts with the other site.

Acceptance:

- the stack starts after VPS reboot without exposing internal services;
- the frontend and existing site coexist on the VPS without port conflicts;
- PostgreSQL, the backend, and the MinIO console are unreachable from the
  public network.

#### OPS-03 — Integrate the existing reverse proxy

Status: in use in production; see the [reverse-proxy runbook](../operations/reverse-proxy.md).

Work:

- application virtual host in the existing Nginx Proxy Manager;
- TLS;
- `/api` and WebSocket upgrade;
- a browser-reachable MinIO API endpoint, preferably a separate hostname such
  as `media.nosblindtests.fr`, for signed URLs;
- request/body/time limits suitable for the application;
- preserve the other site's configuration.

Acceptance:

- both sites work concurrently;
- WebSocket and range audio requests pass through TLS;
- signed media URLs resolve from the browser with their query strings intact;
- the application proxy target is `nosblindtests:80`, while the media proxy
  target is MinIO's API port and the MinIO console remains private.

#### OPS-04 — Manage production secrets

Work:

- database, JWT, Resend, Turnstile, MinIO root, and application service credentials;
- least-privilege MinIO service account;
- file permissions and rotation runbook;
- prevent secrets in Compose files, logs, and CI artifacts.

Acceptance:

- deployed secrets exist only in approved secret locations;
- the VPS Compose environment is provisioned with restricted file permissions
  and is not regenerated into CI logs or artifacts on every deployment;
- rotation has been rehearsed for JWT/provider/storage credentials where possible.

#### OPS-05 — PostgreSQL backup and restore

Decision: [ADR-0013](../adr/0013-rely-on-daily-vps-backups-for-now.md).

Work:

- verify daily VPS backups include the PostgreSQL volume;
- document the provider schedule, retention, and restore procedure;
- restore runbook for an isolated VPS or clone;
- periodic restore test;
- migration rollback policy.

Acceptance:

- a restore to an isolated database is demonstrated and timed.

#### OPS-06 — MinIO backup and restore

Decision: [ADR-0013](../adr/0013-rely-on-daily-vps-backups-for-now.md).

Work:

- verify daily VPS backups include the MinIO volume;
- document provider backup scope and shared-failure risk;
- restore and reconciliation;
- capacity alerts;
- document single-node failure risk.

Acceptance:

- a deleted test object/bucket can be restored in an isolated rehearsal.

#### OPS-07 — Add application observability

Work:

- structured logs with correlation/session/job IDs;
- health and readiness;
- metrics for HTTP, WebSocket sessions, game timers, imports, MinIO, DB pool,
  deletion outbox, and email;
- disk/capacity metrics;
- basic alerting.

Acceptance:

- operators can distinguish application, database, media, and provider failures.

#### OPS-08 — Add retention and cleanup jobs

Decision: [ADR-0011](../adr/0011-data-and-rejected-media-retention.md).

Work:

- expired refresh tokens;
- expired, used, and revoked registration invitations (FND-07);
- old import jobs/errors and staged upload files (CAT-11);
- abandoned sessions;
- rejected/orphaned media;
- history retention if any;
- temporary import directories.

Acceptance:

- every growing dataset has an owner, retention rule, and observable cleanup.

#### OPS-09 — Implement safe deployment and rollback

Current state (2026-10-02): `build-images.yml` publishes commit-tagged GHCR images
on every push to `main`, and `deploy.yml` deploys them over SSH with no CI gate,
health-based promotion stop, pre-deploy backup, or automated rollback. The product
owner accepted that risk until after Lot P2. TEST-06 owns the CI-check gate; this
ticket owns everything after the gate.

Work:

- immutable image tags;
- GitHub Actions builds the backend and frontend images, publishes them to a
  private or appropriately access-controlled GHCR repository, and deploys by
  commit tag over SSH;
- pre-deploy backup;
- migration compatibility check;
- pull/start/health/smoke sequence;
- rollback to compatible images;
- avoid `latest` in production;
- preserve previous Compose/image/env versions securely;
- consume the TEST-06 gate rather than re-implementing it.

Acceptance:

- a failed health check automatically stops promotion;
- the public application smoke test covers REST, WebSocket, and media playback;
- rollback is rehearsed on beta without deleting named data volumes;
- an application restart or deployment clearly accepts the documented loss of
  active in-memory games.

#### OPS-10 — Write operator runbooks

Runbooks:

- deploy/rollback;
- database restore;
- MinIO restore;
- failed import;
- full disk;
- expired/provider credentials;
- stuck session/job;
- compromised account/admin;
- other-site/reverse-proxy conflict;
- Nginx Proxy Manager and shared `proxy` network configuration.

Acceptance:

- each alert points to a practical first-response procedure.

### Lot exit criteria

- stack coexists with the existing site;
- secrets, backups, monitoring, and rollback are operational;
- single-node risks are accepted and mitigated.
