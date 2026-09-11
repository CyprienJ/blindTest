# 8. Sequence Diagrams

[Up: Target design](../target.md)

Historical target design, not an implementation reference. Validated roadmap
decisions and executable code take precedence. See [current architecture](../../architecture.md).

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
