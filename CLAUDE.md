# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Status

This is a **design-phase** repository. All code is yet to be written. The authoritative specs are:

- [`docs/architecture.md`](docs/architecture.md) — full functional + technical spec (v2.0)
- [`docs/swagger.yaml`](docs/swagger.yaml) — OpenAPI 3.0 with all endpoints, schemas, and error codes
- [`docs/diagrams/*.puml`](docs/diagrams/) — PlantUML UML diagrams (components, data model, states, sequences)

## Stack

**Backend:** Java 21 · Spring Boot 3.x · Spring Security + JWT (jjwt) · Spring WebSocket/STOMP · Spring Data JPA + Hibernate · PostgreSQL 16 · Maven · yt-dlp + FFmpeg (audio import) · Cloudflare Turnstile (anti-bot, inscription)

**Frontend:** Angular 17+ (standalone components) · @stomp/rx-stomp · RxJS · Angular Material + TailwindCSS · Web Audio API

**Infrastructure:** Nginx reverse proxy → Angular SPA (static) + Spring Boot :8080 → PostgreSQL :5432 + `/media` (audio files)

## Architecture Overview

### Dual data model
The backend maintains two separate object graphs:
- **JPA entities** (`model/entity/`) persisted to PostgreSQL — users, tracks, playlists, friend requests, game history
- **In-memory session objects** (`model/session/`) held in `SessionStore` (a `ConcurrentHashMap<String, GameSession>`) — active game sessions, players, current round state, timers

Session state is never persisted during play; only written to `GameHistory`/`GameHistoryPlayer` on game end.

### Real-time communication
Two channels run in parallel:
- **REST** (`/api/**`) — CRUD operations and session lifecycle
- **WebSocket/STOMP** (`/ws`) — game events, notifications, import progress

STOMP topics:
- `/topic/session/{code}` — broadcast to all players in a session
- `/user/queue/notifications` — per-user invitations and friend events
- `/user/queue/import/{jobId}` — admin import progress stream

### Audio synchronization
The server computes `startAt = now() + 3000ms` and broadcasts it with `ROUND_START`. Clients schedule `audio.play()` at that exact timestamp. Late joiners receive the original `startAt` plus elapsed time so they can seek to the correct position. See section 11 of `architecture.md` for the `schedulePlay()` implementation.

### Session state machine
`LOBBY → PLAYING → ROUND_END ↔ PLAYING → FINISHED`. Round sub-states: `LOADING → PLAYING → REVEALED`. The `ScheduledExecutorService` drives round timers server-side; the `GameService` handles all transitions.

### Answer validation (anti-cheat)
Answer comparison is **server-side only**: string normalization + Levenshtein distance ≤ 2.

### Auth
- Access JWT: 15 min TTL
- Refresh token: 7 days (HttpOnly cookie)
- Guest session token: lifetime of session
- WebSocket auth: `JwtAuthFilter` validates token in STOMP `CONNECT` header; each `@MessageMapping` verifies the `Principal` belongs to the target session.
- Email verification required before hosting a session (Cloudflare-based SMTP).

### Music import pipeline
Admin submits YouTube URL → POST `/api/admin/tracks/import` returns `202 { jobId }` → `ImportJobService` runs `yt-dlp` + FFmpeg as external processes → progress streamed via `/user/queue/import/{jobId}` → admin previews, then validates (`PUBLISHED`) or rejects (`REJECTED`). Only `PUBLISHED` tracks appear in the public catalogue.

## Backend Package Layout

```
src/main/java/com/blindtest/
├── config/          # SecurityConfig, WebSocketConfig, SchedulerConfig
├── controller/      # REST controllers (/api/**)
│   └── admin/       # Admin-only REST controllers
├── websocket/       # @MessageMapping handlers, SessionEventPublisher, WS event listener
├── service/         # Business logic (AuthService, GameService, ImportJobService, …)
├── store/           # SessionStore (in-memory ConcurrentHashMap)
├── model/
│   ├── entity/      # JPA entities
│   ├── session/     # In-memory game objects (non-JPA)
│   └── dto/         # Request/response DTOs
├── security/        # JwtTokenProvider, JwtAuthFilter
├── infra/           # YtDlpWrapper (external process)
└── repository/      # Spring Data JPA repositories
```

## Frontend Module Layout

```
src/app/
├── core/            # Singleton services (auth, stomp, audio, friend, notification)
│   ├── guards/
│   └── interceptors/
├── features/
│   ├── auth/
│   ├── lobby/       # create-session, join-session, lobby-room (+ friend-invite)
│   ├── game/        # game-room, audio-player, answer-form, scoreboard, player-list
│   ├── profile/     # my-profile, game-history
│   ├── friends/     # friend-list, friend-requests
│   ├── end-screen/
│   └── admin/       # track-list, track-import, playlist-editor, user-management
└── shared/          # notification-toast, shared models
```

## Key Design Decisions

- **Reveal modes:** Mode 1 reveals on first correct answer; Mode 2 reveals when all found OR timer expires (with speed-based scoring).
- **Skip votes:** Configurable threshold (`skipThresholdPercent`). Sends `SKIP_VOTE` event on each vote.
- **Guest accounts:** No registration needed to join — guests get a temporary `sessionToken` from `POST /sessions/{code}/join { guestName }`.
- **Friend invites from lobby:** Host calls `POST /sessions/{code}/invite/{friendId}`; server pushes `SESSION_INVITE` via `/user/queue/notifications` to the friend.
- **Game history visibility:** A player's history is public; guests' game entries record `guestName` without a `userId`.

## Good practices

- develop using Test Driven Developpment
- keep the documentation up-to-date