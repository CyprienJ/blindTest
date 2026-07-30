# ADR-0005: Store media privately in S3 and use pre-signed playback URLs

- Status: Accepted
- Date: 2026-07-27
- Deciders: Product owner and engineering
- Related tickets: FND-06, MEDIA-01 through MEDIA-09
- Supersedes: N/A
- Superseded by: N/A

## Context

MP3 files are currently placed in a shared local volume and served from public
`/media` paths. That couples durable media to one host, bypasses per-request
authorization, and makes moving the application or storage difficult. Imported
audio and administrator previews must not become permanently public URLs.

The initial deployment can operate MinIO on the VPS, but business code should also
work with another S3-compatible service.

## Decision

Durable audio will be stored in a private S3-compatible bucket through a
`MediaStorage` port. MinIO is the initial adapter and deployment. PostgreSQL stores
an opaque, immutable object key and media metadata, never a pre-signed URL or
storage credential.

After authorizing an administrator preview or active game round, the backend
generates a short-lived pre-signed GET URL. Its lifetime covers the round plus a
bounded loading/reconnect margin. URLs are treated as bearer credentials: they are
excluded from logs, public catalogue responses, history snapshots, analytics, and
persistent browser storage. Bucket policy denies anonymous listing and reads.
Browser playback must support `GET`, `HEAD`, byte ranges, and approved-origin CORS.

Imports use isolated temporary files, validate output, upload it under a generated
key, verify the object, then commit the key to PostgreSQL and remove temporary
data. Cleanup and reconciliation handle failures and orphan objects.

## Consequences

- Media durability and movement are decoupled from the application container and
  host filesystem.
- Authorization gates URL issuance, while storage serves audio efficiently.
- Anyone possessing an unexpired URL can use it; short TTLs, TLS, safe referrer
  policy, and log redaction limit exposure but cannot revoke an individual URL.
- Browser CORS, public endpoint routing, clock synchronization, backup, lifecycle,
  and orphan reconciliation become operational responsibilities.
- Switching S3-compatible providers is configuration-only at the business layer,
  subject to adapter compatibility tests.

Migration keeps legacy media readable until object upload, checksum verification,
database cutover, rollback window, and reconciliation have succeeded.

## Alternatives Considered

- **Continue serving a Docker volume through Nginx:** rejected because it is
  host-coupled and provides no per-play authorization.
- **Make the bucket public:** rejected because object keys would become durable
  public access credentials.
- **Proxy every audio byte through Spring Boot:** rejected because it consumes
  application capacity and duplicates storage range/streaming behavior.
- **Store audio in PostgreSQL:** rejected because large binary streaming and backup
  growth do not fit the relational workload.

## Follow-up

- The storage port, MinIO adapter, private bucket bootstrap, CORS, and authorized
  administrator preview URL endpoint are implemented.
- Complete real-MinIO expiry/range playback tests and the remaining URL-leakage
  threat-model checks.
- Add backups, reconciliation, migration, and provider compatibility checks.

## References

- [Roadmap media model](../plan.md#45-media-security-model)
- [Component diagram](../diagrams/01-composants.puml)
- [Import sequence diagram](../diagrams/04-sequences.puml)
