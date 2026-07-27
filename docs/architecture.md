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
        │                     └── JPA / Flyway ──> PostgreSQL
        └── /media ─────────> shared Docker volume
```

Docker Compose starts all three services. Only the frontend publishes a host port;
Nginx proxies the API and WebSocket traffic to the backend on the internal network.

### Delivered backend

- Java 21 and Spring Boot 3.2 with Spring Security, JPA, Flyway, and STOMP.
- Registration with Turnstile, email verification, login, rotating opaque refresh
  tokens, logout, and password reset.
- Public published-track and playlist catalogue endpoints.
- Administrator track review, asynchronous YouTube import jobs, per-user STOMP
  import progress, and ordered playlist management.
- PostgreSQL entities and Flyway migrations for users, refresh tokens, tracks,
  playlists, playlist membership, and import jobs.
- JWT authentication for HTTP requests and STOMP `CONNECT` frames.

The delivered REST surface is limited to `/api/auth/**`, `/api/tracks`,
`/api/playlists/**`, `/api/admin/tracks/**`,
`/api/admin/tracks/import/**`, and `/api/admin/playlists/**`.

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
JWT interception, refresh, and local session state. Non-auth feature components
exist as route scaffolds whose templates are placeholders.

### Not delivered

There is no session or round domain model, `SessionStore`, gameplay controller,
gameplay STOMP handler, scoring/timer/answer logic, synchronized playback, game
history, profile management, friends, guest joining, or admin user management.
The planned private S3-compatible media layer is also absent; current media uses a
Docker volume and `/media` URLs.

The import pipeline invokes `yt-dlp`, but the current backend runtime image does
not install `yt-dlp` or FFmpeg. Consequently, imports fail in that container until
the runtime dependency ticket is completed.

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
- As an administrator, I want to fill in metadata: title, artist, composer, work, decade, image
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
        +String composer
        +String work
        +String audioUrl
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

### Subscription Topics (server → clients)

| Topic | Recipients | Content |
|---|---|---|
| `/topic/session/{code}` | All players in a session | Game events |
| `/user/queue/notifications` | 1 connected user | Invitations, friend requests |
| `/user/queue/private` | 1 user (ACK, errors) | Private messages |
| `/user/queue/import/{jobId}` | 1 admin | Import progress |

### Send Destinations (client → server)

| Destination | Description |
|---|---|
| `/app/session/{code}/start` | Host starts the game |
| `/app/session/{code}/answer` | Submit an answer `{ text }` |
| `/app/session/{code}/vote-skip` | Vote to skip |
| `/app/session/{code}/next` | Host skips manually |

### Game Event Types (`/topic/session/{code}`)

| Type | Trigger |
|---|---|
| `PLAYER_JOINED` | A player joins the lobby |
| `PLAYER_LEFT` | A player leaves |
| `GAME_STARTED` | The game starts |
| `ROUND_START` | New round (contains `audioUrl` + `startAt`) |
| `PLAYER_FOUND` | A player found the answer |
| `ROUND_REVEALED` | Reveal of the correct answer + scores |
| `ROUND_END` | End of round |
| `SKIP_VOTE` | Skip vote update |
| `GAME_FINISHED` | End of game |

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
| `IMPORT_DONE` | `{ jobId, trackId, previewUrl }` |
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
    Friend->>WS: CONNECT + SUBSCRIBE /topic/session/XK9F2A
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
    Job->>WS: IMPORT_DONE { trackId, previewUrl }
    WS-->>Admin: IMPORT_DONE { trackId, previewUrl }

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

    Host->>GS: SEND /app/session/XK9F2A/start
    GS->>GS: startAt = now() + 3000ms
    GS->>GS: scheduleTimer(startAt + timerSeconds * 1000)
    GS-->>Host: ROUND_START { audioUrl, startAt, timerSeconds }
    GS-->>P1: ROUND_START { audioUrl, startAt }
    GS-->>P2: ROUND_START { audioUrl, startAt }

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
   │──── ROUND_START ─────────────►│
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
| Reconnection mid-round | Server sends `ROUND_START` with original `startAt` + `elapsed` |
| Slow network | 3s buffer absorbs typical latencies |

---

## 12. Music Import Workflow

1. The admin pastes the YouTube URL into the form and fills in the metadata
2. **POST** `/api/admin/tracks/import` → the server creates a `TrackImportJob` and responds `202 { jobId }`
3. The admin subscribes to `/user/queue/import/{jobId}` to track progress
4. The server runs `yt-dlp` in the background to download the audio, then FFmpeg to convert to MP3
5. Once done, the `IMPORT_DONE` event contains `previewUrl` and `trackId`
6. The admin previews the track in the preview interface (faithful in-game rendering)
7. The admin validates (**POST** `/api/admin/tracks/{id}/validate`) → `status = PUBLISHED`
   or rejects (**POST** `/api/admin/tracks/{id}/reject`) → `status = REJECTED`
8. Only `PUBLISHED` tracks are available in the catalogue for sessions

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
Comparison is done **server-side only** with normalisation + Levenshtein distance ≤ 2.

### WebSocket Authorization
The `JwtAuthFilter` validates the token in the STOMP `CONNECT` header. Each `@MessageMapping` verifies that the `Principal` belongs to the target session.

---

*v2.0 — 2026-05-13*
