# ADR-0001: Use a modular monolith for the first release

- Status: Accepted
- Date: 2026-07-27
- Deciders: Product owner and engineering
- Related tickets: FND-06
- Supersedes: N/A
- Superseded by: N/A

## Context

Blindtest is deployed to one VPS and is maintained as one Spring Boot backend.
Authentication, catalogue management, imports, gameplay, sessions, history, and
real-time delivery have different responsibilities, but the first release does
not need independent scaling or deployment for each responsibility. Splitting
them into network services now would add deployment, observability, consistency,
and failure-handling costs before those costs solve a measured problem.

The code still needs boundaries that prevent game rules from depending on Spring,
JPA, STOMP, or a particular storage provider. Those boundaries are also the seam
for extracting a service later if operating evidence justifies it.

## Decision

The first release will remain one Spring Boot deployable organized as modules by
business capability: authentication, catalogue, importing, media, game, session,
history, realtime, and shared concerns.

Dependencies point inward from HTTP/WebSocket adapters to application services,
then domain logic, then repository, storage, clock, and event ports. Infrastructure
implements those ports. Modules communicate in-process through explicit APIs and
domain/application data, not by reaching into another module's persistence model.
Package migration may be incremental; package movement alone is not a delivery
goal.

## Consequences

- Local development, testing, transactions, and deployment remain simple.
- Cross-module calls avoid network latency and distributed failure modes.
- Module boundaries require review and tests because a runtime cannot enforce all
  dependency rules automatically.
- A defect or resource spike can affect the whole backend process.
- Modules cannot be deployed or scaled independently in V1.
- Extraction later requires an explicit API/event boundary and a data-ownership
  migration, but domain ports avoid rewriting core rules.

Rollback is the normal application rollback because this decision adds no runtime
component or data migration.

## Alternatives Considered

- **Microservices per capability:** rejected for V1 because one team and one VPS do
  not justify distributed operations and data consistency costs.
- **An unstructured layered monolith:** rejected because shared entities and
  unrestricted service calls would make later changes and extraction unsafe.
- **Separate import worker now:** deferred until import load or isolation needs are
  demonstrated; the importing boundary must permit that extraction.

## Follow-up

- Introduce or migrate packages along capability boundaries when their tickets are
  implemented.
- Keep pure game rules independent of framework and infrastructure types.
- Revisit service extraction only with measured scaling, release-isolation, or
  ownership needs.

## References

- [Roadmap target architecture](../plan.md#42-backend-module-boundaries)
- [Component diagram](../diagrams/01-composants.puml)

