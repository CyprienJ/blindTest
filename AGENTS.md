# Blindtest Repository Guide

This file is the operational context for coding agents working in this repository.
Keep it accurate when the architecture, tooling, or implementation status changes.

## Project Summary

Blindtest is intended to be a real-time multiplayer music blindtest web application.
It is a monorepo with:

- a Java 21 / Spring Boot 3.2 backend;
- an Angular 21 standalone-component frontend;
- PostgreSQL 16 with Flyway migrations;
- STOMP over WebSocket for real-time events;
- Nginx and Docker Compose for deployment;
- `yt-dlp`/FFmpeg for server-side YouTube audio imports.

The product specification is broad, but the application is only partially
implemented. Do not assume that a feature described in the architecture documents
exists in code.

## Source-of-Truth Order

When sources disagree, use this order:

1. executable code and tests;
2. database migrations;
3. validated product decisions and delivery scope in
   `docs/plan.md`;
4. `docs/swagger.yaml` for intended API contracts;
5. `docs/architecture.md` and `docs/diagrams/*.puml` for the original target design;
6. the generated UI reference under `docs/Blindtest/`;
7. `README.md` and `frontend/README.md`.

`AGENTS.md` is the single tool-independent operating guide for coding agents in
this repository. Keep agent instructions here instead of adding assistant-specific
guides.

## Delivery Roadmap

The authoritative roadmap is [`docs/plan.md`](docs/plan.md). It covers the complete
delivery through the first production release and the post-V1 trajectory. Its
validated product decisions override the original target design, and its `Next
Ticket` section identifies the default implementation ticket.

## Ticket Execution Workflow

- Read the `Next Ticket` section at the top of `docs/plan.md` before starting
  roadmap implementation work. It is the authoritative default ticket unless the
  user explicitly selects another ticket.
- Work on one ticket at a time and use its acceptance criteria together with the
  roadmap definition of done as the completion checklist.
- A ticket is finished only after its implementation, relevant tests, required
  validation, and documentation updates are complete. Do not advance the roadmap
  pointer for partial work or failing checks.
- When a ticket is finished, update the `Next Ticket` section in `docs/plan.md` to
  the next ready, unfinished ticket in the recommended execution sequence. Keep
  the ticket title as a direct Markdown link to its detailed heading in the linked roadmap lot,
  then continue with that ticket without waiting for another prompt when the
  user's request authorizes continued roadmap implementation.
- If the next sequential ticket is blocked by a dependency or decision gate,
  record that blocker in `Next Ticket` and select the next ready ticket only when
  the roadmap permits parallel work. Ask the user when a product decision is
  required.
- If the user scoped the request to a single ticket, still update `Next Ticket`
  after completion, but stop after reporting the handoff.

## Read only the context needed

Start with [README.md](README.md), then follow the relevant branch. Do not preload
all documentation. [Current status](docs/product/status.md) distinguishes code,
placeholders, and ticket completion. Detailed behavior belongs in the linked
feature documentation, not in this operating guide.

- [Architecture and code boundaries](docs/architecture.md)
- [Backend](docs/backend/README.md) and [frontend](docs/frontend/README.md)
- [Gameplay rules](docs/product/gameplay.md) and [session internals](docs/backend/sessions.md)
- [Local setup](docs/development/README.md) and [testing](docs/testing.md)
- [Operations](docs/operations/README.md)

## Required Validation

Run checks relevant to every changed area. Before a full application handoff:

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

Karma on this workstation may require `CHROME_BIN=/snap/bin/chromium`.
PostgreSQL and MinIO integration tests require Docker. See [testing](docs/testing.md)
for test boundaries. Documentation-only changes require link, size, and content
validation; they do not require rebuilding the application.

## Coding Conventions

- Use English for code, tests, comments, commits, and technical documentation.
- Follow TDD where practical and add tests for behavior changes.
- Keep controllers thin; business behavior belongs in services.
- Persist durable domain data through JPA repositories.
- Keep live game state in memory as described by the target architecture unless a
  design change is explicitly agreed.
- Use DTOs at HTTP and WebSocket boundaries; do not expose JPA entities.
- Add schema changes as new Flyway migrations. Never edit an applied migration.
- Keep the OpenAPI file and architecture documentation synchronized with delivered
  endpoints and behavior.
- Match frontend work to the corresponding reference screen in
  `docs/Blindtest/Blindtest Views.html` and its JSX sources.
- Preserve standalone Angular components and lazy feature routes.
- Never commit `.env`, credentials, generated build output, media files, or
  dependency directories.

## Documentation maintenance

- Keep agent operating instructions in this file.
- Aim for about 200 lines per Markdown page; up to 300 is reasonable for a cohesive
  topic. Split growing documents by topic and link parent and child pages.
- Keep the root overview short. Explain each fact in one primary location and link
  to it elsewhere; use source links for implementation details.
- Update the relevant current-system page when behavior changes. Distinguish
  observed code from completed roadmap acceptance and historical target design.
- Preserve ticket criteria and accepted decisions when moving documentation; fix
  relative links and anchors with the move.
- Machine-readable OpenAPI and generated HTML/JSX references retain their native
  structure; the prose size guideline is not a source-code line limit.
- Track known limitations in [current status](docs/product/status.md).
