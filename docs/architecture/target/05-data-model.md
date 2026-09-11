# 5. Data Model

[Up: Target design](../target.md)

Historical target design, not an implementation reference. Validated roadmap
decisions and executable code take precedence. See [current architecture](../../architecture.md).

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
