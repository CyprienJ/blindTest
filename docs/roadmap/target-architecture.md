# 4. Target Architecture

[Up: Roadmap](../plan.md)

### 4.1 Deployment topology

```text
Internet
  -> existing host reverse proxy (TLS and virtual host)
     -> 127.0.0.1:${APP_PORT}
        -> application Nginx / Angular
           -> /api and /ws -> Spring Boot
           -> browser receives pre-signed media URL

Spring Boot
  -> PostgreSQL
  -> MinIO S3 API
  -> yt-dlp + FFmpeg through a controlled process wrapper

MinIO
  -> dedicated persistent volume
  -> private blindtest-media bucket
```

Only the application entry point and the required public MinIO media endpoint are
reachable through the host reverse proxy. PostgreSQL, the backend port, and the
MinIO administration console remain private or localhost-bound.

### 4.2 Backend module boundaries

Keep one deployable backend, separated by package and interfaces:

```text
auth/          account, verification, refresh, password reset
catalogue/     tracks, answer metadata, playlists, publication
importing/     import jobs, process execution, progress
media/         S3 storage, pre-signing, deletion, reconciliation
game/          pure rules, answers, scoring, state transitions
session/       active session orchestration and SessionStore
history/       durable completed-game snapshots
realtime/      STOMP commands/events and authorization
shared/        errors, time/ID abstractions, validation
```

The current packages may be migrated incrementally. Package movement alone is not
a release goal; the important constraint is dependency direction:

```text
controllers / WebSocket handlers
  -> application services
     -> game and catalogue domain
        -> repository/storage/event ports
           -> JPA, MinIO, STOMP adapters
```

### 4.3 Game-domain principles

- Keep answer normalization, answer matching, scoring, and transition rules free
  from Spring, JPA, WebSocket, and system-clock calls.
- Inject a clock and deterministic ID/random sources.
- Represent a participant independently from authentication:
  - authenticated user in V1;
  - registered or guest participant later.
- Keep per-player discoveries separate. Secondary-answer feedback must be sent only
  to the player who found it.
- Use idempotent command identifiers for answer and skip commands.
- Treat server timestamps and deadlines as authoritative.
- Snapshot playlist order and track answer metadata for a started game so later
  catalogue edits cannot alter an active or historical result.

### 4.4 Active and durable state

Active session state:

- stored behind `SessionStore`;
- initially implemented with `ConcurrentHashMap`;
- contains current round, deadlines, participants, scores, discoveries, and command
  idempotency state;
- removed after persistence and an expiry window.

Durable history:

- stored in PostgreSQL at game completion;
- contains configuration, total score, ordered rounds, score per round, outcome,
  timings, and track snapshots;
- supports nullable `user_id` plus a guest display name for future multiplayer
  history;
- written idempotently to survive completion retries.

### 4.5 Media storage

Store an object key, never an expiring URL:

```text
tracks/{trackId}/audio-{contentId}.mp3
```

The media boundary provides:

```java
StoredMedia put(String objectKey, Path source, String contentType);
URI presignGet(String objectKey, Duration validity);
boolean exists(String objectKey);
void delete(String objectKey);
```

The import pipeline uses an isolated temporary file, validates it, uploads it,
verifies the object, persists the key, and always removes the temporary file.

Pre-signed URLs:

- are short-lived bearer credentials;
- are generated for an authorized preview or active round;
- are not stored in PostgreSQL;
- are not included in the public catalogue;
- must support browser range requests and seeking.

### 4.6 Scaling path

The first release intentionally avoids microservices and distributed sessions.
Scaling later follows these boundaries:

1. replace `InMemorySessionStore` with Redis;
2. replace the local event implementation with Redis pub/sub or a broker;
3. run multiple stateless backend instances;
4. move MinIO to a distributed topology or change S3 endpoint/credentials;
5. add a load balancer while keeping WebSocket routing compatible;
6. separate heavy import workers only if import load justifies it.

The game domain, REST contracts, WebSocket commands, history model, and S3 object
keys must not depend on a single VPS.
