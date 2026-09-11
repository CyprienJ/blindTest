# ADR-0013: Rely on the VPS daily backup system for now

- Status: Accepted
- Date: 2026-07-30
- Deciders: Product owner and engineering
- Related tickets: DG-07, OPS-05, OPS-06, REL-04
- Supersedes: N/A
- Superseded by: N/A

## Context

The production target is one VPS containing the application, PostgreSQL, and
MinIO data volumes. The roadmap originally requested explicit recovery objectives
and a separate off-host backup destination for PostgreSQL and MinIO.

The selected VPS environment already performs daily infrastructure-level backups.
For the current phase, the product owner prefers to rely on that existing system
instead of operating a second application-managed backup destination.

## Decision

Use the VPS provider's daily backup system as the sole backup mechanism for the
current phase:

- the nominal recovery-point objective is 24 hours, based on one backup per day;
- there is no guaranteed recovery-time objective; restoration is best effort and
  depends on the provider and operator procedure;
- Blindtest does not initially create an additional PostgreSQL dump archive,
  MinIO replication target, or independent S3 backup;
- OPS-05 and OPS-06 must verify and document that the provider backup actually
  includes the PostgreSQL and MinIO Docker volumes;
- a restore rehearsal must recover both database and media on an isolated VPS or
  clone before public launch;
- application configuration and the credentials needed to decrypt or operate a
  restored environment must be recoverable through the approved secret-management
  process, not solely from memory.

If the provider backup does not contain both durable volumes, cannot be restored,
or takes an unacceptable amount of time during rehearsal, this decision must be
superseded before launch with an independent off-host backup design.

## Consequences

- No second backup service or credentials are required immediately.
- Up to approximately 24 hours of data may be lost after a failure.
- Recovery time is unknown until a rehearsal is measured.
- Backup and primary infrastructure may share provider, account, regional, or
  administrative failure modes.
- Database and object storage are restored at infrastructure granularity rather
  than through independently optimized recovery procedures.
- The system cannot claim off-provider or independently controlled backup
  protection under this decision.

## Alternatives Considered

- **Nightly encrypted application backups to external S3:** provides independent
  protection and a defined recovery process but was deferred.
- **Continuous PostgreSQL archive and media replication:** improves recovery
  objectives at substantially greater operational complexity.
- **Weekly manual copies:** offers weaker recovery than the existing daily VPS
  system.

## Follow-up

- Record the provider's actual schedule, retention, scope, and restore procedure
  without storing account secrets in the repository.
- Rehearse and time a full PostgreSQL and MinIO restore in OPS-05, OPS-06, and
  REL-04.
- Revisit independent backups before launch if the rehearsal fails, and after
  material growth, provider migration, or stricter availability requirements.

## References

- [Decision gates](../roadmap/decision-gates.md#5-decision-gates)
- [OPS-05](../roadmap/lot-8-operations.md#ops-05--postgresql-backup-and-restore)
- [OPS-06](../roadmap/lot-8-operations.md#ops-06--minio-backup-and-restore)
