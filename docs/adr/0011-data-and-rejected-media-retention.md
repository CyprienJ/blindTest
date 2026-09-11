# ADR-0011: Apply balanced retention to histories, imports, and rejected media

- Status: Accepted
- Date: 2026-07-30
- Deciders: Product owner and engineering
- Related tickets: DG-05, CAT-04, HIST-01, OPS-08
- Supersedes: N/A
- Superseded by: N/A

## Context

Completed game histories, terminal import jobs, sanitized import errors, rejected
track records, and rejected media all grow over time. Histories provide durable
results, while import records support operational diagnosis and a short recovery
window after an administrator mistake. Keeping every record and media object
indefinitely would increase storage, privacy exposure, and cleanup uncertainty.

Temporary import files and orphan handling already have immediate or retry-based
cleanup paths. Published catalogue media is durable product content and is outside
this retention decision.

## Decision

Apply the following maximum retention periods:

- completed game histories: 24 months after game completion;
- terminal import jobs and their sanitized errors: 30 days after the job's
  `completedAt` timestamp;
- rejected track metadata: 30 days after rejection;
- rejected media objects: 7 days after rejection.

At the end of each period, scheduled cleanup permanently removes the applicable
record or object. Rejected media deletion uses the durable media-deletion outbox
and its retry/reconciliation behavior. The rejected track keeps its media
reference until deletion is safely queued, after which the reference is cleared
or the track is removed according to the cleanup transaction.

Account erasure or another approved privacy operation may remove or anonymize
personal identifiers before the 24-month history limit. Non-personal aggregate
operational metrics may remain, but they cannot contain answers, email addresses,
display names, media keys, or other account identifiers.

Retention calculations use UTC instants and are safe to retry. Cleanup runs in
bounded batches, reports aggregate success/failure metrics, and never deletes
active import jobs or published track media.

## Consequences

- Players can refresh and revisit retained completed-game summaries for up to two
  years once history browsing exists.
- Operators retain one month of import evidence for diagnosis.
- Administrators have a seven-day media grace period after rejection, but
  restoring a rejected item still requires an explicit supported workflow.
- Cleanup needs timestamps, indexes, batch limits, retry behavior, and metrics.
- Backup copies can retain deleted data until their independent backup-retention
  windows expire; restoration procedures must reapply cleanup.

## Alternatives Considered

- **Minimal retention:** lowers storage and privacy exposure but leaves little
  time for diagnosis or recovery.
- **Indefinite or long retention:** maximizes historical availability but creates
  unbounded growth and greater privacy obligations.
- **Immediate rejected-media deletion:** saves storage fastest but removes the
  selected grace period for administrative mistakes.

## Follow-up

- Add history retention metadata and indexed completion timestamps in HIST-01.
- Implement bounded cleanup for histories, import jobs, rejected tracks, and
  rejected media in OPS-08.
- Ensure CAT-04 rejection timestamps are the authoritative retention anchors.
- Document cleanup metrics, failed deletion handling, backup interaction, and
  account-erasure behavior in the operations runbook.

## References

- [Decision gates](../roadmap/decision-gates.md#5-decision-gates)
- [HIST-01](../roadmap/lot-5-sessions-history.md#hist-01--add-durable-history-schema)
- [OPS-08](../roadmap/lot-8-operations.md#ops-08--add-retention-and-cleanup-jobs)
- [ADR-0006](0006-postgresql-history-snapshots.md)
