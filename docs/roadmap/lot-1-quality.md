# 8. Lot 1 — Automated Quality Foundation

[Up: Roadmap](../plan.md)

### Objective

Create reliable feedback before implementing storage and gameplay changes.

### Tickets

#### TEST-01 — Add backend coverage reporting

Status: completed on 2026-07-27.

Work:

- add JaCoCo XML and HTML reports;
- publish the report in CI;
- record baseline line and branch coverage;
- gate changed critical packages incrementally.

Acceptance:

- every backend CI run produces a readable coverage report;
- no arbitrary global threshold encourages low-value tests.

#### TEST-02 — Add PostgreSQL Testcontainers and Flyway validation

Status: completed on 2026-07-27.

Work:

- start PostgreSQL 16 in integration tests;
- run all Flyway migrations from an empty database;
- start Spring with `ddl-auto=validate`;
- cover UUIDs, enum constraints, cascades, quoted `order`, and repository queries;
- keep H2 only for tests where schema fidelity is irrelevant.

Acceptance:

- CI proves that the real schema migrates and the application validates against it;
- schema-sensitive tests no longer rely exclusively on H2.

#### TEST-03 — Add Playwright foundation

Status: completed on 2026-07-27.

Work:

- install `@playwright/test`;
- create `playwright.config.ts`, fixtures, reports, and ignore rules;
- configure `webServer`, base URL, Chromium, trace, screenshot, and failure video;
- add `test:e2e`, `test:e2e:ui`, and debug scripts;
- use one CI worker initially.

Acceptance:

- a deterministic Chromium smoke test passes locally and in CI;
- a forced failure produces a usable trace and screenshot.

#### TEST-04 — Cover authentication with Playwright

Status: completed on 2026-07-28.

Work:

- mocked-API tests for login, registration, Turnstile states, verification,
  forgot/reset password, refresh, logout, guards, and error states;
- semantic locators and keyboard behavior;
- focused ARIA snapshots;
- no real Cloudflare or Resend calls.

Acceptance:

- every implemented authentication page covers success, validation, authorization,
  and server failure;
- tests do not depend on external networks.

#### TEST-05 — Create deterministic external-service adapters

Status: completed on 2026-07-28.

Work:

- fake email adapter for tests;
- fake Turnstile verifier;
- fake process wrapper producing a small valid MP3 fixture;
- injected clock, scheduler, random, and ID generators where needed.

Acceptance:

- full-stack tests can exercise registration and import without external services
  or YouTube.

#### TEST-06 — Make required CI checks a deployment prerequisite

Work:

- expose required status checks for backend, frontend, migration, and smoke tests;
- prevent the push-to-`main` production deployment from racing or bypassing the
  CI workflow (there is no separate beta since the 2026-10-02 Lot 9 re-baseline);
- upload test artifacts on failure.

Acceptance:

- a failing required test prevents deployment;
- manual production deployment cannot bypass required checks accidentally.

### Lot exit criteria

- PostgreSQL/Flyway is validated in CI;
- Playwright covers authentication;
- deterministic integration adapters exist;
- deployments can depend on named required checks.
