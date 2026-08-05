# Blindtest — Technical & Functional Documentation

## Document Status

This document contains two deliberately separate views:

- **Current delivered architecture** describes what is present in executable code
  and migrations as reviewed on 2026-07-27.
- **Target architecture** preserves the original product design in sections 1–13.
  It contains planned and deferred features and must not be used as proof that an
  endpoint, model, screen, or behavior has been implemented.

When documents disagree, executable code and tests come first, followed by
database migrations and the validated decisions in [`plan.md`](plan.md). The plan
is the delivery authority. `swagger.yaml`, the target sections below, diagrams,
and UI references describe intent until code and tests deliver it.

## Table of Contents

- [Current Delivered Architecture](#current-delivered-architecture)
- [Target Architecture](#target-architecture)
  1. [Overview](#1-overview)
  2. [Functional Specifications](#2-functional-specifications)
  3. [Tech Stack](#3-tech-stack)
  4. [General Architecture](#4-general-architecture)
  5. [Data Model](#5-data-model)
  6. [REST API](#6-rest-api)
  7. [WebSocket / STOMP](#7-websocket--stomp)
  8. [Sequence Diagrams](#8-sequence-diagrams)
  9. [Backend Architecture (Spring Boot)](#9-backend-architecture-spring-boot)
  10. [Frontend Architecture (Angular)](#10-frontend-architecture-angular)
  11. [Audio Synchronization (Option 2)](#11-audio-synchronization-option-2)
  12. [Music Import Workflow](#12-music-import-workflow)
  13. [Security & Auth](#13-security--auth)

## Current Delivered Architecture

The repository is a modular monorepo:

```text
Browser
  └── Nginx / Angular SPA (`frontend`)
        ├── /api and /ws ──> Spring Boot (`backend`)
        │                     ├── JPA / Flyway ──> PostgreSQL
        │                     └── MediaStorage ──> MinIO
        └── /media ─────────> shared Docker volume
```

Docker Compose starts PostgreSQL, MinIO, a one-shot MinIO initializer, the backend,
and the frontend. The frontend, MinIO API, and MinIO console bind to localhost;
PostgreSQL remains internal. The initializer creates the private media bucket and
MinIO restricts API CORS to the configured application origin. The `/media` volume
remains temporarily for legacy tracks during the migration window.

### Delivered backend

- Java 21 and Spring Boot 3.2 with Spring Security, JPA, Flyway, and STOMP.
- Registration with Turnstile, email verification, login, rotating opaque refresh
  tokens, logout, and password reset.
- Public published-track and playlist catalogue endpoints with deterministic
  ordering, bounded metadata search, safe public projections, and `Cache-Control:
  public, max-age=60, stale-while-revalidate=300`.
- Administrator track review, asynchronous YouTube import jobs, per-user STOMP
  import progress, and ordered playlist management.
- Imports accept only HTTPS YouTube watch, short-link, and Shorts URLs with
  canonical 11-character video IDs. Equivalent active imports are rejected.
- Imported MP3s are validated in isolated temporary directories, checksummed,
  uploaded under content-addressed private object keys, confirmed, and cleaned up
  before jobs complete.
- External email, Turnstile, process, and import-scheduling boundaries exposed as
  injectable ports, with injected authentication time and opaque-token generation.
- Deterministic test adapters capture email, verify configured Turnstile tokens,
  execute imports synchronously, and write a small MPEG Layer III fixture without
  Resend, Cloudflare, YouTube, `yt-dlp`, or FFmpeg.
- A provider-neutral private-media port supports upload, existence checks,
  short-lived GET URLs, and deletion. Its production adapter uses the MinIO Java
  SDK with typed internal endpoint, public endpoint, region, bucket, credentials,
  and URL lifetime settings; tests can replace it with an in-memory adapter.
- PostgreSQL entities and Flyway migrations for users, refresh tokens, tracks,
  playlists, playlist membership, import jobs, and safe first-administrator
  provisioning audit records.
- A pure `model/game` boundary validates immutable playlist, requested-track-count,
  and maximum-round-duration configuration snapshots. It consumes only an immutable
  playlist-eligibility projection, rejects every reserved extension for V1, and
  exposes stable domain errors without Spring, JPA, or transport dependencies. The
  backend-authoritative limits are 5–30 tracks and 10–60 seconds in five-second
  increments, with defaults of 15 tracks and 30 seconds.
- The same pure game boundary uniformly samples eligible published playlist tracks
  without replacement and independently shuffles their play order through an
  injectable random port. Selected tracks retain detached canonical and alternative
  answers, playback metadata, and private-media object key/checksum versions; a
  cryptographic production adapter and deterministic seeded tests keep the policy
  unpredictable in production and reproducible in tests.
- Gameplay answer normalization is also pure and locale-independent. It performs
  Unicode compatibility decomposition, root-locale case folding, accent removal,
  punctuation and symbol separation, whitespace/token normalization, and optional
  removal of one common French or English leading article without emptying a
  one-token answer. The output retains immutable normalized text and tokens for the
  later matcher.
- The pure answer matcher evaluates normalized canonical values and snapshotted
  aliases in deterministic title, artist, composer, then album order. It completes
  each exact, token-boundary, and typo-tolerant tier before moving to the next, so an
  exact secondary match wins over a fuzzy title. Damerau–Levenshtein tolerance is
  exact-only through five code points, one edit through 12, two through 20, and three
  beyond 20. Results contain only an optional matched answer type; failures retain no
  canonical or alias text. A reviewed positive/negative corpus and a 10,000-match
  regression budget cover behavior and performance.
- Pure title scoring uses the authoritative server acceptance instant, round
  deadline, and configured duration. The versioned `title-linear-v1` rule awards up
  to 1,000 points with exact integer half-up rounding and a 100-point pre-deadline
  floor. Immutable per-round score state prevents duplicate awards; secondary or
  rejected answers, skips, timeouts, and deadline-or-later titles award zero. The
  arithmetic remains exact even when a duration's nanoseconds exceed `long` range.
- Pure immutable game and round state machines enforce `CREATED` through terminal
  game transitions and `PREPARING` through `COMPLETED` round transitions. Title,
  timeout, and skip resolution uses the authoritative deadline; each completed round
  either prepares the next snapshotted track or finishes the game with one ordered,
  immutable score summary. Stable domain errors reject illegal transitions, and no
  scheduler or transport dependency participates in these rules.
- Each started round derives immutable, non-secret playback metadata from its
  snapshotted track and authoritative scoring window: start, deadline, configured
  excerpt offset/end, and the minimum URL expiry. The expiry boundary covers the
  round plus a fixed 30-second reconnect buffer. Bearer URLs are generated only
  after active-session authorization and are not added to catalogue or history
  projections; object keys remain internal snapshot data.
- Participant-private secondary discoveries are held in a separate immutable ledger
  keyed by opaque game-participant IDs rather than account identities. Domain
  projections expose only the requested participant's artist, composer, and album
  categories. A new discovery discloses its category privately; `ALREADY_FOUND`,
  title, and rejected outcomes disclose no secondary category and do not mutate
  round lifecycle or score state. Title remains available to the round state machine.
- The pure game suite closes the domain lot with reviewed matching corpora, the
  10,000-match regression budget, deterministic property sweeps over normalization,
  scoring, and seeded selection, all 20 round action/state combinations, and a full
  five-round solo scenario using explicit test time. The game-test package imports
  no Spring, persistence, WebSocket, PostgreSQL, Testcontainers, or MinIO APIs.
- Immutable active-session snapshots retain an opaque session ID, owner and
  participant identities, validated configuration, game/round and private-discovery
  state, optimistic version, and creation/update/expiry timestamps. A replaceable
  `SessionStore` port exposes atomic create, read, compare-and-set, version-guarded
  removal, expiry cleanup, and safe active-count operations. The process-local
  `ConcurrentHashMap` adapter uses per-key atomic computation, rejects access after
  expiry until scheduled resource cleanup, and prevents stale concurrent commands
  from overwriting a newer snapshot.
- `POST /api/sessions` creates an independent solo session for a verified
  authenticated account. It validates the flat playlist/count/duration contract,
  selects and freezes snapshot-capable published tracks and aliases, creates the
  owner participant at version zero with a bounded initial lifetime, and returns
  native/SockJS endpoints plus public, private, and command STOMP destinations.
- Gameplay commands are handled against the active snapshot by a per-session
  serialized orchestration service. It records bounded idempotency identities,
  persists every optimistic version before publication, owns authoritative round
  deadline and four-second reveal timers through injectable clock/scheduler ports,
  and makes stale duplicate callbacks harmless. Private recovery snapshots retain
  the original playback window, reveal transition time, score, answer projection,
  and only the requesting participant's secondary discoveries. Round-start and
  playing-round recovery projections receive fresh short-lived URLs signed from
  immutable object-key snapshots after membership authorization. The storage port
  enforces a requested minimum validity covering the remaining playback window and
  reconnect buffer while preserving a longer configured TTL.
- Authenticated gameplay input is guarded before expensive work by a 300 ms
  per-player answer cooldown and a fixed one-second, 20-command session budget.
  One stable cooldown or session-limit response is emitted per active rejection
  window and further abusive frames are silently dropped, bounding answer matching,
  media signing, and feedback publication. Aggregate Micrometer counters use only
  the safe `player_cooldown` and `session_command` scope labels.
- Active session expiry is sliding: created and playing snapshots retain 30 minutes
  from their latest accepted state transition, while terminal snapshots retain five
  minutes. A one-minute scheduled pass atomically removes expired process state,
  applies the domain `ABORTED` transition to unfinished games, cancels session-owned
  deadline/reveal tasks, and clears rate-limit ledgers. Pre-signed bearer URLs are
  never retained in the store; releasing the snapshot removes private media keys
  and answers from memory. Low-cardinality counters and an active-session gauge
  expose cleanup results without identifiers. PostgreSQL history is not a cleanup
  dependency or target.
- Flyway V16 and matching JPA repositories define append-oriented completed-game,
  participant, and ordered-round snapshots. History rows retain versioned
  configuration, canonical answers and aliases, outcome/timing/score facts, and
  UTC retention metadata. Track and playlist UUIDs are copied without catalogue
  foreign keys, user references are nullable with `ON DELETE SET NULL`, and owned
  participant/round rows cascade only when their history is removed.
- Terminal solo gameplay publishes its final version and then writes the complete
  history aggregate in one transaction under the unique active-session UUID.
  Ordinary replay returns the existing history; a concurrent uniqueness conflict
  is accepted only after a fresh durable existence check. Transient database
  failures schedule one process-local retry per session with bounded exponential
  backoff while retaining both the immutable completion snapshot and active state.
  Active state is version-guarded and removed only after durable success. Aggregate
  Micrometer outcomes and identifier-free logs make failures observable without
  exposing session, account, track, media, or answer data.
- JWT authentication for HTTP requests and STOMP `CONNECT` frames.
- A non-web operator command promotes one existing verified account when no
  administrator exists. Pessimistic database locking makes concurrent attempts
  safe; repeated promotion of the sole administrator is idempotent. Normal
  registration always creates a `USER`. The production procedure is documented
  in [`admin-provisioning.md`](admin-provisioning.md).

The delivered REST surface is limited to `/api/auth/**`, `/api/tracks`,
`/api/playlists/**`, `POST /api/sessions`,
`GET /api/sessions/{sessionId}/summary`, `/api/admin/tracks/**`,
`/api/admin/tracks/import/**`, and `/api/admin/playlists/**`.

The completed-session summary reads only durable history and combines session UUID
and authenticated owner account in the repository predicate. Missing and foreign
sessions both return `404`. Its ordered round projection contains scores, outcomes,
timing, and canonical title/artist/composer/album snapshots. It deliberately omits
track IDs, provider URLs, object keys, checksums, raw guesses, and answer aliases,
and is returned with `Cache-Control: no-store`.

### REST errors and correlation

All delivered REST error paths, including Spring Security and malformed requests,
return the same JSON shape:

```json
{
  "timestamp": "2026-07-27T17:00:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/auth/login",
  "requestId": "2a4e1208-72f3-4a14-a8d5-f2e36c8f24f8",
  "fieldErrors": {
    "email": "must be a well-formed email address"
  }
}
```

`fieldErrors` is present only for field validation failures. Clients must branch
on the stable `code`, not on `message`. Exception messages, stack traces, rejected
values, tokens, and provider details are never copied into the response.

Every HTTP response carries the same ID in the `X-Request-ID` header. A valid UUID
received in that request header is reused; missing or malformed values are replaced
with a generated UUID. The ID is stored in SLF4J MDC and included in application
log lines. The complete code enumeration and reusable response definitions are in
[`swagger.yaml`](swagger.yaml).

### Delivered frontend

Angular 21 currently implements the authentication pages and infrastructure:
login, registration, email verification, forgot/reset password, route protection,
JWT interception, refresh, and local session state. The administrator area delivers
the track catalogue, YouTube import workflow, and playlist editor. Playlist editing
supports metadata, published-track search, add/remove operations, drag-and-keyboard
ordering with rollback on failure, and unsaved-change navigation protection. The
authenticated game-configuration page lists eligible public playlists, previews
their published tracks, filters out playlists with fewer than five eligible tracks,
and saves bounded track-count and round-duration choices as a local draft. Its controls
share the backend's approved limits and defaults; the frontend does not yet call the
delivered solo-session endpoint. Other non-auth feature components remain route
scaffolds whose templates are placeholders.

### Not delivered

There is no session lookup/join flow, synchronized browser playback, general
history browsing API, profile management, friends, guest joining, or admin user
management.
The private-media port, MinIO SDK adapter and deployment, database object-key
schema, and import-pipeline integration are delivered. Legacy media can still use
the Docker volume and `/media` URLs during migration.

The non-root backend runtime image installs pinned `yt-dlp` and FFmpeg versions
and verifies both binaries while building. Import execution applies a configurable
wall-clock deadline and temporary-file-size ceiling, limits FFmpeg to one worker
thread, and inherits the backend container CPU quota. A timeout or oversized
workspace terminates the process tree and marks the import failed.
Import requests enforce database-aligned metadata bounds and canonicalize
supported YouTube URL forms before creating a job. A partial unique PostgreSQL
index prevents concurrent active jobs for the same video. The process receives a
fixed argument list with an option terminator and an application-owned output
path inside a newly created import directory; symlink or non-regular output is
rejected. Provider and infrastructure exception details are replaced with stable
safe failure messages before persistence, WebSocket delivery, or logging.

Full-stack adapter tests import
`DeterministicTestAdaptersConfig`. Production continues to use `EmailService`,
`TurnstileService`, `AsyncImportJobScheduler`, the `ProcessBuilder` factory, a UTC
system clock, UUID-backed opaque refresh tokens, and `MinioMediaStorage`. The
MinIO adapter keeps SDK types below the service port and uses separate clients for
private storage operations and browser-accessible URL signing.
Testcontainers exercises that adapter against the pinned MinIO API for upload,
existence, recursive listing, pre-signing, HTTP byte-range reads, retry-safe
overwrite/deletion, and unavailable-storage translation. Playwright independently
verifies that Chromium can seek through a range-capable audio response.
Track catalogue responses are immutable scalar metadata snapshots. `title` is
always the canonical primary answer (film title for film-related tracks, music
title otherwise); album, composer, work, category, decade, duration, and image are
nullable secondary metadata. Publication requires nonblank title and artist plus
either a legacy audio URL or a complete object key/checksum/positive-size tuple.
Administrators manage alternative title, artist, composer, and album answers
through dedicated per-track CRUD endpoints. Alias responses expose only their ID,
type, and display value; an internal Unicode/case/whitespace key rejects
duplicates without defining the later gameplay matching rules. Tracks also store
an administrator-configured whole-second playback offset, defaulting to zero.
Publication of a non-zero offset requires a known duration with at least 60
seconds remaining.
Administrators request previews through
`GET /api/admin/tracks/{trackId}/preview-url`; the application verifies a pending
track, a completed import, finalized media metadata, and object existence before
returning the pre-signed URL and its expiration. Signed URLs and object keys are
not written to application logs or persistent API data.
Track deletion and media replacement write obsolete object keys to a durable
`media_deletion_outbox` row in the same transaction as the reference change. A
bounded scheduled worker deletes objects idempotently and retains failed tasks
with exponential backoff. Administrators can run read-only reconciliation through
`GET /api/admin/media/reconciliation`; it reports track IDs and truncated key
fingerprints rather than exposing internal keys. Aggregate pending, success,
failure, missing, and orphan counts are available through the administrator-only
Actuator metrics endpoint. Operational response steps are documented in
[`media-operations.md`](media-operations.md).
Persistence-generated entity IDs remain database-owned; tests consume returned
IDs instead of predicting them.

## Target Architecture

Sections 1–13 below are the original target specification. Some catalogue and
authentication capabilities overlap with delivered code, while gameplay,
multiplayer, social, history, and several administration capabilities remain
planned or deferred. Accepted technical decisions are indexed under
[`adr/`](adr/README.md); they override conflicting target details below. The
roadmap determines when target behavior becomes delivery scope.

---

## 1. Overview

The target product is a real-time multiplayer music blindtest web application.

- A **host** starts a session and configures the game
- **Players** join via a short code or a friend invite (no account required)
- Music plays synchronously across all clients
- Scores and reveals are broadcast in real time
- **Administrators** manage the music catalogue and platform playlists

---

## 2. Functional Specifications

### 2.1 User Roles

| Role | Description | Account required |
|---|---|---|
| **Host** | Creates and controls the game session (start, skip, etc.) | Yes |
| **Registered Player** | Persistent profile, friend system, game history | Yes |
| **Guest Player** | Temporary account valid for the duration of the session | No |
| **Administrator** | Manages the music catalogue and platform playlists | Yes (role `ADMIN`) |

### 2.2 User Stories

**Account Management**
- As a user, I want to register with a verified email (via Cloudflare)
- As a user, I want to log in and personalise my profile (username, avatar, password)

**Friend System**
- As a registered player, I want to search for other players by username
- As a registered player, I want to send/receive/accept/decline friend requests
- As a registered player, I want to remove a friend
- As a host, I want to invite a friend directly into my lobby without sharing the code
- As a player, I want to receive a real-time notification when a friend invites me to a session

**Session Creation**
- As a host, I want to create a session and get a shareable short code
- As a host, I want to configure the rules: reveal mode, timer duration, number of tracks
- As a host, I want to choose the music (playlist, category, decade)
- As a host, I want to start the game when all players are ready
- As a host, I want to invite my friends directly from the lobby

**Joining a Session**
- As a player, I want to join a session with a 6-character code
- As a guest player, I want to choose a username without creating an account
- As a registered player, I want to join a friend's session via a notification

**Gameplay**
- As a player, I want to hear the music at the same time as everyone else
- As a player, I want to submit my answer (title / artist)
- As a player, I want to see in real time who found the answer
- As a player, I want to vote to skip the track
- As a player, I want to see the live score

**History**
- As a registered player, I want to view my game history
- As a player, I want to see the details of a past game (scores, tracks played)
- As a player, I want to view another player's public history

**Catalogue Management (Administrator)**
- As an administrator, I want to import a track by pasting a YouTube link
- As an administrator, I want to fill in metadata: title, artist, album, composer,
  work, category, decade, duration, and image
- As an administrator, I want to preview the in-game rendering before validating
- As an administrator, I want to validate or reject an imported track
- As an administrator, I want to create and edit themed playlists
- As an administrator, I want to add/remove tracks from a playlist

**Reveal Modes**

| Mode | Behaviour |
|---|---|
| **Mode 1** | As soon as a player finds the answer, the title is revealed to all |
| **Mode 2** | The title is revealed when everyone has found it OR when the timer expires |

### 2.3 State Machine — Session

```mermaid
stateDiagram-v2
    [*] --> LOBBY : host creates the session
    LOBBY --> PLAYING : host starts the game
    PLAYING --> ROUND_END : all found OR timer expired
    ROUND_END --> PLAYING : next track
    ROUND_END --> FINISHED : last track
    FINISHED --> [*]
    LOBBY --> [*] : host deletes the session
```

### 2.4 State Machine — Round

```mermaid
stateDiagram-v2
    [*] --> LOADING : server sends startAt + url
    LOADING --> PLAYING : startAt reached (client)
    PLAYING --> REVEALED : Mode 1 = 1st correct / Mode 2 = all correct OR timer
    REVEALED --> [*] : post-reveal delay elapsed
```

### 2.5 State Machine — Music Import

```mermaid
stateDiagram-v2
    [*] --> QUEUED : admin submits YouTube URL
    QUEUED --> DOWNLOADING : yt-dlp starts
    DOWNLOADING --> PROCESSING : audio downloaded, conversion
    PROCESSING --> DONE : audio ready, preview available
    PROCESSING --> FAILED : error (invalid link, rights, ...)
    DOWNLOADING --> FAILED : network error / YouTube
    DONE --> PUBLISHED : admin validates
    DONE --> REJECTED : admin rejects
    FAILED --> [*]
    PUBLISHED --> [*]
    REJECTED --> [*]
```

Publication and rejection are idempotent terminal actions and are accepted only
from `DONE`. The REST status endpoint is the recovery source after a missed private
WebSocket event. Rejected media remains private and retained until DG-05 defines
its deletion period.

### 2.6 State Machine — Friend Request

```mermaid
stateDiagram-v2
    [*] --> PENDING : player A sends a request
    PENDING --> ACCEPTED : player B accepts
    PENDING --> DECLINED : player B declines
    PENDING --> [*] : player A cancels
    ACCEPTED --> [*] : either player removes the friend
    DECLINED --> [*]
```

---

## 3. Tech Stack

### Backend

| Component | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| WebSocket | Spring WebSocket + STOMP |
| Security | Spring Security + JWT (jjwt) |
| Persistence | Spring Data JPA + Hibernate |
| Database | PostgreSQL 16 |
| Mail | Spring Mail + SMTP |
| Audio import | yt-dlp (external process) + FFmpeg |
| Build | Maven |

### Frontend

| Component | Technology |
|---|---|
| Framework | Angular 17+ (standalone components) |
| WebSocket | @stomp/rx-stomp |
| State | RxJS (BehaviorSubject / signals) |
| UI | Angular Material + TailwindCSS |
| Audio | Web Audio API (native) |
| HTTP | Angular HttpClient |

### Infrastructure

```
Browser ──HTTPS──► Nginx (reverse proxy)
                       │
                 ┌─────┴──────┐
                 │            │
           Angular SPA   Spring Boot
           (static)      :8080
                              │
                         PostgreSQL
                         :5432
                              │
                         /media (audio files)
```

---

## 4. General Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                          FRONTEND (Angular)                          │
│                                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────┐  ┌──────────┐  │
│  │  Auth    │  │  Lobby   │  │   Game   │  │ End  │  │  Admin   │  │
│  │  Module  │  │  Module  │  │  Module  │  │Screen│  │  Module  │  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └──┬───┘  └────┬─────┘  │
│       └──────────────┴─────────────┴────────────┴───────────┘        │
│                 StompService  +  AuthService  +  FriendService        │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ WS (STOMP) + REST (HTTPS)
┌──────────────────────────────┼───────────────────────────────────────┐
│                    BACKEND (Spring Boot)                             │
│                                                                      │
│  ┌──────────────────┐  ┌────────────────────────────────────────┐   │
│  │  REST Controllers │  │           WebSocket / STOMP            │   │
│  │  /api/auth        │  │  /ws  (STOMP endpoint)                 │   │
│  │  /api/sessions    │  │  /app/**   (client → server)          │   │
│  │  /api/users       │  │  /topic/** (server → group)           │   │
│  │  /api/tracks      │  │  /user/queue/** (server → 1 client)  │   │
│  │  /api/playlists   │  └────────────────────────────────────────┘   │
│  │  /api/admin/**    │                                               │
│  └──────────────────┘                                               │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │   AuthService │ SessionService │ GameService │ FriendService    │ │
│  │   TrackService │ ImportJobService │ PlaylistService             │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────┐  ┌───────────────────────────────┐   │
│  │  SessionStore              │  │  ScheduledExecutorService     │   │
│  │  (ConcurrentHashMap)       │  │  (round timers)               │   │
│  └────────────────────────────┘  └───────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │              Spring Data JPA → PostgreSQL                       │ │
│  │              YtDlpWrapper → yt-dlp (process) → /media          │ │
│  └─────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 5. Data Model

### 5.1 Persisted Entities (PostgreSQL)

```mermaid
classDiagram
    class User {
        +UUID id
        +String username
        +String email
        +String passwordHash
        +String avatarUrl
        +boolean emailVerified
        +Role role [USER, ADMIN]
        +LocalDateTime createdAt
        +LocalDateTime lastConnexion
    }

    class Track {
        +UUID id
        +String title
        +String artist
        +String album
        +String composer
        +String work
        +String audioUrl
        +String audioObjectKey
        +String audioChecksum
        +Long audioSizeBytes
        +String imageUrl
        +String category
        +Integer decade
        +Integer durationSeconds
        +TrackStatus status [PENDING, PUBLISHED, REJECTED]
        +String youtubeUrl
        +LocalDateTime createdAt
    }

    class Playlist {
        +UUID id
        +String name
        +String description
        +String imageUrl
        +UUID createdBy
        +LocalDateTime createdAt
    }

    class PlaylistTrack {
        +UUID playlistId
        +UUID trackId
        +Integer order
    }

    class TrackImportJob {
        +UUID id
        +String youtubeUrl
        +String youtubeVideoId
        +ImportStatus status
        +Integer progress
        +String errorMessage
        +UUID adminId
        +UUID trackId
        +LocalDateTime createdAt
    }

    class FriendRequest {
        +UUID id
        +UUID fromUserId
        +UUID toUserId
        +FriendStatus status [PENDING, ACCEPTED, DECLINED]
        +LocalDateTime createdAt
        +LocalDateTime respondedAt
    }

    class GameHistory {
        +UUID id
        +String sessionCode
        +LocalDateTime playedAt
        +Integer trackCount
        +Integer playerCount
    }

    class GameHistoryPlayer {
        +UUID id
        +UUID gameId
        +UUID userId
        +String guestName
        +Integer finalScore
        +Integer rank
    }

    User "1" --> "*" FriendRequest : from
    User "1" --> "*" FriendRequest : to
    User "1" --> "*" GameHistoryPlayer
    User "1" --> "*" Playlist : creates
    User "1" --> "*" TrackImportJob : imports
    GameHistory "1" --> "*" GameHistoryPlayer
    Playlist "1" --> "*" PlaylistTrack
    Track "1" --> "*" PlaylistTrack
    TrackImportJob "1" --> "0..1" Track : produces
```

### 5.2 In-Memory Objects (active sessions)

```mermaid
classDiagram
    class GameSession {
        +String id
        +SessionState state
        +String hostUserId
        +GameRules rules
        +List~Track~ tracks
        +int currentTrackIndex
        +Map~String,SessionPlayer~ players
        +Round currentRound
    }

    class SessionPlayer {
        +String userId
        +String displayName
        +String sessionId
        +String socketId
        +int score
        +boolean connected
        +boolean isGuest
    }

    class Round {
        +Track track
        +RoundState state
        +long startAt
        +Set~String~ foundPlayerIds
        +int skipVotes
        +ScheduledFuture timerTask
    }

    class GameRules {
        +RevealMode revealMode
        +int timerSeconds
        +int trackCount
        +boolean speedBonus
        +int skipThresholdPercent
    }

    GameSession "1" --> "*" SessionPlayer
    GameSession "1" --> "1" Round
    GameSession "1" --> "1" GameRules
```

---

## 6. REST API

> See `docs/swagger.yaml` for the full specification with schemas, examples and error codes.

Base URL: `/api`

### Auth

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| POST | `/auth/register` | Register | No |
| POST | `/auth/login` | Login, returns JWT | No |
| POST | `/auth/logout` | Invalidates the refresh token | JWT |
| POST | `/auth/refresh` | Refreshes the JWT | Cookie |
| POST | `/auth/verify-email` | Email verification (token) | No |
| POST | `/auth/forgot-password` | Sends a password reset email | No |
| POST | `/auth/reset-password` | Resets the password | No |

### Users

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | `/users/me` | Full profile of the logged-in user | JWT |
| PATCH | `/users/me` | Update username / avatar / password | JWT |
| DELETE | `/users/me` | Delete account | JWT |
| GET | `/users/{userId}` | Public profile of a user | JWT |
| GET | `/users/search?q=` | Search users by username | JWT |

### Friends

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | `/users/me/friends` | Friend list | JWT |
| DELETE | `/users/me/friends/{userId}` | Remove a friend | JWT |
| GET | `/users/me/friends/requests` | Pending requests (received + sent) | JWT |
| POST | `/users/{userId}/friend-request` | Send a friend request | JWT |
| POST | `/users/me/friends/requests/{requestId}/accept` | Accept a request | JWT |
| POST | `/users/me/friends/requests/{requestId}/decline` | Decline a request | JWT |
| DELETE | `/users/me/friends/requests/{requestId}` | Cancel a sent request | JWT |

### History

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | `/users/me/history` | Game history of the logged-in player | JWT |
| GET | `/users/{userId}/history` | Public history of a player | JWT |
| GET | `/games/{gameId}` | Full details of a game | JWT |

### Sessions

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| POST | `/sessions` | Create a session | JWT |
| GET | `/sessions/{code}` | Public session info | No |
| DELETE | `/sessions/{code}` | Delete the session | JWT (host) |
| POST | `/sessions/{code}/join` | Join (guest: body `{ guestName }`) | Optional |
| POST | `/sessions/{code}/invite/{friendId}` | Invite a friend (WS notification) | JWT (host) |

### Tracks & Playlists (public catalogue)

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | `/tracks?category=&decade=` | Filtered list of published tracks | No |
| GET | `/playlists` | List of available playlists | No |
| GET | `/playlists/{id}` | Playlist detail with its tracks | No |

### Administration — Tracks

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | `/admin/tracks` | All tracks (all statuses) | ADMIN |
| PUT | `/admin/tracks/{id}` | Update track metadata | ADMIN |
| DELETE | `/admin/tracks/{id}` | Delete a track | ADMIN |
| POST | `/admin/tracks/import` | Start a YouTube track import | ADMIN |
| GET | `/admin/tracks/import/{jobId}` | Import job status | ADMIN |
| POST | `/admin/tracks/{id}/validate` | Validate and publish the track | ADMIN |
| POST | `/admin/tracks/{id}/reject` | Reject the track | ADMIN |

### Administration — Playlists

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | `/admin/playlists` | All playlists | ADMIN |
| POST | `/admin/playlists` | Create a playlist | ADMIN |
| GET | `/admin/playlists/{id}` | Playlist detail | ADMIN |
| PUT | `/admin/playlists/{id}` | Update a playlist | ADMIN |
| DELETE | `/admin/playlists/{id}` | Delete a playlist | ADMIN |
| POST | `/admin/playlists/{id}/tracks` | Add a track to the playlist | ADMIN |
| DELETE | `/admin/playlists/{id}/tracks/{trackId}` | Remove a track | ADMIN |
| PUT | `/admin/playlists/{id}/tracks/order` | Atomically replace the complete track order | ADMIN |

Playlist positions are contiguous and zero-based. Only published tracks may be
added, duplicate membership is rejected, and an order replacement must contain
every current track ID exactly once. Playlist rows are locked while membership is
changed, and the database enforces non-negative unique positions. Rejecting or
deleting a published track removes it from every playlist and compacts the
remaining positions in the same transaction. Public and administrator playlist
details expose published tracks only.

### Administration — Users

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | `/admin/users` | User list | ADMIN |
| PATCH | `/admin/users/{userId}` | Update a user (role, status) | ADMIN |
| DELETE | `/admin/users/{userId}` | Delete a user | ADMIN |

---

## 7. WebSocket / STOMP

### Connection

```
URL: ws://host/ws
Protocol: STOMP 1.2
Header: Authorization: Bearer <JWT or sessionToken>
```

Native WebSocket clients connect to `/ws`. SockJS clients use the separate
`/ws-sockjs` fallback endpoint.

### Gameplay destinations

The authoritative V1 payload contract, versioning, idempotency, and recovery rules
are documented in [`websocket-protocol.md`](websocket-protocol.md).

Gameplay `SEND` and `SUBSCRIBE` frames are authorized against the active in-memory
session snapshot. Only exact UUID destinations are accepted, and private feedback
uses Spring's principal-scoped user queues. JWT validity is enforced at `CONNECT`;
an established connection remains authenticated through token expiry, while a
reconnect requires a fresh valid token.

| Destination | Recipients | Content |
|---|---|---|
| `/topic/sessions/{sessionId}` | Authorized session members | Shared game events |
| `/user/queue/sessions/{sessionId}` | One authenticated participant | Private feedback and recovery |
| `/user/queue/notifications` | 1 connected user | Invitations, friend requests |
| `/user/queue/import/{jobId}` | 1 admin | Import progress |

### Gameplay commands (client → server)

| Destination | Description |
|---|---|
| `/app/sessions/{sessionId}` | Versioned `START_GAME`, `SUBMIT_ANSWER`, `SKIP_ROUND`, `ADVANCE_ROUND`, or `REQUEST_SNAPSHOT` command |

### Shared gameplay event types (`/topic/sessions/{sessionId}`)

| Type | Trigger |
|---|---|
| `SESSION_READY` | Initial safe session projection |
| `ROUND_PREPARING` | New round is being prepared |
| `ROUND_STARTED` | New round with playback URL and authoritative timestamps |
| `ROUND_REVEALED` | Reveal of the correct answer + scores |
| `SCORE_UPDATED` | Authoritative participant score changed |
| `GAME_FINISHED` | End of game |

Private `SECONDARY_MATCH`, `ANSWER_REJECTED`, `CURRENT_SNAPSHOT`, and `ERROR`
events use `/user/queue/sessions/{sessionId}` and are never broadcast to the topic.

### Notification Events (`/user/queue/notifications`)

| Type | Payload |
|---|---|
| `SESSION_INVITE` | `{ fromUser, sessionCode, sessionState }` |
| `FRIEND_REQUEST` | `{ fromUser, requestId }` |
| `FRIEND_REQUEST_ACCEPTED` | `{ byUser }` |

### Import Events (`/user/queue/import/{jobId}`)

| Type | Payload |
|---|---|
| `IMPORT_PROGRESS` | `{ jobId, status, progress }` |
| `IMPORT_DONE` | `{ jobId, trackId }`; the admin then requests an authorized preview URL |
| `IMPORT_FAILED` | `{ jobId, errorMessage }` |

---

## 8. Sequence Diagrams

### 8.1 Friend Invite to a Session

```mermaid
sequenceDiagram
    actor Host as Host (A)
    actor Friend as Friend (B)
    participant REST
    participant WS

    Host->>REST: POST /api/sessions/XK9F2A/invite/{friendId}
    REST->>WS: sendToUser(friendId, SESSION_INVITE)
    WS-->>Friend: SESSION_INVITE { fromUser: A, sessionCode: XK9F2A }

    Friend->>REST: POST /api/sessions/XK9F2A/join
    Note over Friend,REST: With their JWT (no guestName required)
    REST-->>Friend: 200 { sessionToken }
    Friend->>WS: CONNECT + SUBSCRIBE /topic/sessions/{sessionId}
    WS-->>Host: PLAYER_JOINED { displayName: B }
```

### 8.2 Music Import (admin)

```mermaid
sequenceDiagram
    actor Admin
    participant REST
    participant WS
    participant Job as ImportJobService
    participant YtDlp as yt-dlp

    Admin->>REST: POST /api/admin/tracks/import { youtubeUrl, title, artist, ... }
    REST->>Job: createJob(url, metadata)
    Job-->>REST: { jobId }
    REST-->>Admin: 202 { jobId }

    Admin->>WS: SUBSCRIBE /user/queue/import/{jobId}

    Job->>YtDlp: download audio
    Job->>WS: IMPORT_PROGRESS { progress: 40 }
    WS-->>Admin: IMPORT_PROGRESS { progress: 40 }

    YtDlp-->>Job: audio downloaded
    Job->>Job: FFmpeg convert → MP3
    Job->>WS: IMPORT_DONE { trackId }
    WS-->>Admin: IMPORT_DONE { trackId }

    Admin->>REST: GET /api/admin/tracks/{trackId}/preview-url
    REST->>S3: verify object and pre-sign GET
    REST-->>Admin: 200 { url, expiresAt }

    Note over Admin: Admin listens to the in-game preview
    Admin->>REST: POST /api/admin/tracks/{trackId}/validate
    REST-->>Admin: 200 { status: PUBLISHED }
```

### 8.3 Game Start and Audio Synchronization

```mermaid
sequenceDiagram
    actor Host as Host
    actor P1 as Player 1
    actor P2 as Player 2
    participant GS as GameService

    Host->>GS: SEND /app/sessions/{sessionId} { protocolVersion: 1, commandId, expectedSessionVersion, type: START_GAME }
    GS->>GS: startAt = now() + 3000ms
    GS->>GS: scheduleTimer(startAt + timerSeconds * 1000)
    GS-->>Host: ROUND_STARTED { playbackUrl, startsAt, deadline }
    GS-->>P1: ROUND_STARTED { playbackUrl, startsAt, deadline }
    GS-->>P2: ROUND_STARTED { playbackUrl, startsAt, deadline }

    Note over Host,P2: All load and play at the exact startAt
    Host->>Host: audio.play() at startAt
    P1->>P1: audio.play() at startAt
    P2->>P2: audio.play() at startAt
```

---

## 9. Backend Architecture (Spring Boot)

### 9.1 Package Structure

```
src/main/java/com/blindtest/
├── config/
│   ├── SecurityConfig.java
│   ├── WebSocketConfig.java
│   └── SchedulerConfig.java
│
├── controller/
│   ├── AuthController.java
│   ├── SessionController.java
│   ├── UserController.java
│   ├── TrackController.java
│   ├── PlaylistController.java
│   └── admin/
│       ├── AdminTrackController.java
│       ├── AdminPlaylistController.java
│       └── AdminUserController.java
│
├── websocket/
│   ├── GameController.java         # @MessageMapping handlers
│   ├── SessionEventPublisher.java
│   └── WebSocketEventListener.java
│
├── service/
│   ├── AuthService.java
│   ├── SessionService.java
│   ├── GameService.java
│   ├── FriendService.java
│   ├── TrackService.java
│   ├── PlaylistService.java
│   ├── ImportJobService.java       # orchestrates yt-dlp + FFmpeg
│   ├── NotificationService.java    # WS push to /user/queue/notifications
│   └── EmailService.java
│
├── store/
│   └── SessionStore.java           # ConcurrentHashMap<String, GameSession>
│
├── model/
│   ├── entity/
│   │   ├── UserEntity.java
│   │   ├── TrackEntity.java
│   │   ├── PlaylistEntity.java
│   │   ├── PlaylistTrackEntity.java
│   │   ├── TrackImportJobEntity.java
│   │   ├── FriendRequestEntity.java
│   │   ├── GameHistoryEntity.java
│   │   └── GameHistoryPlayerEntity.java
│   ├── session/                    # in-memory objects (non-JPA)
│   │   ├── GameSession.java
│   │   ├── SessionPlayer.java
│   │   ├── Round.java
│   │   └── GameRules.java
│   ├── game/                       # pure validated configuration and game rules
│   └── dto/
│       ├── request/
│       └── response/
│
├── security/
│   ├── JwtTokenProvider.java
│   └── JwtAuthFilter.java
│
├── infra/
│   └── YtDlpWrapper.java           # yt-dlp + FFmpeg process
│
└── repository/
    ├── UserRepository.java
    ├── TrackRepository.java
    ├── PlaylistRepository.java
    ├── TrackImportJobRepository.java
    ├── FriendRequestRepository.java
    └── GameHistoryRepository.java
```

---

## 10. Frontend Architecture (Angular)

### 10.1 Module Structure

```
src/app/
├── core/
│   ├── services/
│   │   ├── auth.service.ts
│   │   ├── stomp.service.ts
│   │   ├── audio.service.ts
│   │   ├── friend.service.ts
│   │   └── notification.service.ts  # listens to /user/queue/notifications
│   ├── guards/
│   └── interceptors/
│
├── features/
│   ├── auth/
│   ├── lobby/
│   │   ├── create-session/
│   │   ├── join-session/
│   │   └── lobby-room/
│   │       └── friend-invite/       # friend invite UI
│   ├── game/
│   │   ├── game-room/
│   │   ├── audio-player/
│   │   ├── answer-form/
│   │   ├── scoreboard/
│   │   └── player-list/
│   ├── profile/
│   │   ├── my-profile/
│   │   └── game-history/
│   ├── friends/
│   │   ├── friend-list/
│   │   └── friend-requests/
│   ├── end-screen/
│   └── admin/
│       ├── track-list/
│       ├── track-import/            # YouTube form + preview
│       ├── playlist-editor/
│       └── user-management/
│
└── shared/
    ├── components/
    │   └── notification-toast/      # invitations, friend requests
    └── models/
```

---

## 11. Audio Synchronization (Option 2 — detail)

### Principle

```
Server                          Clients
   │                               │
   │  startAt = now() + 3000ms     │
   │──── ROUND_STARTED ───────────►│
   │                               │ audio.load(url)
   │                               │ setTimeout(play, startAt - Date.now())
   │  scheduleRoundEnd(startAt     │
   │    + timerSeconds * 1000)     │ audio.play() ← exactly at startAt
```

```typescript
schedulePlay(audioUrl: string, startAt: number): void {
  this.audio.src = audioUrl;
  this.audio.load();

  const delay = startAt - Date.now();
  if (delay <= 0) {
    this.audio.currentTime = Math.abs(delay) / 1000;
    this.audio.play();
  } else {
    setTimeout(() => { this.audio.currentTime = 0; this.audio.play(); }, delay);
  }
}
```

| Case | Behaviour |
|---|---|
| Arrival after `startAt` | Immediate play with `currentTime = lag / 1000` |
| Reconnection mid-round | Client requests `CURRENT_SNAPSHOT`, which retains original `startsAt`, `deadline`, and playback URL |
| Slow network | 3s buffer absorbs typical latencies |

---

## 12. Music Import Workflow

1. The admin pastes the YouTube URL into the form
2. **POST** `/api/admin/tracks/import` → the server creates a `TrackImportJob` and responds `202 { jobId }`
3. The admin subscribes to `/user/queue/import/{jobId}` to track progress
4. The server runs `yt-dlp` in the background to extract bounded metadata and
   download the audio, then FFmpeg converts it to MP3 and generates at most 800
   normalized waveform peaks from a bounded mono PCM stream
5. Once done, the `IMPORT_DONE` event contains `jobId` and `trackId`
6. The frontend requests `GET /api/admin/tracks/{trackId}/preview-url` and
   `GET /api/admin/tracks/{trackId}/waveform` once, draws the waveform as SVG, then
   immediately seeks and plays whenever the admin moves or clicks the start selector
7. The admin corrects the extracted metadata when necessary, previews the 60-second
   selection window, and saves it through the track endpoint
8. The admin validates (**POST** `/api/admin/tracks/{id}/validate`) → `status = PUBLISHED`
   or rejects (**POST** `/api/admin/tracks/{id}/reject`) → `status = REJECTED`
9. Only `PUBLISHED` tracks are available in the catalogue for sessions

```java
// YtDlpWrapper.java (simplified)
public Path downloadAudio(String youtubeUrl, UUID jobId) throws IOException {
    Path outputPath = mediaDir.resolve(jobId + ".%(ext)s");
    ProcessBuilder pb = new ProcessBuilder(
        "yt-dlp", "-x", "--audio-format", "mp3",
        "-o", outputPath.toString(),
        youtubeUrl
    );
    Process process = pb.start();
    // read stdout for progress, notify via WS
    int exit = process.waitFor();
    if (exit != 0) throw new ImportException("yt-dlp failed");
    return mediaDir.resolve(jobId + ".mp3");
}
```

---

## 13. Security & Auth

### Registration Flow
```
1. POST /api/auth/register { email, username, password, turnstileToken }
2. Turnstile token verification against the Cloudflare API (siteverify)
3. Account created (emailVerified = false)
4. Email sent with signed token (TTL 24h)
5. POST /api/auth/verify-email { token } → emailVerified = true
```

### Cloudflare Turnstile (anti-bot)
The Turnstile widget is displayed on the registration form. The frontend obtains a `turnstileToken` client-side, which is sent in the `POST /auth/register` request. The backend calls `https://challenges.cloudflare.com/turnstile/v0/siteverify` with the secret key and token before any account creation. A `TurnstileService` encapsulates this HTTP call. The secret key is injected via the `CLOUDFLARE_TURNSTILE_SECRET_KEY` environment variable.

### JWT
- **Access token**: TTL 15 min
- **Refresh token**: TTL 7 days (HttpOnly cookie)
- **Session token** (guest): TTL = session lifetime

### Answer Validation (anti-cheat)
Comparison remains **server-side only**. The delivered pure matcher applies the
normalization and deterministic tiered policy documented under
[Delivered backend](#delivered-backend), including aliases, token boundaries, and
length-sensitive Damerau–Levenshtein thresholds. It never returns answer text for a
failed guess.

### WebSocket Authorization
The `JwtAuthFilter` validates the token in the STOMP `CONNECT` header. Each `@MessageMapping` verifies that the `Principal` belongs to the target session.

---

*v2.0 — 2026-05-13*
