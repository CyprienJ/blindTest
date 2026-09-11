# 16. Lot 9 — Beta Validation and Production Launch

[Up: Roadmap](../plan.md)

### Objective

Validate the complete product with representative users and launch with controlled
risk.

### Tickets

#### REL-01 — Deploy an isolated beta environment

Work:

- separate database, bucket, credentials, ports, and domain;
- same image artifacts as production;
- synthetic/non-sensitive catalogue.

Acceptance:

- beta cannot read or mutate production data.

#### REL-02 — Execute product acceptance

Checklist:

- register/verify/login/reset;
- admin import/preview/publish;
- playlist create/order;
- all game outcomes;
- summary refresh;
- desktop browser coverage;
- error and empty states;
- reference-design review.

Acceptance:

- product owner signs off the V1 journeys and known limitations.

#### REL-03 — Approve content/import policy

Decision: [ADR-0012](../adr/0012-assume-operator-holds-content-rights.md).

Work:

- document the operator-owned rights assumption;
- require external operator confirmation in the release checklist;
- retain administrator-only import and private-media controls;
- document track removal through the existing deletion workflow.

Acceptance:

- production import is not launched without operator confirmation that the
  catalogue satisfies the recorded rights assumption.

#### REL-04 — Rehearse data and media recovery

Work:

- restore beta PostgreSQL;
- restore/reconcile MinIO;
- verify histories and playback;
- measure actual recovery time.

Acceptance:

- recovery evidence meets DG-07.

#### REL-05 — Run final performance and security checks

Work:

- repeat expected/headroom load;
- dependency/image scan;
- authorization regression;
- verify no test keys/defaults;
- verify rate and size limits.

Acceptance:

- no unexplained release-blocking result remains.

#### REL-06 — Execute production launch

Work:

- backups;
- migrations;
- deploy pinned images;
- health and smoke tests;
- create/verify admin;
- import a controlled production track;
- create a playlist;
- complete a solo game;
- monitor.

Acceptance:

- every critical journey works through the public TLS endpoint.

#### REL-07 — Post-launch observation

Work:

- monitor errors, storage, DB, WebSocket, email, and import;
- review feedback;
- prioritize fixes before post-V1 expansion;
- record capacity baseline.

Acceptance:

- launch incident log and prioritized follow-up list exist.
