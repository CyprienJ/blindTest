# 9. Lot 2 — S3/MinIO Media Foundation

[Up: Roadmap](../plan.md)

### Objective

Replace the host-coupled `/media` design with private S3-compatible object storage.

Dependencies: FND-04, TEST-02, TEST-05.

### Tickets

#### MEDIA-01 — Add the media storage port and configuration

Status: completed on 2026-07-28.

Work:

- add `MediaStorage` and `StoredMedia`;
- implement typed endpoint, public endpoint, region, bucket, credentials, and TTL
  configuration;
- use the MinIO Java SDK behind the port;
- add a fake/in-memory implementation for unit tests.

Acceptance:

- business services do not import MinIO SDK types;
- switching to another compatible endpoint is configuration-only at the business
  layer.

#### MEDIA-02 — Add MinIO to local Docker Compose

Status: completed on 2026-07-30.

Work:

- pin a MinIO server image;
- add a persistent `minio_data` volume;
- add health checks;
- bind the console to localhost;
- add a one-shot `mc` initializer;
- create the private `blindtest-media` bucket;
- configure application-origin CORS for GET, HEAD, and range requests.

Acceptance:

- `docker compose up` creates a healthy MinIO and private bucket automatically;
- unauthenticated object listing/read is denied;
- the browser can use a valid pre-signed URL.

#### MEDIA-03 — Add object-key schema migration

Status: completed on 2026-07-30.

Work:

- add nullable `audio_object_key`;
- add optional checksum and byte-size fields;
- temporarily retain legacy `audio_url`;
- add entity and DTO mappings without exposing object keys publicly;
- add a migration test from the current schema.

Acceptance:

- old rows remain readable;
- new rows can reference S3 without storing a pre-signed URL;
- Flyway succeeds on empty and existing-schema fixtures.

#### MEDIA-04 — Upload finalized imports to object storage

Status: completed on 2026-07-30.

Work:

- create a per-job temporary directory;
- run the fake/real process into that directory;
- verify MP3 type, non-empty size, and configured maximum size;
- compute checksum;
- upload and confirm object existence;
- persist key/checksum/size before marking `DONE`;
- remove temporary data in `finally`;
- make retries idempotent.

Acceptance:

- a storage failure marks the job failed and never produces a false `DONE`;
- successful jobs leave no temporary file;
- retry does not create duplicate active objects.

#### MEDIA-05 — Implement authorized preview URLs

Status: completed on 2026-07-30.

Work:

- add an admin-only preview URL endpoint;
- generate a short-lived pre-signed GET URL;
- validate status and object existence;
- avoid URLs in persistent logs.

Acceptance:

- an admin can preview a completed import;
- a normal user cannot request an unpublished preview;
- expiration is tested.

#### MEDIA-06 — Add reliable deletion and reconciliation

Status: completed on 2026-07-30.

Work:

- create a media-deletion outbox table;
- write deletion tasks in the same transaction as track deletion/replacement;
- process with retry/backoff;
- add reconciliation reporting for missing and orphaned objects;
- expose safe operational metrics.

Acceptance:

- transient MinIO failure does not lose the deletion request;
- operators can identify DB/object inconsistencies.

#### MEDIA-07 — Install runtime import dependencies

Status: completed on 2026-07-30.

Work:

- install and pin `yt-dlp` and FFmpeg in the backend runtime image;
- run as a non-root user;
- define CPU/time/file-size limits;
- add image smoke assertions for both binaries.

Acceptance:

- the production image can execute both binaries;
- hung or oversized imports terminate safely.

#### MEDIA-08 — Migrate existing media

Work:

- implement dry-run, batch, single-ID, resume, checksum, and structured reporting;
- upload legacy `/media` files;
- update rows only after verification;
- retain the source volume for an approved rollback window;
- remove legacy read support only in a later deployment.

Acceptance:

- every migrated row has a verified object;
- rerunning the command is safe;
- rollback remains possible during the observation window.

#### MEDIA-09 — Add MinIO integration tests

Status: completed on 2026-07-30.

Work:

- Testcontainers or Compose tests for put, exists, pre-sign, range GET, delete,
  retry, and unavailable storage;
- browser audio seek smoke test.

Acceptance:

- storage behavior is validated against a real MinIO API, not only mocks.

### Lot exit criteria

- new imports store objects in MinIO;
- private preview works;
- database stores object keys;
- failure and deletion are recoverable;
- runtime image contains import dependencies.
