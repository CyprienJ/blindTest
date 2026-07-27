# Architecture Decision Records

Architecture Decision Records (ADRs) capture decisions that materially affect the
structure, operation, security, or evolution of Blindtest. The delivery roadmap in
[`../plan.md`](../plan.md) remains authoritative for product scope and sequencing;
ADRs explain the technical choices made while delivering it.

## Create an ADR

1. Copy [`0000-template.md`](0000-template.md).
2. Assign the next four-digit sequence number and a short kebab-case title, for
   example `0001-modular-monolith.md`.
3. Set the status to `Proposed` while the decision is under review.
4. Record the decision, its trade-offs, and rejected alternatives before dependent
   implementation begins.
5. Change the status to `Accepted` when approved. Do not rewrite an accepted
   decision to change its meaning; supersede it with a new ADR and link both.

Allowed statuses are `Proposed`, `Accepted`, `Rejected`, `Deprecated`, and
`Superseded by ADR-NNNN`.

## Index

No architecture decisions have been accepted yet. FND-06 in the roadmap will add
the first project decisions.
