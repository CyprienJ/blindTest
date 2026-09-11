# 3. Tech Stack

[Up: Target design](../target.md)

Historical target design, not an implementation reference. Validated roadmap
decisions and executable code take precedence. See [current architecture](../../architecture.md).

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
