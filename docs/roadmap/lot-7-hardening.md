# 14. Lot 7 — Full-Stack Validation and Hardening

[Up: Roadmap](../plan.md)

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
