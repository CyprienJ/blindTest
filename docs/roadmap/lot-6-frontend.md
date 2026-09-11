# 13. Lot 6 — Solo-Game Frontend and Summary

[Up: Roadmap](../plan.md)

### Objective

Deliver the playable desktop experience using the reference visual design.

Dependencies: HOME-01 and HOME-02 depend only on the delivered authentication and
route infrastructure. UI-01 and the remaining gameplay tickets depend on Lot 5.

### Tickets

#### HOME-01 — Implement the public home page

Status: completed on 2026-08-04.

Priority: implement before HOME-02 and before resuming SESSION-03.

Work:

- make `/` a real public landing page rather than redirecting immediately to login;
- provide a concise product introduction without depending on unfinished gameplay
  or social data;
- expose clear login and registration actions;
- when a valid local session is already available, expose a direct action to the
  authenticated home page;
- use the shared visual language and provide responsive, keyboard-accessible
  semantics;
- add focused Angular and Playwright coverage for anonymous and authenticated
  visitors.

Acceptance:

- an unauthenticated visitor can understand the application and reach login or
  registration without encountering a protected route;
- an authenticated visitor can continue to `/home` without being forced through
  login again;
- refreshing `/` works through the deployed SPA fallback.

#### HOME-02 — Implement the authenticated home page

Status: completed on 2026-08-04.

Priority: implement immediately after HOME-01 and before resuming SESSION-03.

Work:

- add a guarded `/home` route and make it the default destination after login;
- present a primary action to create a game and show multiplayer joining as
  unavailable until its post-V1 implementation exists;
- expose playlist administration only to users whose authenticated role permits
  it, while retaining route-level authorization as the security boundary;
- provide navigation to the public playlist catalogue and logout;
- handle restored-session loading, invalid/expired authentication, and navigation
  errors without showing stale privileged actions;
- keep unfinished post-V1 actions absent or clearly disabled rather than linking
  to placeholder components;
- add role-aware Angular and Playwright coverage, including refresh and direct URL
  access.

Acceptance:

- a successful login and an authenticated visit to the application reach `/home`;
- every authenticated user can reach game creation, while joining is clearly
  identified as unavailable and never links to its placeholder route;
- administrators can reach playlist editing, while non-administrators neither see
  the action nor bypass the existing admin guard;
- navigation remains correct after refresh and logout returns the user to a public
  entry point.

#### UI-01 — Implement authenticated application shell

Status: implemented on 2026-09-02 in `f8febc9`.

Follow-up shell navigation and session recovery fixes were delivered separately
in `4a0ead9` on 2026-09-11. Those fixes did not reimplement UI-01.

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

Status: implemented on 2026-09-02 in `e9cac42`.

Decision: [ADR-0008](../adr/0008-solo-game-configuration-limits.md).

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

Status: implemented on 2026-09-02 in `4548500`.

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

Status: implemented on 2026-09-02 in `65ea8ec`.

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

Status: implemented on 2026-09-02 in `8228f4b`.

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

Status: completed on 2026-09-11.

Delivery: neutral rejection messages, private secondary discoveries and repeats,
a server-timed cooldown, snapshot discovery recovery, and continued keyboard
input. Secondary matches never change phase, score, or playback. See the
[gameplay client](../frontend/gameplay.md#answer-feedback) for delivered behavior.

Validation: 70 Angular tests, 66 Playwright tests (four workers), frontend lint and
production build, 500 backend tests with Checkstyle and coverage gates, and Compose
configuration validation passed. Existing style-budget and STOMP CommonJS build
warnings remain. No API, event, or schema changes were required.

Work:

- neutral wrong-answer feedback without revealing the answer;
- private artist/composer/album confirmation;
- already-found feedback;
- cooldown feedback;
- clear input and preserve focus for continued title guesses.

Acceptance:

- secondary success never appears as title success and never ends the round.

#### UI-07 — Implement reveal and round transition

Decision: [ADR-0015](../adr/0015-four-second-automatic-reveal-transition.md).

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
