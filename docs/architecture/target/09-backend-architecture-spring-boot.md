# 9. Backend Architecture (Spring Boot)

[Up: Target design](../target.md)

Historical target design, not an implementation reference. Validated roadmap
decisions and executable code take precedence. See [current architecture](../../architecture.md).

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
