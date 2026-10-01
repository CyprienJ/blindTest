# Media Operations Runbook

[Up: Operations](operations/README.md) · [Import implementation](backend/catalogue.md)

## Import resource limits

The backend image runs as the unprivileged `blindtest` user and contains pinned,
build-smoke-tested `yt-dlp` and FFmpeg binaries. The import workspace
(`APP_IMPORT_TEMP_DIR`) is the disk-backed `imports` Compose volume at
`/var/lib/blindtest/imports`, not the RAM-backed `/tmp` tmpfs, because staged
uploads (up to 25 MiB each) must survive restarts for job recovery. Each import is
constrained by:

- `APP_IMPORT_TIMEOUT` (10 minutes by default);
- `APP_IMPORT_MAX_SIZE_BYTES` (25 MiB by default), enforced by both the command
  and live temporary-workspace monitoring;
- a single FFmpeg worker thread;
- `BACKEND_CPU_LIMIT` (1.0 CPU by default) on the Compose backend service.

When an import exceeds its time or size limit, the application terminates the
process and its descendants, removes the per-job temporary directory, and marks
the job `FAILED`. Check the job error and backend logs by job UUID. Raise a limit
only after confirming the source is legitimate and the host has sufficient CPU
and temporary storage; retrying the failed job remains safe.

Uploads are additionally bounded before a job exists: Nginx accepts at most
26 MiB on the upload path, Spring multipart enforces `APP_IMPORT_MAX_SIZE_BYTES`
per file, and `ffprobe` must identify a supported audio file of at most 15
minutes within 15 seconds. A failed transcode logs the last non-progress FFmpeg
line as `ffmpeg=...` next to the job UUID.

For a provider failure, the log includes the last `yt-dlp` `ERROR:` line,
truncated to 500 characters with HTTP(S) URLs removed. The persisted job and
WebSocket event retain only the stable client-safe message. Check the installed
version and the failed job without changing container state:

```bash
docker compose -p blindtest-prod exec backend yt-dlp --version
docker compose -p blindtest-prod logs backend | grep '<job-uuid>'
```

YouTube may require Proof of Origin tokens for downloads from datacenter IPs,
and can block a flagged server IP entirely. When that happens, import the track
by uploading its audio file instead.
Treat the logged provider error as the source of truth before adding cookies or
a token-provider plugin; both introduce credentials and ongoing rotation work.

For `Sign in to confirm you're not a bot`, export only `youtube.com` cookies from
a fresh private browser session in Netscape format. Close that private session
after export and do not reopen it, because YouTube rotates cookies used by an
open browser session. Encode the file without line wrapping and store the result
as the production GitHub environment secret `PROD_YOUTUBE_COOKIES_B64`:

```bash
base64 -w 0 youtube-cookies.txt
```

The deployment workflow decodes it on the VPS, changes ownership to the numeric
user ID read from the deployed backend image, and sets mode `400`. Compose mounts
the containing directory read-only, and the backend passes the configured file
to `yt-dlp`; cookie contents never enter `.env`, the image, or application logs.
Use a dedicated YouTube account because automated access can cause account
restriction. Replace the secret and redeploy when YouTube rotates the session.

## MinIO images

MinIO Inc. no longer publishes official images to Docker Hub or Quay. Compose,
the E2E stack, and `MinioMediaStorageIntegrationTest` use the community-maintained
`pgsty/minio` and `pgsty/mc` fork with pinned `RELEASE.*` tags; upgrade the two
tags together and rerun the MinIO integration test. The switch from the 2025
official server to the 2026 fork keeps the on-disk format, but take a MinIO
backup before the first deployment that recreates the `minio` container.

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
report compares every non-null track audio and cover object reference with a
recursive bucket listing. It reports:

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
