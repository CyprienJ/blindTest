# 18. Ticket Readiness and Completion Rules

[Up: Roadmap](../plan.md)

### Definition of ready

A ticket is ready when:

- blocking decision gates are approved;
- user-visible behavior and errors are stated;
- API/event/schema impact is identified;
- security and authorization rules are known;
- test level and fixtures are defined;
- migration and rollback impact is known;
- dependencies are complete or scheduled.

### Definition of done

A ticket is done when:

- behavior and failure paths are implemented;
- relevant unit/integration/Playwright tests pass;
- Checkstyle, lint, build, migrations, and required CI pass;
- API/events/schema documentation is updated;
- secrets and personal data are not logged;
- metrics/runbooks are updated for operational behavior;
- migrations are backward-compatible for the deployment sequence;
- acceptance criteria are demonstrated.

### Rules for future implementation

- Do not implement a blocked decision silently; ask the product owner.
- Do not mark placeholder routes/components as complete.
- Do not generate long-lived public media URLs.
- Do not couple the game domain to STOMP, JPA, MinIO, or the system clock.
- Do not introduce Redis, microservices, or Kubernetes before an approved need.
- Do not deploy if required CI checks fail.
- Do not remove legacy media until migration verification and rollback window end.
