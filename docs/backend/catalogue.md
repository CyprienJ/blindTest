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
Tracks also carry an administrator difficulty from 1 (easiest) to 5 stars and
optional Deezer, YouTube, Spotify, Qobuz, Tidal, and SoundCloud links, each
validated against its platform domain. The import review requires a difficulty.
Public track responses expose the difficulty so players can filter game levels.
Streaming links and the stored cover are snapshotted with selected game tracks and
shown to players on reveal; catalogue responses keep them administrator-only.

Publication requires primary metadata plus either a legacy audio URL or a complete
private-object key/checksum/positive-size tuple. The excerpt window lasts 5 to 60
seconds; a nonzero offset requires known duration with the whole window remaining.
Pending previews require a completed import; published and rejected tracks with
private media can be previewed from the track editor. Playlist membership only accepts
published tracks, prevents duplicates, and keeps contiguous zero-based ordering.
Rejection or deletion removes track membership and compacts positions.
The administrator track list orders tracks newest first with an ID tie-break, so
pages are stable, and accepts category (ignoring case), decade, difficulty, and
`excludePlaylistId` filters; `GET /api/admin/tracks/categories` lists the
distinct categories for filter menus.

## Cover art

Administrators choose a track cover in the import review or the track editor.
`POST /api/admin/cover-suggestions` searches Deezer, iTunes, and MusicBrainz with
the Cover Art Archive in parallel (no API keys) and returns up to six candidates.
Each provider first contributes up to two covers in that priority order, then
remaining places are filled in the same order; an album or image is offered once
and exact album/artist matches rank first. A failing or slow provider only
reduces the choice. MusicBrainz searches are spaced about one second apart, as its
usage policy requires, and identify the application in the `User-Agent`.

Candidates live 30 minutes in a bounded in-memory registry; clients see only a
random candidate ID, so the server downloads images it found itself and never a
client-supplied URL. Downloads follow redirects manually, stay on HTTPS cover CDNs
(`dzcdn.net`, `mzstatic.com`, `coverartarchive.org`, `archive.org`), and are
capped at 5 MB. Thumbnails are proxied through the backend. An uploaded JPEG, PNG,
or WebP image (detected from its content, 5 MB at most) becomes a candidate too.

Saving a track with `coverCandidateId` decodes the image with the FFmpeg demuxer of
its detected format only, writes a metadata-free JPEG of at most 600x600 to
`covers/{coverId}.jpg`, and records the cover ID and source on the track; the old
cover is queued for deletion, and a rolled-back update deletes the new object.
`removeCover` clears it. `GET /api/covers/{coverId}` serves covers anonymously so
`<img>` can load them: the random ID changes with every cover and reaches players
only in a round reveal, so the response is public and immutable. The legacy
free-text `imageUrl` (the YouTube thumbnail) is kept but no longer edited.
`app.covers.suggestions-enabled=false` disables provider searches; uploads still work.

## Import lifecycle

Imports have two sources recorded on the job (`source`): an uploaded audio file
(`UPLOAD`, the reliable path) and a YouTube URL (`YOUTUBE`, best effort because
YouTube blocks many datacenter IPs as bots).

1. An administrator either submits a single-video HTTPS YouTube watch,
   short-link, or Shorts URL (equivalent active video imports are rejected), or
   uploads one audio file with optional title and artist.
2. For an upload, the request is staged under `<app.import.temp-dir>/uploads/`
   and inspected with `ffprobe` before anything is persisted: an allow-listed
   container and first-audio-stream codec, a known duration of at most 15
   minutes, and title/artist from the form or else from embedded tags. Rejections
   use `IMPORT_FILE_UNSUPPORTED`, `IMPORT_FILE_TOO_LARGE`, `IMPORT_AUDIO_TOO_LONG`,
   or `IMPORT_METADATA_REQUIRED`. Client file names are display-only.
3. A durable import job is created; asynchronous execution starts after commit.
   An upload job moves its staged file to `uploads/<jobId>.upload`, which startup
   recovery reuses; a missing staged file fails the job with a request to upload
   again, and the staged file is deleted when the job ends.
4. The worker produces MP3 audio in an isolated temporary workspace under time,
   size, thread, and CPU limits: `yt-dlp` extracts bounded metadata and downloads
   a YouTube source, or FFmpeg re-encodes an upload's first audio stream to
   160 kbit/s MP3 with source metadata dropped.
5. The backend validates audio, generates a checksum and at most 800 normalized
   waveform peaks, then uploads a content-addressed object to private storage.
6. Object existence is confirmed before completion. Temporary files are cleaned
   up; errors exposed to clients are sanitized.
7. The administrator previews the completed unpublished import, edits metadata,
   difficulty, streaming links, and aliases, and publishes or rejects it
   transactionally and idempotently.

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

Track deletion, media replacement, and cover replacement enqueue old objects in a
transactional outbox. A scheduled worker retries failures. Read-only reconciliation reports
missing and orphaned objects using key fingerprints. See the
[media operations runbook](../media-operations.md) for limits, metrics, and recovery.

## Source entry points

- [CatalogueService](../../backend/src/main/java/com/blindtest/service/CatalogueService.java)
- [AdminTrackService](../../backend/src/main/java/com/blindtest/service/AdminTrackService.java)
  and [AdminPlaylistService](../../backend/src/main/java/com/blindtest/service/AdminPlaylistService.java)
- [ImportJobRunner](../../backend/src/main/java/com/blindtest/service/ImportJobRunner.java)
  with [YtDlpWrapper](../../backend/src/main/java/com/blindtest/infra/YtDlpWrapper.java),
  [AudioProbe](../../backend/src/main/java/com/blindtest/infra/AudioProbe.java), and
  [AudioTranscoder](../../backend/src/main/java/com/blindtest/infra/AudioTranscoder.java)
- [MediaStorage](../../backend/src/main/java/com/blindtest/service/port/MediaStorage.java)
- [TrackMediaService](../../backend/src/main/java/com/blindtest/service/TrackMediaService.java)
- [Administrator UI](../../frontend/src/app/features/admin)
