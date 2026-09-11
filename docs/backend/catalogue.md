# Catalogue, imports, and private media

[Up: Backend](README.md) · [Product journey](../product/README.md)

## Catalogue and publication

Public reads expose only published tracks and public playlists, with bounded
pagination/search and deterministic ordering. Catalogue responses use short-lived
public caching: `max-age=60, stale-while-revalidate=300`.

A track has a canonical primary title, display artist, and optional album,
composer, work, category, decade, duration, and image metadata. Administrators
manage alternative answers for title, artist, composer, and album. An internal
normalized key prevents duplicate aliases; gameplay matching uses its own rules.

Publication requires primary metadata plus either a legacy audio URL or a complete
private-object key/checksum/positive-size tuple. A nonzero excerpt offset requires
known duration and at least 60 seconds remaining. Playlist membership only accepts
published tracks, prevents duplicates, and keeps contiguous zero-based ordering.
Rejection or deletion removes track membership and compacts positions.

## Import lifecycle

1. An administrator submits a single-video HTTPS YouTube watch, short-link, or
   Shorts URL. Equivalent active video imports are rejected.
2. A durable import job is created; asynchronous execution starts after commit.
3. `yt-dlp` extracts bounded metadata and downloads into an isolated temporary
   workspace. FFmpeg produces MP3 audio under time, size, thread, and CPU limits.
4. The backend validates audio, generates a checksum and at most 800 normalized
   waveform peaks, then uploads a content-addressed object to private storage.
5. Object existence is confirmed before completion. Temporary files are cleaned
   up; errors exposed to clients are sanitized.
6. The administrator previews the completed unpublished import, edits metadata
   and aliases, and publishes or rejects it transactionally and idempotently.

STOMP user messages provide live progress. Polling retains progress and lifecycle
state across browser reloads; startup recovery resolves interrupted active jobs.
Retention policy is defined in [ADR-0011](../adr/0011-data-and-rejected-media-retention.md);
do not assume that every planned retention worker is implemented.

## Media access and deletion

The provider-neutral `MediaStorage` port supports upload, existence, listing,
pre-signed GET, and deletion. Its production MinIO adapter keeps SDK details below
the port and supports distinct internal and browser-facing endpoints.

Administrator preview requires a pending track, completed import, finalized media
metadata, and an existing object. Active-round URLs require participant
membership and cover the remaining round plus the reconnect buffer. Object keys
are excluded from DTOs; signed URLs are temporary bearer access to media.

Track deletion and media replacement enqueue old objects in a transactional
outbox. A scheduled worker retries failures. Read-only reconciliation reports
missing and orphaned objects using key fingerprints. See the
[media operations runbook](../media-operations.md) for limits, metrics, and recovery.

## Source entry points

- [CatalogueService](../../backend/src/main/java/com/blindtest/service/CatalogueService.java)
- [AdminTrackService](../../backend/src/main/java/com/blindtest/service/AdminTrackService.java)
  and [AdminPlaylistService](../../backend/src/main/java/com/blindtest/service/AdminPlaylistService.java)
- [ImportJobRunner](../../backend/src/main/java/com/blindtest/service/ImportJobRunner.java)
  and [YtDlpWrapper](../../backend/src/main/java/com/blindtest/infra/YtDlpWrapper.java)
- [MediaStorage](../../backend/src/main/java/com/blindtest/service/port/MediaStorage.java)
- [TrackMediaService](../../backend/src/main/java/com/blindtest/service/TrackMediaService.java)
- [Administrator UI](../../frontend/src/app/features/admin)
