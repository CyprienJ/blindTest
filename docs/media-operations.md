# Media Operations Runbook

## Deletion queue

Track deletion and media replacement add the obsolete object key to
`media_deletion_outbox` in the same PostgreSQL transaction as the track change.
The worker polls up to 25 due rows every `MEDIA_DELETION_POLL_DELAY_MS` (30 seconds
by default). A failed MinIO call retains the row and retries with exponential
backoff from 30 seconds up to 24 hours. `last_error` stores only the exception
class, never provider messages, URLs, or credentials.

Use the administrator-only Actuator metrics to alert on queue growth and failures:

- `blindtest.media.deletion.pending`;
- `blindtest.media.deletion.succeeded`;
- `blindtest.media.deletion.failed`.

A steadily growing pending gauge or repeated failure counter increase means the
operator should check MinIO reachability, bucket permissions, and application
logs using the deletion task UUID. Do not manually remove outbox rows until object
absence has been verified; leaving a row is safe because deletion is idempotent.

## Reconciliation

An administrator can call `GET /api/admin/media/reconciliation`. The read-only
report compares every non-null track object reference with a recursive bucket
listing. It reports:

- missing objects by track UUID and a 16-character SHA-256 key fingerprint;
- orphaned objects by the same fingerprint;
- aggregate missing and orphaned counts.

Objects already queued for deletion are not classified as orphans. The latest
counts are also available as `blindtest.media.reconciliation.missing` and
`blindtest.media.reconciliation.orphaned`. Run reconciliation after storage
incidents, failed imports, restores, and media migrations. The application never
returns raw internal object keys from this endpoint; an operator with direct
database or bucket access can hash candidate keys to correlate a fingerprint.

For a missing object, keep the database row and restore or re-import its media.
For an orphan, verify the fingerprint and confirm that no restoration or migration
is active before scheduling or performing deletion. Reconciliation itself never
mutates the database or bucket.
