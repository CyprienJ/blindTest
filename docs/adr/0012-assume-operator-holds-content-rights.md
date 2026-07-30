# ADR-0012: Treat content rights as an operator-owned prerequisite for now

- Status: Accepted
- Date: 2026-07-30
- Deciders: Product owner and engineering
- Related tickets: DG-06, REL-03
- Supersedes: N/A
- Superseded by: N/A

## Context

Blindtest imports audio from administrator-supplied YouTube URLs. The roadmap
asked whether the product must verify permitted sources, capture an administrator
attestation, display a rights notice, or implement a complaint and removal
workflow before production import is enabled.

For the current delivery phase, the product owner has chosen to proceed on the
assumption that the operator has already obtained all rights and permissions
required for the imported content.

## Decision

Content-rights verification remains outside the application for the current
release:

- the operator is responsible for ensuring that every imported and published
  track may be used;
- the application does not verify ownership, licences, source-channel status, or
  platform terms;
- the application does not add a per-import attestation, evidence upload,
  source whitelist, public complaint endpoint, or dedicated takedown workflow in
  the current scope;
- YouTube import remains available only to authenticated administrators;
- media remains private and is exposed only through authorized, short-lived
  playback URLs as defined by ADR-0005.

Before deploying a public environment, the operator must explicitly confirm
outside the application that the rights assumption is valid for the catalogue
being deployed. This ADR records a product and operational assumption; it is not
a legal determination.

If that assumption becomes false or uncertain, administrators can immediately
remove affected tracks using the existing catalogue deletion and media-cleanup
workflow. A formal notice, attestation, source restriction, or complaint process
requires a superseding decision.

## Consequences

- No additional rights-related UI or backend workflow delays the current release.
- The application cannot demonstrate or enforce that an imported source is
  authorized.
- Rights and source-policy risk is accepted by the operator rather than mitigated
  by product controls.
- Public launch depends on an external operator confirmation that is not stored by
  Blindtest.
- A change in audience, catalogue source, operator, jurisdiction, or commercial
  use must trigger review of this decision.

## Alternatives Considered

- **Administrator attestation and removal workflow:** adds traceability but was
  intentionally deferred.
- **Whitelisted licensed sources only:** provides stronger control but requires
  catalogue and evidence management.
- **Disable YouTube import in production:** minimizes import-policy exposure but
  removes the planned administration workflow.

## Follow-up

- Document the external rights assumption in the release checklist and operator
  runbook.
- Keep administrator authorization and private-media controls covered by tests.
- Revisit this ADR before public launch if the operator cannot make the required
  confirmation.

## References

- [REL-03](../plan.md#rel-03--approve-contentimport-policy)
- [Decision gates](../plan.md#5-decision-gates)
- [ADR-0005](0005-private-s3-media-and-presigned-playback.md)
