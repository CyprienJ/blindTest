# 4. General Architecture

[Up: Target design](../target.md)

Historical target design, not an implementation reference. Validated roadmap
decisions and executable code take precedence. See [current architecture](../../architecture.md).

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
