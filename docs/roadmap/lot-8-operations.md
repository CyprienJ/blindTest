# 15. Lot 8 — VPS Operations and Deployment Readiness

[Up: Roadmap](../plan.md)

### Objective

Deploy safely beside the existing site on one VPS and make recovery possible.

Dependencies: operational pieces begin early; final exit depends on Lots 2-7.

### Tickets

#### OPS-01 — Produce production-ready images

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
- health and import smoke tests pass.

#### OPS-02 — Define production Compose topology

Work:

- PostgreSQL, backend, frontend, MinIO, MinIO init;
- explicit networks;
- localhost-bound application port for host reverse proxy;
- private database and console;
- named persistent volumes;
- resource limits and restart policies;
- avoid port conflicts with the other site.

Acceptance:

- the stack starts after VPS reboot without exposing internal services.

#### OPS-03 — Integrate the existing reverse proxy

Work:

- application virtual host;
- TLS;
- `/api` and WebSocket upgrade;
- public MinIO media endpoint required by signed URLs;
- request/body/time limits suitable for the application;
- preserve the other site's configuration.

Acceptance:

- both sites work concurrently;
- WebSocket and range audio requests pass through TLS.

#### OPS-04 — Manage production secrets

Work:

- database, JWT, Resend, Turnstile, MinIO root, and application service credentials;
- least-privilege MinIO service account;
- file permissions and rotation runbook;
- prevent secrets in Compose files, logs, and CI artifacts.

Acceptance:

- deployed secrets exist only in approved secret locations;
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
- old import jobs/errors;
- abandoned sessions;
- rejected/orphaned media;
- history retention if any;
- temporary import directories.

Acceptance:

- every growing dataset has an owner, retention rule, and observable cleanup.

#### OPS-09 — Implement safe deployment and rollback

Work:

- immutable image tags;
- pre-deploy backup;
- migration compatibility check;
- pull/start/health/smoke sequence;
- rollback to compatible images;
- avoid `latest` in production;
- preserve previous Compose/env versions securely.

Acceptance:

- a failed health check automatically stops promotion;
- rollback is rehearsed on beta.

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
- other-site/reverse-proxy conflict.

Acceptance:

- each alert points to a practical first-response procedure.

### Lot exit criteria

- stack coexists with the existing site;
- secrets, backups, monitoring, and rollback are operational;
- single-node risks are accepted and mitigated.
