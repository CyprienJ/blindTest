# Blindtest

Blindtest is a music guessing web application. Administrators import audio,
review its metadata and accepted answers, then publish tracks into public
playlists. Players choose a playlist and try to identify each title before time
runs out. Faster correct answers earn more points.

The first release targets authenticated solo play. Multiplayer rooms, guests,
friends, and profiles belong to later releases. The project is still in development;
[implementation status](docs/product/status.md) separates working code from
unfinished experiences and roadmap acceptance.

## How it works

1. A player registers, verifies their email, and signs in.
2. An administrator imports a YouTube track, previews the audio, configures the
   excerpt and alternative answers, and publishes it into the catalogue.
3. The administrator creates public playlists from published tracks.
4. A player configures a solo game. The backend randomly selects and snapshots
   eligible tracks so later catalogue edits cannot change that game's answers.
5. The browser receives game events over WebSocket, plays private audio through
   short-lived signed URLs, and sends guesses to the backend.
6. The backend controls deadlines, matches answers, awards points, and saves a
   completed-game summary in PostgreSQL.

Angular provides the browser interface. A Java/Spring Boot backend owns security
and game rules. PostgreSQL stores durable records; MinIO stores private audio.
Active games live in backend memory. Docker Compose runs these services behind
Nginx. See [architecture](docs/architecture.md) for the connections and boundaries.

## Documentation tree

Read this page first, then open the branch relevant to your task. Each branch
links to deeper explanations and source files; reading the whole tree is unnecessary.

| Branch | What you will find |
|---|---|
| [Product](docs/product/README.md) | User journeys → current status → gameplay rules |
| [Architecture](docs/architecture.md) | Runtime and boundaries → data model → decisions and historical design |
| [Backend](docs/backend/README.md) | Request flow → authentication, catalogue/imports, sessions → STOMP contract |
| [Frontend](docs/frontend/README.md) | Routes and state → gameplay client and audio → source and tests |
| [Development](docs/development/README.md) | Local setup → environment configuration → testing |
| [Operations](docs/operations/README.md) | Deployment → administrator provisioning → media runbooks |
| [Roadmap](docs/plan.md) | Next ticket → decisions, individual delivery lots, completion rules |

Contributors and AI agents should also read [AGENTS.md](AGENTS.md) for operating
rules. Pages aim for roughly 200 lines, with up to 300 for a cohesive topic.
The [OpenAPI contract](docs/swagger.yaml) and generated UI references are technical
artifacts rather than prose guides and retain their native structure.

## Start locally

Install Docker and Docker Compose, copy `.env.example` to `.env`, and replace its
placeholders. Then, from the repository root:

```bash
docker compose build
docker compose up -d
```

Open `http://localhost:8090` unless you changed `APP_PORT`. The automatic local
override configures the development profile and Turnstile test keys.
[Local development](docs/development/README.md) explains configuration, host-based
execution, logs, and shutdown. [Testing](docs/testing.md) lists validation commands.
