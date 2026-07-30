# ADR-0010: Provision the first administrator with an operator command

- Status: Accepted
- Date: 2026-07-30
- Deciders: Product owner and engineering
- Related tickets: DG-04, FND-05
- Supersedes: N/A
- Superseded by: N/A

## Context

Public registration must never allow a user to select the `ADMIN` role. The first
production deployment still needs an administrator before catalogue operations
can be performed. Direct SQL is fragile and difficult to validate, while a public
bootstrap endpoint or startup credentials would add a long-lived attack surface
or reusable secrets.

The existing registration and email-verification flow already establishes an
account, password, and verified email address.

## Decision

Provision the first production administrator by promoting an existing verified
user through a one-shot operator command implemented by the backend:

- the intended administrator first registers normally and verifies the account's
  email address;
- an operator runs the command interactively inside a backend container or an
  equivalent one-shot container using the production configuration;
- the command identifies the account by email supplied through interactive input,
  not a public HTTP endpoint;
- it refuses unverified accounts;
- it succeeds only when the database contains no other administrator;
- repeating the command for the already-promoted sole administrator succeeds
  without changing state;
- if a different administrator already exists, the command refuses the request;
- the command never accepts or changes a password and never logs the email
  address, credentials, tokens, or database connection secrets.

The promotion and zero-administrator check are one concurrency-safe database
operation. Successful and refused attempts emit a safe operator result containing
only a user identifier and outcome where appropriate.

Normal registration remains hard-coded to create `USER` accounts. General
administrator role management remains deferred beyond the first release.

## Consequences

- No default administrator credentials exist.
- The first administrator benefits from the normal password and verified-email
  controls.
- Provisioning requires temporary command access to the production backend
  environment.
- Deployment documentation must include registration, verification, promotion,
  login verification, and recovery steps.
- The command cannot be reused to create additional administrators.
- Concurrency tests must prove that simultaneous attempts cannot promote two
  accounts.

## Alternatives Considered

- **Startup environment variables:** automate creation but introduce reusable
  credentials and ambiguous restart behavior.
- **One-time HTTP endpoint:** easier to call remotely but creates a critical
  bootstrap surface requiring token distribution and reliable shutdown.
- **Manual SQL role update:** bypasses application validation and the roadmap's
  safety acceptance criterion.
- **Ship a fixed administrator account:** rejected because shared or default
  credentials are unsafe.

## Follow-up

- Implement the command, transactional guard, and deterministic tests in FND-05.
- Document the exact production and recovery commands in the operator runbook.
- Verify normal registration cannot bind or deserialize an administrator role.
- Add a deployment acceptance check proving that the promoted account can log in
  and access an administrator-only endpoint.

## References

- [FND-05](../plan.md#fnd-05--define-and-implement-first-admin-provisioning)
- [Decision gates](../plan.md#5-decision-gates)
