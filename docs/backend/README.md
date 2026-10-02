# Backend guide

[Up: Project overview](../../README.md) · [Architecture](../architecture.md)

The Java 21 / Spring Boot 3.2 backend is the authority for account security,
catalogue publication, game configuration, answers, scoring, and deadlines.
The build and dependencies are defined in [pom.xml](../../backend/pom.xml).

## Following a request

A REST request passes through correlation-ID and JWT/security filters, reaches a
controller, and delegates to an application service. Services return DTOs instead
of JPA entities. Common errors carry stable codes and a request ID, also propagated
through `X-Request-ID` and logging MDC.

Gameplay uses a separate STOMP transport boundary. JWT authentication occurs on
`CONNECT`; session membership is checked on commands and subscriptions. Both
transport paths ultimately call services and the pure game domain.

## Code map

All paths below are under `backend/src/main/java/com/blindtest/`.

| Package | What belongs here |
|---|---|
| `controller/` | REST routes and transport validation |
| `websocket/` and `websocket/protocol/` | STOMP authorization, commands, publication and payloads |
| `service/` | Application use cases and orchestration |
| `service/port/` | Replaceable external boundaries, storage and scheduling |
| `model/game/` | Immutable pure gameplay rules |
| `store/` | Active-session and lobby storage ports and process-local adapters |
| `model/session/`, `model/lobby/` | Active sessions, command ledger, and pre-game lobbies |
| `model/entity/`, `repository/` | JPA persistence |
| `model/dto/`, `error/` | Safe public/admin projections and error contract |
| `infra/` | Process, storage, scheduling and randomness adapters |
| `config/`, `security/` | Typed settings, Spring wiring and authentication |

Use the [source tree](../../backend/src/main/java/com/blindtest) to find a class
before following a larger call chain. The intended feature-based packages in the
roadmap have not all replaced this layout.

## Delivered HTTP surface

| Route family | Purpose |
|---|---|
| `/api/auth/**` | Invitation lookup, invitation-only registration, verify, login, refresh, logout, recover/reset password |
| `/api/admin/invitations/**` | Administrator invitations: create, list, resend, revoke |
| `/api/admin/users/**` | Administrator account list, edit (username, email, role), and deletion |
| `GET /api/tracks`, `GET /api/playlists`, `GET /api/playlists/{id}` | Public catalogue |
| `POST /api/sessions` | Verified-account solo creation |
| `/api/lobbies/**` | Multiplayer lobbies: open, read, join by code, leave, ready, kick, start |
| `GET /api/sessions/{sessionId}/summary` | Durable completed-game summary for each registered participant |
| `/api/admin/tracks/**` | Track editing, aliases, preview, waveform, publication and rejection |
| `/api/admin/tracks/import/**` | Start a YouTube or file-upload import and poll it |
| `/api/admin/playlists/**` | Playlist CRUD and ordered membership |
| `GET /api/admin/media/reconciliation` | Read-only missing/orphan media report |

[Controllers](../../backend/src/main/java/com/blindtest/controller) define delivered
routes. [OpenAPI](../swagger.yaml) mixes delivered and intended operations; do not
infer implementation from a security matcher or contract entry alone.

## Go deeper

- [Authentication](authentication.md): accounts, token lifecycle and authorization.
- [Catalogue and imports](catalogue.md): publication, media and playlist integrity.
- [Sessions](sessions.md): state, transport, deadlines, history and cleanup.
- [Data model](../architecture/data.md): persistence ownership and migrations.
- [Testing](../testing.md): deterministic adapters and real infrastructure checks.
