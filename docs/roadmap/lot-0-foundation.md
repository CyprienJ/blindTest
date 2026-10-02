# 7. Lot 0 — Repository, Security, and Decision Baseline

[Up: Roadmap](../plan.md)

### Objective

Remove known security hazards, establish accurate documentation, and make runtime
configuration safe before new product code is added.

### Tickets

#### FND-01 — Revoke and remove the committed Resend credential

Status: handled outside the roadmap ticket workflow by the product owner.

Work:

- revoke/rotate the exposed key;
- remove the fallback credential from `application.properties`;
- require a runtime secret outside the test profile;
- inspect Git history and deployment environments;
- document the incident and rotation.

Acceptance:

- no live-looking credential remains in tracked files;
- production refuses to send mail without configured credentials;
- tests use an explicit fake key;
- secret scanning passes.

#### FND-02 — Align documentation with delivered code

Status: completed on 2026-07-27.

Work:

- mark this roadmap as the delivery authority;
- update the root README and remove `CLAUDE.md` in favor of `AGENTS.md`;
- describe the current and target architecture separately;
- update `AGENTS.md` with the roadmap location;
- create an ADR directory and template.

Acceptance:

- a new contributor can distinguish implemented, planned, and deferred behavior;
- local commands reflect the real Compose stack;
- no document still claims that all code remains to be written.

#### FND-03 — Standardize error contracts and correlation IDs

Status: completed on 2026-07-27.

Work:

- define the REST error DTO and stable error codes;
- add one global exception handler;
- include a request/correlation ID in logs and error responses;
- prevent stack traces and provider secrets from reaching clients.

Acceptance:

- authentication, validation, not-found, conflict, and internal errors share one
  documented format;
- integration tests verify representative errors.

#### FND-04 — Validate production configuration at startup

Status: completed on 2026-07-27.

Work:

- introduce typed configuration properties;
- validate JWT, Resend, Turnstile, database, S3, and public URL settings;
- separate development, test, and production defaults;
- remove permissive production fallbacks.

Acceptance:

- missing production secrets fail startup with safe messages;
- test/dev profiles remain easy to run;
- values are not printed in logs.

#### FND-05 — Define and implement first-admin provisioning

Status: completed on 2026-07-30.

Decision: [ADR-0010](../adr/0010-first-administrator-operator-command.md).

Work:

- implement the approved provisioning mechanism;
- make it idempotent and auditable;
- prevent normal registration from selecting `ADMIN`.

Acceptance:

- the first administrator can be created without manual unsafe SQL;
- repeated provisioning cannot create unexpected administrators.

#### FND-06 — Record core architecture decisions

Status: completed on 2026-07-27.

Work:

- ADR: modular monolith;
- ADR: server-authoritative sessions;
- ADR: STOMP from V1;
- ADR: in-memory `SessionStore` behind a port;
- ADR: private S3 bucket and pre-signed playback;
- ADR: PostgreSQL history snapshots.

Acceptance:

- trade-offs and future replacement paths are recorded;
- architecture diagrams reference the ADRs.

#### FND-07 — Make registration invitation-only

Status: completed on 2026-10-02.

Decision: [ADR-0017](../adr/0017-invitation-only-registration.md).

Work:

- administrators invite, list, resend, and revoke invitations by email;
- registration requires a single-use invitation that expires after 48 hours;
- invited accounts are created verified for the invited address;
- delete every existing non-administrator account;
- bootstrap the first administrator with an operator-issued invitation;
- remove public sign-up entry points from the frontend.

Acceptance:

- no account can be created without a valid invitation;
- an invitation cannot be reused, used after expiry, or used after revocation;
- only administrators can issue invitations;
- after migration, only administrator accounts remain.

### Lot exit criteria

- known committed secret is rotated;
- production configuration fails safely;
- documentation and ADRs reflect validated decisions;
- admin provisioning has an approved design.
