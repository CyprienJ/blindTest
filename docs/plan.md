# Blindtest Product Delivery Roadmap

Status: delivery authority; detailed sections are linked below

Structure reviewed: 2026-09-11; ticket statuses preserved

Target: first public production release, followed by multiplayer and social features

Deployment target: one VPS, Docker Compose, existing host reverse proxy

## Next Ticket

**[UI-02 — Implement game configuration](roadmap/lot-6-frontend.md#ui-02--implement-game-configuration)**

Status: ready

UI-01 is complete: the authenticated shell provides play and public playlist
navigation, reactive administrator access, safe authentication redirects, and
sign-out. Refresh, role, logout failure, and layout checks passed alongside the
required application validation. UI-02 is the next ready ticket; existing game
configuration code must be reviewed against its acceptance criteria and ADR-0008.
TEST-06 remains an outstanding CI prerequisite before automated beta deployment
resumes, and MEDIA-08 stays deferred until real legacy files require migration.

This is the authoritative next ticket to implement. When it meets the
[definition of done](roadmap/completion-rules.md#definition-of-done), replace it with the next ready,
unfinished ticket from the [recommended execution sequence](roadmap/execution-sequence.md#19-first-recommended-execution-sequence).
Do not advance this pointer while acceptance criteria or required validation are
still incomplete. If the next ticket is blocked, record the blocker here and move
to the next ready ticket only when the roadmap dependencies allow it.

This document supersedes the previous testing-and-storage-only plan. It organizes
delivery into lots and implementation tickets rather than time-boxed sprints.
Testing, S3-compatible storage, security, and operations are integrated into the
product roadmap.

`docs/architecture.md` remains useful target documentation, but it is not an
unquestionable source of truth. Any contradiction between that document and the
validated decisions below must be resolved through an explicit architecture or
product decision before implementation.


The [code inventory](product/status.md#roadmap-discrepancy) records frontend work
already present beyond this pointer. Its acceptance has not been revalidated here.

## Roadmap tree

Read only the relevant lot and its dependencies. Ticket criteria and decisions
were moved intact; this documentation reorganization does not complete tickets.

- [1. Validated Product Decisions](roadmap/product-decisions.md)
- [2. Explicitly Deferred Features](roadmap/deferred-features.md)
- [3. First-Release User Journeys](roadmap/user-journeys.md)
- [4. Target Architecture](roadmap/target-architecture.md)
- [5. Decision Gates](roadmap/decision-gates.md)
- [6. Delivery Overview](roadmap/delivery-overview.md)
- [7. Lot 0 — Repository, Security, and Decision Baseline](roadmap/lot-0-foundation.md)
- [8. Lot 1 — Automated Quality Foundation](roadmap/lot-1-quality.md)
- [9. Lot 2 — S3/MinIO Media Foundation](roadmap/lot-2-media.md)
- [10. Lot 3 — Catalogue and Playlist Administration](roadmap/lot-3-catalogue.md)
- [11. Lot 4 — Pure Solo-Game Domain](roadmap/lot-4-game-domain.md)
- [12. Lot 5 — Sessions, WebSocket, and History Backend](roadmap/lot-5-sessions-history.md)
- [13. Lot 6 — Solo-Game Frontend and Summary](roadmap/lot-6-frontend.md)
- [14. Lot 7 — Full-Stack Validation and Hardening](roadmap/lot-7-hardening.md)
- [15. Lot 8 — VPS Operations and Deployment Readiness](roadmap/lot-8-operations.md)
- [16. Lot 9 — Beta Validation and Production Launch](roadmap/lot-9-release.md)
- [17. Post-V1 Roadmap](roadmap/post-v1.md)
- [18. Ticket Readiness and Completion Rules](roadmap/completion-rules.md)
- [19. First Recommended Execution Sequence](roadmap/execution-sequence.md)
- [20. Reference Documentation](roadmap/references.md)

[Up: Project overview](../README.md) · [Code status](product/status.md)
