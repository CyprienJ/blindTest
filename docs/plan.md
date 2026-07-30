# Blindtest Product Delivery Roadmap

Status: proposed master plan

Last reviewed: 2026-07-30

Target: first public production release, followed by multiplayer and social features

Deployment target: one VPS, Docker Compose, existing host reverse proxy

## Next Ticket

**[CAT-07 — Complete playlist ordering APIs](#cat-07--complete-playlist-ordering-apis)**

Status: ready

FND-05 remains blocked by DG-04. CAT-01, CAT-03, and CAT-04 are complete. CAT-02
remains blocked by DG-03; CAT-05 and CAT-06 depend on its alias-management API.
CAT-07 is therefore the next ready ticket in the recommended execution sequence.
MEDIA-08 remains deferred until real legacy files require migration.

This is the authoritative next ticket to implement. When it meets the
[definition of done](#definition-of-done), replace it with the next ready,
unfinished ticket from the [recommended execution sequence](#19-first-recommended-execution-sequence).
Do not advance this pointer while acceptance criteria or required validation are
still incomplete. If the next ticket is blocked, record the blocker here and move
to the next ready ticket only when the roadmap dependencies allow it.

This document supersedes the previous testing-and-storage-only plan. It organizes
delivery into lots and implementation tickets rather than time-boxed sprints.
Testing, S3-compatible storage, security, and operations are integrated into the
product roadmap.

`docs/architecture.md` remains useful target documentation, but it is not an
unquestionable source of truth. Any contradiction between that document and the
validated decisions below must be resolved through an explicit architecture or
product decision before implementation.

## 1. Validated Product Decisions

### First production release

The first release must provide:

- account registration with verified email;
- login, logout, token refresh, and password recovery;
- an administrator-only global music catalogue;
- administrator-only YouTube imports;
- an administrator preview and validation step before publication;
- administrator-only public playlist creation and editing;
- a solo game for one authenticated player in one browser;
- game configuration:
  - public playlist;
  - number of tracks;
  - maximum answer time per track;
- typed answers with tolerant matching;
- a score based on remaining time;
- skip;
- timeout and answer reveal;
- an end-of-game summary;
- persistence of completed games from the first release, even though the full
  history UI comes later;
- music files stored in private S3-compatible object storage;
- MinIO hosted on the VPS initially.

### Solo game rules

- The player creating a game must be authenticated.
- The expected primary answer is `title`:
  - the film title for a film-related track;
  - the music title otherwise.
- A correct title ends the round immediately.
- A correct artist, composer, or album answer:
  - is acknowledged privately to the player;
  - awards no points;
  - does not end the round;
  - can only be acknowledged once per field;
  - allows the player to continue guessing the title.
- Manually configured alternative answers must be supported.
- Wrong answers do not remove points.
- A short server-enforced anti-spam cooldown may reject overly frequent attempts.
- Timeout reveals the answer and awards zero title points.
- Skip reveals the answer and awards zero title points.
- The baseline scoring model is up to 1,000 points, decreasing approximately
  linearly with elapsed time. The exact rounding rule remains a decision gate.

### Product administration

- Only administrators import tracks.
- Imported tracks join one global catalogue.
- Only administrators create and edit playlists.
- Playlists are public.
- Imported media retains the current preview/validate/reject workflow.

### Technical direction

- The backend remains a modular Spring Boot monolith for the first release.
- PostgreSQL stores durable product and history data.
- MinIO stores music objects through an S3-compatible storage abstraction.
- The bucket is private; the application generates short-lived pre-signed playback
  URLs after authorization.
- The server is authoritative for game state, timing, answer validation, and score.
- Even a solo game is represented as a server-side game session with one player.
- STOMP/WebSocket is used from the first release for game events so multiplayer
  does not require replacing the transport model.
- Active sessions initially live in memory behind a replaceable `SessionStore`
  interface.
- A later implementation may replace the in-memory store/event transport with
  Redis without changing the game domain.
- The existing generated design in `docs/Blindtest/Blindtest Views.html` remains
  the visual reference.
- Desktop is the first-release target. A phone layout must not crash, but mobile
  gameplay is not a release requirement.

## 2. Explicitly Deferred Features

These features are outside the first release but must be anticipated at domain and
boundary level:

- multiplayer sessions;
- guest players without accounts;
- configurable reveal/scoring game modes beyond the initial solo mode;
- friends and friend requests;
- invitations and notifications;
- profile editing;
- history browsing and public player history;
- game detail pages;
- admin user management;
- horizontal backend scaling;
- Redis-backed sessions and pub/sub;
- managed external S3 or a distributed MinIO deployment;
- mobile-first gameplay.

“Anticipated” means preserving appropriate abstractions and data shapes. It does
not mean implementing unused infrastructure in advance.

## 3. First-Release User Journeys

### Visitor and account

1. A visitor registers with username, email, password, and Turnstile.
2. The user receives an email and verifies the account.
3. The user logs in.
4. The user can recover a forgotten password.
5. The authenticated user reaches the game configuration page.

### Administrator catalogue

1. An administrator submits a supported YouTube URL and metadata.
2. The backend downloads and converts the media.
3. The finalized MP3 is uploaded to MinIO.
4. Import progress is visible to the initiating administrator.
5. The administrator previews the imported audio.
6. The administrator corrects metadata and alternative answers.
7. The administrator publishes or rejects the track.

### Administrator playlist

1. An administrator creates a public playlist.
2. Published tracks are searched and added.
3. Tracks can be removed and reordered.
4. The public playlist detail preserves that order.

### Solo game

1. An authenticated user selects a playlist.
2. The user selects the track count and per-track time limit.
3. The backend creates a one-player game session and snapshots the selection.
4. The server starts a round and sends its start time and a temporary audio URL.
5. The browser starts playback and displays the countdown.
6. The user submits guesses:
   - secondary metadata is acknowledged without score;
   - a correct title ends the round and awards time-based points.
7. The user may skip; otherwise the timer may expire.
8. The server reveals the answer and advances to the next round.
9. The final result and round detail are persisted.
10. The user sees the end summary.

## 4. Target Architecture

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

## 5. Decision Gates

These choices are intentionally not made by this roadmap. They require explicit
product-owner approval before the blocking ticket starts.

| Gate | Required decision | Blocks |
|---|---|---|
| DG-01 | Exact score rounding and minimum score for a correct title | GAME-06 |
| DG-02 | Allowed track-count values and timer range/default | GAME-02, UI-02 |
| DG-03 | Whether playback starts at the beginning or at an admin-configured excerpt offset | CAT-02, GAME-08 |
| DG-04 | How the first production administrator is provisioned | FND-05 |
| DG-05 | Retention period for game histories, import jobs, and rejected media | HIST-01, OPS-08 |
| DG-06 | Content rights, YouTube import policy, and administrator responsibility notice | REL-03 |
| DG-07 | Recovery objectives and off-host backup destination for PostgreSQL and MinIO | OPS-05, OPS-06 |
| DG-08 | Whether game tracks follow playlist order or are randomly selected | GAME-03 |
| DG-09 | Reveal-screen duration and automatic versus manual progression to the next round | UI-07, SESSION-05 |

Each decision must be written as an ADR or product decision note before related
implementation begins.

## 6. Delivery Overview

```text
LOT 0  Repository, security, and decision baseline
  |
  +--> LOT 1  Automated quality foundation
  |
  +--> LOT 2  S3/MinIO media foundation
  |      |
  |      +--> LOT 3  Complete catalogue and playlist administration
  |                    |
  +--> LOT 4  Pure solo-game domain
           |
           +--> LOT 5  Session, WebSocket, and history backend
                         |
                         +--> LOT 6  Solo-game frontend and summary

LOT 7  Full-stack validation and hardening (progresses across Lots 2-6)
LOT 8  VPS operations and deployment readiness
LOT 9  Beta validation and production launch
```

Lots may overlap when their dependencies permit. The critical product path is:

```text
security -> storage -> catalogue -> game domain -> session backend
-> game frontend -> full-stack validation -> production
```

## 7. Lot 0 — Repository, Security, and Decision Baseline

### Objective

Remove known security hazards, establish accurate documentation, and make runtime
configuration safe before new product code is added.

### Tickets

#### FND-01 — Revoke and remove the committed Resend credential

Status: handled outside the roadmap ticket workflow by the product owner.

Work:

- revoke/rotate the exposed key;
- remove the fallback credential from `application.properties`;
- require a runtime secret outside the test profile;
- inspect Git history and deployment environments;
- document the incident and rotation.

Acceptance:

- no live-looking credential remains in tracked files;
- production refuses to send mail without configured credentials;
- tests use an explicit fake key;
- secret scanning passes.

#### FND-02 — Align documentation with delivered code

Status: completed on 2026-07-27.

Work:

- mark this roadmap as the delivery authority;
- update the root README and remove `CLAUDE.md` in favor of `AGENTS.md`;
- describe the current and target architecture separately;
- update `AGENTS.md` with the roadmap location;
- create an ADR directory and template.

Acceptance:

- a new contributor can distinguish implemented, planned, and deferred behavior;
- local commands reflect the real Compose stack;
- no document still claims that all code remains to be written.

#### FND-03 — Standardize error contracts and correlation IDs

Status: completed on 2026-07-27.

Work:

- define the REST error DTO and stable error codes;
- add one global exception handler;
- include a request/correlation ID in logs and error responses;
- prevent stack traces and provider secrets from reaching clients.

Acceptance:

- authentication, validation, not-found, conflict, and internal errors share one
  documented format;
- integration tests verify representative errors.

#### FND-04 — Validate production configuration at startup

Status: completed on 2026-07-27.

Work:

- introduce typed configuration properties;
- validate JWT, Resend, Turnstile, database, S3, and public URL settings;
- separate development, test, and production defaults;
- remove permissive production fallbacks.

Acceptance:

- missing production secrets fail startup with safe messages;
- test/dev profiles remain easy to run;
- values are not printed in logs.

#### FND-05 — Define and implement first-admin provisioning

Blocked by: DG-04.

Work:

- implement the approved provisioning mechanism;
- make it idempotent and auditable;
- prevent normal registration from selecting `ADMIN`.

Acceptance:

- the first administrator can be created without manual unsafe SQL;
- repeated provisioning cannot create unexpected administrators.

#### FND-06 — Record core architecture decisions

Status: completed on 2026-07-27.

Work:

- ADR: modular monolith;
- ADR: server-authoritative sessions;
- ADR: STOMP from V1;
- ADR: in-memory `SessionStore` behind a port;
- ADR: private S3 bucket and pre-signed playback;
- ADR: PostgreSQL history snapshots.

Acceptance:

- trade-offs and future replacement paths are recorded;
- architecture diagrams reference the ADRs.

### Lot exit criteria

- known committed secret is rotated;
- production configuration fails safely;
- documentation and ADRs reflect validated decisions;
- admin provisioning has an approved design.

## 8. Lot 1 — Automated Quality Foundation

### Objective

Create reliable feedback before implementing storage and gameplay changes.

### Tickets

#### TEST-01 — Add backend coverage reporting

Status: completed on 2026-07-27.

Work:

- add JaCoCo XML and HTML reports;
- publish the report in CI;
- record baseline line and branch coverage;
- gate changed critical packages incrementally.

Acceptance:

- every backend CI run produces a readable coverage report;
- no arbitrary global threshold encourages low-value tests.

#### TEST-02 — Add PostgreSQL Testcontainers and Flyway validation

Status: completed on 2026-07-27.

Work:

- start PostgreSQL 16 in integration tests;
- run all Flyway migrations from an empty database;
- start Spring with `ddl-auto=validate`;
- cover UUIDs, enum constraints, cascades, quoted `order`, and repository queries;
- keep H2 only for tests where schema fidelity is irrelevant.

Acceptance:

- CI proves that the real schema migrates and the application validates against it;
- schema-sensitive tests no longer rely exclusively on H2.

#### TEST-03 — Add Playwright foundation

Status: completed on 2026-07-27.

Work:

- install `@playwright/test`;
- create `playwright.config.ts`, fixtures, reports, and ignore rules;
- configure `webServer`, base URL, Chromium, trace, screenshot, and failure video;
- add `test:e2e`, `test:e2e:ui`, and debug scripts;
- use one CI worker initially.

Acceptance:

- a deterministic Chromium smoke test passes locally and in CI;
- a forced failure produces a usable trace and screenshot.

#### TEST-04 — Cover authentication with Playwright

Status: completed on 2026-07-28.

Work:

- mocked-API tests for login, registration, Turnstile states, verification,
  forgot/reset password, refresh, logout, guards, and error states;
- semantic locators and keyboard behavior;
- focused ARIA snapshots;
- no real Cloudflare or Resend calls.

Acceptance:

- every implemented authentication page covers success, validation, authorization,
  and server failure;
- tests do not depend on external networks.

#### TEST-05 — Create deterministic external-service adapters

Status: completed on 2026-07-28.

Work:

- fake email adapter for tests;
- fake Turnstile verifier;
- fake process wrapper producing a small valid MP3 fixture;
- injected clock, scheduler, random, and ID generators where needed.

Acceptance:

- full-stack tests can exercise registration and import without external services
  or YouTube.

#### TEST-06 — Make required CI checks a deployment prerequisite

Work:

- expose required status checks for backend, frontend, migration, and smoke tests;
- prevent beta deployment from racing the CI workflow;
- upload test artifacts on failure.

Acceptance:

- a failing required test prevents deployment;
- manual production deployment cannot bypass required checks accidentally.

### Lot exit criteria

- PostgreSQL/Flyway is validated in CI;
- Playwright covers authentication;
- deterministic integration adapters exist;
- deployments can depend on named required checks.

## 9. Lot 2 — S3/MinIO Media Foundation

### Objective

Replace the host-coupled `/media` design with private S3-compatible object storage.

Dependencies: FND-04, TEST-02, TEST-05.

### Tickets

#### MEDIA-01 — Add the media storage port and configuration

Status: completed on 2026-07-28.

Work:

- add `MediaStorage` and `StoredMedia`;
- implement typed endpoint, public endpoint, region, bucket, credentials, and TTL
  configuration;
- use the MinIO Java SDK behind the port;
- add a fake/in-memory implementation for unit tests.

Acceptance:

- business services do not import MinIO SDK types;
- switching to another compatible endpoint is configuration-only at the business
  layer.

#### MEDIA-02 — Add MinIO to local Docker Compose

Status: completed on 2026-07-30.

Work:

- pin a MinIO server image;
- add a persistent `minio_data` volume;
- add health checks;
- bind the console to localhost;
- add a one-shot `mc` initializer;
- create the private `blindtest-media` bucket;
- configure application-origin CORS for GET, HEAD, and range requests.

Acceptance:

- `docker compose up` creates a healthy MinIO and private bucket automatically;
- unauthenticated object listing/read is denied;
- the browser can use a valid pre-signed URL.

#### MEDIA-03 — Add object-key schema migration

Status: completed on 2026-07-30.

Work:

- add nullable `audio_object_key`;
- add optional checksum and byte-size fields;
- temporarily retain legacy `audio_url`;
- add entity and DTO mappings without exposing object keys publicly;
- add a migration test from the current schema.

Acceptance:

- old rows remain readable;
- new rows can reference S3 without storing a pre-signed URL;
- Flyway succeeds on empty and existing-schema fixtures.

#### MEDIA-04 — Upload finalized imports to object storage

Status: completed on 2026-07-30.

Work:

- create a per-job temporary directory;
- run the fake/real process into that directory;
- verify MP3 type, non-empty size, and configured maximum size;
- compute checksum;
- upload and confirm object existence;
- persist key/checksum/size before marking `DONE`;
- remove temporary data in `finally`;
- make retries idempotent.

Acceptance:

- a storage failure marks the job failed and never produces a false `DONE`;
- successful jobs leave no temporary file;
- retry does not create duplicate active objects.

#### MEDIA-05 — Implement authorized preview URLs

Status: completed on 2026-07-30.

Work:

- add an admin-only preview URL endpoint;
- generate a short-lived pre-signed GET URL;
- validate status and object existence;
- avoid URLs in persistent logs.

Acceptance:

- an admin can preview a completed import;
- a normal user cannot request an unpublished preview;
- expiration is tested.

#### MEDIA-06 — Add reliable deletion and reconciliation

Status: completed on 2026-07-30.

Work:

- create a media-deletion outbox table;
- write deletion tasks in the same transaction as track deletion/replacement;
- process with retry/backoff;
- add reconciliation reporting for missing and orphaned objects;
- expose safe operational metrics.

Acceptance:

- transient MinIO failure does not lose the deletion request;
- operators can identify DB/object inconsistencies.

#### MEDIA-07 — Install runtime import dependencies

Status: completed on 2026-07-30.

Work:

- install and pin `yt-dlp` and FFmpeg in the backend runtime image;
- run as a non-root user;
- define CPU/time/file-size limits;
- add image smoke assertions for both binaries.

Acceptance:

- the production image can execute both binaries;
- hung or oversized imports terminate safely.

#### MEDIA-08 — Migrate existing media

Work:

- implement dry-run, batch, single-ID, resume, checksum, and structured reporting;
- upload legacy `/media` files;
- update rows only after verification;
- retain the source volume for an approved rollback window;
- remove legacy read support only in a later deployment.

Acceptance:

- every migrated row has a verified object;
- rerunning the command is safe;
- rollback remains possible during the observation window.

#### MEDIA-09 — Add MinIO integration tests

Status: completed on 2026-07-30.

Work:

- Testcontainers or Compose tests for put, exists, pre-sign, range GET, delete,
  retry, and unavailable storage;
- browser audio seek smoke test.

Acceptance:

- storage behavior is validated against a real MinIO API, not only mocks.

### Lot exit criteria

- new imports store objects in MinIO;
- private preview works;
- database stores object keys;
- failure and deletion are recoverable;
- runtime image contains import dependencies.

## 10. Lot 3 — Catalogue and Playlist Administration

### Objective

Complete the current backend functionality and replace all catalogue/admin frontend
placeholders required for the first release.

Dependencies: Lot 2.

### Tickets

#### CAT-01 — Extend the track data model

Status: completed on 2026-07-30.

Work:

- add `album`;
- formalize `title` as the primary game answer;
- retain `artist`, `composer`, `work`, category, decade, image, duration;
- define published metadata completeness rules;
- snapshot-compatible DTOs.

Acceptance:

- film title and music title use the same documented primary field;
- optional secondary fields are represented consistently.

#### CAT-02 — Model answer aliases and excerpt metadata

Blocked by: DG-03 for excerpt behavior.

Work:

- persist manually configured aliases by answer type;
- support aliases for title, artist, composer, and album;
- validate duplicate/blank aliases;
- add excerpt offset/duration only if approved;
- expose admin CRUD without exposing internal normalization.

Acceptance:

- administrators can manage alternative valid answers;
- published tracks always have at least one primary title answer.

#### CAT-03 — Harden YouTube import requests

Status: completed on 2026-07-30.

Work:

- accept supported YouTube URL forms only;
- validate metadata and field sizes;
- reject duplicate active imports;
- enforce import timeout and maximum output size;
- sanitize provider errors;
- keep command arguments as a list without a shell.

Acceptance:

- malformed/unsupported URLs fail before process startup;
- import cannot write outside its temporary directory.

#### CAT-04 — Complete import status and publication workflow

Status: completed on 2026-07-30.

Work:

- define allowed status transitions;
- make validate/reject idempotent;
- delete or retain rejected objects according to DG-05;
- expose progress, safe error, preview availability, and timestamps;
- test reconnect/poll fallback when WebSocket progress was missed.

Acceptance:

- only `DONE` tracks can be published or rejected;
- only `PUBLISHED` tracks appear in public playlists/catalogue.

#### CAT-05 — Implement the admin track catalogue UI

Work:

- list/filter/paginate all statuses;
- edit metadata and aliases;
- preview audio;
- validate, reject, and delete with confirmation;
- show import and storage errors.

Acceptance:

- the placeholder is replaced by the reference design;
- all actions enforce loading, empty, success, and failure states.

#### CAT-06 — Implement the admin import UI

Work:

- YouTube URL and metadata form;
- album and aliases;
- progress through STOMP with polling recovery;
- preview and publication;
- prevent duplicate submission;
- match the design reference.

Acceptance:

- an administrator completes the full import-to-publication journey from the UI;
- normal users cannot enter or invoke the flow.

#### CAT-07 — Complete playlist ordering APIs

Work:

- create/update/delete public playlists;
- add/remove/reorder published tracks;
- prevent duplicates;
- validate atomic order updates;
- define behavior when a published track is later rejected/deleted.

Acceptance:

- order is deterministic and transactionally updated;
- playlist detail never exposes unavailable audio metadata.

#### CAT-08 — Implement playlist editor UI

Work:

- playlist metadata;
- published-track search;
- add/remove/reorder;
- unsaved-change warning;
- optimistic UI only where rollback is safe.

Acceptance:

- an administrator can create a playable ordered playlist without direct API use.

#### CAT-09 — Implement public catalogue and playlist selection data

Work:

- public list/detail endpoints aligned with OpenAPI;
- expose published metadata and track counts, never storage keys or YouTube URLs;
- define cache behavior;
- provide the game configuration page with eligible playlists.

Acceptance:

- unauthenticated visitors may view public playlists;
- only an authenticated user may create a game.

#### CAT-10 — Add catalogue/admin automated coverage

Work:

- service and PostgreSQL integration tests;
- Playwright admin import and playlist workflows with deterministic adapters;
- authorization and failure paths;
- OpenAPI contract checks.

Acceptance:

- each admin journey has a browser test and backend boundary coverage.

### Lot exit criteria

- an administrator can import, preview, publish, and organize tracks;
- a public ordered playlist can be selected for a game;
- all required admin placeholders are replaced.

## 11. Lot 4 — Pure Solo-Game Domain

### Objective

Implement deterministic game rules independently from transport and persistence.

Dependencies: CAT-01, CAT-02, CAT-07.

### Tickets

#### GAME-01 — Define game configuration and validation

Work:

- playlist ID, requested track count, maximum round duration;
- immutable configuration snapshot;
- validate playlist eligibility and sufficient published tracks;
- reserve extension fields for future game mode without accepting unsupported
  values.

Acceptance:

- invalid configurations return stable domain errors;
- valid configuration is independent from subsequent playlist edits.

#### GAME-02 — Approve and enforce configuration limits

Blocked by: DG-02.

Work:

- encode approved defaults/minimums/maximums;
- share constraints with frontend DTOs;
- reject abuse-sized games.

Acceptance:

- backend is authoritative for all limits;
- boundary tests cover exact limits.

#### GAME-03 — Select and snapshot tracks

Blocked by: DG-08.

Work:

- select the requested number without duplicates;
- use injectable randomness;
- apply the approved playlist-order or random-selection rule;
- snapshot primary/secondary answers and media key/version.

Acceptance:

- seeded tests are deterministic;
- active game answers do not change after catalogue edits.

#### GAME-04 — Implement answer normalization

Work:

- Unicode normalization;
- case and accent folding;
- punctuation and extra whitespace removal;
- configurable removal of common articles;
- token normalization;
- no locale-dependent surprises.

Acceptance:

- a reviewed corpus covers French accents, punctuation, articles, apostrophes,
  digits, and short answers.

#### GAME-05 — Implement tolerant matching and aliases

Work:

- exact normalized matching first;
- manually configured aliases;
- word/token comparison;
- proportional Damerau-Levenshtein tolerance;
- stricter thresholds for short answers;
- return the matched answer type without leaking canonical answers on failure.

Acceptance:

- false-positive and false-negative corpus is product-reviewed;
- the engine identifies title, artist, composer, and album matches;
- matching is deterministic and benchmarked.

#### GAME-06 — Implement scoring

Blocked by: DG-01.

Work:

- score only a correct title;
- base score on server-measured remaining time;
- secondary matches, timeout, and skip score zero;
- implement approved rounding/minimum;
- prevent duplicate score awards.

Acceptance:

- boundary tests cover start, midpoint, deadline, after deadline, duplicates, skip,
  and secondary answers.

#### GAME-07 — Implement round and game state machines

States:

```text
Game: CREATED -> PLAYING -> FINISHED | ABORTED
Round: PREPARING -> PLAYING -> REVEALED -> COMPLETED
```

Work:

- explicit allowed transitions;
- title, timeout, and skip outcomes;
- next-round progression;
- final summary;
- no transport or scheduler code in transition rules.

Acceptance:

- illegal transitions fail safely;
- the last round produces exactly one game completion.

#### GAME-08 — Define playback window

Blocked by: DG-03.

Work:

- apply approved start/excerpt behavior;
- ensure the pre-signed URL lasts beyond the round and reconnect buffer;
- store playback metadata in the game snapshot.

Acceptance:

- every round has deterministic media timing metadata.

#### GAME-09 — Implement private secondary discoveries

Work:

- track discovered secondary fields per participant;
- return `ALREADY_FOUND` without leaking additional data;
- keep the title guess available;
- do not modify score or round state.

Acceptance:

- one participant's discovery cannot be observed by another participant in domain
  projections prepared for future multiplayer.

#### GAME-10 — Add exhaustive domain tests

Work:

- unit, parameterized, property-oriented, and state-transition tests;
- matching corpus;
- injected clock/random;
- performance guard for answer matching.

Acceptance:

- no game-domain test requires Spring, PostgreSQL, WebSocket, or MinIO.

### Lot exit criteria

- a deterministic solo game can run entirely through domain commands;
- title/secondary behavior and score match approved rules;
- future participants/modes do not require rewriting the core.

## 12. Lot 5 — Sessions, WebSocket, and History Backend

### Objective

Expose the game domain as an authenticated, server-authoritative application.

Dependencies: Lots 3 and 4.

### Tickets

#### SESSION-01 — Add active session models and store port

Work:

- session ID, owner/host, participants, configuration, round state, version;
- `SessionStore` atomic read/update/remove operations;
- `InMemorySessionStore` with concurrency control;
- expiry metadata.

Acceptance:

- concurrent commands cannot overwrite newer state;
- the interface can be implemented by Redis later.

#### SESSION-02 — Create the solo-session REST endpoint

Work:

- require verified authenticated user;
- validate configuration;
- snapshot/select tracks;
- create the one-player session;
- return session ID and connection instructions.

Acceptance:

- anonymous or unverified users cannot create a session;
- one user can create multiple sequential games safely.

#### SESSION-03 — Define STOMP commands and events

Commands:

- connect/subscribe;
- start game;
- submit answer;
- skip round;
- request current snapshot/recover.

Events:

- session ready;
- round preparing/start;
- private secondary match;
- answer rejected/cooldown;
- round revealed;
- score updated;
- game finished;
- error.

Acceptance:

- payloads are versioned/documented;
- public session events and private player feedback are distinct;
- commands include idempotency IDs.

#### SESSION-04 — Authorize WebSocket destinations

Work:

- authenticate STOMP connect;
- authorize session membership for commands/subscriptions;
- use user queues for private feedback;
- reject destination manipulation;
- define behavior for expired access tokens during a connected game.

Acceptance:

- a user cannot observe or command another user's session;
- integration tests use a real STOMP connection.

#### SESSION-05 — Orchestrate server timers

Blocked by: DG-09.

Work:

- schedule authoritative round deadlines;
- handle scheduler races with title/skip;
- persist state version before publishing;
- recover current snapshot after browser reconnect;
- inject scheduler/clock in tests.

Acceptance:

- title-at-deadline, skip-at-deadline, duplicate timer, and reconnect races produce
  one valid outcome.

#### SESSION-06 — Resolve playback URLs for active rounds

Work:

- authorize session participant;
- pre-sign the snapshotted object key;
- include URL, `startAt`, deadline, and playback offset in round start;
- regenerate on authorized reconnect.

Acceptance:

- URL outlives the configured round plus buffer;
- object key is never sent to the browser as an authorization mechanism.

#### SESSION-07 — Enforce answer rate limits

Work:

- short per-player cooldown;
- per-session command rate guard;
- stable feedback without score penalty;
- metrics for abusive rates.

Acceptance:

- normal typing is unaffected;
- automated spam cannot saturate matching or event publishing.

#### HIST-01 — Add durable history schema

Blocked by: DG-05.

Work:

- game history, participant, and ordered round tables;
- authenticated nullable user model prepared for future guests;
- configuration and track-answer snapshots;
- outcome, timing, and score;
- retention metadata.

Acceptance:

- a completed game is reproducible without current catalogue rows;
- deleting a track does not destroy historical summaries.

#### HIST-02 — Persist game completion idempotently

Work:

- completion transaction;
- unique session/history key;
- retry after transient database failure;
- remove active state only after durable success or safe retry scheduling.

Acceptance:

- duplicate completion creates one history;
- failure does not silently lose a completed result.

#### HIST-03 — Add current-game summary endpoint

Work:

- authorize the game owner;
- return total score and ordered round results;
- include correct title and secondary metadata after completion;
- do not expose YouTube URL or storage key.

Acceptance:

- summary survives browser refresh after completion.

#### SESSION-08 — Expire and clean abandoned sessions

Work:

- define inactive/finished TTLs;
- scheduled cleanup;
- mark abandoned games according to product rules;
- remove timers and sensitive URLs;
- report cleanup metrics.

Acceptance:

- abandoned sessions cannot leak memory indefinitely;
- completed history is not removed.

#### SESSION-09 — Add backend integration and concurrency tests

Work:

- REST + STOMP + PostgreSQL + MinIO/fake media;
- timer races;
- reconnect;
- idempotency;
- authorization;
- history completion.

Acceptance:

- the complete backend game flow passes without a browser.

### Lot exit criteria

- an authenticated player can complete a server-authoritative solo game;
- WebSocket authorization and reconnection work;
- results are durably persisted;
- active sessions are bounded and cleanable.

## 13. Lot 6 — Solo-Game Frontend and Summary

### Objective

Deliver the playable desktop experience using the reference visual design.

Dependencies: Lot 5.

### Tickets

#### UI-01 — Implement authenticated application shell

Work:

- navigation and session state;
- access to play and public playlists;
- admin navigation based on role;
- logout;
- desktop layout from the reference design.

Acceptance:

- authenticated and administrator navigation is correct after refresh;
- inaccessible routes redirect safely.

#### UI-02 — Implement game configuration

Blocked by: DG-02.

Work:

- public playlist selection;
- track count and time limit controls;
- validate available track count;
- loading, empty, and error states;
- create session and connect.

Acceptance:

- invalid configuration cannot be submitted;
- backend errors are actionable.

#### UI-03 — Implement resilient STOMP service

Work:

- access-token connect headers;
- typed command/event API;
- reconnect/backoff;
- resubscribe and current snapshot recovery;
- teardown on route exit/logout;
- one connection shared appropriately.

Acceptance:

- a brief network interruption recovers the active game;
- subscriptions do not multiply after reconnect.

#### UI-04 — Implement audio playback service

Work:

- preload audio;
- schedule from server `startAt`;
- apply offset;
- handle autoplay rejection;
- support range/seeking;
- stop and release audio between rounds;
- surface playback failures.

Acceptance:

- playback begins within an approved tolerance on desktop browsers;
- old-round audio cannot continue into the next round.

#### UI-05 — Implement game room

Work:

- timer based on server deadline;
- score;
- answer input and submit;
- skip;
- connection/playback state;
- disable commands outside valid state;
- match the reference design.

Acceptance:

- keyboard-only play is possible;
- duplicate clicks/submissions do not create duplicate commands.

#### UI-06 — Implement answer feedback

Work:

- neutral wrong-answer feedback without revealing the answer;
- private artist/composer/album confirmation;
- already-found feedback;
- cooldown feedback;
- clear input and preserve focus for continued title guesses.

Acceptance:

- secondary success never appears as title success and never ends the round.

#### UI-07 — Implement reveal and round transition

Blocked by: DG-09.

Work:

- display title and available metadata after title/timeout/skip;
- display awarded points and outcome;
- transition to the next server-started round;
- handle last round separately.

Acceptance:

- reveal cannot expose the next round early;
- transition remains correct after reconnect.

#### UI-08 — Implement persisted end summary

Work:

- total score;
- per-round title, outcome, response time, and points;
- replay/new-game action;
- refresh-safe fetch from history summary endpoint;
- reference end-screen design.

Acceptance:

- summary remains available after a page refresh;
- no general history browser is required for V1.

#### UI-09 — Add frontend game Playwright coverage

Mocked-API coverage:

- configuration validation;
- title correct;
- secondary correct then title correct;
- wrong answer;
- cooldown;
- skip;
- timeout;
- reconnect;
- audio failure;
- final summary.

Acceptance:

- state rendering is deterministic without real timers or audio networks.

#### UI-10 — Add desktop browser and accessibility checks

Work:

- Chromium required;
- Firefox/WebKit scheduled initially;
- keyboard and visible focus;
- semantic labels and live regions;
- limited stable visual snapshots;
- minimal small-screen non-crash check.

Acceptance:

- supported desktop browsers complete the smoke journey;
- mobile gameplay quality is explicitly not a V1 gate.

### Lot exit criteria

- the reference-designed solo game is playable on desktop;
- all outcomes and reconnect states are represented;
- summary is persisted and refresh-safe;
- Playwright covers the user-visible state machine.

## 14. Lot 7 — Full-Stack Validation and Hardening

### Objective

Prove that browser, backend, PostgreSQL, MinIO, WebSocket, and containers work
together.

This lot progresses alongside Lots 2 through 6.

### Tickets

#### E2E-01 — Create an isolated full-stack test environment

Work:

- `docker-compose.test.yml`;
- PostgreSQL, MinIO/init, backend e2e profile, frontend/Nginx;
- deterministic credentials;
- fake Turnstile, email, and media process;
- isolated ephemeral data.

Acceptance:

- one command starts, tests, and stops the environment;
- parallel CI runs cannot share data.

#### E2E-02 — Automate account journey

Work:

- registration;
- captured verification;
- login;
- access refresh;
- password reset;
- logout.

Acceptance:

- the browser talks to the real backend/database without external providers.

#### E2E-03 — Automate admin catalogue journey

Work:

- admin login;
- import fake media;
- observe progress;
- preview through MinIO;
- publish;
- create and order a playlist.

Acceptance:

- a resulting playlist is publicly visible and game-eligible.

#### E2E-04 — Automate solo game journey

Work:

- create game;
- receive/play test audio;
- secondary answer feedback;
- title score;
- skip and timeout rounds;
- finish and refresh summary.

Acceptance:

- the primary V1 journey passes end to end in Chromium.

#### E2E-05 — Add load and concurrency smoke tests

Work:

- baseline API/WebSocket load;
- at least the expected ten simultaneous solo games;
- a higher non-production headroom scenario;
- import isolated from game responsiveness;
- measure memory, threads, DB pool, and event latency.

Acceptance:

- ten simultaneous games complete without correctness errors;
- resource bottlenecks and safe operational limits are documented.

#### E2E-06 — Add failure-injection tests

Scenarios:

- MinIO temporarily unavailable;
- PostgreSQL transient failure;
- backend restart during an active in-memory game;
- browser disconnect/reconnect;
- import process failure;
- expired pre-signed URL;
- timer/answer race.

Acceptance:

- expected data-loss boundaries of the initial in-memory architecture are documented;
- failures never publish corrupt tracks or duplicate histories.

#### E2E-07 — Add security verification

Work:

- authorization matrix;
- object and destination enumeration;
- WebSocket destination abuse;
- input size and rate limits;
- CORS, CSP, cookies, headers;
- secret scanning and dependency audit;
- pre-signed URL leakage review.

Acceptance:

- no critical/high finding remains unexplained at release.

### Lot exit criteria

- all three main journeys pass full stack;
- target initial concurrency is demonstrated;
- known failure behavior and security boundaries are documented.

## 15. Lot 8 — VPS Operations and Deployment Readiness

### Objective

Deploy safely beside the existing site on one VPS and make recovery possible.

Dependencies: operational pieces begin early; final exit depends on Lots 2-7.

### Tickets

#### OPS-01 — Produce production-ready images

Work:

- multi-stage reproducible builds;
- non-root runtime;
- pinned base images and runtime tools;
- health checks;
- read-only filesystem where practical;
- bounded temporary import volume;
- image vulnerability scan.

Acceptance:

- images contain only required runtime tools;
- health and import smoke tests pass.

#### OPS-02 — Define production Compose topology

Work:

- PostgreSQL, backend, frontend, MinIO, MinIO init;
- explicit networks;
- localhost-bound application port for host reverse proxy;
- private database and console;
- named persistent volumes;
- resource limits and restart policies;
- avoid port conflicts with the other site.

Acceptance:

- the stack starts after VPS reboot without exposing internal services.

#### OPS-03 — Integrate the existing reverse proxy

Work:

- application virtual host;
- TLS;
- `/api` and WebSocket upgrade;
- public MinIO media endpoint required by signed URLs;
- request/body/time limits suitable for the application;
- preserve the other site's configuration.

Acceptance:

- both sites work concurrently;
- WebSocket and range audio requests pass through TLS.

#### OPS-04 — Manage production secrets

Work:

- database, JWT, Resend, Turnstile, MinIO root, and application service credentials;
- least-privilege MinIO service account;
- file permissions and rotation runbook;
- prevent secrets in Compose files, logs, and CI artifacts.

Acceptance:

- deployed secrets exist only in approved secret locations;
- rotation has been rehearsed for JWT/provider/storage credentials where possible.

#### OPS-05 — PostgreSQL backup and restore

Blocked by: DG-07.

Work:

- scheduled encrypted off-host backups;
- retention;
- restore command/runbook;
- periodic restore test;
- migration rollback policy.

Acceptance:

- a restore to an isolated database is demonstrated and timed.

#### OPS-06 — MinIO backup and restore

Blocked by: DG-07.

Work:

- approved off-host copy/replication;
- object/version policy;
- restore and reconciliation;
- capacity alerts;
- document single-node failure risk.

Acceptance:

- a deleted test object/bucket can be restored in an isolated rehearsal.

#### OPS-07 — Add application observability

Work:

- structured logs with correlation/session/job IDs;
- health and readiness;
- metrics for HTTP, WebSocket sessions, game timers, imports, MinIO, DB pool,
  deletion outbox, and email;
- disk/capacity metrics;
- basic alerting.

Acceptance:

- operators can distinguish application, database, media, and provider failures.

#### OPS-08 — Add retention and cleanup jobs

Blocked by: DG-05.

Work:

- expired refresh tokens;
- old import jobs/errors;
- abandoned sessions;
- rejected/orphaned media;
- history retention if any;
- temporary import directories.

Acceptance:

- every growing dataset has an owner, retention rule, and observable cleanup.

#### OPS-09 — Implement safe deployment and rollback

Work:

- immutable image tags;
- pre-deploy backup;
- migration compatibility check;
- pull/start/health/smoke sequence;
- rollback to compatible images;
- avoid `latest` in production;
- preserve previous Compose/env versions securely.

Acceptance:

- a failed health check automatically stops promotion;
- rollback is rehearsed on beta.

#### OPS-10 — Write operator runbooks

Runbooks:

- deploy/rollback;
- database restore;
- MinIO restore;
- failed import;
- full disk;
- expired/provider credentials;
- stuck session/job;
- compromised account/admin;
- other-site/reverse-proxy conflict.

Acceptance:

- each alert points to a practical first-response procedure.

### Lot exit criteria

- stack coexists with the existing site;
- secrets, backups, monitoring, and rollback are operational;
- single-node risks are accepted and mitigated.

## 16. Lot 9 — Beta Validation and Production Launch

### Objective

Validate the complete product with representative users and launch with controlled
risk.

### Tickets

#### REL-01 — Deploy an isolated beta environment

Work:

- separate database, bucket, credentials, ports, and domain;
- same image artifacts as production;
- synthetic/non-sensitive catalogue.

Acceptance:

- beta cannot read or mutate production data.

#### REL-02 — Execute product acceptance

Checklist:

- register/verify/login/reset;
- admin import/preview/publish;
- playlist create/order;
- all game outcomes;
- summary refresh;
- desktop browser coverage;
- error and empty states;
- reference-design review.

Acceptance:

- product owner signs off the V1 journeys and known limitations.

#### REL-03 — Approve content/import policy

Blocked by: DG-06.

Work:

- document permitted sources and administrator responsibility;
- review YouTube/rights implications;
- add necessary administrator notice and removal process;
- define response to a rights complaint.

Acceptance:

- production import is not launched without an approved policy.

#### REL-04 — Rehearse data and media recovery

Work:

- restore beta PostgreSQL;
- restore/reconcile MinIO;
- verify histories and playback;
- measure actual recovery time.

Acceptance:

- recovery evidence meets DG-07.

#### REL-05 — Run final performance and security checks

Work:

- repeat expected/headroom load;
- dependency/image scan;
- authorization regression;
- verify no test keys/defaults;
- verify rate and size limits.

Acceptance:

- no unexplained release-blocking result remains.

#### REL-06 — Execute production launch

Work:

- backups;
- migrations;
- deploy pinned images;
- health and smoke tests;
- create/verify admin;
- import a controlled production track;
- create a playlist;
- complete a solo game;
- monitor.

Acceptance:

- every critical journey works through the public TLS endpoint.

#### REL-07 — Post-launch observation

Work:

- monitor errors, storage, DB, WebSocket, email, and import;
- review feedback;
- prioritize fixes before post-V1 expansion;
- record capacity baseline.

Acceptance:

- launch incident log and prioritized follow-up list exist.

## 17. Post-V1 Roadmap

### Lot P1 — Profiles and history browsing

- profile read/update;
- avatar strategy;
- password change;
- history list/detail UI;
- public/private history policy decision;
- Playwright and authorization coverage.

Architecture already prepared by durable V1 history and authenticated participant
identity.

### Lot P2 — Multiplayer and guests

- lobby and short join codes;
- guest participant tokens;
- registered and guest players;
- host controls;
- readiness;
- up to ten participants;
- synchronized `startAt`;
- per-player private secondary discoveries;
- scores and scoreboard;
- disconnect/rejoin;
- host departure policy;
- multiplayer concurrency tests.

The V1 server-authoritative engine, participant abstraction, private queues, and
session store port are prerequisites.

### Lot P3 — Additional game modes

- reveal-mode product decisions;
- alternative scoring;
- skip voting;
- categories/decades and mixed selections;
- mode-versioned configuration;
- compatibility with historical snapshots.

### Lot P4 — Friends, invitations, and notifications

- user search;
- friend requests;
- friend list/removal;
- session invitations;
- per-user notifications;
- privacy/rate-limit decisions.

### Lot P5 — Horizontal scaling

Trigger: measured load or availability need, not speculation.

- Redis `SessionStore`;
- distributed locks/versioning;
- pub/sub event propagation;
- multiple backend instances;
- load-balanced WebSocket strategy;
- distributed timer ownership;
- chaos/restart tests.

### Lot P6 — Storage evolution

Trigger: VPS capacity, reliability, or traffic.

- change S3 endpoint/credentials to managed object storage or distributed MinIO;
- copy objects with checksum verification;
- optional CDN;
- preserve object keys and application interfaces;
- rehearse rollback.

## 18. Ticket Readiness and Completion Rules

### Definition of ready

A ticket is ready when:

- blocking decision gates are approved;
- user-visible behavior and errors are stated;
- API/event/schema impact is identified;
- security and authorization rules are known;
- test level and fixtures are defined;
- migration and rollback impact is known;
- dependencies are complete or scheduled.

### Definition of done

A ticket is done when:

- behavior and failure paths are implemented;
- relevant unit/integration/Playwright tests pass;
- Checkstyle, lint, build, migrations, and required CI pass;
- API/events/schema documentation is updated;
- secrets and personal data are not logged;
- metrics/runbooks are updated for operational behavior;
- migrations are backward-compatible for the deployment sequence;
- acceptance criteria are demonstrated.

### Rules for future implementation

- Do not implement a blocked decision silently; ask the product owner.
- Do not mark placeholder routes/components as complete.
- Do not generate long-lived public media URLs.
- Do not couple the game domain to STOMP, JPA, MinIO, or the system clock.
- Do not introduce Redis, microservices, or Kubernetes before an approved need.
- Do not deploy if required CI checks fail.
- Do not remove legacy media until migration verification and rollback window end.

## 19. First Recommended Execution Sequence

Start in this order:

1. FND-01 through FND-06;
2. TEST-01 through TEST-05;
3. MEDIA-01 through MEDIA-07 and MEDIA-09;
4. CAT-01 through CAT-10;
5. resolve DG-01, DG-02, DG-03, DG-08, and DG-09;
6. GAME-01 through GAME-10;
7. SESSION/HIST tickets;
8. UI tickets;
9. complete full-stack and operations lots;
10. beta, recovery rehearsal, then production launch;
11. MEDIA-08 when real legacy files require migration, before removing `/media`.

TEST-06 should be completed before automated beta deployment resumes. Operations
work for secrets, backups, and reverse proxy should begin early rather than waiting
for the last product ticket.

## 20. Reference Documentation

- `docs/architecture.md` — original target architecture, to be revised through ADRs.
- `docs/swagger.yaml` — intended API, currently mixed with unimplemented endpoints.
- `docs/diagrams/*.puml` — original target component/data/sequence diagrams.
- `docs/Blindtest/Blindtest Views.html` — authoritative visual reference.
- `AGENTS.md` — repository operating context.
- `docs/adr/` — architecture decision record index and template.
- [Playwright web-server configuration](https://playwright.dev/docs/test-webserver)
- [Playwright authentication](https://playwright.dev/docs/auth)
- [Playwright API testing](https://playwright.dev/docs/api-testing)
- [Playwright CI guidance](https://playwright.dev/docs/ci)
- [MinIO container deployment](https://min.io/docs/minio/container/operations/install-deploy-manage/deploy-minio-single-node-single-drive.html)
- [MinIO Java SDK](https://docs.min.io/aistor/developers/sdk/java/)
- [Amazon S3 pre-signed URLs](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html)
