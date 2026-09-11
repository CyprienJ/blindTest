# 10. Frontend Architecture (Angular)

[Up: Target design](../target.md)

Historical target design, not an implementation reference. Validated roadmap
decisions and executable code take precedence. See [current architecture](../../architecture.md).

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
